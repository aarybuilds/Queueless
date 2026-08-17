package com.iiitl.canteen.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderItem
import com.iiitl.canteen.data.model.OrderStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Only file with Firestore transaction writes for order claiming and
// status updates from the café staff perspective.
class CafeOrderDataSource(private val firestore: FirebaseFirestore) {

    private val ordersRef = firestore.collection("orders")

    fun observeActiveOrders(cafeteriaId: String): Flow<List<Order>> = callbackFlow {
        // Firestore cannot filter on a list membership with whereIn AND another
        // equality filter without a composite index. We filter cafeteriaId server-side
        // and the active-status filter server-side together — this requires a composite
        // index on (cafeteriaId ASC, status ASC, placedAt ASC).
        val activeStatuses = listOf(
            OrderStatus.PLACED.name,
            OrderStatus.AWAITING_CONFIRMATION.name,
            OrderStatus.ACCEPTED.name,
            OrderStatus.PREPARING.name,
            OrderStatus.READY.name
        )
        val registration = ordersRef
            .whereEqualTo("cafeteriaId", cafeteriaId)
            .whereIn("status", activeStatuses)
            .orderBy("placedAt", Query.Direction.ASCENDING)


            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val orders = snapshot.documents.mapNotNull { it.toObject(Order::class.java) }
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }

    // A transaction is required here because two staff members could read the same
    // PLACED order simultaneously, both see status=PLACED, and both write ACCEPTED —
    // resulting in two staff members working the same order with no way to detect
    // the conflict. The transaction makes the read-check-write atomic: Firestore
    // aborts the second transaction if the document changed since the first read it.
    suspend fun claimOrder(
        orderId: String,
        staffUid: String,
        staffName: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val docRef = ordersRef.document(orderId)
            val snapshot = transaction.get(docRef)
            val order = snapshot.toObject(Order::class.java)
                ?: throw Exception("Order not found.")

            if (order.status != OrderStatus.PLACED) {
                throw Exception("Order already claimed or no longer available.")
            }


            transaction.update(
                docRef,
                mapOf(
                    "status" to OrderStatus.ACCEPTED.name,
                    "claimedBy" to staffUid,
                    "claimedByName" to staffName,
                    "claimedAt" to System.currentTimeMillis()
                )
            )
        }.await()
    }

    // No transaction needed: once claimed, only the staff member who holds the order
    // drives it forward. No competing writer exists for PREPARING/READY/COLLECTED/EXPIRED.
    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit> =
        runCatching {
            ordersRef.document(orderId)
                .update("status", newStatus.name)
                .await()
        }

    // Transaction is used here to safely read, modify, and write the items array
    // atomically — avoids a lost-update if two writes hit the same document concurrently.
    suspend fun markItemsUnavailable(
        orderId: String,
        itemAvailabilities: Map<String, Int>
    ): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val docRef = ordersRef.document(orderId)
            val snapshot = transaction.get(docRef)
            val order = snapshot.toObject(Order::class.java)
                ?: throw Exception("Order not found.")

            val updatedItems: List<Map<String, Any?>> = order.items.map { item ->
                val availableQty = itemAvailabilities[item.itemId] ?: item.quantity
                val isAvailable = availableQty > 0
                mapOf(
                    "itemId" to item.itemId,
                    "name" to item.name,
                    "priceAtOrder" to item.priceAtOrder,
                    "quantity" to if (isAvailable) availableQty else item.quantity,
                    "isAvailable" to isAvailable
                )
            }

            transaction.update(
                docRef,
                mapOf(
                    "items" to updatedItems,
                    "status" to OrderStatus.AWAITING_CONFIRMATION.name
                )
            )
        }.await()
    }

    // Reads the staff member's display name from their user document so the
    // ViewModel never needs to handle Firestore directly.
    suspend fun getStaffName(staffUid: String): String =
        firestore.collection("users").document(staffUid)
            .get().await()
            .getString("name") ?: "Staff"
}
