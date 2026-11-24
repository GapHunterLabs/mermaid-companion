package dev.gaphunter.mermaidcompanion.preview

import com.intellij.ide.util.PropertiesComponent

/**
 * mermaid.js's built-in themes, chosen independently of the IDE's editor
 * theme -- a dark editor with a light, printable diagram is exactly what
 * reviewers of the bundled Mermaid plugin asked for. Remembered IDE-wide.
 */
enum class PreviewTheme(val id: String, val displayName: String) {
    DEFAULT("default", "Default"),
    NEUTRAL("neutral", "Neutral (printable)"),
    FOREST("forest", "Forest"),
    DARK("dark", "Dark"),
    NEO("neo", "Neo"),
    NEO_DARK("neo-dark", "Neo Dark"),
    REDUX("redux", "Redux"),
    REDUX_DARK("redux-dark", "Redux Dark"),
    REDUX_COLOR("redux-color", "Redux Color"),
    REDUX_DARK_COLOR("redux-dark-color", "Redux Dark Color"),
    HIGH_CONTRAST("high-contrast", "High Contrast (printable)"),
    BLUEPRINT("blueprint", "Blueprint"),
    MONOCHROME("monochrome", "Monochrome");

    companion object {
        private const val KEY = "dev.gaphunter.mermaidcompanion.previewTheme"

        fun fromId(id: String?): PreviewTheme = entries.firstOrNull { it.id == id } ?: DEFAULT

        var current: PreviewTheme
            get() = fromId(PropertiesComponent.getInstance().getValue(KEY))
            set(value) = PropertiesComponent.getInstance().setValue(KEY, value.id, DEFAULT.id)
    }
}
