package com.chaoshangka.manager.domain.model

import java.time.LocalDate

/**
 * 卡片主資料。
 *
 * 注意：[barcodes] 內的 [Barcode.rawValue] 一律是 String，前導零不可消失。
 */
data class GiftCard(
    val id: Long = 0L,
    val storeType: StoreType = StoreType.UNKNOWN,
    val productName: String = "",
    val categoryId: Long? = null,
    /** 面額（新臺幣元）。辨識不到時為 null，UI 顯示「待確認」。 */
    val denomination: Int? = null,
    val productSpecification: String = "",
    val quantity: Int = 1,
    val cardType: CardType = CardType.UNKNOWN,
    /** 有效期限起始日（單一日期時與 [expiryDate] 相同）。 */
    val startDate: LocalDate? = null,
    /** 有效期限結束日；排序以此欄位為依據。 */
    val expiryDate: LocalDate? = null,
    val expiryStatus: ExpiryStatus = ExpiryStatus.NO_EXPIRY,
    val cardStatus: CardStatus = CardStatus.UNUSED,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 使用者按下「使用」當下的本機時間（毫秒）。 */
    val usedAt: Long? = null,
    val originalImagePath: String? = null,
    val notes: String = "",
    val isArchived: Boolean = false,
    /** 卡片上另行標示的卡號（與條碼內容不同，例如全家 E100025001788209）。 */
    val cardNumber: String? = null,
    val barcodes: List<Barcode> = emptyList(),
) {
    val primaryBarcode: Barcode?
        get() = barcodes.firstOrNull { it.isPrimary } ?: barcodes.firstOrNull()

    /** 排除已封存的卡片。 */
    val isActive: Boolean
        get() = !isArchived
}

/** 卡片上的一組條碼。 */
data class Barcode(
    val id: Long = 0L,
    val giftCardId: Long = 0L,
    val barcodeFormat: BarcodeFormatType = BarcodeFormatType.UNKNOWN,
    /** 條碼原始內容，永遠以字串保存（保留前導零）。 */
    val rawValue: String = "",
    val barcodeImagePath: String? = null,
    /** 是否為主要兌換條碼；同一張卡片最多一組為 true。 */
    val isPrimary: Boolean = false,
    val recognitionStatus: RecognitionStatus = RecognitionStatus.UNRECOGNIZED,
    val createdAt: Long = 0L,
) {
    val maskedTail: String
        get() = if (rawValue.length <= 4) rawValue else "…" + rawValue.takeLast(4)
}

/** 商品分類（由資料庫管理，不寫死在畫面）。 */
data class ProductCategory(
    val id: Long = 0L,
    /** null 表示通用分類（所有超商皆可選）。 */
    val storeType: StoreType? = null,
    val categoryName: String = "",
    val denomination: Int? = null,
    val productSpecification: String? = null,
    val isDefault: Boolean = false,
    /** true 表示這是超商頁「尚無卡片」區的商品項目捷徑，不是真正的分類。 */
    val isPresetProduct: Boolean = false,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
)

/** 有效期限解析結果。 */
data class ExpiryInfo(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val status: ExpiryStatus,
    /** 卡片上原始日期文字（可能為 null）。 */
    val rawText: String?,
) {
    val isKnown: Boolean
        get() = status != ExpiryStatus.NEEDS_REVIEW && status != ExpiryStatus.NO_EXPIRY
}
