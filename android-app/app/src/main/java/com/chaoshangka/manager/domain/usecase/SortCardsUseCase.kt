package com.chaoshangka.manager.domain.usecase

import com.chaoshangka.manager.domain.ExpiryEvaluator
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import java.time.LocalDate

/**
 * 排序規則（未使用卡片）：
 *  1. 已過期卡片排在最前面
 *  2. 今天到期 → 即將到期 → 尚未到期，依到期日由近到遠
 *  3. 期限待確認的卡片獨立排序，不會被誤認為無期限
 *  4. 無有效期限的卡片排在最後
 *  5. 同一天到期者依新增時間排序
 */
object SortCardsUseCase {

    fun rank(status: ExpiryStatus): Int = when (status) {
        ExpiryStatus.EXPIRED -> 0
        ExpiryStatus.DUE_TODAY -> 1
        ExpiryStatus.EXPIRING_SOON -> 2
        ExpiryStatus.VALID -> 3
        ExpiryStatus.NEEDS_REVIEW -> 4
        ExpiryStatus.NO_EXPIRY -> 5
    }

    fun unusedOrdered(cards: List<GiftCard>, today: LocalDate, warningDays: Int): List<GiftCard> =
        cards.sortedWith(
            compareBy(
                { rank(ExpiryEvaluator.liveStatus(it, today, warningDays)) },
                { it.expiryDate ?: LocalDate.MAX },
                { it.createdAt },
            ),
        )

    /** 已使用清單預設依使用時間由新到舊。 */
    fun usedNewestFirst(cards: List<GiftCard>): List<GiftCard> =
        cards.sortedWith(
            compareByDescending<GiftCard> { it.usedAt ?: 0L }
                .thenByDescending { it.createdAt },
        )
}
