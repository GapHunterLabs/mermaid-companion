package dev.gaphunter.mermaidcompanion.lang

import dev.gaphunter.mermaidcompanion.inspection.MermaidSyntaxChecker
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic large-diagram latency check, same rationale as
 * CMakeLexerLatencyTest: confirms the lexer and the syntax checker built
 * on top of it are linear over a realistically large generated file, not
 * accidentally quadratic. */
class MermaidLexerLatencyTest {
    @Test
    fun `tokenizing and checking a large generated flowchart does not hang`() {
        val sb = StringBuilder()
        sb.append("flowchart TD\n")
        val nodeCount = 60_000
        val padding = "détails supplémentaires ".repeat(6)
        for (i in 0 until nodeCount) {
            sb.append("    node_").append(i).append("[\"Step número ").append(i)
                .append(" café ").append(padding).append("\"] --> node_").append(i + 1).append("\n")
        }
        val largeFile = sb.toString()
        assertTrue("test fixture should exceed 10MB to be meaningful", largeFile.length > 10_000_000)

        val start = System.nanoTime()
        val lexer = MermaidLexer()
        lexer.start(largeFile, 0, largeFile.length, 0)
        var tokenCount = 0
        while (lexer.tokenType != null) {
            tokenCount++
            lexer.advance()
        }
        val issues = MermaidSyntaxChecker.check(largeFile)
        val elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0

        assertTrue("expected many tokens from a large file", tokenCount > nodeCount)
        assertTrue("well-formed generated file should have no unmatched brackets", issues.isEmpty())
        assertTrue(
            "lexing + checking a >10MB Mermaid file took ${elapsedSeconds}s -- too slow, likely quadratic behavior",
            elapsedSeconds < 30.0
        )
    }
}
