package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 面額解析與卡片類型分類測試（避免把卡號、日期誤認為面額）。 */
class DenominationAndCardTypeTest {

    @Test
    fun `解析商品卡面額`() {
        assertEquals(50, DenominationOptions.parse("商品卡 50 元"))
        assertEquals(100, DenominationOptions.parse("商品卡 100 元"))
        assertEquals(1000, DenominationOptions.parse("禮券卡 1000元"))
    }

    @Test
    fun `解析貨幣符號面額`() {
        assertEquals(200, DenominationOptions.parse("面額 \$200"))
    }

    @Test
    fun `卡號與條碼不會被誤判為面額`() {
        assertNull(DenominationOptions.parse("E100025001788209"))
        assertNull(DenominationOptions.parse("730000204307646"))
        assertNull(DenominationOptions.parse("Q0050000645468"))
    }

    @Test
    fun `年份與日期不會被誤判為面額`() {
        assertNull(DenominationOptions.parse("本卡兌換期限至 2029 年 06 月 30 日"))
        assertNull(DenominationOptions.parse("115/02/26~116/08/26"))
    }

    @Test
    fun `萊爾富商品卡沒有標示金額時回傳空值`() {
        assertNull(DenominationOptions.parse("商品卡 注意事項:本商品卡於萊爾富門市使用"))
    }

    @Test
    fun `面額顯示文字`() {
        assertEquals("50 元", DenominationOptions.display(50))
        assertEquals(DenominationOptions.LABEL_PENDING, DenominationOptions.display(null))
    }

    @Test
    fun `卡片類型分類`() {
        assertEquals(CardType.VALUE_CARD, CardTypeClassifier.classify("商品卡 50 元"))
        assertEquals(CardType.PICKUP_CARD, CardTypeClassifier.classify("提貨卡 中杯美式咖啡 1 杯"))
        assertEquals(CardType.PICKUP_CARD, CardTypeClassifier.classify("禮品提領卡(免費兌換) 0014835"))
        assertEquals(CardType.VOUCHER, CardTypeClassifier.classify("現金抵用券 50 元"))
        assertEquals(CardType.GIFT_CARD, CardTypeClassifier.classify("禮品卡 感謝您的支持"))
        assertEquals(CardType.UNKNOWN, CardTypeClassifier.classify("請妥善保管本卡"))
    }
}
