package com.chaoshangka.manager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.backup.BackupInspection
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.GroupSortOrder
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.DisclaimerBanner
import com.chaoshangka.manager.ui.components.FilterChipsRow
import com.chaoshangka.manager.ui.components.SectionTitle
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.SettingsViewModel

/**
 * 設定：提醒門檻、排序、備份還原、資料位置、權限與免責聲明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: (() -> Unit)?,
    onOpenCategories: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var inspection by remember { mutableStateOf<BackupInspection?>(null) }
    var warningDaysText by remember { mutableStateOf(state.warningDays.toString()) }
    var farWarningDaysText by remember { mutableStateOf(state.farWarningDays.toString()) }

    LaunchedEffect(state.warningDays) { warningDaysText = state.warningDays.toString() }
    LaunchedEffect(state.farWarningDays) { farWarningDaysText = state.farWarningDays.toString() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            viewModel.inspectBackup(uri) { result -> inspection = result }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "設定", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(padding),
        ) {
            item { SectionTitle("到期提醒") }
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "「即將到期」的判斷天數。只有日期而沒有時間時，一律以卡片上標示的日期判斷。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = warningDaysText,
                            onValueChange = { text ->
                                warningDaysText = text.filter { it.isDigit() }
                                warningDaysText.toIntOrNull()?.let { viewModel.setWarningDays(it) }
                            },
                            label = { Text("即將到期提醒（天，預設 7）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = farWarningDaysText,
                            onValueChange = { text ->
                                farWarningDaysText = text.filter { it.isDigit() }
                                farWarningDaysText.toIntOrNull()?.let { viewModel.setFarWarningDays(it) }
                            },
                            label = { Text("較長提醒區間（天，預設 30）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SectionTitle("超商頁商品排序") }
            item {
                FilterChipsRow(
                    options = GroupSortOrder.entries,
                    selected = state.sortOrder,
                    labelOf = { it.displayName },
                    onSelect = { viewModel.setSortOrder(it) },
                )
            }

            item { SectionTitle("分類管理") }
            item {
                OutlinedButton(onClick = onOpenCategories, modifier = Modifier.fillMaxWidth()) {
                    Text("管理商品分類")
                }
            }

            item { SectionTitle("備份與還原") }
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "備份檔為 ZIP，內含卡片資料、條碼、自訂分類與原始照片。內容包含卡號等敏感資訊，請妥善保管。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { exportLauncher.launch("chaoshangka-backup.zip") },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("匯出備份") }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("匯入備份") }
                    }
                }
            }

            item { SectionTitle("資料與隱私") }
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "・資料位置：App 私有目錄（Room 資料庫 chaoshangka.db 與 card_photos/ 照片）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "・不使用帳號、不登入、不連線雲端；APP 未宣告網路權限，系統層面即無法連網。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "・要求權限：" + (state.requestedPermissions.joinToString("、").ifBlank { "無" }),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "・解除安裝後，本機資料與照片會一併被系統刪除；請先匯出備份。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "・版本：" + state.appVersion,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                DisclaimerBanner(
                    text = "本 APP 為個人卡片管理工具，不提供兌換、餘額查詢或真偽驗證功能，" +
                        "也不保證辨識結果完全正確；實際兌換仍以超商系統與實體卡片為準。",
                )
            }
        }
    }

    inspection?.let { result ->
        AlertDialog(
            onDismissRequest = { inspection = null },
            title = { Text("準備匯入備份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("備份版本：${result.manifest.backupVersion}（APP ${result.manifest.appVersion}）")
                    Text("卡片 ${result.cardCount} 張、條碼 ${result.barcodeCount} 組、分類 ${result.categoryCount} 項")
                    if (result.potentialDuplicates > 0) {
                        Text(
                            text = "偵測到 ${result.potentialDuplicates} 組條碼可能已存在於清單中，匯入時可選擇略過重複卡片。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    result.problems.forEach { problem -> Text("・$problem", color = MaterialTheme.colorScheme.error) }
                    Text(
                        text = "匯入會新增資料，不會刪除原有卡片；過程中若失敗會整批取消。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { uri -> viewModel.importBackup(uri, skipDuplicates = true) }
                        inspection = null
                        pendingImportUri = null
                    },
                ) { Text("略過重複並匯入") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingImportUri?.let { uri -> viewModel.importBackup(uri, skipDuplicates = false) }
                            inspection = null
                            pendingImportUri = null
                        },
                    ) { Text("全部匯入") }
                    TextButton(onClick = {
                        inspection = null
                        pendingImportUri = null
                    }) { Text("取消") }
                }
            },
        )
    }
}
