package com.starlr.app.data

import android.content.SharedPreferences

/** Token 持久化（SharedPreferences；生产可升级 DataStore） */
class TokenStore(private val prefs: SharedPreferences) {

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        private set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        private set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    val hasSession: Boolean get() = !accessToken.isNullOrBlank()

    fun save(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    fun clear() {
        accessToken = null
        refreshToken = null
    }

    var skippedUpdateVersion: Int
        get() = prefs.getInt(KEY_SKIPPED_UPDATE, 0)
        set(value) = prefs.edit().putInt(KEY_SKIPPED_UPDATE, value).apply()

    companion object {
        private const val KEY_ACCESS = "starlr.access"
        private const val KEY_REFRESH = "starlr.refresh"
        private const val KEY_SKIPPED_UPDATE = "starlr.skipped_update_version"
    }
}
