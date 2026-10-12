package com.starlr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlr.app.data.Post
import com.starlr.app.data.tagNames
import com.starlr.app.ui.theme.SlLike
import com.starlr.app.ui.theme.SlStar

/**
 * 信息流帖子卡片 —— X PostCard 的论坛适配：
 * 头像 + 昵称/@用户名·时间 → 置顶/精华标记 → 标题 → 摘要（最多 3 行）→ 分类与标签 → 操作条。
 */
@Composable
fun PostCard(
    post: Post,
    baseUrl: String,
    liked: Boolean = false,
    favorited: Boolean = false,
    showCategory: Boolean = true,
    onOpen: () -> Unit,
    onLike: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onCategoryClick: ((String) -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row {
            Avatar(
                url = avatarUrl(post.authorAvatar, post.authorUid, baseUrl),
                name = (post.authorNickname?.takeIf { it.isNotBlank() } ?: post.authorName).ifBlank { "用" },
                size = 40.dp,
                modifier = Modifier.clickable(enabled = onAuthorClick != null) { onAuthorClick?.invoke() },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                // 头部：昵称（无则用户名）· @用户名 + 角色徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (post.authorNickname?.takeIf { it.isNotBlank() } ?: post.authorName).ifBlank { "匿名" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable(enabled = onAuthorClick != null) { onAuthorClick?.invoke() },
                    )
                    if (post.authorRole == "admin" || post.authorRole == "moderator") {
                        Spacer(Modifier.width(6.dp))
                        RoleBadge(post.authorRole)
                    }
                    Text(
                        text = " · ${relativeTime(post.createdAt)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                // 置顶 / 精华
                val pinned = post.isPinned?.value == true
                val featured = post.isFeatured?.value == true
                if (pinned || featured) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (pinned) Text("置顶", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (featured) Text("精华", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlStar)
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = post.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val excerpt = stripMarkdown(post.excerpt)
                if (excerpt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = excerpt,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 分类 + 标签
                val tags = post.tagNames.take(3)
                if (showCategory || tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showCategory && !post.categoryName.isNullOrBlank()) {
                            Text(
                                text = post.categoryName!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = onCategoryClick != null) {
                                        post.categorySlug?.let { onCategoryClick?.invoke(it) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                        tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = onTagClick != null) { onTagClick?.invoke(tag) },
                            )
                        }
                    }
                }

                // 操作条
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionCount(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = "回复",
                        count = post.replyCount,
                        onClick = onOpen,
                    )
                    ActionCount(
                        icon = Icons.Outlined.BarChart,
                        label = "浏览",
                        count = post.viewCount,
                    )
                    ActionCount(
                        icon = if (liked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        label = "点赞",
                        count = post.likeCount,
                        active = liked,
                        activeColor = SlLike,
                        onClick = onLike,
                    )
                    ActionCount(
                        icon = if (favorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = "收藏",
                        count = post.favoriteCount,
                        active = favorited,
                        activeColor = SlStar,
                        onClick = onFavorite,
                    )
                }
            }
        }
    }
}

/** 帖子列表骨架行 */
@Composable
fun PostRowPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
