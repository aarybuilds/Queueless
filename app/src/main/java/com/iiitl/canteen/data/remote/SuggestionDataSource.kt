package com.iiitl.canteen.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Isolated data source owning all direct Firestore writes to the "suggestions" collection.
class SuggestionDataSource(private val firestore: FirebaseFirestore) {

    suspend fun submitSuggestion(
        studentUid: String,
        studentName: String,
        message: String
    ): Result<Unit> = runCatching {
        val docRef = firestore.collection("suggestions").document()
        docRef.set(
            mapOf(
                "studentUid" to studentUid,
                "studentName" to studentName,
                "message" to message,
                "submittedAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
