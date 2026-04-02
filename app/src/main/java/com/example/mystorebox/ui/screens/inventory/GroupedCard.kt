package com.example.mystorebox.ui.screens.inventory

import com.example.mystorebox.data.network.ScryfallCard

data class GroupedCard(
    val card: ScryfallCard,
    val quantity: Int,
    val instanceIds: List<String> = emptyList()
)
