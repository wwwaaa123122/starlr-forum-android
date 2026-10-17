package com.starlr.app.data

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Starlr REST API（backend/src/routes 的一一对应） */
interface StarlrApi {

    /* ---------------- 公共 ---------------- */

    @GET("api/config")
    suspend fun siteConfig(): SiteConfigResponse

    @GET("api/categories")
    suspend fun listCategories(): CategoryListResponse

    /* ---------------- 认证 /api/auth ---------------- */

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    /* ---------------- 帖子 /api/posts ---------------- */

    @GET("api/posts")
    suspend fun listPosts(
        @Query("sort") sort: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("author") author: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PostListResponse

    @GET("api/posts/tags")
    suspend fun hotTags(): TagListResponse

    @POST("api/auth/site-login/redeem")
    suspend fun redeemSiteTicket(@Body body: SiteTicketRedeemRequest): AuthResponse

    @POST("api/auth/app-login/prepare")
    suspend fun prepareAppLogin(@Body body: AppLoginPrepareRequest): AppLoginPrepareResponse

    @GET("api/app/latest")
    suspend fun appLatest(): AppUpdateInfo

    @GET("api/posts/{id}")
    suspend fun getPost(@Path("id") id: String): PostResponse

    @POST("api/posts")
    suspend fun createPost(@Body body: CreatePostRequest): CreatedPostResponse

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: String)

    @POST("api/posts/{id}/view")
    suspend fun markView(@Path("id") id: String): ViewResponse

    @POST("api/posts/{id}/like")
    suspend fun likePost(@Path("id") id: String)

    @DELETE("api/posts/{id}/like")
    suspend fun unlikePost(@Path("id") id: String)

    @POST("api/posts/{id}/favorite")
    suspend fun favoritePost(@Path("id") id: String)

    @DELETE("api/posts/{id}/favorite")
    suspend fun unfavoritePost(@Path("id") id: String)

    @GET("api/posts/{id}/replies")
    suspend fun listReplies(
        @Path("id") postId: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): ReplyListResponse

    @POST("api/posts/{id}/replies")
    suspend fun createReply(@Path("id") postId: String, @Body body: CreateReplyRequest)

    @POST("api/replies/{id}/like")
    suspend fun likeReply(@Path("id") id: String)

    @DELETE("api/replies/{id}/like")
    suspend fun unlikeReply(@Path("id") id: String)

    @DELETE("api/replies/{id}")
    suspend fun deleteReply(@Path("id") id: String)

    /* ---------------- 用户 /api/users ---------------- */

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") q: String): UserSearchResponse

    @GET("api/users/{username}")
    suspend fun getUser(@Path("username") username: String): UserResponse

    @GET("api/users/{username}/posts")
    suspend fun getUserPosts(
        @Path("username") username: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PostListResponse

    @GET("api/users/{username}/replies")
    suspend fun getUserReplies(
        @Path("username") username: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): ReplyListResponse

    /* ---------------- 我 /api/me ---------------- */

    @GET("api/me/favorites")
    suspend fun myFavorites(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PostListResponse

    @GET("api/me/likes")
    suspend fun myLikes(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PostListResponse

    @PATCH("api/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): MeResponse

    @PATCH("api/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    /** 编辑帖子（作者或版主）：PATCH api/posts/{id} */
    @PATCH("api/posts/{id}")
    suspend fun updatePost(@Path("id") id: String, @Body body: UpdatePostRequest)

    /** 编辑回复：PATCH api/replies/{id} */
    @PATCH("api/replies/{id}")
    suspend fun updateReply(@Path("id") id: String, @Body body: UpdateReplyRequest)

    /** 图片上传：purpose=avatar（头像）| post | reply | background（主页背景） */
    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: okhttp3.RequestBody,
    ): UploadResponse

    /* ---------------- 通知 /api/notifications ---------------- */

    @GET("api/notifications")
    suspend fun listNotifications(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): NotificationListResponse

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): UnreadResponse

    @POST("api/notifications/read")
    suspend fun markRead(@Body body: MarkReadRequest)

    @POST("api/notifications/read-all")
    suspend fun markAllRead()

    /* ---------------- 搜索 /api/search ---------------- */

    @GET("api/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("type") type: String = "post",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): SearchResponse
}

@Serializable
data class TagListResponse(val tags: List<TagCount> = emptyList())

@Serializable
data class TagCount(val name: String, val count: Int = 0)

@Serializable
data class UserSearchResponse(val users: List<User> = emptyList())

@Serializable
data class SearchResponse(
    val posts: List<Post> = emptyList(),
    val users: List<User> = emptyList(),
    val total: Int = 0,
    val pagination: Pagination = Pagination(),
)
