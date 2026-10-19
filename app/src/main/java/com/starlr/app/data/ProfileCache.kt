package com.starlr.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 个人主页内存缓存（进程级，不落盘）。
 *
 * 目的：个人主页数据（资料 + 帖子/回复/喜欢第一页）在返回再进入时不必重新拉取，
 * 立即显示上次内容，后台照常刷新覆盖。
 *
 * 生命周期：随进程；登出 / 切换账号时整包清空（ProfileCache.accountKey 与当前
 * 会话账号不一致也会自动视为失效，双保险）。
 */
object ProfileCache {

    /** 缓存与账号绑定：登出后为 "anonymous"，登录为 username。账号不匹配即整体失效。 */
    @Volatile
    private var accountKey: String = "anonymous"

    private data class Entry(
        val user: User?,
        val posts: List<Post>,
        val replies: List<Reply>,
        val likes: List<Post>,
        val postHasMore: Boolean,
        val replyHasMore: Boolean,
        val likeHasMore: Boolean,
        val savedAt: Long,
    )

    /** 缓存条目快照（对外只读） */
    data class Snapshot(
        val user: User?,
        val posts: List<Post>,
        val replies: List<Reply>,
        val likes: List<Post>,
        val postHasMore: Boolean,
        val replyHasMore: Boolean,
        val likeHasMore: Boolean,
    )

    private val entries = HashMap<String, Entry>()

    /** 缓存版本号变更事件（UI 可观察，用于切账号后立即刷新当前页） */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun keyFor(server: String, username: String) = "$server|$username"

    fun get(server: String, username: String, currentAccount: String?): Snapshot? {
        if (currentAccount ?: "anonymous" != accountKey) {
            clear()
            return null
        }
        val e = synchronized(entries) { entries[keyFor(server, username)] } ?: return null
        return Snapshot(e.user, e.posts, e.replies, e.likes, e.postHasMore, e.replyHasMore, e.likeHasMore)
    }

    fun put(
        server: String,
        username: String,
        currentAccount: String?,
        user: User?,
        posts: List<Post>,
        replies: List<Reply>,
        likes: List<Post>,
        postHasMore: Boolean,
        replyHasMore: Boolean,
        likeHasMore: Boolean,
    ) {
        accountKey = currentAccount ?: "anonymous"
        synchronized(entries) {
            if (entries.size > 24) entries.clear() // 简单防膨胀
            entries[keyFor(server, username)] = Entry(
                user, posts, replies, likes, postHasMore, replyHasMore, likeHasMore,
                savedAt = System.currentTimeMillis(),
            )
        }
    }

    /** 登出 / 切换账号时清空全部缓存 */
    fun clear() {
        synchronized(entries) { entries.clear() }
        accountKey = "anonymous"
        _version.value = _version.value + 1
    }
}
