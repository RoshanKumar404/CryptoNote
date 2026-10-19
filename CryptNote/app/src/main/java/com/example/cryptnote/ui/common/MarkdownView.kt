package com.example.cryptnote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight, zero-dependency Markdown renderer for CryptoNote.
 *
 * Renders headings, bold, italics, inline code, code blocks, lists,
 * task checkboxes, blockquotes, and horizontal rules using native Material 3 typography.
 */
@Composable
fun MarkdownView(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, fontWeight, color) = when (block.level) {
                        1 -> Triple(24.sp, FontWeight.Bold, MaterialTheme.colorScheme.primary)
                        2 -> Triple(20.sp, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
                        else -> Triple(17.sp, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color,
                        lineHeight = (fontSize.value * 1.3).sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = (block.indent * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = renderInlineMarkdown(block.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.padding(start = (block.indent * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}. ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = renderInlineMarkdown(block.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is MarkdownBlock.TaskItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (block.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (block.isChecked) "Completed task" else "Incomplete task",
                            tint = if (block.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = renderInlineMarkdown(block.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(12.dp)
                    ) {
                        Column {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val indent: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val indent: Int, val text: String) : MarkdownBlock
    data class TaskItem(val isChecked: Boolean, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

internal fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var codeLanguage = ""
    val codeLines = mutableListOf<String>()

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(language = codeLanguage, code = codeLines.joinToString("\n")))
                codeLines.clear()
                inCodeBlock = false
                codeLanguage = ""
            } else {
                inCodeBlock = true
                codeLanguage = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            continue
        }

        if (trimmed.isEmpty()) {
            continue
        }

        when {
            trimmed == "---" || trimmed == "***" -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }

            trimmed.startsWith("#") -> {
                val hashes = trimmed.takeWhile { it == '#' }
                val level = hashes.length.coerceIn(1, 6)
                val text = trimmed.substring(level).trim()
                blocks.add(MarkdownBlock.Heading(level = level, text = text))
            }

            trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                blocks.add(MarkdownBlock.TaskItem(isChecked = false, text = trimmed.substring(6)))
            }

            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> {
                blocks.add(MarkdownBlock.TaskItem(isChecked = true, text = trimmed.substring(6)))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val indent = (line.indexOfFirst { !it.isWhitespace() } / 2).coerceAtLeast(0)
                blocks.add(MarkdownBlock.BulletItem(indent = indent, text = trimmed.substring(2)))
            }

            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val dotIndex = trimmed.indexOf('.')
                val number = trimmed.substring(0, dotIndex)
                val text = trimmed.substring(dotIndex + 1).trim()
                val indent = (line.indexOfFirst { !it.isWhitespace() } / 2).coerceAtLeast(0)
                blocks.add(MarkdownBlock.NumberedItem(number = number, indent = indent, text = text))
            }

            trimmed.startsWith("> ") -> {
                blocks.add(MarkdownBlock.BlockQuote(text = trimmed.substring(2).trim()))
            }

            else -> {
                blocks.add(MarkdownBlock.Paragraph(text = line))
            }
        }
    }

    if (inCodeBlock && codeLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(language = codeLanguage, code = codeLines.joinToString("\n")))
    }

    return blocks
}

/**
 * Parses inline formatting: **bold**, *italic*, `code`.
 */
private fun renderInlineMarkdown(text: String, primaryColor: Color, surfaceColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.size) {
            // Check for **bold**
            if (i + 1 < text.size && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", startIndex = i + 2)
                if (end != -1) {
                    val boldText = text.substring(i + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                    i = end + 2
                    continue
                }
            }

            // Check for *italic* or _italic_
            if (text[i] == '*' || text[i] == '_') {
                val marker = text[i]
                val end = text.indexOf(marker, startIndex = i + 1)
                if (end != -1 && end > i + 1) {
                    val italicText = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                    i = end + 1
                    continue
                }
            }

            // Check for `inline code`
            if (text[i] == '`') {
                val end = text.indexOf('`', startIndex = i + 1)
                if (end != -1) {
                    val codeText = text.substring(i + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = surfaceColor,
                            color = primaryColor
                        )
                    )
                    append(" $codeText ")
                    pop()
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}

private val String.size: Int
    get() = this.length
