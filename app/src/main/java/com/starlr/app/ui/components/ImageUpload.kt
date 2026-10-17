package com.starlr.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starlr.app.data.StarlrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图片上传公共逻辑：Photo Picker 选图 → 读 bytes → multipart 上传。
 * onUploaded 回调带相对 url（如 /api/files/... 或 /api/avatars/1?v=...）。
 */
object ImageUpload {
    /** 可上传的 MIME（与后端 ALLOWED 一致） */
    fun isSupported(mime: String?): Boolean =
        mime in setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    /** 读取 Uri 全部字节（IO 线程调用） */
    fun readBytes(context: android.content.Context, uri: Uri): Pair<ByteArray, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取所选图片")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        return bytes to mime
    }
}

/**
 * 记住一个「选图并上传」的 launcher。
 * maxSizeMB：avatar=2，其余=5（与后端限制一致）。
 * purpose：avatar | post | reply | background。
 */
@Composable
fun rememberImageUploadLauncher(
    repo: StarlrRepository,
    purpose: String,
    maxSizeMB: Int = 5,
    onError: (String) -> Unit,
    onUploaded: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val (bytes, mime) = withContext(Dispatchers.IO) { ImageUpload.readBytes(context, uri) }
                if (!ImageUpload.isSupported(mime)) {
                    onError("仅支持 JPG/PNG/GIF/WEBP 图片")
                    return@launch
                }
                if (bytes.size > maxSizeMB * 1024 * 1024) {
                    onError("图片不能超过 ${maxSizeMB}MB")
                    return@launch
                }
                val url = repo.uploadImage(bytes, mime, purpose)
                onUploaded(url)
            } catch (e: Exception) {
                onError(e.message ?: "上传失败")
            } finally {
                uploading = false
            }
        }
    }

    return {
        if (!uploading) {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** 上传中的圆形进度遮罩小部件 */
@Composable
fun UploadingIndicator(size: Dp = 18.dp) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(size), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
    }
}
