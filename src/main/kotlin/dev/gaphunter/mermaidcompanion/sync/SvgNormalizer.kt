package dev.gaphunter.mermaidcompanion.sync

/**
 * Makes an exported SVG byte-identical across renders of the same diagram.
 *
 * mermaid.js puts the id it was given into the SVG (the root `id`, its
 * scoped CSS selectors and marker ids), and the preview gives every render
 * a new one. Left as is, saving an unchanged diagram would rewrite the file
 * with different bytes every time -- a diff in version control for a diagram
 * that did not change. A fixed id removes that.
 *
 * Icons (architecture diagrams) carry ids from Iconify, which start with a
 * prefix made of the time and a random number, once per page. Every export
 * renders in a page of its own, so the prefix changes every time: those ids
 * are renumbered in the order the SVG declares them, and every reference to
 * one follows it. Text that only looks like such an id is left alone.
 */
object SvgNormalizer {
    private val RENDER_ID = Regex("""gap-hunter-mermaid-(?:svg|png)-\d+""")
    const val STABLE_ID = "mermaid-diagram"

    private val ICONIFY_DECLARATION = Regex("""\bid="(IconifyId[0-9a-f]+)"""")
    private val ICONIFY_TOKEN = Regex("""IconifyId[0-9a-f]+(?![0-9A-Za-z_-])""")

    fun normalize(svg: String): String {
        val fixed = RENDER_ID.replace(svg, STABLE_ID)
        val renamed = LinkedHashMap<String, String>()
        for (match in ICONIFY_DECLARATION.findAll(fixed)) {
            renamed.getOrPut(match.groupValues[1]) { "IconifyId-${renamed.size}" }
        }
        if (renamed.isEmpty()) return fixed
        return ICONIFY_TOKEN.replace(fixed) { renamed[it.value] ?: it.value }
    }
}
