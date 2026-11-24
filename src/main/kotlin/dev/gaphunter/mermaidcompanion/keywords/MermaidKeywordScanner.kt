package dev.gaphunter.mermaidcompanion.keywords

import dev.gaphunter.mermaidcompanion.lang.MermaidLexer
import dev.gaphunter.mermaidcompanion.lang.MermaidRegions
import dev.gaphunter.mermaidcompanion.lang.MermaidTokenTypes

/**
 * Which identifiers of a diagram get a color of their own: the word that declares the diagram's type
 * (its header) and the keywords of that type. Pure (text in, offsets out), run once per file version
 * by the annotator.
 *
 * The header is the first identifier of the file outside front matter, directives (also those that
 * span lines) and comments. A keyword is colored only where it can be one: never inside a shape's
 * label (`A[loop over it]`) or a flowchart edge label (`-->|end|`), and never in the free text after
 * a colon on the same line (a sequence message, a gantt task, a
 * transition label), where words like `loop`, `end` or `over` are just words.
 */
object MermaidKeywordScanner {

    enum class Kind { DIAGRAM_TYPE, KEYWORD }

    data class Result(val type: String?, val marks: Map<Int, Kind>)

    private val OPENERS = setOf(MermaidTokenTypes.LBRACKET, MermaidTokenTypes.LPAREN)
    private val CLOSERS = setOf(MermaidTokenTypes.RBRACKET, MermaidTokenTypes.RPAREN, MermaidTokenTypes.RBRACE)
    private val OPEN_ARROWS = setOf("--", "-.", "==")

    fun scan(text: CharSequence): Result {
        val s = text.toString()
        val skip = MermaidRegions.skipped(s)
        val lexer = MermaidLexer()
        lexer.start(s, 0, s.length, 0)
        val marks = HashMap<Int, Kind>()
        var type: String? = null
        var headerSeen = false
        var depth = 0
        var afterColon = false
        var inPipeLabel = false // flowchart: -->|text| is an edge label
        var inArrowLabel = false // flowchart: A -- text --> B, A -. text .-> B, A == text ==> B
        var previousType: com.intellij.psi.tree.IElementType? = null
        var previousEnd = -1
        while (lexer.tokenType != null) {
            val t = lexer.tokenType!!
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            if (s.subSequence(start, end).any { it == '\n' }) {
                depth = 0
                afterColon = false
                inPipeLabel = false
                inArrowLabel = false
            }
            when {
                t == MermaidTokenTypes.WHITESPACE || t == MermaidTokenTypes.COMMENT -> Unit
                skip(start) -> Unit
                t == MermaidTokenTypes.PIPE -> if (type == "flowchart") inPipeLabel = !inPipeLabel
                t == MermaidTokenTypes.ARROW -> if (type == "flowchart") {
                    inArrowLabel = !inArrowLabel && s.substring(start, end) in OPEN_ARROWS
                }
                t in OPENERS -> depth++
                t == MermaidTokenTypes.LBRACE && previousType == MermaidTokenTypes.IDENTIFIER && previousEnd == start -> depth++
                t in CLOSERS -> if (depth > 0) depth--
                t == MermaidTokenTypes.COLON -> if (headerSeen) afterColon = true
                t == MermaidTokenTypes.IDENTIFIER -> {
                    val word = s.substring(start, end)
                    if (!headerSeen) {
                        headerSeen = true
                        // "stateDiagram-v2" and "xychart-beta" lex as the word plus "-v2"/"-beta": the word decides.
                        type = MermaidKeywords.DIAGRAM_TYPES[word]
                        if (type != null) marks[start] = Kind.DIAGRAM_TYPE
                    } else if (depth == 0 && !afterColon && !inPipeLabel && !inArrowLabel && MermaidKeywords.isKeyword(word, type)) {
                        marks[start] = Kind.KEYWORD
                    }
                }
            }
            if (t != MermaidTokenTypes.WHITESPACE && t != MermaidTokenTypes.COMMENT) {
                previousType = t
                previousEnd = end
            }
            lexer.advance()
        }
        return Result(type, marks)
    }

}
