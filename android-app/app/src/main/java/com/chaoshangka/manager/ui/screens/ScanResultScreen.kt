package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.BarcodeView
import com.chaoshangka.manager.ui.components.CardTypeSelector
import com.chaoshangka.manager.ui.components.CategorySelector
import com.chaoshangka.manager.ui.components.DenominationSelector
import com.chaoshangka.manager.ui.components.ExpiryInput
import com.chaoshangka.manager.ui.components.OptionDialog
import com.chaoshangka.manager.ui.components.decodePhoto
import com.chaoshangka.manager.ui.components.parseExpiryInput
import com.chaoshangka.manager.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 辨識結果確認頁。
 *
 * 所有欄位都可修改；辨識不到的欄位保持空白並標示「待確認」。
 * 儲存前會檢查是否已有相同條碼的卡片，只警告不強制阻止。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    container: AppContainer,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenExistingCard: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var categories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    var formatDialogIndex by remember { mutableStateOf<Int?>(null) }
    var dateText by remember { mutableStateOf("") }

    LaunchedEffect(state.draft.storeType) {
        categories = if (state.draft.storeType == StoreType.UNKNOWN) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { container.categoryRepository.selectableCategories(state.draft.storeType) }
        }
    }

    LaunchedEffect(state.draft.startDate, state.draft.expiryDate) {
        val start = state.draft.startDate
        val end = state.draft.expiryDate
        dateText = when {
            start == null && end == null -> ""
            start == end -> end?.toString().orEmpty()
            start == null -> end?.toString().orEmpty()
            end == null -> start.toString()
            else -> "$start~$end"
        }
    }

    LaunchedEffect(state.savedCardId) {
        state.savedCardId?.let { onSaved(it) }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "確認辨識結果", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(padding),
        ) {
            if (state.analyzing) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("正在本機辨識文字與條碼…（不需要網路）")
                    }
                }
            }

            state.photoPath?.let { path ->
                item {
                    val bitmap = remember(path) { decodePhoto(path, maxSize = 900) }
                    val image = bitmap
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "拍攝的卡片照片",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.warnings.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("辨識提醒", style = MaterialTheme.typography.titleSmall)
                            state.warnings.forEach { warning ->
                                Text("・$warning", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (state.duplicates.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "此條碼可能已存在於卡片清單，請確認是否重複新增。",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            state.duplicates.forEach { existing ->
                                Text(
                                    text = "既有卡片：${existing.productName}（${existing.storeType.displayName}）",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(onClick = {
                                state.duplicates.firstOrNull()?.let { onOpenExistingCard(it.id) }
                            }) { Text("查看既有卡片") }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("卡片資料（全部可手動修改）", style = MaterialTheme.typography.titleSmall)
                    StoreSelectorInline(
                        selected = state.draft.storeType,
                        onSelect = { viewModel.updateDraft { draft -> draft.copy(storeType = it) } },
                    )
                    OutlinedTextField(
                        value = state.draft.productName,
                        onValueChange = { value ->
                            viewModel.updateDraft { draft -> draft.copy(productName = value) }
                        },
                        label = { Text("商品名稱" + if (state.draft.productName.isBlank()) "（待確認，請手動輸入）" else "") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DenominationSelector(
                        denomination = state.draft.denomination,
                        onSelect = { value -> viewModel.updateDraft { draft -> draft.copy(denomination = value) } },
                    )
                    CategorySelector(
                        categories = categories,
                        selectedId = state.draft.categoryId,
                        onSelect = { value -> viewModel.updateDraft { draft -> draft.copy(categoryId = value?.id) } },
                    )
                    OutlinedTextField(
                        value = state.draft.productSpecification,
                        onValueChange = { value ->
                            viewModel.updateDraft { draft -> draft.copy(productSpecification = value) }
                        },
                        label = { Text("商品規格（例如：中杯・冰熱不拘・1 杯）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CardTypeSelector(
                        selected = state.draft.cardType,
                        onSelect = { value -> viewModel.updateDraft { draft -> draft.copy(cardType = value) } },
                    )
                    ExpiryInput(
                        dateText = dateText,
                        needsReview = state.draft.expiryNeedsReview,
                        parsedStart = state.draft.startDate,
                        parsedEnd = state.draft.expiryDate,
                        onDateTextChange = { text ->
                            dateText = text
                            val parsed = parseExpiryInput(text, state.draft.expiryNeedsReview)
                            viewModel.updateDraft { draft ->
                                draft.copy(
                                    startDate = parsed.first,
                                    expiryDate = parsed.second,
                                    expiryNeedsReview = parsed.third,
                                )
                            }
                        },
                        onNeedsReviewChange = { review ->
                            viewModel.updateDraft { draft -> draft.copy(expiryNeedsReview = review) }
                        },
                    )
                    OutlinedTextField(
                        value = state.draft.cardNumber.orEmpty(),
                        onValueChange = { value ->
                            viewModel.updateDraft { draft -> draft.copy(cardNumber = value.ifBlank { null }) }
                        },
                        label = { Text("卡號（若與條碼不同）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.draft.notes,
                        onValueChange = { value -> viewModel.updateDraft { draft -> draft.copy(notes = value) } },
                        label = { Text("備註") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("條碼（${state.draft.barcodes.size} 組，可指定主要條碼）", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        viewModel.updateBarcodes { list ->
                            list + BarcodeDraft(
                                barcodeFormat = BarcodeFormatType.CODE_128,
                                rawValue = "",
                                recognitionStatus = RecognitionStatus.MANUAL,
                                isPrimary = list.isEmpty(),
                            )
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Text("新增")
                    }
                }
            }

            itemsIndexed(state.draft.barcodes) { index, draft ->
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = draft.isPrimary,
                                onClick = {
                                    viewModel.updateBarcodes { list ->
                                        list.mapIndexed { i, item -> item.copy(isPrimary = i == index) }
                                    }
                                },
                                label = { Text("主要條碼") },
                            )
                            FilterChip(
                                selected = draft.recognitionStatus == RecognitionStatus.UNREADABLE,
                                onClick = {
                                    viewModel.updateBarcodes { list ->
                                        val status = if (draft.recognitionStatus == RecognitionStatus.UNREADABLE) {
                                            RecognitionStatus.RECOGNIZED
                                        } else {
                                            RecognitionStatus.UNREADABLE
                                        }
                                        list.mapIndexed { i, item -> if (i == index) item.copy(recognitionStatus = status) else item }
                                    }
                                },
                                label = { Text("無法讀取") },
                            )
                            IconButton(onClick = {
                                viewModel.updateBarcodes { list -> list.filterIndexed { i, _ -> i != index } }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "刪除條碼")
                            }
                        }
                        OutlinedTextField(
                            value = draft.rawValue,
                            onValueChange = { value ->
                                viewModel.updateBarcodes { list ->
                                    list.mapIndexed { i, item ->
                                        if (i == index) {
                                            item.copy(rawValue = value, recognitionStatus = RecognitionStatus.MANUAL)
                                        } else {
                                            item
                                        }
                                    }
                                }
                            },
                            label = { Text("條碼內容（文字，前導零會保留）") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = { formatDialogIndex = index }, modifier = Modifier.fillMaxWidth()) {
                            Text("格式：${draft.barcodeFormat.displayName}")
                        }
                        if (draft.rawValue.isNotBlank()) {
                            BarcodeView(
                                barcode = com.chaoshangka.manager.domain.model.Barcode(
                                    barcodeFormat = draft.barcodeFormat,
                                    rawValue = draft.rawValue,
                                    recognitionStatus = draft.recognitionStatus,
                                    isPrimary = draft.isPrimary,
                                ),
                                photoPath = state.photoPath,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "提醒：條碼能被讀取，不代表該卡片一定可以在超商兌換；APP 不會查詢餘額或驗證真偽。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.analyzing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("儲存卡片") }
            }

            item {
                Text(
                    text = "照片與辨識結果都只存在這支手機（App 私有目錄 + Room 資料庫），不會上傳。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    formatDialogIndex?.let { index ->
        OptionDialog(
            title = "條碼格式",
            options = BarcodeFormatType.entries,
            labelOf = { it.displayName },
            onSelect = { format ->
                viewModel.updateBarcodes { list ->
                    list.mapIndexed { i, item -> if (i == index) item.copy(barcodeFormat = format) else item }
                }
                formatDialogIndex = null
            },
            onDismiss = { formatDialogIndex = null },
        )
    }
}

@Composable
private fun StoreSelectorInline(selected: StoreType, onSelect: (StoreType) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { dialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text("超商：${selected.displayName}")
    }
    if (dialog) {
        OptionDialog(
            title = "選擇超商",
            options = StoreType.mainStores,
            labelOf = { it.displayName },
            onSelect = {
                onSelect(it)
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}
