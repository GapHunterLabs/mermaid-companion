package dev.gaphunter.mermaidcompanion.inspection

import dev.gaphunter.mermaidcompanion.lang.MermaidLexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Heavy inputs for the checker, which the editor runs on every edit. Off by default; run with
 * `./gradlew test -Pstress=true --tests '*StressTest'` (see docs/TESTING.md). Each case prints
 * one `STRESS|case|chars|ms|issues` line so the numbers can be recorded.
 *
 * The limits asserted here are generous on purpose (this has to pass on a slow machine);
 * the printed numbers are the result.
 */
class MermaidCheckerStressTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAsked() {
            assumeTrue("stress tests run only with -Pstress=true", System.getProperty("mermaid.stress") == "true")
        }
    }

    /** Median wall time of [runs] calls after [warmup] calls, in milliseconds. */
    private fun timeCheck(name: String, text: CharSequence, runs: Int = 5, warmup: Int = 2, maxMs: Double): List<MermaidIssue> {
        var issues = emptyList<MermaidIssue>()
        repeat(warmup) { issues = MermaidSyntaxChecker.check(text) }
        val times = (1..runs).map {
            val start = System.nanoTime()
            issues = MermaidSyntaxChecker.check(text)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val median = times[times.size / 2]
        println("STRESS|$name|${text.length}|${"%.3f".format(median)}|${issues.size}")
        assertTrue("$name took $median ms (limit $maxMs)", median < maxMs)
        return issues
    }

    private fun flowchartChain(nodes: Int) = buildString {
        append("flowchart TD\n")
        for (i in 0 until nodes) append("    n").append(i).append("[\"Node ").append(i).append(" café\"] --> n").append(i + 1).append("(Next ").append(i + 1).append(")\n")
    }

    @Test
    fun `realistic files take well under a millisecond or two per check`() {
        for (demo in File("demo").listFiles { f -> f.name.endsWith(".mmd") }!!.sortedBy { it.name }) {
            assertTrue(timeCheck("demo/${demo.name}", demo.readText(), runs = 201, warmup = 200, maxMs = 50.0).isEmpty())
        }
        assertTrue(timeCheck("flowchart 200 nodes", flowchartChain(200), runs = 101, warmup = 100, maxMs = 50.0).isEmpty())
        val sequence = "sequenceDiagram\n" + (0 until 300).joinToString("") { "    Alice->>Bob: message $it (note\n" }
        assertTrue(timeCheck("sequence 300 messages", sequence, runs = 101, warmup = 100, maxMs = 50.0).isEmpty())
    }

    @Test
    fun `thousands of nodes and edges`() {
        assertTrue(timeCheck("flowchart 10k nodes", flowchartChain(10_000), maxMs = 2_000.0).isEmpty())
        val mesh = buildString {
            append("flowchart LR\n")
            for (i in 0 until 5_000) append("    a").append(i).append(" --> b").append(i % 97).append(" & c").append(i % 89).append("\n")
        }
        assertTrue(timeCheck("flowchart 15k edges", mesh, maxMs = 2_000.0).isEmpty())
        assertTrue(timeCheck("flowchart 100k nodes", flowchartChain(100_000), runs = 3, warmup = 1, maxMs = 20_000.0).isEmpty())
    }

    @Test
    fun `a single line of several megabytes`() {
        val line = "flowchart LR\n" + "A[x] --> B(y) --> C{z} ".repeat(250_000)
        assertTrue(line.length > 5_000_000)
        assertTrue(timeCheck("one 5.5 MB line", line, runs = 3, warmup = 1, maxMs = 20_000.0).isEmpty())
        val sequenceLine = "sequenceDiagram\nA->>B: " + "(".repeat(5_000_000)
        assertTrue(timeCheck("sequence 5 MB free-text line", sequenceLine, runs = 3, warmup = 1, maxMs = 20_000.0).isEmpty())
    }

    @Test
    fun `nesting depth in the hundreds of thousands does not overflow the stack`() {
        val depth = 200_000
        val balanced = "flowchart LR\nA" + "[(".repeat(depth / 2) + "x" + ")]".repeat(depth / 2)
        assertTrue(timeCheck("balanced depth 200k", balanced, runs = 3, warmup = 1, maxMs = 10_000.0).isEmpty())
        val unclosed = "flowchart LR\nA" + "[".repeat(depth)
        assertEquals(depth, timeCheck("unclosed depth 200k", unclosed, runs = 3, warmup = 1, maxMs = 10_000.0).size)
        val subgraphs = "flowchart LR\n" + "subgraph s\n".repeat(depth)
        assertEquals(depth, timeCheck("subgraph depth 200k", subgraphs, runs = 3, warmup = 1, maxMs = 10_000.0).size)
    }

    @Test
    fun `pathological unterminated input stays linear`() {
        val n = 500_000
        // Every line opens a quote: the multi-line rule pairs them up across lines.
        val quotes = "flowchart LR\n" + "A[\"x\n".repeat(n)
        val small = "flowchart LR\n" + "A[\"x\n".repeat(n / 10)
        val tSmall = medianMs(small)
        val tBig = medianMs(quotes)
        println("STRESS|quote per line ${n / 10} vs $n|${small.length}/${quotes.length}|${"%.3f".format(tSmall)}/${"%.3f".format(tBig)}|-")
        assertTrue("10x the input took ${tBig / tSmall}x the time", tBig < tSmall * 30 + 50)
        // One quote that never closes, then megabytes of text.
        timeCheck("one open quote + 5 MB", "flowchart LR\nA[\"" + "x\n".repeat(2_500_000), runs = 3, warmup = 1, maxMs = 10_000.0)
        // Sequence and class diagrams report each unterminated string.
        val seq = timeCheck("sequence 200k open quotes", "sequenceDiagram\n" + "participant \"A\n".repeat(200_000), runs = 3, warmup = 1, maxMs = 10_000.0)
        assertEquals(200_000, seq.count { it.message == "Unterminated string literal" })
        // A class body that never closes stops the scan instead of repeating it.
        timeCheck("200k unclosed class bodies", "classDiagram\n" + "class A {\n".repeat(200_000), runs = 3, warmup = 1, maxMs = 10_000.0)
        // A directive that never closes, repeated.
        timeCheck("100k unclosed directives", "flowchart LR\n" + "%%{init\n".repeat(100_000), runs = 3, warmup = 1, maxMs = 10_000.0)
    }

    @Test
    fun `ten megabytes of random garbage`() {
        val alphabet = "\"[](){}|:;-=.>ox%_ \n\tAz0á日🚀"
        val random = Random(7)
        val text = buildString(10_000_000) { repeat(10_000_000) { append(alphabet[random.nextInt(alphabet.length)]) } }
        timeCheck("10 MB random garbage", text, runs = 3, warmup = 1, maxMs = 30_000.0)
        // The lexer alone, for comparison.
        val start = System.nanoTime()
        val lexer = MermaidLexer()
        lexer.start(text, 0, text.length, 0)
        var tokens = 0
        while (lexer.tokenType != null) { tokens++; lexer.advance() }
        println("STRESS|lexer only, 10 MB random garbage|${text.length}|${"%.3f".format((System.nanoTime() - start) / 1_000_000.0)}|$tokens tokens")
    }

    private fun medianMs(text: String): Double {
        MermaidSyntaxChecker.check(text)
        return (1..3).map {
            val s = System.nanoTime()
            MermaidSyntaxChecker.check(text)
            (System.nanoTime() - s) / 1_000_000.0
        }.sorted()[1]
    }
}
