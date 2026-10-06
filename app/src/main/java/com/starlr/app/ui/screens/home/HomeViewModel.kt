package com.starlr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.Post
import com.starlr.app.data.StarlrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 首页时间线状态 */
data class FeedState(
    val sort: String = "new",
    val posts: List<Post> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** 乐观互动覆盖：id -> 是否已赞 / 已收藏 */
    val likedOverride: Map<String, Boolean> = emptyMap(),
    val favedOverride: Map<String, Boolean> = emptyMap(),
) {
    fun isLiked(post: Post): Boolean = likedOverride[post.id] ?: post.liked

    fun isFaved(post: Post): Boolean = favedOverride[post.id] ?: post.favorited

    /** 展示用点赞数（叠加乐观增量） */
    fun likeCount(post: Post): Int {
        val override = likedOverride[post.id] ?: return post.likeCount
        return when {
            override && !post.liked -> post.likeCount + 1
            !override && post.liked -> (post.likeCount - 1).coerceAtLeast(0)
            else -> post.likeCount
        }
    }

    fun favoriteCount(post: Post): Int {
        val override = favedOverride[post.id] ?: return post.favoriteCount
        return when {
            override && !post.favorited -> post.favoriteCount + 1
            !override && post.favorited -> (post.favoriteCount - 1).coerceAtLeast(0)
            else -> post.favoriteCount
        }
    }
}

/**
 * 首页时间线（最新 / 热门）：追加分页 + 下拉刷新 + 乐观点赞收藏，
 * 与 Web 端 useInfiniteQuery 行为一致。
 */
class HomeViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state

    init {
        load("new", refresh = true)
    }

    fun load(sort: String, refresh: Boolean = false) {
        val s = _state.value
        if (s.loading) return
        val switching = sort != s.sort
        val page = if (refresh || switching) 1 else s.page + 1
        val base = if (refresh || switching) emptyList() else s.posts

        _state.value = s.copy(
            sort = sort,
            loading = true,
            refreshing = refresh,
            error = null,
            posts = base,
        )

        viewModelScope.launch {
            runCatching { repo.api.listPosts(sort = sort, page = page, pageSize = 20) }
                .onSuccess { res ->
                    val merged = if (page == 1) {
                        res.posts
                    } else {
                        val existing = _state.value.posts.map { it.id }.toSet()
                        _state.value.posts + res.posts.filterNot { it.id in existing }
                    }
                    _state.value = _state.value.copy(
                        posts = merged,
                        page = page,
                        hasMore = res.pagination.hasMore,
                        loading = false,
                        refreshing = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        load(s.sort)
    }

    fun like(post: Post) {
        if (!repo.session.value.loggedIn) return
        val current = _state.value.isLiked(post)
        _state.value = _state.value.copy(likedOverride = _state.value.likedOverride + (post.id to !current))
        viewModelScope.launch {
            val result = runCatching { if (current) repo.api.unlikePost(post.id) else repo.api.likePost(post.id) }
            if (result.isFailure) {
                // 回滚
                _state.value = _state.value.copy(likedOverride = _state.value.likedOverride + (post.id to current))
            }
        }
    }

    fun favorite(post: Post) {
        if (!repo.session.value.loggedIn) return
        val current = _state.value.isFaved(post)
        _state.value = _state.value.copy(favedOverride = _state.value.favedOverride + (post.id to !current))
        viewModelScope.launch {
            val result = runCatching { if (current) repo.api.unfavoritePost(post.id) else repo.api.favoritePost(post.id) }
            if (result.isFailure) {
                _state.value = _state.value.copy(favedOverride = _state.value.favedOverride + (post.id to current))
            }
        }
    }

    companion object {
        fun factory(appContainer: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(appContainer.repository) }
        }
    }
}
