package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.data.repository.CategoryRepository
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.StoreType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val loading: Boolean = true,
    val categories: List<ProductCategory> = emptyList(),
    val storeFilter: StoreType? = null,
    val message: String? = null,
)

/**
 * 商品分類管理：新增、修改、刪除自訂分類。
 * 刪除已被卡片使用的分類時會被阻擋，避免卡片遺失。
 */
class CategoriesViewModel(private val container: AppContainer) : ViewModel() {

    private val storeFilter = MutableStateFlow<StoreType?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        container.categoryRepository.observeActive(),
        storeFilter,
        message,
    ) { categories, store, userMessage ->
        CategoriesUiState(
            loading = false,
            categories = categories
                .filter { !it.isPresetProduct }
                .filter { store == null || it.storeType == null || it.storeType == store },
            storeFilter = store,
            message = userMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(),
    )

    fun setStoreFilter(store: StoreType?) {
        storeFilter.value = store
    }

    fun addCategory(name: String, store: StoreType?, denomination: Int?, specification: String?) {
        if (name.isBlank()) {
            message.value = "分類名稱不可空白"
            return
        }
        viewModelScope.launch {
            container.categoryRepository.addCustomCategory(name, store, denomination, specification)
            message.value = "已新增分類「${name.trim()}」"
        }
    }

    fun renameCategory(category: ProductCategory, newName: String) {
        if (newName.isBlank()) {
            message.value = "分類名稱不可空白"
            return
        }
        viewModelScope.launch {
            container.categoryRepository.updateCategory(category.copy(categoryName = newName))
            message.value = "已更新分類名稱"
        }
    }

    fun setActive(category: ProductCategory, active: Boolean) {
        viewModelScope.launch {
            container.categoryRepository.updateCategory(category.copy(isActive = active))
            message.value = if (active) "已啟用分類" else "已停用分類（既有卡片不受影響）"
        }
    }

    /** 刪除分類。若仍被卡片使用，會被阻擋並提示先轉移卡片。 */
    fun deleteCategory(category: ProductCategory) {
        viewModelScope.launch {
            when (val result = container.categoryRepository.deleteCategory(category.id)) {
                is CategoryRepository.DeleteResult.Deleted ->
                    message.value = "已刪除分類「${category.categoryName}」"

                is CategoryRepository.DeleteResult.Blocked ->
                    message.value = "「${category.categoryName}」仍有 ${result.cardCount} 張卡片使用，請先將卡片移至其他分類，卡片不會被刪除"
            }
        }
    }

    /** 把卡片移到其他分類後再刪除原分類。 */
    fun moveCardsAndDelete(from: ProductCategory, to: ProductCategory) {
        viewModelScope.launch {
            container.categoryRepository.moveCardsAndDelete(from.id, to.id)
            message.value = "已將卡片移至「${to.categoryName}」並刪除「${from.categoryName}」"
        }
    }

    suspend fun selectableCategories(store: StoreType): List<ProductCategory> =
        container.categoryRepository.selectableCategories(store)

    fun clearMessage() {
        message.value = null
    }
}
