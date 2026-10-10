package com.starlr.app.ui

import com.starlr.app.data.ServerConfig

/**
 * API 基址的全局可访问点（Compose 组件深层需要拼接头像 URL 时使用）。
 * 跟随设置页中的服务器地址。
 */
object BuildConfigAware {
    var provider: () -> String = { ServerConfig.DEFAULT_URL }

    val baseUrl: String get() = provider()
}
