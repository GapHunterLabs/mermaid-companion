package dev.gaphunter.mermaidcompanion.lang

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Explicit editor highlighter wiring -- see NginxEditorHighlighterProvider/
 * CMakeEditorHighlighterProvider for why this is registered explicitly
 * rather than relying on automatic `Language` -> `SyntaxHighlighterFactory`
 * resolution for a content/extension-detected file type. */
class MermaidEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme
    ): EditorHighlighter = LexerEditorHighlighter(MermaidSyntaxHighlighter(), colors)
}
