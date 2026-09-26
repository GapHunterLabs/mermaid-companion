package dev.gaphunter.mermaidcompanion.sync

import dev.gaphunter.mermaidcompanion.preview.ExportFiles
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

sealed interface WriteResult {
    data class Written(val path: Path) : WriteResult
    data class Unchanged(val path: Path) : WriteResult
    data class Failed(val message: String) : WriteResult
}

/**
 * Writes an export next to its source file: `order-flow.mmd` ->
 * `order-flow.svg`, the same name the manual export suggests. Only touches
 * the disk when the content actually changed (so an unchanged diagram leaves
 * the file, and version control, alone), and never leaves a half-written
 * file: the bytes go to a temp file in the same folder and are moved over
 * the target.
 */
object SyncWriter {

    fun targetFor(sourceDir: Path, sourceName: String, format: ExportFormat): Path? {
        val name = ExportFiles.suggestedName(sourceName, format.extension)
        // A file name never carries a separator; if it somehow does, refuse
        // rather than write outside the source folder.
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        return sourceDir.resolve(name)
    }

    fun write(target: Path, bytes: ByteArray): WriteResult {
        return try {
            if (Files.isRegularFile(target) && Files.size(target) == bytes.size.toLong() && Files.readAllBytes(target).contentEquals(bytes)) {
                return WriteResult.Unchanged(target)
            }
            val parent = target.parent ?: return WriteResult.Failed("No folder to write ${target.fileName} into.")
            val temp = Files.createTempFile(parent, ".mermaid-sync-", ".tmp")
            try {
                Files.write(temp, bytes)
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
            WriteResult.Written(target)
        } catch (e: IOException) {
            WriteResult.Failed("Couldn't write ${target.fileName}: ${e.message}")
        }
    }
}
