package dev.gaphunter.mermaidcompanion.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.mermaidcompanion.detection.MermaidFileDetector
import javax.swing.Icon

/**
 * Implements [FileTypeIdentifiableByVirtualFile] + `order="first"` in
 * plugin.xml proactively, same defensive call already made in
 * cmake-companion -- see SDK_GOTCHAS.md SS10 for the real 5-round
 * investigation (nginx-companion) that first uncovered this race against
 * bundled `FileTypeIdentifiableByVirtualFile` implementations.
 */
object MermaidFileType : LanguageFileType(MermaidLanguage), FileTypeIdentifiableByVirtualFile {
    // Kept distinct from the Language ID ("MermaidCompanion") on
    // purpose, but changed here too for consistency and to avoid a
    // future FileType-name collision with JetBrains's bundled Mermaid
    // support -- see the comment on MermaidLanguage for the full story.
    override fun getName(): String = "MermaidCompanion"
    override fun getDescription(): String = "Mermaid diagram source"
    override fun getDefaultExtension(): String = "mmd"
    override fun getIcon(): Icon? = null

    override fun isMyFileType(file: VirtualFile): Boolean = MermaidFileDetector.isMermaidFile(file.name)
}
