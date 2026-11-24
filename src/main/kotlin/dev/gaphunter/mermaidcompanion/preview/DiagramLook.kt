package dev.gaphunter.mermaidcompanion.preview

import com.intellij.ide.util.PropertiesComponent

/**
 * How mermaid.js draws shapes and lines (its `look` setting): Classic, Hand-Drawn (sketched, with a
 * fixed seed so the same diagram always draws the same strokes) or Neo. Chosen independently of the
 * theme and remembered IDE-wide; a look the diagram sets in its own front matter wins.
 */
enum class DiagramLook(val id: String, val displayName: String) {
    CLASSIC("classic", "Classic"),
    HAND_DRAWN("handDrawn", "Hand-Drawn"),
    NEO("neo", "Neo");

    companion object {
        private const val KEY = "dev.gaphunter.mermaidcompanion.diagramLook"

        fun fromId(id: String?): DiagramLook = entries.firstOrNull { it.id == id } ?: CLASSIC

        var current: DiagramLook
            get() = fromId(PropertiesComponent.getInstance().getValue(KEY))
            set(value) = PropertiesComponent.getInstance().setValue(KEY, value.id, CLASSIC.id)
    }
}
