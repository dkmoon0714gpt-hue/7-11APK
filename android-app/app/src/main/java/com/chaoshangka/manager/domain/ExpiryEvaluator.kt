package com.chaoshangka.manager.domain

import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.util.DateParser
import java.time.LocalDate

/**
 * 期限狀態一律以「本機今天」即時重算，不使用寫死在資料庫的舊狀態，
 * 確保每天開啟 APP 時過期／即將到期都是最新的。
 *
 * 「待確認」狀態會保留，避免被誤認為沒有期限。
 */
object ExpiryEvaluator {

    fun liveStatus(card: GiftCard, today: LocalDate, warningDays: Int): ExpiryStatus =
        DateParser.evaluate(
            start = card.startDate,
            end = card.expiryDate,
            needsReview = card.expiryStatus == ExpiryStatus.NEEDS_REVIEW,
            today = today,
            warningDays = warningDays,
        )
}
