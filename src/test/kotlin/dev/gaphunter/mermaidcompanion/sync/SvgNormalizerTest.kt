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

    private fun iconSvg(prefix: String) =
        """<svg id="mermaid-diagram"><defs><path id="${prefix}3" d="m1"/><path id="${prefix}30" d="m2"/></defs>""" +
            """<use href="#${prefix}3"/><use xlink:href="#${prefix}30"/><g fill="url(#${prefix}30)"/></svg>"""

    @Test
    fun `Iconify ids from two pages export identical bytes`() {
        assertEquals(
            SvgNormalizer.normalize(iconSvg("IconifyId1a20b47addf67cae")),
            SvgNormalizer.normalize(iconSvg("IconifyId1a20b47b01db418f")),
        )
    }

    @Test
    fun `Iconify ids that share a prefix stay distinct and every reference follows its own id`() {
        assertEquals(
            """<svg id="mermaid-diagram"><defs><path id="IconifyId-0" d="m1"/><path id="IconifyId-1" d="m2"/></defs>""" +
                """<use href="#IconifyId-0"/><use xlink:href="#IconifyId-1"/><g fill="url(#IconifyId-1)"/></svg>""",
            SvgNormalizer.normalize(iconSvg("IconifyId1a2b")),
        )
    }

    @Test
    fun `normalizing twice changes nothing more`() {
        val once = SvgNormalizer.normalize(iconSvg("IconifyIdabc"))

        assertEquals(once, SvgNormalizer.normalize(once))
    }

    @Test
    fun `text that only looks like an Iconify id is left alone`() {
        val svg = """<svg><text>IconifyIdabc123 is my label</text><path id="IconifyIdff1"/><use href="#IconifyIdff1"/></svg>"""

        assertEquals(
            """<svg><text>IconifyIdabc123 is my label</text><path id="IconifyId-0"/><use href="#IconifyId-0"/></svg>""",
            SvgNormalizer.normalize(svg),
        )
    }

    @Test
    fun `an SVG without Iconify ids and an empty string pass through`() {
        assertEquals("", SvgNormalizer.normalize(""))
        assertEquals("<svg><text>IconifyId</text></svg>", SvgNormalizer.normalize("<svg><text>IconifyId</text></svg>"))
    }

    @Test
    fun `everything else in the diagram is left untouched`() {
        val text = "<svg><g><text>gap-hunter is a label, not an id</text></g></svg>"

        assertEquals(text, SvgNormalizer.normalize(text))
    }
}
