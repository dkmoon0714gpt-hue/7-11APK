package com.chaoshangka.manager.domain.model

/** 首頁統計（即時由資料庫計算，非寫死資料）。 */
data class StatsSummary(
    val unused: Int = 0,
    val used: Int = 0,
    val expired: Int = 0,
    val expiringSoon: Int = 0,
)

/** 單一超商的統計。 */
data class StoreSummary(
    val storeType: StoreType,
    val unused: Int = 0,
    val used: Int = 0,
    val expired: Int = 0,
)

/**
 * 超商頁／分類頁中的商品群組。
 *
 * [isHeld] 為 false 時代表「尚無卡片」的捷徑項目，不可計入未使用卡片總數。
 */
data class ProductGroup(
    val key: String,
    val categoryName: String,
    val categoryId: Long?,
    val productName: String,
    val denomination: Int?,
    val specification: String?,
    val unusedCount: Int,
    val usedCount: Int,
    val hasExpired: Boolean,
    val cards: List<GiftCard> = emptyList(),
) {
    val isHeld: Boolean
        get() = unusedCount > 0
}

/** 商品群組排序方式。 */
enum class GroupSortOrder(val displayName: String) {
    EXPIRY("依到期日"),
    NAME("依商品名稱"),
    DENOMINATION("依面額"),
    UNUSED_COUNT("依未使用數量"),
}
