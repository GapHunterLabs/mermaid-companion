package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class PreviewPartsTest {

    // --- BridgeMessage ----------------------------------------------------------

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2)

    @Test
    fun parsesAnSvgExport() {
        assertEquals(BridgeMessage.Svg("<svg id=\"a\"></svg>"), BridgeMessage.parse("svg:<svg id=\"a\"></svg>"))
    }

    @Test
    fun rejectsAnSvgExportThatIsNotSvg() {
        assertTrue(BridgeMessage.parse("svg:") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("svg:<html></html>") is BridgeMessage.Failure)
    }

    @Test
    fun decodesAPngExport() {
        val message = BridgeMessage.parse("png:" + Base64.getEncoder().encodeToString(png))
        assertArrayEquals(png, (message as BridgeMessage.Png).bytes)
    }

    @Test
    fun rejectsAPngExportThatIsNotAPng() {
        assertTrue(BridgeMessage.parse("png:not base64!") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("png:" + Base64.getEncoder().encodeToString("GIF89a".toByteArray())) is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("png:") is BridgeMessage.Failure)
    }

    @Test
    fun passesPageErrorsThrough() {
        assertEquals(BridgeMessage.Failure("PNG export isn't possible"), BridgeMessage.parse("error:PNG export isn't possible"))
        assertEquals(BridgeMessage.Failure("Export failed."), BridgeMessage.parse("error:"))
    }

    @Test
    fun parsesZoomAndIgnoresAnythingElse() {
        assertEquals(BridgeMessage.Zoom(125), BridgeMessage.parse("zoom:125"))
        assertNull(BridgeMessage.parse("zoom:abc"))
        assertNull(BridgeMessage.parse("hello:world"))
        assertNull(BridgeMessage.parse("no separator"))
    }

    // --- ExportFiles --------------------------------------------------------------

    @Test
    fun suggestsTheSourceNameWithTheExportExtension() {
        assertEquals("order-flow.svg", ExportFiles.suggestedName("order-flow.mmd", "svg"))
        assertEquals("diagram.png", ExportFiles.suggestedName("diagram.mermaid", "png"))
        assertEquals("a.b.svg", ExportFiles.suggestedName("a.b.mmd", "svg"))
        assertEquals("noext.svg", ExportFiles.suggestedName("noext", "svg"))
        assertEquals("diagram.svg", ExportFiles.suggestedName(".mmd", "svg"))
    }

    // --- PreviewTheme ---------------------------------------------------------------

    @Test
    fun themesAreMermaidsOwnAndFallBackToDefault() {
        val mermaidBuiltIns = setOf("default", "neutral", "forest", "dark", "base")
        assertTrue(PreviewTheme.entries.all { it.id in mermaidBuiltIns })
        assertEquals(PreviewTheme.entries.size, PreviewTheme.entries.map { it.id }.toSet().size)
        assertEquals(PreviewTheme.DARK, PreviewTheme.fromId("dark"))
        assertEquals(PreviewTheme.DEFAULT, PreviewTheme.fromId(null))
        assertEquals(PreviewTheme.DEFAULT, PreviewTheme.fromId("bogus"))
    }

    // --- PreviewPage <-> companion-preview.js contract ------------------------------

    @Test
    fun everyElementTheScriptNeedsIsInThePage() {
        val script = File("src/main/resources/preview/companion-preview.js").readText()
        val html = PreviewPage.html("/*mermaid*/", "/*companion*/")
        for (id in PreviewPage.ELEMENT_IDS) {
            assertTrue("page is missing #$id", html.contains("id=\"$id\""))
            assertTrue("script never looks up #$id", script.contains("getElementById('$id')"))
        }
        assertTrue("mermaid.js must load before the companion script",
            html.indexOf("/*mermaid*/") < html.indexOf("/*companion*/"))
    }

    @Test
    fun theCompanionScriptShipsInThePluginJar() {
        val script = PreviewPage.companionScript
        for (entryPoint in listOf("gapHunterRender", "gapHunterZoom", "gapHunterFit", "gapHunterActualSize",
                "gapHunterSetTheme", "gapHunterExportSvg", "gapHunterExportPng")) {
            assertTrue("companion-preview.js no longer defines $entryPoint", script.contains("root.$entryPoint = function"))
        }
    }
}
