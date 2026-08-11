package com.iiitl.canteen.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderItem
import com.iiitl.canteen.data.model.OrderStatus

// Maps each OrderStatus to a student-facing sentence.
private fun OrderStatus.toDisplayString(): String = when (this) {
    OrderStatus.PLACED                -> "Order placed, waiting for confirmation"
    OrderStatus.AWAITING_CONFIRMATION -> "Some items are unavailable — please confirm or cancel"
    OrderStatus.ACCEPTED              -> "Order accepted by the cafeteria"
    OrderStatus.PREPARING             -> "Your order is being prepared"
    OrderStatus.READY                 -> "Your order is ready! Go collect it."
    OrderStatus.COLLECTED             -> "Order collected"
    OrderStatus.CANCELLED             -> "Order cancelled"
    OrderStatus.REJECTED              -> "Order rejected by the cafeteria"
    OrderStatus.EXPIRED               -> "Order expired — not collected in time"
}

private fun OrderStatus.toStepLabel(): String = when (this) {
    OrderStatus.PLACED    -> "Placed"
    OrderStatus.ACCEPTED  -> "Accepted"
    OrderStatus.PREPARING -> "Preparing"
    OrderStatus.READY     -> "Ready"
    else                  -> name
}

// Steps shown in the linear progress row. Terminal/error states are excluded
// because they don't fit a linear progression.
private val progressSteps = listOf(
    OrderStatus.PLACED,
    OrderStatus.ACCEPTED,
    OrderStatus.PREPARING,
    OrderStatus.READY
)

@Composable
fun OrderStatusScreen(
    uiState: OrderStatusUiState,
    onBack: () -> Unit,
    onConfirmReducedOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onViewHistory: () -> Unit = {}
) {
    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage != null && uiState.order == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        uiState.order != null -> {
            OrderContent(
                order = uiState.order,
                onBack = onBack,
                onConfirmReducedOrder = onConfirmReducedOrder,
                onCancelOrder = onCancelOrder,
                onViewHistory = onViewHistory
            )
        }
    }
}

@Composable
private fun OrderContent(
    order: Order,
    onBack: () -> Unit,
    onConfirmReducedOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onViewHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.orderNumber}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = order.status.toDisplayString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onViewHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Order History"
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item { StatusProgressRow(currentStatus = order.status) }

        item { HorizontalDivider() }

        // Status-specific banners
        item { StatusBanner(order = order) }

        // AWAITING_CONFIRMATION action buttons
        if (order.status == OrderStatus.AWAITING_CONFIRMATION) {
            item {
                val unavailable = order.items.filter { !it.isAvailable }
                if (unavailable.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "The following items are unavailable:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        unavailable.forEach { item ->
                            Text(
                                text = "• ${item.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onConfirmReducedOrder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm reduced order")
                    }
                    Button(
                        onClick = onCancelOrder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel order")
                    }
                }
            }
        }

        item { HorizontalDivider() }

        item {
            Text(
                text = "Items",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(order.items) { orderItem ->
            OrderItemRow(orderItem = orderItem)
        }

        item { HorizontalDivider() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "₹${"%.2f".format(order.totalAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun StatusProgressRow(currentStatus: OrderStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        progressSteps.forEachIndexed { index, step ->
            val isPast = progressSteps.indexOf(currentStatus).let { it >= 0 && index <= it }
            val color = if (isPast) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Filled dot for reached steps, hollow for upcoming.
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = color)
                    }
                }
                Text(
                    text = step.toStepLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    overflow = TextOverflow.Visible,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            // Connector line between dots (except after the last step)
            if (index < progressSteps.lastIndex) {
                val lineColor = if (progressSteps.indexOf(currentStatus).let { it >= 0 && index < it })
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = lineColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(order: Order) {
    when (order.status) {
        OrderStatus.READY -> Text(
            text = "Your order is ready! Go collect it.",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF2E7D32),  // Material green 800 — readable on both light and dark
            modifier = Modifier.fillMaxWidth()
        )
        OrderStatus.REJECTED -> Text(
            text = "Your order was rejected by the cafeteria.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        OrderStatus.CANCELLED -> Text(
            text = "This order was cancelled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        OrderStatus.EXPIRED -> Text(
            text = "This order expired because it was not collected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        else -> { /* no banner for other states */ }
    }
}

@Composable
private fun OrderItemRow(orderItem: OrderItem) {
    val textColor = if (orderItem.isAvailable) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${orderItem.name} × ${orderItem.quantity}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "₹${"%.2f".format(orderItem.priceAtOrder * orderItem.quantity)}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}
