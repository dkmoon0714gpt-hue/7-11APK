package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.ExpiryEvaluator
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.domain.usecase.SortCardsUseCase
import com.chaoshangka.manager.util.CardSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CardListUiState(
    val loading: Boolean = true,
    val query: String = "",
    val storeFilter: StoreType? = null,
    val statusFilter: CardStatus? = null,
    val cards: List<GiftCard> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val warningDays: Int = 7,
)

/** 「全部卡片」頁：搜尋 + 超商篩選 + 未使用／已使用切換。 */
class CardListViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val storeFilter = MutableStateFlow<StoreType?>(null)
    private val statusFilter = MutableStateFlow<CardStatus?>(null)

    val uiState: StateFlow<CardListUiState> = combine(
        container.cardRepository.observeAllCards(),
        container.settingsRepository.warningDays,
    ) { cards, warningDays ->
        Pair(cards, warningDays)
    }.combine(
        query,
    ) { (cards, warningDays), queryText ->
        Triple(cards, warningDays, queryText)
    }.combine(
        storeFilter,
        statusFilter,
    ) { (cards, warningDays, queryText), store, status ->
        val today = LocalDate.now(container.clock)
        val filtered = cards
            .filter { !it.isArchived }
            .filter { store == null || it.storeType == store }
            .filter { status == null || it.cardStatus == status }
            .filter { CardSearch.matches(it, queryText) }
        val ordered = if (status == null || status == CardStatus.UNUSED) {
            SortCardsUseCase.unusedOrdered(filtered, today, warningDays)
        } else {
            SortCardsUseCase.usedNewestFirst(filtered)
        }
        CardListUiState(
            loading = false,
            query = queryText,
            storeFilter = store,
            statusFilter = status,
            cards = ordered,
            today = today,
            warningDays = warningDays,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardListUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStoreFilter(value: StoreType?) {
        storeFilter.value = value
    }

    fun setStatusFilter(value: CardStatus?) {
        statusFilter.value = value
    }
}

data class CardDetailUiState(
    val loading: Boolean = true,
    val card: GiftCard? = null,
    val categoryName: String? = null,
    val categories: List<com.chaoshangka.manager.domain.model.ProductCategory> = emptyList(),
    val liveStatus: ExpiryStatus = ExpiryStatus.NO_EXPIRY,
    val today: LocalDate = LocalDate.now(),
    val warningDays: Int = 7,
    val message: String? = null,
)

/** 卡片詳細頁：顯示全部欄位、標記使用／恢復未使用、刪除、指定主要條碼。 */
class CardDetailViewModel(private val container: AppContainer) : ViewModel() {

    private val cardId = MutableStateFlow(0L)
    private val message = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cardFlow: Flow<GiftCard?> = cardId.flatMapLatest { id ->
        if (id <= 0L) flowOf(null) else container.cardRepository.observeCard(id)
    }

    val uiState: StateFlow<CardDetailUiState> = combine(
        cardFlow,
        container.settingsRepository.warningDays,
        message,
        container.categoryRepository.observeActive(),
    ) { card, warningDays, userMessage, categories ->
        val today = LocalDate.now(container.clock)
        CardDetailUiState(
            loading = false,
            card = card,
            categoryName = card?.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.categoryName },
            categories = categories,
            liveStatus = card?.let { ExpiryEvaluator.liveStatus(it, today, warningDays) } ?: ExpiryStatus.NO_EXPIRY,
            today = today,
            warningDays = warningDays,
            message = userMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardDetailUiState(),
    )

    fun setCardId(id: Long) {
        cardId.value = id
    }

    fun markUsed() {
        val id = cardId.value
        viewModelScope.launch {
            container.cardRepository.markUsed(id)
            message.value = "已標記為已使用（僅記錄你的操作，非超商系統驗證）"
        }
    }

    fun markUnused() {
        val id = cardId.value
        viewModelScope.launch {
            container.cardRepository.markUnused(id)
            message.value = "已恢復為未使用"
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = cardId.value
        viewModelScope.launch {
            container.cardRepository.deleteCard(id)
            onDeleted()
        }
    }

    fun setPrimaryBarcode(barcodeId: Long) {
        val id = cardId.value
        viewModelScope.launch {
            container.cardRepository.setPrimaryBarcode(id, barcodeId)
            message.value = "已設為主要兌換條碼"
        }
    }

    fun updateCard(card: GiftCard) {
        viewModelScope.launch {
            runCatching {
                container.cardRepository.updateCard(
                    card = card,
                    warningDays = container.settingsRepository.warningDays.value,
                )
            }.onSuccess { message.value = "已儲存修改" }
                .onFailure { message.value = "儲存失敗：${it.message}" }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
