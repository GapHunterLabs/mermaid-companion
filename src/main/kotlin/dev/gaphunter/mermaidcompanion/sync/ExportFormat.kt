package dev.gaphunter.mermaidcompanion.sync

/** A diagram export format the Pro "Export on Save" can keep up to date. */
enum class ExportFormat(val extension: String, val label: String) {
    SVG("svg", "SVG"),
    PNG("png", "PNG"),
}
