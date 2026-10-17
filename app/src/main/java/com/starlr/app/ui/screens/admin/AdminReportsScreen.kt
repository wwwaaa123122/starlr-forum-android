package com.starlr.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlr.app.AppContainer
import com.starlr.app.data.AdminReport
import com.starlr.app.data.HandleReportRequest
import com.starlr.app.ui.components.relativeTime

private val REASON_LABELS = mapOf(
    "spam" to "垃圾广告",
    "abuse" to "人身攻击",
    "porn" to "色情低俗",
    "illegal" to "违法信息",
    "other" to "其他",
)

/** 举报处理页（版主/管理员）：pending 列表 + 处理对话框（删内容/封禁/忽略）。JWT 经 AuthInterceptor 自动注入，后端二次鉴权 */
@Composable
fun AdminReportsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val api = container.repository.api
    var loading by remember { mutableStateOf(true) }
    var reports by remember { mutableStateOf<List<AdminReport>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var handling by remember { mutableStateOf<AdminReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("pending") }

    suspend fun load(status: String) {
        loading = true
        error = null
        val res = runCatching { api.adminReports(status = status) }
        res.onSuccess { reports = it.reports; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    LaunchedEffect(tab) { load(tab) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("举报处理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 状态 tab
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending" to "待处理", "resolved" to "已处理", "dismissed" to "已驳回").forEach { (value, label) ->
                val selected = tab == value
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { tab = value }
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        when {
            loading -> Column(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            error != null -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { tab = tab /* 重载 */ }) { Text("重试") }
            }

            reports.isEmpty() -> Text(
                if (tab == "pending") "没有待处理的举报" else "暂无记录",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(reports, key = { it.id }) { r ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (r.targetType == "post" && tab == "pending") onOpenPost(r.targetId)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (r.targetType) {
                                    "post" -> "帖子"
                                    "reply" -> "回复"
                                    "user" -> "用户"
                                    else -> r.targetType
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                REASON_LABELS[r.reason] ?: r.reason,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                relativeTime(r.createdAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "举报人：${r.reporterName ?: "未知"}" + (r.targetAuthorName?.let { " · 被举报者：$it" } ?: ""),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        r.targetTitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        r.detail?.takeIf { it.isNotBlank() }?.let {
                            Text("补充：$it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        r.resolutionNote?.takeIf { it.isNotBlank() }?.let {
                            Text("处理备注：$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (tab == "pending") {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { handling = r }) { Text("处理") }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // 处理对话框
    handling?.let { r ->
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var action by remember { mutableStateOf("dismiss") }
        var note by remember { mutableStateOf("") }
        var daysText by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!submitting) handling = null },
            title = { Text("处理举报", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("对被举报内容的处理动作", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        "dismiss" to "无违规，仅记录",
                        "delete_content" to "删除被举报内容",
                        "mute" to "禁言作者（需填天数）",
                        "ban" to "封禁作者",
                    ).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { action = value }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                        ) {
                            RadioButton(selected = action == value, onClick = { action = value })
                            Text(label, fontSize = 15.sp)
                        }
                    }
                    if (action == "mute") {
                        androidx.compose.material3.OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("禁言天数") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("处理备注（可选）") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && (action != "mute" || daysText.toIntOrNull() in 1..3650),
                    onClick = {
                        submitting = true
                        val req = HandleReportRequest(
                            action = action,
                            note = note.trim(),
                            days = if (action == "mute") daysText.toIntOrNull() else null,
                        )
                        // 无违规走 dismiss，其余走 resolve（封禁/删除内容）
                        scope.launch {
                            val job = if (action == "dismiss") runCatching { api.dismissReport(r.id, req) } else runCatching { api.resolveReport(r.id, req) }
                            submitting = false
                            handling = null
                            message = job.fold(
                                onSuccess = { "已处理" },
                                onFailure = { "处理失败：${it.message}" },
                            )
                        }
                    },
                ) { if (submitting) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp)) else Text("确认") }
            },
            dismissButton = { TextButton(onClick = { handling = null }, enabled = !submitting) { Text("取消") } },
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("举报处理") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好的") } },
        )
    }
}
