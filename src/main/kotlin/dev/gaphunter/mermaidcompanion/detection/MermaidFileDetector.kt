package dev.gaphunter.mermaidcompanion.detection

/**
 * `.mmd` and `.mermaid` are the de facto standard extensions for standalone
 * Mermaid diagram files (used by mermaid-cli and most editor tooling) --
 * unambiguous by extension alone, no content sniffing needed the way
 * nginx-companion's `.conf`/cmake-companion's content fallback needed it.
 */
object MermaidFileDetector {
    private val EXTENSIONS = setOf("mmd", "mermaid")

    fun isMermaidFile(fileName: String): Boolean {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0) return false
        return fileName.substring(dot + 1).lowercase() in EXTENSIONS
    }
}
