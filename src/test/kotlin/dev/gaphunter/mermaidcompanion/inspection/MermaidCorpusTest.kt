package dev.gaphunter.mermaidcompanion.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The checker's verdicts against the bundled mermaid.js on the same files.
 *
 * `src/test/resources/corpus/` holds one diagram per file, covering every diagram type
 * mermaid.js 11.16.1 ships plus edge cases (Unicode, CRLF, BOM, tabs, directives,
 * front matter, free text with brackets and quotes). `mermaid-verdicts.tsv` next to them
 * records what mermaid.parse() said about each file, produced headless by
 * `tools/render-corpus.py` (see docs/TESTING.md). This test never runs mermaid.js; it
 * compares the checker with that recorded verdict:
 *
 * - false positive: mermaid accepts the file, the checker reports an error. Never allowed.
 * - false negative: mermaid rejects the file, the checker reports nothing. Allowed only
 *   for the files listed in [KNOWN_FALSE_NEGATIVES], so a new one (or a fixed one) is
 *   noticed and the list stays honest.
 */
class MermaidCorpusTest {

    private val corpus = File("src/test/resources/corpus")

    private data class Verdict(val file: String, val parses: Boolean, val detail: String)

    private fun verdicts(): List<Verdict> =
        File(corpus, "mermaid-verdicts.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val cols = line.split('\t')
                Verdict(cols[0], cols[1] == "ok", cols.getOrElse(2) { "" })
            }

    private fun text(file: String) = File(corpus, file).readBytes().toString(Charsets.UTF_8)

    @Test
    fun `every corpus file has a recorded verdict and every verdict has a file`() {
        val onDisk = corpus.listFiles { f -> f.name.endsWith(".mmd") }!!.map { it.name }.toSortedSet()
        val recorded = verdicts().map { it.file }.toSortedSet()
        assertEquals(onDisk, recorded)
        assertTrue("the corpus should cover a meaningful number of diagrams", recorded.size >= 80)
    }

    @Test
    fun `the checker never reports an error in a diagram mermaid accepts`() {
        val falsePositives = verdicts().filter { it.parses }.mapNotNull { v ->
            val issues = MermaidSyntaxChecker.check(text(v.file))
            if (issues.isEmpty()) null else "${v.file}: " + issues.joinToString("; ") { "${it.message} @${it.offset}" }
        }
        assertTrue("false positives against mermaid.js:\n" + falsePositives.joinToString("\n"), falsePositives.isEmpty())
    }

    @Test
    fun `the diagrams the checker misses are exactly the documented ones`() {
        val falseNegatives = verdicts().filter { !it.parses && MermaidSyntaxChecker.check(text(it.file)).isEmpty() }
            .map { it.file }.toSortedSet()
        assertEquals(KNOWN_FALSE_NEGATIVES.toSortedSet(), falseNegatives)
    }

    @Test
    fun `the checker flags every corpus file mermaid rejects for a reason it claims to check`() {
        // Bracket, string and subgraph errors in the three diagram types the checker covers.
        val claimed = listOf(
            "invalid-unterminated-string.mmd",
            "invalid-unclosed-bracket.mmd",
            "invalid-extra-closing-bracket.mmd",
            "invalid-mismatched-bracket.mmd",
            "invalid-subgraph-without-end.mmd",
            "invalid-flowchart-asymmetric-unclosed.mmd",
            "invalid-flowchart-multiline-label-unclosed.mmd",
            "invalid-flowchart-end-inside-subgraph-label.mmd",
            "invalid-flowchart-paren-in-edge-label.mmd",
            "invalid-class-unclosed-body.mmd",
            "invalid-sequence-unbalanced-participant.mmd",
        )
        val missed = claimed.filter { MermaidSyntaxChecker.check(text(it)).isEmpty() }
        assertTrue("missed: $missed", missed.isEmpty())
    }

    companion object {
        /**
         * Rejected by mermaid.js, silent in the checker. Each one is outside what the checker
         * claims to validate (brackets, strings and subgraph/end in flowchart, sequence and
         * class diagrams): the preview still shows mermaid's own error for them.
         */
        val KNOWN_FALSE_NEGATIVES = listOf(
            // No diagram at all: nothing to underline; the preview says "No diagram type detected".
            "invalid-empty.mmd",
            "invalid-whitespace-only.mmd",
            "invalid-unknown-type.mmd",
            "invalid-zenuml-not-bundled.mmd",
            // A directive that never closes swallows the rest of the file in mermaid.js.
            "invalid-flowchart-directive-unclosed.mmd",
            // Grammar errors that are not bracket, string or subgraph errors.
            "invalid-end-without-subgraph.mmd",
            "invalid-flowchart-bad-arrow.mmd",
            "invalid-sequence-missing-arrow-target.mmd",
            // Diagram types the checker does not validate.
            "invalid-er-bad-cardinality.mmd",
            "invalid-pie-unterminated-label.mmd",
            "invalid-state-unclosed-composite.mmd",
        )
    }
}
