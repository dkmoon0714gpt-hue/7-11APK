package com.chaoshangka.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.CardDraft
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ocr.CardInfoExtractor
import com.chaoshangka.manager.ocr.ScanAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ScanUiState(
    val analyzing: Boolean = false,
    val photoPath: String? = null,
    val draft: CardDraft = CardDraft(),
    val expiryText: String? = null,
    val warnings: List<String> = emptyList(),
    val duplicates: List<GiftCard> = emptyList(),
    val savedCardId: Long? = null,
    val error: String? = null,
) {
    val hasPhoto: Boolean get() = photoPath != null
}

/**
 * 掃描 → 辨識 → 人工確認 → 儲存。
 *
 * OCR 與條碼辨識在本機同時執行；辨識不到就留空（「待確認」），不填假資料。
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onPhotoCaptured(path: String) {
        _state.update {
            it.copy(
                analyzing = true,
                photoPath = path,
                error = null,
                savedCardId = null,
                warnings = emptyList(),
                duplicates = emptyList(),
                draft = CardDraft(originalImagePath = path),
            )
        }
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { container.photoStore.loadBitmap(path, maxSize = 2200) }
                    ?: throw IllegalStateException("無法讀取照片內容")
                val textResult = async {
                    runCatching { container.textRecognitionEngine.recognize(bitmap) }.getOrDefault(emptyList())
                }
                val barcodeResult = async {
                    runCatching { container.barcodeEngine.detect(bitmap) }.getOrDefault(emptyList())
                }
                val analysis = ScanAnalysis(textResult.await(), barcodeResult.await())
                val today = LocalDate.now(container.clock)
                val warningDays = container.settingsRepository.warningDays.value
                val extraction = CardInfoExtractor.extract(analysis, today, warningDays)

                val draft = CardDraft(
                    storeType = extraction.storeType,
                    productName = extraction.productName.orEmpty(),
                    denomination = extraction.denomination,
                    productSpecification = extraction.productSpecification.orEmpty(),
                    cardType = extraction.cardType,
                    cardNumber = extraction.cardNumber,
                    startDate = extraction.expiry.startDate,
                    expiryDate = extraction.expiry.endDate,
                    expiryNeedsReview = extraction.expiry.status == ExpiryStatus.NEEDS_REVIEW ||
                        extraction.expiry.endDate == null,
                    originalImagePath = path,
                    barcodes = extraction.barcodeDrafts,
                )

                val duplicates = if (extraction.storeType != StoreType.UNKNOWN) {
                    container.cardRepository.findDuplicateCards(
                        extraction.storeType,
                        extraction.barcodeDrafts.map { it.rawValue },
                    )
                } else {
                    emptyList()
                }

                _state.update {
                    it.copy(
                        analyzing = false,
                        draft = draft,
                        expiryText = extraction.expiryText,
                        warnings = extraction.warnings,
                        duplicates = duplicates,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(analyzing = false, error = e.message ?: "照片分析失敗") }
            }
        }
    }

    /** 手動新增（不拍照，或拍照後改為全部手動輸入）。 */
    fun startManualEntry(photoPath: String? = null) {
        _state.value = ScanUiState(
            photoPath = photoPath,
            draft = CardDraft(originalImagePath = photoPath, cardType = CardType.UNKNOWN),
        )
    }

    fun updateDraft(transform: (CardDraft) -> CardDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun updateBarcodes(transform: (List<BarcodeDraft>) -> List<BarcodeDraft>) {
        _state.update { it.copy(draft = it.draft.copy(barcodes = transform(it.draft.barcodes))) }
    }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            try {
                val draft = current.draft
                if (draft.storeType == StoreType.UNKNOWN) {
                    _state.update { it.copy(error = "請先選擇超商") }
                    return@launch
                }
                val id = container.cardRepository.saveNewCard(
                    draft = draft.copy(originalImagePath = current.photoPath ?: draft.originalImagePath),
                    today = LocalDate.now(container.clock),
                    warningDays = container.settingsRepository.warningDays.value,
                )
                _state.update { it.copy(savedCardId = id, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "儲存失敗") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        _state.value = ScanUiState()
    }
}
