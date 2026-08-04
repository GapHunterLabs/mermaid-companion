package dev.gaphunter.mermaidcompanion.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import dev.gaphunter.mermaidcompanion.keywords.MermaidKeywords
import dev.gaphunter.mermaidcompanion.lang.MermaidHighlighterColors
import dev.gaphunter.mermaidcompanion.lang.MermaidTokenTypes

/** Colors an IDENTIFIER token distinctly when its text is a recognized
 * Mermaid structural keyword (`flowchart`, `subgraph`, `end`,
 * `sequenceDiagram`, `classDiagram`, etc.) -- same split of responsibility
 * as NginxDirectiveAnnotator/CMakeCommandAnnotator: the lexer stays dumb,
 * this Annotator decides meaning from token text. Mermaid keywords are
 * case-sensitive, so no lowercasing here (unlike CMakeCommandIndex). */
class MermaidKeywordAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node?.elementType != MermaidTokenTypes.IDENTIFIER) return
        if (!MermaidKeywords.isKeyword(element.text)) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .textAttributes(MermaidHighlighterColors.KNOWN_KEYWORD)
            .range(element.textRange)
            .create()
    }
}
