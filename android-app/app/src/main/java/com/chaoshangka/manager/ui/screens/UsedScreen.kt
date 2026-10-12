package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.EmptyState
import com.chaoshangka.manager.ui.components.GiftCardRow
import com.chaoshangka.manager.ui.components.SearchField
import com.chaoshangka.manager.ui.components.StoreFilterRow
import com.chaoshangka.manager.util.DateParser
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.UsedViewModel

/**
 * 已使用清單：預設依使用時間新到舊，可搜尋、依超商與使用日期篩選。
 * 已使用的卡片會永久保留在紀錄中，只有使用者明確刪除才會消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsedScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    onOpenCard: (Long) -> Unit,
) {
    val viewModel: UsedViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "已使用", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = "搜尋已使用的卡片",
                )
            }
            item {
                OutlinedTextField(
                    value = state.dateFilter?.toString().orEmpty(),
                    onValueChange = { text ->
                        viewModel.setDateFilter(DateParser.parse(text).end)
                    },
                    label = { Text("依使用日期篩選（例：2026/10/09）") },
                    trailingIcon = {
                        TextButton(onClick = { viewModel.setDateFilter(null) }) { Text("清除") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { StoreFilterRow(selected = state.storeFilter, onSelect = viewModel::setStoreFilter) }

            if (state.cards.isEmpty()) {
                item {
                    EmptyState(
                        title = "目前沒有已使用的卡片",
                        description = "在卡片詳細頁按下「使用」後，紀錄會保留在這裡。",
                    )
                }
            } else {
                items(state.cards, key = { it.id }) { card ->
                    GiftCardRow(
                        card = card,
                        today = state.today,
                        warningDays = 7,
                        onClick = { onOpenCard(card.id) },
                    )
                }
            }
        }
    }
}
