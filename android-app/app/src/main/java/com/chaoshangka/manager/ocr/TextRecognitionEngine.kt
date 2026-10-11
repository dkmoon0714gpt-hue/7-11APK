package com.chaoshangka.manager.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 文字辨識引擎（ML Kit 內嵌中文模型，離線可用、不需連線下載模型）。
 *
 * 使用 `com.google.mlkit:text-recognition-chinese` 這類 bundled 版本，
 * 因此不需要 Google Play 服務、也不需要網路權限。
 */
class TextRecognitionEngine {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    suspend fun recognize(bitmap: Bitmap): List<OcrBlock> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    continuation.resume(mapBlocks(text))
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    private fun mapBlocks(text: com.google.mlkit.vision.text.Text): List<OcrBlock> =
        text.textBlocks.map { block ->
            OcrBlock(
                text = block.lines.joinToString("\n") { it.text },
                left = block.boundingBox?.left ?: 0,
                top = block.boundingBox?.top ?: 0,
                right = block.boundingBox?.right ?: 0,
                bottom = block.boundingBox?.bottom ?: 0,
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        left = line.boundingBox?.left ?: 0,
                        top = line.boundingBox?.top ?: 0,
                        right = line.boundingBox?.right ?: 0,
                        bottom = line.boundingBox?.bottom ?: 0,
                    )
                },
            )
        }

    fun close() {
        recognizer.close()
    }
}
