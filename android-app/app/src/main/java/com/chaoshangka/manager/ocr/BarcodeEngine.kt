package com.chaoshangka.manager.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.util.BarcodeFormatMapper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 條碼辨識引擎：方向校正、灰階、高對比與多次結果交叉確認。 */
class BarcodeEngine {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )

    private data class Candidate(
        val barcode: DetectedBarcode,
        var observations: Int = 1,
    )

    suspend fun detect(bitmap: Bitmap): List<DetectedBarcode> {
        val candidates = LinkedHashMap<String, Candidate>()
        val fallback = LinkedHashMap<String, DetectedBarcode>()

        for (variant in imageVariants(bitmap)) {
            for (rotation in ROTATIONS) {
                val rotated = if (rotation == 0) variant else rotateBitmap(variant, rotation)
                val detections = (detectSingle(rotated) + decodeWithZxing(rotated))
                    .filter { it.hasValue }
                    .distinctBy { it.rawValue }
                detections.forEach { barcode ->
                    val key = barcode.rawValue
                    fallback.putIfAbsent(key, barcode)
                    val candidate = candidates[key]
                    if (candidate == null) {
                        candidates[key] = Candidate(barcode)
                    } else {
                        candidate.observations++
                    }
                }
                if (rotated !== variant) rotated.recycle()
            }
            if (variant !== bitmap) variant.recycle()
        }

        val confirmed = candidates.values
            .filter { it.observations >= MIN_OBSERVATIONS }
            .sortedByDescending { it.observations }
            .map { it.barcode }
        if (confirmed.isNotEmpty()) return confirmed

        return fallback.values.toList()
    }

    private fun imageVariants(bitmap: Bitmap): List<Bitmap> = buildList {
        add(bitmap)
        add(transform(bitmap, grayscale = true, contrast = 1.35f))
        add(transform(bitmap, grayscale = true, contrast = 1.9f))
    }

    private fun transform(bitmap: Bitmap, grayscale: Boolean, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val scale = (1f - contrast) * 128f
        val matrix = ColorMatrix().apply {
            if (grayscale) setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, scale,
                        0f, contrast, 0f, 0f, scale,
                        0f, 0f, contrast, 0f, scale,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        Canvas(result).drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
        return result
    }

    private suspend fun detectSingle(bitmap: Bitmap): List<DetectedBarcode> =
        suspendCancellableCoroutine { continuation ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    continuation.resume(barcodes.map { it.toDetected() })
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    private fun decodeWithZxing(bitmap: Bitmap): List<DetectedBarcode> = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to RETAIL_FORMATS,
        )
        val result = MultiFormatReader().apply { setHints(hints) }
            .decode(BinaryBitmap(HybridBinarizer(source)))
        val format = when (result.barcodeFormat) {
            com.google.zxing.BarcodeFormat.CODE_128 -> BarcodeFormatType.CODE_128
            com.google.zxing.BarcodeFormat.CODE_39 -> BarcodeFormatType.CODE_39
            com.google.zxing.BarcodeFormat.CODE_93 -> BarcodeFormatType.CODE_93
            com.google.zxing.BarcodeFormat.CODABAR -> BarcodeFormatType.CODABAR
            com.google.zxing.BarcodeFormat.EAN_13 -> BarcodeFormatType.EAN_13
            com.google.zxing.BarcodeFormat.EAN_8 -> BarcodeFormatType.EAN_8
            com.google.zxing.BarcodeFormat.ITF -> BarcodeFormatType.ITF
            com.google.zxing.BarcodeFormat.UPC_A -> BarcodeFormatType.UPC_A
            com.google.zxing.BarcodeFormat.UPC_E -> BarcodeFormatType.UPC_E
            com.google.zxing.BarcodeFormat.QR_CODE -> BarcodeFormatType.QR_CODE
            com.google.zxing.BarcodeFormat.DATA_MATRIX -> BarcodeFormatType.DATA_MATRIX
            com.google.zxing.BarcodeFormat.PDF_417 -> BarcodeFormatType.PDF417
            com.google.zxing.BarcodeFormat.AZTEC -> BarcodeFormatType.AZTEC
            else -> BarcodeFormatType.UNKNOWN
        }
        listOf(DetectedBarcode(format, result.text))
    }.getOrDefault(emptyList())

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun Barcode.toDetected(): DetectedBarcode {
        val box = boundingBox
        val value = rawValue ?: displayValue.orEmpty()
        return DetectedBarcode(
            barcodeFormat = BarcodeFormatMapper.fromMlKit(format),
            rawValue = value,
            left = box?.left ?: 0,
            top = box?.top ?: 0,
            right = box?.right ?: 0,
            bottom = box?.bottom ?: 0,
        )
    }

    fun close() {
        scanner.close()
    }

    companion object {
        private val ROTATIONS = listOf(0, 90, 180, 270)
        private val RETAIL_FORMATS = listOf(
            com.google.zxing.BarcodeFormat.CODE_128,
            com.google.zxing.BarcodeFormat.CODE_39,
            com.google.zxing.BarcodeFormat.CODE_93,
            com.google.zxing.BarcodeFormat.CODABAR,
            com.google.zxing.BarcodeFormat.EAN_13,
            com.google.zxing.BarcodeFormat.EAN_8,
            com.google.zxing.BarcodeFormat.ITF,
            com.google.zxing.BarcodeFormat.UPC_A,
            com.google.zxing.BarcodeFormat.UPC_E,
        )
        private const val MIN_OBSERVATIONS = 2
    }
}
