package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.ExpiryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 日期解析測試：民國年／西元年、單一日期／日期區間、各種分隔符號，
 * 以及閏年、月底、跨年等邊界情況。
 */
class DateParserTest {

    private val today = LocalDate.of(2026, 10, 9)

    // ---------------------------------------------------- 西元年

    @Test
    fun `解析中文西元單一日期`() {
        val result = DateParser.parse("本卡兌換期限至 2029 年 06 月 30 日")
        assertEquals(LocalDate.of(2029, 6, 30), result.end)
        assertEquals(LocalDate.of(2029, 6, 30), result.start)
        assertEquals(DateConfidence.HIGH, result.confidence)
    }

    @Test
    fun `解析斜線與連字號西元日期`() {
        assertEquals(LocalDate.of(2027, 12, 31), DateParser.parse("有效期限 2027/12/31").end)
        assertEquals(LocalDate.of(2027, 12, 31), DateParser.parse("有效期限 2027-12-31").end)
        assertEquals(LocalDate.of(2027, 12, 31), DateParser.parse("有效期限 2027.12.31").end)
    }

    @Test
    fun `解析八碼連續西元日期`() {
        assertEquals(LocalDate.of(2028, 3, 5), DateParser.parse("20280305").end)
    }

    // ---------------------------------------------------- 民國年

    @Test
    fun `三碼年份視為民國年`() {
        val result = DateParser.parse("使用期限 115/02/26")
        assertEquals(LocalDate.of(2026, 2, 26), result.end)
        assertEquals(DateConfidence.HIGH, result.confidence)
    }

    @Test
    fun `解析民國年日期區間`() {
        val result = DateParser.parse("115/02/26~116/08/26")
        assertEquals(LocalDate.of(2026, 2, 26), result.start)
        assertEquals(LocalDate.of(2027, 8, 26), result.end)
        assertEquals(DateConfidence.HIGH, result.confidence)
    }

    @Test
    fun `民國年區間跨西元年仍可解析`() {
        val result = DateParser.parse("民國 114 年 12 月 31 日~115 年 01 月 01 日")
        assertEquals(LocalDate.of(2025, 12, 31), result.start)
        assertEquals(LocalDate.of(2026, 1, 1), result.end)
    }

    @Test
    fun `一或兩碼年份標記為需人工確認`() {
        val result = DateParser.parse("99/06/30")
        assertEquals(DateConfidence.LOW, result.confidence)
    }

    // ---------------------------------------------------- 區間與分隔符號

    @Test
    fun `西元日期區間以結束日為到期依據`() {
        val result = DateParser.parse("有效期限 2025/04/01~2026/06/30")
        assertEquals(LocalDate.of(2025, 4, 1), result.start)
        assertEquals(LocalDate.of(2026, 6, 30), result.end)
    }

    @Test
    fun `全形數字與波浪號也能解析`() {
        val result = DateParser.parse("有效期限 ２０２６／０６／３０")
        assertEquals(LocalDate.of(2026, 6, 30), result.end)
    }

    @Test
    fun `多個日期取最早為起始、最晚為結束`() {
        val text = "開卡日: 2025/04/01\n有效期限 2025/04/01~2026/06/30"
        val result = DateParser.parse(text)
        assertEquals(LocalDate.of(2025, 4, 1), result.start)
        assertEquals(LocalDate.of(2026, 6, 30), result.end)
        assertTrue(result.confidence == DateConfidence.HIGH)
    }

    @Test
    fun `兩個日期但沒有區間符號時標記需確認`() {
        val result = DateParser.parse("2025/04/01 2026/06/30")
        assertEquals(DateConfidence.LOW, result.confidence)
    }

    @Test
    fun `台灣常見的至字區間`() {
        val result = DateParser.parse("适用期間 2025/04/01 至 2026/06/30")
        assertEquals(LocalDate.of(2026, 6, 30), result.end)
    }

    // ---------------------------------------------------- 邊界：閏年、月底、跨年

    @Test
    fun `閏年二月二十九日可解析`() {
        assertEquals(LocalDate.of(2028, 2, 29), DateParser.parse("117/02/29").end)
        assertEquals(LocalDate.of(2024, 2, 29), DateParser.parse("2024/02/29").end)
    }

    @Test
    fun `非閏年的二月二十九日無法解析並標記須確認`() {
        val result = DateParser.parse("2027/02/29")
        assertNull(result.end)
        assertTrue(result.hasDateLikeText)
        assertEquals(DateConfidence.LOW, result.confidence)
    }

    @Test
    fun `月底十二月三十一日可解析`() {
        assertEquals(LocalDate.of(2026, 12, 31), DateParser.parse("2026/12/31").end)
    }

    @Test
    fun `跨年區間以結束日判斷`() {
        val result = DateParser.parse("2025/12/31~2026/01/01")
        assertEquals(LocalDate.of(2025, 12, 31), result.start)
        assertEquals(LocalDate.of(2026, 1, 1), result.end)
    }

    @Test
    fun `不存在的月份不會產生日期並標記須確認`() {
        val result = DateParser.parse("有效期限 2029/13/30")
        assertNull(result.end)
        assertEquals(DateConfidence.LOW, result.confidence)
    }

    // ---------------------------------------------------- 狀態判斷

    @Test
    fun `無日期文字時視為無期限`() {
        val info = DateParser.toExpiryInfo("本卡無使用期限，請妥善保管", today, 7)
        assertEquals(ExpiryStatus.NO_EXPIRY, info.status)
        assertNull(info.endDate)
    }

    @Test
    fun `期限待確認不會被當成無期限`() {
        val info = DateParser.toExpiryInfo("有效期限 2029/13/30", today, 7)
        assertEquals(ExpiryStatus.NEEDS_REVIEW, info.status)
    }

    @Test
    fun `已過期的狀態判斷`() {
        assertEquals(
            ExpiryStatus.EXPIRED,
            DateParser.evaluate(null, LocalDate.of(2026, 1, 1), false, today, 7),
        )
    }

    @Test
    fun `今天到期的狀態判斷`() {
        assertEquals(ExpiryStatus.DUE_TODAY, DateParser.evaluate(null, today, false, today, 7))
    }

    @Test
    fun `七天內到期的狀態判斷`() {
        assertEquals(
            ExpiryStatus.EXPIRING_SOON,
            DateParser.evaluate(null, today.plusDays(7), false, today, 7),
        )
        assertEquals(
            ExpiryStatus.VALID,
            DateParser.evaluate(null, today.plusDays(8), false, today, 7),
        )
    }

    @Test
    fun `提醒門檻可調整`() {
        assertEquals(
            ExpiryStatus.EXPIRING_SOON,
            DateParser.evaluate(null, today.plusDays(30), false, today, 30),
        )
    }

    @Test
    fun `沒有到期日時狀態為無期限`() {
        assertEquals(ExpiryStatus.NO_EXPIRY, DateParser.evaluate(null, null, false, today, 7))
        assertFalse(DateParser.parse("本卡無使用期限").hasDateLikeText)
    }
}
