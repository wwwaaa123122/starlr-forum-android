package com.starlr.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 个人主页持久化缓存（SharedPreferences + JSON，重启 App 后仍然有效）。
 *
 * 内容：资料（头像/背景/昵称等文本字段）+ 帖子/回复/喜欢第一页。
 * 与内存缓存 [ProfileCache] 配合：内存层管进程内秒开，本层管重启后秒开。
 *
 * 存储：按 `server|username` 一条 JSON，最多 [MAX_ENTRIES] 条（超出清最早写入的）。
 * 失效：存储的账号标记 ≠ 当前账号|服务器（登出/切换/换服务器）→ 整包清空。
 */
object ProfileDiskCache {

    private const val FILE = "profile_cache"
    private const val KEY_ACCOUNT = "account_key"
    private const val KEY_PREFIX = "entry:"
    private const val KEY_TIME_PREFIX = "time:"
    private const val MAX_ENTRIES = 24

    @Serializable
    private data class EntryDto(
        val user: String,       // User JSON
        val posts: String,      // List<Post> JSON
        val replies: String,    // List<Reply> JSON
        val likes: String,      // List<Post> JSON
        val postHasMore: Boolean,
        val replyHasMore: Boolean,
        val likeHasMore: Boolean,
        val savedAt: Long,
    )

    class Snapshot(
        val user: User?,
        val posts: List<Post>,
        val replies: List<Reply>,
        val likes: List<Post>,
        val postHasMore: Boolean,
        val replyHasMore: Boolean,
        val likeHasMore: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 缓存被清空/切换时递增（UI 可观察，触发当前页刷新） */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun accountKeyOf(repo: StarlrRepository): String =
        "${repo.serverConfig.current}|${repo.session.value.user?.username ?: "anonymous"}"

    private fun entryKey(server: String, username: String) = "$KEY_PREFIX$server|$username"

    /**
     * 读取缓存。存储的账号标记 ≠ 当前账号|服务器（登出/切号/换服务器）时自动整包清空。
     * 结构变更/损坏时静默返回 null，下次刷新覆盖。
     */
    fun get(context: Context, repo: StarlrRepository, username: String): Snapshot? {
        val sp = prefs(context)
        val stored = sp.getString(KEY_ACCOUNT, null)
        val now = accountKeyOf(repo)
        if (stored != now) {
            clear(context, now)
            return null
        }
        val raw = sp.getString(entryKey(repo.serverConfig.current, username), null) ?: return null
        return runCatching {
            val dto = json.decodeFromString(EntryDto.serializer(), raw)
            Snapshot(
                user = json.decodeFromString(User.serializer(), dto.user),
                posts = json.decodeFromString(ListSerializer(Post.serializer()), dto.posts),
                replies = json.decodeFromString(ListSerializer(Reply.serializer()), dto.replies),
                likes = json.decodeFromString(ListSerializer(Post.serializer()), dto.likes),
                postHasMore = dto.postHasMore,
                replyHasMore = dto.replyHasMore,
                likeHasMore = dto.likeHasMore,
            )
        }.getOrNull()
    }

    fun put(
        context: Context,
        repo: StarlrRepository,
        username: String,
        user: User?,
        posts: List<Post>,
        replies: List<Reply>,
        likes: List<Post>,
        postHasMore: Boolean,
        replyHasMore: Boolean,
        likeHasMore: Boolean,
    ) {
        val u = user ?: return
        val sp = prefs(context)
        val e = sp.edit()
        e.putString(KEY_ACCOUNT, accountKeyOf(repo))
        val now = System.currentTimeMillis()
        val dto = EntryDto(
            user = json.encodeToString(User.serializer(), u),
            posts = json.encodeToString(ListSerializer(Post.serializer()), posts),
            replies = json.encodeToString(ListSerializer(Reply.serializer()), replies),
            likes = json.encodeToString(ListSerializer(Post.serializer()), likes),
            postHasMore = postHasMore,
            replyHasMore = replyHasMore,
            likeHasMore = likeHasMore,
            savedAt = now,
        )
        val k = entryKey(repo.serverConfig.current, username)
        e.putString(k, json.encodeToString(EntryDto.serializer(), dto))
        e.putLong("$KEY_TIME_PREFIX$k", now)
        evictOldEntries(sp, e, keepKey = k)
        e.apply()
    }

    /** 登出 / 会话失效时调用：清空全部条目并重置账号标记 */
    fun clear(context: Context?, newAccountKey: String = "anonymous") {
        if (context == null) return
        val e = prefs(context).edit()
        e.clear()
        e.putString(KEY_ACCOUNT, newAccountKey)
        e.apply()
        _version.value = _version.value + 1
    }

    /** 条目超上限时删掉除 [keepKey] 外写入最早的 */
    private fun evictOldEntries(sp: SharedPreferences, e: SharedPreferences.Editor, keepKey: String) {
        val times: Map<String, Long> = sp.all
            .filterKeys { it.startsWith(KEY_TIME_PREFIX) }
            .mapKeys { it.key.removePrefix(KEY_TIME_PREFIX) }
            .mapValues { it.value as? Long ?: 0L }
        if (times.size < MAX_ENTRIES) return
        times.filterKeys { it != keepKey }
            .toList()
            .sortedBy { it.second }
            .take(times.size - MAX_ENTRIES + 1)
            .forEach { (k, _) ->
                e.remove(k)
                e.remove("$KEY_TIME_PREFIX$k")
            }
    }
}
