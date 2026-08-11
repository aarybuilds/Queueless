package com.iiitl.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.iiitl.canteen.data.remote.AuthDataSource
import com.iiitl.canteen.data.repository.AuthRepository
import com.iiitl.canteen.ui.auth.LoginViewModel

// Manual DI: we hold singleton Firebase instances here and build the
// repository graph by hand. We're skipping Hilt deliberately — annotation
// processors add build complexity that isn't worth it at this stage of the
// project. If the graph grows, migrate then.
class AppContainer {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val authDataSource: AuthDataSource by lazy {
        AuthDataSource(firebaseAuth)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(authDataSource, firestore)
    }

    // A factory is required because LoginViewModel takes a constructor argument.
    // Without it, viewModel() would call the zero-arg constructor and crash.
    val loginViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(authRepository) as T
    }
}
