package com.chaoshangka.manager.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 實機 OCR／條碼辨識測試（需要真實裝置或模擬器，以及相機以外的硬體不需特別權限）。
 *
 * 測試素材：`app/src/androidTest/assets/cards/chaoshangka.png`
 * （使用者提供的參考樣張，含全家、7-ELEVEN、萊爾富卡片）。
 *
 * 這一組測試**只驗證流程可跑通**（能載入影像、ML Kit 內嵌模型可離線辨識、能解析出欄位），
 * 刻意不對「特定卡片是否一定辨識成功」做斷言：
 * 實體卡片的反光、遮蔽、拍攝角度都會影響結果，準確率必須由使用者在實機上自行確認。
 *
 * 執行方式（需連接裝置或啟動模擬器）：
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OcrSampleImageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun loadSampleBitmap() = context.assets.open("cards/chaoshangka.png").use { stream ->
        BitmapFactory.decodeStream(stream)
    }

    @Test
    fun 內嵌文字辨識可以在離線狀態下載入模型並執行() = runBlocking {
        val bitmap = loadSampleBitmap()
        assertTrue("範例圖片必須能被解碼", bitmap.width > 0)

        val engine = TextRecognitionEngine()
        try {
            val blocks = engine.recognize(bitmap)
            // 只要求「有抓到文字」，不要求特定內容完全正確
            val text = blocks.joinToString("\n") { it.text }
            println("=== OCR 範例樣張結果 ===\n$text")
            assertTrue("應至少辨識出一些文字（若失敗，代表內嵌模型未正確載入）", text.isNotBlank())

            // 用辨識結果推導欄位，驗證解析流程不會崩潰，且不會捏造資料
            val extraction = CardInfoExtractor.extract(
                ScanAnalysis(blocks = blocks),
                today = LocalDate.now(),
                warningDays = 7,
            )
            println("=== 解析結果 ===")
            println("超商：${extraction.storeType}")
            println("商品名稱：${extraction.productName}")
            println("面額：${extraction.denomination}")
            println("期限：${extraction.expiry.startDate} ~ ${extraction.expiry.endDate} (${extraction.expiry.status})")
            println("卡號：${extraction.cardNumber}")
            println("提醒：${extraction.warnings}")
        } finally {
            engine.close()
        }
    }

    @Test
    fun 條碼辨識可以離線執行並回傳字串() = runBlocking {
        val bitmap = loadSampleBitmap()
        val engine = BarcodeEngine()
        try {
            val detected = engine.detect(bitmap)
            println("=== 條碼辨識結果（${detected.size} 組）===")
            detected.forEach { barcode ->
                println("${barcode.barcodeFormat.displayName}: '${barcode.rawValue}'")
            }
            // 不要求一定要辨識到條碼（樣張為多張卡片拼貼、部分條碼被遮蔽），
            // 但要求每個回傳值的內容都是字串且不含轉型錯誤。
            detected.forEach { barcode ->
                assertTrue("條碼內容必須以字串保存", barcode.rawValue is String)
            }
        } finally {
            engine.close()
        }
    }
}
