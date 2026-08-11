package com.iiitl.canteen.data.repository

import com.iiitl.canteen.data.model.Order
import com.iiitl.canteen.data.model.OrderStatus
import com.iiitl.canteen.data.remote.CafeOrderDataSource
import kotlinx.coroutines.flow.Flow

class CafeOrderRepository(private val dataSource: CafeOrderDataSource) {

    fun observeActiveOrders(cafeteriaId: String): Flow<List<Order>> =
        dataSource.observeActiveOrders(cafeteriaId)

    suspend fun claimOrder(
        orderId: String,
        staffUid: String,
        staffName: String
    ): Result<Unit> = dataSource.claimOrder(orderId, staffUid, staffName)

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit> =
        dataSource.updateOrderStatus(orderId, newStatus)

    suspend fun markItemsUnavailable(
        orderId: String,
        unavailableItemIds: List<String>
    ): Result<Unit> = dataSource.markItemsUnavailable(orderId, unavailableItemIds)

    suspend fun getStaffName(staffUid: String): String =
        dataSource.getStaffName(staffUid)
}
