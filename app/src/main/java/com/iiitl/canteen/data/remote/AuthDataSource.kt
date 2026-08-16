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
            val uid = result.user?.uid ?: error("Firebase returned no UID after sign-up")
            // Verification email send is attempted but swallowed if it fails so account creation succeeds.
            runCatching { auth.currentUser?.sendEmailVerification()?.await() }
            uid
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

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    // Reload fetches fresh token claims from Firebase Auth server, updating local cache.
    suspend fun reloadUser() {
        auth.currentUser?.reload()?.await()
    }

    suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        auth.currentUser?.sendEmailVerification()?.await()
        Unit
    }

    // Direct password update on authenticated Firebase user.
    suspend fun changePassword(newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("User not authenticated")
        user.updatePassword(newPassword).await()
    }

    // Dispatches Firebase password reset link to user's registered inbox.
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    fun observeAuthState() = auth.currentUser
}

