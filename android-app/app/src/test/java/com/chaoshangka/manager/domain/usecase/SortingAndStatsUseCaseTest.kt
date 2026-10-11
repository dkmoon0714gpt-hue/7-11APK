package com.chaoshangka.manager.domain.usecase

import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StoreType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 排序與統計測試：
 *  - 已過期卡片排最前面
 *  - 尚未過期者依到期日由近到遠
 *  - 無期限卡片排最後
 *  - 期限待確認卡片獨立（不可被誤認為無期限）
 *  - 同日到期依新增時間排序
 */
class SortingAndStatsUseCaseTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val warningDays = 7

    private fun card(
        id: Long,
        expiry: LocalDate?,
        createdAt: Long,
        status: CardStatus = CardStatus.UNUSED,
        expiryStatus: ExpiryStatus = ExpiryStatus.VALID,
        store: StoreType = StoreType.SEVEN_ELEVEN,
        usedAt: Long? = null,
        name: String = "卡片$id",
    ) = GiftCard(
        id = id,
        storeType = store,
        productName = name,
        expiryDate = expiry,
        startDate = expiry,
        expiryStatus = expiryStatus,
        cardStatus = status,
        createdAt = createdAt,
        updatedAt = createdAt,
        usedAt = usedAt,
    )

    @Test
    fun `已過期卡片排在最前面`() {
        val cards = listOf(
            card(1, today.plusDays(30), createdAt = 100),
            card(2, today.minusDays(1), createdAt = 200),
            card(3, today.plusDays(3), createdAt = 300),
        )
        val ordered = SortCardsUseCase.unusedOrdered(cards, today, warningDays)
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun `尚未過期者依到期日由近到遠`() {
        val cards = listOf(
            card(1, today.plusDays(60), createdAt = 100),
            card(2, today.plusDays(10), createdAt = 200),
            card(3, today.plusDays(20), createdAt = 300),
        )
        val ordered = SortCardsUseCase.unusedOrdered(cards, today, warningDays)
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun `無期限卡片排在最後`() {
        val cards = listOf(
            card(1, null, createdAt = 100, expiryStatus = ExpiryStatus.NO_EXPIRY),
            card(2, today.plusDays(5), createdAt = 200),
        )
        val ordered = SortCardsUseCase.unusedOrdered(cards, today, warningDays)
        assertEquals(1L, ordered.last().id)
    }

    @Test
    fun `期限待確認卡片獨立排序且排在無期限之前`() {
        val cards = listOf(
            card(1, null, createdAt = 100, expiryStatus = ExpiryStatus.NO_EXPIRY),
            card(2, null, createdAt = 200, expiryStatus = ExpiryStatus.NEEDS_REVIEW),
            card(3, today.plusDays(1), createdAt = 300),
        )
        val ordered = SortCardsUseCase.unusedOrdered(cards, today, warningDays)
        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.id })
    }

    @Test
    fun `同一天到期依新增時間排序`() {
        val expiry = today.plusDays(10)
        val cards = listOf(
            card(1, expiry, createdAt = 300),
            card(2, expiry, createdAt = 100),
            card(3, expiry, createdAt = 200),
        )
        val ordered = SortCardsUseCase.unusedOrdered(cards, today, warningDays)
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun `排序不會排除過期卡片`() {
        val cards = listOf(card(1, today.minusDays(30), createdAt = 100))
        assertEquals(1, SortCardsUseCase.unusedOrdered(cards, today, warningDays).size)
    }

    @Test
    fun `已使用清單依使用時間由新到舊`() {
        val cards = listOf(
            card(1, null, createdAt = 1, status = CardStatus.USED, usedAt = 1000),
            card(2, null, createdAt = 2, status = CardStatus.USED, usedAt = 3000),
            card(3, null, createdAt = 3, status = CardStatus.USED, usedAt = 2000),
        )
        assertEquals(listOf(2L, 3L, 1L), SortCardsUseCase.usedNewestFirst(cards).map { it.id })
    }

    @Test
    fun `統計分別計算未使用已使用已過期與即將到期`() {
        val cards = listOf(
            card(1, today.plusDays(3), createdAt = 1),
            card(2, today.minusDays(2), createdAt = 2),
            card(3, today.plusDays(100), createdAt = 3),
            card(4, null, createdAt = 4, status = CardStatus.USED, usedAt = 10),
            card(5, today, createdAt = 5),
        )
        val summary = ComputeStatsUseCase.summary(cards, today, warningDays)
        assertEquals(4, summary.unused)
        assertEquals(1, summary.used)
        assertEquals(1, summary.expired)
        assertEquals(2, summary.expiringSoon)
    }

    @Test
    fun `各超商統計各自計算`() {
        val cards = listOf(
            card(1, today.plusDays(3), createdAt = 1, store = StoreType.SEVEN_ELEVEN),
            card(2, today.minusDays(1), createdAt = 2, store = StoreType.FAMILYMART),
            card(3, null, createdAt = 3, status = CardStatus.USED, usedAt = 5, store = StoreType.HILIFE),
        )
        val summaries = ComputeStatsUseCase.storeSummaries(cards, today, warningDays).associateBy { it.storeType }
        assertEquals(1, summaries.getValue(StoreType.SEVEN_ELEVEN).unused)
        assertEquals(1, summaries.getValue(StoreType.FAMILYMART).expired)
        assertEquals(1, summaries.getValue(StoreType.HILIFE).used)
        assertEquals(0, summaries.getValue(StoreType.OKMART).unused)
    }
}
