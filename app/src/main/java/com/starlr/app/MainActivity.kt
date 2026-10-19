package com.starlr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.starlr.app.data.AppUpdateInfo
import com.starlr.app.ui.StarlrApp
import com.starlr.app.ui.theme.StarlrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var updateInfo by mutableStateOf<AppUpdateInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as StarlrApplication).container
        handleDeepLink(intent, container)
        checkForUpdate(container)
        setContent {
            StarlrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    StarlrApp(container = container)
                }
                updateInfo?.let { info ->
                    UpdateDialog(
                        info = info,
                        container = container,
                        onDismiss = { updateInfo = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent, (application as StarlrApplication).container)
    }

    private fun checkForUpdate(container: AppContainer) {
        lifecycleScope.launch {
            runCatching { container.repository.api.appLatest() }.onSuccess { info ->
                if (info.versionCode > BuildConfig.VERSION_CODE && info.versionCode != container.tokenStore.skippedUpdateVersion) {
                    updateInfo = info
                }
            }
        }
    }

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

@Composable
private fun UpdateDialog(info: AppUpdateInfo, container: AppContainer, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本" + (info.versionName?.takeIf { it.isNotEmpty() }?.let { " v$it" } ?: "")) },
        text = {
            // notes 为空时不硬编码营销文案，给中性兜底说明
            val notes = info.notes?.takeIf { it.isNotEmpty() }
            Text(notes ?: "新版本已发布，包含稳定性与体验改进。可在应用内查看更新内容。")
        },
        confirmButton = {
            TextButton(onClick = {
                val url = info.url
                if (!url.isNullOrEmpty()) {
                    runCatching {
                        container.appContext.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                onDismiss()
            }) { Text("更新") }
        },
        dismissButton = {
            TextButton(onClick = {
                container.tokenStore.skippedUpdateVersion = info.versionCode
                onDismiss()
            }) { Text("跳过此版本") }
        },
    )
}
