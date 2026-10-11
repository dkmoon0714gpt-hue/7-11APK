package com.chaoshangka.manager.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.data.repository.GiftCardRepository
import com.chaoshangka.manager.data.repository.PhotoStore
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardDraft
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.domain.model.StoreType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Repository 測試（Robolectric + 記憶體 Room 資料庫）。
 *
 * 涵蓋：新增四大超商卡片、使用／恢復未使用、刪除、重複條碼檢查、
 * 條碼前導零保留、主要條碼選擇、分類刪除保護。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftCardRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var photoStore: PhotoStore
    private lateinit var repository: GiftCardRepository
    private lateinit var categoryRepository: CategoryRepository

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneOffset.ofHours(8))

    private val today: LocalDate = LocalDate.of(2026, 10, 9)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.inMemory(context)
        photoStore = PhotoStore(context)
        repository = GiftCardRepository(db, photoStore, fixedClock)
        categoryRepository = CategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(
        store: StoreType,
        name: String,
        denomination: Int? = null,
        expiry: LocalDate? = null,
        barcodes: List<BarcodeDraft> = emptyList(),
        cardType: CardType = CardType.VALUE_CARD,
        cardNumber: String? = null,
    ) = CardDraft(
        storeType = store,
        productName = name,
        denomination = denomination,
        expiryDate = expiry,
        startDate = expiry,
        cardType = cardType,
        cardNumber = cardNumber,
        expiryNeedsReview = false,
        barcodes = barcodes,
    )

    @Test
    fun `新增全家商品卡`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.FAMILYMART,
                name = "FamilyMart 禮品提領卡",
                denomination = null,
                expiry = LocalDate.of(2029, 6, 30),
                cardNumber = "E100025001788209",
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.CODE_128, "0014835", isPrimary = true),
                ),
            ),
            today = today,
        )

        val card = repository.getCard(id)
        assertNotNull(card)
        assertEquals(StoreType.FAMILYMART, card!!.storeType)
        assertEquals("E100025001788209", card.cardNumber)
        assertEquals(CardStatus.UNUSED, card.cardStatus)
        assertEquals(1, card.barcodes.size)
    }

    @Test
    fun `新增 7-ELEVEN 50 元商品卡並保留條碼前導零`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.SEVEN_ELEVEN,
                name = "商品卡 50 元",
                denomination = 50,
                expiry = LocalDate.of(2026, 12, 31),
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.CODE_128, "0014835", isPrimary = true),
                ),
            ),
            today = today,
        )

        val card = repository.getCard(id)!!
        assertEquals(50, card.denomination)
        // 前導零必須保留（不可轉成整數）
        assertEquals("0014835", card.barcodes.single().rawValue)
    }

    @Test
    fun `新增萊爾富 QR Code 商品卡`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.HILIFE,
                name = "萊爾富商品卡",
                expiry = LocalDate.of(2027, 8, 26),
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.QR_CODE, "Q0050000645468", isPrimary = true),
                ),
            ),
            today = today,
        )

        val card = repository.getCard(id)!!
        assertEquals(BarcodeFormatType.QR_CODE, card.barcodes.single().barcodeFormat)
        assertEquals("Q0050000645468", card.barcodes.single().rawValue)
        assertTrue(card.barcodes.single().isPrimary)
    }

    @Test
    fun `新增 OKmart 卡片`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.OKMART,
                name = "OK超商商品卡 100 元",
                denomination = 100,
                expiry = LocalDate.of(2027, 12, 31),
            ),
            today = today,
        )
        assertEquals(StoreType.OKMART, repository.getCard(id)!!.storeType)
    }

    @Test
    fun `多組條碼時只會有一個主要條碼且優先選一維條碼`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.SEVEN_ELEVEN,
                name = "商品卡 50 元",
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.QR_CODE, "https://example.invalid/qr"),
                    BarcodeDraft(BarcodeFormatType.CODE_128, "730000204307646"),
                    BarcodeDraft(BarcodeFormatType.EAN_13, "4710088100029"),
                ),
            ),
            today = today,
        )

        val card = repository.getCard(id)!!
        assertEquals(3, card.barcodes.size)
        assertEquals(1, card.barcodes.count { it.isPrimary })
        assertEquals("730000204307646", card.barcodes.first { it.isPrimary }.rawValue)
    }

    @Test
    fun `可以手動指定主要條碼`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.FAMILYMART,
                name = "禮品提領卡",
                barcodes = listOf(
                    BarcodeDraft(BarcodeFormatType.CODE_128, "0014835", isPrimary = true),
                    BarcodeDraft(BarcodeFormatType.QR_CODE, "FAMILY-QR"),
                ),
            ),
            today = today,
        )
        val card = repository.getCard(id)!!
        val qr = card.barcodes.first { it.barcodeFormat == BarcodeFormatType.QR_CODE }

        repository.setPrimaryBarcode(id, qr.id)

        val updated = repository.getCard(id)!!
        assertEquals(1, updated.barcodes.count { it.isPrimary })
        assertEquals("FAMILY-QR", updated.primaryBarcode!!.rawValue)
    }

    @Test
    fun `標記使用後會記錄本機使用時間並移出未使用清單`() = runTest {
        val id = repository.saveNewCard(
            draft(store = StoreType.SEVEN_ELEVEN, name = "商品卡 50 元"),
            today = today,
        )

        repository.markUsed(id)

        val card = repository.getCard(id)!!
        assertEquals(CardStatus.USED, card.cardStatus)
        assertNotNull(card.usedAt)

        val unused = repository.observeCardsByStatus(CardStatus.UNUSED).first()
        val used = repository.observeCardsByStatus(CardStatus.USED).first()
        assertFalse(unused.any { it.id == id })
        assertTrue(used.any { it.id == id })
    }

    @Test
    fun `誤按後可恢復為未使用並清除使用時間`() = runTest {
        val id = repository.saveNewCard(
            draft(store = StoreType.FAMILYMART, name = "經典美式咖啡"),
            today = today,
        )
        repository.markUsed(id)
        repository.markUnused(id)

        val card = repository.getCard(id)!!
        assertEquals(CardStatus.UNUSED, card.cardStatus)
        assertNull(card.usedAt)
        // 原有資料保留
        assertEquals("經典美式咖啡", card.productName)
    }

    @Test
    fun `刪除卡片會同時刪除條碼`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.SEVEN_ELEVEN,
                name = "商品卡 50 元",
                barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835")),
            ),
            today = today,
        )
        assertEquals(1, db.barcodeDao().getAll().size)

        repository.deleteCard(id)

        assertNull(repository.getCard(id))
        assertTrue(db.barcodeDao().getAll().isEmpty())
    }

    @Test
    fun `同一超商相同條碼會回報重複`() = runTest {
        repository.saveNewCard(
            draft(
                store = StoreType.SEVEN_ELEVEN,
                name = "商品卡 50 元",
                barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835")),
            ),
            today = today,
        )

        val duplicates = repository.findDuplicateCards(StoreType.SEVEN_ELEVEN, listOf("0014835"))
        assertEquals(1, duplicates.size)

        val differentStore = repository.findDuplicateCards(StoreType.FAMILYMART, listOf("0014835"))
        assertTrue(differentStore.isEmpty())
    }

    @Test
    fun `重複條碼不會阻止使用者仍要新增`() = runTest {
        val first = repository.saveNewCard(
            draft(
                store = StoreType.FAMILYMART,
                name = "禮品提領卡 A",
                barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835")),
            ),
            today = today,
        )
        val second = repository.saveNewCard(
            draft(
                store = StoreType.FAMILYMART,
                name = "禮品提領卡 B",
                barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835")),
            ),
            today = today,
        )

        assertTrue(first != second)
        assertEquals(2, repository.observeAllCards().first().size)
    }

    @Test
    fun `刪除已被卡片使用的分類會被阻擋且卡片不會消失`() = runTest {
        val categoryId = categoryRepository.addCustomCategory("我的分類", StoreType.SEVEN_ELEVEN)
        repository.saveNewCard(
            draft(store = StoreType.SEVEN_ELEVEN, name = "商品卡 50 元").copy(categoryId = categoryId),
            today = today,
        )

        val result = categoryRepository.deleteCategory(categoryId)
        assertTrue(result is CategoryRepository.DeleteResult.Blocked)
        assertEquals(1, repository.observeAllCards().first().size)
        assertEquals(1, categoryRepository.cardsUsing(categoryId))
    }

    @Test
    fun `未使用的分類可以直接刪除`() = runTest {
        val categoryId = categoryRepository.addCustomCategory("空的分類", null)
        val result = categoryRepository.deleteCategory(categoryId)
        assertEquals(CategoryRepository.DeleteResult.Deleted, result)
    }

    @Test
    fun `移轉卡片後可刪除原分類且卡片留存`() = runTest {
        val from = categoryRepository.addCustomCategory("舊分類", null)
        val to = categoryRepository.addCustomCategory("新分類", null)
        repository.saveNewCard(
            draft(store = StoreType.SEVEN_ELEVEN, name = "商品卡 50 元").copy(categoryId = from),
            today = today,
        )

        categoryRepository.moveCardsAndDelete(from, to)

        val cards = repository.observeAllCards().first()
        assertEquals(1, cards.size)
        assertEquals(to, cards.single().categoryId)
        assertTrue(categoryRepository.all().none { it.id == from })
    }

    @Test
    fun `編輯卡片會更新欄位並整批取代條碼`() = runTest {
        val id = repository.saveNewCard(
            draft(
                store = StoreType.SEVEN_ELEVEN,
                name = "商品卡 50 元",
                barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835")),
            ),
            today = today,
        )
        val card = repository.getCard(id)!!

        repository.updateCard(
            card = card.copy(
                productName = "商品卡 100 元",
                denomination = 100,
                notes = "手動修正",
            ),
            barcodes = listOf(
                com.chaoshangka.manager.domain.model.Barcode(
                    barcodeFormat = BarcodeFormatType.CODE_128,
                    rawValue = "0014835",
                    isPrimary = true,
                    recognitionStatus = RecognitionStatus.MANUAL,
                ),
            ),
            today = today,
        )

        val updated = repository.getCard(id)!!
        assertEquals("商品卡 100 元", updated.productName)
        assertEquals(100, updated.denomination)
        assertEquals("手動修正", updated.notes)
        assertEquals(1, updated.barcodes.size)
        assertEquals("0014835", updated.barcodes.single().rawValue)
    }
}
