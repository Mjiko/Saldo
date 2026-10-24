package com.jiko.moneytracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR via ML Kit Text Recognition (bundled Latin model — no network,
 * no Play-services download). Returns the recognized text lines in reading order.
 *
 * Before OCR the photo is normalized on Dispatchers.Default (see
 * [prepareBitmap]): EXIF orientation applied physically, small photos
 * upscaled, and a grayscale + contrast pass to fight faded thermal prints.
 */
object ReceiptOcr {

    suspend fun recognizeLines(context: Context, uri: Uri): List<String> {
        val bitmap = withContext(Dispatchers.Default) { prepareBitmap(context, uri) }
            ?: throw IOException("Foto struk tidak bisa dibaca")
        return suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            cont.invokeOnCancellation { runCatching { recognizer.close() } }
            // prepareBitmap already rotated the pixels upright, so the
            // InputImage rotation is 0 (the old fromFilePath path relied on
            // EXIF being honoured, which silently fails for sideways photos
            // from some sources — a top cause of garbage OCR).
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks
                        .flatMap { it.lines }
                        .map { it.text.trim() }
                        .filter { it.isNotBlank() }
                    runCatching { recognizer.close() }
                    if (cont.isActive) cont.resume(lines)
                }
                .addOnFailureListener { e ->
                    runCatching { recognizer.close() }
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    /**
     * Decode + normalize a receipt photo for OCR:
     *  1. EXIF orientation applied to the pixels (rotate/flip matrix) —
     *     sideways photos were producing garbage text.
     *  2. Upscale so the short edge is at least ~1200px (small print on
     *     downscaled gallery picks reads much better) — capped so the long
     *     edge stays sane.
     *  3. Grayscale with a contrast boost for faded thermal paper.
     * Returns null when the image cannot be decoded at all.
     */
    private fun prepareBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        // EXIF orientation — read before decoding (works for file:// photos
        // from the camera intent and content:// gallery picks alike).
        val orientation = try {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        // Decode, sampling down only when the photo is enormous.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 3500) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // 1) Rotate / flip per EXIF.
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }

        // 2) Upscale so the short edge reaches ~1200px.
        val shortEdge = minOf(bmp.width, bmp.height)
        if (shortEdge in 1 until 1200) {
            val scale = 1200f / shortEdge
            val w = (bmp.width * scale).toInt().coerceAtMost(4200)
            val h = (bmp.height * scale).toInt().coerceAtMost(4200)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            if (scaled !== bmp) {
                bmp.recycle()
                bmp = scaled
            }
        }

        // 3) Grayscale + contrast boost (offset = (1 - c) * 128 for c = 1.35).
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, -44.8f,
                        0f, 1.35f, 0f, 0f, -44.8f,
                        0f, 0f, 1.35f, 0f, -44.8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(bmp, 0f, 0f, paint)
        bmp.recycle()
        return out
    }
}
