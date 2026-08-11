package com.iiitl.canteen.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iiitl.canteen.data.model.MenuItem

@Composable
fun MenuScreen(
    uiState: MenuUiState,
    // Takes state + lambdas instead of the ViewModel so this composable
    // can be previewed with static data and tested without a ViewModel.
    onItemClick: (MenuItem) -> Unit
) {
    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        uiState.items.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No items available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                uiState.groupedByCategory.forEach { (category, categoryItems) ->
                    item(key = "header_$category") {
                        CategoryHeader(category = category)
                    }
                    items(
                        items = categoryItems,
                        key = { it.id }
                    ) { menuItem ->
                        MenuItemRow(menuItem = menuItem, onItemClick = onItemClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String) {
    Column {
        Text(
            text = category,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider()
    }
}

@Composable
private fun MenuItemRow(menuItem: MenuItem, onItemClick: (MenuItem) -> Unit) {
    // Unavailable items are greyed out and ignore clicks — no separate
    // "disabled" variant needed, the same row just uses muted colors.
    val contentAlpha = if (menuItem.isAvailable) 1f else 0.38f
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)

    Surface(
        onClick = { if (menuItem.isAvailable) onItemClick(menuItem) },
        enabled = menuItem.isAvailable,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = menuItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Text(
                    text = "~${menuItem.prepTimeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
            Text(
                text = "₹${"%.2f".format(menuItem.price)}",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}
