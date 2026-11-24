package dev.gaphunter.mermaidcompanion.preview

import com.intellij.psi.tree.IElementType
import dev.gaphunter.mermaidcompanion.lang.MermaidLexer
import dev.gaphunter.mermaidcompanion.lang.MermaidRegions
import dev.gaphunter.mermaidcompanion.lang.MermaidTokenTypes

/**
 * Where, in the diagram's text, a clicked element of the preview is written: the offset the
 * caret moves to. Pure (text in, offset out), so it is tested without an IDE.
 *
 * A name (node, class, state, entity, subgraph, participant) is matched as a whole identifier,
 * never as part of one, and never inside a comment, a quoted string or front matter. The first
 * match outside a shape's label wins (in `A[Go to B]`, B's own line is preferred over the label);
 * when the name only appears inside labels, the first of those is used. A `{` counts as a shape
 * opener only right after an identifier (`A{Ok?}`, `Animal {`), because ER cardinalities use
 * braces too (`||--o{`). When no token matches (a name with spaces, such as a kanban item, or a
 * name after a quoted label that spans lines), the name is looked up as plain text: a whole word or
 * phrase, still outside front matter and comment lines. Nothing is matched for a name that is not in
 * the text.
 */
object SourceLocator {
    private val OPENERS = setOf(MermaidTokenTypes.LBRACKET, MermaidTokenTypes.LPAREN)
    private val CLOSERS = setOf(MermaidTokenTypes.RBRACKET, MermaidTokenTypes.RPAREN, MermaidTokenTypes.RBRACE)

    private data class Token(val type: IElementType, val start: Int, val end: Int)

    private fun tokens(text: CharSequence): List<Token> {
        val lexer = MermaidLexer()
        lexer.start(text, 0, text.length, 0)
        val out = ArrayList<Token>()
        while (lexer.tokenType != null) {
            out.add(Token(lexer.tokenType!!, lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return out
    }

    /** Where the front matter (--- ... --- at the very top) ends, or 0 when there is none. */
    private fun bodyStart(text: CharSequence): Int {
        val s = text.toString()
        val first = s.lineSequence().firstOrNull()?.trim()
        if (first != "---") return 0
        var offset = s.indexOf('\n').let { if (it < 0) return 0 else it + 1 }
        while (offset < s.length) {
            val end = s.indexOf('\n', offset).let { if (it < 0) s.length else it }
            if (s.substring(offset, end).trim() == "---") return minOf(s.length, end + 1)
            offset = end + 1
        }
        return 0 // never closed: mermaid would not read it as front matter either
    }

    fun offsetOfName(text: CharSequence, name: String): Int? {
        if (name.isEmpty()) return null
        val from = bodyStart(text)
        val skip = MermaidRegions.skipped(text)
        // Labels never span lines, so the depth starts over on every line. `previous` is the last
        // token that is not whitespace.
        var depth = 0
        var firstAnywhere: Int? = null
        var previous: Token? = null
        for (t in tokens(text)) {
            if (text.subSequence(t.start, t.end).any { it == '\n' }) depth = 0
            when {
                t.type == MermaidTokenTypes.WHITESPACE -> continue
                t.type in OPENERS -> depth++
                t.type == MermaidTokenTypes.LBRACE && previous?.type == MermaidTokenTypes.IDENTIFIER && previous.end == t.start -> depth++
                t.type in CLOSERS -> if (depth > 0) depth--
                t.type == MermaidTokenTypes.IDENTIFIER && t.start >= from && !skip(t.start) && text.subSequence(t.start, t.end).toString() == name -> {
                    if (depth == 0) return t.start
                    if (firstAnywhere == null) firstAnywhere = t.start
                }
            }
            previous = t
        }
        return firstAnywhere ?: plainOffset(text, name, from)
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    /** The first whole-word occurrence of [name] at or after [from], skipping `%%` comment lines. */
    private fun plainOffset(text: CharSequence, name: String, from: Int): Int? {
        val s = text.toString()
        var lineStart = from
        while (lineStart <= s.length) {
            val lineEnd = s.indexOf('\n', lineStart).let { if (it < 0) s.length else it }
            if (!s.substring(lineStart, lineEnd).trimStart().startsWith("%%")) {
                var i = s.indexOf(name, lineStart)
                while (i in lineStart until lineEnd) {
                    val before = if (i > 0) s[i - 1] else ' '
                    val after = if (i + name.length < s.length) s[i + name.length] else ' '
                    val wholeWord = !(isWordChar(name.first()) && isWordChar(before)) && !(isWordChar(name.last()) && isWordChar(after))
                    if (wholeWord && i + name.length <= lineEnd) return i
                    i = s.indexOf(name, i + 1)
                }
            }
            if (lineEnd >= s.length) break
            lineStart = lineEnd + 1
        }
        return null
    }

    /**
     * The n-th mindmap node (0 = the root): the n-th line after the `mindmap` header that holds
     * a node, skipping blank lines, comments, and `::icon(...)` / `:::class` decoration lines.
     */
    fun offsetOfMindmapNode(text: CharSequence, index: Int): Int? {
        if (index < 0) return null
        val s = text.toString()
        val from = bodyStart(s)
        var offset = from
        var seenHeader = false
        var count = 0
        while (offset <= s.length) {
            val end = s.indexOf('\n', offset).let { if (it < 0) s.length else it }
            val line = s.substring(offset, end)
            val trimmed = line.trim()
            val indent = line.length - line.trimStart().length
            if (!seenHeader) {
                if (trimmed.isNotEmpty() && !trimmed.startsWith("%%")) {
                    if (trimmed != "mindmap") return null
                    seenHeader = true
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("%%") && !trimmed.startsWith("::icon(") && !trimmed.startsWith(":::")) {
                if (count == index) return offset + indent
                count++
            }
            if (end >= s.length) break
            offset = end + 1
        }
        return null
    }
}
