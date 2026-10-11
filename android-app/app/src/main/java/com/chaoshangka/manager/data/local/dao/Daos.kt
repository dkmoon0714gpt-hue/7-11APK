package com.chaoshangka.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chaoshangka.manager.data.local.BarcodeEntity
import com.chaoshangka.manager.data.local.GiftCardEntity
import com.chaoshangka.manager.data.local.ProductCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftCardDao {

    @Insert
    suspend fun insert(card: GiftCardEntity): Long

    @Update
    suspend fun update(card: GiftCardEntity)

    @Query("DELETE FROM gift_cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM gift_cards WHERE id = :id")
    suspend fun getById(id: Long): GiftCardEntity?

    @Query("SELECT * FROM gift_cards WHERE id = :id")
    fun observeById(id: Long): Flow<GiftCardEntity?>

    @Query("SELECT * FROM gift_cards WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GiftCardEntity>>

    @Query("SELECT * FROM gift_cards ORDER BY createdAt DESC")
    fun observeAllIncludingArchived(): Flow<List<GiftCardEntity>>

    @Query(
        """
        SELECT * FROM gift_cards
        WHERE isArchived = 0 AND (:store IS NULL OR storeType = :store)
        ORDER BY createdAt DESC
        """,
    )
    fun observeByStore(store: String?): Flow<List<GiftCardEntity>>

    @Query("SELECT COUNT(*) FROM gift_cards")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM gift_cards WHERE cardStatus = :status AND isArchived = 0")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query(
        """
        SELECT * FROM gift_cards
        WHERE isArchived = 0 AND cardStatus = :status AND (:store IS NULL OR storeType = :store)
        ORDER BY usedAt DESC, createdAt DESC
        """,
    )
    fun observeByStatusAndStore(status: String, store: String?): Flow<List<GiftCardEntity>>

    /**
     * 同一超商內是否已有相同條碼內容的未使用卡片（重複條碼警告用）。
     */
    @Query(
        """
        SELECT c.* FROM gift_cards c
        INNER JOIN barcodes b ON b.giftCardId = c.id
        WHERE c.storeType = :storeType
          AND c.cardStatus = 'UNUSED'
          AND c.isArchived = 0
          AND b.rawValue = :rawValue
        """,
    )
    suspend fun findUnusedCardsUsingBarcode(storeType: String, rawValue: String): List<GiftCardEntity>

    @Query("UPDATE gift_cards SET cardStatus = :status, usedAt = :usedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUsage(id: Long, status: String, usedAt: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM gift_cards WHERE categoryId = :categoryId AND isArchived = 0")
    suspend fun countCardsUsingCategory(categoryId: Long): Int
}

@Dao
interface BarcodeDao {

    @Insert
    suspend fun insert(barcode: BarcodeEntity): Long

    @Insert
    suspend fun insertAll(barcodes: List<BarcodeEntity>): List<Long>

    @Update
    suspend fun update(barcode: BarcodeEntity)

    @Query("SELECT * FROM barcodes WHERE id = :id")
    suspend fun getById(id: Long): BarcodeEntity?

    @Query("SELECT * FROM barcodes WHERE giftCardId = :cardId ORDER BY isPrimary DESC, id ASC")
    suspend fun getForCard(cardId: Long): List<BarcodeEntity>

    @Query("SELECT * FROM barcodes WHERE giftCardId = :cardId ORDER BY isPrimary DESC, id ASC")
    fun observeByCard(cardId: Long): Flow<List<BarcodeEntity>>

    @Query("SELECT * FROM barcodes ORDER BY id ASC")
    fun observeAll(): Flow<List<BarcodeEntity>>

    @Query("SELECT * FROM barcodes ORDER BY id ASC")
    suspend fun getAll(): List<BarcodeEntity>

    @Query("DELETE FROM barcodes WHERE giftCardId = :cardId")
    suspend fun deleteByCard(cardId: Long)

    @Query("UPDATE barcodes SET isPrimary = 0 WHERE giftCardId = :cardId")
    suspend fun clearPrimary(cardId: Long)

    @Query("UPDATE barcodes SET isPrimary = 1 WHERE id = :barcodeId")
    suspend fun setPrimary(barcodeId: Long)

    /** 將條碼內容複製回主表用（匯入備份時需要原始 giftCardId）。 */
    @Query("UPDATE barcodes SET giftCardId = :cardId WHERE id = :barcodeId")
    suspend fun reassignToCard(barcodeId: Long, cardId: Long)
}

@Dao
interface ProductCategoryDao {

    @Insert
    suspend fun insert(category: ProductCategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<ProductCategoryEntity>): List<Long>

    @Update
    suspend fun update(category: ProductCategoryEntity)

    @Query("DELETE FROM product_categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM product_categories WHERE id = :id")
    suspend fun getById(id: Long): ProductCategoryEntity?

    @Query("SELECT * FROM product_categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<ProductCategoryEntity>

    @Query("SELECT * FROM product_categories WHERE isActive = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<ProductCategoryEntity>>

    @Query(
        """
        SELECT * FROM product_categories
        WHERE isActive = 1 AND (storeType IS NULL OR storeType = :store)
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    suspend fun getForStore(store: String): List<ProductCategoryEntity>

    @Query("SELECT COUNT(*) FROM product_categories")
    suspend fun count(): Int

    @Query("UPDATE gift_cards SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun moveCardsToCategory(fromCategoryId: Long, toCategoryId: Long)

    @Query("SELECT MAX(sortOrder) FROM product_categories")
    suspend fun maxSortOrder(): Int?
}
