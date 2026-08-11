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

data class OrderHistoryUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class OrderHistoryViewModel(
    private val orderRepository: OrderRepository,
    private val studentUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderHistoryUiState())
    val uiState: StateFlow<OrderHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderRepository.observeStudentOrders(studentUid)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load order history.")
                    }
                }
                .collect { orders ->
                    _uiState.update { it.copy(orders = orders, isLoading = false, errorMessage = null) }
                }
        }
    }
}
