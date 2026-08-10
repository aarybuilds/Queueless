package com.iiitl.canteen.data.model

data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val priceAtOrder: Double = 0.0,
    val quantity: Int = 1,
    val isAvailable: Boolean = true
)
