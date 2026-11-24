package dev.gaphunter.mermaidcompanion.inspection

import com.intellij.psi.tree.IElementType
import dev.gaphunter.mermaidcompanion.lang.MermaidLexer
import dev.gaphunter.mermaidcompanion.lang.MermaidTokenTypes

data class MermaidIssue(val offset: Int, val length: Int, val message: String)

/**
 * Real syntax validation for Mermaid diagrams: unterminated string
 * literals, unmatched/mismatched node-shape brackets (`[]`/`()`/`{}`,
 * matched with proper LIFO kind-checking so valid nested shapes like
 * `id[(cylinder)]` are never flagged), and `subgraph` blocks missing
 * their `end`.
 *
 * This is the direct fix for the cited competitor bug ("shows false
 * warnings for valid non-ASCII subgraph identifiers... shows false
 * errors for valid node shapes where the node contains non-ASCII
 * characters"): this checker never inspects the *characters inside* an
 * identifier or a label at all -- it only tracks token TYPES (bracket
 * kinds, string termination) from [MermaidLexer], which already lexes
 * identifiers with [Char.isLetterOrDigit] (Unicode-aware). There is no
 * ASCII-only regex anywhere in this path for the bug to hide in. See
 * MermaidSyntaxCheckerTest for fixtures reproducing the exact reported
 * cases (`subgraph á`, quoted labels with escaped quotes) and asserting
 * zero issues.
 *
 * The rules depend on the diagram type, read from the first statement
 * (after front matter, comments and directives), because the same
 * characters mean different things in each type:
 *
 * - flowchart / graph: brackets and strings are node shapes and labels.
 *   `id>text]` is the asymmetric shape, a quoted label may span lines,
 *   and `subgraph`/`end` only count as the first word of a statement.
 * - sequenceDiagram: text after a `:` (messages, notes), block labels
 *   (`loop`, `alt`, `else`...) and participant aliases are free text, and
 *   `-)` is an arrow.
 * - classDiagram: text after a `:` (relation labels, members) and the
 *   members inside a class body `{ ... }` are free text.
 * - any other diagram type Mermaid knows (state, ER, gantt, mindmap...):
 *   not checked at all -- its own grammar gives brackets and quotes other
 *   meanings, and a false error is worse than none. The preview still
 *   shows mermaid.js's own error for those.
 * - no recognizable header: checked with the flowchart rules.
 *
 * `%%{ ... }%%` directives (also multi-line), `---` front matter,
 * `accTitle:`/`accDescr:` lines and `accDescr { ... }` blocks are never
 * checked.
 *
 * MermaidCorpusTest compares these verdicts with mermaid.js's own on a
 * corpus of real diagrams.
 *
 * A pure function over text (not a PsiElement walk) -- directly
 * unit-testable, and
 * [dev.gaphunter.mermaidcompanion.highlighting.MermaidSyntaxAnnotator] is
 * a thin per-file wrapper around it. Linear in the text length: every
 * skip moves forward, and nothing recurses.
 */
object MermaidSyntaxChecker {

    private enum class Mode { FLOWCHART, SEQUENCE, CLASS, UNCHECKED }

    /** An open bracket; [namespace] marks a class diagram's `namespace X {`. */
    private data class Open(val type: IElementType, val offset: Int, val symbol: Char, val namespace: Boolean = false)

    private val FLOWCHART_HEADERS = setOf("flowchart", "graph", "flowchart-elk")
    private val SEQUENCE_HEADERS = setOf("sequenceDiagram")
    private val CLASS_HEADERS = setOf("classDiagram", "classDiagram-v2")

    /** Every other diagram type mermaid.js 11.x detects. Not checked: see the class doc. */
    private val UNCHECKED_HEADERS = setOf(
        "stateDiagram", "stateDiagram-v2", "erDiagram", "gantt", "pie", "journey", "gitGraph",
        "mindmap", "timeline", "quadrantChart", "requirementDiagram", "requirement",
        "C4Context", "C4Container", "C4Component", "C4Dynamic", "C4Deployment",
        "sankey-beta", "sankey", "xychart-beta", "xychart", "block-beta", "block",
        "packet-beta", "packet", "kanban", "architecture-beta", "architecture",
        "radar-beta", "radar", "treemap-beta", "treemap", "info", "zenuml",
    )

    /** Sequence statements whose rest of line is a free-text label. */
    private val SEQUENCE_LABEL_KEYWORDS = setOf(
        "loop", "alt", "else", "opt", "par", "and", "critical", "break", "option", "box", "title",
    )

    /** Sequence statements where `as` starts a free-text alias. */
    private val SEQUENCE_ALIAS_KEYWORDS = setOf("participant", "actor", "create")

    /** Statements, in every diagram type, whose text after `:` is free text. */
    private val ACCESSIBILITY_KEYWORDS = setOf("accTitle", "accDescr")

    fun check(text: CharSequence): List<MermaidIssue> {
        val length = text.length
        // Where the last `}%%` is: a directive opened after it can never close, without searching again.
        val lastDirectiveClose = text.lastIndexOf("}%%")
        val bodyStart = frontMatterEnd(text)
        val mode = modeFor(headerWord(text, bodyStart, lastDirectiveClose))
        if (mode == Mode.UNCHECKED) return emptyList()

        val lexer = MermaidLexer()
        lexer.start(text, bodyStart, length, 0)

        val bracketStack = ArrayDeque<Open>()
        val subgraphStack = ArrayDeque<Int>()
        val issues = mutableListOf<MermaidIssue>()

        var atStatementStart = true
        var lineFirstWord: String? = null
        var inPipeLabel = false
        var previousType: IElementType? = null
        var previousEnd = -1
        var openNamespaces = 0

        while (true) {
            val type = lexer.tokenType ?: break
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            var resumeAt = -1
            // Only class diagram namespaces may be open at the top level; counted, so this stays O(1).
            val topLevel = bracketStack.size == openNamespaces

            when (type) {
                MermaidTokenTypes.WHITESPACE -> {
                    if (text.containsNewline(start, end)) {
                        atStatementStart = true
                        lineFirstWord = null
                        inPipeLabel = false
                    }
                }
                MermaidTokenTypes.COMMENT -> resumeAt = directiveEnd(text, start, end, lastDirectiveClose)
                MermaidTokenTypes.UNTERMINATED_STRING -> {
                    val closing = if (mode == Mode.FLOWCHART && end < length) text.indexOf('"', end) else -1
                    if (closing >= 0) {
                        // A quoted flowchart label may span lines: mermaid.js accepts it.
                        resumeAt = closing + 1
                    } else {
                        issues.add(MermaidIssue(start, end - start, "Unterminated string literal"))
                    }
                }
                MermaidTokenTypes.LBRACE -> when {
                    lineFirstWord in ACCESSIBILITY_KEYWORDS && topLevel -> {
                        // A multi-line accessibility description is free text, in every diagram type.
                        val closing = text.indexOf('}', end)
                        resumeAt = if (closing < 0) length else closing + 1
                    }
                    mode == Mode.CLASS && lineFirstWord == "namespace" && topLevel -> {
                        bracketStack.addLast(Open(type, start, '{', namespace = true))
                        openNamespaces++
                    }
                    mode == Mode.CLASS && topLevel -> {
                        // A class body: one free-text member per line, up to the first '}'.
                        val closing = text.indexOf('}', end)
                        if (closing < 0) {
                            issues.add(MermaidIssue(start, 1, "Unmatched opening '{'"))
                            resumeAt = length
                        } else {
                            resumeAt = closing + 1
                        }
                    }
                    else -> bracketStack.addLast(Open(type, start, '{'))
                }
                MermaidTokenTypes.LBRACKET -> bracketStack.addLast(Open(type, start, '['))
                MermaidTokenTypes.LPAREN -> bracketStack.addLast(Open(type, start, '('))
                MermaidTokenTypes.RBRACKET ->
                    closeBracket(bracketStack, MermaidTokenTypes.LBRACKET, "]", start, issues)
                MermaidTokenTypes.RPAREN -> {
                    // `A-)B` and `A--)B` are sequence diagram arrows, not a closing paren.
                    val asyncArrow = mode == Mode.SEQUENCE && previousType == MermaidTokenTypes.ARROW && previousEnd == start
                    if (!asyncArrow) closeBracket(bracketStack, MermaidTokenTypes.LPAREN, ")", start, issues)
                }
                MermaidTokenTypes.RBRACE -> {
                    val closesNamespace = bracketStack.lastOrNull()?.namespace == true
                    if (closeBracket(bracketStack, MermaidTokenTypes.LBRACE, "}", start, issues) && closesNamespace) openNamespaces--
                }
                MermaidTokenTypes.PIPE -> if (mode == Mode.FLOWCHART) inPipeLabel = !inPipeLabel
                MermaidTokenTypes.OTHER -> {
                    // `id>text]`: flowchart's asymmetric shape opens with '>' and closes with ']'.
                    if (mode == Mode.FLOWCHART && text[start] == '>' && bracketStack.isEmpty() && !inPipeLabel &&
                        previousType == MermaidTokenTypes.IDENTIFIER && previousEnd == start
                    ) {
                        bracketStack.addLast(Open(MermaidTokenTypes.LBRACKET, start, '>'))
                    }
                }
                MermaidTokenTypes.COLON -> {
                    val freeText = (mode != Mode.FLOWCHART || lineFirstWord in ACCESSIBILITY_KEYWORDS) && topLevel
                    if (freeText) resumeAt = lineEnd(text, end)
                }
                MermaidTokenTypes.SEMICOLON -> {
                    if (mode == Mode.FLOWCHART && bracketStack.isEmpty()) atStatementStart = true
                }
                MermaidTokenTypes.IDENTIFIER -> {
                    val firstOnLine = atStatementStart
                    if (firstOnLine && lineFirstWord == null) lineFirstWord = text.substring(start, end)
                    when (mode) {
                        Mode.FLOWCHART -> if (firstOnLine) {
                            if (text.isWord(start, end, "subgraph")) {
                                subgraphStack.addLast(start)
                            } else if (text.isWord(start, end, "end") && subgraphStack.isNotEmpty()) {
                                subgraphStack.removeLast()
                            }
                        }
                        Mode.SEQUENCE -> if (bracketStack.isEmpty()) {
                            if (firstOnLine && lineFirstWord in SEQUENCE_LABEL_KEYWORDS) {
                                resumeAt = lineEnd(text, end)
                            } else if (!firstOnLine && lineFirstWord in SEQUENCE_ALIAS_KEYWORDS && text.isWord(start, end, "as")) {
                                resumeAt = lineEnd(text, end)
                            }
                        }
                        else -> {}
                    }
                }
                else -> {}
            }

            if (type != MermaidTokenTypes.WHITESPACE && type != MermaidTokenTypes.COMMENT &&
                !(type == MermaidTokenTypes.SEMICOLON && atStatementStart)
            ) {
                atStatementStart = false
            }
            previousType = type
            previousEnd = end

            if (resumeAt >= 0) {
                lexer.start(text, resumeAt.coerceIn(end, length), length, 0)
            } else {
                lexer.advance()
            }
        }

        for (open in bracketStack) {
            issues.add(MermaidIssue(open.offset, 1, "Unmatched opening '${open.symbol}'"))
        }
        for (offset in subgraphStack) {
            issues.add(MermaidIssue(offset, "subgraph".length, "Missing matching 'end' for this subgraph"))
        }
        return issues.sortedBy { it.offset }
    }

    private fun modeFor(header: String?): Mode = when (header) {
        in FLOWCHART_HEADERS -> Mode.FLOWCHART
        in SEQUENCE_HEADERS -> Mode.SEQUENCE
        in CLASS_HEADERS -> Mode.CLASS
        in UNCHECKED_HEADERS -> Mode.UNCHECKED
        else -> Mode.FLOWCHART
    }

    /** The first identifier of the diagram, skipping a BOM, whitespace, comments and directives. */
    private fun headerWord(text: CharSequence, from: Int, lastDirectiveClose: Int): String? {
        val lexer = MermaidLexer()
        lexer.start(text, from, text.length, 0)
        while (true) {
            val type = lexer.tokenType ?: return null
            when {
                type == MermaidTokenTypes.WHITESPACE -> lexer.advance()
                type == MermaidTokenTypes.COMMENT -> {
                    val resume = directiveEnd(text, lexer.tokenStart, lexer.tokenEnd, lastDirectiveClose)
                    if (resume >= 0) lexer.start(text, resume, text.length, 0) else lexer.advance()
                }
                type == MermaidTokenTypes.OTHER && text[lexer.tokenStart] == '﻿' -> lexer.advance()
                type == MermaidTokenTypes.IDENTIFIER -> return text.substring(lexer.tokenStart, lexer.tokenEnd)
                else -> return null
            }
        }
    }

    /**
     * Where checking starts: after a `---` front matter block at the very top (YAML, never
     * diagram syntax), or 0 when there is none or it never closes.
     */
    private fun frontMatterEnd(text: CharSequence): Int {
        var i = 0
        if (i < text.length && text[i] == '﻿') i++
        if (!text.startsWith("---", i)) return 0
        val firstLineEnd = lineEnd(text, i)
        if (text.substring(i, firstLineEnd).trim() != "---" || firstLineEnd >= text.length) return 0
        var lineStart = firstLineEnd + 1
        while (lineStart < text.length) {
            val end = lineEnd(text, lineStart)
            if (text.substring(lineStart, end).trim() == "---") return end
            lineStart = end + 1
        }
        return 0
    }

    /**
     * For a comment token that opens a `%%{` directive closing on a later line, the offset
     * just past its `}%%`; -1 for any other comment (it ends at the line end by itself).
     * [lastDirectiveClose] keeps a file full of unclosed directives linear.
     */
    private fun directiveEnd(text: CharSequence, start: Int, end: Int, lastDirectiveClose: Int): Int {
        if (!text.startsWith("%%{", start) || lastDirectiveClose < start) return -1
        val closing = text.indexOf("}%%", start)
        if (closing < 0 || closing + 3 <= end) return -1
        return closing + 3
    }

    private fun lineEnd(text: CharSequence, from: Int): Int {
        val newline = text.indexOf('\n', from)
        return if (newline < 0) text.length else newline
    }

    private fun CharSequence.containsNewline(start: Int, end: Int): Boolean {
        for (i in start until end) if (this[i] == '\n') return true
        return false
    }

    /** Compares characters, never CharSequence.equals (which differs between implementations). */
    private fun CharSequence.isWord(start: Int, end: Int, word: String): Boolean {
        if (end - start != word.length) return false
        for (i in word.indices) if (this[start + i] != word[i]) return false
        return true
    }

    private fun closeBracket(
        stack: ArrayDeque<Open>,
        expectedOpener: IElementType,
        closerSymbol: String,
        closerOffset: Int,
        issues: MutableList<MermaidIssue>,
    ): Boolean {
        if (stack.isEmpty() || stack.last().type != expectedOpener) {
            issues.add(MermaidIssue(closerOffset, 1, "Unmatched closing '$closerSymbol'"))
            return false
        }
        stack.removeLast()
        return true
    }
}
