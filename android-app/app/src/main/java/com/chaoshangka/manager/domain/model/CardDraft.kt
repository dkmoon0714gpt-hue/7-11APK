package com.chaoshangka.manager.domain.model

import java.time.LocalDate

/** 條碼草稿（尚未寫入資料庫）。 */
data class BarcodeDraft(
    val barcodeFormat: BarcodeFormatType,
    /** 一律以字串保存，保留前導零。 */
    val rawValue: String,
    val recognitionStatus: RecognitionStatus = RecognitionStatus.RECOGNIZED,
    val barcodeImagePath: String? = null,
    val isPrimary: Boolean = false,
)

/**
 * 卡片草稿：由掃描辨識或手動新增產生，經確認後才寫入資料庫。
 *
 * 任何欄位都可能未辨識（null 或空白），UI 必須讓使用者補登，且不得自動填入虛構資料。
 */
data class CardDraft(
    val storeType: StoreType = StoreType.UNKNOWN,
    val productName: String = "",
    val categoryId: Long? = null,
    val denomination: Int? = null,
    val productSpecification: String = "",
    val quantity: Int = 1,
    val cardType: CardType = CardType.UNKNOWN,
    val startDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    /** true 表示期限解析失敗或使用者尚未確認，儲存後會標示「期限待確認」。 */
    val expiryNeedsReview: Boolean = false,
    val cardNumber: String? = null,
    val notes: String = "",
    val originalImagePath: String? = null,
    val barcodes: List<BarcodeDraft> = emptyList(),
)
