package dev.gaphunter.mermaidcompanion.coexistence

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import dev.gaphunter.mermaidcompanion.lang.MermaidFileType

/**
 * Who edits `.mmd`/`.mermaid` files when JetBrains's Mermaid plugin is also
 * present: this plugin (the default, unchanged behavior) or JetBrains's
 * editor. Only ever changed by an explicit user click. The Preview tab
 * (zoom, themes, export) stays available either way, since it opens by file
 * name, not by file type.
 *
 * Deferring does two things: [MermaidFileType.isMyFileType] stops claiming
 * the files, and the `*.mmd`/`*.mermaid` associations move to the other
 * file type through FileTypeManager -- the same persisted mapping the user
 * could set by hand in Settings | Editor | File Types.
 */
object EditorOwnership {
    val EXTENSIONS = listOf("mmd", "mermaid")

    private const val DEFER_KEY = "dev.gaphunter.mermaidcompanion.deferEditingToNative"
    private const val NOTICE_KEY = "dev.gaphunter.mermaidcompanion.coexistenceNoticeShown"

    var deferEditingToNative: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(DEFER_KEY, false)
        private set(value) = PropertiesComponent.getInstance().setValue(DEFER_KEY, value)

    var noticeShown: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(NOTICE_KEY, false)
        set(value) = PropertiesComponent.getInstance().setValue(NOTICE_KEY, value)

    /**
     * Read on every file-type check of a `.mmd` file. The stored choice is
     * checked first, so the file-type registry is only consulted for users
     * who actually handed editing to JetBrains's editor.
     */
    fun defersToNative(): Boolean = deferEditingToNative && NativeMermaid.isEnabled()

    /** The one-time choice is offered only while the user hasn't decided anything yet. */
    fun shouldOfferChoice(nativeEnabled: Boolean, deferred: Boolean, noticeShown: Boolean): Boolean =
        nativeEnabled && !deferred && !noticeShown

    /** [editor] is JetBrains's Mermaid file type in practice; a parameter so tests can use a stand-in. */
    fun deferTo(editor: FileType) {
        deferEditingToNative = true // first, so isMyFileType already says no when files are re-detected
        moveAssociations(from = MermaidFileType, to = editor)
    }

    fun reclaimFrom(editor: FileType?) {
        deferEditingToNative = false
        moveAssociations(from = editor, to = MermaidFileType)
    }

    private fun moveAssociations(from: FileType?, to: FileType) {
        ApplicationManager.getApplication().runWriteAction {
            val fileTypes = FileTypeManager.getInstance()
            for (extension in EXTENSIONS) {
                val matcher = ExtensionFileNameMatcher(extension)
                if (from != null) fileTypes.removeAssociation(from, matcher)
                fileTypes.associate(to, matcher)
            }
        }
    }
}
