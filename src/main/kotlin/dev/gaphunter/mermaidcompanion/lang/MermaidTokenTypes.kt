package dev.gaphunter.mermaidcompanion.lang

import com.intellij.psi.tree.IElementType

class MermaidTokenType(debugName: String) : IElementType(debugName, MermaidLanguage)

object MermaidTokenTypes {
    val WHITESPACE = MermaidTokenType("MERMAID_WHITESPACE")
    val COMMENT = MermaidTokenType("MERMAID_COMMENT")
    val STRING = MermaidTokenType("MERMAID_STRING")

    /** A `"` that never found its closing quote before end of line/file --
     * a distinct token type (not just STRING) so [dev.gaphunter.mermaidcompanion.inspection.MermaidSyntaxChecker]
     * can flag it without re-implementing the same escape-aware scan. */
    val UNTERMINATED_STRING = MermaidTokenType("MERMAID_UNTERMINATED_STRING")

    val LBRACKET = MermaidTokenType("MERMAID_LBRACKET")
    val RBRACKET = MermaidTokenType("MERMAID_RBRACKET")
    val LPAREN = MermaidTokenType("MERMAID_LPAREN")
    val RPAREN = MermaidTokenType("MERMAID_RPAREN")
    val LBRACE = MermaidTokenType("MERMAID_LBRACE")
    val RBRACE = MermaidTokenType("MERMAID_RBRACE")
    val PIPE = MermaidTokenType("MERMAID_PIPE")
    val COLON = MermaidTokenType("MERMAID_COLON")
    val SEMICOLON = MermaidTokenType("MERMAID_SEMICOLON")
    val ARROW = MermaidTokenType("MERMAID_ARROW")
    val IDENTIFIER = MermaidTokenType("MERMAID_IDENTIFIER")
    val OTHER = MermaidTokenType("MERMAID_OTHER")
    val BAD_CHARACTER = MermaidTokenType("MERMAID_BAD_CHARACTER")
}
