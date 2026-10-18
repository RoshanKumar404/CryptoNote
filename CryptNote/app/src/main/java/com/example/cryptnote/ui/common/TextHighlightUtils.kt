package com.example.cryptnote.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Builds an [AnnotatedString] with visual background and weight highlights on every occurrence
 * of [query] in [text] (case-insensitive).
 */
fun buildHighlightedString(
    text: String,
    query: String,
    highlightBgColor: Color,
    highlightTextColor: Color = Color.Unspecified
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)
        val queryLength = trimmed.length
        var startIndex = 0

        while (startIndex < text.length) {
            val foundIndex = text.indexOf(trimmed, startIndex, ignoreCase = true)
            if (foundIndex == -1) break

            addStyle(
                style = SpanStyle(
                    background = highlightBgColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                ),
                start = foundIndex,
                end = foundIndex + queryLength
            )
            startIndex = foundIndex + queryLength
        }
    }
}
