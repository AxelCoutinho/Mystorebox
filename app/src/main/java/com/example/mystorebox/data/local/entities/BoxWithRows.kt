package com.example.mystorebox.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class BoxWithRows(
    @Embedded val box: BoxEntity,

    @Relation(
        parentColumn = "boxId",
        entityColumn = "parentBoxId"
    )
    val rows: List<RowEntity>
)