package dev.gaphunter.mermaidcompanion.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Deliberately flat, same call as NginxParserDefinition/CMakeParserDefinition:
 * every token is a direct leaf of the file root. [dev.gaphunter.mermaidcompanion.inspection.MermaidSyntaxChecker]
 * does its own bracket/subgraph matching over the flat token stream instead
 * of relying on a real parse tree for that -- a full Mermaid grammar (three
 * different diagram-type sub-languages) is out of scope for v1's feature
 * set (highlighting + syntax validation, no visual diagram preview).
 */
class MermaidParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType(MermaidLanguage)
    }

    override fun createLexer(project: Project): Lexer = MermaidLexer()

    override fun createParser(project: Project): PsiParser = PsiParser { root, builder ->
        val marker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = TokenSet.create(MermaidTokenTypes.COMMENT)

    override fun getStringLiteralElements(): TokenSet =
        TokenSet.create(MermaidTokenTypes.STRING, MermaidTokenTypes.UNTERMINATED_STRING)

    override fun getWhitespaceTokens(): TokenSet =
        TokenSet.create(MermaidTokenTypes.WHITESPACE, TokenType.WHITE_SPACE)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = MermaidFile(viewProvider)

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
}
