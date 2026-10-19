package com.starlr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlr.app.ui.theme.SlBoost

/**
 * 操作条按钮（X 式）：18dp 图标 + 计数文字，计数为 0 时隐藏，激活时整组着色。
 */
@Composable
fun ActionCount(
    icon: ImageVector,
    label: String,
    count: Int? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    active: Boolean = false,
    activeColor: Color = tint,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            // 最小命中区 40×40dp（视觉紧凑、命中区补足；48dp 会破坏操作条排版密度）
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) activeColor else tint,
            modifier = Modifier.size(18.dp),
        )
        if (count != null && count > 0) {
            Text(
                text = formatCount(count),
                fontSize = 13.sp,
                color = if (active) activeColor else tint,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** 角色徽章：站长 / 版主 */
@Composable
fun RoleBadge(role: String?, uid: Long? = null, modifier: Modifier = Modifier) {
    val label = roleLabel(role, uid) ?: return
    val bg = if (role == "admin") MaterialTheme.colorScheme.primaryContainer else SlBoost.copy(alpha = 0.16f)
    val fg = if (role == "admin") MaterialTheme.colorScheme.primary else SlBoost
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = label, fontSize = 11.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

/** 未读数圆点徽标 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
