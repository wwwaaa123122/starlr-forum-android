package com.starlr.app.ui.screens.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.Post
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.BuildConfigAware
import com.starlr.app.ui.components.EmptyGlyph
import com.starlr.app.ui.components.PostCard
import com.starlr.app.ui.components.PostRowPlaceholder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BookmarksState(
    val loading: Boolean = false,
    val posts: List<Post> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/** 书签 VM：/api/me/favorites 分页 */
class BookmarksViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(BookmarksState())
    val state: StateFlow<BookmarksState> = _state

    fun load(refresh: Boolean = false) {
        val s = _state.value
        if (s.loading || !repo.session.value.loggedIn) return
        if (!refresh && !s.hasMore && s.posts.isNotEmpty()) return
        val page = if (refresh) 1 else s.page + 1
        _state.value = s.copy(loading = true, error = null, posts = if (refresh) emptyList() else s.posts)
        viewModelScope.launch {
            runCatching { repo.api.myFavorites(page = page, pageSize = 20) }
                .onSuccess { res ->
                    val existing = _state.value.posts.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        loading = false,
                        posts = if (page == 1) res.posts else _state.value.posts + res.posts.filterNot { it.id in existing },
                        page = page,
                        hasMore = res.pagination.hasMore,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                }
        }
    }

    fun unfavorite(post: Post) {
        _state.value = _state.value.copy(posts = _state.value.posts.filterNot { it.id == post.id })
        viewModelScope.launch { runCatching { repo.api.unfavoritePost(post.id) } }
    }

    companion object {
        fun factory(appContainer: AppContainer) = viewModelFactory {
            initializer { BookmarksViewModel(appContainer.repository) }
        }
    }
}

/** 我的书签（X Bookmarks 形态） */
@Composable
fun BookmarksScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val vm: BookmarksViewModel = viewModel(factory = BookmarksViewModel.factory(container))
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load(refresh = true) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("书签", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    baseUrl = BuildConfigAware.baseUrl,
                    favorited = true,
                    onOpen = { onOpenPost(post.id) },
                    onFavorite = { vm.unfavorite(post) },
                    onAuthorClick = { onOpenUser(post.authorName) },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }

            if (state.posts.isEmpty() && state.loading) {
                items(4) { PostRowPlaceholder() }
            } else if (state.posts.isEmpty() && !state.loading) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyGlyph("★")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error ?: "还没有收藏的星迹",
                            color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.hasMore) {
                item {
                    TextButton(
                        onClick = { vm.load() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text(if (state.loading) "加载中…" else "加载更多") }
                }
            }

            if (state.posts.isNotEmpty() && state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
