package com.starlr.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellableContinuation
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
 *
 * WebView 在构造时（UI 线程）立即创建，保证 challenge 进入组合时一定可用。
 */
class TurnstileGate(context: Context) {

    private val finished = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Bridge(), "StarlrAndroid")
    }

    @Volatile
    private var continuation: CancellableContinuation<String>? = null

    suspend fun fetchToken(): String = try {
        withTimeout(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                webView.loadUrl(PAGE_URL)
                suspendCancellableCoroutine { cont ->
                    continuation = cont
                    cont.invokeOnCancellation { finish() }
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw TurnstileException("安全验证超时，请检查网络后重试")
    }

    fun finish() {
        if (finished.getAndSet(true)) return
        continuation = null
        webView.post {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onToken(token: String) {
            val c = continuation ?: return
            if (finished.compareAndSet(false, true)) {
                continuation = null
                c.resume(token)
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            val c = continuation ?: return
            if (finished.compareAndSet(false, true)) {
                continuation = null
                c.resumeWithException(TurnstileException(message))
            }
        }
    }

    companion object {
        const val PAGE_URL = "https://x.182030.xyz/turnstile-app"
        private const val TIMEOUT_MS = 45_000L
    }
}
