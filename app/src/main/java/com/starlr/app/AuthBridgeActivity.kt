package com.starlr.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.theme.StarlrTheme
import kotlinx.coroutines.launch
import java.net.URLDecoder

class AuthBridgeActivity : ComponentActivity() {

    private val exchanging = mutableStateOf(false)
    private val exchangeError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            StarlrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                "安全验证",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        AuthWebView(url = url, onReturnUrl = { consumeReturnUrl(it) }, modifier = Modifier.weight(1f))
                        exchangeError.value?.let { err ->
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun consumeReturnUrl(raw: String): Boolean {
        if (exchanging.value) return true
        val parsed = parseReturnUrl(raw) ?: return false
        exchanging.value = true
        val app = application as? StarlrApplication
        if (app == null) {
            finish()
            return true
        }
        lifecycleScope.launch {
            val result = runCatching {
                app.container.repository.exchangeSiteTicket(parsed.first, parsed.second)
            }
            result.fold(
                onSuccess = { finish() },
                onFailure = { e ->
                    exchanging.value = false
                    exchangeError.value = StarlrRepository.toApiError(e).message
                },
            )
        }
        return true
    }

    private fun parseReturnUrl(raw: String): Pair<String, String>? {
        return try {
            if (raw.startsWith("starlr://")) {
                val uri = Uri.parse(raw)
                val ticket = uri.getQueryParameter("ticket")
                val challenge = uri.getQueryParameter("challenge")
                if (!ticket.isNullOrBlank() && !challenge.isNullOrBlank()) ticket to challenge else null
            } else if (raw.startsWith("intent://")) {
                val query = raw.substringAfter('?', "").substringBefore("#Intent")
                val ticket = Regex("ticket=([^&]+)").find(query)?.groupValues?.get(1)
                val challenge = Regex("challenge=([^&]+)").find(query)?.groupValues?.get(1)
                if (ticket != null && challenge != null) {
                    URLDecoder.decode(ticket, "UTF-8") to URLDecoder.decode(challenge, "UTF-8")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthWebView(url: String, onReturnUrl: (String) -> Boolean, modifier: Modifier = Modifier) {
    val checkUrl: (String) -> Boolean = { target ->
        if (target.startsWith("starlr://") || target.startsWith("intent://")) {
            onReturnUrl(target)
            true
        } else {
            false
        }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = settings.userAgentString.replace("; wv)", "")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return checkUrl(request.url.toString())
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return checkUrl(url)
                    }
                }
                loadUrl(url)
            }
        },
    )
}
