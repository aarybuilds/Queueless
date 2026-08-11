package com.iiitl.canteen.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iiitl.canteen.data.model.MenuItem

// Defined here because it is only ever constructed in the UI layer and
// passed straight through to PlaceOrderViewModel via the onPlaceOrder callback.
data class StudentInfo(val name: String, val rollNumber: String)

@Composable
fun CartScreen(
    uiState: CartUiState,
    studentInfo: StudentInfo,
    onAddItem: (MenuItem) -> Unit,
    onRemoveItem: (MenuItem) -> Unit,
    onPlaceOrder: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Your Cart",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(
                items = uiState.items.entries.toList(),
                key = { it.key.id }
            ) { (menuItem, quantity) ->
                CartItemRow(
                    menuItem = menuItem,
                    quantity = quantity,
                    onAdd = { onAddItem(menuItem) },
                    onRemove = { onRemoveItem(menuItem) }
                )
                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "₹${"%.2f".format(uiState.totalAmount)}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onPlaceOrder,
            enabled = uiState.items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Place Order")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Menu")
        }
    }
}

@Composable
private fun CartItemRow(
    menuItem: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = menuItem.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "₹${"%.2f".format(menuItem.price)} × $quantity = ₹${"%.2f".format(menuItem.price * quantity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRemove) {
                // Minus icon composed from the Add icon shape via rotation is not
                // reliable — use a Text label for the decrement button instead.
                Text(text = "−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add one more")
            }
        }
    }
}
