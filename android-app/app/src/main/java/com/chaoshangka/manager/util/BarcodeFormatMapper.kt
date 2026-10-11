package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/**
 * ML Kit 條碼格式 <-> 本 APP 的 [BarcodeFormatType] 對應。
 *
 * 這裡刻意「不」把一維條碼與二維碼混為同一種格式，也不把卡號文字當成條碼。
 */
object BarcodeFormatMapper {

    fun fromMlKit(format: Int): BarcodeFormatType = when (format) {
        MlKitBarcode.FORMAT_CODE_128 -> BarcodeFormatType.CODE_128
        MlKitBarcode.FORMAT_CODE_39 -> BarcodeFormatType.CODE_39
        MlKitBarcode.FORMAT_CODE_93 -> BarcodeFormatType.CODE_93
        MlKitBarcode.FORMAT_CODABAR -> BarcodeFormatType.CODABAR
        MlKitBarcode.FORMAT_EAN_13 -> BarcodeFormatType.EAN_13
        MlKitBarcode.FORMAT_EAN_8 -> BarcodeFormatType.EAN_8
        MlKitBarcode.FORMAT_ITF -> BarcodeFormatType.ITF
        MlKitBarcode.FORMAT_UPC_A -> BarcodeFormatType.UPC_A
        MlKitBarcode.FORMAT_UPC_E -> BarcodeFormatType.UPC_E
        MlKitBarcode.FORMAT_QR_CODE -> BarcodeFormatType.QR_CODE
        MlKitBarcode.FORMAT_DATA_MATRIX -> BarcodeFormatType.DATA_MATRIX
        MlKitBarcode.FORMAT_PDF417 -> BarcodeFormatType.PDF417
        MlKitBarcode.FORMAT_AZTEC -> BarcodeFormatType.AZTEC
        else -> BarcodeFormatType.UNKNOWN
    }

    /** 一維條碼是否可用 ZXing 重新繪製。 */
    fun canRender(format: BarcodeFormatType): Boolean = format != BarcodeFormatType.UNKNOWN
}
