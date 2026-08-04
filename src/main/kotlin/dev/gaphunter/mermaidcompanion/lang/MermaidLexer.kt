package dev.gaphunter.mermaidcompanion.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-rolled lexer covering the syntax shared across Mermaid's
 * flowchart/sequence/class diagrams: `%% comments`, double-quoted strings,
 * node-shape delimiters (`[]`, `()`, `{}`), edges/arrows, and identifiers.
 *
 * The bug this whole plugin exists to fix (see README "Why it exists")
 * is a competitor flagging valid non-ASCII identifiers and valid quoted
 * labels as errors. The fix lives here, structurally, not as a special
 * case: [scanIdentifier] uses [Char.isLetterOrDigit], which is
 * Unicode-aware, and never applies an ASCII-only character class to
 * identifiers or label text anywhere in this lexer. There is no separate
 * "is this identifier valid" character-class check to get wrong.
 */
class MermaidLexer : LexerBase() {

    private lateinit var buffer: CharSequence
    private var startOffset = 0
    private var endOffset = 0

    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        locateToken()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    private val arrowChars = charArrayOf('-', '.', '=', '>', '<', 'x', 'o')

    private fun locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null
            tokenEnd = tokenStart
            return
        }
        val c = buffer[tokenStart]
        when {
            c.isWhitespace() -> {
                tokenType = MermaidTokenTypes.WHITESPACE
                tokenEnd = scanWhile(tokenStart) { it.isWhitespace() }
            }
            c == '%' && peekAt(tokenStart + 1) == '%' -> {
                tokenType = MermaidTokenTypes.COMMENT
                tokenEnd = scanWhile(tokenStart + 2) { it != '\n' }
            }
            c == '"' -> {
                val (end, type) = scanQuotedString(tokenStart)
                tokenType = type
                tokenEnd = end
            }
            c == '[' -> single(MermaidTokenTypes.LBRACKET)
            c == ']' -> single(MermaidTokenTypes.RBRACKET)
            c == '(' -> single(MermaidTokenTypes.LPAREN)
            c == ')' -> single(MermaidTokenTypes.RPAREN)
            c == '{' -> single(MermaidTokenTypes.LBRACE)
            c == '}' -> single(MermaidTokenTypes.RBRACE)
            c == '|' -> single(MermaidTokenTypes.PIPE)
            c == ':' -> single(MermaidTokenTypes.COLON)
            c == ';' -> single(MermaidTokenTypes.SEMICOLON)
            c == '-' || c == '=' || c == '.' -> {
                tokenType = MermaidTokenTypes.ARROW
                tokenEnd = scanWhile(tokenStart) { it in arrowChars }
            }
            c.isLetterOrDigit() || c == '_' -> {
                tokenType = MermaidTokenTypes.IDENTIFIER
                tokenEnd = scanIdentifier(tokenStart)
            }
            else -> {
                tokenType = MermaidTokenTypes.OTHER
                tokenEnd = tokenStart + 1
            }
        }
        if (tokenEnd <= tokenStart) {
            // Safety net: never emit a zero-length token.
            tokenType = MermaidTokenTypes.BAD_CHARACTER
            tokenEnd = tokenStart + 1
        }
    }

    private fun single(type: IElementType) {
        tokenType = type
        tokenEnd = tokenStart + 1
    }

    private fun peekAt(i: Int): Char? = if (i < endOffset) buffer[i] else null

    private fun scanWhile(from: Int, predicate: (Char) -> Boolean): Int {
        var i = from
        while (i < endOffset && predicate(buffer[i])) i++
        return i
    }

    /** Unicode-aware on purpose (see class doc): letters/digits/underscore
     * always continue the identifier; a hyphen continues it only when
     * immediately followed by another letter/digit, so `node-1` lexes as
     * one identifier while `A-->B` still splits into `A`, an ARROW, `B`. */
    private fun scanIdentifier(from: Int): Int {
        var i = from
        while (i < endOffset) {
            val c = buffer[i]
            if (c.isLetterOrDigit() || c == '_') {
                i++
                continue
            }
            if (c == '-' && i + 1 < endOffset && (buffer[i + 1].isLetterOrDigit() || buffer[i + 1] == '_')) {
                i++
                continue
            }
            break
        }
        return i
    }

    private fun scanQuotedString(from: Int): Pair<Int, IElementType> {
        var i = from + 1
        while (i < endOffset) {
            val c = buffer[i]
            if (c == '\\' && i + 1 < endOffset) {
                i += 2
                continue
            }
            if (c == '"') return (i + 1) to MermaidTokenTypes.STRING
            if (c == '\n') return i to MermaidTokenTypes.UNTERMINATED_STRING
            i++
        }
        return i to MermaidTokenTypes.UNTERMINATED_STRING
    }
}
