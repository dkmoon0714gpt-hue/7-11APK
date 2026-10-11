package com.chaoshangka.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaoshangka.manager.domain.ExpiryEvaluator
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.ProductGroup
import com.chaoshangka.manager.domain.model.StatsSummary
import com.chaoshangka.manager.domain.model.StoreSummary
import com.chaoshangka.manager.util.DateFormatters
import com.chaoshangka.manager.util.DenominationOptions
import com.chaoshangka.manager.ui.theme.ExpiredContainer
import com.chaoshangka.manager.ui.theme.ExpiredRed
import com.chaoshangka.manager.ui.theme.InfoContainer
import com.chaoshangka.manager.ui.theme.InfoBlue
import com.chaoshangka.manager.ui.theme.ValidContainer
import com.chaoshangka.manager.ui.theme.ValidGreen
import com.chaoshangka.manager.ui.theme.WarningContainer
import com.chaoshangka.manager.ui.theme.WarningOrange
import java.time.LocalDate

@Composable
fun StatTile(
    label: String,
    value: Int,
    container: Color,
    content: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(text = label, color = content, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(text = value.toString(), color = content, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun StatsOverview(summary: StatsSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "未使用",
                value = summary.unused,
                container = InfoContainer,
                content = InfoBlue,
                icon = Icons.Default.Inbox,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "已使用",
                value = summary.used,
                container = ValidContainer,
                content = ValidGreen,
                icon = Icons.Default.Check,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "已過期",
                value = summary.expired,
                container = ExpiredContainer,
                content = ExpiredRed,
                icon = Icons.Default.Warning,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "即將到期",
                value = summary.expiringSoon,
                container = WarningContainer,
                content = WarningOrange,
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun StoreSummaryCard(summary: StoreSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.storeType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("未使用 ${summary.unused}", style = MaterialTheme.typography.bodySmall)
                    Text("已使用 ${summary.used}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "已過期 ${summary.expired}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary.expired > 0) ExpiredRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "進入 ${summary.storeType.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GiftCardRow(
    card: GiftCard,
    today: LocalDate,
    warningDays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = ExpiryEvaluator.liveStatus(card, today, warningDays)
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.productName.ifBlank { "（未命名商品）" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DenominationOptions.display(card.denomination),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExpiryBadge(status = status, expiryDate = card.expiryDate, today = today)
                if (card.cardStatus == CardStatus.USED) {
                    CardStatusBadge(status = card.cardStatus, usedAt = card.usedAt)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(card.storeType.displayName)
                    if (card.productSpecification.isNotBlank()) append("・${card.productSpecification}")
                    append("・期限 ${DateFormatters.rangeDisplay(card.startDate, card.expiryDate)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            card.primaryBarcode?.let { barcode ->
                Text(
                    text = "${barcode.barcodeFormat.displayName}・${barcode.rawValue}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 商品群組卡片。未持有的項目（unusedCount == 0）只顯示為分類捷徑，不計入統計。
 */
@Composable
fun ProductGroupCard(
    group: ProductGroup,
    today: LocalDate,
    warningDays: Int,
    onCardClick: (GiftCard) -> Unit,
    onAddCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.productName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DenominationOptions.display(group.denomination),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(group.categoryName)
                    group.specification?.let { append("・$it") }
                    append("・未使用 ${group.unusedCount}・已使用 ${group.usedCount}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.hasExpired) {
                Spacer(Modifier.height(6.dp))
                ExpiryBadge(status = com.chaoshangka.manager.domain.model.ExpiryStatus.EXPIRED, expiryDate = null, today = today)
            }

            if (group.cards.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "目前沒有未使用的卡片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onAddCard) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新增")
                    }
                }
            } else {
                group.cards.forEach { card ->
                    Spacer(Modifier.height(8.dp))
                    GiftCardRow(
                        card = card,
                        today = today,
                        warningDays = warningDays,
                        onClick = { onCardClick(card) },
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 4.dp),
    )
}
