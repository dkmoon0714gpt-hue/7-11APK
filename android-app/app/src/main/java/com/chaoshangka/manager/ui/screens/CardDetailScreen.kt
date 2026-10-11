package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.Barcode
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.BarcodeView
import com.chaoshangka.manager.ui.components.CardStatusBadge
import com.chaoshangka.manager.ui.components.CardTypeSelector
import com.chaoshangka.manager.ui.components.CategorySelector
import com.chaoshangka.manager.ui.components.DenominationSelector
import com.chaoshangka.manager.ui.components.ExpiryBadge
import com.chaoshangka.manager.ui.components.ExpiryInput
import com.chaoshangka.manager.ui.components.OptionDialog
import com.chaoshangka.manager.ui.components.decodePhoto
import com.chaoshangka.manager.ui.components.parseExpiryInput
import com.chaoshangka.manager.util.DateFormatters
import com.chaoshangka.manager.util.DenominationOptions
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.CardDetailViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    container: AppContainer,
    cardId: Long,
    onBack: () -> Unit,
    onOpenFullscreen: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: CardDetailViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var showUseConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) { viewModel.setCardId(cardId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val card = state.card

    Scaffold(
        topBar = {
            AppTopBar(
                title = card?.productName?.ifBlank { "卡片詳細" } ?: "卡片詳細",
                onBack = onBack,
                actions = {
                    if (card != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "編輯")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "刪除")
                        }
                    }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (card == null) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(text = if (state.loading) "載入中…" else "找不到這張卡片")
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = card.productName.ifBlank { "（未命名商品）" }, style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpiryBadge(status = state.liveStatus, expiryDate = card.expiryDate, today = state.today)
                            CardStatusBadge(status = card.cardStatus, usedAt = card.usedAt)
                        }
                        HorizontalDivider()
                        InfoRow("超商", card.storeType.displayName)
                        InfoRow("商品分類", state.categoryName ?: "未分類")
                        InfoRow("面額", DenominationOptions.display(card.denomination))
                        InfoRow("商品規格", card.productSpecification.ifBlank { "—" })
                        InfoRow("數量", card.quantity.toString())
                        InfoRow("卡片類型", card.cardType.displayName)
                        InfoRow("有效期限", DateFormatters.rangeDisplay(card.startDate, card.expiryDate))
                        InfoRow("卡號", card.cardNumber ?: "—")
                        InfoRow("新增日期", DateFormatters.dateTimeDisplay(card.createdAt))
                        InfoRow("備註", card.notes.ifBlank { "—" })
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenFullscreen(card.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.Fullscreen, contentDescription = null)
                        Text(text = " 全螢幕條碼")
                    }
                }
            }

            item {
                if (card.cardStatus == CardStatus.UNUSED) {
                    Button(
                        onClick = { showUseConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("使用") }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.markUnused() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("恢復為未使用") }
                }
            }

            item { Text("條碼（可指定主要兌換條碼）", style = MaterialTheme.typography.titleSmall) }

            if (card.barcodes.isEmpty()) {
                item {
                    Text(
                        text = "這張卡片沒有條碼資料，請使用編輯功能補登。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            items(card.barcodes, key = { it.id }) { barcode ->
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${barcode.barcodeFormat.displayName}・${barcode.recognitionStatus.displayName}" +
                                if (barcode.isPrimary) "・主要條碼" else "",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        BarcodeView(barcode = barcode, photoPath = card.originalImagePath)
                        if (!barcode.isPrimary) {
                            TextButton(onClick = { viewModel.setPrimaryBarcode(barcode.id) }) {
                                Text("設為主要兌換條碼")
                            }
                        }
                    }
                }
            }

            if (card.barcodes.size > 1) {
                item {
                    Text(
                        text = "提醒：請在超商櫃檯確認哪一組條碼才是店員要刷的條碼。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            card.originalImagePath?.let { path ->
                item {
                    Text("原始卡片照片", style = MaterialTheme.typography.titleSmall)
                }
                item { OriginalPhoto(path = path) }
            }
        }
    }

    if (showUseConfirm) {
        AlertDialog(
            onDismissRequest = { showUseConfirm = false },
            title = { Text("確認將此卡片標記為已使用？") },
            text = {
                Text(
                    "這只會記錄你確認使用的時間，APP 不會連線超商系統，也不代表已完成兌換或驗證卡片真偽。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUseConfirm = false
                    viewModel.markUsed()
                }) { Text("確認使用") }
            },
            dismissButton = {
                TextButton(onClick = { showUseConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("確定要刪除這張卡片？") },
            text = { Text("刪除後卡片資料、條碼與照片都會從本機移除，無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted)
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showEdit && card != null) {
        CardEditDialog(
            card = card,
            categories = state.categories,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                viewModel.updateCard(updated)
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun OriginalPhoto(path: String) {
    val bitmap = remember(path) { decodePhoto(path, maxSize = 1400) }
    val image = bitmap
    if (image == null) {
        Text("照片檔案不存在", style = MaterialTheme.typography.bodySmall)
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "原始卡片照片",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 編輯卡片：所有辨識欄位都可手動修改。 */
@Composable
private fun CardEditDialog(
    card: GiftCard,
    categories: List<ProductCategory>,
    onDismiss: () -> Unit,
    onSave: (GiftCard) -> Unit,
) {
    var productName by remember { mutableStateOf(card.productName) }
    var specification by remember { mutableStateOf(card.productSpecification) }
    var notes by remember { mutableStateOf(card.notes) }
    var cardNumber by remember { mutableStateOf(card.cardNumber.orEmpty()) }
    var denomination by remember { mutableStateOf(card.denomination) }
    var categoryId by remember { mutableStateOf(card.categoryId) }
    var cardType by remember { mutableStateOf(card.cardType) }
    var expiryText by remember {
        mutableStateOf(if (card.startDate == null && card.expiryDate == null) "" else DateFormatters.rangeDisplay(card.startDate, card.expiryDate))
    }
    var needsReview by remember { mutableStateOf(card.expiryStatus == com.chaoshangka.manager.domain.model.ExpiryStatus.NEEDS_REVIEW) }
    var barcodeRaw by remember { mutableStateOf(card.barcodes.firstOrNull()?.rawValue.orEmpty()) }
    var barcodeFormat by remember { mutableStateOf(card.barcodes.firstOrNull()?.barcodeFormat ?: BarcodeFormatType.CODE_128) }
    var formatDialog by remember { mutableStateOf(false) }

    val parsed = parseExpiryInput(expiryText, needsReview)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯卡片") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("商品名稱") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DenominationSelector(denomination = denomination, onSelect = { denomination = it })
                CategorySelector(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it?.id },
                )
                OutlinedTextField(
                    value = specification,
                    onValueChange = { specification = it },
                    label = { Text("商品規格（例如：中杯・冰熱不拘・1 杯）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                CardTypeSelector(selected = cardType, onSelect = { cardType = it })
                ExpiryInput(
                    dateText = expiryText,
                    needsReview = parsed.third,
                    parsedStart = parsed.first,
                    parsedEnd = parsed.second,
                    onDateTextChange = { expiryText = it },
                    onNeedsReviewChange = { needsReview = it },
                )
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    label = { Text("卡號（與條碼不同）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("備註") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = barcodeRaw,
                    onValueChange = { barcodeRaw = it },
                    label = { Text("主要條碼內容（文字，前導零會保留）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { formatDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("條碼格式：${barcodeFormat.displayName}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val primaryBarcode = if (barcodeRaw.isBlank()) {
                    card.barcodes
                } else {
                    listOf(
                        Barcode(
                            id = card.primaryBarcode?.id ?: 0L,
                            giftCardId = card.id,
                            barcodeFormat = barcodeFormat,
                            rawValue = barcodeRaw,
                            isPrimary = true,
                            recognitionStatus = RecognitionStatus.MANUAL,
                            createdAt = card.primaryBarcode?.createdAt ?: System.currentTimeMillis(),
                        ),
                    ) + card.barcodes.filter { !it.isPrimary }
                }
                onSave(
                    card.copy(
                        productName = productName,
                        productSpecification = specification,
                        notes = notes,
                        cardNumber = cardNumber.ifBlank { null },
                        denomination = denomination,
                        categoryId = categoryId,
                        cardType = cardType,
                        startDate = parsed.first,
                        expiryDate = parsed.second,
                        expiryStatus = if (parsed.third) {
                            com.chaoshangka.manager.domain.model.ExpiryStatus.NEEDS_REVIEW
                        } else {
                            com.chaoshangka.manager.domain.model.ExpiryStatus.VALID
                        },
                        barcodes = primaryBarcode,
                    ),
                )
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (formatDialog) {
        OptionDialog(
            title = "條碼格式",
            options = BarcodeFormatType.entries,
            labelOf = { it.displayName },
            onSelect = {
                barcodeFormat = it
                formatDialog = false
            },
            onDismiss = { formatDialog = false },
        )
    }
}
