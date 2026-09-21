package dev.gaphunter.mermaidcompanion.preview

/** File naming for exported diagrams: `order-flow.mmd` -> `order-flow.svg`. */
object ExportFiles {
    fun suggestedName(sourceFileName: String, extension: String): String {
        val base = sourceFileName.substringBeforeLast('.', sourceFileName).ifBlank { "diagram" }
        return "$base.$extension"
    }
}
