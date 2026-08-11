package com.iiitl.canteen.data.model

data class Order(
    val id: String = "",
    val cafeteriaId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val studentRollNumber: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: OrderStatus = OrderStatus.PLACED,
    val claimedBy: String? = null,
    val claimedByName: String? = null,
    val orderNumber: Int = 0,
    val placedAt: Long = 0L,
    val claimedAt: Long? = null,
    val readyAt: Long? = null,
    val collectedAt: Long? = null
) {
    // Not a constructor parameter, so Firestore never persists it.
    // Recomputed on access so it always reflects the current items list.
    val availableTotal: Double
        get() = items
            .filter { it.isAvailable }
            .sumOf { it.priceAtOrder * it.quantity }
}
