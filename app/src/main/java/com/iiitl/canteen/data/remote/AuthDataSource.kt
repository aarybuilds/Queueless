package com.iiitl.canteen.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

// This is the only file in the project that touches FirebaseAuth directly.
// All Firebase exceptions are caught here and converted to Result.failure so
// that callers never have to handle raw Firebase exceptions.
class AuthDataSource(private val auth: FirebaseAuth) {

    suspend fun signUp(email: String, password: String): Result<String> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.uid ?: error("Firebase returned no UID after sign-up")
        }

    suspend fun signIn(email: String, password: String): Result<String> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.uid ?: error("Firebase returned no UID after sign-in")
        }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun observeAuthState() = auth.currentUser
}
