package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two halves of click-to-source against each other: `click-targets.tsv` holds every target
 * the real preview page sent when each corpus diagram was clicked in Chromium
 * (tools/click-corpus.py), and SourceLocator must find each one in that diagram's text, on a token
 * that is exactly the name.
 */
class SourceLocatorCorpusTest {

    private val corpus = File("src/test/resources/corpus")

    @Test
    fun `every target the real page sends is found in its diagram`() {
        val rows = File(corpus, "click-targets.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        assertTrue("click-targets.tsv looks empty (${rows.size} rows): run tools/click-corpus.py", rows.size >= 150)
        val misses = mutableListOf<String>()
        for (row in rows) {
            assertEquals("malformed row $row", 3, row.size)
            val (fileName, kind, value) = row
            val text = File(corpus, fileName).readText(Charsets.UTF_8)
            when (kind) {
                "name" -> {
                    val offset = SourceLocator.offsetOfName(text, value)
                    if (offset == null || text.substring(offset, minOf(text.length, offset + value.length)) != value) {
                        misses.add("$fileName: $value -> $offset")
                    }
                }
                "index" -> if (SourceLocator.offsetOfMindmapNode(text, value.toInt()) == null) misses.add("$fileName: node $value")
                else -> misses.add("$fileName: unknown kind $kind")
            }
        }
        assertTrue("not found: $misses", misses.isEmpty())
    }
}
