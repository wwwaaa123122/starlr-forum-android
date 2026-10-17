package com.starlr.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Starlr API DTO —— 与 backend/src/routes 返回结构一致。
 * 后端数值字段可能是 Int 或 Boolean（isPinned/isFeatured/liked 等），
 * 统一用 flexible 布尔 / 可空字段 + JsonNames 兼容。
 */
@Serializable
data class Pagination(
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
data class Post(
    val id: String,
    val title: String,
    val excerpt: String? = null,
    @JsonNames("isPinned") val isPinned: FlexibleBool? = null,
    @JsonNames("isFeatured") val isFeatured: FlexibleBool? = null,
    val viewCount: Int = 0,
    val replyCount: Int = 0,
    val likeCount: Int = 0,
    val favoriteCount: Int = 0,
    val createdAt: String = "",
    @JsonNames("editedAt") val editedAt: String? = null,
    @JsonNames("lastReplyAt") val lastReplyAt: String? = null,
    val authorId: String = "",
    @JsonNames("authorName") val authorName: String = "",
    @JsonNames("authorNickname") val authorNickname: String? = null,
    @JsonNames("authorAvatar") val authorAvatar: String? = null,
    @JsonNames("authorUid") val authorUid: Long? = null,
    @JsonNames("authorRole") val authorRole: String? = null,
    val categoryName: String? = null,
    val categorySlug: String? = null,
    val tags: List<TagValue> = emptyList(),
    // 详情专属
    val content: String? = null,
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val isAuthor: Boolean = false,
    val canModerate: Boolean = false,
    val isDeleted: Boolean = false,
)

/** 标签可能是 string 或 {"name": "..."} —— 自定义反序列化 */
@Serializable(with = TagValueSerializer::class)
data class TagValue(val name: String)

/** 0/1 int 与 true/false bool 的兼容布尔 */
@Serializable(with = FlexibleBoolSerializer::class)
data class FlexibleBool(val value: Boolean) {
    companion object {
        val TRUE = FlexibleBool(true)
        val FALSE = FlexibleBool(false)
    }
}

@Serializable
data class Reply(
    val id: String,
    val content: String,
    @JsonNames("parentReplyId") val parentReplyId: String? = null,
    @JsonNames("replyToUserId") val replyToUserId: String? = null,
    @JsonNames("replyToUserName") val replyToUserName: String? = null,
    val likeCount: Int = 0,
    val liked: FlexibleBool? = null,
    val createdAt: String = "",
    @JsonNames("editedAt") val editedAt: String? = null,
    val authorId: String = "",
    val authorName: String = "",
    @JsonNames("authorNickname") val authorNickname: String? = null,
    @JsonNames("authorAvatar") val authorAvatar: String? = null,
    @JsonNames("authorUid") val authorUid: Long? = null,
    @JsonNames("authorRole") val authorRole: String? = null,
    // 用户主页回复专属
    val postId: String? = null,
    val postTitle: String? = null,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val slug: String,
    val description: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val postCount: Int = 0,
    val postPolicy: String = "everyone",
)

@Serializable
data class User(
    val id: String? = null,
    val uid: Long? = null,
    val username: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val bio: String? = null,
    val role: String? = null,
    val postCount: Int = 0,
    val replyCount: Int = 0,
    val createdAt: String? = null,
    val banned: Boolean = false,
    val emailVerified: Boolean = true,
    val accountStatus: String? = null,
)

@Serializable
data class Notification(
    val id: String,
    val type: String,
    val postId: String? = null,
    val replyId: String? = null,
    val content: String? = null,
    val isRead: FlexibleBool? = null,
    val createdAt: String = "",
    val actorName: String? = null,
    val actorAvatar: String? = null,
    val postTitle: String? = null,
)

@Serializable
data class SessionUser(
    val id: String? = null,
    val uid: Long? = null,
    val username: String = "",
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null,
    val accountStatus: String? = null,
)

/* ---------------- 请求体 ---------------- */

@Serializable
data class LoginRequest(val identifier: String, val password: String, val turnstileToken: String? = null)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val nickname: String? = null,
    val turnstileToken: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String,
    val categoryId: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class CreateReplyRequest(val content: String, val parentReplyId: String? = null)

@Serializable
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class UpdateReplyRequest(val content: String)

@Serializable
data class UpdateMeRequest(val nickname: String? = null, val bio: String? = null, val backgroundUrl: String? = null)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class MarkReadRequest(val ids: List<String>? = null)

/* ---------------- 响应包装 ---------------- */

@Serializable
data class AuthResponse(
    val user: SessionUser? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

@Serializable
data class SiteTicketRedeemRequest(val ticket: String, val challenge: String, val origin: String)

/** 应用登录凭据预交接：先在应用内校验密码，再跳浏览器完成 Turnstile（密码只输一遍） */
@Serializable
data class AppLoginPrepareRequest(
    val mode: String,
    val password: String,
    @JsonNames("identifier") val identifier: String? = null,
    @JsonNames("username") val username: String? = null,
    @JsonNames("email") val email: String? = null,
    @JsonNames("nickname") val nickname: String? = null,
)

@Serializable
data class AppLoginPrepareResponse(
    @JsonNames("preauth") val preauth: String = "",
    @JsonNames("expiresIn") val expiresIn: Int? = null,
    @JsonNames("mode") val mode: String? = null,
    @JsonNames("name") val name: String? = null,
)

@Serializable
data class AppUpdateInfo(
    @JsonNames("versionCode") val versionCode: Int = 0,
    @JsonNames("versionName") val versionName: String? = null,
    @JsonNames("hash") val hash: String? = null,
    @JsonNames("notes") val notes: String? = null,
    @JsonNames("url") val url: String? = null,
)

@Serializable
data class PostListResponse(val posts: List<Post> = emptyList(), val pagination: Pagination = Pagination())

@Serializable
data class ReplyListResponse(val replies: List<Reply> = emptyList(), val pagination: Pagination = Pagination())

@Serializable
data class PostResponse(val post: Post)

@Serializable
data class UserResponse(val user: User)

@Serializable
data class MeResponse(val user: User)

@Serializable
data class CategoryListResponse(val categories: List<Category> = emptyList())

@Serializable
data class NotificationListResponse(
    val notifications: List<Notification> = emptyList(),
    val pagination: Pagination = Pagination(),
    val unread: Int = 0,
)

@Serializable
data class UnreadResponse(val unread: Int = 0)

@Serializable
data class CreatedPostResponse(val id: String)

@Serializable
data class ViewResponse(val viewCount: Int? = null)

@Serializable
data class SimpleOk(val success: Boolean? = null)

@Serializable
data class SiteConfigResponse(
    val siteName: String? = null,
    val turnstileSiteKey: String? = null,
    val registrationEnabled: Boolean = true,
)

/** 统一错误体 */
@Serializable
data class ApiErrorBody(val error: ApiErrorDetail = ApiErrorDetail())

@Serializable
data class ApiErrorDetail(val code: String = "UNKNOWN", val message: String = "请求失败")

@Serializable
data class ReportRequest(
    val targetType: String,
    val targetId: String,
    val reason: String,
    val detail: String = "",
)

@Serializable
data class AdminReport(
    val id: String,
    val targetType: String = "",
    val targetId: String = "",
    val reason: String = "",
    val detail: String? = null,
    val status: String = "pending",
    val resolutionNote: String? = null,
    val createdAt: String = "",
    val handledAt: String? = null,
    val reporterName: String? = null,
    val targetTitle: String? = null,
    val targetContent: String? = null,
    val targetAuthorName: String? = null,
    val targetDeleted: FlexibleBool? = null,
)

@Serializable
data class AdminReportListResponse(
    val reports: List<AdminReport> = emptyList(),
)

@Serializable
data class HandleReportRequest(
    val action: String,
    val note: String = "",
    val days: Int? = null,
)
