package com.iiitl.canteen.data.model

enum class OrderStatus {
    PLACED,
    AWAITING_CONFIRMATION,
    ACCEPTED,
    PREPARING,
    READY,
    COLLECTED,
    CANCELLED,
    REJECTED,
    EXPIRED
}

// Every valid (from, to, role) combination lives in one place.
// Adding or revoking a rule is a single-line change with no risk of
// breaking unrelated when-branches. Set membership is O(1).
private val allowedTransitions: Set<Triple<OrderStatus, OrderStatus, UserRole>> = setOf(
    Triple(OrderStatus.PLACED, OrderStatus.ACCEPTED,               UserRole.CAFE_STAFF),
    Triple(OrderStatus.PLACED, OrderStatus.AWAITING_CONFIRMATION,  UserRole.CAFE_STAFF),
    Triple(OrderStatus.PLACED, OrderStatus.REJECTED,               UserRole.CAFE_STAFF),
    Triple(OrderStatus.PLACED, OrderStatus.CANCELLED,              UserRole.STUDENT),
    Triple(OrderStatus.AWAITING_CONFIRMATION, OrderStatus.ACCEPTED,  UserRole.STUDENT),
    Triple(OrderStatus.AWAITING_CONFIRMATION, OrderStatus.CANCELLED, UserRole.STUDENT),
    Triple(OrderStatus.ACCEPTED,  OrderStatus.PREPARING,  UserRole.CAFE_STAFF),
    Triple(OrderStatus.PREPARING, OrderStatus.READY,      UserRole.CAFE_STAFF),
    Triple(OrderStatus.READY,     OrderStatus.COLLECTED,  UserRole.CAFE_STAFF),
    Triple(OrderStatus.READY,     OrderStatus.EXPIRED,    UserRole.CAFE_STAFF)
)

fun canTransition(from: OrderStatus, to: OrderStatus, role: UserRole): Boolean =
    Triple(from, to, role) in allowedTransitions

fun allowedNextStates(current: OrderStatus, role: UserRole): List<OrderStatus> =
    OrderStatus.entries.filter { next -> canTransition(current, next, role) }
