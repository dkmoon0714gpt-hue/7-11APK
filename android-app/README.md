# 超商卡管家（Chaoshangka Manager）

臺灣四大超商實體提貨卡／商品卡／兌換卡的本機管理 APP。
使用手機相機拍照，離線辨識文字（OCR）與條碼，人工確認後存入本機資料庫，並可依有效期限排序、在櫃檯全螢幕出示條碼。

- 支援超商：7-ELEVEN、全家便利商店、萊爾富、OK超商
- Android 原生：Kotlin + Jetpack Compose（Material 3）+ Room + CameraX + ML Kit（內嵌模型）
- 完全離線：**APK 不宣告網路權限**，卡片照片、條碼、卡號、有效期限都不會離開手機

---

## 1. 目前狀態（實測結果，誠實標註）

| 項目 | 狀態 | 說明 |
|---|---|---|
| Gradle 建置 `:app:assembleDebug` | **已完成並驗證** | 於建置環境實際執行，`BUILD SUCCESSFUL`，產出 APK（見下方檔案清單） |
| 單元測試 `:app:testDebugUnitTest` | **已完成並驗證** | **69 個測試全部通過**（日期解析 24、排序統計 9、面額與類型分類 7、OCR 欄位擷取 10、Repository 15、資料持久化 1、備份還原 3） |
| Lint `:app:lintDebug` | **已完成並驗證** | **0 個 Error**，25 個 Warning（多為「有新版相依套件」提示） |
| APK 實際權限 | **已完成並驗證** | 以 `aapt2 dump badging` 檢查：只有 `CAMERA`（無 `INTERNET`、無 `ACCESS_NETWORK_STATE`） |
| 離線模型內嵌 | **已完成並驗證** | APK 內含 `assets/mlkit-google-ocr-models/...` 與 `lib/*/libmlkit_google_ocr_pipeline.so`，安裝後不需下載模型 |
| 實機測試 APK 編譯 | **已完成並驗證** | `:app:assembleDebugAndroidTest` 成功 |
| 實機相機／拍照流程 | **尚未驗證** | 建置環境沒有相機，必須在實機測試 |
| 實機 OCR／條碼辨識準確率 | **尚未驗證** | 建置環境沒有實體卡片；程式流程與欄位解析已由單元測試覆蓋，但**不保證所有實體卡片都能正確辨識** |
| 實機安裝與條碼被超商掃描 | **尚未驗證** | 需在實機與實際櫃檯確認 |
| 實機上的 JUnit 測試執行 | **尚未驗證** | 需要連接裝置才能跑 `connectedDebugAndroidTest` |

> 依規格要求：**不曾在未成功建置時宣稱 APK 已完成**；以上未驗證項目一律標示為未驗證，未以推測代替事實。

### 本次交付的檔案

| 檔案 | 大小 | 說明 |
|---|---|---|
| `app-arm64-v8a-debug.apk` | 約 39 MiB | **建議優先使用**。適用絕大多數 2016 年後的手機（arm64 裝置） |
| `app-armeabi-v7a-debug.apk` | 約 34 MiB | 給較舊的 32 位元手機使用 |
| `app-universal-debug.apk` | 約 83 MiB | 同時包含兩種 ABI（可由下方建置指令產生），檔名為 `app-universal-debug.apk` |
| `chaoshangka-android.zip` | 約 3.6 MiB | 完整 Android Studio 專案原始碼（不含建置產物） |

如果安裝時出現「應用程式與您的手機不相容」，代表你抓到了不支援該手機 ABI 的 APK，改用另一個即可。

---

## 2. 開發環境需求

| 項目 | 版本 |
|---|---|
| JDK | **17**（必要；AGP 8.7 不支援 JDK 21 以上以外的其他版本組合） |
| Android Studio | Ladybug（2024.2）或更新版本 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9（專案已附 wrapper） |
| Kotlin | 2.0.21（KSP 2.0.21-1.0.28） |
| compileSdk / targetSdk / minSdk | 35 / 35 / **26**（Android 8.0 以上） |
| Android SDK 元件 | `platforms;android-35`、`build-tools;35.0.0`、`platform-tools` |

主要相依套件：Compose BOM 2024.10.01、Room 2.6.1、CameraX 1.4.0、
`com.google.mlkit:text-recognition-chinese:16.0.1`、`com.google.mlkit:barcode-scanning:17.3.0`（兩者皆為 **bundled 內嵌版**）、
ZXing core 3.5.3、kotlinx-serialization-json 1.7.3、Coil 2.7.0、Navigation Compose 2.8.4。

---

## 3. 如何開啟專案

1. Android Studio → `File` → `Open`，選擇 `android-app/` 資料夾（不是上層資料夾）。
2. 第一次開啟時 Studio 會提示使用專案的 Gradle wrapper，選擇 **Use Gradle from 'gradle-wrapper.properties'**。
3. 若出現 SDK 授權提示，請接受；`local.properties` 會由 Studio 自動產生（內容是你電腦上的 SDK 路徑，**不要**提交到版本控制）。
4. 等 Gradle Sync 完成即可執行。

---

## 4. 如何編譯 APK

### 使用 Android Studio
`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`。

產物位置：`android-app/app/build/outputs/apk/debug/`
- `app-arm64-v8a-debug.apk`（現代手機）
- `app-armeabi-v7a-debug.apk`（較舊的 32 位元手機）
- `app-universal-debug.apk`（兩種 ABI 都包含）

### 使用命令列
```bash
cd android-app

# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

若專案複製到新環境後 `gradle/wrapper/gradle-wrapper.jar` 遺失，可用本機 Gradle 重新產生：
```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

其他常用指令：
```bash
./gradlew :app:testDebugUnitTest      # 單元測試（JVM，不需要裝置）
./gradlew :app:lintDebug              # Lint
./gradlew :app:assembleDebugAndroidTest   # 編譯實機測試 APK
./gradlew :app:connectedDebugAndroidTest  # 實機／模擬器上執行 OCR 與條碼測試
```

---

## 5. 如何安裝到 Android 手機

### 方式 A：傳輸 APK 檔案
1. 將對應手機 ABI 的 APK（多數手機用 `app-arm64-v8a-debug.apk`）傳到手機（USB、雲端硬碟或即時通訊軟體）。
2. 用手機的檔案管理員點選 APK。
3. 系統會要求「允許安裝未知的應用程式」，允許後即可安裝。
4. 安裝完成後，桌面會出現「超商卡管家」。

### 方式 B：ADB（開發者用）
```bash
adb install -r android-app/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

首次啟動時，APP 只會要求 **相機權限**。若拒絕相機權限，仍可使用「從相簿選取」或「手動新增」完成卡片登錄。

Debug APK 使用 debug 簽章，可直接安裝測試；要上架或長期使用請建立自己的 release 簽章。

---

## 6. 如何測試離線功能

APP 沒有宣告網路權限，因此「無法連網」是系統層級的保證。建議這樣驗證：

1. **飛航模式測試**：開啟飛航模式（或關閉 Wi-Fi 與行動網路）後再啟動 APP。
2. 拍照 → 辨識 → 儲存 → 查看詳細頁 → 全螢幕條碼，整個流程都應正常運作。
3. 到「設定」頁確認「要求權限」欄位只列出「相機」。
4. 若要更嚴格驗證，可在電腦端執行：
   ```bash
   aapt2 dump badging app-debug.apk | grep uses-permission
   ```
   應只看到 `android.permission.CAMERA`（以及 AndroidX 內部的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`）。

> 技術說明：ML Kit 的遞移相依套件 `com.google.android.datatransport` 會在合併清單時自動加入 `INTERNET`；
> 本專案在 `AndroidManifest.xml` 以 `tools:node="remove"` 明確移除，確保最終 APK 完全沒有網路權限（已實測驗證）。

---

## 7. 如何測試條碼辨識

### 實機測試步驟
1. 啟動 APP → 右上角相機圖示 → 允許相機權限。
2. 將卡片放在光線充足處，讓**整張卡片**進入取景框（鏡頭內若有條碼，畫面會提示「鏡頭內偵測到 N 組條碼」）。
3. 按快門 → 等待「正在本機辨識文字與條碼…」。
4. 確認頁會列出所有辨識到的條碼；可切換「主要條碼」、切換格式、修改內容或手動新增。
5. 儲存後進入卡片詳細頁，確認條碼以「黑白高對比、保留留白」重新繪製；若無法可靠重新編碼，畫面會改顯示**原始卡片照片**並提示以原卡為準（不會硬畫錯誤的條碼）。
6. 點「全螢幕條碼」在櫃檯出示（離開畫面會自動還原原本的螢幕亮度）。

### 支援的條碼格式
辨識（ML Kit `barcode-scanning`，內嵌）：Code 128、Code 39、Code 93、Codabar、EAN-13、EAN-8、UPC-A、UPC-E、ITF、QR Code、Data Matrix、PDF417、Aztec。

重新繪製（ZXing core）：上述格式皆可嘗試；其中 EAN-13／EAN-8／UPC-A／UPC-E／ITF 需要符合檢查碼與長度規則，不符合時會自動回退成顯示原始照片。

### 可重複執行的測試素材
- OCR／欄位解析測試使用 `app/src/test/resources/cards/*.txt`（依 `超商卡.png` 樣張中的實際文字撰寫：
  全家禮品提領卡、全家禮券卡 1000 元、7-ELEVEN 商品卡 50 元、7-ELEVEN 提貨卡、萊爾富商品卡、OK超商商品卡）。
- 實機影像測試使用 `app/src/androidTest/assets/cards/chaoshangka.png`（你提供的樣張）。
- 要新增自己的測試素材：把照片放到 `app/src/androidTest/assets/cards/`，並在 `OcrSampleImageTest` 指定檔名即可。

### 重要提醒
條碼能被讀取，**只代表程式讀到了條碼內容**，不代表該卡片一定可以在超商兌換、也不代表餘額或真偽已被驗證。APP 不會查詢超商系統，也不保證能通過所有掃描設備。

---

## 8. 如何備份與還原資料

**設定 → 備份與還原**

- **匯出備份**：選擇儲存位置（Android 系統檔案選擇器），會產生一個 ZIP 檔，內容為：
  - `manifest.json`：備份版本、APP 版本、匯出時間、各項計數
  - `cards.json`、`barcodes.json`、`categories.json`、`settings.json`
  - `photos/`：原始卡片照片
- **匯入備份**：選擇 ZIP 檔後，APP 會先檢查格式、版本與資料完整性，並顯示卡片／條碼／分類數量與**可能重複的條碼數**，再由你選擇：
  - 「略過重複並匯入」：略過條碼已存在的卡片
  - 「全部匯入」：不阻止合法重複（部分卡片確實可能共用識別碼）
- 匯入全程在**單一資料庫交易**中進行；失敗會整批回滾，**不會破壞原有資料**（已由單元測試驗證）。
- 備份檔含卡號等敏感資訊，請妥善保管（例如放在加密的隨身碟）。

備份檔格式有版本欄位（目前 `backupVersion = 1`），APP 升級後仍可讀取舊備份；讀到較新版本會提示先更新 APP。

---

## 9. 資料儲存位置、備份方式、解除安裝後處理

| 項目 | 位置 |
|---|---|
| 卡片／條碼／分類資料 | App 私有目錄內的 Room 資料庫：`/data/data/com.chaoshangka.manager/databases/chaoshangka.db` |
| 卡片原始照片 | App 私有目錄：`/data/data/com.chaoshangka.manager/files/card_photos/` |
| 偏好設定（提醒天數、排序） | `SharedPreferences`：`chaoshangka_settings` |

- 這些位置**只有本 APP 能存取**，其他 App 無法讀取；卸載後資料一定消失。
- **解除安裝 APP 會一併刪除資料庫與照片**，無法復原。請在解除安裝前先匯出備份。
- 系統層級的備份（`adb backup`／Android 備份服務）預設可能包含 App 私有資料；本 APP 未另外停用，若你不希望被系統備份，請保持 `android:allowBackup="true"` 為預設值並自行評估，或在使用後刪除系統備份。
- 資料在手機重開機、APP 關閉後仍然存在（已由 `PersistenceTest` 驗證）。

---

## 10. 功能與規格對照

| 規格需求 | 實作位置 |
|---|---|
| 首頁統計（未使用／已使用／已過期／即將到期）由資料庫即時計算 | `ui/screens/HomeScreen.kt`、`domain/usecase/ComputeStatsUseCase.kt` |
| 四大超商區塊與各自統計 | `ui/components/Cards.kt`、`HomeViewModel` |
| 拍照、取景框、自動對焦、閃光燈、前後鏡頭切換 | `ui/screens/ScanScreen.kt`（CameraX） |
| 相機權限被拒時的替代路徑（相簿／手動） | 同上（`PickVisualMedia`） |
| 同時執行文字辨識與條碼辨識 | `ocr/TextRecognitionEngine.kt`、`ocr/BarcodeEngine.kt`（平行執行於 `ScanViewModel`） |
| 內容＋版面綜合判斷、不靠固定座標 | `ocr/CardInfoExtractor.kt` |
| 辨識結果全部可手動修改、未辨識顯示「待確認」 | `ui/screens/ScanResultScreen.kt` |
| 多組條碼可選擇主要條碼 | `GiftCardRepository.setPrimaryBarcode`、`ScanResultScreen` |
| 期限解析（西元／民國、單一／區間、中文、各種分隔符號） | `util/DateParser.kt` |
| 排序（已過期最前、到期日近→遠、無期限最後、待確認獨立） | `domain/usecase/SortCardsUseCase.kt` |
| 超商頁「已有卡片／尚無卡片」兩區、不計入總數 | `domain/usecase/ProductGroupBuilder.kt`、`ui/screens/StoreScreen.kt` |
| 卡片詳細頁與全螢幕條碼（高對比、不裁切、亮度調整後還原） | `CardDetailScreen.kt`、`FullscreenBarcodeScreen.kt` |
| 「使用」二次確認、記錄本機時間、可恢復未使用 | `CardDetailViewModel`、`GiftCardRepository` |
| 已使用清單（篩選／搜尋／時間新→舊、永久保留） | `ui/screens/UsedScreen.kt` |
| 重複條碼警告（可仍要新增） | `GiftCardRepository.findDuplicateCards` |
| 分類管理、刪除被使用中的分類會被阻擋 | `data/repository/CategoryRepository.kt`、`CategoriesScreen.kt` |
| 備份與還原（ZIP + JSON，含照片，交易式寫入） | `backup/BackupManager.kt`、`SettingsScreen.kt` |
| 單元測試 | `app/src/test/...`（69 個測試） |
| 實機辨識測試骨架 | `app/src/androidTest/...` |

### 資料庫結構（Room，version 1）
- `gift_cards`：卡片主資料（含 `usedAt`、`originalImagePath`、`cardNumber`、`isArchived` 等）
- `barcodes`：條碼子表，外鍵指向 `gift_cards` 並設 `onDelete = CASCADE`
- `product_categories`：分類與「尚無卡片」用的預設商品項目（以 `isPresetProduct` 區分）

日期一律以標準數值儲存：有效期限存 `LocalDate` 的 epochDay，時間戳存 epoch millis；民國年只在解析與顯示階段處理。

---

## 11. 專案結構

```
android-app/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew(.bat)
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml（只有 CAMERA 權限）
        │   ├── java/com/chaoshangka/manager/
        │   │   ├── GiftCardApp.kt, MainActivity.kt, di/AppContainer.kt
        │   │   ├── data/local/（Entities、Dao、AppDatabase、DatabaseSeeder、PresetProducts、Mappers）
        │   │   ├── data/repository/（GiftCardRepository、CategoryRepository、PhotoStore、SettingsRepository）
        │   │   ├── domain/（model、ExpiryEvaluator、usecase）
        │   │   ├── ocr/（TextRecognitionEngine、BarcodeEngine、CardInfoExtractor、ScanModels）
        │   │   ├── scanner/LiveBarcodeAnalyzer.kt
        │   │   ├── barcode/BarcodeRenderer.kt
        │   │   ├── backup/（BackupManager、BackupModels）
        │   │   ├── util/（DateParser、DateFormatters、DenominationOptions、CardTypeClassifier、CardSearch、BarcodeFormatMapper）
        │   │   └── ui/（theme、navigation、components、screens、viewmodel）
        │   └── res/（字串、主題、自適應圖示）
        ├── test/（JVM 單元測試 + Robolectric + 文字 fixtures）
        └── androidTest/（實機 OCR／條碼測試與樣張）
```

---

## 12. 已知限制

1. **未在實機驗證**：相機流程、OCR 準確率、條碼掃描成功率、實機安裝都需要你在手機上確認。
2. **辨識準確率受拍攝品質影響**：反光、陰影、角度、遮蔽都會降低 OCR 準確率；設計上所有欄位都可手動修改，且辨識不到時一律顯示「待確認」，不會填入虛構資料。
3. **條碼被刮除或遮蔽時不猜測**：APP 不會嘗試補齊缺失內容，只能手動補登。
4. **無法驗證兌換資格**：APP 不查詢超商系統、不驗證真偽或餘額；「使用」只是記錄你自己的操作。
5. **OK超商沒有實體樣本**：採通用流程（關鍵字判斷超商、日期與面額解析、條碼全格式辨識），未硬編任何版面；後續若有樣本，可在 `ocr/CardInfoExtractor.kt` 的
   `STORE_KEYWORDS`、`DATE_LABELS`、`PRODUCT_KEYWORDS` 增加規則，並在 `app/src/test/resources/cards/` 加入 fixture 測試。
6. **民國年判斷規則**：3 位數年份視為民國年（+1911）；1～2 位數年份視為民國年但標記為「期限待確認」，需人工核對；4 位數（≥1900）視為西元。
7. **多個日期時的處理**：取最早者為起始日、最晚者為結束日；若多個日期之間沒有明確區間符號（`～ - 至 到`），會標記為「期限待確認」。
8. **客服專線不會被誤認為日期**：日期偵測要求完整日期形狀（年-月-日）或含 `年`／`/` 的殘缺日期，因此像 `0800-008-711` 不會被誤判（已有單元測試）。
9. **APK 較大（單一 ABI 約 34～39 MiB，全包約 83 MiB）**：因為內嵌了 ML Kit 中文 OCR 模型與條碼辨識原生程式庫。
   專案已在 `app/build.gradle.kts` 啟用 ABI 分割（`splits { abi { ... } }`），
   單一 ABI APK 約 34～39 MiB；若你想改成只輸出一個小一點的 APK，可把
   `include("arm64-v8a", "armeabi-v7a")` 改成只留 `arm64-v8a`（2016 年後的手機幾乎都是 arm64）。
10. **資料庫版本**：目前 schema 為 version 1 且未提供 Migration；未來新增欄位時請補上 `Migration`，避免使用者資料被清除。
11. **Lint 警告**：25 個警告多為「相依套件有新版本」的提示；本專案刻意鎖定彼此相容的穩定版本組合。
12. **`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`**：這是 AndroidX 自動加入的內部權限，與網路無關。

---

## 13. 免責聲明

本 APP 為個人卡片管理工具，用於記錄與出示自己持有的超商卡片。
APP 不會、也無法查詢超商系統的餘額、兌換狀態或卡片真偽；辨識結果可能出錯，請以實體卡片與超商公告為準。
卡片遺失、毀損或逾期的風險由使用者自行承擔。
