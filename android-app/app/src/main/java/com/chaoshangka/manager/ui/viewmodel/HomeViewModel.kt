package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.StatsSummary
import com.chaoshangka.manager.domain.model.StoreSummary
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.domain.usecase.ComputeStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val summary: StatsSummary = StatsSummary(),
    val stores: List<StoreSummary> = StoreType.mainStores.map { StoreSummary(it) },
    val today: LocalDate = LocalDate.now(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        container.cardRepository.observeAllCards(),
        container.settingsRepository.warningDays,
    ) { cards, warningDays ->
        val today = LocalDate.now(container.clock)
        HomeUiState(
            loading = false,
            summary = ComputeStatsUseCase.summary(cards, today, warningDays),
            stores = ComputeStatsUseCase.storeSummaries(cards, today, warningDays),
            today = today,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
