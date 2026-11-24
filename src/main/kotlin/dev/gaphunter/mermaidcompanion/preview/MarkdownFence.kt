package dev.gaphunter.mermaidcompanion.preview

/**
 * The diagram as a Markdown code block that GitHub, GitLab and most wikis render as a diagram:
 * ```mermaid ... ```. A fence must be longer than any run of backticks inside the text, or the
 * block would end early, so a diagram that contains ``` gets a fence of four (and so on).
 * Line endings become \n and the text keeps its own content otherwise; a trailing newline is not doubled.
 */
object MarkdownFence {
    private const val MIN_FENCE = 3
    private val BACKTICK_RUN = Regex("`+")

    fun wrap(diagram: String): String {
        val body = diagram.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        val longest = BACKTICK_RUN.findAll(body).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(MIN_FENCE, longest + 1))
        return "${fence}mermaid\n$body\n$fence\n"
    }
}
