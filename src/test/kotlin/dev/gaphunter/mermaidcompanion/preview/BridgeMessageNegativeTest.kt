package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Malformed and hostile payloads from the preview page never reach the file system. */
class BridgeMessageNegativeTest {

    @Test
    fun `payloads this plugin never sends are ignored`() {
        for (payload in listOf("", ":", "svg", "png", "unknown:x", "SVG:<svg/>", " svg:<svg/>", "zoom", "rendered", "\u0000:x")) {
            val parsed = BridgeMessage.parse(payload)
            assertTrue("'$payload' -> $parsed", parsed == null || parsed is BridgeMessage.Failure || parsed is BridgeMessage.SyncFailure)
        }
        assertNull(BridgeMessage.parse("unknown:x"))
        assertNull(BridgeMessage.parse(""))
    }

    @Test
    fun `zoom and token values that are not integers are ignored`() {
        for (body in listOf("", "NaN", "Infinity", "-Infinity", "1.5", "1e3", "99999999999", " 5", "5 ", "0x10")) {
            assertNull("zoom:$body", BridgeMessage.parse("zoom:$body"))
            assertNull("rendered:$body", BridgeMessage.parse("rendered:$body"))
            assertNull("rendersuperseded:$body", BridgeMessage.parse("rendersuperseded:$body"))
        }
        assertEquals(BridgeMessage.Zoom(-5), BridgeMessage.parse("zoom:-5"))
        assertEquals(BridgeMessage.Zoom(0), BridgeMessage.parse("zoom:0"))
    }

    @Test
    fun `an empty or truncated PNG is a failure with a clear message`() {
        // An over-sized canvas answers "data:," -- the page then sends an empty PNG body.
        val empty = BridgeMessage.parse("syncpng:") as BridgeMessage.SyncFailure
        assertTrue(empty.message, empty.message.contains("empty or malformed"))
        val sigOnly = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        assertTrue(BridgeMessage.parse("png:$sigOnly") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("png:!!!not base64!!!") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("syncpng:${Base64.getEncoder().encodeToString("<svg/>".toByteArray())}") is BridgeMessage.SyncFailure)
    }

    @Test
    fun `an SVG must start with an svg element`() {
        assertTrue(BridgeMessage.parse("svg:") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("svg:<html>") is BridgeMessage.Failure)
        assertTrue(BridgeMessage.parse("syncsvg:<?xml version=\"1.0\"?><svg/>") is BridgeMessage.SyncFailure)
        assertTrue(BridgeMessage.parse("syncsvg:   <svg/>") is BridgeMessage.SyncSvg)
    }

    @Test
    fun `a render failure needs a usable token`() {
        assertNull(BridgeMessage.parse("renderfailed:"))
        assertNull(BridgeMessage.parse("renderfailed::message"))
        assertNull(BridgeMessage.parse("renderfailed:abc:message"))
        // The page always sends "renderfailed:<token>:<message>"; without the second colon it is ignored.
        assertNull(BridgeMessage.parse("renderfailed:5"))
        assertEquals(BridgeMessage.RenderFailed(5, "The diagram doesn't render."), BridgeMessage.parse("renderfailed:5:"))
    }

    @Test
    fun `a very large SVG payload is accepted whole`() {
        val big = "<svg>" + "x".repeat(8_000_000) + "</svg>"
        val parsed = BridgeMessage.parse("syncsvg:$big") as BridgeMessage.SyncSvg
        assertEquals(big.length, parsed.markup.length)
    }
}
