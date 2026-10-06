package com.starlr.app.ui

/**
 * API 基址的全局可访问点（Compose 组件深层需要拼接头像 URL 时使用）。
 * 与 BuildConfig.API_BASE_URL 保持同步。
 */
object BuildConfigAware {
    var baseUrl: String = "http://10.0.2.2:8787/"
        private set

    fun init(url: String) {
        baseUrl = url
    }
}
