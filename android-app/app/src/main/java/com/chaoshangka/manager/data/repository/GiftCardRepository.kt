package com.chaoshangka.manager.data.repository

import androidx.room.withTransaction
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.local.BarcodeEntity
import com.chaoshangka.manager.data.local.GiftCardEntity
import com.chaoshangka.manager.data.local.toDomain
import com.chaoshangka.manager.data.local.toEntity
import com.chaoshangka.manager.domain.model.Barcode
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.CardDraft
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.util.DateParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * 卡片資料的唯一存取入口。
 *
 * 一致性規則：
 *  - 卡片與其條碼的新增／更新／刪除都在同一個資料庫交易內完成，不會出現部分成功。
 *  - 卡片狀態與使用時間一起寫入：[CardStatus.USED] 必定有 usedAt，恢復未使用時清空。
 *  - 刪除卡片時，條碼由外鍵 CASCADE 刪除，照片檔案另行刪除。
 */
class GiftCardRepository(
    private val db: AppDatabase,
    private val photoStore: PhotoStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val cardDao = db.giftCardDao()
    private val barcodeDao = db.barcodeDao()

    private fun attach(cards: List<GiftCardEntity>, barcodes: List<BarcodeEntity>): List<GiftCard> {
        val grouped = barcodes.groupBy { it.giftCardId }
        return cards.map { entity ->
            entity.toDomain((grouped[entity.id] ?: emptyList()).map { it.toDomain() })
        }
    }

    /** 所有卡片（含已封存），供統計使用。 */
    fun observeAllCards(): Flow<List<GiftCard>> =
        combine(cardDao.observeAllIncludingArchived(), barcodeDao.observeAll()) { cards, barcodes ->
            attach(cards, barcodes)
        }

    /** 未封存卡片，可依超商篩選（null = 全部）。 */
    fun observeCards(store: StoreType? = null): Flow<List<GiftCard>> =
        combine(cardDao.observeByStore(store?.key), barcodeDao.observeAll()) { cards, barcodes ->
            attach(cards, barcodes)
        }

    fun observeCardsByStatus(status: CardStatus, store: StoreType? = null): Flow<List<GiftCard>> =
        combine(
            cardDao.observeByStatusAndStore(status.key, store?.key),
            barcodeDao.observeAll(),
        ) { cards, barcodes -> attach(cards, barcodes) }

    suspend fun getCard(id: Long): GiftCard? {
        val entity = cardDao.getById(id) ?: return null
        return entity.toDomain(barcodeDao.getForCard(id).map { it.toDomain() })
    }

    /** 單張卡片的即時資料（詳細頁用；使用狀態改變會立即反映）。 */
    fun observeCard(id: Long): Flow<GiftCard?> =
        combine(cardDao.observeById(id), barcodeDao.observeByCard(id)) { entity, barcodes ->
            entity?.toDomain(barcodes.map { it.toDomain() })
        }

    suspend fun saveNewCard(
        draft: CardDraft,
        today: LocalDate = LocalDate.now(clock),
        warningDays: Int = 7,
    ): Long = db.withTransaction {
        val now = clock.millis()
        val status = DateParser.evaluate(
            start = draft.startDate,
            end = draft.expiryDate,
            needsReview = draft.expiryNeedsReview,
            today = today,
            warningDays = warningDays,
        )
        val entity = GiftCard(
            storeType = draft.storeType,
            productName = draft.productName.trim(),
            categoryId = draft.categoryId,
            denomination = draft.denomination,
            productSpecification = draft.productSpecification.trim(),
            quantity = draft.quantity.coerceAtLeast(1),
            cardType = draft.cardType,
            startDate = draft.startDate,
            expiryDate = draft.expiryDate,
            expiryStatus = status,
            cardStatus = CardStatus.UNUSED,
            createdAt = now,
            updatedAt = now,
            usedAt = null,
            originalImagePath = draft.originalImagePath,
            notes = draft.notes.trim(),
            isArchived = false,
            cardNumber = draft.cardNumber?.trim()?.takeIf { it.isNotEmpty() },
            barcodes = emptyList(),
        ).toEntity()

        val cardId = cardDao.insert(entity)
        val rows = buildBarcodeRows(draft.barcodes, cardId, now)
        if (rows.isNotEmpty()) barcodeDao.insertAll(rows)
        cardId
    }

    suspend fun updateCard(
        card: GiftCard,
        barcodes: List<Barcode> = card.barcodes,
        today: LocalDate = LocalDate.now(clock),
        warningDays: Int = 7,
    ) = db.withTransaction {
        val now = clock.millis()
        val status = DateParser.evaluate(
            start = card.startDate,
            end = card.expiryDate,
            needsReview = card.expiryStatus == ExpiryStatus.NEEDS_REVIEW,
            today = today,
            warningDays = warningDays,
        )
        val existing = cardDao.getById(card.id)
            ?: throw IllegalStateException("卡片不存在，無法更新：${card.id}")
        cardDao.update(
            existing.copy(
                storeType = card.storeType.key,
                productName = card.productName.trim(),
                categoryId = card.categoryId,
                denomination = card.denomination,
                productSpecification = card.productSpecification.trim(),
                quantity = card.quantity.coerceAtLeast(1),
                cardType = card.cardType.key,
                startDate = card.startDate?.toEpochDay(),
                expiryDate = card.expiryDate?.toEpochDay(),
                expiryStatus = status.key,
                originalImagePath = card.originalImagePath,
                notes = card.notes.trim(),
                isArchived = card.isArchived,
                cardNumber = card.cardNumber?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            ),
        )

        // 條碼以整批取代的方式更新，確保不會殘留舊資料。
        barcodeDao.deleteByCard(card.id)
        val drafts = barcodes.map {
            BarcodeDraft(
                barcodeFormat = it.barcodeFormat,
                rawValue = it.rawValue,
                recognitionStatus = it.recognitionStatus,
                barcodeImagePath = it.barcodeImagePath,
                isPrimary = it.isPrimary,
            )
        }
        val rows = buildBarcodeRows(drafts, card.id, now)
        if (rows.isNotEmpty()) barcodeDao.insertAll(rows)
    }

    suspend fun setPrimaryBarcode(cardId: Long, barcodeId: Long) = db.withTransaction {
        barcodeDao.clearPrimary(cardId)
        barcodeDao.setPrimary(barcodeId)
    }

    /** 使用者按下「使用」：寫入當下本機時間。 */
    suspend fun markUsed(cardId: Long, atMillis: Long = clock.millis()) =
        cardDao.updateUsage(cardId, CardStatus.USED.key, atMillis, clock.millis())

    /** 誤按後恢復為未使用：清除使用時間，保留原有卡片資料。 */
    suspend fun markUnused(cardId: Long, atMillis: Long = clock.millis()) =
        cardDao.updateUsage(cardId, CardStatus.UNUSED.key, null, atMillis)

    suspend fun deleteCard(cardId: Long) = db.withTransaction {
        val entity = cardDao.getById(cardId)
        val barcodes = barcodeDao.getForCard(cardId)
        cardDao.deleteById(cardId)
        entity?.originalImagePath?.let { photoStore.delete(it) }
        barcodes.forEach { it.barcodeImagePath?.let { path -> photoStore.delete(path) } }
    }

    /**
     * 重複條碼檢查：同一超商內是否已有相同條碼內容的未使用卡片。
     * 只作為警告，不阻止使用者新增（部分卡片確實可能有多個合法識別碼）。
     */
    suspend fun findDuplicateCards(storeType: StoreType, rawValues: List<String>): List<GiftCard> {
        val result = LinkedHashMap<Long, GiftCard>()
        rawValues.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { value ->
            cardDao.findUnusedCardsUsingBarcode(storeType.key, value).forEach { entity ->
                if (!result.containsKey(entity.id)) {
                    result[entity.id] = entity.toDomain(barcodeDao.getForCard(entity.id).map { it.toDomain() })
                }
            }
        }
        return result.values.toList()
    }

    suspend fun replacePhoto(cardId: Long, newPath: String?) = db.withTransaction {
        val entity = cardDao.getById(cardId) ?: return@withTransaction
        val old = entity.originalImagePath
        cardDao.update(entity.copy(originalImagePath = newPath, updatedAt = clock.millis()))
        if (old != null && old != newPath) photoStore.delete(old)
    }

    /** 備份用：完整快照（含條碼與照片路徑）。 */
    suspend fun snapshot(): List<GiftCard> = attach(
        cardDao.observeAllIncludingArchived().first(),
        barcodeDao.getAll(),
    )

    private fun buildBarcodeRows(drafts: List<BarcodeDraft>, cardId: Long, now: Long): List<BarcodeEntity> {
        val cleaned = drafts
            .map { it.copy(rawValue = it.rawValue.trim()) }
            .filter { it.rawValue.isNotEmpty() }
            .distinctBy { it.rawValue }
        if (cleaned.isEmpty()) return emptyList()

        val explicitPrimary = cleaned.indexOfFirst { it.isPrimary }
        val primaryIndex = when {
            explicitPrimary >= 0 -> explicitPrimary
            else -> cleaned.indexOfFirst { !it.barcodeFormat.isTwoDimensional }.takeIf { it >= 0 } ?: 0
        }
        return cleaned.mapIndexed { index, draft ->
            BarcodeEntity(
                giftCardId = cardId,
                barcodeFormat = draft.barcodeFormat.key,
                rawValue = draft.rawValue,
                barcodeImagePath = draft.barcodeImagePath,
                isPrimary = index == primaryIndex,
                recognitionStatus = draft.recognitionStatus.key,
                createdAt = now,
            )
        }
    }
}
