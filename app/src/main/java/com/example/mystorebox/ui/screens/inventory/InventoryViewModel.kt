package com.example.mystorebox.ui.screens.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mystorebox.data.local.AppDatabase
import com.example.mystorebox.data.local.entities.BoxEntity
import com.example.mystorebox.data.local.entities.BoxWithRows
import com.example.mystorebox.data.local.entities.CardEntity
import com.example.mystorebox.data.local.entities.RowEntity
import com.example.mystorebox.data.network.ScryfallCard
import com.example.mystorebox.utils.GoogleSheetsExporter
import com.example.mystorebox.utils.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val inventoryDao = AppDatabase.getDatabase(application).inventoryDao()

    val boxes: StateFlow<List<BoxWithRows>> = inventoryDao.getBoxesWithRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rowCardCounts: StateFlow<Map<String, Int>> = inventoryDao.getCardCountsPerRow()
        .map { list -> list.associate { it.locationRowId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedBoxId = MutableStateFlow<String?>(null)
    val selectedBox: StateFlow<BoxWithRows?> = combine(boxes, _selectedBoxId) { boxList, selectedId ->
        boxList.find { it.box.boxId == selectedId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedRowId = MutableStateFlow<String?>(null)
    val selectedRow: StateFlow<RowEntity?> = combine(selectedBox, _selectedRowId) { box, rowId ->
        box?.rows?.find { it.rowId == rowId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cardsInSelectedRow: StateFlow<List<CardEntity>> = _selectedRowId
        .flatMapLatest { rowId ->
            if (rowId != null) inventoryDao.getCardsByRowId(rowId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showCreateBoxDialog = MutableStateFlow(false)
    val showCreateBoxDialog: StateFlow<Boolean> = _showCreateBoxDialog.asStateFlow()

    private val _showCreateRowDialog = MutableStateFlow(false)
    val showCreateRowDialog: StateFlow<Boolean> = _showCreateRowDialog.asStateFlow()

    private val _selectedBoxesForDeletion = MutableStateFlow<Set<String>>(emptySet())
    val selectedBoxesForDeletion = _selectedBoxesForDeletion.asStateFlow()

    private val _selectedRowsForDeletion = MutableStateFlow<Set<String>>(emptySet())
    val selectedRowsForDeletion = _selectedRowsForDeletion.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportSuccessUrl = MutableStateFlow<String?>(null)
    val exportSuccessUrl: StateFlow<String?> = _exportSuccessUrl.asStateFlow()

    fun selectBox(box: BoxWithRows?) {
        _selectedBoxId.value = box?.box?.boxId
        _selectedRowId.value = null
    }

    fun selectRow(row: RowEntity?) {
        _selectedRowId.value = row?.rowId
    }

    fun onBoxDialogVisibilityChanged(isVisible: Boolean) {
        _showCreateBoxDialog.value = isVisible
    }

    fun onRowDialogVisibilityChanged(isVisible: Boolean) {
        _showCreateRowDialog.value = isVisible
    }

    fun clearExportState() {
        _exportSuccessUrl.value = null
    }

    fun confirmCreateBox(name: String) {
        viewModelScope.launch {
            val newBox = BoxEntity(name = name)
            inventoryDao.insertBox(newBox)
        }
        _showCreateBoxDialog.value = false
    }

    fun confirmCreateRow(name: String) {
        selectedBox.value?.let { currentBox ->
            viewModelScope.launch {
                val newRow = RowEntity(parentBoxId = currentBox.box.boxId, name = name)
                inventoryDao.insertRow(newRow)
            }
        }
        _showCreateRowDialog.value = false
    }

    fun processInventorySave(
        scannedCards: List<ScryfallCard>,
        onSuccessCallback: (String, String, List<ScryfallCard>) -> Unit
    ) {
        val box = selectedBox.value
        val row = selectedRow.value

        if (box != null && row != null) {
            viewModelScope.launch {
                val cardEntities = scannedCards.map { it.toEntity(box.box.boxId, row.rowId) }
                inventoryDao.insertCards(cardEntities)
                onSuccessCallback(box.box.boxId, row.rowId, scannedCards)
            }
        }
    }

    fun toggleBoxForDeletion(boxId: String) {
        val current = _selectedBoxesForDeletion.value.toMutableSet()
        if (current.contains(boxId)) current.remove(boxId) else current.add(boxId)
        _selectedBoxesForDeletion.value = current
    }

    fun toggleRowForDeletion(rowId: String) {
        val current = _selectedRowsForDeletion.value.toMutableSet()
        if (current.contains(rowId)) current.remove(rowId) else current.add(rowId)
        _selectedRowsForDeletion.value = current
    }

    fun clearDeletionSelection() {
        _selectedBoxesForDeletion.value = emptySet()
        _selectedRowsForDeletion.value = emptySet()
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            val boxesToDelete = _selectedBoxesForDeletion.value.toList()
            val rowsToDelete = _selectedRowsForDeletion.value.toList()

            if (boxesToDelete.isNotEmpty()) {
                inventoryDao.deleteBoxesByIds(boxesToDelete)
                if (boxesToDelete.contains(_selectedBoxId.value)) _selectedBoxId.value = null
            }
            if (rowsToDelete.isNotEmpty()) {
                inventoryDao.deleteRowsByIds(rowsToDelete)
                if (rowsToDelete.contains(_selectedRowId.value)) _selectedRowId.value = null
            }

            clearDeletionSelection()
        }
    }

    fun exportToGoogleSheets(accessToken: String) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val inventoryData = inventoryDao.getInventoryForSheetsExport()

                val spreadsheetUrl = GoogleSheetsExporter.exportInventory(accessToken, inventoryData)

                _exportSuccessUrl.value = spreadsheetUrl
            } catch (e: Exception) {
                android.util.Log.e("ExportToSheets", "Error exportando: ${e.message}", e)
            } finally {
                _isExporting.value = false
            }
        }
    }
}