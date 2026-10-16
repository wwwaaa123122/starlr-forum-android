package com.starlr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.starlr.app.ui.theme.SlBrand

@Composable
fun ProfileBanner(
    backgroundUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(SlBrand.copy(alpha = 0.75f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
            ),
        ),
    ) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        content()
    }
}
