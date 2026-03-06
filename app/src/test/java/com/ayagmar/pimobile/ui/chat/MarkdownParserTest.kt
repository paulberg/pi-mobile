package com.ayagmar.pimobile.ui.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    // ── Block parsing ───────────────────────────────────────────────────────

    @Test
    fun parsesPlainTextAsSingleParagraph() {
        val blocks = parseMarkdownBlocks("Hello world")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.ParagraphBlock)
        assertEquals("Hello world", (blocks[0] as MarkdownBlock.ParagraphBlock).content)
    }

    @Test
    fun parsesHeaders() {
        val blocks = parseMarkdownBlocks("# Title\n## Subtitle\n### H3")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.HeaderBlock)
        assertEquals(1, (blocks[0] as MarkdownBlock.HeaderBlock).level)
        assertEquals("Title", (blocks[0] as MarkdownBlock.HeaderBlock).content)
        assertEquals(2, (blocks[1] as MarkdownBlock.HeaderBlock).level)
        assertEquals("Subtitle", (blocks[1] as MarkdownBlock.HeaderBlock).content)
        assertEquals(3, (blocks[2] as MarkdownBlock.HeaderBlock).level)
    }

    @Test
    fun parsesUnorderedList() {
        val blocks = parseMarkdownBlocks("- item one\n- item two\n  - nested")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.UnorderedListItemBlock)
        assertEquals("item one", (blocks[0] as MarkdownBlock.UnorderedListItemBlock).content)
        assertEquals(0, (blocks[0] as MarkdownBlock.UnorderedListItemBlock).indent)
        assertEquals("item two", (blocks[1] as MarkdownBlock.UnorderedListItemBlock).content)
        assertEquals(1, (blocks[2] as MarkdownBlock.UnorderedListItemBlock).indent)
    }

    @Test
    fun parsesOrderedList() {
        val blocks = parseMarkdownBlocks("1. first\n2. second")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.OrderedListItemBlock)
        assertEquals("1", (blocks[0] as MarkdownBlock.OrderedListItemBlock).number)
        assertEquals("first", (blocks[0] as MarkdownBlock.OrderedListItemBlock).content)
        assertEquals("2", (blocks[1] as MarkdownBlock.OrderedListItemBlock).number)
    }

    @Test
    fun parsesBlockquote() {
        val blocks = parseMarkdownBlocks("> This is a quote")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BlockquoteBlock)
        assertEquals("This is a quote", (blocks[0] as MarkdownBlock.BlockquoteBlock).content)
    }

    @Test
    fun parsesHorizontalRule() {
        val blocks = parseMarkdownBlocks("text above\n\n---\n\ntext below")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.ParagraphBlock)
        assertTrue(blocks[1] is MarkdownBlock.HorizontalRuleBlock)
        assertTrue(blocks[2] is MarkdownBlock.ParagraphBlock)
    }

    @Test
    fun mergesAdjacentPlainLinesIntoParagraph() {
        val blocks = parseMarkdownBlocks("line one\nline two\nline three")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.ParagraphBlock)
        assertEquals("line one line two line three", (blocks[0] as MarkdownBlock.ParagraphBlock).content)
    }

    @Test
    fun blankLineSeparatesParagraphs() {
        val blocks = parseMarkdownBlocks("paragraph one\n\nparagraph two")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.ParagraphBlock)
        assertTrue(blocks[1] is MarkdownBlock.ParagraphBlock)
        assertEquals("paragraph one", (blocks[0] as MarkdownBlock.ParagraphBlock).content)
        assertEquals("paragraph two", (blocks[1] as MarkdownBlock.ParagraphBlock).content)
    }

    @Test
    fun emptyTextProducesNoBlocks() {
        assertEquals(0, parseMarkdownBlocks("").size)
        assertEquals(0, parseMarkdownBlocks("   ").size)
    }

    @Test
    fun mixedBlockTypes() {
        val md = """
            # Title
            
            Some intro text.
            
            - bullet one
            - bullet two
            
            > A quote
            
            1. step one
            2. step two
        """.trimIndent()
        val blocks = parseMarkdownBlocks(md)
        assertTrue(blocks[0] is MarkdownBlock.HeaderBlock)
        assertTrue(blocks[1] is MarkdownBlock.ParagraphBlock)
        assertTrue(blocks[2] is MarkdownBlock.UnorderedListItemBlock)
        assertTrue(blocks[3] is MarkdownBlock.UnorderedListItemBlock)
        assertTrue(blocks[4] is MarkdownBlock.BlockquoteBlock)
        assertTrue(blocks[5] is MarkdownBlock.OrderedListItemBlock)
        assertTrue(blocks[6] is MarkdownBlock.OrderedListItemBlock)
    }

    // ── Inline parsing ──────────────────────────────────────────────────────

    private val testColors = MarkdownColors(
        text = Color.White,
        code = Color.Blue,
        codeBackground = Color.DarkGray,
        link = Color.Cyan,
        blockquoteBorder = Color.Gray,
        blockquoteText = Color.LightGray,
    )

    @Test
    fun inlineBoldParsesCorrectly() {
        val result = parseInlineMarkdown("Hello **world**", testColors)
        assertEquals("Hello world", result.text)
    }

    @Test
    fun inlineItalicParsesCorrectly() {
        val result = parseInlineMarkdown("Hello *world*", testColors)
        assertEquals("Hello world", result.text)
    }

    @Test
    fun inlineBoldItalicParsesCorrectly() {
        val result = parseInlineMarkdown("Hello ***world***", testColors)
        assertEquals("Hello world", result.text)
    }

    @Test
    fun inlineCodeParsesCorrectly() {
        val result = parseInlineMarkdown("Use `println()` to print", testColors)
        assertEquals("Use println() to print", result.text)
    }

    @Test
    fun inlineStrikethroughParsesCorrectly() {
        val result = parseInlineMarkdown("This is ~~deleted~~ text", testColors)
        assertEquals("This is deleted text", result.text)
    }

    @Test
    fun inlineLinkParsesCorrectly() {
        val result = parseInlineMarkdown("Visit [Google](https://google.com)", testColors)
        assertEquals("Visit Google", result.text)
    }

    @Test
    fun inlineCodeProtectsContentsFromParsing() {
        // Bold markers inside backticks should NOT be parsed as bold
        val result = parseInlineMarkdown("Use `**not bold**` here", testColors)
        assertEquals("Use **not bold** here", result.text)
    }

    @Test
    fun multipleMixedInlineFormats() {
        val result = parseInlineMarkdown("**bold** and *italic* and `code`", testColors)
        assertEquals("bold and italic and code", result.text)
    }

    @Test
    fun plainTextPassesThroughUnchanged() {
        val result = parseInlineMarkdown("No formatting here", testColors)
        assertEquals("No formatting here", result.text)
    }

    @Test
    fun emptyStringProducesEmptyAnnotatedString() {
        val result = parseInlineMarkdown("", testColors)
        assertEquals("", result.text)
    }
}
