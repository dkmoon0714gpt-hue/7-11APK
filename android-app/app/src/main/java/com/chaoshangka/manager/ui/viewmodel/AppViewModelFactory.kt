package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.chaoshangka.manager.di.AppContainer

/**
 * 單一 ViewModel 工廠，避免為每個畫面建立各自的工廠。
 * 所有 ViewModel 都只依賴 [AppContainer]（內部再取用 Repository / 引擎）。
 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(container)
        modelClass.isAssignableFrom(StoreViewModel::class.java) -> StoreViewModel(container)
        modelClass.isAssignableFrom(CardListViewModel::class.java) -> CardListViewModel(container)
        modelClass.isAssignableFrom(CardDetailViewModel::class.java) -> CardDetailViewModel(container)
        modelClass.isAssignableFrom(ScanViewModel::class.java) -> ScanViewModel(container)
        modelClass.isAssignableFrom(UsedViewModel::class.java) -> UsedViewModel(container)
        modelClass.isAssignableFrom(CategoriesViewModel::class.java) -> CategoriesViewModel(container)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
        else -> throw IllegalArgumentException("未知的 ViewModel：${modelClass.name}")
    } as T
}
