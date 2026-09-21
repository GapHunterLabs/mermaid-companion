package dev.gaphunter.mermaidcompanion.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every diagram in demo/ is valid Mermaid, so the syntax checker must
 * report nothing on any of them -- they use hexagon, cylinder and nested
 * shapes, generics, edge labels and %% comments.
 */
class DemoFilesTest {

    @Test
    fun theSyntaxCheckerIsSilentOnEveryDemoDiagram() {
        val demos = File("demo").listFiles { f -> f.extension in setOf("mmd", "mermaid") }.orEmpty()
        assertTrue("no demo diagrams found", demos.size >= 3)
        for (demo in demos) {
            assertEquals(demo.name, emptyList<MermaidIssue>(), MermaidSyntaxChecker.check(demo.readText()))
        }
    }
}
