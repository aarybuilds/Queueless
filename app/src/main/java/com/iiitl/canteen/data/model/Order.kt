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
    /**
     * The sum of [OrderItem.priceAtOrder] * [OrderItem.quantity] for every item
     * where [OrderItem.isAvailable] is true.
     *
     * Declared as a `val` inside the class body (not a constructor parameter) so
     * Firestore ignores it during deserialization — computed properties are not
     * persisted. This is the idiomatic Kotlin alternative to a C++ member function
     * that computes a value on demand.
     *
     * `sumOf` is a stdlib extension function equivalent to
     * `std::accumulate` over a transformed range; it folds the collection into a
     * single Double using the lambda as the element-to-number mapping.
     */
    val availableTotal: Double
        get() = items
            .filter { it.isAvailable }
            .sumOf { it.priceAtOrder * it.quantity }
}
