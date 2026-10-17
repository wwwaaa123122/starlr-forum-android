package com.starlr.app.data

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * 401 时用 refreshToken 换新 Access Token 并重试（单次，避免死循环）。
 * Refresh 失败或 TOKEN_REPLAY → 清空会话并通知上层跳转登录。
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val baseUrlProvider: () -> String,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** 只用于刷新请求的裸客户端，避免再次进入 Authenticator */
    private val bareClient = OkHttpClient.Builder().build()

    /** 单飞锁：并发 401 时只放行一个线程真正调 /refresh，其余复用新 token */
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Starlr-Retry") != null) return null // 已重试过
        val failedAccess = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(refreshLock) {
            // 另一线程已完成刷新：直接复用最新 access token，不再打 /refresh
            val current = tokenStore.accessToken
            if (!current.isNullOrBlank() && current != failedAccess) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .header("X-Starlr-Retry", "1")
                    .build()
            }

            val refresh = tokenStore.refreshToken ?: run { onSessionExpired(); return null }

            val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrlProvider() + "api/auth/refresh")
                .post(body)
                .build()

            return try {
                bareClient.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        val errBody = res.body?.string().orEmpty().take(300)
                        android.util.Log.w("TokenAuthenticator", "刷新失败 HTTP ${res.code}: $errBody")
                        tokenStore.clear()
                        onSessionExpired()
                        return null
                    }
                    val text = res.body?.string().orEmpty()
                    val auth = json.decodeFromString(AuthResponse.serializer(), text)
                    val access = auth.accessToken
                    val newRefresh = auth.refreshToken ?: refresh
                    if (access.isNullOrBlank()) {
                        tokenStore.clear()
                        onSessionExpired()
                        return null
                    }
                    tokenStore.save(access, newRefresh)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $access")
                        .header("X-Starlr-Retry", "1")
                        .build()
                }
            } catch (e: Exception) {
                android.util.Log.w("TokenAuthenticator", "刷新异常: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}
