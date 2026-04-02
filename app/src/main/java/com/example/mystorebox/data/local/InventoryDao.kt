package com.example.mystorebox.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.mystorebox.data.local.entities.BoxEntity
import com.example.mystorebox.data.local.entities.BoxWithRows
import com.example.mystorebox.data.local.entities.CardEntity
import com.example.mystorebox.data.local.entities.RowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBox(box: BoxEntity)

    @Transaction
    @Query("SELECT * FROM boxes ORDER BY createdAt DESC")
    fun getBoxesWithRows(): Flow<List<BoxWithRows>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(row: RowEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE locationBoxId = :boxId AND locationRowId = :rowId")
    fun getCardsInLocation(boxId: String, rowId: String): Flow<List<CardEntity>>

    @Query("DELETE FROM boxes WHERE boxId IN (:boxIds)")
    suspend fun deleteBoxesByIds(boxIds: List<String>)

    @Query("DELETE FROM box_rows WHERE rowId IN (:rowIds)")
    suspend fun deleteRowsByIds(rowIds: List<String>)

    @Query("SELECT * FROM cards WHERE locationRowId = :rowId")
    fun getCardsByRowId(rowId: String): Flow<List<CardEntity>>

    @Query("DELETE FROM cards WHERE uniqueId IN (:idList)")
    suspend fun deleteCardsByIds(idList: List<String>)

    data class SheetsExportRecord(
        val boxName: String,
        val rowName: String,
        val cardName: String,
        val edition: String?,
        val quantity: Int
    )

    @Query("""
        SELECT 
            b.name AS boxName, 
            COALESCE(r.name, 'Fila Única') AS rowName, 
            c.name AS cardName, 
            c.setCode AS edition, 
            COUNT(c.uniqueId) AS quantity
        FROM cards c
        INNER JOIN boxes b ON c.locationBoxId = b.boxId
        LEFT JOIN box_rows r ON c.locationRowId = r.rowId 
        GROUP BY b.name, r.name, c.name, c.setCode
        ORDER BY b.name, r.name, c.name
    """)
    suspend fun getInventoryForSheetsExport(): List<SheetsExportRecord>

    data class RowCardCount(
        val locationRowId: String,
        val count: Int
    )

    @Query("SELECT locationRowId, COUNT(uniqueId) as count FROM cards WHERE locationRowId IS NOT NULL GROUP BY locationRowId")
    fun getCardCountsPerRow(): Flow<List<RowCardCount>>
}
