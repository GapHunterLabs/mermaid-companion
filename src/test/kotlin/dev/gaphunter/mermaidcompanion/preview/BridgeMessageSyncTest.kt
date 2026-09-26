package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BridgeMessageSyncTest {

    private val pngBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private val pngBase64 = Base64.getEncoder().encodeToString(pngBytes)

    @Test
    fun `a finished render carries the token of the render that was asked for`() {
        assertEquals(BridgeMessage.Rendered(7), BridgeMessage.parse("rendered:7"))
    }

    @Test
    fun `a render message without a usable token is ignored`() {
        assertNull(BridgeMessage.parse("rendered:"))
        assertNull(BridgeMessage.parse("rendered:abc"))
        assertNull(BridgeMessage.parse("rendersuperseded:"))
        assertNull(BridgeMessage.parse("renderfailed:oops"))
    }

    @Test
    fun `a superseded render carries its token`() {
        assertEquals(BridgeMessage.RenderSuperseded(3), BridgeMessage.parse("rendersuperseded:3"))
    }

    @Test
    fun `a failed render keeps the whole message, colons included`() {
        assertEquals(
            BridgeMessage.RenderFailed(4, "Parse error on line 2: Expecting 'SEMI', got 'NODE_STRING'"),
            BridgeMessage.parse("renderfailed:4:Parse error on line 2: Expecting 'SEMI', got 'NODE_STRING'"),
        )
    }

    @Test
    fun `a failed render with no message still says something`() {
        val message = BridgeMessage.parse("renderfailed:4:") as BridgeMessage.RenderFailed

        assertEquals(4, message.token)
        assertTrue(message.message.isNotBlank())
    }

    @Test
    fun `a sync SVG is only accepted when it really is an SVG`() {
        assertEquals(BridgeMessage.SyncSvg("<svg/>"), BridgeMessage.parse("syncsvg:<svg/>"))
        assertTrue(BridgeMessage.parse("syncsvg:") is BridgeMessage.SyncFailure)
        assertTrue(BridgeMessage.parse("syncsvg:<html/>") is BridgeMessage.SyncFailure)
    }

    @Test
    fun `a sync PNG is only accepted when it really is a PNG`() {
        val ok = BridgeMessage.parse("syncpng:$pngBase64") as BridgeMessage.SyncPng
        assertArrayEquals(pngBytes, ok.bytes)

        assertTrue(BridgeMessage.parse("syncpng:not-base64!!") is BridgeMessage.SyncFailure)
        val notPng = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertTrue(BridgeMessage.parse("syncpng:$notPng") is BridgeMessage.SyncFailure)
    }

    @Test
    fun `a sync error comes back as a sync failure, never as a manual export failure`() {
        assertEquals(BridgeMessage.SyncFailure("Nothing to export yet"), BridgeMessage.parse("syncerror:Nothing to export yet"))
        assertTrue(BridgeMessage.parse("syncerror:") is BridgeMessage.SyncFailure)
    }

    @Test
    fun `manual exports keep their own messages`() {
        assertEquals(BridgeMessage.Svg("<svg/>"), BridgeMessage.parse("svg:<svg/>"))
        assertEquals(BridgeMessage.Failure("boom"), BridgeMessage.parse("error:boom"))
    }
}
