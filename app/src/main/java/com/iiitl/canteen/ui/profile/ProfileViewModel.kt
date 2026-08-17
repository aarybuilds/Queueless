package com.iiitl.canteen.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val rollNumber: String = "",
    val assignedCafeteriaId: String = "",
    val isLoading: Boolean = true
)

class ProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId()
            if (uid != null) {
                val profile = authRepository.getUserProfile(uid)
                if (profile != null) {
                    // Fetch assigned cafeteria ID explicitly from the repository for staff accounts
                    val assignedCafeteriaId = if (profile.role.name == "CAFE_STAFF") {
                        authRepository.getAssignedCafeteriaId(uid) ?: ""
                    } else ""
                    _uiState.update {
                        it.copy(
                            name = profile.name,
                            email = profile.email,
                            role = profile.role.name,
                            rollNumber = profile.rollNumber,
                            assignedCafeteriaId = assignedCafeteriaId,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logout() {
        authRepository.signOut()
    }
}

