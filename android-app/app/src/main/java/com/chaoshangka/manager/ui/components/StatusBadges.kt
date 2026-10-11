package com.chaoshangka.manager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.ui.theme.ExpiredContainer
import com.chaoshangka.manager.ui.theme.ExpiredRed
import com.chaoshangka.manager.ui.theme.InfoContainer
import com.chaoshangka.manager.ui.theme.InfoBlue
import com.chaoshangka.manager.ui.theme.ReviewContainer
import com.chaoshangka.manager.ui.theme.ReviewPurple
import com.chaoshangka.manager.ui.theme.UsedContainer
import com.chaoshangka.manager.ui.theme.UsedGray
import com.chaoshangka.manager.ui.theme.ValidContainer
import com.chaoshangka.manager.ui.theme.ValidGreen
import com.chaoshangka.manager.ui.theme.WarningContainer
import com.chaoshangka.manager.ui.theme.WarningOrange
import com.chaoshangka.manager.util.DateFormatters
import java.time.LocalDate

/**
 * 狀態標籤：顏色 + 文字標籤並存，不依賴顏色單獨辨識。
 */
@Composable
fun StatusBadge(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        border = BorderStroke(1.dp, content.copy(alpha = 0.35f)),
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ExpiryBadge(
    status: ExpiryStatus,
    expiryDate: LocalDate?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val visuals = expiryVisuals(status, expiryDate, today)
    StatusBadge(
        label = visuals.label,
        container = visuals.container,
        content = visuals.content,
        modifier = modifier,
    )
}

@Composable
fun CardStatusBadge(status: CardStatus, usedAt: Long?, modifier: Modifier = Modifier) {
    when (status) {
        CardStatus.USED -> StatusBadge(
            label = "已使用 " + DateFormatters.dateTimeDisplay(usedAt),
            container = UsedContainer,
            content = UsedGray,
            modifier = modifier,
        )

        CardStatus.ARCHIVED -> StatusBadge(
            label = "已封存",
            container = UsedContainer,
            content = UsedGray,
            modifier = modifier,
        )

        CardStatus.UNUSED -> Unit
    }
}

data class BadgeVisuals(val label: String, val container: Color, val content: Color)

fun expiryVisuals(status: ExpiryStatus, expiryDate: LocalDate?, today: LocalDate): BadgeVisuals =
    when (status) {
        ExpiryStatus.EXPIRED -> BadgeVisuals("已過期", ExpiredContainer, ExpiredRed)

        ExpiryStatus.DUE_TODAY -> BadgeVisuals("今天到期", WarningContainer, WarningOrange)

        ExpiryStatus.EXPIRING_SOON -> BadgeVisuals(
            "即將到期・" + DateFormatters.remainingDaysText(expiryDate, today),
            WarningContainer,
            WarningOrange,
        )

        ExpiryStatus.VALID -> BadgeVisuals(
            "尚未到期・" + DateFormatters.remainingDaysText(expiryDate, today),
            ValidContainer,
            ValidGreen,
        )

        ExpiryStatus.NEEDS_REVIEW -> BadgeVisuals("期限待確認", ReviewContainer, ReviewPurple)

        ExpiryStatus.NO_EXPIRY -> BadgeVisuals("無期限", UsedContainer, UsedGray)
    }

@Composable
fun InfoBadge(label: String, modifier: Modifier = Modifier) {
    StatusBadge(label = label, container = InfoContainer, content = InfoBlue, modifier = modifier)
}

/**
 * 提醒：條碼辨識成功只代表程式讀得到條碼，不代表該卡可在超商兌換。
 */
@Composable
fun DisclaimerBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = InfoContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = InfoBlue,
            )
        }
    }
}
