package com.example.mystorebox.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "box_rows",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["boxId"],
            childColumns = ["parentBoxId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentBoxId")]
)
data class RowEntity(
    @PrimaryKey val rowId: String = UUID.randomUUID().toString(),
    val parentBoxId: String,
    val name: String
)