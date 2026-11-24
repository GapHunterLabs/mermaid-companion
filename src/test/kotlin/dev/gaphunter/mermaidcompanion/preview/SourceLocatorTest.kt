package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Click on a node in the preview -> caret offset in the text. */
class SourceLocatorTest {

    private fun at(text: String, needle: String, nth: Int = 0): Int {
        var i = -1
        repeat(nth + 1) { i = text.indexOf(needle, i + 1) }
        check(i >= 0) { "'$needle' #$nth not in the test text" }
        return i
    }

    @Test
    fun `a flowchart node is found where it is first written outside a label`() {
        val t = "flowchart LR\n  start[Start] --> check{Ok?}\n  check -->|yes| done((Done))"
        assertEquals(at(t, "start"), SourceLocator.offsetOfName(t, "start"))
        assertEquals(at(t, "check"), SourceLocator.offsetOfName(t, "check"))
        assertEquals(at(t, "done"), SourceLocator.offsetOfName(t, "done"))
    }

    @Test
    fun `a node's own line beats a label that mentions it`() {
        val t = "flowchart TD\n  A[Go to B] --> C\n  B --> D"
        assertEquals(at(t, "B -->"), SourceLocator.offsetOfName(t, "B"))
    }

    @Test
    fun `a name that only appears inside labels falls back to the first label`() {
        val t = "flowchart TD\n  A[Start here] --> B(Start)"
        assertEquals(at(t, "Start"), SourceLocator.offsetOfName(t, "Start"))
    }

    @Test
    fun `comments, quoted strings and front matter never match`() {
        val t = "---\ntitle: B\n---\n%% B is described below\nflowchart TD\n  A[\"B\"] --> C\n  B --> D"
        assertEquals(at(t, "B -->"), SourceLocator.offsetOfName(t, "B"))
    }

    @Test
    fun `front matter that is never closed is not skipped`() {
        val t = "---\nflowchart TD\n  B --> C"
        assertEquals(at(t, "B"), SourceLocator.offsetOfName(t, "B"))
    }

    @Test
    fun `only a whole identifier matches`() {
        val t = "flowchart TD\n  AB --> ABC\n  ABC --> A"
        assertEquals(at(t, "A", 3), SourceLocator.offsetOfName(t, "A"))
        assertNull(SourceLocator.offsetOfName(t, "B"))
    }

    @Test
    fun `ER cardinality braces are not label brackets`() {
        val t = "erDiagram\n  CUSTOMER ||--o{ ORDER : places\n  ORDER {\n    int id\n  }"
        assertEquals(at(t, "ORDER"), SourceLocator.offsetOfName(t, "ORDER"))
    }

    @Test
    fun `classes, states and participants`() {
        val c = "classDiagram\n  class Animal {\n    +name\n  }\n  Animal <|-- Duck"
        assertEquals(at(c, "Animal"), SourceLocator.offsetOfName(c, "Animal"))
        assertEquals(at(c, "Duck"), SourceLocator.offsetOfName(c, "Duck"))
        val s = "stateDiagram-v2\n  [*] --> Idle\n  Idle --> Busy : go"
        assertEquals(at(s, "Busy"), SourceLocator.offsetOfName(s, "Busy"))
        val q = "sequenceDiagram\n  participant A as Alice\n  A->>Bob: Hi A"
        assertEquals(at(q, "A as"), SourceLocator.offsetOfName(q, "A"))
        assertEquals(at(q, "Bob"), SourceLocator.offsetOfName(q, "Bob"))
    }

    @Test
    fun `names that are not a single token are found as plain text`() {
        val kanban = "kanban\n  Todo\n    [Create documentation]\n  Done\n    [Ship it]"
        assertEquals(at(kanban, "Create documentation"), SourceLocator.offsetOfName(kanban, "Create documentation"))
        assertEquals(at(kanban, "Ship it"), SourceLocator.offsetOfName(kanban, "Ship it"))
        val multiLine = "flowchart LR\n    A[\"line one\nline two\"] --> B"
        assertEquals(multiLine.lastIndexOf("B"), SourceLocator.offsetOfName(multiLine, "B"))
    }

    @Test
    fun `the plain-text fallback still matches whole words only, and skips comments and front matter`() {
        val t = "---\ntitle: Ship it\n---\nkanban\n  %% Ship it later\n  Shipping\n    [Ship items]\n    [Ship it]"
        assertEquals(at(t, "[Ship it]") + 1, SourceLocator.offsetOfName(t, "Ship it"))
        assertNull(SourceLocator.offsetOfName("kanban\n  [Shipped]", "Ship"))
        assertNull(SourceLocator.offsetOfName("kanban\n  %% only here: Ship it", "Ship it"))
    }

    @Test
    fun `unicode names and CRLF text`() {
        val t = "flowchart TD\r\n  café --> 日本\r\n  日本 --> done"
        assertEquals(at(t, "café"), SourceLocator.offsetOfName(t, "café"))
        assertEquals(at(t, "日本"), SourceLocator.offsetOfName(t, "日本"))
    }

    @Test
    fun `nothing is found for an empty name, a missing name or empty text`() {
        assertNull(SourceLocator.offsetOfName("flowchart TD\n A --> B", ""))
        assertNull(SourceLocator.offsetOfName("flowchart TD\n A --> B", "Z"))
        assertNull(SourceLocator.offsetOfName("", "A"))
    }

    @Test
    fun `mindmap nodes are counted in the order they are written`() {
        val t = "mindmap\n  root((Root))\n    %% a comment\n    Child A\n    ::icon(fa fa-book)\n\n    Child B\n      :::urgent"
        assertEquals(at(t, "root"), SourceLocator.offsetOfMindmapNode(t, 0))
        assertEquals(at(t, "Child A"), SourceLocator.offsetOfMindmapNode(t, 1))
        assertEquals(at(t, "Child B"), SourceLocator.offsetOfMindmapNode(t, 2))
        assertNull(SourceLocator.offsetOfMindmapNode(t, 3))
        assertNull(SourceLocator.offsetOfMindmapNode(t, -1))
    }

    @Test
    fun `mindmap lookup needs the mindmap header first`() {
        assertNull(SourceLocator.offsetOfMindmapNode("flowchart TD\n  A", 0))
        assertNull(SourceLocator.offsetOfMindmapNode("", 0))
        val withFrontMatter = "---\ntitle: x\n---\n%%{init: {}}%%\nmindmap\n  Root"
        assertEquals(at(withFrontMatter, "Root"), SourceLocator.offsetOfMindmapNode(withFrontMatter, 0))
    }

    // --- BridgeMessage: goto -------------------------------------------------------------------

    @Test
    fun `goto messages carry a name or a mindmap index`() {
        assertEquals(BridgeMessage.GoToName("start"), BridgeMessage.parse("goto:name:start"))
        assertEquals(BridgeMessage.GoToName("a:b"), BridgeMessage.parse("goto:name:a:b"))
        assertEquals(BridgeMessage.GoToName("x".repeat(256)), BridgeMessage.parse("goto:name:" + "x".repeat(256)))
        assertEquals(BridgeMessage.GoToMindmapNode(0), BridgeMessage.parse("goto:index:0"))
        assertEquals(BridgeMessage.GoToMindmapNode(100_000), BridgeMessage.parse("goto:index:100000"))
    }

    @Test
    fun `malformed goto messages are dropped`() {
        for (bad in listOf(
            "goto:", "goto:name:", "goto:name:" + "x".repeat(257), "goto:name:a\nb", "goto:name:a\u0000",
            "goto:index:", "goto:index:-1", "goto:index:1e3", "goto:index:7 ", "goto:index:100001",
            "goto:index:9999999", "goto:edge:L_a_b", "goto:start",
        )) {
            assertNull(bad, BridgeMessage.parse(bad))
        }
    }
}
