package com.chaoshangka.manager.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.chaoshangka.manager.ocr.DetectedBarcode
import com.chaoshangka.manager.util.BarcodeFormatMapper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * 即時條碼提示：鏡頭對到條碼時可以立即顯示「已偵測到 N 組條碼」，
 * 讓使用者知道可以按下快門。真正寫入資料仍以拍下的照片辨識結果為準。
 */
class LiveBarcodeAnalyzer(
    private val onDetected: (List<DetectedBarcode>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onDetected(
                    barcodes.map { barcode ->
                        val box = barcode.boundingBox
                        DetectedBarcode(
                            barcodeFormat = BarcodeFormatMapper.fromMlKit(barcode.format),
                            rawValue = barcode.rawValue ?: barcode.displayValue.orEmpty(),
                            left = box?.left ?: 0,
                            top = box?.top ?: 0,
                            right = box?.right ?: 0,
                            bottom = box?.bottom ?: 0,
                        )
                    },
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        scanner.close()
    }
}
