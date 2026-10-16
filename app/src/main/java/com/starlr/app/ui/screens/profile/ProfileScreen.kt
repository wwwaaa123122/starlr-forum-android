package com.starlr.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.starlr.app.data.Post
import com.starlr.app.data.Reply
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.User
import com.starlr.app.ui.components.Avatar
import com.starlr.app.ui.components.EmptyGlyph
import com.starlr.app.ui.components.PostCard
import com.starlr.app.ui.components.joinedMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class ProfileState(
    val loading: Boolean = false,
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val replies: List<Reply> = emptyList(),
    val postPage: Int = 1,
    val replyPage: Int = 1,
    val postHasMore: Boolean = false,
    val replyHasMore: Boolean = false,
    val error: String? = null,
)

/** 用户资料 VM：资料 + 帖子/回复 Tab 分页 */
class ProfileViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state

    private var target: String? = null
    private var loadedFor: String? = null

    /** 进入页面：绑定目标用户并刷新第一页 */
    fun bind(username: String?) {
        target = username
        refresh()
    }

    fun refresh() {
        val name = target ?: repo.session.value.user?.username ?: return
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val userResult = runCatching { repo.api.getUser(name).user }
            userResult.fold(
                onSuccess = { user ->
                    loadedFor = name
                    _state.value = _state.value.copy(user = user)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                    return@launch
                },
            )
            coroutineScope {
                launch {
                    runCatching { repo.api.getUserPosts(name, page = 1, pageSize = 20) }.onSuccess { res ->
                        _state.value = _state.value.copy(
                            posts = res.posts,
                            postPage = 1,
                            postHasMore = res.pagination.hasMore,
                        )
                    }
                }
                launch {
                    runCatching { repo.api.getUserReplies(name, page = 1, pageSize = 20) }.onSuccess { res ->
                        _state.value = _state.value.copy(
                            replies = res.replies,
                            replyPage = 1,
                            replyHasMore = res.pagination.hasMore,
                        )
                    }
                }
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    /** 加载更多（tab: 0=帖子 1=回复） */
    fun loadMore(tab: Int) {
        val name = loadedFor ?: return
        val s = _state.value
        if (s.loading) return
        val isPosts = tab == 0
        if (isPosts && !s.postHasMore) return
        if (!isPosts && !s.replyHasMore) return
        _state.value = s.copy(loading = true)

        viewModelScope.launch {
            try {
                if (isPosts) {
                    val res = repo.api.getUserPosts(name, page = s.postPage + 1, pageSize = 20)
                    val existing = _state.value.posts.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        posts = _state.value.posts + res.posts.filterNot { it.id in existing },
                        postPage = s.postPage + 1,
                        postHasMore = res.pagination.hasMore,
                        loading = false,
                    )
                } else {
                    val res = repo.api.getUserReplies(name, page = s.replyPage + 1, pageSize = 20)
                    val existing = _state.value.replies.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        replies = _state.value.replies + res.replies.filterNot { it.id in existing },
                        replyPage = s.replyPage + 1,
                        replyHasMore = res.pagination.hasMore,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = StarlrRepository.toApiError(e).message,
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(container.repository) as T
    }
}

/**
 * 用户资料页 —— X Profile 形态：
 * 渐变横幅 + 叠压头像 + 统计（帖子/回复）+ 帖子/回复 Tab 列表。
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    username: String?,
    loggedIn: Boolean,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit = {},
    onBack: (() -> Unit)?,
) {
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(container))
    val state by vm.state.collectAsState()
    val session by container.repository.session.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val target = username ?: session.user?.username
    LaunchedEffect(target) { vm.bind(username) }

    // 未登录访问「我」
    if (username == null && !loggedIn) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyGlyph("✦")
            Spacer(Modifier.height(12.dp))
            Text("登录后查看你的主页", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLogin) { Text("去登录") }
        }
        return
    }

    val user = state.user
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize()) {
            // 沉浸式背景图头部 + 操作按钮
            item {
                Box {
                    com.starlr.app.ui.components.ProfileBanner(
                        backgroundUrl = com.starlr.app.ui.components.mediaUrl(
                            user?.backgroundUrl,
                            com.starlr.app.ui.BuildConfigAware.baseUrl,
                        ),
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.background(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                    if (username == null) {
                        Row(modifier = Modifier.align(Alignment.TopEnd)) {
                            IconButton(
                                onClick = onBookmarks,
                                modifier = Modifier.background(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.BookmarkBorder,
                                    contentDescription = "书签",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                            IconButton(
                                onClick = onSettings,
                                modifier = Modifier.background(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
            }

            // 头像嵌入背景图下缘 + 基本信息
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Box(Modifier.height(42.dp)) {
                        Avatar(
                            url = com.starlr.app.ui.components.avatarUrl(
                                user?.avatarUrl,
                                user?.uid,
                                com.starlr.app.ui.BuildConfigAware.baseUrl,
                            ),
                            name = user?.username ?: "用",
                            size = 84.dp,
                            modifier = Modifier
                                .offset(y = (-42).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .padding(3.dp),
                        )
                    }
                    Text(
                        user?.nickname?.takeIf { it.isNotBlank() } ?: user?.username ?: "",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "@${user?.username}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    user?.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    // 统计：UID / 加入 / 帖子 / 回复
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                        Text("UID ${user?.uid ?: "—"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        user?.createdAt?.let {
                            Text("加入于 ${joinedMonth(it)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${user?.postCount ?: 0}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("帖子", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${user?.replyCount ?: 0}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("回复", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Tab
            item {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[tab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp,
                        )
                    },
                ) {
                    listOf("帖子", "回复").forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }
            }

            // 帖子 / 回复列表
            if (tab == 0) {
                if (state.loading && state.posts.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
                items(state.posts, key = { "p_${it.id}" }) { post ->
                    PostCard(
                        post = post,
                        baseUrl = com.starlr.app.ui.BuildConfigAware.baseUrl,
                        onOpen = { onOpenPost(post.id) },
                        onLike = null,
                        onFavorite = null,
                        onAuthorClick = { onOpenUser(post.authorName) },
                        showCategory = true,
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                if (state.postHasMore) {
                    item {
                        TextButton(onClick = { vm.loadMore(0) }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                    }
                }
            } else {
                if (state.loading && state.replies.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
                items(state.replies, key = { "r_${it.id}" }) { reply ->
                    ReplyRow(reply = reply, onOpenPost = onOpenPost)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                if (state.replyHasMore) {
                    item {
                        TextButton(onClick = { vm.loadMore(1) }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                    }
                }
            }

            if (state.posts.isEmpty() && state.replies.isEmpty() && !state.loading) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyGlyph("✦")
                        Spacer(Modifier.height(10.dp))
                        Text(if (tab == 0) "还没有帖子" else "还没有回复", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 用户主页的回复行（关联所属帖子，点击跳详情） */
@Composable
private fun ReplyRow(reply: Reply, onOpenPost: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !reply.postId.isNullOrBlank()) { reply.postId?.let(onOpenPost) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        reply.postTitle?.let {
            Text(
                "回复：$it",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            com.starlr.app.ui.components.stripMarkdown(reply.content),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            com.starlr.app.ui.components.relativeTime(reply.createdAt),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
