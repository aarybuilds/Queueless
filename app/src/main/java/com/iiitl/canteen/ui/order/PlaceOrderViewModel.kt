package com.iiitl.canteen.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.repository.AuthRepository
import com.iiitl.canteen.data.repository.CartItem
import com.iiitl.canteen.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceOrderUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Non-null signals that placement succeeded; the UI navigates on this.
    val placedOrderId: String? = null
)

class PlaceOrderViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceOrderUiState())
    val uiState: StateFlow<PlaceOrderUiState> = _uiState.asStateFlow()

    fun placeOrder(
        cafeteriaId: String,
        cartItems: List<CartItem>,
        studentName: String,
        studentRollNumber: String
    ) {
        val uid = authRepository.getCurrentUserId()
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "You must be signed in to place an order.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = orderRepository.placeOrder(
                cafeteriaId = cafeteriaId,
                studentUid = uid,
                studentName = studentName,
                studentRollNumber = studentRollNumber,
                cartItems = cartItems
            )

            result.fold(
                onSuccess = { orderId ->
                    _uiState.update { it.copy(isLoading = false, placedOrderId = orderId) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to place order. Please try again."
                        )
                    }
                }
            )
        }
    }
}
