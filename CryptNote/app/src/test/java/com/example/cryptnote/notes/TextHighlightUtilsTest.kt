package com.example.cryptnote.notes

import androidx.compose.ui.graphics.Color
import com.example.cryptnote.ui.common.buildHighlightedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHighlightUtilsTest {

    @Test
    fun `empty query returns unstyled text`() {
        val result = buildHighlightedString("Hello World", "", Color.Yellow)
        assertEquals("Hello World", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `matching substring receives highlight span style`() {
        val text = "Zero Knowledge Cryptography"
        val query = "knowledge"
        val highlightColor = Color.Yellow

        val result = buildHighlightedString(text, query, highlightColor)
        assertEquals(text, result.text)
        assertEquals(1, result.spanStyles.size)

        val span = result.spanStyles[0]
        assertEquals(5, span.start)
        assertEquals(14, span.end)
        assertEquals(highlightColor, span.item.background)
    }

    @Test
    fun `multiple occurrences highlight each match`() {
        val text = "key one, key two, key three"
        val query = "key"

        val result = buildHighlightedString(text, query, Color.Cyan)
        assertEquals(3, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(3, result.spanStyles[0].end)
        assertEquals(9, result.spanStyles[1].start)
        assertEquals(12, result.spanStyles[1].end)
        assertEquals(18, result.spanStyles[2].start)
        assertEquals(21, result.spanStyles[2].end)
    }
}
