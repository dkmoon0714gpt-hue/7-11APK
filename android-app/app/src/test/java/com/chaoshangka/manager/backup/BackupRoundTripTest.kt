package com.chaoshangka.manager.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.local.DatabaseSeeder
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.data.repository.GiftCardRepository
import com.chaoshangka.manager.data.repository.PhotoStore
import com.chaoshangka.manager.data.repository.SettingsRepository
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardDraft
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.StoreType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 備份與還原測試。
 *
 * 涵蓋：匯出後再匯入到新的資料庫，卡片、條碼、使用狀態與照片是否完整；
 * 以及匯入毀損檔案時不得破壞原有資料。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneOffset.ofHours(8))
    private val today = LocalDate.of(2026, 10, 9)

    private lateinit var context: Context
    private lateinit var photoStore: PhotoStore
    private lateinit var sourceDatabase: AppDatabase
    private lateinit var sourceCards: GiftCardRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        photoStore = PhotoStore(context)
        sourceDatabase = AppDatabase.inMemory(context)
        sourceCards = GiftCardRepository(sourceDatabase, photoStore, clock)
    }

    @After
    fun tearDown() {
        sourceDatabase.close()
    }

    private fun backupFile(name: String): File = File(context.cacheDir, name)

    @Test
    fun `匯出後匯入到新資料庫 卡片條碼使用狀態與照片都完整`() = runTest {
        DatabaseSeeder.seedIfEmpty(sourceDatabase)
        val photo = photoStore.newPhotoFile()
        FileOutputStream(photo).use { it.write(byteArrayOf(0x01, 0x02, 0x03, 0x04)) }

        val cardId = sourceCards.saveNewCard(
            CardDraft(
                storeType = StoreType.FAMILYMART,
                productName = "FamilyMart 禮品提領卡",
                cardType = CardType.PICKUP_CARD,
                expiryDate = LocalDate.of(2029, 6, 30),
                startDate = LocalDate.of(2029, 6, 30),
                expiryNeedsReview = false,
                cardNumber = "E100025001788209",
                originalImagePath = photo.absolutePath,
                barcodes = listOf(
                    BarcodeDraft(
                        barcodeFormat = BarcodeFormatType.CODE_128,
                        rawValue = "0014835",
                        isPrimary = true,
                    ),
                ),
            ),
            today = today,
        )
        sourceCards.markUsed(cardId)

        val sourceCategories = CategoryRepository(sourceDatabase)
        val sourceSettings = SettingsRepository(context)
        val exporter = BackupManager(
            context = context,
            db = sourceDatabase,
            cardRepository = sourceCards,
            categoryRepository = sourceCategories,
            photoStore = photoStore,
            settingsRepository = sourceSettings,
            clock = clock,
        )

        val file = backupFile("round_trip.zip")
        val manifest = exporter.exportTo(Uri.fromFile(file), appVersion = "1.0.0")

        assertEquals(1, manifest.cardCount)
        assertEquals(1, manifest.barcodeCount)
        assertEquals(1, manifest.photoCount)
        assertTrue(file.exists())

        // ---- 匯入到另一個資料庫 ----
        val targetDatabase = AppDatabase.inMemory(context)
        val targetCards = GiftCardRepository(targetDatabase, photoStore, clock)
        val importer = BackupManager(
            context = context,
            db = targetDatabase,
            cardRepository = targetCards,
            categoryRepository = CategoryRepository(targetDatabase),
            photoStore = photoStore,
            settingsRepository = SettingsRepository(context),
            clock = clock,
        )

        val inspection = importer.inspect(Uri.fromFile(file))
        assertTrue(inspection.isUsable)
        assertEquals(1, inspection.cardCount)

        val report = importer.restoreFrom(Uri.fromFile(file), skipDuplicates = true)
        assertEquals(1, report.cardsImported)
        assertEquals(1, report.barcodesImported)
        assertEquals(1, report.photosRestored)

        val restored = targetCards.observeAllCards().first().single()
        assertEquals("FamilyMart 禮品提領卡", restored.productName)
        assertEquals(StoreType.FAMILYMART, restored.storeType)
        assertEquals(CardStatus.USED, restored.cardStatus)
        assertNotNull(restored.usedAt)
        assertEquals("E100025001788209", restored.cardNumber)
        // 條碼前導零在備份與還原後仍然保留
        assertEquals("0014835", restored.barcodes.single().rawValue)
        assertTrue(restored.barcodes.single().isPrimary)
        // 照片確實被還原成本機檔案
        assertTrue(photoStore.exists(restored.originalImagePath))

        targetDatabase.close()
    }

    @Test
    fun `重複的條碼可以在匯入時略過`() = runTest {
        val draft = CardDraft(
            storeType = StoreType.SEVEN_ELEVEN,
            productName = "商品卡 50 元",
            cardType = CardType.VALUE_CARD,
            expiryNeedsReview = false,
            barcodes = listOf(BarcodeDraft(BarcodeFormatType.CODE_128, "0014835", isPrimary = true)),
        )
        sourceCards.saveNewCard(draft, today = today)
        val sourceCategories = CategoryRepository(sourceDatabase)
        val settings = SettingsRepository(context)
        val exporter = BackupManager(context, sourceDatabase, sourceCards, sourceCategories, photoStore, settings, clock)
        val file = backupFile("duplicate.zip")
        exporter.exportTo(Uri.fromFile(file), appVersion = "1.0.0")

        // 目標資料庫先放入相同條碼的卡片
        val targetDatabase = AppDatabase.inMemory(context)
        val targetCards = GiftCardRepository(targetDatabase, photoStore, clock)
        targetCards.saveNewCard(draft, today = today)
        val importer = BackupManager(
            context, targetDatabase, targetCards, CategoryRepository(targetDatabase), photoStore, settings, clock,
        )

        val skipped = importer.restoreFrom(Uri.fromFile(file), skipDuplicates = true)
        assertEquals(0, skipped.cardsImported)
        assertEquals(1, skipped.duplicateCardsSkipped)

        val imported = importer.restoreFrom(Uri.fromFile(file), skipDuplicates = false)
        assertEquals(1, imported.cardsImported)
        assertEquals(2, targetCards.observeAllCards().first().size)

        targetDatabase.close()
    }

    @Test
    fun `毀損的備份檔不會破壞原有資料`() = runTest {
        val existingId = sourceCards.saveNewCard(
            CardDraft(
                storeType = StoreType.OKMART,
                productName = "OK超商商品卡 100 元",
                denomination = 100,
                expiryNeedsReview = false,
            ),
            today = today,
        )

        val broken = backupFile("broken.zip")
        broken.writeText("這不是一個 ZIP 檔")

        val manager = BackupManager(
            context,
            sourceDatabase,
            sourceCards,
            CategoryRepository(sourceDatabase),
            photoStore,
            SettingsRepository(context),
            clock,
        )

        val result = runCatching { manager.restoreFrom(Uri.fromFile(broken), skipDuplicates = true) }
        assertTrue(result.isFailure)

        // 原有資料完全不受影響
        val cards = sourceCards.observeAllCards().first()
        assertEquals(1, cards.size)
        assertEquals(existingId, cards.single().id)
        assertEquals("OK超商商品卡 100 元", cards.single().productName)
    }
}
