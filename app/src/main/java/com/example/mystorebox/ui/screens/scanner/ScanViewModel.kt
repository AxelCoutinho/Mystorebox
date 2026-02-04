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
    val detectedSet: String? = null,
    val cardFound: ScryfallCard? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ScannerViewModel : ViewModel() {

    var uiState by mutableStateOf(ScannerUiState())
        private set

    private var searchJob: Job? = null

    fun onTextDetected(rawText: String) {
        val identity = extractCardIdentity(rawText)

        if (identity.name.length < 3) return

        if (identity.name == uiState.detectedText && identity.setCode == uiState.detectedSet) return

        uiState = uiState.copy(
            detectedText = identity.name,
            detectedSet = identity.setCode
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(1000)
            searchCard(identity.name, identity.setCode)
        }
    }

    private suspend fun searchCard(name: String, setCode: String?) {
        uiState = uiState.copy(isLoading = true, error = null)
        try {
            val card = RetrofitClient.service.getCardByName(name = name, set = setCode)
            uiState = uiState.copy(isLoading = false, cardFound = card)
        } catch (e: Exception) {
            // Fallback: With this we'll ensure to get the card only using the name if the reading on the set failed.
            if (setCode != null) {
                try {
                    val fallbackCard = RetrofitClient.service.getCardByName(name = name)
                    uiState = uiState.copy(isLoading = false, cardFound = fallbackCard)
                } catch (e2: Exception) {
                    uiState = uiState.copy(isLoading = false, error = "No encontrada")
                }
            } else {
                uiState = uiState.copy(isLoading = false, error = "No encontrada")
            }
        }
    }

    private fun extractCardIdentity(text: String): CardIdentity {
        val lines = text.split("\n")
        val possibleName = lines.firstOrNull { it.length > 3 }?.trim() ?: ""

        val validLangs = "en|es|fr|de|it|pt|ja|ko|ru|zh|ph"

        val strictPattern = Regex(
            "([a-z0-9]{3,4})\\s*[\\u2022\\.\\-\\s]\\s*($validLangs)\\b",
            RegexOption.IGNORE_CASE
        )

        val matches = strictPattern.findAll(text)
        val bestMatch = matches.lastOrNull()

        val setCode = bestMatch?.groupValues?.get(1)

        if (setCode != null) {
            android.util.Log.d("OCR_MATCH", "Set válido encontrado: $setCode (Idioma: ${bestMatch.groupValues[2]})")
        }

        return CardIdentity(possibleName, setCode)
    }
}

data class CardIdentity(
    val name: String,
    val setCode: String? = null
)
