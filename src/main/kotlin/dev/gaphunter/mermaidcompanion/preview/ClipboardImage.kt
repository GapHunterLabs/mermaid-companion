package dev.gaphunter.mermaidcompanion.preview

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * A PNG from the preview as an image other applications accept from the clipboard (Slack, Jira,
 * Google Docs, an issue form). Decoding happens before anything touches the clipboard, so bytes
 * that are not a PNG image are refused instead of replacing what the user had copied.
 *
 * The page can draw a PNG of up to 268 million pixels; decoded in the IDE that is about 1 GB.
 * The size is read from the PNG header first, and an image over [MAX_PIXELS] is refused before
 * it is decoded.
 */
class ClipboardImage private constructor(val image: BufferedImage) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Image {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }

    sealed interface Result {
        class Ready(val image: ClipboardImage) : Result
        data class TooLarge(val width: Long, val height: Long) : Result
        object NotAnImage : Result
    }

    companion object {
        /** 40 million pixels: about 160 MB once decoded. */
        const val MAX_PIXELS = 40_000_000L

        private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** Width and height from the IHDR chunk, or null when the bytes do not start like a PNG. */
        fun headerSize(bytes: ByteArray): Pair<Long, Long>? {
            if (bytes.size < 24 || !SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }) return null
            if (bytes[12] != 'I'.code.toByte() || bytes[13] != 'H'.code.toByte() ||
                bytes[14] != 'D'.code.toByte() || bytes[15] != 'R'.code.toByte()) return null
            fun u32(at: Int): Long = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 0xFF) }
            return u32(16) to u32(20)
        }

        fun fromPng(bytes: ByteArray): Result {
            val (w, h) = headerSize(bytes) ?: return Result.NotAnImage
            if (w <= 0 || h <= 0) return Result.NotAnImage
            if (w * h > MAX_PIXELS) return Result.TooLarge(w, h)
            val image = try {
                ImageIO.read(ByteArrayInputStream(bytes))
            } catch (e: IOException) {
                null
            } ?: return Result.NotAnImage
            return if (image.width > 0 && image.height > 0) Result.Ready(ClipboardImage(image)) else Result.NotAnImage
        }
    }
}
