package com.chaoshangka.manager.domain.model

/** 支援的超商。 */
enum class StoreType(
    val key: String,
    val displayName: String,
) {
    SEVEN_ELEVEN("SEVEN_ELEVEN", "7-ELEVEN"),
    FAMILYMART("FAMILYMART", "全家便利商店"),
    HILIFE("HILIFE", "萊爾富"),
    OKMART("OKMART", "OK超商"),
    UNKNOWN("UNKNOWN", "未指定"),
    ;

    companion object {
        /** 首頁顯示順序：7-ELEVEN、全家、萊爾富、OK超商。 */
        val mainStores: List<StoreType> = listOf(SEVEN_ELEVEN, FAMILYMART, HILIFE, OKMART)

        fun fromKey(key: String?): StoreType =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** 卡片狀態。 */
enum class CardStatus(val key: String, val displayName: String) {
    UNUSED("UNUSED", "未使用"),
    USED("USED", "已使用"),
    ARCHIVED("ARCHIVED", "已封存"),
    ;

    companion object {
        fun fromKey(key: String?): CardStatus =
            entries.firstOrNull { it.key == key } ?: UNUSED
    }
}

/** 卡片類型。 */
enum class CardType(val key: String, val displayName: String) {
    PICKUP_CARD("PICKUP_CARD", "指定商品提貨卡"),
    VALUE_CARD("VALUE_CARD", "金額型商品卡"),
    GIFT_CARD("GIFT_CARD", "禮品卡"),
    VOUCHER("VOUCHER", "抵用券"),
    OTHER("OTHER", "其他兌換卡"),
    UNKNOWN("UNKNOWN", "待確認"),
    ;

    companion object {
        fun fromKey(key: String?): CardType =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/**
 * 有效期限狀態。
 * [NEEDS_REVIEW] 代表「有日期文字但無法明確解析」，必須獨立標示，不可當成「無期限」。
 */
enum class ExpiryStatus(val key: String, val displayName: String) {
    EXPIRED("EXPIRED", "已過期"),
    DUE_TODAY("DUE_TODAY", "今天到期"),
    EXPIRING_SOON("EXPIRING_SOON", "即將到期"),
    VALID("VALID", "尚未到期"),
    NEEDS_REVIEW("NEEDS_REVIEW", "期限待確認"),
    NO_EXPIRY("NO_EXPIRY", "無期限"),
    ;

    companion object {
        fun fromKey(key: String?): ExpiryStatus =
            entries.firstOrNull { it.key == key } ?: NO_EXPIRY
    }
}

/** 條碼格式（與 ML Kit Format 對應，經 [com.chaoshangka.manager.util.BarcodeFormatMapper] 轉換）。 */
enum class BarcodeFormatType(val key: String, val displayName: String) {
    CODE_128("CODE_128", "Code 128"),
    CODE_39("CODE_39", "Code 39"),
    CODE_93("CODE_93", "Code 93"),
    CODABAR("CODABAR", "Codabar"),
    EAN_13("EAN_13", "EAN-13"),
    EAN_8("EAN_8", "EAN-8"),
    ITF("ITF", "ITF"),
    UPC_A("UPC_A", "UPC-A"),
    UPC_E("UPC_E", "UPC-E"),
    QR_CODE("QR_CODE", "QR Code"),
    DATA_MATRIX("DATA_MATRIX", "Data Matrix"),
    PDF417("PDF417", "PDF417"),
    AZTEC("AZTEC", "Aztec"),
    UNKNOWN("UNKNOWN", "未知格式"),
    ;

    val isTwoDimensional: Boolean
        get() = this == QR_CODE || this == DATA_MATRIX || this == PDF417 || this == AZTEC

    companion object {
        fun fromKey(key: String?): BarcodeFormatType =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** 條碼資訊來源狀態。 */
enum class RecognitionStatus(val key: String, val displayName: String) {
    /** 由條碼辨識元件讀出。 */
    RECOGNIZED("RECOGNIZED", "已辨識"),

    /** 使用者手動補登。 */
    MANUAL("MANUAL", "手動補登"),

    /** 沒辨識到任何條碼，等待補登。 */
    UNRECOGNIZED("UNRECOGNIZED", "待補登"),

    /** 條碼被遮蔽／刮除，或辨識結果不可信任。 */
    UNREADABLE("UNREADABLE", "無法讀取"),
    ;

    companion object {
        fun fromKey(key: String?): RecognitionStatus =
            entries.firstOrNull { it.key == key } ?: UNRECOGNIZED
    }
}
