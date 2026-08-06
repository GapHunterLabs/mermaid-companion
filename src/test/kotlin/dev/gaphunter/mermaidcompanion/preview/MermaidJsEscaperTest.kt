package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class MermaidJsEscaperTest {

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("graph TD; A-->B;", MermaidJsEscaper.escapeForTemplateLiteral("graph TD; A-->B;"))
    }

    @Test
    fun escapesBackticks() {
        assertEquals(
            "A[\\`quoted\\`]",
            MermaidJsEscaper.escapeForTemplateLiteral("A[`quoted`]"),
        )
    }

    @Test
    fun escapesDollarSignsToPreventTemplateInterpolation() {
        assertEquals(
            "A[Price: \\$5]",
            MermaidJsEscaper.escapeForTemplateLiteral("A[Price: \$5]"),
        )
    }

    @Test
    fun escapesBackslashesBeforeOtherEscaping() {
        // A literal backslash in the diagram must survive as one backslash
        // in the rendered string, not accidentally combine with the next
        // escape this function inserts.
        assertEquals("A[C:\\\\path]", MermaidJsEscaper.escapeForTemplateLiteral("A[C:\\path]"))
    }

    @Test
    fun escapesNewlinesAndCarriageReturns() {
        assertEquals(
            "graph TD;\\nA-->B;\\r\\n",
            MermaidJsEscaper.escapeForTemplateLiteral("graph TD;\nA-->B;\r\n"),
        )
    }

    @Test
    fun preservesNonAsciiCharactersUnescaped() {
        val diagram = "graph TD; subgraph á [日本語]; end;"
        assertEquals(diagram, MermaidJsEscaper.escapeForTemplateLiteral(diagram))
    }

    @Test
    fun handlesCombinationOfAllSpecialCharacters() {
        val input = "A[`\$5\\path`]\nB"
        val expected = "A[\\`\\\$5\\\\path\\`]\\nB"
        assertEquals(expected, MermaidJsEscaper.escapeForTemplateLiteral(input))
    }
}
