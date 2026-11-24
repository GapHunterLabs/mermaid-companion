package dev.gaphunter.mermaidcompanion.preview

import java.util.Base64

/**
 * One message from the preview page (companion-preview.js) to the plugin,
 * sent through a JBCefJSQuery as `kind:payload`. Parsed and validated here,
 * so nothing half-formed ever reaches the file system.
 */
sealed interface BridgeMessage {
    data class Svg(val markup: String) : BridgeMessage
    class Png(val bytes: ByteArray) : BridgeMessage
    data class Failure(val message: String) : BridgeMessage
    data class Zoom(val percent: Int) : BridgeMessage

    /** The render the plugin asked for (identified by [token]) finished. */
    data class Rendered(val token: Int) : BridgeMessage

    /** That render could not be drawn (a syntax error while typing, say). */
    data class RenderFailed(val token: Int, val message: String) : BridgeMessage

    /** A newer render replaced that one before it finished. */
    data class RenderSuperseded(val token: Int) : BridgeMessage

    /** Exports the plugin asked for itself (Export on Save): written without a file dialog. */
    data class SyncSvg(val markup: String) : BridgeMessage
    class SyncPng(val bytes: ByteArray) : BridgeMessage
    data class SyncFailure(val message: String) : BridgeMessage

    /** Exports for the clipboard: copied, never written to a file. */
    data class CopySvg(val markup: String) : BridgeMessage
    class CopyPng(val bytes: ByteArray) : BridgeMessage
    data class CopyFailure(val message: String) : BridgeMessage

    /** A click on a node, class, state, entity, subgraph or participant: move the caret to [name]. */
    data class GoToName(val name: String) : BridgeMessage

    /** A click on the [index]-th mindmap node (0 = the root). */
    data class GoToMindmapNode(val index: Int) : BridgeMessage

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

        /** Null for anything this plugin never sends. */
        fun parse(payload: String): BridgeMessage? {
            val kind = payload.substringBefore(':', "")
            val body = payload.substringAfter(':', "")
            return when (kind) {
                "svg" -> if (body.trimStart().startsWith("<svg")) Svg(body) else Failure("The exported SVG was empty or malformed.")
                "png" -> decodePng(body)
                "error" -> Failure(body.ifBlank { "Export failed." })
                "zoom" -> body.toIntOrNull()?.let { Zoom(it) }
                "rendered" -> body.toIntOrNull()?.let { Rendered(it) }
                "rendersuperseded" -> body.toIntOrNull()?.let { RenderSuperseded(it) }
                "renderfailed" -> body.substringBefore(':', "").toIntOrNull()?.let { token ->
                    RenderFailed(token, body.substringAfter(':', "").ifBlank { "The diagram doesn't render." })
                }
                "syncsvg" -> if (body.trimStart().startsWith("<svg")) SyncSvg(body) else SyncFailure("The exported SVG was empty or malformed.")
                "syncpng" -> when (val decoded = decodePng(body)) {
                    is Png -> SyncPng(decoded.bytes)
                    is Failure -> SyncFailure(decoded.message)
                    else -> null
                }
                "syncerror" -> SyncFailure(body.ifBlank { "Export failed." })
                "copysvg" -> if (body.trimStart().startsWith("<svg")) CopySvg(body) else CopyFailure("The copied SVG was empty or malformed.")
                "copypng" -> when (val decoded = decodePng(body)) {
                    is Png -> CopyPng(decoded.bytes)
                    is Failure -> CopyFailure(decoded.message)
                    else -> null
                }
                "copyerror" -> CopyFailure(body.ifBlank { "Copy failed." })
                "goto" -> parseGoTo(body)
                else -> null
            }
        }

        const val MAX_NAME_LENGTH = 256
        const val MAX_MINDMAP_INDEX = 100_000

        /** `name:<id>` or `index:<n>`; anything else, an empty or overlong name, or a control character is dropped. */
        private fun parseGoTo(body: String): BridgeMessage? {
            val kind = body.substringBefore(':', "")
            val value = body.substringAfter(':', "")
            return when (kind) {
                "name" -> value.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && it.none { c -> c.isISOControl() } }
                    ?.let { GoToName(it) }
                "index" -> value.takeIf { it.length <= 6 && it.all { c -> c in '0'..'9' } && it.isNotEmpty() }
                    ?.toInt()?.takeIf { it <= MAX_MINDMAP_INDEX }?.let { GoToMindmapNode(it) }
                else -> null
            }
        }

        private fun decodePng(base64: String): BridgeMessage {
            val bytes = try {
                Base64.getDecoder().decode(base64)
            } catch (e: IllegalArgumentException) {
                return Failure("The exported PNG was not valid base64.")
            }
            val isPng = bytes.size > PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
            return if (isPng) Png(bytes) else Failure("The exported PNG was empty or malformed.")
        }
    }
}
