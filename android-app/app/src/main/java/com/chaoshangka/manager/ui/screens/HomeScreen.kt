package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.DisclaimerBanner
import com.chaoshangka.manager.ui.components.SectionTitle
import com.chaoshangka.manager.ui.components.StatsOverview
import com.chaoshangka.manager.ui.components.StoreSummaryCard
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onScan: () -> Unit,
    onManualAdd: () -> Unit,
    onOpenStore: (StoreType) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUsed: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "超商卡管家",
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "搜尋")
                    }
                    IconButton(onClick = onScan) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "掃描卡片")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            item { SectionTitle("統計") }
            item { StatsOverview(summary = state.summary) }
            item { SectionTitle("四大超商") }
            items(state.stores, key = { it.storeType.key }) { store ->
                StoreSummaryCard(summary = store, onClick = { onOpenStore(store.storeType) })
            }
            item {
                OutlinedButton(onClick = onManualAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("不拍照，手動新增卡片")
                }
            }
            item {
                OutlinedButton(onClick = onOpenUsed, modifier = Modifier.fillMaxWidth()) {
                    Text("查看已使用紀錄")
                }
            }
            item {
                DisclaimerBanner(
                    text = "本 APP 完全離線運作（無網路權限）：卡片資料、照片與辨識都在手機本機處理。" +
                        "「使用」只代表你確認已使用，APP 不會連線超商系統驗證兌換結果或卡片真偽。",
                )
            }
        }
    }
}
