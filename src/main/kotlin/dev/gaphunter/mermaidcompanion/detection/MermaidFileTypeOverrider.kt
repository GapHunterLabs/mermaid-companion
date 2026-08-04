package dev.gaphunter.mermaidcompanion.detection

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.mermaidcompanion.lang.MermaidFileType

/** Lowest-priority-tier fallback; [MermaidFileType.isMyFileType] (tier 1,
 * see SDK_GOTCHAS.md SS10) is what actually wins the race against bundled
 * file types in practice. Registered for defense in depth only. */
class MermaidFileTypeOverrider : FileTypeRegistry.FileTypeDetector {
    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? =
        if (MermaidFileDetector.isMermaidFile(file.name)) MermaidFileType else null

    @Deprecated("Overrides a deprecated platform member; still the correct hook for cache invalidation.")
    override fun getVersion(): Int = 1
}
