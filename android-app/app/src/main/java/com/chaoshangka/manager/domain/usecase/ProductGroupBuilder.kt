package com.chaoshangka.manager.domain.usecase

import com.chaoshangka.manager.domain.ExpiryEvaluator
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.GroupSortOrder
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.ProductGroup
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 把某個超商的卡片分成「已有卡片」與「尚無卡片」兩區。
 *
 *  - 已有卡片：目前持有（未使用）的卡片，依有效期限排序；已使用的卡片只計數不佔卡片位。
 *  - 尚無卡片：分類選單中存在、但目前沒有任何未使用卡片的商品項目。
 *    這些只是分類捷徑，**不計入未使用卡片總數**。
 */
object ProductGroupBuilder {

    fun build(
        storeCards: List<GiftCard>,
        presetProducts: List<ProductCategory>,
        categories: List<ProductCategory>,
        today: LocalDate,
        warningDays: Int,
        sortOrder: GroupSortOrder = GroupSortOrder.EXPIRY,
    ): List<ProductGroup> {
        val activeCards = storeCards.filter { !it.isArchived }
        val categoryNames = categories.associate { it.id to it.categoryName }

        val buckets = LinkedHashMap<String, MutableList<GiftCard>>()
        activeCards.forEach { card ->
            buckets.getOrPut(groupKey(card)) { mutableListOf() } += card
        }

        val held = buckets.values.map { cards -> toGroup(cards, categoryNames, today, warningDays) }
        val sortedHeld = sortHeld(held, sortOrder)

        val notHeld = presetProducts
            .filter { preset -> held.none { matches(preset, it) } }
            .map { preset ->
                ProductGroup(
                    key = "preset:${preset.id}",
                    categoryName = preset.categoryName,
                    categoryId = preset.id,
                    productName = preset.categoryName,
                    denomination = preset.denomination,
                    specification = preset.productSpecification,
                    unusedCount = 0,
                    usedCount = 0,
                    hasExpired = false,
                    cards = emptyList(),
                )
            }

        return sortedHeld + notHeld
    }

    private fun groupKey(card: GiftCard): String {
        val name = normalizeName(card.productName)
        if (name.isNotEmpty()) {
            return "text:$name|${card.denomination ?: -1}|${normalizeName(card.productSpecification)}"
        }
        return card.categoryId?.let { "cat:$it" } ?: "unknown:${card.id}"
    }

    private fun toGroup(
        cards: List<GiftCard>,
        categoryNames: Map<Long, String>,
        today: LocalDate,
        warningDays: Int,
    ): ProductGroup {
        val sample = cards.first()
        val unusedCards = cards.filter { it.cardStatus == CardStatus.UNUSED }
        val categoryId = cards.map { it.categoryId }.distinct().singleOrNull()
        val categoryName = categoryId?.let { categoryNames[it] }
            ?: categoryNames[sample.categoryId]
            ?: "未分類"
        val ordered = SortCardsUseCase.unusedOrdered(unusedCards, today, warningDays)
        return ProductGroup(
            key = "held:${groupKey(sample)}",
            categoryName = categoryName,
            categoryId = categoryId,
            productName = sample.productName.ifBlank { categoryName },
            denomination = sample.denomination,
            specification = sample.productSpecification.ifBlank { null },
            unusedCount = unusedCards.size,
            usedCount = cards.count { it.cardStatus == CardStatus.USED },
            hasExpired = unusedCards.any {
                ExpiryEvaluator.liveStatus(it, today, warningDays) == ExpiryStatus.EXPIRED
            },
            cards = ordered,
        )
    }

    private fun sortHeld(groups: List<ProductGroup>, sortOrder: GroupSortOrder): List<ProductGroup> = when (sortOrder) {
        GroupSortOrder.EXPIRY -> groups.sortedWith(
            compareBy(
                { it.cards.firstOrNull()?.expiryDate ?: LocalDate.MAX },
                { it.productName },
            ),
        )

        GroupSortOrder.NAME -> groups.sortedBy { it.productName }
        GroupSortOrder.DENOMINATION -> groups.sortedWith(
            compareBy({ it.denomination ?: Int.MAX_VALUE }, { it.productName }),
        )

        GroupSortOrder.UNUSED_COUNT -> groups.sortedWith(
            compareByDescending<ProductGroup> { it.unusedCount }.thenBy { it.productName },
        )
    }

    /** 「尚無卡片」項目是否已被既有群組涵蓋。 */
    private fun matches(preset: ProductCategory, group: ProductGroup): Boolean {
        val presetName = normalizeName(preset.categoryName)
        val groupName = normalizeName(group.productName)
        if (presetName == groupName) return true
        if (preset.denomination != null && preset.denomination == group.denomination) {
            val bothValueCards = presetName.contains("商品卡") && groupName.contains("商品卡")
            if (bothValueCards) return true
        }
        return false
    }

    fun normalizeName(value: String): String =
        value.replace(Regex("\\s+"), "")
            .replace("（", "(")
            .replace("）", ")")

    /** 期限剩餘天數（除錯與顯示用）。 */
    fun daysLeft(end: LocalDate?, today: LocalDate): Long? =
        end?.let { ChronoUnit.DAYS.between(today, it) }
}
