package com.chaoshangka.manager.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 備份檔格式（ZIP 容器）。
 *
 * 檔案結構：
 * ```
 * manifest.json     備份版本、APP 版本、匯出時間、計數
 * cards.json        卡片資料
 * barcodes.json     條碼資料（以 sourceCardId 對應 cards 的 sourceId）
 * categories.json   自訂與預設分類
 * settings.json     必要的設定資料
 * photos 目錄        原始卡片照片（jpg）
 * ```
 *
 * [BackupManifest.backupVersion] 用於版本相容判斷，APP 升級時仍可讀取舊備份。
 */
@Serializable
data class BackupManifest(
    @SerialName("backupVersion") val backupVersion: Int = CURRENT_BACKUP_VERSION,
    @SerialName("appVersion") val appVersion: String = "",
    @SerialName("exportedAt") val exportedAt: Long = 0L,
    @SerialName("cardCount") val cardCount: Int = 0,
    @SerialName("barcodeCount") val barcodeCount: Int = 0,
    @SerialName("categoryCount") val categoryCount: Int = 0,
    @SerialName("photoCount") val photoCount: Int = 0,
) {
    companion object {
        const val CURRENT_BACKUP_VERSION = 1
        const val MIN_SUPPORTED_VERSION = 1
    }
}

@Serializable
data class BackupCard(
    @SerialName("sourceId") val sourceId: Long,
    @SerialName("storeType") val storeType: String,
    @SerialName("productName") val productName: String,
    @SerialName("categoryName") val categoryName: String? = null,
    @SerialName("denomination") val denomination: Int? = null,
    @SerialName("productSpecification") val productSpecification: String = "",
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("cardType") val cardType: String,
    @SerialName("startDate") val startDate: String? = null,
    @SerialName("expiryDate") val expiryDate: String? = null,
    @SerialName("expiryNeedsReview") val expiryNeedsReview: Boolean = false,
    @SerialName("cardStatus") val cardStatus: String,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long,
    @SerialName("usedAt") val usedAt: Long? = null,
    @SerialName("cardNumber") val cardNumber: String? = null,
    @SerialName("notes") val notes: String = "",
    @SerialName("isArchived") val isArchived: Boolean = false,
    @SerialName("photoFile") val photoFile: String? = null,
)

@Serializable
data class BackupBarcode(
    @SerialName("sourceCardId") val sourceCardId: Long,
    @SerialName("barcodeFormat") val barcodeFormat: String,
    /** 條碼內容以字串儲存，前導零不會消失。 */
    @SerialName("rawValue") val rawValue: String,
    @SerialName("barcodeFormatRaw") val barcodeFormatRaw: String? = null,
    @SerialName("isPrimary") val isPrimary: Boolean = false,
    @SerialName("recognitionStatus") val recognitionStatus: String,
    @SerialName("photoFile") val photoFile: String? = null,
)

@Serializable
data class BackupCategory(
    @SerialName("storeType") val storeType: String? = null,
    @SerialName("categoryName") val categoryName: String,
    @SerialName("denomination") val denomination: Int? = null,
    @SerialName("productSpecification") val productSpecification: String? = null,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("isPresetProduct") val isPresetProduct: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("sortOrder") val sortOrder: Int = 0,
)

@Serializable
data class BackupSettings(
    @SerialName("warningDays") val warningDays: Int = 7,
    @SerialName("farWarningDays") val farWarningDays: Int = 30,
)

/** 匯入結果（顯示給使用者確認與回報）。 */
data class RestoreReport(
    val cardsImported: Int,
    val barcodesImported: Int,
    val categoriesImported: Int,
    val photosRestored: Int,
    val duplicateCardsSkipped: Int,
    val warnings: List<String> = emptyList(),
)

/** 匯入前的檢查結果。 */
data class BackupInspection(
    val manifest: BackupManifest,
    val cardCount: Int,
    val barcodeCount: Int,
    val categoryCount: Int,
    val potentialDuplicates: Int,
    val versionSupported: Boolean,
    val problems: List<String> = emptyList(),
) {
    val isUsable: Boolean
        get() = versionSupported && problems.isEmpty()
}
