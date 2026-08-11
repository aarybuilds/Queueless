package com.iiitl.canteen.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iiitl.canteen.data.model.MenuItem
import com.iiitl.canteen.data.repository.MenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MenuUiState(
    val items: List<MenuItem> = emptyList(),
    val groupedByCategory: Map<String, List<MenuItem>> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MenuViewModel(
    private val menuRepository: MenuRepository,
    // cafeteriaId is a constructor parameter because the decision of which
    // cafeteria to show is made before this ViewModel is created. Fetching
    // it internally would require a shared state mechanism that adds complexity
    // for no benefit.
    private val cafeteriaId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            menuRepository.getMenuItems(cafeteriaId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load menu."
                        )
                    }
                }
                .collect { items ->
                    // groupedByCategory is derived here so the Composable never
                    // computes it on the UI thread during recomposition.
                    _uiState.update {
                        it.copy(
                            items = items,
                            groupedByCategory = items.groupBy { item -> item.category },
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
}
