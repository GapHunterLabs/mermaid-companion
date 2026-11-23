package dev.gaphunter.mermaidcompanion.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.CharBuffer

/**
 * Inputs the checker was never tested with before: empty and degenerate files, every
 * diagram type, line endings, BOM, tabs, Unicode in every form, brackets at every nesting
 * depth, and the free-text positions where each diagram type allows any character. Each
 * "valid" case below was confirmed with the bundled mermaid.js (see MermaidCorpusTest).
 */
class MermaidSyntaxCheckerNegativeTest {

    private fun messages(text: CharSequence) = MermaidSyntaxChecker.check(text).map { it.message }

    private fun assertClean(text: CharSequence) {
        val issues = MermaidSyntaxChecker.check(text)
        assertTrue("expected no issues, got $issues", issues.isEmpty())
    }

    // --- degenerate input -------------------------------------------------------

    @Test
    fun `empty, blank and minimal inputs produce no issues and never throw`() {
        for (text in listOf("", " ", "\n", "\r\n", "\t", "   \n\t\n  ", "﻿", "%%", "%", "-", "flowchart", "graph", "sequenceDiagram", "classDiagram")) {
            assertClean(text)
        }
    }

    @Test
    fun `the shortest possible errors are still found`() {
        assertEquals(listOf("Unterminated string literal"), messages("\""))
        assertEquals(listOf("Unmatched opening '['"), messages("["))
        assertEquals(listOf("Unmatched closing ']'"), messages("]"))
        assertEquals(listOf("Unmatched opening '('"), messages("("))
        assertEquals(listOf("Unmatched closing '}'"), messages("}"))
        assertEquals(listOf("Missing matching 'end' for this subgraph"), messages("subgraph"))
    }

    @Test
    fun `an unterminated string at end of file is reported once, with its real range`() {
        val text = "flowchart LR\n    A[\"never closed"
        val issues = MermaidSyntaxChecker.check(text)
        val string = issues.single { it.message == "Unterminated string literal" }
        assertEquals(text.indexOf('"'), string.offset)
        assertEquals(text.length - text.indexOf('"'), string.length)
        assertTrue("the open bracket before it is reported too", issues.any { it.message == "Unmatched opening '['" })
    }

    @Test
    fun `every reported range stays inside the text`() {
        for (text in listOf("\"", "flowchart\n\"abc", "A[\"x\\", "subgraph", "A[(]", "\"\\")) {
            for (issue in MermaidSyntaxChecker.check(text)) {
                assertTrue("$issue in ${text.length} chars", issue.offset >= 0 && issue.length >= 1 && issue.offset + issue.length <= text.length)
            }
        }
    }

    // --- brackets at every depth -------------------------------------------------

    @Test
    fun `balanced brackets at every nesting depth up to 64 are clean`() {
        val opens = "[({"
        val closes = "])}"
        for (depth in 1..64) {
            val sb = StringBuilder("flowchart LR\n    A")
            for (i in 0 until depth) sb.append(opens[i % 3])
            sb.append("x")
            for (i in depth - 1 downTo 0) sb.append(closes[i % 3])
            assertClean(sb)
        }
    }

    @Test
    fun `a mismatch at any nesting depth is reported at that bracket`() {
        val opens = "[({"
        val closes = "])}"
        for (depth in 1..32) {
            for (broken in 0 until depth) {
                val sb = StringBuilder("flowchart LR\n    A")
                for (i in 0 until depth) sb.append(opens[i % 3])
                val closers = (depth - 1 downTo 0).map { closes[it % 3] }.toMutableList()
                // Replace one closer with a wrong kind.
                val wrong = closes[(broken + 1) % 3]
                val position = depth - 1 - broken
                closers[position] = wrong
                closers.forEach { sb.append(it) }
                assertTrue("depth $depth, broken $broken: $sb", MermaidSyntaxChecker.check(sb).any { it.message.startsWith("Unmatched") })
            }
        }
    }

    @Test
    fun `ten thousand levels of nesting are handled without recursion`() {
        val depth = 10_000
        assertClean("flowchart LR\nA" + "[(".repeat(depth / 2) + "x" + ")]".repeat(depth / 2))
        assertEquals(depth, MermaidSyntaxChecker.check("flowchart LR\nA" + "{".repeat(depth)).size)
        assertEquals(depth, MermaidSyntaxChecker.check("flowchart LR\n" + "subgraph s\n".repeat(depth)).size)
    }

    @Test
    fun `brackets and quotes inside comments are ignored`() {
        assertClean("flowchart LR\n    %% [ ( { \" unbalanced on purpose\n    A --> B\n")
        assertClean("flowchart LR\n    A --> B %% trailing comment with ( and \"\n")
    }

    @Test
    fun `a single percent sign is not a comment`() {
        assertEquals(listOf("Unmatched opening '['"), messages("flowchart LR\n    A[50% done --> B\n"))
    }

    // --- subgraph and end ----------------------------------------------------------

    @Test
    fun `end without subgraph is not reported (mermaid reports it in the preview)`() {
        assertClean("flowchart TD\n    A --> B\n    end\n")
    }

    @Test
    fun `subgraph without end at end of file is reported at the subgraph`() {
        val text = "flowchart TD\n    subgraph one\n        A --> B"
        val issue = MermaidSyntaxChecker.check(text).single()
        assertEquals("Missing matching 'end' for this subgraph", issue.message)
        assertEquals(text.indexOf("subgraph"), issue.offset)
    }

    @Test
    fun `nested subgraphs need one end each`() {
        assertClean("flowchart TB\n subgraph a\n  subgraph b\n   A --> B\n  end\n end\n")
        assertEquals(1, messages("flowchart TB\n subgraph a\n  subgraph b\n   A --> B\n  end\n").size)
    }

    @Test
    fun `subgraph and end inside a label or edge text are words, not blocks`() {
        assertClean("flowchart LR\n    A[open a subgraph here] --> B\n")
        assertClean("flowchart LR\n    A[the end] --> B\n")
        assertClean("flowchart LR\n    A -- into subgraph --> B\n")
        // ...but an `end` inside a label does not close a real subgraph either.
        assertEquals(listOf("Missing matching 'end' for this subgraph"), messages("flowchart LR\n    subgraph S\n        A[the end]\n"))
    }

    @Test
    fun `statements separated by semicolons start a statement each`() {
        assertClean("flowchart LR\n    A --> B; subgraph S; C --> D; end\n")
        assertEquals(listOf("Missing matching 'end' for this subgraph"), messages("flowchart LR\n    A --> B; subgraph S; C --> D\n"))
    }

    // --- flowchart shapes and labels ------------------------------------------------

    @Test
    fun `the asymmetric shape opens with a greater-than sign`() {
        assertClean("flowchart LR\n    A>Flag shape] --> B\n")
        assertClean("flowchart LR\n    B --> E>Asymmetric]\n")
        assertEquals(listOf("Unmatched opening '>'"), messages("flowchart LR\n    A>Flag shape --> B\n"))
        assertEquals(listOf("Unmatched opening '>'", "Unmatched closing ')'"), messages("flowchart LR\n    A>Flag shape) --> B\n"))
    }

    @Test
    fun `a greater-than sign inside a label or edge text is just text`() {
        assertClean("flowchart LR\n    A[a>b] -->|x>y| B\n    C[\"q>r\"] --> D\n")
        // Arrows are never shapes.
        assertClean("flowchart LR\n    A-->B\n    A ==> B\n    A -.-> B\n    A --o B\n    A --x B\n    A <--> B\n")
    }

    @Test
    fun `a quoted flowchart label may span lines`() {
        assertClean("flowchart LR\n    A[\"line one\nline two\"] --> B\n")
        assertClean("flowchart LR\n    A[\"`**bold**\nline two`\"] --> B\n")
        assertClean("flowchart LR\r\n    A[\"line one\r\nline two\"] --> B\r\n")
    }

    @Test
    fun `a quote that never closes is still reported, even across lines`() {
        assertTrue(messages("flowchart LR\n    A[\"line one\n    B --> C\n").contains("Unterminated string literal"))
        // An odd number of quotes always leaves one unterminated, wherever the pairs fall.
        assertTrue(messages("flowchart LR\n    A[\"one\n    B[\"two\"] --> C\n").contains("Unterminated string literal"))
    }

    @Test
    fun `brackets inside a pipe edge label are still checked, like mermaid does`() {
        assertEquals(listOf("Unmatched closing ')'"), messages("flowchart LR\n    A -->|yes :)| B\n"))
    }

    // --- directives, front matter, accessibility --------------------------------------

    @Test
    fun `a multi-line directive is not diagram text`() {
        assertClean("%%{\n  init: {\n    \"theme\": \"forest\"\n  }\n}%%\nflowchart LR\n    A --> B\n")
        assertClean("%%{init: {\"theme\": \"forest\"}}%%\nflowchart LR\n    A --> B\n")
    }

    @Test
    fun `a directive that never closes falls back to a one-line comment`() {
        // mermaid.js itself rejects this file; the checker just does not crash or skip the file.
        assertClean("%%{init: {\"theme\": \"forest\"}\nflowchart LR\n    A --> B\n")
        assertEquals(listOf("Unmatched opening '['"), messages("%%{init: {\"theme\": \"forest\"}\nflowchart LR\n    A[x --> B\n"))
    }

    @Test
    fun `front matter is not diagram text, and a diagram after it is still checked`() {
        assertClean("---\ntitle: Order flow (draft\nconfig:\n  theme: neutral\n---\nflowchart LR\n    A --> B\n")
        assertEquals(listOf("Unmatched opening '['"), messages("---\ntitle: x\n---\nflowchart LR\n    A[x --> B\n"))
        // Front matter that never closes is checked as text: nothing is silently skipped.
        assertEquals(listOf("Unmatched opening '('"), messages("---\ntitle: x (draft\nflowchart LR\n"))
    }

    @Test
    fun `front matter decides the diagram type after it`() {
        assertClean("---\ntitle: x\n---\nerDiagram\n    CUSTOMER ||--o{ ORDER : places\n")
        assertClean("﻿---\ntitle: x\n---\nerDiagram\n    CUSTOMER ||--o{ ORDER : places\n")
    }

    @Test
    fun `accessibility titles and descriptions are free text`() {
        assertClean("flowchart LR\n    accTitle: Order flow (draft\n    accDescr: Shows the flow (draft\n    A --> B\n")
        assertClean("flowchart LR\n    accDescr {\n        A description (with an open paren\n    }\n    A --> B\n")
        assertClean("sequenceDiagram\n    accDescr {\n        Text with [ an open bracket\n    }\n    A->>B: hi\n")
    }

    // --- sequence diagrams -----------------------------------------------------------

    @Test
    fun `sequence message and note text is free text`() {
        assertClean("sequenceDiagram\n    Alice->>Bob: all good :)\n    Bob->>Alice: see note (1\n")
        assertClean("sequenceDiagram\n    Alice->>Bob: the 27\" monitor\n")
        assertClean("sequenceDiagram\n    Alice->>Bob: items[0\n    Note right of Bob: value } closes\n")
    }

    @Test
    fun `sequence block labels and participant aliases are free text`() {
        assertClean("sequenceDiagram\n    loop retry (max 3\n        A->>B: x\n    end\n")
        assertClean("sequenceDiagram\n    alt is sick (really\n        A->>B: x\n    else is well [ok\n        A->>B: y\n    end\n")
        assertClean("sequenceDiagram\n    participant A as Alice (admin\n    actor B as Bob \"the builder\n    A->>B: hi\n")
    }

    @Test
    fun `sequence async arrows are arrows`() {
        assertClean("sequenceDiagram\n    Alice-)John: See you later!\n    John--)Alice: Bye\n")
    }

    @Test
    fun `sequence participant config braces are still checked`() {
        assertClean("sequenceDiagram\n    participant Alice@{ \"type\" : \"boundary\" }\n    Alice->>Alice: hi\n")
        assertEquals(listOf("Unmatched opening '{'"), messages("sequenceDiagram\n    participant Alice@{ \"type\" : \"boundary\"\n    Alice->>Alice: hi\n"))
        assertClean("sequenceDiagram\n    rect rgb(191, 223, 255)\n        A->>B: x\n    end\n")
        assertEquals(listOf("Unmatched opening '('"), messages("sequenceDiagram\n    rect rgb(191, 223, 255\n        A->>B: x\n    end\n"))
    }

    @Test
    fun `a keyword used as a participant name is not a block label`() {
        // `loop` only starts a label at the start of a statement.
        assertEquals(listOf("Unmatched opening '('"), messages("sequenceDiagram\n    A->>loop(: x\n"))
    }

    // --- class diagrams ---------------------------------------------------------------

    @Test
    fun `class relation labels and class bodies are free text`() {
        assertClean("classDiagram\n    Animal <|-- Duck : extends (x\n")
        assertClean("classDiagram\n    class A {\n        +foo(int x\n        -int[ ids\n    }\n")
        assertClean("classDiagram\n    Animal : +isMammal(\n")
        assertClean("classDiagram-v2\n    class A\n")
    }

    @Test
    fun `a class body that never closes is reported once`() {
        assertEquals(listOf("Unmatched opening '{'"), messages("classDiagram\n    class Animal {\n        +String name\n"))
    }

    @Test
    fun `class namespaces nest class bodies`() {
        assertClean("classDiagram\n    namespace BaseShapes {\n        class Triangle\n        class Rectangle {\n            double width (m\n        }\n    }\n")
        assertEquals(listOf("Unmatched opening '{'"), messages("classDiagram\n    namespace BaseShapes {\n        class Triangle\n"))
        assertEquals(listOf("Unmatched closing '}'"), messages("classDiagram\n    namespace N {\n        class T\n    }\n    }\n"))
    }

    // --- other diagram types -----------------------------------------------------------

    @Test
    fun `diagram types the checker does not validate never get a false error`() {
        val samples = listOf(
            "erDiagram\n    CUSTOMER ||--o{ ORDER : places\n    CUSTOMER }|..|{ ADDRESS : uses\n",
            "stateDiagram-v2\n    [*] --> Still\n    note right of Still : resting (idle\n",
            "stateDiagram\n    [*] --> A\n",
            "gantt\n    title A plan (draft\n    dateFormat YYYY-MM-DD\n",
            "mindmap\n  root((center))\n    id1)cloud shape(\n    id2))bang shape((\n",
            "pie title Pets\n    \"Dogs\" : 386\n",
            "journey\n    title My day\n",
            "gitGraph\n    commit\n",
            "timeline\n    title History (draft\n",
            "quadrantChart\n    title Reach (x\n",
            "requirementDiagram\n    requirement r {\n    id: 1\n",
            "C4Context\n    title System (draft\n",
            "sankey-beta\nA,B,1\n",
            "xychart-beta\n    title \"Sales\n",
            "block-beta\n    columns 3\n    a[\"A\n",
            "packet-beta\n    0-15: \"Source\n",
            "kanban\n  Todo\n    [Create\n",
            "architecture-beta\n    group api(cloud[API]\n",
            "radar-beta\n    axis A, B\n",
            "treemap-beta\n\"Section\n",
            "zenuml\n    Alice->Bob: hello (\n",
        )
        for (sample in samples) assertClean(sample)
    }

    @Test
    fun `a header-less snippet keeps the flowchart rules`() {
        assertEquals(listOf("Unmatched closing ']'"), messages("A[Start]] --> B"))
        assertEquals(listOf("Unmatched opening '['"), messages("notADiagram\n    A[x --> B\n"))
    }

    @Test
    fun `the header is case-sensitive like mermaid`() {
        // `ERDIAGRAM` is not a diagram type; the flowchart rules apply and flag the brace.
        assertTrue(messages("ERDIAGRAM\n    CUSTOMER ||--o{ ORDER : places\n").isNotEmpty())
    }

    // --- encodings and characters ---------------------------------------------------------

    @Test
    fun `CRLF, LF, CR-only and tabs give the same verdict`() {
        val lf = "flowchart TD\n\tsubgraph S\n\t\tA[Start] --> B\n\tend\n"
        assertClean(lf)
        assertClean(lf.replace("\n", "\r\n"))
        assertEquals(messages("flowchart TD\n\tA[x --> B\n"), messages("flowchart TD\r\n\tA[x --> B\r\n"))
    }

    @Test
    fun `a byte order mark before the header changes nothing`() {
        assertClean("﻿flowchart TD\n    A --> B\n")
        assertClean("﻿erDiagram\n    CUSTOMER ||--o{ ORDER : places\n")
        assertEquals(listOf("Unmatched opening '['"), messages("﻿flowchart TD\n    A[x --> B\n"))
    }

    @Test
    fun `non-ASCII identifiers and labels in every form are clean`() {
        val samples = listOf(
            "subgraph á\n  A --> B\nend",
            "A[café] --> B[日本語]",
            "A[\"emoji 🚀 label\"] --> B",
            "A[🚀] --> B[👍🏽]",
            "A[\"עברית RTL\"] --> B[العربية]",
            "A[é combining] --> B[ñ]",
            "A[\"𝒳 surrogate 𠀀\"] --> B[𝒳𠀀]",
            "café --> naïve\n日本 --> 中文\nΩmega --> Δelta",
            "subgraph 日本語のグラフ\n  A --> B\nend",
            "A[​zero width‍] --> B",
        )
        for (sample in samples) assertClean("flowchart TD\n$sample\n")
    }

    @Test
    fun `a lone surrogate or control character never breaks the checker`() {
        assertClean("flowchart TD\n    A[\uD800] --> B[\uDC00]\n")
        assertClean("flowchart TD\n    A[\u0000\u0007\u001B] --> B\n")
    }

    @Test
    fun `any CharSequence gives the same verdict as a String`() {
        val text = "flowchart TD\n    subgraph one\n        A[x] --> B\n    end\n    subgraph two\n"
        val expected = MermaidSyntaxChecker.check(text)
        assertEquals(1, expected.size)
        assertEquals(expected, MermaidSyntaxChecker.check(StringBuilder(text)))
        assertEquals(expected, MermaidSyntaxChecker.check(CharBuffer.wrap(text)))
        assertEquals(expected, MermaidSyntaxChecker.check(CharBuffer.wrap(text.toCharArray())))
    }
}
