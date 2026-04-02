package com.example.mystorebox.utils

import com.example.mystorebox.data.local.entities.CardEntity
import com.example.mystorebox.data.network.ScryfallCard
import java.util.UUID

fun ScryfallCard.toEntity(boxId: String, rowId: String): CardEntity {
    return CardEntity(
        uniqueId = UUID.randomUUID().toString(),
        name = this.name,
        setCode = this.set,
        collectorNumber = this.collector_number,
        imageUrl = this.image_uris?.normal,
        priceUsd = this.prices?.usd,
        locationBoxId = boxId,
        locationRowId = rowId,
        imageUri = this.image_uris?.normal,
        quantity = 1
    )
}