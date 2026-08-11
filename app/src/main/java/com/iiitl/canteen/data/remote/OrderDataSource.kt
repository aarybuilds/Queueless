package com.iiitl.canteen.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.iiitl.canteen.data.model.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Only file with direct Firestore writes to the orders collection.
class OrderDataSource(private val firestore: FirebaseFirestore) {

    suspend fun placeOrder(order: Order): Result<String> = runCatching {
        // document() with no argument generates a unique ID without a network round-trip.
        val docRef = firestore.collection("orders").document()
        val orderWithId = order.copy(id = docRef.id)
        docRef.set(orderWithId).await()
        docRef.id
    }

    fun observeOrder(orderId: String): Flow<Order?> = callbackFlow {
        val registration = firestore.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                // toObject returns null if the document does not exist.
                trySend(snapshot.toObject(Order::class.java))
            }
        awaitClose { registration.remove() }
    }

    // Requires a composite index on (studentUid ASC, placedAt DESC).
    // Firestore cannot serve this query without it; see docs/OrderStatusLayer.md.
    fun observeStudentOrders(studentUid: String): Flow<List<Order>> = callbackFlow {
        val registration = firestore.collection("orders")
            .whereEqualTo("studentUid", studentUid)
            .orderBy("placedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val orders = snapshot.documents.mapNotNull { it.toObject(Order::class.java) }
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }
}
