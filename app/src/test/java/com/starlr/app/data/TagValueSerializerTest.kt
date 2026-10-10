package com.starlr.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用生产 API 实际返回形态做回归：任何 tags 形态都不能让整页解码失败 */
class TagValueSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun `字符串标签数组正常解码`() {
        val body = """{"posts":[{"id":"p1","title":"t","tags":["公告","指南"]}],"pagination":{"page":1,"pageSize":20,"total":1,"totalPages":1,"hasMore":false}}"""
        val res = json.decodeFromString(PostListResponse.serializer(), body)
        assertEquals(listOf("公告", "指南"), res.posts[0].tagNames)
    }

    @Test
    fun `对象标签数组正常解码`() {
        val body = """{"posts":[{"id":"p1","title":"t","tags":[{"name":"公告","count":1}]}]}"""
        val res = json.decodeFromString(PostListResponse.serializer(), body)
        assertEquals(listOf("公告"), res.posts[0].tagNames)
    }

    @Test
    fun `tags 为 null 或缺失不崩溃`() {
        val body = """{"posts":[{"id":"p1","title":"t","tags":null},{"id":"p2","title":"t2"}]}"""
        val res = json.decodeFromString(PostListResponse.serializer(), body)
        assertTrue(res.posts.all { it.tagNames.isEmpty() })
    }

    @Test
    fun `生产首页真实响应形态可解码`() {
        val body = """{"posts":[{"id":"yhk2uwvtv97qxxsn","title":"欢迎来到Starlr论坛，请阅读以下社区守则","isPinned":1,"isFeatured":1,"viewCount":32,"replyCount":4,"likeCount":4,"favoriteCount":2,"createdAt":"2026-10-05 12:27:59","tags":["公告"],"authorId":"u1","authorName":"admin"}],"pagination":{"page":1,"pageSize":20,"total":1,"totalPages":1,"hasMore":false}}"""
        val res = json.decodeFromString(PostListResponse.serializer(), body)
        assertEquals(1, res.posts.size)
        assertEquals(listOf("公告"), res.posts[0].tagNames)
    }
}
