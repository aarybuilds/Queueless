package com.iiitl.canteen.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderStatusUiState(
    val order: Order? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class OrderStatusViewModel(
    private val orderRepository: OrderRepository,
    // orderId is a constructor param for the same reason cafeteriaId is in
    // MenuViewModel: the ViewModel is fully configured at creation time, never
    // in a transient "no order yet" state.
    private val orderId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderStatusUiState())
    val uiState: StateFlow<OrderStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderRepository.observeOrder(orderId)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load order.")
                    }
                }
                .collect { order ->
                    if (order == null && !_uiState.value.isLoading) {
                        // Document existed once (we navigated here with its ID) and is now gone.
                        _uiState.update { it.copy(errorMessage = "Order not found.") }
                    } else {
                        _uiState.update { it.copy(order = order, isLoading = false, errorMessage = null) }
                    }
                }
        }
    }
}
