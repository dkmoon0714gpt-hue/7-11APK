package com.chaoshangka.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.GroupSortOrder
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.EmptyState
import com.chaoshangka.manager.ui.components.FilterChipsRow
import com.chaoshangka.manager.ui.components.InfoBadge
import com.chaoshangka.manager.ui.components.ProductGroupCard
import com.chaoshangka.manager.ui.components.SearchField
import com.chaoshangka.manager.ui.components.SectionTitle
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.StoreViewModel

/**
 * 超商頁：上方統計與搜尋，下方分「已有卡片」與「尚無卡片」兩區。
 * 「尚無卡片」只是分類捷徑，不計入未使用卡片總數。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    container: AppContainer,
    storeType: StoreType,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenCard: (Long) -> Unit,
) {
    val viewModel: StoreViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortDialog by remember { mutableStateOf(false) }

    LaunchedEffect(storeType) { viewModel.setStore(storeType) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = storeType.displayName,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoBadge("未使用 ${state.summary.unused}")
                    InfoBadge("已使用 ${state.summary.used}")
                    InfoBadge("已過期 ${state.summary.expired}")
                }
            }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = "搜尋此超商的卡片",
                )
            }
            item {
                FilterChipsRow(
                    options = GroupSortOrder.entries,
                    selected = state.sortOrder,
                    labelOf = { it.displayName },
                    onSelect = { viewModel.setSortOrder(it) },
                )
            }

            if (state.heldGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "目前沒有未使用的卡片",
                        description = "已使用的卡片會在「已使用」頁面保留紀錄。",
                        actionLabel = "掃描卡片",
                        onAction = onScan,
                    )
                }
            } else {
                item { SectionTitle("已有卡片") }
                items(state.heldGroups, key = { it.key }) { group ->
                    ProductGroupCard(
                        group = group,
                        today = state.today,
                        warningDays = state.warningDays,
                        onCardClick = { card -> onOpenCard(card.id) },
                        onAddCard = onScan,
                    )
                }
            }

            item { SectionTitle("尚無卡片（分類捷徑，不計入總數）") }
            items(state.notHeldGroups, key = { it.key }) { group ->
                ProductGroupCard(
                    group = group,
                    today = state.today,
                    warningDays = state.warningDays,
                    onCardClick = { card -> onOpenCard(card.id) },
                    onAddCard = onScan,
                )
            }
        }
    }
}
