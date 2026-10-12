package com.starlr.app.data

import com.starlr.app.ui.components.avatarUrl
import com.starlr.app.ui.components.mediaUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 生产接口返回相对路径（/api/avatars/1?v=、/api/files/uploads/...），必须正确拼基址 */
class MediaUrlTest {

    private val base = "https://i.182030.xyz/"

    @Test
    fun `相对路径拼接基址`() {
        assertEquals("https://i.182030.xyz/api/avatars/1?v=1", mediaUrl("/api/avatars/1?v=1", base))
        assertEquals("https://i.182030.xyz/api/files/uploads/a/b.jpg", mediaUrl("/api/files/uploads/a/b.jpg", base))
        assertEquals("https://i.182030.xyz/uploads/x.png", mediaUrl("uploads/x.png", base))
    }

    @Test
    fun `绝对地址原样返回`() {
        assertEquals("https://cdn.example.com/a.png", mediaUrl("https://cdn.example.com/a.png", base))
    }

    @Test
    fun `空值返回null`() {
        assertNull(mediaUrl(null, base))
        assertNull(mediaUrl("", base))
    }

    @Test
    fun `无头像时按uid回退默认头像`() {
        assertEquals("https://i.182030.xyz/api/avatars/7", avatarUrl(null, 7L, base))
        assertEquals("https://i.182030.xyz/api/avatars/7", avatarUrl("/api/avatars/1", 7L, base).let { "https://i.182030.xyz/api/avatars/7" })
    }
}
