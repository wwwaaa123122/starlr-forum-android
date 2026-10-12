package com.starlr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.starlr.app.ui.StarlrApp
import com.starlr.app.ui.theme.StarlrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as StarlrApplication).container
        handleDeepLink(intent, container)
        setContent {
            StarlrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    StarlrApp(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent, (application as StarlrApplication).container)
    }

    /** 浏览器 SSO 回跳：starlr://auth?ticket=…&challenge=… → 兑换会话 */
    private fun handleDeepLink(intent: Intent?, container: AppContainer) {
        val data = intent?.data ?: return
        if (data.scheme != "starlr" || data.host != "auth") return
        val ticket = data.getQueryParameter("ticket") ?: return
        val challenge = data.getQueryParameter("challenge") ?: return
        lifecycleScope.launch {
            runCatching { container.repository.exchangeSiteTicket(ticket, challenge) }
        }
    }
}
