package com.starlr.app.data

import kotlinx.serialization.Serializable

/** /api/upload 返回 */
@Serializable
data class UploadResponse(
    val url: String? = null,
    val key: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val purpose: String? = null,
    val avatarUpdated: Boolean? = null,
    val quotaRemaining: Int? = null,
)
