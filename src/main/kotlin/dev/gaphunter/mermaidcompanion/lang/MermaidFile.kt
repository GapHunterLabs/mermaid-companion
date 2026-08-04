package dev.gaphunter.mermaidcompanion.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class MermaidFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, MermaidLanguage) {
    override fun getFileType() = MermaidFileType
    override fun toString(): String = "Mermaid File"
}
