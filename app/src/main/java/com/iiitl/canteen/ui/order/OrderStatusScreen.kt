package com.iiitl.canteen.ui.order

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderItem
import com.iiitl.canteen.data.model.OrderStatus

private fun OrderStatus.toDisplayString(): String = when (this) {
    OrderStatus.PLACED                -> "Order placed, waiting for confirmation"
    OrderStatus.AWAITING_CONFIRMATION -> "Some items are unavailable — please confirm or cancel"
    OrderStatus.ACCEPTED              -> "Order accepted by the cafeteria"
    OrderStatus.PREPARING             -> "Your order is being prepared"
    OrderStatus.READY                 -> "Your order is ready! Go collect it."
    OrderStatus.COLLECTED             -> "Order collected"
    OrderStatus.CANCELLED             -> "Order cancelled"
    OrderStatus.REJECTED              -> "Order rejected"
    OrderStatus.EXPIRED               -> "Order expired — not collected in time"
}

private fun OrderStatus.toStepLabel(): String = when (this) {
    OrderStatus.PLACED    -> "Placed"
    OrderStatus.ACCEPTED  -> "Accepted"
    OrderStatus.PREPARING -> "Preparing"
    OrderStatus.READY     -> "Ready"
    else                  -> name
}

private fun OrderStatus.badgeColor(): Color = when (this) {
    OrderStatus.PLACED,
    OrderStatus.AWAITING_CONFIRMATION -> Color(0xFFFFB300)
    OrderStatus.ACCEPTED,
    OrderStatus.PREPARING             -> Color(0xFF2E7D32)
    OrderStatus.READY,
    OrderStatus.COLLECTED             -> Color(0xFF4CAF50)
    OrderStatus.CANCELLED,
    OrderStatus.REJECTED,
    OrderStatus.EXPIRED               -> Color(0xFFE53935)
}

private val progressSteps = listOf(
    OrderStatus.PLACED,
    OrderStatus.ACCEPTED,
    OrderStatus.PREPARING,
    OrderStatus.READY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderStatusScreen(
    uiState: OrderStatusUiState,
    onBack: () -> Unit,
    onConfirmReducedOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onViewHistory: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Tracker", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Order History",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        onCancelOrder = onCancelOrder
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderContent(
    order: Order,
    onBack: () -> Unit,
    onConfirmReducedOrder: () -> Unit,
    onCancelOrder: () -> Unit
) {
    val isTerminal = order.status in listOf(
        OrderStatus.REJECTED,
        OrderStatus.CANCELLED,
        OrderStatus.EXPIRED
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Order #${order.orderNumber}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = order.status.toDisplayString(),
                            fontSize = 13.sp,
                            color = if (order.status == OrderStatus.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = order.status.badgeColor(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = order.status.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Hide progress bar for terminal states (REJECTED, CANCELLED, EXPIRED)
        if (!isTerminal) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        StatusProgressRow(currentStatus = order.status)
                    }
                }
            }
        }

        item { StatusBanner(order = order) }

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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancelOrder,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Order Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    order.items.forEach { orderItem ->
                        OrderItemRow(orderItem = orderItem)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "₹${"%.2f".format(order.totalAmount)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Back to Canteen", fontWeight = FontWeight.Bold)
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
            val dotColor = if (isPast) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCircle(color = dotColor)
                    }
                }
                Text(
                    text = step.toStepLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isPast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Visible,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            if (index < progressSteps.lastIndex) {
                val lineColor = if (progressSteps.indexOf(currentStatus).let { it >= 0 && index < it })
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.fillMaxWidth()
        )
        OrderStatus.REJECTED -> Text(
            text = "Your order was rejected — items were unavailable at the canteen.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935),
            modifier = Modifier.fillMaxWidth()
        )
        OrderStatus.CANCELLED -> Text(
            text = "This order was cancelled.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935)
        )
        OrderStatus.EXPIRED -> Text(
            text = "This order expired because it was not collected.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935)
        )
        else -> { /* no banner for other states */ }
    }
}

@Composable
private fun OrderItemRow(orderItem: OrderItem) {
    val textColor = if (orderItem.isAvailable) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
