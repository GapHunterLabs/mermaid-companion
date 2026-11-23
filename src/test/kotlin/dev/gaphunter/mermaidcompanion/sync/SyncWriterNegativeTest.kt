package dev.gaphunter.mermaidcompanion.sync

import dev.gaphunter.mermaidcompanion.preview.ExportFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Export on Save against the file system it will really meet: unusual names, a read-only
 * export, a folder where the export should be, and empty content. In every failure the
 * existing export must survive untouched and no temp file may be left behind.
 */
class SyncWriterNegativeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir: Path get() = folder.root.toPath()

    private fun leftovers(): List<Path> = Files.walk(dir).use { s ->
        s.filter { it.fileName.toString().startsWith(".mermaid-sync-") }.toList()
    }

    @Test
    fun `export names follow the source name for every shape of name`() {
        assertEquals("order flow.svg", ExportFiles.suggestedName("order flow.mmd", "svg"))
        assertEquals("diagrama de café.png", ExportFiles.suggestedName("diagrama de café.mermaid", "png"))
        assertEquals("日本語 図.svg", ExportFiles.suggestedName("日本語 図.mmd", "svg"))
        assertEquals("🚀.svg", ExportFiles.suggestedName("🚀.mmd", "svg"))
        assertEquals("עברית.svg", ExportFiles.suggestedName("עברית.mmd", "svg"))
        assertEquals("a.b.svg", ExportFiles.suggestedName("a.b.mmd", "svg"))
        assertEquals("noext.svg", ExportFiles.suggestedName("noext", "svg"))
        assertEquals("trailing.svg", ExportFiles.suggestedName("trailing.", "svg"))
        // Only an extension, or nothing at all: a fixed fallback name, never ".svg".
        assertEquals("diagram.svg", ExportFiles.suggestedName(".mmd", "svg"))
        assertEquals("diagram.svg", ExportFiles.suggestedName("", "svg"))
        assertEquals("diagram.svg", ExportFiles.suggestedName("   .mmd", "svg"))
        assertEquals("..svg", ExportFiles.suggestedName("..mmd", "svg"))
    }

    @Test
    fun `unusual but legal names are written next to the source`() {
        for (name in listOf("order flow.mmd", "diagrama de café.mmd", "日本語 図.mmd", "🚀.mmd", "עברית.mmd", "..mmd")) {
            val target = SyncWriter.targetFor(dir, name, ExportFormat.SVG)!!
            assertEquals(dir, target.parent)
            assertEquals(WriteResult.Written(target), SyncWriter.write(target, "<svg/>".toByteArray()))
            assertArrayEquals("<svg/>".toByteArray(), Files.readAllBytes(target))
        }
        assertTrue("temp files: ${leftovers()}", leftovers().isEmpty())
    }

    @Test
    fun `names that would escape the folder are refused in every form`() {
        for (name in listOf("../x.mmd", "..\\x.mmd", "a/b.mmd", "a\\b.mmd", "/abs.mmd", "\\abs.mmd")) {
            assertEquals(name, null, SyncWriter.targetFor(dir, name, ExportFormat.SVG))
        }
    }

    @Test
    fun `a folder where the export should be is never replaced`() {
        val target = dir.resolve("order-flow.svg")
        Files.createDirectory(target)
        Files.writeString(target.resolve("keep.txt"), "inside")

        val result = SyncWriter.write(target, "<svg/>".toByteArray())

        assertTrue("expected a failure, got $result", result is WriteResult.Failed)
        assertTrue(Files.isDirectory(target))
        assertEquals("inside", Files.readString(target.resolve("keep.txt")))
        assertTrue("temp files: ${leftovers()}", leftovers().isEmpty())
    }

    @Test
    fun `an empty folder where the export should be is never replaced either`() {
        val target = dir.resolve("order-flow.svg")
        Files.createDirectory(target)

        val result = SyncWriter.write(target, "<svg/>".toByteArray())

        assertTrue("expected a failure, got $result", result is WriteResult.Failed)
        assertTrue("the folder must still be there", Files.isDirectory(target))
        assertTrue("temp files: ${leftovers()}", leftovers().isEmpty())
    }

    @Test
    fun `a read-only export is not silently overwritten`() {
        // Verified on Windows, where replacing a read-only file fails. On Linux and macOS a
        // rename replaces it whatever its permissions; not covered here (see docs/TESTING.md).
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows"))
        val target = dir.resolve("order-flow.svg")
        Files.writeString(target, "<svg>old</svg>")
        assertTrue(target.toFile().setReadOnly())
        try {
            val result = SyncWriter.write(target, "<svg>new</svg>".toByteArray())

            assertTrue("expected a failure, got $result", result is WriteResult.Failed)
            assertEquals("<svg>old</svg>", Files.readString(target))
            assertTrue("temp files: ${leftovers()}", leftovers().isEmpty())
        } finally {
            target.toFile().setWritable(true)
        }
    }

    @Test
    fun `a read-only export with the same content is reported unchanged, not failed`() {
        val target = dir.resolve("order-flow.svg")
        Files.writeString(target, "<svg/>")
        assertTrue(target.toFile().setReadOnly())
        try {
            assertEquals(WriteResult.Unchanged(target), SyncWriter.write(target, "<svg/>".toByteArray()))
        } finally {
            target.toFile().setWritable(true)
        }
    }

    @Test
    fun `empty content is written as an empty file and is then unchanged`() {
        val target = dir.resolve("empty.svg")
        assertEquals(WriteResult.Written(target), SyncWriter.write(target, ByteArray(0)))
        assertEquals(0L, Files.size(target))
        assertEquals(WriteResult.Unchanged(target), SyncWriter.write(target, ByteArray(0)))
    }

    @Test
    fun `same size but different bytes is a change`() {
        val target = dir.resolve("a.svg")
        SyncWriter.write(target, "<svg>aaaa</svg>".toByteArray())
        assertEquals(WriteResult.Written(target), SyncWriter.write(target, "<svg>bbbb</svg>".toByteArray()))
        assertEquals("<svg>bbbb</svg>", Files.readString(target))
    }

    @Test
    fun `a failure message names the file but never contains the diagram content`() {
        val target = dir.resolve("order-flow.svg")
        Files.createDirectory(target)
        Files.writeString(target.resolve("keep.txt"), "x")
        val secret = "<svg>secret-diagram-text</svg>"
        val result = SyncWriter.write(target, secret.toByteArray()) as WriteResult.Failed
        assertTrue(result.message, result.message.contains("order-flow.svg"))
        assertTrue(result.message, !result.message.contains("secret-diagram-text"))
    }
}
