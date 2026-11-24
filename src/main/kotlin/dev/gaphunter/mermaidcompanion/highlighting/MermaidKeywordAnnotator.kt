package dev.gaphunter.mermaidcompanion.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import dev.gaphunter.mermaidcompanion.keywords.MermaidKeywordScanner
import dev.gaphunter.mermaidcompanion.lang.MermaidHighlighterColors
import dev.gaphunter.mermaidcompanion.lang.MermaidTokenTypes

/**
 * Colors the word that declares the diagram's type and the keywords of that type. The lexer stays
 * dumb; [MermaidKeywordScanner] decides from the whole file which identifiers are keywords (it needs
 * the diagram type, the label brackets and the colons around them), once per version of the file,
 * and each identifier is then a lookup.
 */
class MermaidKeywordAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node?.elementType != MermaidTokenTypes.IDENTIFIER) return
        val file = element.containingFile ?: return
        val result = CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(MermaidKeywordScanner.scan(file.viewProvider.contents), file)
        }
        val key = when (result.marks[element.textRange.startOffset]) {
            MermaidKeywordScanner.Kind.DIAGRAM_TYPE -> MermaidHighlighterColors.DIAGRAM_TYPE
            MermaidKeywordScanner.Kind.KEYWORD -> MermaidHighlighterColors.KNOWN_KEYWORD
            null -> return
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(key).range(element.textRange).create()
    }
}
