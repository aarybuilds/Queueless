package com.iiitl.canteen.ui.cafe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderStatus
import com.iiitl.canteen.data.repository.AuthRepository
import com.iiitl.canteen.data.repository.CafeOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CafeQueueUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    // claimError is separate so the UI can show it as a transient snackbar
    // without clearing the main error state.
    val claimError: String? = null
)

class CafeOrderQueueViewModel(
    private val cafeOrderRepository: CafeOrderRepository,
    private val authRepository: AuthRepository,
    private val cafeteriaId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CafeQueueUiState())
    val uiState: StateFlow<CafeQueueUiState> = _uiState.asStateFlow()

    // Fetched once at init so every claim call has the name without a repeated read.
    private var staffUid: String = ""
    private var staffName: String = ""

    init {
        viewModelScope.launch {
            staffUid = authRepository.getCurrentUserId() ?: ""
            if (staffUid.isNotEmpty()) {
                staffName = cafeOrderRepository.getStaffName(staffUid)
            }

            cafeOrderRepository.observeActiveOrders(cafeteriaId)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load orders.")
                    }
                }
                .collect { orders ->
                    val sortedOrders = orders.sortedByDescending { it.placedAt }
                    _uiState.update { it.copy(orders = sortedOrders, isLoading = false, errorMessage = null) }
                }

        }
    }

    fun claimOrder(orderId: String) {
        if (staffUid.isEmpty()) {
            _uiState.update { it.copy(claimError = "Not signed in as staff.") }
            return
        }
        viewModelScope.launch {
            cafeOrderRepository.claimOrder(orderId, staffUid, staffName).onFailure { error ->
                _uiState.update { it.copy(claimError = error.message) }
            }
        }
    }

    fun updateStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            cafeOrderRepository.updateOrderStatus(orderId, newStatus).onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun markUnavailable(orderId: String, itemAvailabilities: Map<String, Int>) {
        viewModelScope.launch {
            cafeOrderRepository.markItemsUnavailable(orderId, itemAvailabilities).onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun clearClaimError() {
        _uiState.update { it.copy(claimError = null) }
    }
}
