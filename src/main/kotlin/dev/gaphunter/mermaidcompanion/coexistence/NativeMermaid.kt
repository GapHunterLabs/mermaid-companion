package dev.gaphunter.mermaidcompanion.coexistence

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager

/**
 * JetBrains's own Mermaid plugin (`com.intellij.mermaid`): free on
 * Marketplace, and bundled with IntelliJ IDEA from 2026.2. It claims the
 * same `.mmd`/`.mermaid` extensions as this plugin, and the platform gives
 * a conflicting extension to the non-bundled plugin -- this one -- so
 * without a way to step aside, installing Mermaid Companion would silently
 * take those files away from the richer bundled editor.
 *
 * Detected by the file type it registers ("Mermaid"), which exists only
 * while that plugin is loaded and enabled. Not by plugin id: from 2025.2
 * `PluginId` is a Kotlin class, and `PluginId.getId(...)` compiled against
 * it resolves through a companion object that 2024.3 and 2025.1 don't have
 * (NoSuchFieldError there, caught by verifyPlugin).
 */
object NativeMermaid {
    const val FILE_TYPE_NAME = "Mermaid"

    /** Tests only: there is no JetBrains Mermaid plugin in the test platform. */
    @Volatile
    internal var enabledOverride: Boolean? = null

    fun fileType(): FileType? = FileTypeManager.getInstance().findFileTypeByName(FILE_TYPE_NAME)

    fun isEnabled(): Boolean = enabledOverride ?: (fileType() != null)
}
