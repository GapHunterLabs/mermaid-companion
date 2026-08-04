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
 * A pure function over text (not a PsiElement walk), same pattern as
 * CMakeParenChecker -- directly unit-testable, and
 * [dev.gaphunter.mermaidcompanion.highlighting.MermaidSyntaxAnnotator] is
 * a thin per-file wrapper around it.
 */
object MermaidSyntaxChecker {
    fun check(text: CharSequence): List<MermaidIssue> {
        val lexer = MermaidLexer()
        lexer.start(text, 0, text.length, 0)

        val bracketStack = ArrayDeque<Pair<IElementType, Int>>()
        val subgraphStack = ArrayDeque<Int>()
        val issues = mutableListOf<MermaidIssue>()

        while (true) {
            val type = lexer.tokenType ?: break
            when (type) {
                MermaidTokenTypes.UNTERMINATED_STRING -> issues.add(
                    MermaidIssue(lexer.tokenStart, lexer.tokenEnd - lexer.tokenStart, "Unterminated string literal")
                )
                MermaidTokenTypes.LBRACKET, MermaidTokenTypes.LPAREN, MermaidTokenTypes.LBRACE ->
                    bracketStack.addLast(type to lexer.tokenStart)
                MermaidTokenTypes.RBRACKET ->
                    closeBracket(bracketStack, MermaidTokenTypes.LBRACKET, "]", lexer.tokenStart, issues)
                MermaidTokenTypes.RPAREN ->
                    closeBracket(bracketStack, MermaidTokenTypes.LPAREN, ")", lexer.tokenStart, issues)
                MermaidTokenTypes.RBRACE ->
                    closeBracket(bracketStack, MermaidTokenTypes.LBRACE, "}", lexer.tokenStart, issues)
                MermaidTokenTypes.IDENTIFIER -> {
                    val text0 = text.subSequence(lexer.tokenStart, lexer.tokenEnd)
                    if (text0 == "subgraph") {
                        subgraphStack.addLast(lexer.tokenStart)
                    } else if (text0 == "end" && subgraphStack.isNotEmpty()) {
                        subgraphStack.removeLast()
                    }
                }
                else -> {}
            }
            lexer.advance()
        }

        for ((openType, offset) in bracketStack) {
            issues.add(MermaidIssue(offset, 1, "Unmatched opening '${symbolFor(openType)}'"))
        }
        for (offset in subgraphStack) {
            issues.add(MermaidIssue(offset, "subgraph".length, "Missing matching 'end' for this subgraph"))
        }
        return issues.sortedBy { it.offset }
    }

    private fun closeBracket(
        stack: ArrayDeque<Pair<IElementType, Int>>,
        expectedOpener: IElementType,
        closerSymbol: String,
        closerOffset: Int,
        issues: MutableList<MermaidIssue>,
    ) {
        if (stack.isEmpty() || stack.last().first != expectedOpener) {
            issues.add(MermaidIssue(closerOffset, 1, "Unmatched closing '$closerSymbol'"))
        } else {
            stack.removeLast()
        }
    }

    private fun symbolFor(openType: IElementType): String = when (openType) {
        MermaidTokenTypes.LBRACKET -> "["
        MermaidTokenTypes.LPAREN -> "("
        MermaidTokenTypes.LBRACE -> "{"
        else -> "?"
    }
}
