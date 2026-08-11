package com.iiitl.canteen.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // true = show login fields; false = show signup fields
    val isLoginMode: Boolean = true
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }
    fun onRollNumberChange(value: String) = _uiState.update { it.copy(rollNumber = value, errorMessage = null) }

    fun toggleMode() = _uiState.update {
        it.copy(isLoginMode = !it.isLoginMode, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = if (state.isLoginMode) {
                authRepository.signIn(state.email, state.password)
            } else {
                authRepository.signUp(state.email, state.password, state.name, state.rollNumber)
            }

            result.fold(
                onSuccess = {
                    // Navigation is handled by the auth state observer in the UI,
                    // so nothing to do here on success.
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "An unexpected error occurred.")
                    }
                }
            )
        }
    }
}
