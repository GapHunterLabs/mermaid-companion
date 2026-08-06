package dev.gaphunter.mermaidcompanion.preview

/**
 * Escapes diagram text for safe interpolation into a JS template
 * literal (`` `...` ``) built as a string and handed to
 * `CefBrowser.executeJavaScript`. A diagram containing a backtick, a
 * `${'$'}{...}` sequence, or a raw newline would otherwise break out of
 * the literal or get silently mangled -- the one piece of custom logic
 * in the preview feature worth unit testing on its own, since the
 * actual rendering is done by bundled mermaid.js, not this plugin's
 * code.
 */
object MermaidJsEscaper {
    fun escapeForTemplateLiteral(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '`' -> sb.append("\\`")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
