package com.starlr.app

import android.app.Application
import android.content.Context
import com.starlr.app.data.ServerConfig
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.TokenStore
import com.starlr.app.ui.BuildConfigAware

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val tokenStore = TokenStore(
        context.getSharedPreferences("starlr", Context.MODE_PRIVATE),
    )
    val serverConfig = ServerConfig(
        context.getSharedPreferences("starlr", Context.MODE_PRIVATE),
    )

    val repository: StarlrRepository = StarlrRepository(
        serverConfig = serverConfig,
        tokenStore = tokenStore,
        onSessionExpired = { this@AppContainer.repository.onSessionExpired() },
    )

    init {
        BuildConfigAware.provider = { serverConfig.current }
        // UI 扩展点：会话过期时可监听跳转登录（StateFlow 更新已在 Repository.onSessionExpired 内）
    }
}

class StarlrApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
