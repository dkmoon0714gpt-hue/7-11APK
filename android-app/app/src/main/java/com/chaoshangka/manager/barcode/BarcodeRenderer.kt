package com.chaoshangka.manager.barcode

import android.graphics.Bitmap
import android.graphics.Color
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

/**
 * 條碼繪製（ZXing，純本機、離線）。
 *
 * 規則：
 *  - 一維條碼：黑白高對比、保留左右留白（quiet zone），不任意改變長寬比例。
 *  - 二維碼：維持正方形（原始比例）。
 *  - 內容無法重新編碼時回傳 null，由 UI 改為顯示原始卡片照片，**不硬畫錯誤的條碼**。
 */
object BarcodeRenderer {

    private const val DEFAULT_QUIET_ZONE_MODULES = 12
    private const val ONE_D_MIN_HEIGHT_PX = 320

    fun render(
        rawValue: String,
        format: BarcodeFormatType,
        targetWidthPx: Int,
        quietZoneModules: Int = DEFAULT_QUIET_ZONE_MODULES,
    ): Bitmap? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        val zxingFormat = toZxingFormat(format) ?: return null
        val width = targetWidthPx.coerceAtLeast(240)
        val height = if (format.isTwoDimensional) width else ONE_D_MIN_HEIGHT_PX

        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, quietZoneModules)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }

        return runCatching {
            val matrix = MultiFormatWriter().encode(value, zxingFormat, width, height, hints)
            matrix.toBitmap()
        }.getOrNull()
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun toZxingFormat(format: BarcodeFormatType): BarcodeFormat? = when (format) {
        BarcodeFormatType.CODE_128 -> BarcodeFormat.CODE_128
        BarcodeFormatType.CODE_39 -> BarcodeFormat.CODE_39
        BarcodeFormatType.CODE_93 -> BarcodeFormat.CODE_93
        BarcodeFormatType.CODABAR -> BarcodeFormat.CODABAR
        BarcodeFormatType.EAN_13 -> BarcodeFormat.EAN_13
        BarcodeFormatType.EAN_8 -> BarcodeFormat.EAN_8
        BarcodeFormatType.ITF -> BarcodeFormat.ITF
        BarcodeFormatType.UPC_A -> BarcodeFormat.UPC_A
        BarcodeFormatType.UPC_E -> BarcodeFormat.UPC_E
        BarcodeFormatType.QR_CODE -> BarcodeFormat.QR_CODE
        BarcodeFormatType.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        BarcodeFormatType.PDF417 -> BarcodeFormat.PDF_417
        BarcodeFormatType.AZTEC -> BarcodeFormat.AZTEC
        BarcodeFormatType.UNKNOWN -> null
    }
}
