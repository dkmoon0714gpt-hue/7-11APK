package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.CardType

/**
 * 面額選項與面額解析。
 *
 * 下列預設面額只是 UI 常用選項，不代表所有超商都有提供；使用者可自行輸入其他金額。
 */
object DenominationOptions {

    val PRESETS: List<Int> = listOf(10, 20, 30, 35, 50, 60, 65, 100, 200, 300, 500, 1000)

    const val LABEL_OTHER_AMOUNT = "其他金額"
    const val LABEL_SPECIFIC_PRODUCT = "指定商品，非固定面額"
    const val LABEL_NO_DENOMINATION = "無面額"
    const val LABEL_PENDING = "待確認"

    private val YUAN_REGEX = Regex("""(?<!\d)(\d{1,5})\s*(?:元|圓)""")
    private val DOLLAR_REGEX = Regex("""[$＄]\s*(\d{1,5})""")
    private val PRODUCT_CARD_REGEX = Regex("""(?:商品卡|禮品卡|禮券卡|現金禮券)\s*(\d{1,5})""")

    /**
     * 從卡片文字解析面額。
     *
     * 必須有「元／圓／$」或「商品卡」等明確線索才會回傳金額，
     * 避免把卡號、條碼或日期誤認為面額；無法判斷時回傳 null（UI 顯示「待確認」）。
     */
    fun parse(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val normalized = DateParser.normalize(text)
        YUAN_REGEX.find(normalized)?.let { return it.groupValues[1].toIntOrNull() }
        DOLLAR_REGEX.find(normalized)?.let { return it.groupValues[1].toIntOrNull() }
        PRODUCT_CARD_REGEX.find(normalized)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    fun display(denomination: Int?): String = denomination?.let { "$it 元" } ?: LABEL_PENDING

    /** 一般（非指定商品）的金額型面額。 */
    fun isPreset(denomination: Int?): Boolean =
        denomination != null && PRESETS.contains(denomination)
}

/** 由卡片文字判斷卡片類型；無法判斷時回傳 [CardType.UNKNOWN]（待確認），不猜測。 */
object CardTypeClassifier {

    fun classify(text: String?): CardType {
        if (text.isNullOrBlank()) return CardType.UNKNOWN
        val normalized = DateParser.normalize(text)
        return when {
            normalized.contains("抵用") ||
                normalized.contains("折價") ||
                normalized.contains("優惠券") ||
                normalized.contains("兌換券") -> CardType.VOUCHER

            normalized.contains("商品卡") ||
                normalized.contains("現金禮券") -> CardType.VALUE_CARD

            normalized.contains("提貨") ||
                normalized.contains("提領") ||
                normalized.contains("免費兌換") -> CardType.PICKUP_CARD

            normalized.contains("禮品") ||
                normalized.contains("禮物") ||
                normalized.contains("禮券") -> CardType.GIFT_CARD

            normalized.contains("兌換") -> CardType.OTHER

            else -> CardType.UNKNOWN
        }
    }
}
