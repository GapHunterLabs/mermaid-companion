package dev.gaphunter.mermaidcompanion.lang

/**
 * Parts of a diagram file that are configuration, not diagram: the front matter (`---` ... `---`
 * at the very top) and every directive (`%%{ ... }%%`, which may span several lines). The lexer
 * only knows a directive's first line as a comment, so anything that reads words from the diagram
 * (keyword colors, click-to-source) skips these ranges.
 */
object MermaidRegions {

    /** Where the front matter ends, or 0 when there is none (or it is never closed, like mermaid reads it). */
    fun frontMatterEnd(text: CharSequence): Int {
        val s = text.toString()
        if (s.lineSequence().firstOrNull()?.trim() != "---") return 0
        var offset = s.indexOf('\n').let { if (it < 0) return 0 else it + 1 }
        while (offset < s.length) {
            val end = s.indexOf('\n', offset).let { if (it < 0) s.length else it }
            if (s.substring(offset, end).trim() == "---") return minOf(s.length, end + 1)
            offset = end + 1
        }
        return 0
    }

    /** [start, end) of every directive; one left open runs to the end of the file. */
    fun directives(text: CharSequence): List<IntRange> {
        val s = text.toString()
        val out = ArrayList<IntRange>()
        var i = s.indexOf("%%{")
        while (i >= 0) {
            val close = s.indexOf("}%%", i + 3)
            val end = if (close < 0) s.length else close + 3
            out.add(i until end)
            if (close < 0) break
            i = s.indexOf("%%{", end)
        }
        return out
    }

    /** A predicate over offsets: true inside the front matter or a directive. */
    fun skipped(text: CharSequence): (Int) -> Boolean {
        val fm = frontMatterEnd(text)
        val ranges = directives(text)
        return { offset -> offset < fm || ranges.any { offset in it } }
    }
}
