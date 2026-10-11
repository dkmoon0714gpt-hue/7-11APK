package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.backup.BackupInspection
import com.chaoshangka.manager.backup.RestoreReport
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.GroupSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val warningDays: Int = 7,
    val farWarningDays: Int = 30,
    val sortOrder: GroupSortOrder = GroupSortOrder.EXPIRY,
    val busy: Boolean = false,
    val message: String? = null,
    val appVersion: String = "",
    val requestedPermissions: List<String> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.warningDays,
        container.settingsRepository.farWarningDays,
        container.settingsRepository.groupSortOrder,
        busy,
        message,
    ) { warningDays, farWarningDays, sortOrder, isBusy, userMessage ->
        SettingsUiState(
            warningDays = warningDays,
            farWarningDays = farWarningDays,
            sortOrder = sortOrder,
            busy = isBusy,
            message = userMessage,
            appVersion = container.appVersion,
            requestedPermissions = container.grantedPermissions(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setWarningDays(days: Int) = container.settingsRepository.setWarningDays(days)

    fun setFarWarningDays(days: Int) = container.settingsRepository.setFarWarningDays(days)

    fun setSortOrder(order: GroupSortOrder) = container.settingsRepository.setGroupSortOrder(order)

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            busy.value = true
            runCatching { container.backupManager.exportTo(uri, container.appVersion) }
                .onSuccess { manifest ->
                    message.value = "已匯出備份：${manifest.cardCount} 張卡片、${manifest.photoCount} 張照片"
                }
                .onFailure { message.value = "匯出失敗：${it.message}" }
            busy.value = false
        }
    }

    fun inspectBackup(uri: android.net.Uri, onResult: (BackupInspection) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { container.backupManager.inspect(uri) }
                .onSuccess { onResult(it) }
                .onFailure { message.value = "無法讀取備份檔：${it.message}" }
            busy.value = false
        }
    }

    fun importBackup(uri: android.net.Uri, skipDuplicates: Boolean) {
        viewModelScope.launch {
            busy.value = true
            runCatching { container.backupManager.restoreFrom(uri, skipDuplicates) }
                .onSuccess { report: RestoreReport ->
                    message.value = buildString {
                        append("已匯入 ${report.cardsImported} 張卡片、${report.barcodesImported} 組條碼、")
                        append("${report.photosRestored} 張照片")
                        if (report.duplicateCardsSkipped > 0) {
                            append("；略過 ${report.duplicateCardsSkipped} 張重複卡片")
                        }
                        report.warnings.forEach { append("\n$it") }
                    }
                }
                .onFailure { message.value = it.message ?: "匯入失敗" }
            busy.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
