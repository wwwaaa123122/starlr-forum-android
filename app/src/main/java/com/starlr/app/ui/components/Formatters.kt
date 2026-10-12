package com.starlr.app.ui.components

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 数字缩写：1200 → 1.2K，1200000 → 1.2M */
fun formatCount(n: Int?): String {
    val v = n ?: 0
    return when {
        v < 1000 -> v.toString()
        v < 1_000_000 -> {
            val k = v / 1000.0
            if (k >= 100) "${Math.round(k)}K" else String.format(Locale.US, "%.1fK", k)
        }
        else -> {
            val m = v / 1_000_000.0
            if (m >= 100) "${Math.round(m)}M" else String.format(Locale.US, "%.1fM", m)
        }
    }
}

/** 解析后端时间戳：`2026-10-06 07:00:10`（UTC，无 T/Z） */
private fun parseUtc(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val normalized = raw.trim()
        .replace(' ', 'T')
        .let { if (it.endsWith("Z") || it.contains('+')) it else "${it}Z" }
    return runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(normalized)?.time
    }.getOrNull()
}

/** 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 昨天 / 3天前 / 3月12日 / 2024年3月12日 */
fun relativeTime(raw: String?): String {
    val ts = parseUtc(raw) ?: return raw.orEmpty()
    val diff = System.currentTimeMillis() - ts
    val sec = diff / 1000
    if (sec < 60) return "刚刚"
    val min = sec / 60
    if (min < 60) return "$min 分钟前"
    val hour = min / 60
    if (hour < 24) return "$hour 小时前"
    val day = hour / 24
    if (day == 1L) return "昨天"
    if (day < 7) return "$day 天前"

    val date = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    val md = "${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日"
    return if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR)) md
    else "${date.get(Calendar.YEAR)}年$md"
}

/** 加入时间：2024年3月 */
fun joinedMonth(raw: String?): String {
    val ts = parseUtc(raw) ?: return ""
    val date = Calendar.getInstance().apply { timeInMillis = ts }
    return "${date.get(Calendar.YEAR)}年${date.get(Calendar.MONTH) + 1}月"
}

/** 完整时间：2024年3月12日 14:30 */
fun fullTime(raw: String?): String {
    val ts = parseUtc(raw) ?: return raw.orEmpty()
    val date = Calendar.getInstance().apply { timeInMillis = ts }
    return "%d年%d月%d日 %02d:%02d".format(
        date.get(Calendar.YEAR),
        date.get(Calendar.MONTH) + 1,
        date.get(Calendar.DAY_OF_MONTH),
        date.get(Calendar.HOUR_OF_DAY),
        date.get(Calendar.MINUTE),
    )
}

/** 去除 Markdown 语法得到纯文本摘要 */
fun stripMarkdown(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return text
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~>]{1,3}"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** 展示名：昵称优先 */
fun displayName(nickname: String?, username: String?): String =
    nickname?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: "匿名"

/** 相对路径拼接基址（后端返回 /api/...、/uploads/... 等相对地址时使用） */
fun mediaUrl(raw: String?, baseUrl: String): String? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("http")) raw else baseUrl.trimEnd('/') + (if (raw.startsWith("/")) raw else "/$raw")
}

/** 头像地址：优先返回值本身，否则按 UID 回退到默认头像 */
fun avatarUrl(raw: String?, uid: Long?, baseUrl: String): String? =
    mediaUrl(raw, baseUrl) ?: uid?.let { "${baseUrl.trimEnd('/')}/api/avatars/$it" }

/** 角色文案 */
fun roleLabel(role: String?): String? = when (role) {
    "admin" -> "站长"
    "moderator" -> "版主"
    else -> null
}
