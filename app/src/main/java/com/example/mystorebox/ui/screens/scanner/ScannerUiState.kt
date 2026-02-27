package com.example.mystorebox.ui.screens.scanner

import com.example.mystorebox.data.network.ScryfallCard

data class ScannerUiState(
    val detectedText: String = "",
    val detectedSet: String? = null,
    val cardFound: ScryfallCard? = null,
    val trayItems: List<TrayItem> = emptyList(),
    val isTrayVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)