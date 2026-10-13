package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.GroupSortOrder
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.ProductGroup
import com.chaoshangka.manager.domain.model.StoreSummary
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.util.CardSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StoreUiState(
    val loading: Boolean = true,
    val storeType: StoreType = StoreType.UNKNOWN,
    val summary: StoreSummary = StoreSummary(StoreType.UNKNOWN),
    val heldGroups: List<ProductGroup> = emptyList(),
    val notHeldGroups: List<ProductGroup> = emptyList(),
    val query: String = "",
    val sortOrder: GroupSortOrder = GroupSortOrder.EXPIRY,
    val today: LocalDate = LocalDate.now(),
    val warningDays: Int = 7,
)

class StoreViewModel(private val container: AppContainer) : ViewModel() {

    private val store = MutableStateFlow(StoreType.UNKNOWN)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(container.settingsRepository.groupSortOrder.value)

    private data class Source(
        val cards: List<GiftCard>,
        val categories: List<ProductCategory>,
        val warningDays: Int,
    )

    private val source = combine(
        container.cardRepository.observeAllCards(),
        container.categoryRepository.observeActive(),
        container.settingsRepository.warningDays,
    ) { cards, categories, warningDays -> Source(cards, categories, warningDays) }

    val uiState: StateFlow<StoreUiState> = combine(store, query, sort, source) { storeType, queryText, sortOrder, data ->
        val today = LocalDate.now(container.clock)
        val storeCards = data.cards.filter { it.storeType == storeType }
        val summary = StoreSummary(
            storeType = storeType,
            unused = storeCards.count { it.cardStatus == CardStatus.UNUSED && !it.isArchived },
            used = storeCards.count { it.cardStatus == CardStatus.USED && !it.isArchived },
            expired = storeCards.count {
                !it.isArchived &&
                    it.cardStatus == CardStatus.UNUSED &&
                    com.chaoshangka.manager.domain.ExpiryEvaluator
                        .liveStatus(it, today, data.warningDays) == ExpiryStatus.EXPIRED
            },
        )

        val categoryNames = data.categories.associate { it.id to it.categoryName }
        val visibleCards = storeCards.filter { CardSearch.matches(it, queryText, it.categoryId?.let { id -> categoryNames[id] }) }
        val presets = data.categories.filter { it.isPresetProduct && it.storeType == storeType }
        val groups = com.chaoshangka.manager.domain.usecase.ProductGroupBuilder.build(
            storeCards = visibleCards,
            presetProducts = presets,
            categories = data.categories,
            today = today,
            warningDays = data.warningDays,
            sortOrder = sortOrder,
        )

        StoreUiState(
            loading = false,
            storeType = storeType,
            summary = summary,
            heldGroups = groups.filter { it.isHeld },
            notHeldGroups = groups.filterNot { it.isHeld },
            query = queryText,
            sortOrder = sortOrder,
            today = today,
            warningDays = data.warningDays,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StoreUiState(),
    )

    fun setStore(storeType: StoreType) {
        store.value = storeType
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSortOrder(order: GroupSortOrder) {
        sort.value = order
        container.settingsRepository.setGroupSortOrder(order)
    }
}
