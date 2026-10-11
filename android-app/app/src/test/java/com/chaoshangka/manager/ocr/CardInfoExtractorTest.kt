package com.chaoshangka.manager.ocr

import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.StoreType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 辨識欄位擷取測試。
 *
 * 文字 fixtures 依使用者提供的參考樣張（超商卡.png）撰寫，
 * 內容為全家、7-ELEVEN、萊爾富實際卡片上可見的文字；
 * OKmart 因為沒有樣本，使用通用流程驗證，不假設固定版面。
 *
 * 這些測試驗證的是「從 OCR 文字與條碼結果推導欄位」的邏輯；
 * 實機 OCR 準確率仍需在手機上以真實照片驗證。
 */
class CardInfoExtractorTest {

    private val today = LocalDate.of(2026, 10, 9)

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("cards/$name")!!.readText()

    private fun analysisOf(
        text: String,
        barcodes: List<DetectedBarcode> = emptyList(),
    ): ScanAnalysis {
        val lines = text.trim().lines().filter { it.isNotBlank() }
        val blocks = lines.mapIndexed { index, line ->
            OcrBlock(
                text = line,
                left = 0,
                top = index * 30,
                right = 900,
                bottom = index * 30 + 24,
                lines = listOf(OcrLine(line, 0, index * 30, 900, index * 30 + 24)),
            )
        }
        return ScanAnalysis(blocks = blocks, barcodes = barcodes)
    }

    @Test
    fun `全家禮品提領卡可辨識商品名稱 期限與卡號`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(
                fixture("familymart_gift_pickup.txt"),
                listOf(
                    DetectedBarcode(BarcodeFormatType.CODE_128, "0014835"),
                    DetectedBarcode(BarcodeFormatType.QR_CODE, "https://www.family.com.tw"),
                ),
            ),
            today,
            7,
        )

        assertEquals(StoreType.FAMILYMART, extraction.storeType)
        assertEquals(LocalDate.of(2029, 6, 30), extraction.expiry.endDate)
        assertEquals(ExpiryStatus.VALID, extraction.expiry.status)
        assertEquals("E100025001788209", extraction.cardNumber)
        assertTrue(extraction.productName!!.contains("Let's Café"))
        assertEquals(2, extraction.barcodeDrafts.size)
        // 一維條碼優先做為主要兌換條碼
        assertEquals("0014835", extraction.barcodeDrafts.first { it.isPrimary }.rawValue)
    }

    @Test
    fun `全家禮券卡可辨識面額且沒有期限時標示無期限`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(fixture("familymart_voucher_1000.txt")),
            today,
            7,
        )

        assertEquals(StoreType.FAMILYMART, extraction.storeType)
        assertEquals(1000, extraction.denomination)
        assertEquals(ExpiryStatus.NO_EXPIRY, extraction.expiry.status)
        assertNull(extraction.expiry.endDate)
        assertTrue(extraction.warnings.any { it.contains("條碼") })
    }

    @Test
    fun `7-ELEVEN 商品卡可辨識面額與有效期限`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(
                fixture("seven_value_card_50.txt"),
                listOf(DetectedBarcode(BarcodeFormatType.EAN_13, "730000204307646")),
            ),
            today,
            7,
        )

        assertEquals(StoreType.SEVEN_ELEVEN, extraction.storeType)
        assertEquals(50, extraction.denomination)
        assertEquals(LocalDate.of(2025, 4, 1), extraction.expiry.endDate)
        assertEquals(ExpiryStatus.EXPIRED, extraction.expiry.status)
        assertEquals(CardType.VALUE_CARD, extraction.cardType)
        // 條碼內容不可被當成卡號
        assertNull(extraction.cardNumber)
    }

    @Test
    fun `7-ELEVEN 提貨卡可辨識日期區間並以結束日為到期日`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(
                fixture("seven_pickup_card.txt"),
                listOf(DetectedBarcode(BarcodeFormatType.CODE_128, "730000204307646")),
            ),
            today,
            7,
        )

        assertEquals(StoreType.SEVEN_ELEVEN, extraction.storeType)
        assertEquals(LocalDate.of(2025, 4, 1), extraction.expiry.startDate)
        assertEquals(LocalDate.of(2026, 6, 30), extraction.expiry.endDate)
        assertEquals(CardType.PICKUP_CARD, extraction.cardType)
        assertTrue(extraction.productName!!.contains("美式"))
    }

    @Test
    fun `萊爾富可辨識民國年區間與卡號`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(
                fixture("hilife_value_card.txt"),
                listOf(DetectedBarcode(BarcodeFormatType.CODE_128, "7100015065445114")),
            ),
            today,
            7,
        )

        assertEquals(StoreType.HILIFE, extraction.storeType)
        assertEquals(LocalDate.of(2026, 2, 26), extraction.expiry.startDate)
        assertEquals(LocalDate.of(2027, 8, 26), extraction.expiry.endDate)
        assertEquals("Q0050000645468", extraction.cardNumber)
        // 沒有標示金額時不得捏造面額
        assertNull(extraction.denomination)
    }

    @Test
    fun `萊爾富條碼不會與卡號混淆`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(
                fixture("hilife_value_card.txt"),
                listOf(
                    DetectedBarcode(BarcodeFormatType.CODE_128, "7100015065445114"),
                    DetectedBarcode(BarcodeFormatType.QR_CODE, "Q0050000645468"),
                ),
            ),
            today,
            7,
        )
        // QR 內容即卡號時，不重複當成文字卡號
        assertNull(extraction.cardNumber)
        assertEquals(2, extraction.barcodeDrafts.size)
        assertEquals("7100015065445114", extraction.barcodeDrafts.first { it.isPrimary }.rawValue)
    }

    @Test
    fun `OKmart 使用通用流程辨識`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf(fixture("okmart_value_card.txt")),
            today,
            7,
        )

        assertEquals(StoreType.OKMART, extraction.storeType)
        assertEquals(100, extraction.denomination)
        assertEquals(LocalDate.of(2027, 12, 31), extraction.expiry.endDate)
    }

    @Test
    fun `完全辨識不到時不得填入虛構資料`() {
        val extraction = CardInfoExtractor.extract(ScanAnalysis(), today, 7)

        assertEquals(StoreType.UNKNOWN, extraction.storeType)
        assertNull(extraction.productName)
        assertNull(extraction.denomination)
        assertNull(extraction.cardNumber)
        assertNull(extraction.expiry.endDate)
        assertEquals(ExpiryStatus.NO_EXPIRY, extraction.expiry.status)
        assertTrue(extraction.barcodeDrafts.isEmpty())
        assertTrue(extraction.warnings.any { it.contains("商品名稱") })
        assertTrue(extraction.warnings.any { it.contains("面額") })
        assertTrue(extraction.warnings.any { it.contains("條碼") })
    }

    @Test
    fun `無法解析的期限標示為待確認`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf("統一超商股份有限公司\n商品卡 50 元\n有效期限 2029/13/30"),
            today,
            7,
        )
        assertEquals(ExpiryStatus.NEEDS_REVIEW, extraction.expiry.status)
        assertTrue(extraction.warnings.any { it.contains("有效期限") })
    }

    @Test
    fun `條碼被刮除時提示手動補登`() {
        val extraction = CardInfoExtractor.extract(
            analysisOf("全家便利商店股份有限公司\n商品卡 100 元\n條碼區已被刮除，請見實體卡片"),
            today,
            7,
        )
        assertTrue(extraction.warnings.any { it.contains("刮除") || it.contains("遮蔽") })
    }
}
