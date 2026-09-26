package dev.gaphunter.mermaidcompanion.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class SyncCoordinatorTest {

    private class Harness(
        var enabled: Set<ExportFormat> = setOf(ExportFormat.SVG),
        var licensed: Boolean = true,
    ) {
        val renders = mutableListOf<Int>()
        val exports = mutableListOf<ExportFormat>()
        val writes = mutableListOf<Pair<ExportFormat, ByteArray>>()
        val statuses = mutableListOf<SyncStatus>()

        val coordinator = SyncCoordinator(
            enabledFormats = { enabled },
            licensed = { licensed },
            requestRender = { renders += it },
            requestExport = { exports += it },
            requestWrite = { format, bytes -> writes += format to bytes },
            report = { statuses += it },
        )

        val lastToken: Int get() = renders.last()
    }

    private fun path(name: String): Path = Paths.get("diagrams", name)

    @Test
    fun `a save renders first and only then asks for the enabled exports`() {
        val h = Harness(enabled = setOf(ExportFormat.SVG, ExportFormat.PNG))
        h.coordinator.onSave()

        assertEquals(listOf(SyncStatus.Syncing), h.statuses)
        assertEquals(1, h.renders.size)
        assertTrue("nothing is exported before the render succeeded", h.exports.isEmpty())

        h.coordinator.onRendered(h.lastToken)
        assertEquals(listOf(ExportFormat.SVG, ExportFormat.PNG), h.exports)
    }

    @Test
    fun `the sync completes once every export is written and reports what changed`() {
        val h = Harness(enabled = setOf(ExportFormat.SVG, ExportFormat.PNG))
        h.coordinator.onSave()
        h.coordinator.onRendered(h.lastToken)

        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1))
        h.coordinator.onExport(ExportFormat.PNG, byteArrayOf(2))
        assertEquals(2, h.writes.size)
        assertEquals("still writing: not done yet", SyncStatus.Syncing, h.statuses.last())

        h.coordinator.onWritten(ExportFormat.SVG, WriteResult.Written(path("a.svg")))
        assertEquals("one write is still in flight", SyncStatus.Syncing, h.statuses.last())
        h.coordinator.onWritten(ExportFormat.PNG, WriteResult.Unchanged(path("a.png")))

        assertEquals(SyncStatus.Synced(listOf(path("a.svg")), unchanged = 1), h.statuses.last())
        assertFalse(h.coordinator.isBusy)
    }

    @Test
    fun `a diagram that does not render exports nothing and says why`() {
        val h = Harness()
        h.coordinator.onSave()
        h.coordinator.onRenderFailed(h.lastToken, "Parse error on line 2")

        assertTrue(h.exports.isEmpty())
        assertTrue(h.writes.isEmpty())
        assertEquals(SyncStatus.RenderFailed("Parse error on line 2"), h.statuses.last())
        assertFalse(h.coordinator.isBusy)
    }

    @Test
    fun `an unrelated render is never mistaken for the one the sync asked for`() {
        val h = Harness()
        h.coordinator.onSave()
        val token = h.lastToken

        h.coordinator.onRendered(token + 41) // some other render
        assertTrue(h.exports.isEmpty())

        h.coordinator.onRendered(token)
        assertEquals(listOf(ExportFormat.SVG), h.exports)
    }

    @Test
    fun `a newer save supersedes one still in flight and the old render no longer counts`() {
        val h = Harness()
        h.coordinator.onSave()
        val first = h.lastToken
        h.coordinator.onSave()
        val second = h.lastToken
        assertTrue(second > first)

        h.coordinator.onRendered(first)
        assertTrue("the superseded render is ignored", h.exports.isEmpty())
        h.coordinator.onRendered(second)
        assertEquals(listOf(ExportFormat.SVG), h.exports)
    }

    @Test
    fun `when typing replaces the render it asks again, a couple of times at most`() {
        val h = Harness()
        h.coordinator.onSave()
        h.coordinator.onRenderSuperseded(h.lastToken)
        h.coordinator.onRenderSuperseded(h.lastToken)
        assertEquals("first render plus two retries", 3, h.renders.size)
        assertTrue(h.coordinator.isBusy)

        h.coordinator.onRenderSuperseded(h.lastToken)
        assertEquals("no fourth render", 3, h.renders.size)
        assertTrue(h.statuses.last() is SyncStatus.Failed)
        assertFalse(h.coordinator.isBusy)
    }

    @Test
    fun `without a license a save does nothing at all`() {
        val h = Harness(licensed = false)
        h.coordinator.onSave()

        assertTrue(h.renders.isEmpty())
        assertTrue(h.exports.isEmpty())
        assertTrue(h.writes.isEmpty())
        assertTrue("silent: no status either", h.statuses.isEmpty())
    }

    @Test
    fun `Sync Now without a license says it is a Pro feature and does nothing else`() {
        val h = Harness(licensed = false)
        h.coordinator.onSyncNow()

        assertEquals(listOf<SyncStatus>(SyncStatus.NeedsLicense), h.statuses)
        assertTrue(h.renders.isEmpty())
    }

    @Test
    fun `a license lost mid-flight stops the write`() {
        val h = Harness()
        h.coordinator.onSave()
        h.coordinator.onRendered(h.lastToken)

        h.licensed = false
        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1))

        assertTrue("fail-closed: nothing is written", h.writes.isEmpty())
        assertFalse(h.coordinator.isBusy)
    }

    @Test
    fun `a save with nothing enabled is silent but Sync Now explains`() {
        val h = Harness(enabled = emptySet())
        h.coordinator.onSave()
        assertTrue(h.statuses.isEmpty())
        assertTrue(h.renders.isEmpty())

        h.coordinator.onSyncNow()
        assertEquals(listOf<SyncStatus>(SyncStatus.NothingEnabled), h.statuses)
    }

    @Test
    fun `only the formats that were asked for are accepted, and only once`() {
        val h = Harness(enabled = setOf(ExportFormat.SVG))
        h.coordinator.onSave()
        h.coordinator.onRendered(h.lastToken)

        h.coordinator.onExport(ExportFormat.PNG, byteArrayOf(9)) // never asked for
        assertTrue(h.writes.isEmpty())

        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1))
        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1)) // duplicate
        assertEquals(1, h.writes.size)
    }

    @Test
    fun `a failed export is reported once the writes still in flight have finished`() {
        val h = Harness(enabled = setOf(ExportFormat.SVG, ExportFormat.PNG))
        h.coordinator.onSave()
        h.coordinator.onRendered(h.lastToken)

        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1))
        h.coordinator.onExportFailed("PNG export isn't possible for this diagram type")
        assertTrue("SVG is still being written", h.coordinator.isBusy)

        h.coordinator.onWritten(ExportFormat.SVG, WriteResult.Written(path("a.svg")))
        assertEquals(SyncStatus.Failed("PNG export isn't possible for this diagram type"), h.statuses.last())
        assertFalse(h.coordinator.isBusy)
    }

    @Test
    fun `a failed write is reported with its message`() {
        val h = Harness()
        h.coordinator.onSave()
        h.coordinator.onRendered(h.lastToken)
        h.coordinator.onExport(ExportFormat.SVG, byteArrayOf(1))
        h.coordinator.onWritten(ExportFormat.SVG, WriteResult.Failed("Couldn't write a.svg: disk full"))

        assertEquals(SyncStatus.Failed("Couldn't write a.svg: disk full"), h.statuses.last())
    }

    @Test
    fun `a page that never answers ends as a failure, and a late answer is ignored`() {
        val h = Harness()
        h.coordinator.onSave()
        val token = h.lastToken
        h.coordinator.onTimeout()

        assertTrue(h.statuses.last() is SyncStatus.Failed)
        assertFalse(h.coordinator.isBusy)

        h.coordinator.onRendered(token)
        assertTrue("too late to export", h.exports.isEmpty())
    }
}
