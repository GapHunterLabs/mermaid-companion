package dev.gaphunter.mermaidcompanion.preview

import com.intellij.ide.util.PropertiesComponent

/**
 * How a PNG is drawn: its scale (1x, 2x or 3x the diagram's size) and whether the background
 * stays transparent instead of taking the theme's color. Used by the PNG export, Export on Save
 * and the clipboard. Remembered IDE-wide; a stored value that is not one of these falls back to
 * the default instead of reaching the page.
 */
data class PngOptions(val scale: Int = DEFAULT_SCALE, val transparent: Boolean = false) {
    init {
        require(scale in SCALES) { "PNG scale must be one of $SCALES, was $scale" }
    }

    /** The object literal companion-preview.js reads: only numbers and booleans, nothing from the user. */
    fun toJs(): String = "{scale: $scale, transparent: $transparent}"

    /** Whether drawing this needs Pro: a scale above [FREE_MAX_SCALE] or a transparent background. */
    val needsPro: Boolean get() = scale > FREE_MAX_SCALE || transparent

    /**
     * What may actually be drawn. Without Pro, 3x becomes 2x and the background stays opaque, so a
     * choice stored while a license was active never outlives it.
     */
    fun forTier(pro: Boolean): PngOptions = if (pro) this else PngOptions(minOf(scale, FREE_MAX_SCALE), false)

    companion object {
        val SCALES = listOf(1, 2, 3)
        const val DEFAULT_SCALE = 2
        /** The largest scale without Pro. */
        const val FREE_MAX_SCALE = 2
        private const val SCALE_KEY = "dev.gaphunter.mermaidcompanion.pngScale"
        private const val TRANSPARENT_KEY = "dev.gaphunter.mermaidcompanion.pngTransparent"

        fun parseScale(stored: String?): Int = stored?.trim()?.toIntOrNull()?.takeIf { it in SCALES } ?: DEFAULT_SCALE

        fun parseTransparent(stored: String?): Boolean = stored?.trim() == "true"

        var current: PngOptions
            get() {
                val props = PropertiesComponent.getInstance()
                return PngOptions(parseScale(props.getValue(SCALE_KEY)), parseTransparent(props.getValue(TRANSPARENT_KEY)))
            }
            set(value) {
                val props = PropertiesComponent.getInstance()
                props.setValue(SCALE_KEY, value.scale.toString(), DEFAULT_SCALE.toString())
                props.setValue(TRANSPARENT_KEY, value.transparent.toString(), "false")
            }
    }
}
