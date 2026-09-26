package dev.gaphunter.mermaidcompanion.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class SyncWriterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir: Path get() = folder.root.toPath()

    @Test
    fun `the export sits next to its source with the same name as the manual export`() {
        assertEquals(dir.resolve("order-flow.svg"), SyncWriter.targetFor(dir, "order-flow.mmd", ExportFormat.SVG))
        assertEquals(dir.resolve("order-flow.png"), SyncWriter.targetFor(dir, "order-flow.mermaid", ExportFormat.PNG))
        assertEquals(dir.resolve("notes.svg"), SyncWriter.targetFor(dir, "notes", ExportFormat.SVG))
    }

    @Test
    fun `a name that would leave the folder is refused`() {
        assertNull(SyncWriter.targetFor(dir, "..\\..\\evil.mmd", ExportFormat.SVG))
        assertNull(SyncWriter.targetFor(dir, "a/b.mmd", ExportFormat.SVG))
    }

    @Test
    fun `a new export is written`() {
        val target = dir.resolve("a.svg")
        val result = SyncWriter.write(target, "<svg/>".toByteArray())

        assertEquals(WriteResult.Written(target), result)
        assertArrayEquals("<svg/>".toByteArray(), Files.readAllBytes(target))
    }

    @Test
    fun `identical content leaves the file alone`() {
        val target = dir.resolve("a.svg")
        SyncWriter.write(target, "<svg/>".toByteArray())
        val before = Files.getLastModifiedTime(target)
        Thread.sleep(20)

        val result = SyncWriter.write(target, "<svg/>".toByteArray())

        assertEquals(WriteResult.Unchanged(target), result)
        assertEquals("not even the timestamp moves", before, Files.getLastModifiedTime(target))
    }

    @Test
    fun `changed content replaces the file and leaves no temp file behind`() {
        val target = dir.resolve("a.svg")
        SyncWriter.write(target, "<svg>old</svg>".toByteArray())

        val result = SyncWriter.write(target, "<svg>new</svg>".toByteArray())

        assertEquals(WriteResult.Written(target), result)
        assertArrayEquals("<svg>new</svg>".toByteArray(), Files.readAllBytes(target))
        val leftovers = Files.list(dir).use { stream -> stream.filter { it.fileName.toString().startsWith(".mermaid-sync-") }.toList() }
        assertTrue("temp files: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `a folder that is not there is a failure, not a crash`() {
        val result = SyncWriter.write(dir.resolve("missing").resolve("a.svg"), byteArrayOf(1))

        assertNotNull(result as? WriteResult.Failed)
    }
}
