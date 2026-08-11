package com.iiitl.canteen

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iiitl.canteen.ui.auth.LoginScreen
import com.iiitl.canteen.ui.auth.LoginViewModel
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
                val loginViewModel: LoginViewModel = viewModel(
                    factory = appContainer.loginViewModelFactory
                )

                // Temporary: log auth state until navigation is wired up.
                LaunchedEffect(Unit) {
                    appContainer.authRepository.observeAuthState().collect { isLoggedIn ->
                        Log.d("AuthState", "User logged in: $isLoggedIn")
                    }
                }

                LoginScreen(viewModel = loginViewModel)
            }
        }
    }
}