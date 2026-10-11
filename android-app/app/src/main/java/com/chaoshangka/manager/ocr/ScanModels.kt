package com.chaoshangka.manager.ocr

import com.chaoshangka.manager.domain.model.BarcodeFormatType

/**
 * OCR 文字區塊（座標為圖片像素）。
 *
 * 保留整個區塊與每一行的位置，讓 CardInfoExtractor 可以「內容 + 版面」綜合判斷，
 * 而不是只靠固定座標擷取。
 */
data class OcrBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lines: List<OcrLine> = emptyList(),
) {
    val centerY: Int get() = (top + bottom) / 2
}

data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** 一張卡片上被偵測到的條碼（可能有多組）。 */
data class DetectedBarcode(
    val barcodeFormat: BarcodeFormatType,
    /** 條碼原始內容，永遠是字串，前導零不會消失。 */
    val rawValue: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val hasValue: Boolean get() = rawValue.isNotBlank()
}

/** 一次掃描的完整分析結果。 */
data class ScanAnalysis(
    val blocks: List<OcrBlock> = emptyList(),
    val barcodes: List<DetectedBarcode> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val allText: String get() = blocks.joinToString("\n") { it.text }
}
