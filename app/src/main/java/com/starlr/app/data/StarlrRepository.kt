package com.starlr.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** 统一 API 错误（携带后端 error.code / error.message） */
data class ApiError(val httpStatus: Int, val code: String, override val message: String) : Exception(message)

/** 会话状态 */
data class Session(
    val loggedIn: Boolean,
    val user: User? = null,
    val unread: Int = 0,
)

/** 仓库层：Retrofit API + 会话状态管理 */
class StarlrRepository(
    baseUrl: String,
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
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(TokenAuthenticator(tokenStore, { baseUrl }, onSessionExpired))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: StarlrApi = Retrofit.Builder()
        .baseUrl(baseUrl)
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

    suspend fun login(identifier: String, password: String): User {
        val res = api.login(LoginRequest(identifier.trim(), password))
        applyTokens(res)
        val user = api.me().user
        _session.value = Session(true, user, 0)
        return user
    }

    suspend fun register(username: String, email: String, password: String, nickname: String?): User {
        val res = api.register(RegisterRequest(username.trim(), email.trim(), password, nickname?.trim()?.ifBlank { null }))
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
        /** 把 retrofit HttpException / IOException / 其它 Throwable 归一化为 ApiError */
        fun toApiError(e: Throwable): ApiError = when (e) {
            is ApiError -> e
            is HttpException -> {
                val body = e.response()?.errorBody()?.string()
                val parsed = body?.let {
                    runCatching {
                        val json = Json { ignoreUnknownKeys = true }
                        json.decodeFromString(ApiErrorBody.serializer(), it).error
                    }.getOrNull()
                }
                ApiError(e.code(), parsed?.code ?: "HTTP_${e.code()}", parsed?.message ?: "请求失败（${e.code()}）")
            }
            else -> ApiError(0, "NETWORK", "网络连接失败，请检查网络后重试")
        }
    }
}
