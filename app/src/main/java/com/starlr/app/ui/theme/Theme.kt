package com.starlr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = SlBrand,
    onPrimary = Color.White,
    primaryContainer = SlBrandSoft,
    onPrimaryContainer = SlBrand,
    secondary = SlBoost,
    onSecondary = Color.White,
    tertiary = SlStar,
    onTertiary = Color(0xFF241A00),
    background = SlCanvas,
    onBackground = SlInk,
    surface = SlSurface,
    onSurface = SlInk,
    surfaceVariant = SlSurface2,
    onSurfaceVariant = SlMuted,
    outline = SlLineStrong,
    outlineVariant = SlLine,
    error = SlDanger,
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = SlLightBrand,
    onPrimary = Color.White,
    primaryContainer = Color(0x1A6A58F5),
    onPrimaryContainer = SlLightBrand,
    secondary = Color(0xFF0F9F74),
    onSecondary = Color.White,
    tertiary = Color(0xFFD9930D),
    onTertiary = Color.White,
    background = SlLightCanvas,
    onBackground = SlLightInk,
    surface = SlLightSurface,
    onSurface = SlLightInk,
    surfaceVariant = SlLightSurface2,
    onSurfaceVariant = SlLightMuted,
    outline = SlLightLine,
    outlineVariant = SlLightLine,
    error = Color(0xFFE5484D),
    onError = Color.White,
)

private val StarlrTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun StarlrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = StarlrTypography,
        content = content,
    )
}

/** 语义化扩展色（品牌/收藏/点赞/转发） */
object StarlrColors {
    val like = SlLike
    val star = SlStar
    val boost = SlBoost
    val brand = SlBrand

    @Composable
    fun muted() = MaterialTheme.colorScheme.onSurfaceVariant
}
