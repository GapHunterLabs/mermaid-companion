package dev.gaphunter.mermaidcompanion.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncSettingsTest {

    private val a = "file:///work/docs/a.mmd"
    private val b = "file:///work/docs/b.mmd"

    @Test
    fun `nothing is on until someone turns it on`() {
        val settings = AutoSyncSettings()

        assertFalse(settings.isEnabled(a, ExportFormat.SVG))
        assertTrue(settings.enabledFormats(a).isEmpty())
    }

    @Test
    fun `each format and each file is its own switch`() {
        val settings = AutoSyncSettings()
        settings.setEnabled(a, ExportFormat.SVG, true)

        assertTrue(settings.isEnabled(a, ExportFormat.SVG))
        assertFalse(settings.isEnabled(a, ExportFormat.PNG))
        assertFalse(settings.isEnabled(b, ExportFormat.SVG))
        assertEquals(setOf(ExportFormat.SVG), settings.enabledFormats(a))
    }

    @Test
    fun `turning a switch on twice does not duplicate it, and off removes it`() {
        val settings = AutoSyncSettings()
        settings.setEnabled(a, ExportFormat.PNG, true)
        settings.setEnabled(a, ExportFormat.PNG, true)
        assertEquals(1, settings.state.pngFiles.size)

        settings.setEnabled(a, ExportFormat.PNG, false)
        assertFalse(settings.isEnabled(a, ExportFormat.PNG))
    }

    @Test
    fun `the state survives being saved and loaded`() {
        val settings = AutoSyncSettings()
        settings.setEnabled(a, ExportFormat.SVG, true)
        settings.setEnabled(b, ExportFormat.PNG, true)

        val restored = AutoSyncSettings()
        restored.loadState(settings.state)

        assertEquals(setOf(ExportFormat.SVG), restored.enabledFormats(a))
        assertEquals(setOf(ExportFormat.PNG), restored.enabledFormats(b))
    }
}
