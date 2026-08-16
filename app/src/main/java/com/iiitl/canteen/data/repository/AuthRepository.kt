package com.iiitl.canteen.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.iiitl.canteen.data.model.User
import com.iiitl.canteen.data.model.UserRole
import com.iiitl.canteen.data.remote.AuthDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val authDataSource: AuthDataSource,
    private val firestore: FirebaseFirestore
) {

    // Domain validation runs here, not in the ViewModel, because the rule
    // "only @iiitl.ac.in addresses may register" is a business invariant that
    // belongs to the data layer. The ViewModel only knows about UI state.
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        rollNumber: String
    ): Result<Unit> {
        if (!email.endsWith("@iiitl.ac.in")) {
            return Result.failure(IllegalArgumentException("Only @iiitl.ac.in email addresses can register."))
        }

        val signUpResult = authDataSource.signUp(email, password)
        if (signUpResult.isFailure) {
            return Result.failure(signUpResult.exceptionOrNull()!!)
        }

        val uid = signUpResult.getOrThrow()
        val user = User(
            uid = uid,
            email = email,
            name = name,
            rollNumber = rollNumber,
            role = UserRole.STUDENT,
            noShowCount = 0
        )

        return runCatching {
            firestore.collection("users").document(uid).set(user).await()
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        authDataSource.signIn(email, password).map { }

    fun signOut() = authDataSource.signOut()

    fun getCurrentUserId(): String? = authDataSource.getCurrentUserId()

    fun getCurrentUserEmail(): String? = authDataSource.getCurrentUserEmail()

    // Reloads user record from server to get fresh verification flag instead of cached state.
    suspend fun isEmailVerified(): Boolean = runCatching {
        authDataSource.reloadUser()
        authDataSource.isEmailVerified()
    }.getOrDefault(false)

    suspend fun sendEmailVerification(): Result<Unit> =
        authDataSource.sendEmailVerification()

    suspend fun getUserRole(uid: String): UserRole = runCatching {
        val snapshot = firestore.collection("users").document(uid).get().await()
        snapshot.toObject(User::class.java)?.role ?: UserRole.STUDENT
    }.getOrDefault(UserRole.STUDENT)

    suspend fun getUserProfile(uid: String): User? = runCatching {
        firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }.getOrNull()

    // Reads assigned cafeteria ID for staff accounts to restrict queue access.
    suspend fun getAssignedCafeteriaId(uid: String): String? = runCatching {
        val snapshot = firestore.collection("users").document(uid).get().await()
        snapshot.getString("assignedCafeteriaId")?.ifEmpty { null }
    }.getOrNull()

    // Updates authenticated user's password directly.
    suspend fun changePassword(newPassword: String): Result<Unit> =
        authDataSource.changePassword(newPassword)

    // Sends reset email to explicit address (unauthenticated login flow).
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        authDataSource.sendPasswordResetEmail(email)


    // Sends reset email to current authenticated user.
    suspend fun sendPasswordResetEmail(): Result<Unit> {
        val email = authDataSource.getCurrentUserEmail()
            ?: return Result.failure(IllegalStateException("User is not authenticated."))
        return authDataSource.sendPasswordResetEmail(email)
    }

    // callbackFlow bridges Firebase's listener-based auth state callbacks into

    // a Kotlin Flow. awaitClose ensures the listener is removed when the Flow
    // collector is cancelled, preventing a memory leak.
    fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(listener)
        awaitClose {
            com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }
}
