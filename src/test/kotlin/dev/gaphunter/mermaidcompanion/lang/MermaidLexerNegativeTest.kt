package dev.gaphunter.mermaidcompanion.lang

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The lexer's contract with the platform on input nobody types on purpose: tokens are
 * contiguous, never empty, never past the end, and a restart in the middle of the buffer
 * (what the editor does on every incremental re-highlight) lexes the rest the same way.
 */
class MermaidLexerNegativeTest {

    private data class Token(val type: IElementType, val start: Int, val end: Int)

    private fun tokens(text: CharSequence, start: Int = 0, end: Int = text.length): List<Token> {
        val lexer = MermaidLexer()
        lexer.start(text, start, end, 0)
        val out = mutableListOf<Token>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += Token(type, lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
            check(out.size <= text.length + 1) { "lexer did not advance" }
        }
        return out
    }

    private fun assertCovers(text: CharSequence, start: Int = 0, end: Int = text.length) {
        val list = tokens(text, start, end)
        var expected = start
        for (t in list) {
            assertEquals("gap or overlap at $expected in ${text.toString().take(60)}", expected, t.start)
            assertTrue("empty token at ${t.start}", t.end > t.start)
            expected = t.end
        }
        assertEquals("tokens must end exactly at the end offset", end, expected)
    }

    @Test
    fun `empty and one-character inputs are covered exactly`() {
        assertEquals(emptyList<Token>(), tokens(""))
        for (c in "\"[](){}|:;-=.>x%\\\n\r\t ﻿\u0000𐀀á") assertCovers(c.toString())
    }

    @Test
    fun `random garbage is always covered without gaps, empty tokens or overruns`() {
        val alphabet = "\"\\[](){}|:;-=.>ox%_ \n\r\tAz0á日🚀\uD800﻿"
        val random = Random(20261123)
        repeat(2_000) {
            val length = random.nextInt(0, 80)
            val text = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertCovers(text)
        }
    }

    @Test
    fun `a restart in the middle of the buffer lexes the rest consistently`() {
        val text = "flowchart LR\n    A[\"x\"] --> B{y}\n    %% c\n    C(z) -.-> D\n"
        for (offset in text.indices) assertCovers(text, offset, text.length)
        // Restarting at a token boundary gives exactly the tail of the full token list.
        val full = tokens(text)
        for (token in full) assertEquals(full.filter { it.start >= token.start }, tokens(text, token.start))
    }

    @Test
    fun `an end offset before the buffer end is respected`() {
        val text = "\"abcdef\" [x]"
        assertCovers(text, 0, 4)
        assertEquals(MermaidTokenTypes.UNTERMINATED_STRING, tokens(text, 0, 4).single().type)
    }

    @Test
    fun `a backslash at the very end of an unterminated string stays inside it`() {
        val list = tokens("\"abc\\")
        assertEquals(listOf(Token(MermaidTokenTypes.UNTERMINATED_STRING, 0, 5)), list)
    }

    @Test
    fun `an escaped quote does not end the string, and a backslash carries it past a newline`() {
        assertEquals(MermaidTokenTypes.STRING, tokens("\"a\\\"b\"").single().type)
        // Pinned current behaviour: backslash + newline is consumed as an escape, so the
        // unterminated string runs on to the next line (mermaid.js has no backslash escapes).
        assertEquals(listOf(Token(MermaidTokenTypes.UNTERMINATED_STRING, 0, 5)), tokens("\"a\\\nb"))
    }

    @Test
    fun `a supplementary character is two OTHER tokens and identifiers stop around it`() {
        val list = tokens("a🚀b")
        assertEquals(listOf(MermaidTokenTypes.IDENTIFIER, MermaidTokenTypes.OTHER, MermaidTokenTypes.OTHER, MermaidTokenTypes.IDENTIFIER), list.map { it.type })
    }

    @Test
    fun `a combining mark splits a decomposed identifier (highlighting only, never an error)`() {
        // "é" as e + U+0301: U+0301 is not a letter, so the identifier stops before it.
        assertEquals(3, tokens("éx").size)
        // The precomposed form stays one identifier.
        assertEquals(1, tokens("éx").size)
    }

    @Test
    fun `CRLF is one whitespace token, like LF`() {
        assertEquals(listOf(MermaidTokenTypes.IDENTIFIER, MermaidTokenTypes.WHITESPACE, MermaidTokenTypes.IDENTIFIER), tokens("a\r\nb").map { it.type })
    }

    @Test
    fun `a comment at end of file without a newline ends at the end`() {
        assertEquals(listOf(Token(MermaidTokenTypes.COMMENT, 0, 4)), tokens("%% x"))
    }
}
