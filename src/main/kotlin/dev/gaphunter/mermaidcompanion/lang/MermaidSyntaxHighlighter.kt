package dev.gaphunter.mermaidcompanion.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

object MermaidHighlighterColors {
    val COMMENT: TextAttributesKey =
        createTextAttributesKey("MERMAID_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val STRING: TextAttributesKey =
        createTextAttributesKey("MERMAID_STRING", DefaultLanguageHighlighterColors.STRING)
    val BRACKETS: TextAttributesKey =
        createTextAttributesKey("MERMAID_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val PARENS: TextAttributesKey =
        createTextAttributesKey("MERMAID_PARENS", DefaultLanguageHighlighterColors.PARENTHESES)
    val BRACES: TextAttributesKey =
        createTextAttributesKey("MERMAID_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val ARROW: TextAttributesKey =
        createTextAttributesKey("MERMAID_ARROW", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val KNOWN_KEYWORD: TextAttributesKey =
        createTextAttributesKey("MERMAID_KNOWN_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val BAD_CHARACTER: TextAttributesKey =
        createTextAttributesKey("MERMAID_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
    val UNTERMINATED_STRING: TextAttributesKey =
        createTextAttributesKey("MERMAID_UNTERMINATED_STRING", HighlighterColors.BAD_CHARACTER)
}

class MermaidSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = MermaidLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        val key = when (tokenType) {
            MermaidTokenTypes.COMMENT -> MermaidHighlighterColors.COMMENT
            MermaidTokenTypes.STRING -> MermaidHighlighterColors.STRING
            MermaidTokenTypes.UNTERMINATED_STRING -> MermaidHighlighterColors.UNTERMINATED_STRING
            MermaidTokenTypes.LBRACKET, MermaidTokenTypes.RBRACKET -> MermaidHighlighterColors.BRACKETS
            MermaidTokenTypes.LPAREN, MermaidTokenTypes.RPAREN -> MermaidHighlighterColors.PARENS
            MermaidTokenTypes.LBRACE, MermaidTokenTypes.RBRACE -> MermaidHighlighterColors.BRACES
            MermaidTokenTypes.ARROW -> MermaidHighlighterColors.ARROW
            MermaidTokenTypes.BAD_CHARACTER -> MermaidHighlighterColors.BAD_CHARACTER
            else -> return emptyArray()
        }
        return arrayOf(key)
    }
}

class MermaidSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = MermaidSyntaxHighlighter()
}
