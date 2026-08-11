package com.iiitl.canteen.ui.cafe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderItem
import com.iiitl.canteen.data.model.OrderStatus
import kotlinx.coroutines.delay

@Composable
fun CafeOrderQueueScreen(
    uiState: CafeQueueUiState,
    onClaimOrder: (String) -> Unit,
    onUpdateStatus: (String, OrderStatus) -> Unit,
    onMarkUnavailable: (String, List<String>) -> Unit,
    onClearClaimError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.orders.isEmpty() -> {
                Text(
                    text = "No active orders",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                // READY orders are visually separated: staff must collect them
                // differently from orders still in progress. Grouping prevents
                // READY orders from getting buried in a long active queue.
                val readyOrders = uiState.orders.filter { it.status == OrderStatus.READY }
                val activeOrders = uiState.orders.filter { it.status != OrderStatus.READY }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    if (activeOrders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active Orders",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(activeOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                isReady = false,
                                onClaimOrder = onClaimOrder,
                                onUpdateStatus = onUpdateStatus,
                                onMarkUnavailable = onMarkUnavailable
                            )
                        }
                    }

                    if (readyOrders.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item {
                            Text(
                                text = "Ready for Collection",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(readyOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                isReady = true,
                                onClaimOrder = onClaimOrder,
                                onUpdateStatus = onUpdateStatus,
                                onMarkUnavailable = onMarkUnavailable
                            )
                        }
                    }
                }
            }
        }

        // Snackbar for transient claim errors — auto-dismissed after 3 s.
        if (uiState.claimError != null) {
            LaunchedEffect(uiState.claimError) {
                delay(3000)
                onClearClaimError()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(uiState.claimError)
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    isReady: Boolean,
    onClaimOrder: (String) -> Unit,
    onUpdateStatus: (String, OrderStatus) -> Unit,
    onMarkUnavailable: (String, List<String>) -> Unit
) {
    // Local state for the "Can't prepare" item checklist — pure UI concern,
    // doesn't belong in the ViewModel.
    var showUnavailableChooser by remember(order.id) { mutableStateOf(false) }
    var unavailableSelections by remember(order.id) { mutableStateOf(setOf<String>()) }

    val cardBackground = if (isReady)
        Color(0xFFE8F5E9)  // light green tint for READY group
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Large order number — the staff's primary reference at the counter.
            Text(
                text = "#${order.orderNumber}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = order.studentName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            order.items.forEach { item ->
                OrderItemLine(item = item)
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (order.status) {
                OrderStatus.PLACED -> {
                    if (showUnavailableChooser) {
                        UnavailableChooser(
                            items = order.items,
                            selections = unavailableSelections,
                            onToggle = { itemId ->
                                unavailableSelections = if (itemId in unavailableSelections)
                                    unavailableSelections - itemId
                                else
                                    unavailableSelections + itemId
                            },
                            onConfirm = {
                                onMarkUnavailable(order.id, unavailableSelections.toList())
                                showUnavailableChooser = false
                                unavailableSelections = emptySet()
                            },
                            onCancel = {
                                showUnavailableChooser = false
                                unavailableSelections = emptySet()
                            }
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onClaimOrder(order.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Accept")
                            }
                            OutlinedButton(
                                onClick = { showUnavailableChooser = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Can't prepare")
                            }
                        }
                    }
                }

                OrderStatus.AWAITING_CONFIRMATION -> {
                    Text(
                        text = "Waiting for student confirmation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OrderStatus.ACCEPTED, OrderStatus.PREPARING -> {
                    val nextStatus = if (order.status == OrderStatus.ACCEPTED)
                        OrderStatus.PREPARING else OrderStatus.READY
                    val label = if (order.status == OrderStatus.ACCEPTED)
                        "Start preparing" else "Mark Ready"
                    Button(
                        onClick = { onUpdateStatus(order.id, nextStatus) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }

                OrderStatus.READY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUpdateStatus(order.id, OrderStatus.COLLECTED) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Collected")
                        }
                        OutlinedButton(
                            onClick = { onUpdateStatus(order.id, OrderStatus.EXPIRED) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Not collected")
                        }
                    }
                }

                else -> { /* terminal states don't appear in the active queue */ }
            }
        }
    }
}

@Composable
private fun OrderItemLine(item: OrderItem) {
    val alpha = if (item.isAvailable) 1f else 0.4f
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Text(
        text = "• ${item.name} × ${item.quantity}" + if (!item.isAvailable) " (unavailable)" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}

@Composable
private fun UnavailableChooser(
    items: List<OrderItem>,
    selections: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Text(
            text = "Select items you cannot prepare:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = item.itemId in selections,
                    onCheckedChange = { onToggle(item.itemId) }
                )
                Text(text = "${item.name} × ${item.quantity}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConfirm,
                enabled = selections.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Notify student")
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
}
