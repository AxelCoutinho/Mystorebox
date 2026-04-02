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
import android.content.Context
import androidx.core.content.edit
import com.example.mystorebox.data.network.RetrofitClient

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val inventoryDao = AppDatabase.getDatabase(application).inventoryDao()

    private val prefs = application.getSharedPreferences("StoreBoxPrefs", Context.MODE_PRIVATE)


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

    val groupedCardsInSelectedRow: StateFlow<List<GroupedCard>> = cardsInSelectedRow
        .map { flatCardList ->
            flatCardList.groupBy { "${it.name}_${it.setCode}" }
                .map { (_, group) ->
                    val firstCard = group.first()
                    GroupedCard(
                        card = ScryfallCard(
                            id = firstCard.uniqueId,
                            name = firstCard.name,
                            set = firstCard.setCode,
                            collector_number = firstCard.collectorNumber,
                            prices = com.example.mystorebox.data.network.Prices(
                                usd = firstCard.priceUsd,
                                eur = null,
                            ),
                            image_uris = com.example.mystorebox.data.network.ImageUris(
                                small = null,
                                normal = firstCard.imageUri ?: ""
                            ),
                            prints_search_uri = null
                        ),
                        quantity = group.size,
                        instanceIds = group.map { it.uniqueId }
                    )
                }
                .sortedBy { it.card.name }
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

    private val _selectedCardsForDeletion = MutableStateFlow<Set<String>>(emptySet())
    val selectedCardsForDeletion = _selectedCardsForDeletion.asStateFlow()

    fun toggleCardForDeletion(groupKey: String) {
        val current = _selectedCardsForDeletion.value.toMutableSet()
        if (current.contains(groupKey)) current.remove(groupKey) else current.add(groupKey)
        _selectedCardsForDeletion.value = current
    }

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportSuccessUrl = MutableStateFlow<String?>(null)
    val exportSuccessUrl: StateFlow<String?> = _exportSuccessUrl.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError = _exportError.asStateFlow()

    fun clearExportError() {
        _exportError.value = null
    }

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
        _selectedCardsForDeletion.value = emptySet()
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            val boxesToDelete = _selectedBoxesForDeletion.value.toList()
            val rowsToDelete = _selectedRowsForDeletion.value.toList()
            val cardKeysToDelete = _selectedCardsForDeletion.value.toList() // NUEVO

            if (boxesToDelete.isNotEmpty()) {
                inventoryDao.deleteBoxesByIds(boxesToDelete)
                if (boxesToDelete.contains(_selectedBoxId.value)) _selectedBoxId.value = null
            }
            if (rowsToDelete.isNotEmpty()) {
                inventoryDao.deleteRowsByIds(rowsToDelete)
                if (rowsToDelete.contains(_selectedRowId.value)) _selectedRowId.value = null
            }

            if (cardKeysToDelete.isNotEmpty()) {
                val idsToDelete = groupedCardsInSelectedRow.value
                    .filter { cardKeysToDelete.contains("${it.card.name}_${it.card.set}") }
                    .flatMap { it.instanceIds }

                if (idsToDelete.isNotEmpty()) {
                    inventoryDao.deleteCardsByIds(idsToDelete)
                }
            }

            clearDeletionSelection()
        }
    }

    private val _cardBeingEdited = MutableStateFlow<GroupedCard?>(null)
    val cardBeingEdited = _cardBeingEdited.asStateFlow()

    private val _availablePrints = MutableStateFlow<List<ScryfallCard>>(emptyList())
    val availablePrints = _availablePrints.asStateFlow()

    fun onEditCardClicked(group: GroupedCard) {
        _cardBeingEdited.value = group
        _availablePrints.value = emptyList()

        viewModelScope.launch {
            try {
                val finalUrl = group.card.prints_search_uri ?:
                "https://api.scryfall.com/cards/search?q=%21%22${java.net.URLEncoder.encode(group.card.name, "UTF-8")}%22+unique%3Aprints"

                android.util.Log.d("InventoryVM", "Buscando en: $finalUrl")

                val response = RetrofitClient.service.getCardPrints(finalUrl)

                if (response.data.isNotEmpty()) {
                    _availablePrints.value = response.data
                } else {
                    _cardBeingEdited.value = null
                }

            } catch (e: Exception) {
                android.util.Log.e("InventoryVM", "Error en la búsqueda: ${e.message}")
                _cardBeingEdited.value = null
            }
        }
    }

    fun clearEditState() {
        _cardBeingEdited.value = null
        _availablePrints.value = emptyList()
    }

    fun confirmPrintDistribution(
        oldGroup: GroupedCard,
        distributionMap: Map<ScryfallCard, Int>
    ) {
        viewModelScope.launch {
            val oldIds = oldGroup.instanceIds

            inventoryDao.deleteCardsByIds(oldIds)

            val newCardsToInsert = mutableListOf<CardEntity>()

            val boxId = selectedBox.value?.box?.boxId ?: ""
            val rowId = selectedRow.value?.rowId ?: ""

            distributionMap.forEach { (scryfallCard, quantity) ->
                repeat(quantity) {
                    newCardsToInsert.add(
                        CardEntity(
                            uniqueId = java.util.UUID.randomUUID().toString(),
                            name = scryfallCard.name,
                            setCode = scryfallCard.set,
                            collectorNumber = scryfallCard.collector_number,
                            priceUsd = scryfallCard.prices?.usd,
                            imageUrl = scryfallCard.image_uris?.normal,
                            locationBoxId = boxId,
                            locationRowId = rowId
                        )
                    )
                }
            }

            if (newCardsToInsert.isNotEmpty()) {
                inventoryDao.insertCards(newCardsToInsert)
            }
        }
    }

    fun exportToGoogleSheets(accessToken: String) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportError.value = null

            try {
                val inventoryData = inventoryDao.getInventoryForSheetsExport()
                val savedSheetId = prefs.getString("SHEET_ID", null)

                val result = GoogleSheetsExporter.exportInventory(accessToken, inventoryData, savedSheetId)

                prefs.edit { putString("SHEET_ID", result.spreadsheetId) }
                _exportSuccessUrl.value = result.spreadsheetUrl

            } catch (e: Exception) {
                android.util.Log.e("ExportToSheets", "Error exportando: ${e.message}", e)

                _exportError.value = e.localizedMessage ?: "Error desconocido de la API de Google"
            } finally {
                _isExporting.value = false
            }
        }
    }
}