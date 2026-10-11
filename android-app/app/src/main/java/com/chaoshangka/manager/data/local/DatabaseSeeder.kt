package com.chaoshangka.manager.data.local

import com.chaoshangka.manager.domain.model.StoreType

/**
 * 第一次啟動時寫入預設分類與各超商商品項目。
 * 已有資料時不會覆蓋（不影響使用者自訂分類）。
 */
object DatabaseSeeder {

    suspend fun seedIfEmpty(db: AppDatabase) {
        val dao = db.categoryDao()
        if (dao.count() > 0) return

        var order = 0
        val commonRows = PresetProducts.COMMON_CATEGORIES.map { name ->
            ProductCategoryEntity(
                storeType = null,
                categoryName = name,
                denomination = null,
                productSpecification = null,
                isDefault = true,
                isPresetProduct = false,
                isActive = true,
                sortOrder = order++,
            )
        }
        dao.insertAll(commonRows)

        var storeOrder = 100
        val storeRows = mutableListOf<ProductCategoryEntity>()
        StoreType.mainStores.forEach { store ->
            PresetProducts.itemsFor(store).forEach { item ->
                storeRows += ProductCategoryEntity(
                    storeType = store.key,
                    categoryName = item.productName,
                    denomination = item.denomination,
                    productSpecification = item.specification,
                    isDefault = true,
                    isPresetProduct = true,
                    isActive = true,
                    sortOrder = storeOrder++,
                )
            }
        }
        if (storeRows.isNotEmpty()) {
            dao.insertAll(storeRows)
        }
    }
}
