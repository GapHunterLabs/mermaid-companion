package dev.gaphunter.mermaidcompanion.preview

/**
 * The preview's HTML shell: a viewport that clips, a stage that is
 * translated/scaled for zoom and pan, and an error bar that keeps the last
 * good diagram visible while the current text doesn't parse. The element
 * ids are the contract with `preview/companion-preview.js` (checked in
 * PreviewPageTest).
 */
object PreviewPage {
    val ELEMENT_IDS = listOf("viewport", "stage", "error")

    val companionScript: String by lazy {
        val stream = PreviewPage::class.java.getResourceAsStream("/preview/companion-preview.js")
            ?: error("companion-preview.js resource missing from plugin jar")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun html(mermaidScript: String, companionScript: String): String = """
        <html>
          <head>
            <style>
              html, body { margin: 0; height: 100%; overflow: hidden; background: #ffffff; }
              #viewport { position: absolute; inset: 0; overflow: hidden; cursor: grab; }
              #stage { position: absolute; left: 0; top: 0; transform-origin: 0 0; }
              #error { display: none; position: absolute; left: 0; right: 0; top: 0; z-index: 1;
                       padding: 4px 8px; font: 12px sans-serif; background: #fdecea; color: #611a15; }
            </style>
          </head>
          <body>
            <div id="error"></div>
            <div id="viewport"><div id="stage"></div></div>
            <script>$mermaidScript</script>
            <script>$companionScript</script>
          </body>
        </html>
    """.trimIndent()
}
