package com.starlr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

/**
 * 轻量 Markdown 渲染（Compose 原生，无 WebView）：
 * 支持 标题/加粗/斜体/行内代码/代码块/引用/无序列表/分割线/链接文本化。
 * 安全性：纯文本渲染（AnnotatedString），不解析 HTML，天然免疫 XSS。
 */

private data class Line(val type: Type, val text: String, val level: Int = 0) {
    enum class Type { HEADING, PARA, CODE, QUOTE, LIST_ITEM, HR }
}

private fun parseMarkdown(src: String): List<Line> {
    val lines = mutableListOf<Line>()
    var inCode = false
    val codeBuf = StringBuilder()
    for (raw in src.lines()) {
        if (raw.trimStart().startsWith("```")) {
            if (inCode) {
                lines.add(Line(Line.Type.CODE, codeBuf.toString().trimEnd('\n')))
                codeBuf.clear()
                inCode = false
            } else {
                inCode = true
            }
            continue
        }
        if (inCode) {
            codeBuf.appendLine(raw)
            continue
        }
        val trimmed = raw.trimEnd()
        when {
            trimmed.isBlank() -> Unit
            trimmed.matches(Regex("^(-{3,}|\\*{3,})$")) -> lines.add(Line(Line.Type.HR, ""))
            Regex("^#{1,6}\\s+.*").matches(trimmed) -> {
                val level = trimmed.takeWhile { it == '#' }.length
                lines.add(Line(Line.Type.HEADING, trimmed.dropWhile { it == '#' }.trim(), level))
            }
            trimmed.startsWith("> ") || trimmed == ">" -> lines.add(Line(Line.Type.QUOTE, trimmed.removePrefix("> ")))
            Regex("^[-*+]\\s+.*").matches(trimmed) -> lines.add(Line(Line.Type.LIST_ITEM, trimmed.drop(2).trim()))
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> lines.add(Line(Line.Type.LIST_ITEM, trimmed.substringAfter(". ").trim()))
            else -> lines.add(Line(Line.Type.PARA, trimmed))
        }
    }
    if (inCode && codeBuf.isNotEmpty()) lines.add(Line(Line.Type.CODE, codeBuf.toString().trimEnd('\n')))
    return lines
}

/** 行内格式：**bold** *italic* `code` */
private fun inlineStyled(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var rest = text
    // 简易 tokenizer：按顺序匹配 **..** / *..* / `..`
    val patterns = listOf(
        Regex("\\*\\*(.+?)\\*\\*") to SpanStyle(fontWeight = FontWeight.Bold),
        Regex("(?<!\\*)\\*([^*\\s][^*]*?)\\*(?!\\*)") to SpanStyle(fontStyle = FontStyle.Italic),
        Regex("`([^`]+)`") to SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg),
    )
    while (rest.isNotEmpty()) {
        val first = patterns.mapNotNull { (pattern, style) ->
            pattern.find(rest)?.let { Triple(pattern, style, it) }
        }.minByOrNull { it.third.range.first }
        if (first == null) {
            append(rest)
            break
        }
        val (pattern, style, matchResult) = first
        append(rest.substring(0, matchResult.range.first))
        withStyle(style) { append(matchResult.groupValues[1]) }
        rest = rest.substring(matchResult.range.last + 1)
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val start = length
    block()
    addStyle(style, start, length)
}

/**
 * Markdown 文本块。compact=true 时为回复内的小字号渲染。
 */
@Composable
fun MarkdownText(content: String, compact: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    val lines = remember(content) { parseMarkdown(content) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val bodySize = if (compact) 14.sp else 15.sp
    val bodyLine = if (compact) 20.sp else 23.sp
    var rendered = 0

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            if (rendered >= maxLines) break
            when (line.type) {
                Line.Type.HEADING -> {
                    val size = when {
                        line.level <= 1 -> 20.sp
                        line.level == 2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        inlineStyled(line.text, codeBg),
                        fontSize = size,
                        fontWeight = FontWeight.Black,
                        lineHeight = size * 1.4f,
                    )
                    rendered += 1
                }
                Line.Type.PARA -> {
                    Text(
                        inlineStyled(line.text, codeBg),
                        fontSize = bodySize,
                        lineHeight = bodyLine,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                    )
                    rendered += 1
                }
                Line.Type.CODE -> {
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                    rendered += 1
                }
                Line.Type.QUOTE -> {
                    Row {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            inlineStyled(line.text, codeBg),
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rendered += 1
                }
                Line.Type.LIST_ITEM -> {
                    Row {
                        Text("•", fontSize = bodySize, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(inlineStyled(line.text, codeBg), fontSize = bodySize, lineHeight = bodyLine)
                    }
                    rendered += 1
                }
                Line.Type.HR -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    rendered += 1
                }
            }
        }
    }
}
