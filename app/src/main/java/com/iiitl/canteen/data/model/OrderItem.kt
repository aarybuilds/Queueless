package com.iiitl.canteen.data.model

// priceAtOrder snapshots the price at purchase time so order history stays
// accurate even if the live menu price changes later.
data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val priceAtOrder: Double = 0.0,
    val quantity: Int = 1,
    val isAvailable: Boolean = true
)
