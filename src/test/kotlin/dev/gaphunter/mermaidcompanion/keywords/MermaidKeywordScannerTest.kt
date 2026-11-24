package dev.gaphunter.mermaidcompanion.keywords

import dev.gaphunter.mermaidcompanion.highlighting.MermaidColorSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import dev.gaphunter.mermaidcompanion.keywords.MermaidKeywordScanner.Kind.DIAGRAM_TYPE
import dev.gaphunter.mermaidcompanion.keywords.MermaidKeywordScanner.Kind.KEYWORD

/** Which words of a diagram get the keyword and diagram-type colors. */
class MermaidKeywordScannerTest {

    /** The colored words, as text, in order. */
    private fun colored(text: String, kind: MermaidKeywordScanner.Kind? = null): List<String> {
        val r = MermaidKeywordScanner.scan(text)
        return r.marks.filter { kind == null || it.value == kind }.keys.sorted().map { start ->
            text.substring(start).takeWhile { it.isLetterOrDigit() || it == '_' }
        }
    }

    @Test
    fun `the header names the diagram type`() {
        for ((header, type) in listOf(
            "flowchart LR" to "flowchart", "graph TD" to "flowchart", "sequenceDiagram" to "sequence",
            "classDiagram" to "class", "stateDiagram-v2" to "state", "stateDiagram" to "state", "erDiagram" to "er",
            "gantt" to "gantt", "gitGraph" to "git", "xychart-beta" to "xychart", "C4Context" to "c4", "kanban" to "kanban",
        )) {
            val r = MermaidKeywordScanner.scan("$header\n")
            assertEquals(header, type, r.type)
            assertEquals(header, listOf(0), r.marks.filterValues { it == DIAGRAM_TYPE }.keys.toList())
        }
    }

    @Test
    fun `the header is found after front matter, directives and comments`() {
        val t = "---\ntitle: flowchart\n---\n%%{init: {'theme': 'forest'}}%%\n%% sequenceDiagram in a comment\nsequenceDiagram\n  participant A"
        val r = MermaidKeywordScanner.scan(t)
        assertEquals("sequence", r.type)
        assertEquals(listOf(t.indexOf("sequenceDiagram\n")), r.marks.filterValues { it == DIAGRAM_TYPE }.keys.toList())
        assertEquals(listOf("participant"), colored(t, KEYWORD))
    }

    @Test
    fun `keywords belong to their diagram type only`() {
        assertEquals(listOf("subgraph", "end"), colored("flowchart TD\n  subgraph s\n    participant --> loop\n  end", KEYWORD))
        assertEquals(listOf("participant", "loop", "end"), colored("sequenceDiagram\n  participant A\n  loop Every minute\n  end", KEYWORD))
        assertEquals(listOf("commit", "branch", "checkout", "merge"), colored("gitGraph\n  commit\n  branch dev\n  checkout dev\n  merge main", KEYWORD))
        assertEquals(listOf("title", "dateFormat", "section"), colored("gantt\n  title Plan\n  dateFormat YYYY-MM-DD\n  section A", KEYWORD))
    }

    @Test
    fun `words inside labels and after a colon are text, not keywords`() {
        val seq = "sequenceDiagram\n  Alice->>Bob: loop over the end\n  Note over Bob: done"
        assertEquals(listOf("Note", "over"), colored(seq, KEYWORD))
        val flow = "flowchart TD\n  A[subgraph end] --> B(\"end\")\n  B{end}"
        assertEquals(emptyList<String>(), colored(flow, KEYWORD))
        val state = "stateDiagram-v2\n  A --> B : note end of story\n  state B"
        assertEquals(listOf("state"), colored(state, KEYWORD))
    }

    @Test
    fun `a directive that spans lines is configuration, not diagram`() {
        val t = "%%{init: {\n  'theme': 'forest',\n  'flowchart': {'curve': 'end'}\n}}%%\nflowchart LR\n  subgraph a\n  end"
        val r = MermaidKeywordScanner.scan(t)
        assertEquals("flowchart", r.type)
        assertEquals(listOf("flowchart", "subgraph", "end"), colored(t))
        val open = MermaidKeywordScanner.scan("%%{init: {\nflowchart LR\n  subgraph a")
        assertNull("a directive never closed swallows the rest, like mermaid", open.type)
    }

    @Test
    fun `a flowchart edge label is text`() {
        assertEquals(listOf("subgraph", "end"), colored("flowchart LR\n  A -->|end| B -- end --> C\n  subgraph s\n  end", KEYWORD))
        assertEquals(emptyList<String>(), colored("flowchart LR\n  A -. end .-> B == end ==> C", KEYWORD))
        assertEquals(listOf("end"), colored("flowchart LR\n  A --> B\n  end", KEYWORD))
        assertEquals(emptyList<String>(), colored("flowchart LR\n  A -->|subgraph| B", KEYWORD))
    }

    @Test
    fun `ER cardinality braces do not hide what follows`() {
        assertEquals(listOf("title"), colored("erDiagram\n  title Shop\n  CUSTOMER ||--o{ ORDER : places\n  accTitle: x", KEYWORD).take(1))
    }

    @Test
    fun `no header, an unknown header, or an empty file color nothing but the common words`() {
        assertNull(MermaidKeywordScanner.scan("").type)
        assertEquals(emptyMap<Int, MermaidKeywordScanner.Kind>(), MermaidKeywordScanner.scan("").marks)
        assertEquals(emptyMap<Int, MermaidKeywordScanner.Kind>(), MermaidKeywordScanner.scan("%% only a comment\n").marks)
        val unknown = MermaidKeywordScanner.scan("notADiagram\n  subgraph x\n  title y")
        assertNull(unknown.type)
        assertEquals(listOf("title"), colored("notADiagram\n  subgraph x\n  title y"))
    }

    @Test
    fun `CRLF and unicode do not shift the offsets`() {
        val t = "flowchart TD\r\n  subgraph café\r\n    日本 --> é\r\n  end"
        val r = MermaidKeywordScanner.scan(t)
        assertEquals(setOf(0, t.indexOf("subgraph"), t.indexOf("end")), r.marks.keys)
    }

    @Test
    fun `every corpus diagram that mermaid accepts gets its type from its header`() {
        val corpus = File("src/test/resources/corpus")
        val untyped = corpus.listFiles { f -> f.name.startsWith("valid-") && f.name.endsWith(".mmd") }!!
            .filter { MermaidKeywordScanner.scan(it.readText(Charsets.UTF_8)).type == null }.map { it.name }
        assertEquals("diagrams whose type was not recognized", emptyList<String>(), untyped)
    }

    @Test
    fun `scanning stays fast on a large file`() {
        val big = "flowchart TD\n" + (0 until 20_000).joinToString("\n") { "  n$it[label $it] -->|end| n${it + 1}" }
        MermaidKeywordScanner.scan(big)
        val start = System.nanoTime()
        val r = MermaidKeywordScanner.scan(big)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1, r.marks.size)
        println("SCAN|${big.length} characters|$ms ms")
        assertTrue("took $ms ms for ${big.length} characters", ms < 1500)
    }

    @Test
    fun `the color settings demo text matches what the scanner colors`() {
        val tagged = MermaidColorSettingsPage.DEMO
        val plain = tagged.replace(Regex("</?(type|kw)>"), "")
        val expected = Regex("<(type|kw)>(\\w+)</\\1>").findAll(tagged).map { it.groupValues[2] }.toList()
        assertEquals(expected, colored(plain))
    }
}
