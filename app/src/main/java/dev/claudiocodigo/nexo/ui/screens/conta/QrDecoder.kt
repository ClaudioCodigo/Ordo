package dev.claudiocodigo.nexo.ui.screens.conta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumSet

/**
 * ZXing-based QR decoder supporting compact camera luminance frames and
 * legacy JPEG/ARGB input.
 *
 * The camera path uses [PlanarYUVLuminanceSource] directly; the bitmap path is
 * retained for callers that already have JPEG/ARGB data.
 */
object QrDecoder {

    /** Hints restrict detection to QR codes (faster, avoids false positives). */
    private val hints: Map<com.google.zxing.DecodeHintType, Any> = mapOf(
        com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to EnumSet.of(BarcodeFormat.QR_CODE),
        com.google.zxing.DecodeHintType.TRY_HARDER to true
    )

    /** Decodes a single QR code from JPEG bytes, or returns `null`. */
    fun decodeJpeg(bytes: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decodeArgb(argb, bitmap.width, bitmap.height)
    }

    fun decodePixels(argb: IntArray, width: Int, height: Int): String? =
        decodeArgb(argb, width, height)

    /** Decodes directly from a compact Y (luminance) plane, avoiding an ARGB
     * bitmap allocation for every camera frame. */
    fun decodeLuma(luma: ByteArray, width: Int, height: Int): String? {
        if (luma.size < width * height) return null
        val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        return try {
            reader.setHints(hints)
            reader.decode(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun decodeArgb(argb: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, argb)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        return try {
            reader.setHints(hints)
            reader.decode(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
