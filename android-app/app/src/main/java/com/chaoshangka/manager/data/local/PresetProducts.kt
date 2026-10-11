package com.chaoshangka.manager.data.local

import com.chaoshangka.manager.domain.model.StoreType

/** 超商頁「尚無卡片」區用的預設商品項目。這些只是分類捷徑，不代表使用者真的持有卡片。 */
data class PresetProduct(
    val productName: String,
    val denomination: Int?,
    val specification: String?,
    val categoryName: String,
)

object PresetProducts {

    const val CATEGORY_COFFEE = "咖啡"
    const val CATEGORY_TEA = "茶飲"
    const val CATEGORY_OTHER_DRINK = "其他飲料"
    const val CATEGORY_WATER = "水及機能飲品"
    const val CATEGORY_MILK = "乳品及豆漿"
    const val CATEGORY_BAKERY = "麵包及烘焙食品"
    const val CATEGORY_RICE = "飯糰及壽司"
    const val CATEGORY_SANDWICH = "三明治及輕食"
    const val CATEGORY_BENTO = "便當及熟食"
    const val CATEGORY_CHICKEN = "雞胸肉及肉類食品"
    const val CATEGORY_EGG = "茶葉蛋及其他蛋類"
    const val CATEGORY_SNACK = "零食及餅乾"
    const val CATEGORY_DESSERT = "甜點及蛋糕"
    const val CATEGORY_ICE = "冰品"
    const val CATEGORY_NOODLE = "泡麵及即食食品"
    const val CATEGORY_FRESH = "生鮮及冷藏食品"
    const val CATEGORY_DAILY = "日用品"
    const val CATEGORY_VALUE_CARD = "商品卡／禮物卡"
    const val CATEGORY_VOUCHER = "抵用券／兌換券"
    const val CATEGORY_OTHER = "其他"

    /** 20 項通用商品分類（依預設順序）。 */
    val COMMON_CATEGORIES: List<String> = listOf(
        CATEGORY_COFFEE,
        CATEGORY_TEA,
        CATEGORY_OTHER_DRINK,
        CATEGORY_WATER,
        CATEGORY_MILK,
        CATEGORY_BAKERY,
        CATEGORY_RICE,
        CATEGORY_SANDWICH,
        CATEGORY_BENTO,
        CATEGORY_CHICKEN,
        CATEGORY_EGG,
        CATEGORY_SNACK,
        CATEGORY_DESSERT,
        CATEGORY_ICE,
        CATEGORY_NOODLE,
        CATEGORY_FRESH,
        CATEGORY_DAILY,
        CATEGORY_VALUE_CARD,
        CATEGORY_VOUCHER,
        CATEGORY_OTHER,
    )

    /** 各超商常見的商品卡面額。 */
    private val VALUE_CARD_DENOMINATIONS = listOf(35, 50, 100, 200, 300, 500, 1000)

    private fun valueCards(): List<PresetProduct> = VALUE_CARD_DENOMINATIONS.map { amount ->
        PresetProduct(
            productName = "商品卡 $amount 元",
            denomination = amount,
            specification = null,
            categoryName = CATEGORY_VALUE_CARD,
        )
    }

    fun itemsFor(store: StoreType): List<PresetProduct> = when (store) {
        StoreType.SEVEN_ELEVEN -> valueCards() + listOf(
            PresetProduct("CITY CAFE 美式咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("CITY CAFE 拿鐵咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("CITY TEA 現萃茶（中杯）", null, "中杯・冰熱不拘", CATEGORY_TEA),
            PresetProduct("茶葉蛋", null, "1 顆", CATEGORY_EGG),
            PresetProduct("雞胸肉", null, "1 份", CATEGORY_CHICKEN),
            PresetProduct("御飯糰", null, "1 個", CATEGORY_RICE),
            PresetProduct("三明治", null, "1 個", CATEGORY_SANDWICH),
            PresetProduct("便當", null, "1 份", CATEGORY_BENTO),
            PresetProduct("冰品", null, "1 份", CATEGORY_ICE),
            PresetProduct("泡麵", null, "1 碗", CATEGORY_NOODLE),
        )

        StoreType.FAMILYMART -> valueCards() + listOf(
            PresetProduct("Let's Café 經典美式（小杯）", null, "小杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("Let's Café 經典拿鐵（小杯）", null, "小杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("FamilyMart 禮品提領卡（免費兌換）", null, "指定商品 1 件", CATEGORY_VOUCHER),
            PresetProduct("茶葉蛋", null, "1 顆", CATEGORY_EGG),
            PresetProduct("雞胸肉", null, "1 份", CATEGORY_CHICKEN),
            PresetProduct("飯糰", null, "1 個", CATEGORY_RICE),
            PresetProduct("三明治", null, "1 個", CATEGORY_SANDWICH),
            PresetProduct("便當", null, "1 份", CATEGORY_BENTO),
            PresetProduct("冰品", null, "1 份", CATEGORY_ICE),
            PresetProduct("泡麵", null, "1 碗", CATEGORY_NOODLE),
        )

        StoreType.HILIFE -> valueCards() + listOf(
            PresetProduct("Hi Café 美式咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("Hi Café 拿鐵咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("茶葉蛋", null, "1 顆", CATEGORY_EGG),
            PresetProduct("雞胸肉", null, "1 份", CATEGORY_CHICKEN),
            PresetProduct("飯糰", null, "1 個", CATEGORY_RICE),
            PresetProduct("三明治", null, "1 個", CATEGORY_SANDWICH),
            PresetProduct("冰品", null, "1 份", CATEGORY_ICE),
            PresetProduct("泡麵", null, "1 碗", CATEGORY_NOODLE),
        )

        StoreType.OKMART -> valueCards() + listOf(
            PresetProduct("OK Café 美式咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("OK Café 拿鐵咖啡（中杯）", null, "中杯・冰熱不拘", CATEGORY_COFFEE),
            PresetProduct("茶葉蛋", null, "1 顆", CATEGORY_EGG),
            PresetProduct("雞胸肉", null, "1 份", CATEGORY_CHICKEN),
            PresetProduct("飯糰", null, "1 個", CATEGORY_RICE),
            PresetProduct("三明治", null, "1 個", CATEGORY_SANDWICH),
            PresetProduct("冰品", null, "1 份", CATEGORY_ICE),
        )

        StoreType.UNKNOWN -> emptyList()
    }
}
