package com.chaoshangka.manager.data.repository

import androidx.room.withTransaction
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.local.ProductCategoryEntity
import com.chaoshangka.manager.data.local.toDomain
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.StoreType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 分類管理。
 *
 * 刪除保護：分類若已被卡片使用，**不會**刪除分類、也**不會**刪除卡片，
 * 而是回傳 [DeleteResult.Blocked]，要求使用者先把卡片移到其他分類。
 */
class CategoryRepository(private val db: AppDatabase) {

    private val dao = db.categoryDao()

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data class Blocked(val cardCount: Int) : DeleteResult
    }

    fun observeActive(): Flow<List<ProductCategory>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<ProductCategory> = dao.getAll().map { it.toDomain() }

    /** 商品分類（不含「尚無卡片」用的預設商品項目）。 */
    suspend fun categoriesForStore(store: StoreType): List<ProductCategory> =
        dao.getForStore(store.key).map { it.toDomain() }.filter { !it.isPresetProduct }

    /** 供超商頁「尚無卡片」區使用的預設商品項目。 */
    suspend fun presetProductsForStore(store: StoreType): List<ProductCategory> =
        dao.getForStore(store.key).map { it.toDomain() }.filter { it.isPresetProduct }

    /** 可選擇的分類（通用 + 該超商）。 */
    suspend fun selectableCategories(store: StoreType): List<ProductCategory> =
        dao.getForStore(store.key).map { it.toDomain() }.filter { !it.isPresetProduct && it.isActive }

    suspend fun addCustomCategory(
        categoryName: String,
        storeType: StoreType? = null,
        denomination: Int? = null,
        specification: String? = null,
    ): Long {
        val nextOrder = (dao.maxSortOrder() ?: 0) + 1
        return dao.insert(
            ProductCategoryEntity(
                storeType = storeType?.key,
                categoryName = categoryName.trim(),
                denomination = denomination,
                productSpecification = specification,
                isDefault = false,
                isPresetProduct = false,
                isActive = true,
                sortOrder = nextOrder,
            ),
        )
    }

    suspend fun updateCategory(category: ProductCategory) {
        dao.update(
            ProductCategoryEntity(
                id = category.id,
                storeType = category.storeType?.key,
                categoryName = category.categoryName.trim(),
                denomination = category.denomination,
                productSpecification = category.productSpecification,
                isDefault = category.isDefault,
                isPresetProduct = category.isPresetProduct,
                isActive = category.isActive,
                sortOrder = category.sortOrder,
            ),
        )
    }

    /** 檢查分類是否被卡片使用。 */
    suspend fun cardsUsing(categoryId: Long): Int = db.giftCardDao().countCardsUsingCategory(categoryId)

    suspend fun deleteCategory(categoryId: Long): DeleteResult {
        val used = cardsUsing(categoryId)
        if (used > 0) return DeleteResult.Blocked(used)
        dao.deleteById(categoryId)
        return DeleteResult.Deleted
    }

    /** 把卡片移到其他分類後再刪除原分類；兩個動作在同一個交易內完成。 */
    suspend fun moveCardsAndDelete(fromCategoryId: Long, toCategoryId: Long): DeleteResult = db.withTransaction {
        val used = cardsUsing(fromCategoryId)
        if (used > 0) {
            dao.moveCardsToCategory(fromCategoryId, toCategoryId)
        }
        dao.deleteById(fromCategoryId)
        DeleteResult.Deleted
    }
}
