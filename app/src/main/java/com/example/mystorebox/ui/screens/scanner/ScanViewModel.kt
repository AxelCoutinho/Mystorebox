package com.example.mystorebox.ui.screens.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mystorebox.data.network.RetrofitClient
import com.example.mystorebox.data.network.ScryfallCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScannerUiState(
    val detectedText: String = "",
    val cardFound: ScryfallCard? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ScannerViewModel : ViewModel() {

    var uiState by mutableStateOf(ScannerUiState())
        private set

    private var searchJob: Job? = null

    fun onTextDetected(rawText: String) {
        val cleanName = extractCardName(rawText)

        if (cleanName.length < 3 || cleanName == uiState.detectedText) return
        uiState = uiState.copy(detectedText = cleanName)

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            delay(1500)

            searchCardInScryfall(cleanName)
        }
    }

    private fun searchCardInScryfall(cardName: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val card = RetrofitClient.service.getCardByName(cardName)

                uiState = uiState.copy(
                    isLoading = false,
                    cardFound = card
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "No encontrada"
                )
            }
        }
    }

    private fun extractCardName(text: String): String {
        return text.split("\n").firstOrNull { it.length > 3 }?.trim() ?: ""
    }
}