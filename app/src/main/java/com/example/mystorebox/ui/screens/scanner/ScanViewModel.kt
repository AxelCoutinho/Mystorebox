package com.example.mystorebox.ui.screens.scanner

import android.util.Log
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

class ScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onTextDetected(rawText: String) {
        val identity = extractCardIdentity(rawText)
        val currentState = _uiState.value

        if (currentState.isLoading || currentState.cardFound != null) return
        if (identity.name.length < 3) return
        if (identity.name == currentState.detectedText && identity.setCode == currentState.detectedSet) return

        _uiState.update { state ->
            state.copy(
                detectedText = identity.name,
                detectedSet = identity.setCode,
                error = null
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(1000)
            searchCard(identity.name, identity.setCode)
        }
    }

    private suspend fun searchCard(name: String, setCode: String?) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val cleanName = name.replace(Regex("[^\\p{L}\\p{N}\\s'-,]"), "").trim()
        val cleanSet = setCode?.replace(Regex("[^a-zA-Z0-9]"), "")?.trim()

        try {
            Log.d("ScryfallAPI", "ENVIANDO A API -> Nombre: '[$cleanName]' | Set: '[$cleanSet]'")
            val card = RetrofitClient.service.getCardByName(name = cleanName, set = cleanSet)

            handleSuccessfulScan(card)

        } catch (e: Exception) {
            if (e is CancellationException) throw e

            if (e is retrofit2.HttpException) {
                val errorJson = e.response()?.errorBody()?.string()
                Log.e("ScryfallAPI", "Scryfall rejected the request. Real reason: $errorJson")
            } else {
                Log.e("ScryfallAPI", "Main search error: ${e.message}", e)
            }

            if (cleanSet != null) {
                try {
                    Log.d("ScryfallAPI", "Iniciando Fallback -> Nombre: '[$cleanName]'")
                    val fallbackCard = RetrofitClient.service.getCardByName(name = cleanName)

                    handleSuccessfulScan(fallbackCard)

                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    Log.e("ScryfallAPI", "Error en fallback de la API: ${e2.message}", e2)
                    resetAfterError()
                }
            } else {
                resetAfterError()
            }
        }
    }

    private suspend fun handleSuccessfulScan(card: ScryfallCard) {
        addCardToTray(card)

        _uiState.update { it.copy(isLoading = false, cardFound = card) }

        delay(2000)
        _uiState.update { it.copy(cardFound = null, detectedText = "", detectedSet = null) }
    }

    private fun resetAfterError() {
        _uiState.update {
            it.copy(isLoading = false, error = "Not found", detectedText = "", detectedSet = null)
        }
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

        val matches = strictPattern.findAll(text)
        val bestMatch = matches.lastOrNull()

        val setCode = bestMatch?.groupValues?.get(1)

        if (setCode != null) {
            Log.d("OCR_MATCH", "Set válido encontrado: $setCode (Idioma: ${bestMatch.groupValues[2]})")
        }

        return CardIdentity(possibleName, setCode)
    }

    fun fetchAlternativePrints(card: ScryfallCard) {
        val printsUri = card.prints_search_uri
        if (printsUri.isNullOrEmpty()) return

        _uiState.update { it.copy(isFetchingPrints = true, cardBeingEdited = card) }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.service.getCardPrints(printsUri)

                _uiState.update {
                    it.copy(
                        isFetchingPrints = false,
                        availablePrints = response.data
                    )
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("ScryfallAPI", "Error buscando impresiones: ${e.message}", e)
                    _uiState.update { it.copy(isFetchingPrints = false, availablePrints = emptyList()) }
                }
            }
        }
    }
    fun clearAlternativePrints() {
        _uiState.update {
            it.copy(availablePrints = emptyList(), cardBeingEdited = null)
        }
    }
    fun swapTrayItemPrint(oldCardId: String, newCard: ScryfallCard) {
        _uiState.update { state ->
            val newTray = state.trayItems.map { item ->
                if (item.card.id == oldCardId) {
                    item.copy(card = newCard)
                } else {
                    item
                }
            }
            state.copy(
                trayItems = newTray,
                availablePrints = emptyList(),
                cardBeingEdited = null
            )
        }
    }
}