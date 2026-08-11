package com.iiitl.canteen.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.iiitl.canteen.data.model.MenuItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Only file allowed to read from the menuItems subcollection.
class MenuDataSource(private val firestore: FirebaseFirestore) {

    fun observeMenuItems(cafeteriaId: String): Flow<List<MenuItem>> = callbackFlow {
        val registration: ListenerRegistration = firestore
            .collection("cafeterias")
            .document(cafeteriaId)
            .collection("menuItems")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    // Don't close the flow — a transient error shouldn't kill the listener.
                    // The next successful snapshot will still arrive.
                    return@addSnapshotListener
                }

                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
                }
                trySend(items)
            }

        // Store the registration so we can detach the listener when the collector cancels.
        awaitClose { registration.remove() }
    }
}
