package dev.gaphunter.mermaidcompanion.sync

/**
 * Makes an exported SVG byte-identical across renders of the same diagram.
 *
 * mermaid.js puts the id it was given into the SVG (the root `id`, its
 * scoped CSS selectors and marker ids), and the preview gives every render
 * a new one. Left as is, saving an unchanged diagram would rewrite the file
 * with different bytes every time -- a diff in version control for a diagram
 * that did not change. A fixed id removes that.
 */
object SvgNormalizer {
    private val RENDER_ID = Regex("""gap-hunter-mermaid-(?:svg|png)-\d+""")
    const val STABLE_ID = "mermaid-diagram"

    fun normalize(svg: String): String = RENDER_ID.replace(svg, STABLE_ID)
}
