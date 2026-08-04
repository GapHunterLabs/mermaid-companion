package dev.gaphunter.mermaidcompanion.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.mermaidcompanion.inspection.MermaidSyntaxChecker

/** Runs [MermaidSyntaxChecker] once per file (guarded on `element is
 * PsiFile`, since the check is whole-file, not per-token) and turns each
 * issue into a real error annotation. */
class MermaidSyntaxAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        for (issue in MermaidSyntaxChecker.check(element.text)) {
            holder.newAnnotation(HighlightSeverity.ERROR, issue.message)
                .range(TextRange(issue.offset, issue.offset + issue.length))
                .create()
        }
    }
}
