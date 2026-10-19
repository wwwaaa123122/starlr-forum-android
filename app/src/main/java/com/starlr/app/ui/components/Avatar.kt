package com.starlr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 头像：无 URL 或加载失败时显示「首字母 + 姓名哈希渐变」圆底；
 * 图片加载成功后不再垫任何背景，透明区域保持透明。
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val key = name.ifBlank { "用户" }
    val initial = key.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "用"
    var hue by remember(key) { mutableStateOf(hueOf(key)) }
    var imageLoaded by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (imageLoaded) {
                    SolidColor(Color.Transparent)
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.hsl(hue, 0.72f, 0.58f),
                            Color.hsl((hue + 50f) % 360f, 0.70f, 0.46f),
                        ),
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageLoaded) {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    imageLoaded = state is coil.compose.AsyncImagePainter.State.Success
                },
            )
        }
    }
}

private val HUES = listOf(258f, 200f, 330f, 150f, 30f, 90f, 280f, 180f)

private fun hueOf(key: String): Float {
    var h = 0L
    for (c in key) h = h * 31 + c.code
    val idx = ((h % HUES.size) + HUES.size).toInt() % HUES.size
    return HUES[idx]
}

/** 简单的「空数据」占位图形 */
@Composable
fun EmptyGlyph(symbol: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = symbol,
            fontSize = 44.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
