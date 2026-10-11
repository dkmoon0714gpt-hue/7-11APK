package com.chaoshangka.manager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 卡片主資料表。
 *
 * 日期一律以標準數值型態儲存：
 *  - [startDate] / [expiryDate]：`LocalDate.toEpochDay()`（天）
 *  - [createdAt] / [updatedAt] / [usedAt]：epoch millis
 * 不可儲存民國年字串，避免排序與比較出錯。
 */
@Entity(
    tableName = "gift_cards",
    indices = [
        Index("cardStatus"),
        Index("storeType"),
        Index("expiryDate"),
        Index("categoryId"),
    ],
)
data class GiftCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storeType: String,
    val productName: String,
    val categoryId: Long?,
    val denomination: Int?,
    val productSpecification: String,
    val quantity: Int,
    val cardType: String,
    val startDate: Long?,
    val expiryDate: Long?,
    val expiryStatus: String,
    val cardStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val usedAt: Long?,
    val originalImagePath: String?,
    val notes: String,
    val isArchived: Boolean,
    val cardNumber: String?,
)

/**
 * 條碼子資料表。一張卡片可有多組條碼。
 *
 * 重點：[rawValue] 是 **String**，前導零必須保留（例如 `0014835`）。
 */
@Entity(
    tableName = "barcodes",
    foreignKeys = [
        ForeignKey(
            entity = GiftCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["giftCardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("giftCardId"), Index("rawValue")],
)
data class BarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val giftCardId: Long,
    val barcodeFormat: String,
    val rawValue: String,
    val barcodeImagePath: String?,
    val isPrimary: Boolean,
    val recognitionStatus: String,
    val createdAt: Long,
)

/**
 * 商品分類表。[storeType] 為 null 代表通用分類。
 *
 * 兩種用途，以 [isPresetProduct] 區分：
 *  - `isPresetProduct = false`：商品分類（咖啡、茶飲…），可通用或專屬某超商，卡片以 categoryId 指向它。
 *  - `isPresetProduct = true`：超商頁「尚無卡片」區的商品項目（例如「商品卡 50 元」），
 *    只是一種分類捷徑，**不計入未使用卡片總數**。
 *
 * 分類與面額皆由資料庫驅動，不寫死在畫面程式碼中。
 */
@Entity(
    tableName = "product_categories",
    indices = [Index("storeType"), Index("sortOrder")],
)
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storeType: String?,
    val categoryName: String,
    val denomination: Int?,
    val productSpecification: String?,
    val isDefault: Boolean,
    val isPresetProduct: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
)
