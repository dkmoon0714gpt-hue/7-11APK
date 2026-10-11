package com.chaoshangka.manager.ui.navigation

import com.chaoshangka.manager.domain.model.StoreType

object Routes {
    const val HOME = "home"
    const val CARDS = "cards"
    const val USED = "used"
    const val SETTINGS = "settings"
    const val STORE = "store/{storeKey}"
    const val CARD = "card/{cardId}"
    const val BARCODE = "barcode/{cardId}"
    const val SCAN = "scan"
    const val SCAN_RESULT = "scan_result"
    const val CATEGORIES = "categories"

    const val ARG_STORE_KEY = "storeKey"
    const val ARG_CARD_ID = "cardId"

    fun store(storeType: StoreType): String = "store/${storeType.key}"

    fun card(cardId: Long): String = "card/$cardId"

    fun barcode(cardId: Long): String = "barcode/$cardId"

    val bottomBarRoutes: Set<String> = setOf(HOME, CARDS, USED, SETTINGS)
}
