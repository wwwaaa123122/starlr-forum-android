package com.starlr.app

import android.app.Application
import android.content.Context
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.TokenStore
import com.starlr.app.ui.BuildConfigAware

/** 手动依赖容器（本项目不引入 Hilt/Koin） */
class AppContainer(context: Context) {
    val tokenStore = TokenStore(
        context.getSharedPreferences("starlr", Context.MODE_PRIVATE),
    )

    val repository: StarlrRepository = StarlrRepository(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenStore = tokenStore,
        onSessionExpired = { /* 会话状态由 Repository 内部 StateFlow 通知 UI */ },
    )
}

class StarlrApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        BuildConfigAware.init(BuildConfig.API_BASE_URL)
        container = AppContainer(this)
    }
}
