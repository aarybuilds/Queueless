package com.iiitl.canteen.data.repository

import com.iiitl.canteen.data.model.MenuItem
import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderItem
import com.iiitl.canteen.data.model.OrderStatus
import com.iiitl.canteen.data.remote.OrderDataSource
import kotlinx.coroutines.flow.Flow

// CartItem is defined here rather than in the UI layer because the Repository
// is the consumer of cart data. The UI layer depends on this definition.
data class CartItem(val item: MenuItem, val quantity: Int)

class OrderRepository(private val orderDataSource: OrderDataSource) {

    suspend fun placeOrder(
        cafeteriaId: String,
        studentUid: String,
        studentName: String,
        studentRollNumber: String,
        cartItems: List<CartItem>
    ): Result<String> {
        val orderItems = cartItems.map { (item, qty) ->
            OrderItem(
                itemId = item.id,
                name = item.name,
                priceAtOrder = item.price,
                quantity = qty,
                isAvailable = item.isAvailable
            )
        }

        val totalAmount = cartItems.sumOf { (item, qty) -> item.price * qty }

        // 4-digit number gives staff a short, speakable reference without
        // exposing the full Firestore document ID at the counter.
        val orderNumber = (System.currentTimeMillis() % 9000 + 1000).toInt()

        val order = Order(
            cafeteriaId = cafeteriaId,
            studentUid = studentUid,
            studentName = studentName,
            studentRollNumber = studentRollNumber,
            items = orderItems,
            totalAmount = totalAmount,
            status = OrderStatus.PLACED,
            orderNumber = orderNumber,
            placedAt = System.currentTimeMillis()
        )

        return orderDataSource.placeOrder(order)
    }

    fun observeOrder(orderId: String): Flow<Order?> =
        orderDataSource.observeOrder(orderId)

    fun observeStudentOrders(studentUid: String): Flow<List<Order>> =
        orderDataSource.observeStudentOrders(studentUid)

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit> =
        orderDataSource.updateOrderStatus(orderId, newStatus)
}
