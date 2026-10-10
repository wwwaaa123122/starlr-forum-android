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
        onSessionExpired = { /* 会话状态由 Repository 内部 StateFlow 通知 UI */ },
    )

    init {
        BuildConfigAware.provider = { serverConfig.current }
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
