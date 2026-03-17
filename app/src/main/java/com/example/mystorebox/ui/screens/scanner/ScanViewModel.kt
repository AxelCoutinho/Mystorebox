package com.example.mystorebox.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mystorebox.data.network.RetrofitClient
import com.example.mystorebox.data.network.ScryfallCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private var lastScannedName: String? = null
    private var pendingIdentity: CardIdentity? = null
    private var lastSuccessTimestamp = 0L
    private val SCAN_COOLDOWN = 3500L

    fun onTextDetected(rawText: String) {
        val identity = extractCardIdentity(rawText)
        val currentState = _uiState.value
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSuccessTimestamp < SCAN_COOLDOWN) return

        if (currentState.isLoading || identity.name.length < 3) return

        if (identity.name.equals(lastScannedName, ignoreCase = true)) return

        if (identity == pendingIdentity && searchJob?.isActive == true) return

        pendingIdentity = identity

        _uiState.update { state ->
            state.copy(detectedText = identity.name, detectedSet = identity.setCode, error = null)
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(700)
            searchCard(identity.name, identity.setCode)
        }
    }

    fun onNoTextDetected() {
        if (System.currentTimeMillis() - lastSuccessTimestamp > SCAN_COOLDOWN) {
            pendingIdentity = null
            lastScannedName = null
        }
    }

    private suspend fun searchCard(name: String, setCode: String?) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val cleanName = name.replace(Regex("[^\\p{L}\\p{N}\\s'-,]"), "").trim()
        val cleanSet = setCode?.replace(Regex("[^a-zA-Z0-9]"), "")?.trim()

        try {
            val card = RetrofitClient.service.getCardByName(name = cleanName, set = cleanSet)
            handleSuccessfulScan(card)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (cleanSet != null) {
                try {
                    val fallbackCard = RetrofitClient.service.getCardByName(name = cleanName)
                    handleSuccessfulScan(fallbackCard)
                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    resetAfterError()
                }
            } else {
                resetAfterError()
            }
        }
    }

    private suspend fun handleSuccessfulScan(card: ScryfallCard) {
        lastScannedName = card.name
        lastSuccessTimestamp = System.currentTimeMillis()
        pendingIdentity = null

        addCardToTray(card)

        _uiState.update { it.copy(isLoading = false, cardFound = card) }

        delay(2500)
        _uiState.update {
            it.copy(cardFound = null, detectedText = "", detectedSet = null)
        }
    }

    private fun resetAfterError() {
        _uiState.update {
            it.copy(isLoading = false, error = "Not found", detectedText = "", detectedSet = null)
        }
        lastScannedName = null
    }

    private fun addCardToTray(newCard: ScryfallCard) {
        _uiState.update { state ->
            val existingItem = state.trayItems.find { it.card.id == newCard.id }
            val newTray = if (existingItem != null) {
                state.trayItems.map { item ->
                    if (item.card.id == newCard.id) item.copy(quantity = item.quantity + 1) else item
                }
            } else {
                listOf(TrayItem(newCard, 1)) + state.trayItems
            }
            state.copy(trayItems = newTray)
        }
    }

    fun increaseTrayItemQuantity(cardId: String) {
        _uiState.update { state ->
            val newTray = state.trayItems.map { item ->
                if (item.card.id == cardId) item.copy(quantity = item.quantity + 1) else item
            }
            state.copy(trayItems = newTray)
        }
    }

    fun decreaseTrayItemQuantity(cardId: String) {
        _uiState.update { state ->
            val newTray = state.trayItems.mapNotNull { item ->
                if (item.card.id == cardId) {
                    if (item.quantity > 1) item.copy(quantity = item.quantity - 1)
                    else null
                } else {
                    item
                }
            }
            state.copy(trayItems = newTray)
        }
    }

    fun setTrayVisibility(isVisible: Boolean) {
        _uiState.update { it.copy(isTrayVisible = isVisible) }
    }

    fun clearTray() {
        _uiState.update { it.copy(trayItems = emptyList(), isTrayVisible = false) }
    }

    private fun extractCardIdentity(text: String): CardIdentity {
        val lines = text.split("\n")
        val possibleName = lines.firstOrNull { it.length > 3 }?.trim() ?: ""
        val validLangs = "en|es|fr|de|it|pt"
        val strictPattern = Regex(
            "([a-z0-9]{3,4})\\s*[\\u2022\\.\\-\\s]\\s*($validLangs)\\b",
            RegexOption.IGNORE_CASE
        )
        val match = strictPattern.findAll(text).lastOrNull()
        return CardIdentity(possibleName, match?.groupValues?.get(1))
    }

    fun fetchAlternativePrints(card: ScryfallCard) {
        val printsUri = card.prints_search_uri
        if (printsUri.isNullOrEmpty()) return
        _uiState.update { it.copy(isFetchingPrints = true, cardBeingEdited = card) }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.service.getCardPrints(printsUri)
                _uiState.update { it.copy(isFetchingPrints = false, availablePrints = response.data) }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.update { it.copy(isFetchingPrints = false, availablePrints = emptyList()) }
                }
            }
        }
    }

    fun clearAlternativePrints() {
        _uiState.update { it.copy(availablePrints = emptyList(), cardBeingEdited = null) }
    }

    fun swapTrayItemPrint(oldCardId: String, newCard: ScryfallCard) {
        _uiState.update { state ->
            val newTray = state.trayItems.map { item ->
                if (item.card.id == oldCardId) item.copy(card = newCard) else item
            }
            state.copy(trayItems = newTray, availablePrints = emptyList(), cardBeingEdited = null)
        }
    }
}