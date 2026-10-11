package com.chaoshangka.manager.di

import android.content.Context
import android.content.pm.PackageManager
import com.chaoshangka.manager.backup.BackupManager
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.local.DatabaseSeeder
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.data.repository.GiftCardRepository
import com.chaoshangka.manager.data.repository.PhotoStore
import com.chaoshangka.manager.data.repository.SettingsRepository
import com.chaoshangka.manager.ocr.BarcodeEngine
import com.chaoshangka.manager.ocr.TextRecognitionEngine
import java.time.Clock

/**
 * 手動依賴注入容器。
 *
 * 刻意不使用程式碼產生型的 DI 框架，讓專案在任何 Android Studio / Gradle 版本下都能直接建置。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()

    val database: AppDatabase = AppDatabase.get(appContext)

    val photoStore: PhotoStore = PhotoStore(appContext)

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val cardRepository: GiftCardRepository = GiftCardRepository(database, photoStore, clock)

    val categoryRepository: CategoryRepository = CategoryRepository(database)

    val backupManager: BackupManager = BackupManager(
        context = appContext,
        db = database,
        cardRepository = cardRepository,
        categoryRepository = categoryRepository,
        photoStore = photoStore,
        settingsRepository = settingsRepository,
        clock = clock,
    )

    /** ML Kit 內嵌模型引擎（離線，首次使用不需下載模型）。 */
    val textRecognitionEngine: TextRecognitionEngine by lazy { TextRecognitionEngine() }

    val barcodeEngine: BarcodeEngine by lazy { BarcodeEngine() }

    val appVersion: String
        get() = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        }.getOrDefault("1.0.0")

    suspend fun seedIfNeeded() {
        DatabaseSeeder.seedIfEmpty(database)
    }

    /** 供「關於」頁顯示目前權限（應該只有 CAMERA）。 */
    fun grantedPermissions(): List<String> = runCatching {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()
    }.getOrDefault(emptyList())
}
