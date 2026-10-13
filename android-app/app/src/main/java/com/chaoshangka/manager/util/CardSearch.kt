package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.GiftCard

/**
 * 搜尋比對：商品名稱、商品分類、面額。
 */
object CardSearch {

    fun matches(
        card: GiftCard,
        query: String,
        categoryName: String? = null,
    ): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return card.productName.contains(q, ignoreCase = true) ||
            (categoryName != null && categoryName.contains(q, ignoreCase = true)) ||
            (card.denomination != null && card.denomination.toString().contains(q))
    }

    fun filter(
        cards: List<GiftCard>,
        query: String = "",
        store: com.chaoshangka.manager.domain.model.StoreType? = null,
        status: CardStatus? = null,
        categoryNames: Map<Long, String> = emptyMap(),
    ): List<GiftCard> = cards.filter { card ->
        !card.isArchived &&
            (store == null || card.storeType == store) &&
            (status == null || card.cardStatus == status) &&
            matches(card, query, card.categoryId?.let { categoryNames[it] })
    }
}
