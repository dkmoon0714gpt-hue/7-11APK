package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.domain.usecase.SortCardsUseCase
import com.chaoshangka.manager.util.CardSearch
import com.chaoshangka.manager.util.DateFormatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UsedUiState(
    val loading: Boolean = true,
    val cards: List<GiftCard> = emptyList(),
    val query: String = "",
    val storeFilter: StoreType? = null,
    val dateFilter: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
)

/** 已使用清單：預設依使用時間由新到舊，可搜尋、篩選超商與使用日期。 */
class UsedViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val storeFilter = MutableStateFlow<StoreType?>(null)
    private val dateFilter = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<UsedUiState> = combine(
        container.cardRepository.observeAllCards(),
        query,
        storeFilter,
        dateFilter,
    ) { cards, queryText, store, date ->
        val used = cards.filter { it.cardStatus == CardStatus.USED && !it.isArchived }
        val filtered = used
            .filter { store == null || it.storeType == store }
            .filter { date == null || DateFormatters.epochMillisToLocalDate(it.usedAt) == date }
            .filter { CardSearch.matches(it, queryText) }
        UsedUiState(
            loading = false,
            cards = SortCardsUseCase.usedNewestFirst(filtered),
            query = queryText,
            storeFilter = store,
            dateFilter = date,
            today = LocalDate.now(container.clock),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UsedUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStoreFilter(value: StoreType?) {
        storeFilter.value = value
    }

    fun setDateFilter(value: LocalDate?) {
        dateFilter.value = value
    }

    /** 讓使用者可把誤標為已使用的卡片回復成未使用。 */
    fun restoreToUnused(cardId: Long) {
        viewModelScope.launch {
            container.cardRepository.markUnused(cardId)
        }
    }
}
