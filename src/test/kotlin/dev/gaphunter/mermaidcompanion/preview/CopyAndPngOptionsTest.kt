package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/** PNG scale and transparency, the Markdown code block, the clipboard image and the copy messages of the bridge. */
class CopyAndPngOptionsTest {

    private fun png(w: Int, h: Int): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", it)
    }.toByteArray()

    // --- PngOptions -----------------------------------------------------------------------------

    @Test
    fun `every allowed scale builds, and the page literal holds only numbers and booleans`() {
        for (scale in PngOptions.SCALES) {
            for (transparent in listOf(true, false)) {
                assertEquals("{scale: $scale, transparent: $transparent}", PngOptions(scale, transparent).toJs())
            }
        }
    }

    @Test
    fun `a scale outside 1 to 3 is refused when built`() {
        for (bad in listOf(0, -1, 4, 100, Int.MIN_VALUE, Int.MAX_VALUE)) {
            try {
                PngOptions(bad)
                throw AssertionError("scale $bad was accepted")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("$bad"))
            }
        }
    }

    @Test
    fun `stored values that are not a valid scale fall back to the default`() {
        assertEquals(1, PngOptions.parseScale("1"))
        assertEquals(3, PngOptions.parseScale(" 3 "))
        for (bad in listOf(null, "", "  ", "0", "4", "-2", "2.0", "2x", "abc", "99999999999")) {
            assertEquals("stored '$bad'", PngOptions.DEFAULT_SCALE, PngOptions.parseScale(bad))
        }
    }

    @Test
    fun `only the stored word true turns transparency on`() {
        assertTrue(PngOptions.parseTransparent("true"))
        assertTrue(PngOptions.parseTransparent(" true "))
        for (bad in listOf(null, "", "TRUE", "True", "1", "yes", "false", "truex")) {
            assertEquals("stored '$bad'", false, PngOptions.parseTransparent(bad))
        }
    }

    // --- MarkdownFence --------------------------------------------------------------------------

    @Test
    fun `a diagram becomes a mermaid code block`() {
        assertEquals("```mermaid\nflowchart TD\n  A --> B\n```\n", MarkdownFence.wrap("flowchart TD\n  A --> B"))
    }

    @Test
    fun `line endings are normalized and a trailing newline is not doubled`() {
        assertEquals("```mermaid\nA\nB\n```\n", MarkdownFence.wrap("A\r\nB\r\n"))
        assertEquals("```mermaid\nA\nB\n```\n", MarkdownFence.wrap("A\rB\n\n\n"))
    }

    @Test
    fun `the fence is longer than any run of backticks in the diagram`() {
        assertEquals("```mermaid\nA[`code`]\n```\n", MarkdownFence.wrap("A[`code`]"))
        assertEquals("````mermaid\nA[```]\n````\n", MarkdownFence.wrap("A[```]"))
        assertEquals("``````mermaid\nnote: `````\n``````\n", MarkdownFence.wrap("note: `````"))
    }

    @Test
    fun `an empty diagram still gives a valid block`() {
        assertEquals("```mermaid\n\n```\n", MarkdownFence.wrap(""))
        assertEquals("```mermaid\n\n```\n", MarkdownFence.wrap("\n\n"))
    }

    // --- ClipboardImage -------------------------------------------------------------------------

    private fun ready(bytes: ByteArray): ClipboardImage =
        (ClipboardImage.fromPng(bytes) as? ClipboardImage.Result.Ready)?.image ?: throw AssertionError("not ready")

    /** Only the signature and an IHDR header with the given size: never decodable, and never decoded. */
    private fun header(w: Long, h: Long): ByteArray {
        val b = png(1, 1).copyOf(33)
        for (i in 0 until 4) {
            b[16 + i] = (w shr (24 - 8 * i)).toByte()
            b[20 + i] = (h shr (24 - 8 * i)).toByte()
        }
        return b
    }

    @Test
    fun `the clipboard limit is checked from the header, before decoding`() {
        assertEquals(ClipboardImage.Result.TooLarge(40_001L, 1_000L), ClipboardImage.fromPng(header(40_001, 1_000)))
        assertEquals(ClipboardImage.Result.TooLarge(65_535L, 65_535L), ClipboardImage.fromPng(header(65_535, 65_535)))
        assertEquals(ClipboardImage.Result.TooLarge(4_294_967_295L, 2L), ClipboardImage.fromPng(header(4_294_967_295, 2)))
        // At the limit the header is accepted and the (truncated) bytes then fail to decode: not an image.
        assertEquals(ClipboardImage.Result.NotAnImage, ClipboardImage.fromPng(header(40_000, 1_000)))
        assertEquals(ClipboardImage.Result.NotAnImage, ClipboardImage.fromPng(header(0, 10)))
        assertEquals(40_000L to 1_000L, ClipboardImage.headerSize(header(40_000, 1_000)))
    }

    @Test
    fun `a real PNG becomes a clipboard image of the same size`() {
        val image = ready(png(37, 11))
        assertEquals(37, image.image.width)
        assertEquals(11, image.image.height)
        assertArrayEquals(arrayOf(DataFlavor.imageFlavor), image.transferDataFlavors)
        assertTrue(image.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertEquals(image.image, image.getTransferData(DataFlavor.imageFlavor))
    }

    @Test
    fun `bytes that are not a PNG image are refused before the clipboard is touched`() {
        val good = png(4, 4)
        for ((label, bytes) in listOf(
            "empty" to ByteArray(0),
            "text" to "not a png".toByteArray(),
            "signature only" to good.copyOf(8),
            "truncated" to good.copyOf(good.size / 2),
        )) {
            assertEquals(label, ClipboardImage.Result.NotAnImage, ClipboardImage.fromPng(bytes))
        }
    }

    @Test(expected = UnsupportedFlavorException::class)
    fun `asking a clipboard image for text fails clearly`() {
        ready(png(2, 2)).getTransferData(DataFlavor.stringFlavor)
    }

    // --- BridgeMessage: copy ------------------------------------------------------------------

    @Test
    fun `copy messages parse to their own kinds, never to a file export`() {
        assertEquals(BridgeMessage.CopySvg("<svg/>"), BridgeMessage.parse("copysvg:<svg/>"))
        val copied = BridgeMessage.parse("copypng:" + Base64.getEncoder().encodeToString(png(3, 3)))
        assertTrue(copied is BridgeMessage.CopyPng)
        assertEquals(BridgeMessage.CopyFailure("boom"), BridgeMessage.parse("copyerror:boom"))
    }

    @Test
    fun `malformed copy messages become a copy failure with a reason`() {
        assertEquals(BridgeMessage.CopyFailure("The copied SVG was empty or malformed."), BridgeMessage.parse("copysvg:"))
        assertEquals(BridgeMessage.CopyFailure("The copied SVG was empty or malformed."), BridgeMessage.parse("copysvg:<html>"))
        assertEquals(BridgeMessage.CopyFailure("Copy failed."), BridgeMessage.parse("copyerror:  "))
        assertEquals(BridgeMessage.CopyFailure("The exported PNG was not valid base64."), BridgeMessage.parse("copypng:%%%"))
        assertEquals(
            BridgeMessage.CopyFailure("The exported PNG was empty or malformed."),
            BridgeMessage.parse("copypng:" + Base64.getEncoder().encodeToString("GIF89a".toByteArray())),
        )
        assertNull(BridgeMessage.parse("copy:anything"))
        assertNull(BridgeMessage.parse("copypngx:AAAA"))
    }
}
