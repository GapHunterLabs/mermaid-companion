package dev.gaphunter.mermaidcompanion.lang

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class MermaidLexerTest {

    private fun tokenize(text: String): List<Pair<IElementType, String>> {
        val lexer = MermaidLexer()
        lexer.start(text, 0, text.length, 0)
        val result = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            result.add(type to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return result
    }

    private fun nonWhitespace(text: String) = tokenize(text).filter { it.first != MermaidTokenTypes.WHITESPACE }

    @Test
    fun simpleFlowchartEdge() {
        val tokens = nonWhitespace("A-->B")
        assertEquals(
            listOf(
                MermaidTokenTypes.IDENTIFIER to "A",
                MermaidTokenTypes.ARROW to "-->",
                MermaidTokenTypes.IDENTIFIER to "B",
            ),
            tokens,
        )
    }

    @Test
    fun hyphenatedIdentifierIsNotSplitByArrowScanning() {
        val tokens = nonWhitespace("node-1-->node-2")
        assertEquals(
            listOf(
                MermaidTokenTypes.IDENTIFIER to "node-1",
                MermaidTokenTypes.ARROW to "-->",
                MermaidTokenTypes.IDENTIFIER to "node-2",
            ),
            tokens,
        )
    }

    @Test
    fun nodeShapeBrackets() {
        val tokens = nonWhitespace("A[Start]-->B(Round)")
        assertEquals(
            listOf(
                MermaidTokenTypes.IDENTIFIER to "A",
                MermaidTokenTypes.LBRACKET to "[",
                MermaidTokenTypes.IDENTIFIER to "Start",
                MermaidTokenTypes.RBRACKET to "]",
                MermaidTokenTypes.ARROW to "-->",
                MermaidTokenTypes.IDENTIFIER to "B",
                MermaidTokenTypes.LPAREN to "(",
                MermaidTokenTypes.IDENTIFIER to "Round",
                MermaidTokenTypes.RPAREN to ")",
            ),
            tokens,
        )
    }

    @Test
    fun commentRunsToEndOfLineOnly() {
        val tokens = nonWhitespace("%% a comment\nflowchart TD")
        assertEquals(MermaidTokenTypes.COMMENT, tokens[0].first)
        assertEquals("%% a comment", tokens[0].second)
        assertEquals(MermaidTokenTypes.IDENTIFIER to "flowchart", tokens[1])
    }

    @Test
    fun quotedLabelWithEscapedQuote() {
        val tokens = nonWhitespace("""A["a \"quoted\" label"]""")
        val string = tokens.first { it.first == MermaidTokenTypes.STRING }
        assertEquals(""""a \"quoted\" label"""", string.second)
    }

    @Test
    fun unterminatedStringIsFlaggedByTokenType() {
        val tokens = nonWhitespace("A[\"never closed")
        val string = tokens.last()
        assertEquals(MermaidTokenTypes.UNTERMINATED_STRING, string.first)
    }

    @Test
    fun nonAsciiIdentifierLexesAsOneToken() {
        val tokens = nonWhitespace("subgraph á")
        assertEquals(
            listOf(MermaidTokenTypes.IDENTIFIER to "subgraph", MermaidTokenTypes.IDENTIFIER to "á"),
            tokens,
        )
    }

    @Test
    fun cjkIdentifierLexesAsOneToken() {
        val tokens = nonWhitespace("subgraph 日本語")
        assertEquals(
            listOf(MermaidTokenTypes.IDENTIFIER to "subgraph", MermaidTokenTypes.IDENTIFIER to "日本語"),
            tokens,
        )
    }

    @Test
    fun sequenceDiagramArrow() {
        val tokens = nonWhitespace("Alice->>Bob: Hello")
        assertEquals(MermaidTokenTypes.IDENTIFIER to "Alice", tokens[0])
        assertEquals(MermaidTokenTypes.ARROW, tokens[1].first)
        assertEquals(MermaidTokenTypes.IDENTIFIER to "Bob", tokens[2])
        assertEquals(MermaidTokenTypes.COLON to ":", tokens[3])
    }

    @Test
    fun emptyInputProducesNoTokens() {
        assertEquals(emptyList<Pair<IElementType, String>>(), tokenize(""))
    }
}
