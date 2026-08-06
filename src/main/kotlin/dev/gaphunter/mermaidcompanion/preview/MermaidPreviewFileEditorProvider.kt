package dev.gaphunter.mermaidcompanion.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.mermaidcompanion.detection.MermaidFileDetector

class MermaidPreviewFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = MermaidFileDetector.isMermaidFile(file.name)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = MermaidPreviewFileEditor(file)

    override fun getEditorTypeId(): String = "mermaid-companion-preview"

    // A "Preview" tab alongside the text editor, never a replacement --
    // same non-intrusive pattern as Spreadsheet Companion's
    // XlsxViewerEditorProvider.
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
