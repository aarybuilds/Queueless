package com.iiitl.canteen

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iiitl.canteen.ui.cafeteria.CafeteriaSelectionScreen
import com.iiitl.canteen.ui.menu.MenuScreen
import com.iiitl.canteen.ui.menu.MenuViewModel
import com.iiitl.canteen.ui.theme.QueuelessTheme

class MainActivity : ComponentActivity() {

    // AppContainer lives on the Activity so the ViewModel factory can reach it.
    // The ViewModel itself survives rotation; the container just needs to outlive
    // the factory call, which happens on the first composition.
    private val appContainer = AppContainer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QueuelessTheme {
                // Temporary: log auth state until navigation is wired up.
                LaunchedEffect(Unit) {
                    appContainer.authRepository.observeAuthState().collect { isLoggedIn ->
                        Log.d("AuthState", "User logged in: $isLoggedIn")
                    }
                }

                // Temporary state-based routing — will be replaced with NavHost.
                // null means no cafeteria has been selected yet.
                var selectedCafeteriaId by remember { mutableStateOf<String?>(null) }

                when (val cafeteriaId = selectedCafeteriaId) {
                    null -> CafeteriaSelectionScreen(
                        onCafeteriaSelected = { selectedCafeteriaId = it }
                    )
                    else -> {
                        // key() forces a new ViewModel when the cafeteria changes,
                        // discarding the previous cafeteria's state cleanly.
                        val menuViewModel: MenuViewModel = viewModel(
                            key = cafeteriaId,
                            factory = appContainer.menuViewModelFactory(cafeteriaId)
                        )
                        val uiState by menuViewModel.uiState.collectAsStateWithLifecycle()
                        MenuScreen(
                            uiState = uiState,
                            onItemClick = { item -> Log.d("MenuScreen", "Clicked: ${item.name}") }
                        )
                    }
                }
            }
        }
    }
}