package com.iiitl.canteen

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.iiitl.canteen.data.remote.AuthDataSource
import com.iiitl.canteen.data.repository.AuthRepository

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
}
