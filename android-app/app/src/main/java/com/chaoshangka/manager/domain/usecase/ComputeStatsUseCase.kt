package com.chaoshangka.manager.domain.usecase

import com.chaoshangka.manager.domain.ExpiryEvaluator
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StatsSummary
import com.chaoshangka.manager.domain.model.StoreSummary
import com.chaoshangka.manager.domain.model.StoreType
import java.time.LocalDate

/**
 * 首頁與各分類的統計。所有數字都由資料庫資料即時計算，不使用假資料。
 * 「即將到期」包含今天到期與提醒門檻內到期的卡片。
 */
object ComputeStatsUseCase {

    fun summary(cards: List<GiftCard>, today: LocalDate, warningDays: Int): StatsSummary {
        val active = cards.filter { !it.isArchived }
        val unused = active.filter { it.cardStatus == CardStatus.UNUSED }
        val used = active.count { it.cardStatus == CardStatus.USED }
        var expired = 0
        var expiringSoon = 0
        unused.forEach { card ->
            when (ExpiryEvaluator.liveStatus(card, today, warningDays)) {
                ExpiryStatus.EXPIRED -> expired++
                ExpiryStatus.DUE_TODAY, ExpiryStatus.EXPIRING_SOON -> expiringSoon++
                else -> Unit
            }
        }
        return StatsSummary(
            unused = unused.size,
            used = used,
            expired = expired,
            expiringSoon = expiringSoon,
        )
    }

    fun storeSummaries(cards: List<GiftCard>, today: LocalDate, warningDays: Int): List<StoreSummary> =
        StoreType.mainStores.map { store ->
            val storeCards = cards.filter { !it.isArchived && it.storeType == store }
            val unusedCards = storeCards.filter { it.cardStatus == CardStatus.UNUSED }
            StoreSummary(
                storeType = store,
                unused = unusedCards.size,
                used = storeCards.count { it.cardStatus == CardStatus.USED },
                expired = unusedCards.count {
                    ExpiryEvaluator.liveStatus(it, today, warningDays) == ExpiryStatus.EXPIRED
                },
            )
        }
}
