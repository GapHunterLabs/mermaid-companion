package dev.gaphunter.mermaidcompanion.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SvgNormalizerTest {

    private fun svgFor(renderId: String) =
        """<svg id="$renderId" xmlns="http://www.w3.org/2000/svg"><style>#$renderId .node{fill:#eee}#$renderId marker{fill:#333}</style>""" +
            """<defs><marker id="${renderId}_flowchart-v2-pointEnd"/></defs><g class="node"><text>Start</text></g></svg>"""

    @Test
    fun `two renders of the same diagram export identical bytes`() {
        val first = SvgNormalizer.normalize(svgFor("gap-hunter-mermaid-svg-3"))
        val second = SvgNormalizer.normalize(svgFor("gap-hunter-mermaid-svg-47"))

        assertEquals(first, second)
    }

    @Test
    fun `every use of the render id is replaced, including selectors and marker ids`() {
        val normalized = SvgNormalizer.normalize(svgFor("gap-hunter-mermaid-svg-12"))

        assertFalse(normalized.contains("gap-hunter-mermaid"))
        assertEquals(4, Regex(SvgNormalizer.STABLE_ID).findAll(normalized).count())
    }

    @Test
    fun `the id the PNG render uses is normalized too`() {
        assertEquals("<svg id=\"mermaid-diagram\"/>", SvgNormalizer.normalize("<svg id=\"gap-hunter-mermaid-png-9\"/>"))
    }

    @Test
    fun `everything else in the diagram is left untouched`() {
        val text = "<svg><g><text>gap-hunter is a label, not an id</text></g></svg>"

        assertEquals(text, SvgNormalizer.normalize(text))
    }
}
