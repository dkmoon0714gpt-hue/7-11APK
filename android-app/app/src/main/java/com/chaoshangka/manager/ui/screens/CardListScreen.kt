package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.EmptyState
import com.chaoshangka.manager.ui.components.GiftCardRow
import com.chaoshangka.manager.ui.components.StoreFilterRow
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.CardListViewModel

/** 全部卡片：搜尋（商品名／面額／條碼／卡號／備註）+ 超商篩選 + 未使用／已使用切換。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    container: AppContainer,
    onBack: (() -> Unit)?,
    onScan: () -> Unit,
    onOpenCard: (Long) -> Unit,
) {
    val viewModel: CardListViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "全部卡片",
                onBack = onBack,
                actions = {
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
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("搜尋商品名稱、面額、條碼、卡號、備註") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StoreFilterRow(selected = state.storeFilter, onSelect = viewModel::setStoreFilter)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.statusFilter == CardStatus.UNUSED,
                        onClick = { viewModel.setStatusFilter(CardStatus.UNUSED) },
                        label = { Text("未使用") },
                    )
                    FilterChip(
                        selected = state.statusFilter == CardStatus.USED,
                        onClick = { viewModel.setStatusFilter(CardStatus.USED) },
                        label = { Text("已使用") },
                    )
                }
            }
            if (state.cards.isEmpty()) {
                item {
                    EmptyState(
                        title = "沒有符合條件的卡片",
                        description = "可以切換狀態或清除搜尋關鍵字，或直接掃描新卡片。",
                        actionLabel = "掃描卡片",
                        onAction = onScan,
                    )
                }
            } else {
                items(state.cards, key = { it.id }) { card ->
                    GiftCardRow(
                        card = card,
                        today = state.today,
                        warningDays = state.warningDays,
                        onClick = { onOpenCard(card.id) },
                    )
                }
            }
        }
    }
}
