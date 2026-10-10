package com.starlr.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starlr.app.AppContainer
import com.starlr.app.ui.components.EmptyGlyph
import com.starlr.app.ui.components.PostCard
import com.starlr.app.ui.BuildConfigAware
import com.starlr.app.ui.components.PostRowPlaceholder
import com.starlr.app.ui.theme.SlStar

/**
 * 首页 —— X Home 的时间线形态：
 * 吸顶双 Tab（最新/热门）+ 下拉刷新 + 触底自动加载 + PostCard 流。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onTag: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val session by container.repository.session.collectAsState()
    val loggedIn = session.loggedIn

    // 触底加载更多
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) vm.loadMore()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 吸顶标题 + Tab（X 的 sticky header）
        TabRow(
            selectedTabIndex = if (state.sort == "new") 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[if (state.sort == "new") 0 else 1]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            listOf("最新" to "new", "热门" to "hot").forEachIndexed { index, (label, sort) ->
                Tab(
                    selected = state.sort == sort,
                    onClick = { vm.load(sort, refresh = true) },
                    text = {
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = if (state.sort == sort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(state.sort, refresh = true) },
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        baseUrl = BuildConfigAware.baseUrl,
                        liked = state.isLiked(post),
                        favorited = state.isFaved(post),
                        onOpen = { onOpenPost(post.id) },
                        onLike = { if (loggedIn) vm.like(post) else onLogin() },
                        onFavorite = { if (loggedIn) vm.favorite(post) else onLogin() },
                        onAuthorClick = { onOpenUser(post.authorName) },
                        onTagClick = { onTag(it) },
                        onCategoryClick = { onTag(it) },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                if (state.posts.isEmpty() && state.loading) {
                    items(6) { PostRowPlaceholder() }
                } else if (state.posts.isEmpty() && !state.loading && state.error == null) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyGlyph("✦")
                            Spacer(Modifier.height(12.dp))
                            Text("还没有帖子", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.error != null && state.posts.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyGlyph("☁")
                            Spacer(Modifier.height(12.dp))
                            Text(state.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = if (state.loading) 16.dp else 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else if (!state.hasMore && state.posts.isNotEmpty()) {
                            Text("✦ 已经到底啦", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
