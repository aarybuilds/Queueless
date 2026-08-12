package com.iiitl.canteen.ui.cafe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeOrderQueueScreen(
    uiState: CafeQueueUiState,
    onClaimOrder: (String) -> Unit,
    onUpdateStatus: (String, OrderStatus) -> Unit,
    onMarkUnavailable: (String, Map<String, Int>) -> Unit,
    onClearClaimError: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Queue", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.orders.isEmpty() -> {
                Text(
                    text = "No active orders in queue",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                val readyOrders = uiState.orders.filter { it.status == OrderStatus.READY }
                val activeOrders = uiState.orders.filter { it.status != OrderStatus.READY }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (activeOrders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active Queue (${activeOrders.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
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
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                        item {
                            Text(
                                text = "Ready for Collection (${readyOrders.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20),
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
}

@Composable
private fun OrderCard(
    order: Order,
    isReady: Boolean,
    onClaimOrder: (String) -> Unit,
    onUpdateStatus: (String, OrderStatus) -> Unit,
    onMarkUnavailable: (String, Map<String, Int>) -> Unit
) {
    var showUnavailableChooser by remember(order.id) { mutableStateOf(false) }

    val border = if (isReady) BorderStroke(2.dp, Color(0xFF1B5E20)) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${order.orderNumber}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = order.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isReady) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "${order.studentName} (${order.studentRollNumber})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            order.items.forEach { item ->
                OrderItemLine(item = item)
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (order.status) {
                OrderStatus.PLACED -> {
                    if (showUnavailableChooser) {
                        UnavailableChooser(
                            items = order.items,
                            onMarkUnavailable = { availabilities ->
                                onMarkUnavailable(order.id, availabilities)
                                showUnavailableChooser = false
                            },
                            onRejectOrder = {
                                onUpdateStatus(order.id, OrderStatus.REJECTED)
                                showUnavailableChooser = false
                            },
                            onCancel = {
                                showUnavailableChooser = false
                            }
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onClaimOrder(order.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Accept", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showUnavailableChooser = true },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Can't prepare")
                            }
                        }
                    }
                }

                OrderStatus.AWAITING_CONFIRMATION -> {
                    Text(
                        text = "Waiting for student confirmation...",
                        style = MaterialTheme.typography.bodyMedium,
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }

                OrderStatus.READY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onUpdateStatus(order.id, OrderStatus.COLLECTED) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Collected", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onUpdateStatus(order.id, OrderStatus.EXPIRED) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Not collected")
                        }
                    }
                }

                else -> { /* Terminal states */ }
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
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
private fun UnavailableChooser(
    items: List<OrderItem>,
    onMarkUnavailable: (Map<String, Int>) -> Unit,
    onRejectOrder: () -> Unit,
    onCancel: () -> Unit
) {
    var availableQuantities by remember(items) {
        mutableStateOf(items.associate { it.itemId to it.quantity })
    }

    val allUnavailable = items.isNotEmpty() && items.all { (availableQuantities[it.itemId] ?: 0) == 0 }
    val noneReduced = items.all { (availableQuantities[it.itemId] ?: it.quantity) == it.quantity }
    val isPartial = !allUnavailable && !noneReduced

    Column {
        Text(
            text = "Adjust available quantities:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Select All / Mark All Unavailable Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = allUnavailable,
                onCheckedChange = { checkAll ->
                    availableQuantities = if (checkAll) {
                        items.associate { it.itemId to 0 }
                    } else {
                        items.associate { it.itemId to it.quantity }
                    }
                }
            )
            Text(
                text = "Mark all unavailable (Reject order)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Per-item quantity selector
        items.forEach { item ->
            val currentQty = availableQuantities[item.itemId] ?: item.quantity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ordered: ${item.quantity} | Available: $currentQty",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentQty == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (currentQty > 0) {
                                availableQuantities = availableQuantities + (item.itemId to (currentQty - 1))
                            }
                        },
                        enabled = currentQty > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Reduce available quantity",
                            tint = if (currentQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Text(
                        text = "$currentQty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = {
                            if (currentQty < item.quantity) {
                                availableQuantities = availableQuantities + (item.itemId to (currentQty + 1))
                            }
                        },
                        enabled = currentQty < item.quantity
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase available quantity",
                            tint = if (currentQty < item.quantity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allUnavailable) {
                Button(
                    onClick = onRejectOrder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reject order", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onMarkUnavailable(availableQuantities) },
                    enabled = isPartial,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Notify student", fontWeight = FontWeight.Bold)
                }
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
