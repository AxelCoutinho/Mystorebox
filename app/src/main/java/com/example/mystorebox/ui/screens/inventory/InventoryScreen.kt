package com.example.mystorebox.ui.screens.inventory

import InventoryCard
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mystorebox.data.network.ScryfallCard
import com.example.mystorebox.ui.composables.CreateItemDialog
import com.example.mystorebox.ui.composables.SavedCardRow
import com.example.mystorebox.ui.composables.ScannedCardRow
import com.example.mystorebox.utils.GoogleAuthUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    scannedCards: List<ScryfallCard> = emptyList(),
    onSaveInventory: (String, String, List<ScryfallCard>) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit = {},
    viewModel: InventoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val boxes by viewModel.boxes.collectAsState()
    val cardsInRow by viewModel.cardsInSelectedRow.collectAsState()
    val rowCardCounts by viewModel.rowCardCounts.collectAsState()

    val selectedBox by viewModel.selectedBox.collectAsState()
    val selectedRow by viewModel.selectedRow.collectAsState()
    val currentBox = selectedBox
    val currentRow = selectedRow

    val selectedBoxesForDeletion by viewModel.selectedBoxesForDeletion.collectAsState()
    val selectedRowsForDeletion by viewModel.selectedRowsForDeletion.collectAsState()

    val isSavingMode = scannedCards.isNotEmpty()
    val canSave = currentBox != null && currentRow != null
    val isDeletionMode = selectedBoxesForDeletion.isNotEmpty() || selectedRowsForDeletion.isNotEmpty()

    val showCreateBoxDialog by viewModel.showCreateBoxDialog.collectAsState()
    val showCreateRowDialog by viewModel.showCreateRowDialog.collectAsState()

    val isExporting by viewModel.isExporting.collectAsState()
    val exportSuccessUrl by viewModel.exportSuccessUrl.collectAsState()

    val exportError by viewModel.exportError.collectAsState()

    val authClient = remember { GoogleAuthUtil.getAuthClient(context) }
    val authRequest = remember { GoogleAuthUtil.getSheetsAuthRequest() }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authResult = authClient.getAuthorizationResultFromIntent(result.data)
                authResult.accessToken?.let { token ->
                    android.util.Log.d("GoogleAuth", "¡Permiso concedido! Token: $token")
                    viewModel.exportToGoogleSheets(token)
                } ?: run {
                    val errorMsg = "Error: El token de Google regresó nulo"
                    android.util.Log.e("GoogleAuth", errorMsg)
                    android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleAuth", "Error de autorización: ${e.message}")
                android.widget.Toast.makeText(context, "Fallo de autorización: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            val errorMsg = "Autenticación cancelada o rechazada. Código: ${result.resultCode}"
            android.util.Log.e("GoogleAuth", errorMsg)
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    BackHandler(enabled = isSavingMode || isDeletionMode || currentBox != null) {
        when {
            isSavingMode -> onDismiss()
            isDeletionMode -> viewModel.clearDeletionSelection()
            currentRow != null && !isSavingMode -> viewModel.selectRow(null)
            currentBox != null -> viewModel.selectBox(null)
        }
    }
    Scaffold(
        topBar = {
            val topBarColors = if (isDeletionMode) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                TopAppBarDefaults.topAppBarColors()
            }
            TopAppBar(
                title = {
                    val titleText = when {
                        isDeletionMode -> "${selectedBoxesForDeletion.size + selectedRowsForDeletion.size} seleccionados"
                        isSavingMode -> "Guardar (${scannedCards.size})"
                        currentRow != null && !isSavingMode -> currentRow.name
                        else -> "Gestión de Almacén"
                    }
                    Text(titleText)
                },
                colors = topBarColors,
                navigationIcon = {
                    when {
                        isDeletionMode -> {
                            IconButton(onClick = { viewModel.clearDeletionSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar borrado")
                            }
                        }
                        isSavingMode -> {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar")
                            }
                        }
                        currentRow != null -> {
                            IconButton(onClick = { viewModel.selectRow(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a Filas")
                            }
                        }
                        currentBox != null -> {
                            IconButton(onClick = { viewModel.selectBox(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a Cajas")
                            }
                        }
                    }
                },
                actions = {
                    if (isDeletionMode) {
                        IconButton(onClick = { viewModel.deleteSelectedItems() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar seleccionados")
                        }
                    } else if (isSavingMode) {
                        IconButton(
                            onClick = { viewModel.processInventorySave(scannedCards, onSaveInventory) },
                            enabled = canSave
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Confirmar")
                        }
                    }

                    if (!isDeletionMode && !isSavingMode && currentBox == null) {
                        IconButton(onClick = {
                            authClient.authorize(authRequest)
                                .addOnSuccessListener { authResult ->
                                    if (authResult.hasResolution()) {
                                        authResult.pendingIntent?.let { pendingIntent ->
                                            val intentSender = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                            authLauncher.launch(intentSender)
                                        }
                                    } else {
                                        authResult.accessToken?.let { token ->
                                            android.util.Log.d("GoogleAuth", "¡Token silencioso recuperado!")
                                            viewModel.exportToGoogleSheets(token)
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("GoogleAuth", "Error: ${e.message}")
                                    android.widget.Toast.makeText(context, "Error de conexión: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar a Google Sheets")
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            if (!isDeletionMode && currentRow == null) {
                FloatingActionButton(
                    onClick = {
                        if (currentBox != null) viewModel.onRowDialogVisibilityChanged(true)
                        else viewModel.onBoxDialogVisibilityChanged(true)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (currentBox == null) {
                if (boxes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aún no tienes cajas, presiona + para crear una",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "Selecciona una Caja",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(boxes) { boxWithRows ->
                            val rowCount = boxWithRows.rows.size
                            val isDeleteSelected = selectedBoxesForDeletion.contains(boxWithRows.box.boxId)

                            InventoryCard(
                                title = boxWithRows.box.name,
                                subtitle = if (rowCount > 0) "$rowCount fila${if (rowCount > 1) "s" else ""}" else null,
                                isSelected = false,
                                isDeleteSelected = isDeleteSelected,
                                onClick = {
                                    if (isDeletionMode) viewModel.toggleBoxForDeletion(boxWithRows.box.boxId)
                                    else viewModel.selectBox(boxWithRows)
                                },
                                onLongClick = {
                                    if (!isSavingMode) viewModel.toggleBoxForDeletion(boxWithRows.box.boxId)
                                }
                            )
                        }
                    }
                }
            }
            else if (currentRow == null || isSavingMode) {
                if (currentBox.rows.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Esta caja no tiene filas, presiona + para crear una",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "Caja: ${currentBox.box.name} > Selecciona Fila",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(currentBox.rows) { row ->

                            val cardCount = rowCardCounts[row.rowId] ?: 0

                            val isDeleteSelected = selectedRowsForDeletion.contains(row.rowId)

                            InventoryCard(
                                title = row.name,
                                subtitle = if (cardCount > 0) "$cardCount carta${if (cardCount > 1) "s" else ""}" else null,
                                isSelected = currentRow?.rowId == row.rowId,
                                isDeleteSelected = isDeleteSelected,
                                onClick = {
                                    if (isDeletionMode) viewModel.toggleRowForDeletion(row.rowId)
                                    else viewModel.selectRow(row)
                                },
                                onLongClick = {
                                    if (!isSavingMode) viewModel.toggleRowForDeletion(row.rowId)
                                }
                            )
                        }
                    }
                }
            }
            else {
                Text(
                    text = "${currentBox.box.name} > ${currentRow.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                if (cardsInRow.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aún no hay cartas en esta fila",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cardsInRow) { cardEntity ->
                            SavedCardRow(card = cardEntity)
                        }
                    }
                }
            }
            if (isSavingMode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Resumen a guardar:", style = MaterialTheme.typography.titleSmall)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(scannedCards) { card ->
                        ScannedCardRow(card = card)
                    }
                }
            }
        }
    }
    if (showCreateBoxDialog) {
        CreateItemDialog(
            title = "Nueva Caja",
            label = "Nombre de la caja",
            onConfirm = { name -> viewModel.confirmCreateBox(name) },
            onDismiss = { viewModel.onBoxDialogVisibilityChanged(false) }
        )
    }
    if (showCreateRowDialog) {
        CreateItemDialog(
            title = "Nueva Fila",
            label = "Nombre de la fila",
            onConfirm = { name -> viewModel.confirmCreateRow(name) },
            onDismiss = { viewModel.onRowDialogVisibilityChanged(false) }
        )
    }
    if (isExporting) {
        AlertDialog(
            onDismissRequest = {  },
            confirmButton = {},
            title = { Text("Exportando a la Nube...") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Construyendo tu Excel y subiendo a Google Drive...")
                }
            }
        )
    }
    exportSuccessUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportState() },
            title = { Text("¡Exportación Exitosa!") },
            text = {
                Text("Tu inventario completo se ha guardado correctamente como una hoja de cálculo en tu Google Drive.")
            },
            confirmButton = {
                Button(onClick = {
                    uriHandler.openUri(url)
                    viewModel.clearExportState()
                }) {
                    Text("Abrir en Sheets")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearExportState() }) {
                    Text("Cerrar")
                }
            }
        )
    }
    exportError?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportError() },
            title = {
                Text(
                    text = "Error al Exportar",
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text("Ocurrió un problema al intentar subir tu inventario a Google Sheets:\n\n$errorMessage")
            },
            confirmButton = {
                Button(onClick = { viewModel.clearExportError() }) {
                    Text("Entendido")
                }
            }
        )
    }
}