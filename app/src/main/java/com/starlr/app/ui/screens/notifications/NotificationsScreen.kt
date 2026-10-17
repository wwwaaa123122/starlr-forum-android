package com.starlr.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.Notification
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.components.Avatar
import com.starlr.app.ui.components.EmptyGlyph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NotificationsState(
    val loading: Boolean = false,
    val items: List<Notification> = emptyList(),
    val unread: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** 通知列表 VM：分页 + 全部已读 */
class NotificationsViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state

    fun load(refresh: Boolean = false) {
        val s = _state.value
        if (s.loading || !repo.session.value.loggedIn) return
        val page = if (refresh) 1 else s.page + 1
        _state.value = s.copy(loading = true, error = null, items = if (refresh) emptyList() else s.items)
        viewModelScope.launch {
            runCatching { repo.api.listNotifications(page = page, pageSize = 20) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        items = if (page == 1) res.notifications else _state.value.items + res.notifications,
                        unread = res.unread,
                        page = page,
                        hasMore = res.pagination.hasMore,
                        loading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = StarlrRepository.toApiError(e).message)
                }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { repo.api.markAllRead() }.onSuccess {
                _state.value = _state.value.copy(
                    items = _state.value.items.map { it.copy(isRead = com.starlr.app.data.FlexibleBool.TRUE) },
                    unread = 0,
                )
                repo.refreshUnread()
            }
        }
    }

    fun markRead(id: String, onChanged: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.api.markRead(com.starlr.app.data.MarkReadRequest(ids = listOf(id))) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        items = _state.value.items.map {
                            if (it.id == id) it.copy(isRead = com.starlr.app.data.FlexibleBool.TRUE) else it
                        },
                    )
                    repo.refreshUnread()
                    onChanged()
                }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsViewModel(container.repository) as T
    }
}

/**
 * 通知页：类型图标 + 执行者 + 内容 + 帖子标题，未读高亮，全部已读。
 */
@Composable
fun NotificationsScreen(
    container: AppContainer,
    loggedIn: Boolean,
    onOpenPost: (String) -> Unit,
    onLogin: () -> Unit,
    onUnreadChanged: () -> Unit,
) {
    val vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory(container))
    val state by vm.state.collectAsState()

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            vm.load(refresh = true)
        }
    }

    if (!loggedIn) {
        // 未登录占位
        Column(
            Modifier.fillMaxSize().padding(vertical = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyGlyph("🔔")
            Spacer(Modifier.height(12.dp))
            Text("登录后查看你的星光通知", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLogin) { Text("去登录") }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("通知", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (state.unread > 0) {
                TextButton(onClick = { vm.markAllRead(); onUnreadChanged() }) { Text("全部已读") }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.items, key = { it.id }) { n ->
                NotificationRow(notification = n, onClick = {
                    if (n.isRead?.value != true) {
                        vm.markRead(n.id, onChanged = onUnreadChanged)
                    }
                    val target = n.postId
                    if (!target.isNullOrBlank()) onOpenPost(target)
                })
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }

            if (state.items.isEmpty() && state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (state.items.isEmpty() && !state.loading) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyGlyph("✦")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error ?: "还没有通知，当有人与你互动时星光会在这里闪烁",
                            color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        if (state.error != null) {
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { vm.load(refresh = true) }) { Text("重试") }
                        }
                    }
                }
            }

            if (state.hasMore && state.items.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { vm.load() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text(if (state.loading) "加载中…" else "加载更多") }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    val unread = notification.isRead?.value != true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = when (notification.type) {
                "reply" -> Icons.Filled.ChatBubble
                "mention" -> Icons.Filled.AlternateEmail
                "favorite" -> Icons.Filled.Favorite
                else -> Icons.Filled.Star // like / system / report 等
            },
            contentDescription = notification.type,
            tint = when (notification.type) {
                "reply" -> MaterialTheme.colorScheme.primary
                "mention" -> MaterialTheme.colorScheme.tertiary
                "favorite" -> com.starlr.app.ui.theme.SlLike
                else -> com.starlr.app.ui.theme.SlStar
            },
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.actorName ?: "系统",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (notification.type) {
                        "reply" -> "回复了你"
                        "mention" -> "提及了你"
                        "favorite" -> "收藏了你的帖子"
                        else -> "赞了你"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = com.starlr.app.ui.components.relativeTime(notification.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            notification.content?.let {
                Text(it, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            notification.postTitle?.let {
                Text(
                    "「$it」",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (unread) {
            Box(
                Modifier
                    .padding(start = 8.dp, top = 6.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}
