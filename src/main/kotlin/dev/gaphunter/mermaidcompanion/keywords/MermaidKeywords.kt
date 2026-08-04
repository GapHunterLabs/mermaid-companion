package dev.gaphunter.mermaidcompanion.keywords

/** Structural keywords shared across flowchart/sequence/class diagrams --
 * not an exhaustive Mermaid grammar, just the ones worth highlighting
 * distinctly (diagram-type declarations, block openers/closers). Mermaid
 * keywords are case-sensitive, unlike CMake's commands. */
object MermaidKeywords {
    private val KEYWORDS = setOf(
        "flowchart", "graph", "subgraph", "end",
        "sequenceDiagram", "participant", "actor", "activate", "deactivate",
        "loop", "alt", "else", "opt", "par", "and", "rect", "note", "over",
        "classDiagram", "class", "interface", "enum",
    )

    fun isKeyword(word: String): Boolean = word in KEYWORDS
}
