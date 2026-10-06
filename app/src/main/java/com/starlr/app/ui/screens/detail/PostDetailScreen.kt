package com.starlr.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.starlr.app.data.CreateReplyRequest
import com.starlr.app.data.Post
import com.starlr.app.data.Reply
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.components.ActionCount
import com.starlr.app.ui.components.Avatar
import com.starlr.app.ui.components.MarkdownText
import com.starlr.app.ui.components.RoleBadge
import com.starlr.app.ui.components.formatCount
import com.starlr.app.ui.components.relativeTime
import com.starlr.app.ui.theme.SlLike
import com.starlr.app.ui.theme.SlStar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailState(
    val loading: Boolean = true,
    val post: Post? = null,
    val replies: List<Reply> = emptyList(),
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val likeCount: Int = 0,
    val favoriteCount: Int = 0,
    val viewCount: Int = 0,
    val error: String? = null,
    val sending: Boolean = false,
    val replyTo: Reply? = null,
)

/** 帖子详情 VM：正文 + 回复树（顶层 + 楼中楼）+ 互动 + 发回复 */
class PostDetailViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private var postId: String = ""

    fun bind(id: String) {
        if (postId == id && _state.value.post != null) return
        postId = id
        refresh()
    }

    fun refresh() {
        if (postId.isBlank()) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val post = repo.api.getPost(postId).post
                val replies = repo.api.listReplies(postId, page = 1, pageSize = 50).replies
                _state.value = _state.value.copy(
                    loading = false,
                    post = post,
                    replies = replies,
                    liked = post.liked,
                    favorited = post.favorited,
                    likeCount = post.likeCount,
                    favoriteCount = post.favoriteCount,
                    viewCount = post.viewCount,
                )
                // 计入浏览（服务端 KV 去重）
                runCatching { repo.api.markView(postId) }
                    .onSuccess { res -> res.viewCount?.let { _state.value = _state.value.copy(viewCount = it) } }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = StarlrRepository.toApiError(e).message,
                )
            }
        }
    }

    fun toggleLike() {
        val s = _state.value
        if (!repo.session.value.loggedIn) return
        val next = !s.liked
        _state.value = s.copy(
            liked = next,
            likeCount = (s.likeCount + if (next) 1 else -1).coerceAtLeast(0),
        )
        viewModelScope.launch {
            val res = runCatching { if (next) repo.api.likePost(postId) else repo.api.unlikePost(postId) }
            if (res.isFailure) {
                _state.value = _state.value.copy(liked = s.liked, likeCount = s.likeCount)
            }
        }
    }

    fun toggleFavorite() {
        val s = _state.value
        if (!repo.session.value.loggedIn) return
        val next = !s.favorited
        _state.value = s.copy(
            favorited = next,
            favoriteCount = (s.favoriteCount + if (next) 1 else -1).coerceAtLeast(0),
        )
        viewModelScope.launch {
            val res = runCatching { if (next) repo.api.favoritePost(postId) else repo.api.unfavoritePost(postId) }
            if (res.isFailure) {
                _state.value = _state.value.copy(favorited = s.favorited, favoriteCount = s.favoriteCount)
            }
        }
    }

    fun likeReply(reply: Reply) {
        if (!repo.session.value.loggedIn) return
        val liked = reply.liked?.value == true
        _state.value = _state.value.copy(
            replies = _state.value.replies.map {
                if (it.id == reply.id) {
                    it.copy(
                        liked = com.starlr.app.data.FlexibleBool(!liked),
                        likeCount = (it.likeCount + if (liked) -1 else 1).coerceAtLeast(0),
                    )
                } else it
            },
        )
        viewModelScope.launch {
            runCatching { if (liked) repo.api.unlikeReply(reply.id) else repo.api.likeReply(reply.id) }
        }
    }

    fun setReplyTo(reply: Reply?) {
        _state.value = _state.value.copy(replyTo = reply)
    }

    fun submitReply(content: String) {
        val s = _state.value
        if (s.sending || content.isBlank()) return
        _state.value = s.copy(sending = true)
        viewModelScope.launch {
            runCatching {
                repo.api.createReply(postId, CreateReplyRequest(content.trim(), s.replyTo?.id))
            }.onSuccess {
                _state.value = _state.value.copy(sending = false, replyTo = null)
                refresh()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    sending = false,
                    error = StarlrRepository.toApiError(e).message,
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PostDetailViewModel(container.repository) as T
    }
}

/**
 * 帖子详情：正文（Markdown 轻量渲染）+ 操作条 + 回复树（顶层列表，楼中楼缩进）+ 底部回复输入。
 */
@Composable
fun PostDetailScreen(
    container: AppContainer,
    postId: String,
    loggedIn: Boolean,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onLogin: () -> Unit,
    onReply: (String) -> Unit,
) {
    val vm: PostDetailViewModel = viewModel(factory = PostDetailViewModel.Factory(container))
    val state by vm.state.collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(postId) { vm.bind(postId) }

    val topLevel = state.replies.filter { it.parentReplyId.isNullOrBlank() }
    val childrenOf: (String) -> List<Reply> = { parentId ->
        state.replies.filter { it.parentReplyId == parentId }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("帖子", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(Modifier.weight(1f)) {
            val post = state.post
            if (post != null) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        // 作者行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(
                                url = com.starlr.app.ui.components.avatarUrl(
                                    post.authorAvatar, post.authorUid, com.starlr.app.ui.BuildConfigAware.baseUrl,
                                ),
                                name = (post.authorNickname?.takeIf { it.isNotBlank() } ?: post.authorName).ifBlank { "星" },
                                size = 44.dp,
                                modifier = Modifier.clickable { onOpenUser(post.authorName) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (post.authorNickname?.takeIf { it.isNotBlank() } ?: post.authorName).ifBlank { "星旅者" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.clickable { onOpenUser(post.authorName) },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    RoleBadge(post.authorRole)
                                }
                                Text(
                                    "· ${relativeTime(post.createdAt)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(post.title, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)

                        // 分类与标签
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            post.categoryName?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                            post.tags.take(5).forEach { tag ->
                                Text("#${tag.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        MarkdownText(post.content.orEmpty())

                        Spacer(Modifier.height(14.dp))
                        // 统计 + 操作
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${formatCount(state.viewCount)} 浏览 · ${formatCount(post.replyCount)} 回复",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ActionCount(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                label = "回复",
                                count = state.replies.size,
                                onClick = { if (loggedIn) onReply(post.id) else onLogin() },
                            )
                            ActionCount(
                                icon = Icons.Outlined.BarChart,
                                label = "浏览",
                                count = state.viewCount,
                            )
                            ActionCount(
                                icon = if (state.liked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                label = "点赞",
                                count = state.likeCount,
                                active = state.liked,
                                activeColor = SlLike,
                                onClick = { if (loggedIn) vm.toggleLike() else onLogin() },
                            )
                            ActionCount(
                                icon = if (state.favorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                label = "收藏",
                                count = state.favoriteCount,
                                active = state.favorited,
                                activeColor = SlStar,
                                onClick = { if (loggedIn) vm.toggleFavorite() else onLogin() },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "全部回复（${state.replies.size}）",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                items(topLevel, key = { it.id }) { reply ->
                    ReplyBlock(
                        reply = reply,
                        children = childrenOf(reply.id),
                        onOpenUser = onOpenUser,
                        onLike = { vm.likeReply(it) },
                        onReplyTo = { vm.setReplyTo(it); if (!loggedIn) onLogin() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (topLevel.isEmpty() && !state.loading) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 54.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有回复，来说点什么吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "帖子不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 底部回复输入栏
        if (loggedIn) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            state.replyTo?.let { "回复 @${it.authorName}" } ?: "写下你的回复…",
                            fontSize = 14.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            vm.submitReply(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !state.sending,
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = "发送回复", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 单条回复 + 楼中楼子回复（缩进一档） */
@Composable
private fun ReplyBlock(
    reply: Reply,
    children: List<Reply>,
    onOpenUser: (String) -> Unit,
    onLike: (Reply) -> Unit,
    onReplyTo: (Reply) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        ReplyRow(reply = reply, onOpenUser = onOpenUser, onLike = onLike, onReplyTo = onReplyTo, indented = false)
        children.forEach { child ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(start = 30.dp)) {
                ReplyRow(reply = child, onOpenUser = onOpenUser, onLike = onLike, onReplyTo = onReplyTo, indented = true)
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: Reply,
    onOpenUser: (String) -> Unit,
    onLike: (Reply) -> Unit,
    onReplyTo: (Reply) -> Unit,
    indented: Boolean,
) {
    val liked = reply.liked?.value == true
    Row(Modifier.fillMaxWidth()) {
        Avatar(
            url = com.starlr.app.ui.components.avatarUrl(
                reply.authorAvatar, reply.authorUid, com.starlr.app.ui.BuildConfigAware.baseUrl,
            ),
            name = reply.authorName.ifBlank { "星" },
            size = if (indented) 28.dp else 36.dp,
            modifier = Modifier.clickable { onOpenUser(reply.authorName) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reply.authorName.ifBlank { "星旅者" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onOpenUser(reply.authorName) },
                )
                Spacer(Modifier.width(6.dp))
                RoleBadge(reply.authorRole)
                reply.replyToUserName?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("回复 @$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    relativeTime(reply.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            MarkdownText(reply.content, compact = true)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionCount(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "回复",
                    onClick = { onReplyTo(reply) },
                )
                Spacer(Modifier.width(8.dp))
                ActionCount(
                    icon = if (liked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    label = "点赞",
                    count = reply.likeCount,
                    active = liked,
                    activeColor = SlLike,
                    onClick = { onLike(reply) },
                )
            }
        }
    }
}
