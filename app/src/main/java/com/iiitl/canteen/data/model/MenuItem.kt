package com.iiitl.canteen.data.model

data class MenuItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val isAvailable: Boolean = true,
    val prepTimeMinutes: Int = 5
)
