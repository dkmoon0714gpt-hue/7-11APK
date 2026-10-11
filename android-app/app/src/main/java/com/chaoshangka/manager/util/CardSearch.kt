package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.GiftCard

/**
 * 搜尋比對：商品名稱、面額、條碼內容、卡號、備註、超商名稱。
 * 條碼以字串比對，因此輸入前導零也能搜尋得到。
 */
object CardSearch {

    fun matches(card: GiftCard, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return card.productName.contains(q, ignoreCase = true) ||
            card.productSpecification.contains(q, ignoreCase = true) ||
            card.notes.contains(q, ignoreCase = true) ||
            card.cardNumber?.contains(q, ignoreCase = true) == true ||
            (card.denomination != null && card.denomination.toString().contains(q)) ||
            card.storeType.displayName.contains(q, ignoreCase = true) ||
            card.barcodes.any { it.rawValue.contains(q, ignoreCase = true) }
    }

    fun filter(
        cards: List<GiftCard>,
        query: String = "",
        store: com.chaoshangka.manager.domain.model.StoreType? = null,
        status: CardStatus? = null,
    ): List<GiftCard> = cards.filter { card ->
        !card.isArchived &&
            (store == null || card.storeType == store) &&
            (status == null || card.cardStatus == status) &&
            matches(card, query)
    }
}
