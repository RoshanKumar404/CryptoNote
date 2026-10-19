package com.example.cryptnote.notes

import com.example.cryptnote.ui.common.MarkdownBlock
import com.example.cryptnote.ui.common.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses headings correctly`() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.Heading(1, "Heading 1"), blocks[0])
        assertEquals(MarkdownBlock.Heading(2, "Heading 2"), blocks[1])
        assertEquals(MarkdownBlock.Heading(3, "Heading 3"), blocks[2])
    }

    @Test
    fun `parses code blocks with language`() {
        val markdown = """
            ```kotlin
            val key = generateKey()
            encrypt(key)
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(1, blocks.size)
        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("val key = generateKey()\nencrypt(key)", codeBlock.code)
    }

    @Test
    fun `parses tasks with checked and unchecked states`() {
        val markdown = """
            - [ ] Buy Bitcoin
            - [x] Secure seed phrase in vault
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(2, blocks.size)
        val task1 = blocks[0] as MarkdownBlock.TaskItem
        assertFalse(task1.isChecked)
        assertEquals("Buy Bitcoin", task1.text)

        val task2 = blocks[1] as MarkdownBlock.TaskItem
        assertTrue(task2.isChecked)
        assertEquals("Secure seed phrase in vault", task2.text)
    }

    @Test
    fun `parses bullet lists and numbered lists`() {
        val markdown = """
            - Item A
            - Item B
            1. First step
            2. Second step
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BulletItem)
        assertTrue(blocks[1] is MarkdownBlock.BulletItem)
        assertTrue(blocks[2] is MarkdownBlock.NumberedItem)
        assertTrue(blocks[3] is MarkdownBlock.NumberedItem)
    }

    @Test
    fun `parses blockquotes and dividers`() {
        val markdown = """
            > Zero knowledge encryption is essential
            ---
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.BlockQuote("Zero knowledge encryption is essential"), blocks[0])
        assertEquals(MarkdownBlock.HorizontalRule, blocks[1])
    }
}
