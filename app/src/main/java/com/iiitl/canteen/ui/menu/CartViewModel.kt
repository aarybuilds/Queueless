package com.iiitl.canteen.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartUiState(
    val items: Map<MenuItem, Int> = emptyMap(),
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val errorMessage: String? = null
)

class CartViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    fun addItem(item: MenuItem) {
        val current = _uiState.value.items

        // Enforce the 3-distinct-item cap before touching the map.
        if (!current.containsKey(item) && current.size >= 3) {
            _uiState.update { it.copy(errorMessage = "You can add at most 3 different items per order.") }
            return
        }

        val updated = current.toMutableMap()
        updated[item] = (updated[item] ?: 0) + 1
        _uiState.update { it.copy(items = updated, errorMessage = null).recompute() }
    }

    fun removeItem(item: MenuItem) {
        val current = _uiState.value.items.toMutableMap()
        val qty = current[item] ?: return
        if (qty <= 1) current.remove(item) else current[item] = qty - 1
        _uiState.update { it.copy(items = current, errorMessage = null).recompute() }
    }

    fun clearCart() {
        _uiState.update { CartUiState() }
    }

    // Extension so recompute is co-located with CartUiState but not part of its data.
    private fun CartUiState.recompute(): CartUiState = copy(
        totalAmount = items.entries.sumOf { (item, qty) -> item.price * qty },
        itemCount = items.values.sum()
    )
}
