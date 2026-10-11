package com.chaoshangka.manager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.repository.GiftCardRepository
import com.chaoshangka.manager.data.repository.PhotoStore
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardDraft
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.StoreType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 資料持久化測試：模擬「關閉 APP 再重新開啟」，
 * 確認資料存在磁碟上的資料庫，而不是只留在記憶體。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneOffset.ofHours(8))
    private val today = LocalDate.of(2026, 10, 9)
    private val databaseName = "persistence_test.db"

    @Test
    fun `關閉並重新開啟資料庫後卡片與條碼仍存在`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)

        val first = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val firstRepository = GiftCardRepository(first, PhotoStore(context), clock)
        val cardId = firstRepository.saveNewCard(
            CardDraft(
                storeType = StoreType.FAMILYMART,
                productName = "禮品提領卡",
                cardType = com.chaoshangka.manager.domain.model.CardType.PICKUP_CARD,
                expiryDate = LocalDate.of(2029, 6, 30),
                startDate = LocalDate.of(2029, 6, 30),
                expiryNeedsReview = false,
                cardNumber = "E100025001788209",
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.CODE_128, "0014835", isPrimary = true),
                ),
            ),
            today = today,
        )
        firstRepository.markUsed(cardId)
        first.close()

        val second = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val secondRepository = GiftCardRepository(second, PhotoStore(context), clock)
        val restored = secondRepository.getCard(cardId)

        assertNotNull(restored)
        assertEquals("禮品提領卡", restored!!.productName)
        assertEquals(CardStatus.USED, restored.cardStatus)
        assertNotNull(restored.usedAt)
        assertEquals("E100025001788209", restored.cardNumber)
        // 條碼以字串保存，前導零不會消失
        assertEquals("0014835", restored.barcodes.single().rawValue)
        second.close()

        context.deleteDatabase(databaseName)
    }
}
