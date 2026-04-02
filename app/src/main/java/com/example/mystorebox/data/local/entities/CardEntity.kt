package com.example.mystorebox.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["boxId"],
            childColumns = ["locationBoxId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RowEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["locationRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationBoxId"), Index("locationRowId")]
)
data class CardEntity(
    @PrimaryKey val uniqueId: String,
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val imageUrl: String?,
    val priceUsd: String?,

    val locationBoxId: String,
    val locationRowId: String,

    val imageUri: String? = null,
    val quantity: Int = 1,

    val addedAt: Long = System.currentTimeMillis()
)