package com.example.mystorebox.ui.screens.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.mystorebox.data.network.ScryfallCard
import com.example.mystorebox.ui.composables.AlternativePrintsBottomSheet
import com.example.mystorebox.utils.CardImageAnalyzer
import java.util.concurrent.Executors

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToInventory: (List<ScryfallCard>) -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                if (uiState.trayItems.isNotEmpty()) {
                    val totalCards = uiState.trayItems.sumOf { it.quantity }
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.setTrayVisibility(true) },
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Bandeja") },
                        text = { Text("Bandeja ($totalCards)") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                CameraView(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    onTextFound = { text -> viewModel.onTextDetected(text) }
                )
                CardOverlay(state = uiState)
            }
        }

        if (uiState.isTrayVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setTrayVisibility(false) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ) {
                TrayContent(
                    trayItems = uiState.trayItems,
                    onIncrease = { cardId -> viewModel.increaseTrayItemQuantity(cardId) },
                    onDecrease = { cardId -> viewModel.decreaseTrayItemQuantity(cardId) },
                    onEditClick = { card -> viewModel.fetchAlternativePrints(card) },
                    onSaveClick = {
                        val flattenedCards = uiState.trayItems.flatMap { item -> List(item.quantity) { item.card } }
                        onNavigateToInventory(flattenedCards)
                        viewModel.clearTray()
                    }
                )
            }
        }

        if (uiState.cardBeingEdited != null && uiState.availablePrints.isNotEmpty()) {
            AlternativePrintsBottomSheet(
                availablePrints = uiState.availablePrints,
                onDismissRequest = { viewModel.clearAlternativePrints() },
                onPrintSelected = { selectedCard ->
                    viewModel.swapTrayItemPrint(
                        oldCardId = uiState.cardBeingEdited!!.id,
                        newCard = selectedCard
                    )
                }
            )
        }
    }
}

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    viewModel: ScanViewModel,
    onTextFound: (String) -> Unit
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val executor = Executors.newSingleThreadExecutor()
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, CardImageAnalyzer(
                            onTextFound = { text ->
                                viewModel.onTextDetected(text)
                            },
                            onNoText = {
                                viewModel.onNoTextDetected()
                            }
                        ))
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                camera.cameraControl.setZoomRatio(1.2f)

            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

@Composable
fun CardOverlay(state: ScannerUiState) {
    if (state.cardFound == null && !state.isLoading && state.detectedText.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Buscando carta...", color = Color.White)
            }

            state.cardFound?.let { card ->
                Text(
                    text = card.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                AsyncImage(
                    model = card.image_uris?.normal,
                    contentDescription = "Magic Card",
                    modifier = Modifier.height(300.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Precio: $${card.prices?.usd ?: "?"}",
                    color = Color.Green,
                    fontSize = 16.sp
                )
            }

            if (state.cardFound == null && !state.isLoading) {
                Text("Texto detectado: ${state.detectedText}", color = Color.Gray)
            }
        }
    }
}

@Composable
fun TrayContent(
    trayItems: List<TrayItem>,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onEditClick: (ScryfallCard) -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Bandeja de Escaneo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (trayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("La bandeja está vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trayItems) { item ->
                    TrayItemRow(
                        item = item,
                        onIncrease = { onIncrease(item.card.id) },
                        onDecrease = { onDecrease(item.card.id) },
                        onEdit = { onEditClick(item.card) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val totalCards = trayItems.sumOf { it.quantity }
            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Guardar $totalCards cartas en Inventario",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun TrayItemRow(
    item: TrayItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.card.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.card.set.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar Edición", modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Restar", modifier = Modifier.size(18.dp))
                }

                Text(
                    text = item.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.defaultMinSize(minWidth = 32.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Sumar", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}