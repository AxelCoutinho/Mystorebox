package com.example.mystorebox.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mystorebox.data.network.ScryfallCard
import com.example.mystorebox.ui.screens.inventory.GroupedCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativePrintsBottomSheet(
    cardGroupToEdit: GroupedCard,
    availablePrints: List<ScryfallCard>,
    onDismissRequest: () -> Unit,
    onConfirmDistribution: (Map<ScryfallCard, Int>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var distribution by remember {
        mutableStateOf(
            availablePrints.associateWith { print ->
                if (print.id == cardGroupToEdit.card.id) cardGroupToEdit.quantity else 0
            }
        )
    }

    val totalAssigned = distribution.values.sum()
    val totalAvailable = cardGroupToEdit.quantity
    val remainingToAssign = totalAvailable - totalAssigned
    val isDistributionValid = totalAssigned == totalAvailable

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Reasignando las ediciones: ${cardGroupToEdit.card.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Te faltan por asignar: $remainingToAssign cartas",
                style = MaterialTheme.typography.bodyMedium,
                color = if (remainingToAssign == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availablePrints) { print ->
                    PrintDistributionRow(
                        card = print,
                        currentQuantity = distribution[print] ?: 0,
                        onIncrease = {
                            if (totalAssigned < totalAvailable) {
                                distribution = distribution.toMutableMap().apply {
                                    this[print] = (this[print] ?: 0) + 1
                                }
                            }
                        },
                        onDecrease = {
                            val current = distribution[print] ?: 0
                            if (current > 0) {
                                distribution = distribution.toMutableMap().apply {
                                    this[print] = current - 1
                                }
                            }
                        }
                    )
                }
            }

            Button(
                onClick = { onConfirmDistribution(distribution) },
                enabled = isDistributionValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Confirmar Ediciones")
            }
        }
    }
}

@Composable
private fun PrintDistributionRow(
    card: ScryfallCard,
    currentQuantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = card.image_uris?.small,
                contentDescription = null,
                modifier = Modifier
                    .width(45.dp)
                    .aspectRatio(2.5f / 3.5f)
                    .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${card.set.uppercase()} • #${card.collector_number ?: "?"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (card.prices?.usd != null) {
                    Text(
                        text = "$${card.prices?.usd}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDecrease, enabled = currentQuantity > 0) {
                    Icon(Icons.Default.Remove, contentDescription = "Menos")
                }
                Text(
                    text = "$currentQuantity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 24.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onIncrease) {
                    Icon(Icons.Default.Add, contentDescription = "Más")
                }
            }
        }
    }
}