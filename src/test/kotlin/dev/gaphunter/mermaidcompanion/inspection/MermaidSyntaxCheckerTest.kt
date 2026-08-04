package dev.gaphunter.mermaidcompanion.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures that matter most here reproduce the exact competitor bugs
 * cited in NEXT_BATCH_PLAN.md/README.md: non-ASCII subgraph identifiers
 * and node labels wrongly flagged as errors. Each "no false positive"
 * test below is the direct regression test for one specific quoted
 * complaint.
 */
class MermaidSyntaxCheckerTest {
    @Test
    fun `well-formed flowchart produces no issues`() {
        val diagram = """
            flowchart TD
                A[Start] --> B{Decision}
                B -->|Yes| C[Do thing]
                B -->|No| D[Skip]
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `non-ascii subgraph identifier produces no false positive`() {
        // Reproduces: "the plugin shows false warnings for valid non-ASCII
        // subgraph identifiers... e.g. for subgraph á it warns for
        // 'Subgraph identifier á contains special characters'"
        val diagram = """
            flowchart TD
                subgraph á
                    A --> B
                end
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `cjk subgraph identifier produces no false positive`() {
        val diagram = """
            flowchart TD
                subgraph 日本語のグラフ
                    A --> B
                end
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `non-ascii node labels and nested quotes produce no false positive`() {
        // Reproduces: "shows false errors for valid node shapes where the
        // node contains non-ASCII characters" / valid nested quotes marked
        // as an error.
        val diagram = """
            flowchart TD
                A["café \"münchen\" 日本語"] --> B[(cylinder)]
                B --> C([stadium día])
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `well-formed sequence diagram produces no issues`() {
        val diagram = """
            sequenceDiagram
                participant Alice
                participant Bób
                Alice->>Bób: Hëllo
                Bób-->>Alice: Hi there
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `well-formed class diagram produces no issues`() {
        val diagram = """
            classDiagram
                class Animal {
                    +String name
                    +makeSound()
                }
                class Perro
                Animal <|-- Perro
        """.trimIndent()
        assertTrue(MermaidSyntaxChecker.check(diagram).isEmpty())
    }

    @Test
    fun `flags a genuinely unmatched closing bracket`() {
        val issues = MermaidSyntaxChecker.check("A[Start]] --> B")
        assertEquals(1, issues.size)
        assertEquals("Unmatched closing ']'", issues[0].message)
    }

    @Test
    fun `flags a genuinely unclosed opening bracket`() {
        val issues = MermaidSyntaxChecker.check("A[Start --> B")
        assertEquals(1, issues.size)
        assertEquals("Unmatched opening '['", issues[0].message)
    }

    @Test
    fun `flags mismatched bracket kinds`() {
        val issues = MermaidSyntaxChecker.check("A[Start) --> B")
        assertTrue(issues.any { it.message == "Unmatched closing ')'" })
    }

    @Test
    fun `accepts valid nested shape combinations`() {
        // id[(cylinder)] and id([stadium]) both close in proper LIFO order.
        assertTrue(MermaidSyntaxChecker.check("A[(db)] --> B([stadium])").isEmpty())
    }

    @Test
    fun `flags a subgraph missing its end`() {
        val issues = MermaidSyntaxChecker.check("flowchart TD\nsubgraph one\nA --> B")
        assertEquals(1, issues.size)
        assertEquals("Missing matching 'end' for this subgraph", issues[0].message)
    }

    @Test
    fun `flags an unterminated string literal`() {
        val issues = MermaidSyntaxChecker.check("A --> \"never closed")
        assertEquals(1, issues.size)
        assertEquals("Unterminated string literal", issues[0].message)
    }

    @Test
    fun `does not flag brackets inside a quoted label`() {
        assertTrue(MermaidSyntaxChecker.check("""A["contains (parens) and [brackets]"] --> B""").isEmpty())
    }
}
