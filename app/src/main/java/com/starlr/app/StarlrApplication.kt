package com.starlr.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.starlr.app.data.ServerConfig
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.TokenStore
import com.starlr.app.ui.BuildConfigAware

/** Coil 图片缓存参数（memory + disk，重启后图片仍秒开） */
object ImageCacheConfig {
    const val DISK_CACHE_SIZE_BYTES: Long = 64L * 1024 * 1024 // 64MB 磁盘缓存
    const val MEMORY_CACHE_PERCENT: Double = 0.20             // 可用内存的 20%

    fun create(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_SIZE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
}

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val tokenStore = TokenStore(
        context.getSharedPreferences("starlr", Context.MODE_PRIVATE),
    )
    val serverConfig = ServerConfig(
        context.getSharedPreferences("starlr", Context.MODE_PRIVATE),
    )

    val repository: StarlrRepository = StarlrRepository(
        appContext = appContext,
        serverConfig = serverConfig,
        tokenStore = tokenStore,
        onSessionExpired = { this@AppContainer.repository.onSessionExpired() },
    )

    init {
        BuildConfigAware.provider = { serverConfig.current }
        // UI 扩展点：会话过期时可监听跳转登录（StateFlow 更新已在 Repository.onSessionExpired 内）
    }
}

class StarlrApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil 全局 ImageLoader：配置 memory + disk 两级缓存。
     * 所有 AsyncImage（头像/背景/帖子图）自动走这个 loader——磁盘缓存让 App
     * 重启后同一张图直接读本地文件，不再等网络。
     * 缓存键 = 完整 URL；头像 URL 自带 ?v= 版本号，换头像后 URL 变化自然失效旧缓存。
     * 不随登出清空：图片是公开资源，切账号复用磁盘缓存反而更快。
     */
    override fun newImageLoader(): ImageLoader = ImageCacheConfig.create(this)
}
