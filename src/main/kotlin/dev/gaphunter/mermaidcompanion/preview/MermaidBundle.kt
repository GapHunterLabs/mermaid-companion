package dev.gaphunter.mermaidcompanion.preview

/**
 * Lazily reads the bundled mermaid.js (pinned 11.16.1, MIT, downloaded
 * once and committed under `resources/mermaid/` alongside its
 * `LICENSE-mermaid.txt` -- never fetched at runtime) once per IDE
 * session and caches it in memory. `JBCefBrowserBase.loadHTML` takes a
 * `String`, not a file path, so inlining the script content directly
 * into the shell HTML is the only way to serve it without standing up
 * a custom JCEF scheme handler, which is more machinery than v1 needs.
 */
object MermaidBundle {
    val scriptContent: String by lazy {
        val stream = MermaidBundle::class.java.getResourceAsStream("/mermaid/mermaid.min.js")
            ?: error("mermaid.min.js resource missing from plugin jar")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
