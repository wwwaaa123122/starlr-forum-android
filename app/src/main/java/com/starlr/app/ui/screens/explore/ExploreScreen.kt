package com.starlr.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import com.starlr.app.data.Post
import com.starlr.app.data.StarlrApi
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.TagCount
import com.starlr.app.data.User
import com.starlr.app.ui.components.Avatar
import com.starlr.app.ui.components.PostCard
import com.starlr.app.ui.components.PostRowPlaceholder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class ExploreState(
    val query: String = "",
    val searching: Boolean = false,
    val posts: List<Post> = emptyList(),
    val users: List<User> = emptyList(),
    val hotTags: List<TagCount> = emptyList(),
    val activeTag: String? = null,
    val error: String? = null,
    val done: Boolean = false,
)

/** 探索页 VM：防抖搜索 + 热门标签 */
@OptIn(FlowPreview::class)
class ExploreViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state

    private val queryFlow = MutableStateFlow("")

    init {
        loadHotTags()
        viewModelScope.launch {
            queryFlow.debounce(400).collect { q -> if (q.isNotBlank()) doSearch(q) }
        }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q, activeTag = null)
        queryFlow.value = q
    }

    fun loadHotTags() {
        viewModelScope.launch {
            runCatching { repo.api.hotTags().tags }
                .onSuccess { _state.value = _state.value.copy(hotTags = it.take(12)) }
        }
    }

    fun searchTag(tag: String) {
        _state.value = _state.value.copy(activeTag = tag, query = "#$tag")
        doSearch(tag, isTag = true)
    }

    private fun doSearch(q: String, isTag: Boolean = false) {
        _state.value = _state.value.copy(searching = true, error = null)
        viewModelScope.launch {
            try {
                val posts: List<Post>
                val users: List<User>
                if (isTag) {
                    posts = repo.api.listPosts(tag = q, sort = "hot", pageSize = 30).posts
                    users = emptyList()
                } else {
                    val search = repo.api.search(q = q, type = "post", pageSize = 30)
                    posts = search.posts
                    users = runCatching { repo.api.searchUsers(q).users }.getOrDefault(emptyList())
                }
                _state.value = _state.value.copy(
                    searching = false,
                    posts = posts,
                    users = users,
                    done = true,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    searching = false,
                    error = StarlrRepository.toApiError(e).message,
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(container.repository) as T
    }
}

/**
 * 探索页：搜索框 + 热门标签 + 帖子/用户结果。
 */
@Composable
fun ExploreScreen(
    container: AppContainer,
    initialTag: String,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val vm: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory(container))
    val state by vm.state.collectAsState()
    val loggedIn by container.repository.session.collectAsState()

    LaunchedEffect(initialTag) {
        if (initialTag.isNotBlank()) vm.searchTag(initialTag)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部搜索
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("搜索帖子、用户、#标签", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // 热门标签（无查询时）
            if (state.query.isBlank() && state.hotTags.isNotEmpty()) {
                item {
                    Text(
                        "正在闪耀",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.hotTags.take(8).forEach { tag ->
                            Text(
                                "#${tag.name} ${tag.count}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                                    .clickable { vm.searchTag(tag.name) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            // 用户结果
            if (state.users.isNotEmpty()) {
                item {
                    Text(
                        "用户",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(state.users, key = { "u_${it.username}" }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUser(user.username) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(url = user.avatarUrl, name = user.username, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user.nickname?.takeIf { it.isNotBlank() } ?: user.username, fontWeight = FontWeight.Bold)
                            Text("@${user.username}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 帖子结果
            if (state.searching) {
                items(4) { PostRowPlaceholder() }
            } else if (state.posts.isNotEmpty()) {
                items(state.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        baseUrl = com.starlr.app.ui.BuildConfigAware.baseUrl,
                        onOpen = { onOpenPost(post.id) },
                        onLike = null,
                        onFavorite = null,
                        onAuthorClick = { onOpenUser(post.authorName) },
                    )
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            } else if (state.query.isNotBlank() && !state.searching) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("没有找到相关内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
            } else if (state.error != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
