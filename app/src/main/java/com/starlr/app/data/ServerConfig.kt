package com.starlr.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ServerConfig(private val prefs: android.content.SharedPreferences) {

    private val _current = MutableStateFlow(
        prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
    )
    val changes: StateFlow<String> = _current

    val current: String get() = _current.value

    /** 保存新地址；返回 null 表示成功，否则为错误提示 */
    fun set(raw: String): String? {
        var v = raw.trim().trimEnd('/')
        if (v.isEmpty()) return "服务器地址不能为空"
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "https://$v"
        val url = v.toHttpUrlOrNull() ?: return "地址格式不正确"
        if (url.encodedPath.isNotEmpty() && url.encodedPath != "/") {
            return "地址需为站点根路径，例如 https://example.com"
        }
        val normalized = url.toString()
        prefs.edit().putString(KEY_SERVER, normalized).apply()
        _current.value = normalized
        return null
    }

    fun reset() {
        prefs.edit().putString(KEY_SERVER, null).apply()
        _current.value = DEFAULT_URL
    }

    companion object {
        val DEFAULT_URL: String = com.starlr.app.BuildConfig.API_BASE_URL
        private const val KEY_SERVER = "starlr.server"
    }
}
