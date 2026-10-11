package com.chaoshangka.manager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chaoshangka.manager.data.local.dao.BarcodeDao
import com.chaoshangka.manager.data.local.dao.GiftCardDao
import com.chaoshangka.manager.data.local.dao.ProductCategoryDao

@Database(
    entities = [GiftCardEntity::class, BarcodeEntity::class, ProductCategoryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun giftCardDao(): GiftCardDao

    abstract fun barcodeDao(): BarcodeDao

    abstract fun categoryDao(): ProductCategoryDao

    companion object {
        const val DATABASE_NAME = "chaoshangka.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }

        /** 測試用：建立記憶體資料庫。 */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
