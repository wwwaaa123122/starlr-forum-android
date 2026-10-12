package com.starlr.app.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** 统一 API 错误（携带后端 error.code / error.message） */
data class ApiError(val httpStatus: Int, val code: String, override val message: String) : Exception(message)

/** 会话状态 */
data class Session(
    val loggedIn: Boolean,
    val user: User? = null,
    val unread: Int = 0,
)

class ServerRewriteInterceptor(private val server: ServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = server.current.toHttpUrlOrNull() ?: return chain.proceed(chain.request())
        val req = chain.request()
        val url = req.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(req.newBuilder().url(url).build())
    }
}

/** 仓库层：Retrofit API + 会话状态管理 */
class StarlrRepository(
    private val serverConfig: ServerConfig,
    private val tokenStore: TokenStore,
    onSessionExpired: () -> Unit,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ServerRewriteInterceptor(serverConfig))
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(TokenAuthenticator(tokenStore, { serverConfig.current }, onSessionExpired))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: StarlrApi = Retrofit.Builder()
        .baseUrl(ServerConfig.DEFAULT_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StarlrApi::class.java)

    private val _session = MutableStateFlow(Session(tokenStore.hasSession))
    val session: StateFlow<Session> = _session

    suspend fun loadCurrentUser(): User? {
        val me = runCatching { api.me().user }.getOrNull()
        _session.value = Session(tokenStore.hasSession, me, _session.value.unread)
        return me
    }

    suspend fun refreshUnread(): Int {
        val unread = runCatching { api.unreadCount().unread }.getOrDefault(_session.value.unread)
        _session.value = _session.value.copy(unread = unread)
        return unread
    }

    suspend fun login(identifier: String, password: String, turnstileToken: String? = null): User {
        val res = api.login(LoginRequest(identifier.trim(), password, turnstileToken))
        applyTokens(res)
        val user = api.me().user
        _session.value = Session(true, user, 0)
        return user
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        nickname: String?,
        turnstileToken: String? = null,
    ): User {
        val res = api.register(RegisterRequest(username.trim(), email.trim(), password, nickname?.trim()?.ifBlank { null }, turnstileToken))
        applyTokens(res)
        val user = api.me().user
        _session.value = Session(true, user, 0)
        return user
    }

    suspend fun logout() {
        runCatching { api.logout(LogoutRequest(tokenStore.refreshToken)) }
        tokenStore.clear()
        _session.value = Session(false)
    }

    /** 浏览器 SSO：用一次性 ticket 兑换当前服务器的会话（deep link 回来后调用） */
    suspend fun exchangeSiteTicket(ticket: String, challenge: String): User {
        val res = api.redeemSiteTicket(SiteTicketRedeemRequest(ticket, challenge, APP_SSO_ORIGIN))
        applyTokens(res)
        val user = api.me().user
        _session.value = Session(true, user, 0)
        return user
    }

    fun resetSession() {
        tokenStore.clear()
        _session.value = Session(false)
    }

    private suspend fun applyTokens(res: AuthResponse) {
        val access = res.accessToken ?: throw ApiError(401, "NO_TOKEN", "登录响应缺少 Token")
        val refresh = res.refreshToken ?: throw ApiError(401, "NO_TOKEN", "登录响应缺少 Token")
        tokenStore.save(access, refresh)
    }

    /** 会话过期（由 Authenticator 回调） */
    fun onSessionExpired() {
        tokenStore.clear()
        _session.value = Session(false)
    }

    companion object {
        /** 浏览器登录桥接页所在源（与 site-login 白名单一致） */
        const val APP_SSO_ORIGIN = "https://x.182030.xyz"
        const val APP_AUTH_URL = "https://x.182030.xyz/app-auth"

        /** 把 retrofit HttpException / IOException / 其它 Throwable 归一化为 ApiError，保留具体原因 */
        fun toApiError(e: Throwable): ApiError {
            Log.w("StarlrApi", "请求失败: ${e.javaClass.simpleName}: ${e.message}", e)
            return when (e) {
                is ApiError -> e
                is HttpException -> {
                    val body = e.response()?.errorBody()?.string()
                    val parsed = body?.let {
                        runCatching {
                            val json = Json { ignoreUnknownKeys = true }
                            json.decodeFromString(ApiErrorBody.serializer(), it).error
                        }.getOrNull()
                    }
                    if (parsed == null && body != null && !body.trimStart().startsWith("{")) {
                        ApiError(e.code(), "HTTP_${e.code()}", "服务器返回异常响应（${e.code()}），若持续出现请检查服务器地址")
                    } else {
                        ApiError(e.code(), parsed?.code ?: "HTTP_${e.code()}", parsed?.message ?: "请求失败（${e.code()}）")
                    }
                }
                is UnknownHostException -> ApiError(0, "DNS", "无法解析服务器地址，请检查网络或服务器地址设置")
                is SocketTimeoutException -> ApiError(0, "TIMEOUT", "连接超时，请检查网络后重试")
                is ConnectException -> ApiError(0, "CONNECT", "无法连接服务器，请检查网络或服务器地址设置")
                is SSLException -> ApiError(0, "SSL", "安全连接失败：${e.message?.take(100) ?: "证书或网络异常"}")
                is IOException -> ApiError(0, "NETWORK", "网络错误：${e.message?.take(120) ?: "连接中断"}")
                else -> ApiError(0, "UNKNOWN", e.message?.take(150) ?: "请求失败，请重试")
            }
        }
    }
}
