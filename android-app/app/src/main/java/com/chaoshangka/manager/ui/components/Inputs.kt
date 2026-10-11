package com.chaoshangka.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.util.DateFormatters
import com.chaoshangka.manager.util.DateParser
import com.chaoshangka.manager.util.DenominationOptions
import java.time.LocalDate

@Composable
fun StoreFilterRow(
    selected: StoreType?,
    onSelect: (StoreType?) -> Unit,
    includeAll: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (includeAll) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") },
            )
        }
        StoreType.mainStores.forEach { store ->
            FilterChip(
                selected = selected == store,
                onClick = { onSelect(store) },
                label = { Text(store.displayName) },
            )
        }
    }
}

@Composable
fun StoreSelector(
    selected: StoreType,
    onSelect: (StoreType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedButton(onClick = { dialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("超商：${selected.displayName}" + if (selected == StoreType.UNKNOWN) "（待確認）" else "")
        }
    }
    if (dialog) {
        OptionDialog(
            title = "選擇超商",
            options = StoreType.mainStores + StoreType.UNKNOWN,
            labelOf = { it.displayName },
            onSelect = {
                onSelect(it)
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}

@Composable
fun DenominationSelector(
    denomination: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    OutlinedButton(onClick = { dialog = true }, modifier = modifier.fillMaxWidth()) {
        Text("面額：${DenominationOptions.display(denomination)}")
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("選擇面額") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DenominationOptions.PRESETS.chunked(6).forEach { row ->
                            Column {
                                row.forEach { amount ->
                                    TextButton(onClick = {
                                        onSelect(amount)
                                        dialog = false
                                    }) { Text("$amount 元") }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input -> customText = input.filter { it.isDigit() } },
                        label = { Text("其他金額") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            customText.toIntOrNull()?.let {
                                onSelect(it)
                                dialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("使用自訂金額") }
                    TextButton(onClick = {
                        onSelect(null)
                        dialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(DenominationOptions.LABEL_SPECIFIC_PRODUCT) }
                    TextButton(onClick = {
                        onSelect(null)
                        dialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(DenominationOptions.LABEL_NO_DENOMINATION) }
                    TextButton(onClick = {
                        onSelect(null)
                        dialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(DenominationOptions.LABEL_PENDING) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
fun CategorySelector(
    categories: List<ProductCategory>,
    selectedId: Long?,
    onSelect: (ProductCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.categoryName
    OutlinedButton(onClick = { dialog = true }, modifier = modifier.fillMaxWidth()) {
        Text("商品分類：${selectedName ?: "未選擇"}")
    }
    if (dialog) {
        OptionDialog(
            title = "選擇商品分類",
            options = categories,
            labelOf = { category ->
                buildString {
                    append(category.categoryName)
                    category.denomination?.let { append("・$it 元") }
                    if (category.storeType != null) append("（${category.storeType.displayName}）")
                }
            },
            onSelect = {
                onSelect(it)
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}

@Composable
fun CardTypeSelector(
    selected: CardType,
    onSelect: (CardType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { dialog = true }, modifier = modifier.fillMaxWidth()) {
        Text("卡片類型：${selected.displayName}")
    }
    if (dialog) {
        OptionDialog(
            title = "選擇卡片類型",
            options = CardType.entries,
            labelOf = { it.displayName },
            onSelect = {
                onSelect(it)
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}

/**
 * 有效期限輸入：可直接輸入卡片上的日期文字（民國年、西元年、日期區間皆可），
 * 由 [DateParser] 解析；解析不出來時標示「待確認」。
 */
@Composable
fun ExpiryInput(
    dateText: String,
    needsReview: Boolean,
    parsedStart: LocalDate?,
    parsedEnd: LocalDate?,
    onDateTextChange: (String) -> Unit,
    onNeedsReviewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = dateText,
            onValueChange = onDateTextChange,
            label = { Text("有效期限（例：2029/06/30 或 2029/06/30~2030/06/30 或 118/06/30）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            FilterChip(
                selected = needsReview,
                onClick = { onNeedsReviewChange(!needsReview) },
                label = { Text("期限待確認") },
            )
            Text(
                text = if (parsedEnd == null) {
                    "尚未取得可用的到期日"
                } else {
                    "解析為 ${DateFormatters.rangeDisplay(parsedStart, parsedEnd)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 解析使用者輸入的日期文字；回傳 (start, end, needsReview)。 */
fun parseExpiryInput(text: String, explicitNeedsReview: Boolean): Triple<LocalDate?, LocalDate?, Boolean> {
    val result = DateParser.parse(text)
    val needsReview = explicitNeedsReview || (text.isNotBlank() && result.end == null)
    return Triple(result.start, result.end, needsReview)
}

@Composable
fun <T> OptionDialog(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    TextButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(labelOf(option))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
