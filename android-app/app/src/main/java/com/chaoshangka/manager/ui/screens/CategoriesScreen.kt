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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.components.StoreFilterRow
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.CategoriesViewModel
import kotlinx.coroutines.launch

/**
 * 商品分類管理。
 *
 * 刪除已被卡片使用的分類時會被阻擋（卡片不會遺失），
 * 並提供「把卡片移到其他分類後再刪除」的選項。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: CategoriesViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductCategory?>(null) }
    var blockedDelete by remember { mutableStateOf<ProductCategory?>(null) }
    var confirmDelete by remember { mutableStateOf<ProductCategory?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "商品分類管理", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增分類")
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            item {
                StoreFilterRow(selected = state.storeFilter, onSelect = viewModel::setStoreFilter)
            }
            item {
                Text(
                    text = "通用分類可用於所有超商；指定超商的分類只會在該超商出現。刪除已被使用的分類會被阻擋，以確保卡片不會遺失。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.categories, key = { it.id }) { category ->
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = category.categoryName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = buildString {
                                    append(category.storeType?.displayName ?: "通用")
                                    if (!category.isActive) append("・已停用")
                                    if (category.denomination != null) append("・預設 ${category.denomination} 元")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = category }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "修改分類")
                        }
                        IconButton(onClick = { confirmDelete = category }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "刪除分類")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CategoryFormDialog(
            title = "新增分類",
            initialName = "",
            initialDenomination = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, denomination ->
                viewModel.addCategory(name, state.storeFilter, denomination, null)
                showAdd = false
            },
        )
    }

    editing?.let { category ->
        CategoryFormDialog(
            title = "修改分類",
            initialName = category.categoryName,
            initialDenomination = category.denomination,
            onDismiss = { editing = null },
            onConfirm = { name, _ ->
                viewModel.renameCategory(category, name)
                editing = null
            },
        )
    }

    confirmDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("刪除分類「${category.categoryName}」？") },
            text = { Text("若仍有卡片使用此分類，APP 不會刪除卡片，會要求你先將卡片移到其他分類。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.deleteCategory(category)
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }

    blockedDelete?.let { category ->
        MoveCardsDialog(
            fromCategory = category,
            categories = state.categories,
            onDismiss = { blockedDelete = null },
            onConfirm = { target ->
                blockedDelete = null
                viewModel.moveCardsAndDelete(category, target)
            },
        )
    }
}

@Composable
private fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialDenomination: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var denomination by remember { mutableStateOf(initialDenomination?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分類名稱") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = denomination,
                    onValueChange = { denomination = it.filter { ch -> ch.isDigit() } },
                    label = { Text("預設面額（可留空）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, denomination.toIntOrNull()) }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MoveCardsDialog(
    fromCategory: ProductCategory,
    categories: List<ProductCategory>,
    onDismiss: () -> Unit,
    onConfirm: (ProductCategory) -> Unit,
) {
    val targets = categories.filter { it.id != fromCategory.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${fromCategory.categoryName}」仍有卡片使用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("請選擇要將卡片移往的分類，卡片資料不會被刪除。")
                targets.take(8).forEach { target ->
                    OutlinedButton(onClick = { onConfirm(target) }, modifier = Modifier.fillMaxWidth()) {
                        Text(target.categoryName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
