package com.starlr.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TurnstileException(message: String) : Exception(message)

/**
 * 通过隐藏 WebView 加载验证页拿 Turnstile token。
 * 页面固定用 x 域静态文件（该域名已在人机验证组件的可用域名内），
 * token 服务端 siteverify 不区分来源，与当前 API 服务器无关。
 */
class TurnstileGate(context: Context) {

    private val appContext = context.applicationContext
    private val finished = AtomicBoolean(false)

    var webView: WebView? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchToken(): String = try {
        withTimeout(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val wv = WebView(appContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(Bridge(cont), "StarlrAndroid")
                    }
                    webView = wv
                    wv.loadUrl(PAGE_URL)
                    cont.invokeOnCancellation { finish() }
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw TurnstileException("安全验证超时，请检查网络后重试")
    }

    fun finish() {
        finished.set(true)
        val wv = webView ?: return
        webView = null
        wv.post {
            wv.loadUrl("about:blank")
            wv.destroy()
        }
    }

    private inner class Bridge(private val cont: kotlinx.coroutines.CancellableContinuation<String>) {
        @JavascriptInterface
        fun onToken(token: String) {
            if (finished.compareAndSet(false, true)) cont.resume(token)
        }

        @JavascriptInterface
        fun onError(message: String) {
            if (finished.compareAndSet(false, true)) cont.resumeWithException(TurnstileException(message))
        }
    }

    companion object {
        const val PAGE_URL = "https://x.182030.xyz/turnstile-app"
        private const val TIMEOUT_MS = 45_000L
    }
}
