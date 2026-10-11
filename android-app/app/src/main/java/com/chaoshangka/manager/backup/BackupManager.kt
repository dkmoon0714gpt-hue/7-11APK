package com.chaoshangka.manager.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.chaoshangka.manager.data.local.AppDatabase
import com.chaoshangka.manager.data.local.BarcodeEntity
import com.chaoshangka.manager.data.local.ProductCategoryEntity
import com.chaoshangka.manager.data.local.toDomain
import com.chaoshangka.manager.data.local.toEntity
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.data.repository.GiftCardRepository
import com.chaoshangka.manager.data.repository.PhotoStore
import com.chaoshangka.manager.data.repository.SettingsRepository
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.util.DateParser
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本機備份與還原（ZIP + JSON）。
 *
 *  - 匯出：卡片、條碼、自訂分類、原始照片與設定，全部寫入一個 ZIP 檔。
 *  - 匯入：先檢查格式、版本與完整性，並提示可能重複的資料；
 *    實際寫入在**單一資料庫交易**內完成，失敗會整批回滾，不會破壞原有資料。
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val cardRepository: GiftCardRepository,
    private val categoryRepository: CategoryRepository,
    private val photoStore: PhotoStore,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri, appVersion: String): BackupManifest {
        val cards = cardRepository.snapshot()
        val categories = categoryRepository.all()
        val warnings = mutableListOf<String>()

        val categoryNames = categories.associate { it.id to it.categoryName }
        val photos = mutableListOf<Pair<String, File>>()

        val backupCards = cards.map { card ->
            val photoName = card.originalImagePath
                ?.takeIf { photoStore.exists(it) }
                ?.let { path ->
                    val entry = "photos/card_${card.id}.jpg"
                    photos += entry to File(path)
                    entry
                }
            BackupCard(
                sourceId = card.id,
                storeType = card.storeType.key,
                productName = card.productName,
                categoryName = card.categoryId?.let { categoryNames[it] },
                denomination = card.denomination,
                productSpecification = card.productSpecification,
                quantity = card.quantity,
                cardType = card.cardType.key,
                startDate = card.startDate?.toString(),
                expiryDate = card.expiryDate?.toString(),
                expiryNeedsReview = card.expiryStatus == ExpiryStatus.NEEDS_REVIEW,
                cardStatus = card.cardStatus.key,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
                usedAt = card.usedAt,
                cardNumber = card.cardNumber,
                notes = card.notes,
                isArchived = card.isArchived,
                photoFile = photoName,
            )
        }

        val backupBarcodes = cards.flatMap { card ->
            card.barcodes.map { barcode ->
                BackupBarcode(
                    sourceCardId = card.id,
                    barcodeFormat = barcode.barcodeFormat.key,
                    rawValue = barcode.rawValue,
                    isPrimary = barcode.isPrimary,
                    recognitionStatus = barcode.recognitionStatus.key,
                    photoFile = null,
                )
            }
        }

        if (cards.any { it.originalImagePath != null && !photoStore.exists(it.originalImagePath) }) {
            warnings += "部分卡片照片檔案已不存在，備份內容不含該照片。"
        }

        val manifest = BackupManifest(
            backupVersion = BackupManifest.CURRENT_BACKUP_VERSION,
            appVersion = appVersion,
            exportedAt = clock.millis(),
            cardCount = backupCards.size,
            barcodeCount = backupBarcodes.size,
            categoryCount = categories.size,
            photoCount = photos.size,
        )

        val resolver = context.contentResolver
        resolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                fun writeText(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                writeText("manifest.json", json.encodeToString(manifest))
                writeText("cards.json", json.encodeToString(backupCards))
                writeText("barcodes.json", json.encodeToString(backupBarcodes))
                writeText(
                    "categories.json",
                    json.encodeToString(
                        categories.map { BackupCategory(
                            storeType = it.storeType?.key,
                            categoryName = it.categoryName,
                            denomination = it.denomination,
                            productSpecification = it.productSpecification,
                            isDefault = it.isDefault,
                            isPresetProduct = it.isPresetProduct,
                            isActive = it.isActive,
                            sortOrder = it.sortOrder,
                        ) },
                    ),
                )
                writeText(
                    "settings.json",
                    json.encodeToString(
                        BackupSettings(
                            warningDays = settingsRepository.warningDays.value,
                            farWarningDays = settingsRepository.farWarningDays.value,
                        ),
                    ),
                )
                photos.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("無法寫入備份檔，請確認儲存位置權限。")

        return manifest
    }

    /** 只讀取備份檔的資訊，不修改任何資料。 */
    suspend fun inspect(uri: Uri): BackupInspection {
        val problems = mutableListOf<String>()
        var manifest = BackupManifest(appVersion = "", exportedAt = 0L)
        var cards = emptyList<BackupCard>()
        var barcodes = emptyList<BackupBarcode>()
        var categories = emptyList<BackupCategory>()

        readZip(uri) { name, bytes ->
            when (name) {
                "manifest.json" -> runCatching { json.decodeFromString<BackupManifest>(bytes.decodeToString()) }
                    .onSuccess { manifest = it }
                    .onFailure { problems += "manifest.json 格式錯誤：${it.message}" }

                "cards.json" -> runCatching { json.decodeFromString<List<BackupCard>>(bytes.decodeToString()) }
                    .onSuccess { cards = it }
                    .onFailure { problems += "cards.json 格式錯誤：${it.message}" }

                "barcodes.json" -> runCatching { json.decodeFromString<List<BackupBarcode>>(bytes.decodeToString()) }
                    .onSuccess { barcodes = it }
                    .onFailure { problems += "barcodes.json 格式錯誤：${it.message}" }

                "categories.json" -> runCatching { json.decodeFromString<List<BackupCategory>>(bytes.decodeToString()) }
                    .onSuccess { categories = it }
                    .onFailure { problems += "categories.json 格式錯誤：${it.message}" }
            }
        }

        if (manifest.backupVersion < BackupManifest.MIN_SUPPORTED_VERSION) {
            problems += "備份檔版本過舊，無法匯入。"
        }
        if (manifest.backupVersion > BackupManifest.CURRENT_BACKUP_VERSION) {
            problems += "備份檔版本較新，請先更新 APP 再匯入。"
        }

        val duplicates = countExistingBarcodes(cards, barcodes)

        return BackupInspection(
            manifest = manifest,
            cardCount = cards.size,
            barcodeCount = barcodes.size,
            categoryCount = categories.size,
            potentialDuplicates = duplicates,
            versionSupported = manifest.backupVersion in
                BackupManifest.MIN_SUPPORTED_VERSION..BackupManifest.CURRENT_BACKUP_VERSION,
            problems = problems,
        )
    }

    private suspend fun countExistingBarcodes(
        cards: List<BackupCard>,
        barcodes: List<BackupBarcode>,
    ): Int {
        val storeByCard = cards.associate { it.sourceId to it.storeType }
        var duplicates = 0
        barcodes.forEach { barcode ->
            val store = storeByCard[barcode.sourceCardId] ?: return@forEach
            val found = db.giftCardDao().findUnusedCardsUsingBarcode(store, barcode.rawValue)
            if (found.isNotEmpty()) duplicates++
        }
        return duplicates
    }

    /**
     * 匯入備份。
     *
     * @param skipDuplicates true（預設）時略過條碼已存在的卡片；false 時仍全部新增（不阻止合法重複）。
     */
    suspend fun restoreFrom(uri: Uri, skipDuplicates: Boolean = true): RestoreReport {
        var cards = emptyList<BackupCard>()
        var barcodes = emptyList<BackupBarcode>()
        var categories = emptyList<BackupCategory>()
        var settings: BackupSettings? = null
        var hasManifest = false
        val photoBytes = mutableMapOf<String, ByteArray>()

        readZip(uri) { name, bytes ->
            when (name) {
                "manifest.json" -> {
                    hasManifest = true
                    runCatching { json.decodeFromString<BackupManifest>(bytes.decodeToString()) }
                }

                "cards.json" -> cards = json.decodeFromString(bytes.decodeToString())
                "barcodes.json" -> barcodes = json.decodeFromString(bytes.decodeToString())
                "categories.json" -> categories = json.decodeFromString(bytes.decodeToString())
                "settings.json" -> settings = runCatching { json.decodeFromString<BackupSettings>(bytes.decodeToString()) }.getOrNull()
                else -> if (name.startsWith("photos/") && !name.endsWith("/")) {
                    photoBytes[name] = bytes
                }
            }
        }

        // 不是本 APP 的備份檔時直接失敗，且此時尚未寫入任何資料。
        if (!hasManifest && cards.isEmpty() && categories.isEmpty() && barcodes.isEmpty()) {
            throw IllegalStateException(
                "這不是有效的備份檔：找不到 manifest.json，請確認是否為本 APP 匯出的 .zip 備份檔。",
            )
        }

        val warnings = mutableListOf<String>()
        val now = clock.millis()
        val today = LocalDate.now(clock)

        // 先把照片寫入私有目錄（交易失敗時會刪除，不留下孤兒檔案）。
        val restoredPhotos = mutableMapOf<String, String>()
        photoBytes.forEach { (entryName, bytes) ->
            val fileName = "restored_${entryName.substringAfterLast('/')}"
            restoredPhotos[entryName] = photoStore.restorePhoto(fileName, bytes)
        }

        var importedCards = 0
        var importedBarcodes = 0
        var importedCategories = 0
        var skippedDuplicates = 0

        try {
            db.withTransaction {
                val existingCategories = db.categoryDao().getAll().map { it.toDomain() }
                val categoryIdByName = existingCategories
                    .associateBy({ it.categoryName to it.storeType }, { it.id })
                    .toMutableMap()

                categories.forEach { category ->
                    val key = category.categoryName to category.storeType?.let { StoreType.fromKey(it) }
                    if (categoryIdByName.containsKey(key)) return@forEach
                    val id = db.categoryDao().insert(
                        ProductCategoryEntity(
                            storeType = category.storeType,
                            categoryName = category.categoryName,
                            denomination = category.denomination,
                            productSpecification = category.productSpecification,
                            isDefault = category.isDefault,
                            isPresetProduct = category.isPresetProduct,
                            isActive = category.isActive,
                            sortOrder = category.sortOrder,
                        ),
                    )
                    categoryIdByName[key] = id
                    importedCategories++
                }

                val idMap = mutableMapOf<Long, Long>()
                val barcodesByCard = barcodes.groupBy { it.sourceCardId }

                cards.forEach { card ->
                    val store = StoreType.fromKey(card.storeType)
                    val cardBarcodes = barcodesByCard[card.sourceId].orEmpty()
                    if (skipDuplicates && cardBarcodes.isNotEmpty()) {
                        val duplicate = db.giftCardDao().findUnusedCardsUsingBarcode(store.key, cardBarcodes.first().rawValue)
                        if (duplicate.isNotEmpty()) {
                            skippedDuplicates++
                            return@forEach
                        }
                    }

                    val photoPath = card.photoFile?.let { restoredPhotos[it] }
                    val entity = GiftCard(
                        storeType = store,
                        productName = card.productName,
                        categoryId = card.categoryName?.let { categoryIdByName[it to store] },
                        denomination = card.denomination,
                        productSpecification = card.productSpecification,
                        quantity = card.quantity,
                        cardType = CardType.fromKey(card.cardType),
                        startDate = card.startDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() },
                        expiryDate = card.expiryDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() },
                        expiryStatus = if (card.expiryNeedsReview) {
                            ExpiryStatus.NEEDS_REVIEW
                        } else {
                            DateParser.evaluate(
                                start = null,
                                end = card.expiryDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() },
                                needsReview = false,
                                today = today,
                                warningDays = settings?.warningDays ?: 7,
                            )
                        },
                        cardStatus = CardStatus.fromKey(card.cardStatus),
                        createdAt = card.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = now,
                        usedAt = card.usedAt,
                        originalImagePath = photoPath,
                        notes = card.notes,
                        isArchived = card.isArchived,
                        cardNumber = card.cardNumber,
                    ).toEntity()

                    val newId = db.giftCardDao().insert(entity)
                    idMap[card.sourceId] = newId
                    importedCards++
                }

                val barcodeRows = barcodes.mapNotNull { barcode ->
                    val newCardId = idMap[barcode.sourceCardId] ?: return@mapNotNull null
                    BarcodeEntity(
                        giftCardId = newCardId,
                        barcodeFormat = BarcodeFormatType.fromKey(barcode.barcodeFormat).key,
                        rawValue = barcode.rawValue,
                        barcodeImagePath = null,
                        isPrimary = barcode.isPrimary,
                        recognitionStatus = RecognitionStatus.fromKey(barcode.recognitionStatus).key,
                        createdAt = now,
                    )
                }
                if (barcodeRows.isNotEmpty()) {
                    db.barcodeDao().insertAll(barcodeRows)
                    importedBarcodes = barcodeRows.size
                }

                // 每張卡片最多一個主要條碼
                idMap.values.forEach { cardId ->
                    val rows = db.barcodeDao().getForCard(cardId)
                    if (rows.isNotEmpty() && rows.none { it.isPrimary }) {
                        db.barcodeDao().setPrimary(rows.first().id)
                    }
                    val primaries = rows.filter { it.isPrimary }
                    if (primaries.size > 1) {
                        db.barcodeDao().clearPrimary(cardId)
                        db.barcodeDao().setPrimary(primaries.first().id)
                    }
                }
            }
        } catch (e: Exception) {
            restoredPhotos.values.forEach { photoStore.delete(it) }
            throw IllegalStateException("匯入失敗，原有資料未被變更：${e.message}", e)
        }

        settings?.let {
            settingsRepository.setWarningDays(it.warningDays)
            settingsRepository.setFarWarningDays(it.farWarningDays)
        }

        if (importedCards == 0) warnings += "備份檔中沒有可匯入的卡片。"
        if (skippedDuplicates > 0) warnings += "有 $skippedDuplicates 張卡片的條碼已存在，已略過。"

        return RestoreReport(
            cardsImported = importedCards,
            barcodesImported = importedBarcodes,
            categoriesImported = importedCategories,
            photosRestored = restoredPhotos.size,
            duplicateCardsSkipped = skippedDuplicates,
            warnings = warnings,
        )
    }

    private fun readZip(uri: Uri, onEntry: (String, ByteArray) -> Unit) {
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && isSupportedEntry(name)) {
                        onEntry(name, zip.readBytes())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("無法讀取備份檔，請確認檔案是否存在。")
    }

    private fun isSupportedEntry(name: String): Boolean =
        name in setOf(
            "manifest.json",
            "cards.json",
            "barcodes.json",
            "categories.json",
            "settings.json",
        ) || name.startsWith("photos/")

    /** 匯入前檢查檔名後綴，避免使用者選到非備份檔。 */
    fun looksLikeBackup(displayName: String?): Boolean =
        displayName?.lowercase()?.endsWith(".zip") == true
}
