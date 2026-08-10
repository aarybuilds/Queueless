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

/**
 * Returns true if a transition from [from] to [to] is permitted for the given [role].
 *
 * The allowed transition table encodes business rules as a Set<Triple> so that
 * each lookup is O(1) and new rules can be added in one place without touching
 * any branching logic elsewhere.
 *
 * `Triple` has no direct C++ equivalent; it is a stdlib data class that groups
 * three values into a single object and provides structural equality — meaning
 * two Triples are equal when all three components are equal, making it safe to
 * use as a Set element or Map key.
 */
fun canTransition(from: OrderStatus, to: OrderStatus, role: UserRole): Boolean {
    val allowedTransitions: Set<Triple<OrderStatus, OrderStatus, UserRole>> = setOf(
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
    return Triple(from, to, role) in allowedTransitions
}

/**
 * Returns every [OrderStatus] that [current] can legally transition to for the given [role].
 *
 * `entries` is the Kotlin 1.9+ replacement for `values()` on enums; it returns a
 * read-only `List<OrderStatus>` instead of a freshly allocated array, which is
 * more idiomatic and avoids unnecessary allocations.
 *
 * `filter` is a higher-order function (analogous to `std::copy_if` in C++) that
 * returns a new list containing only the elements for which the predicate holds.
 */
fun allowedNextStates(current: OrderStatus, role: UserRole): List<OrderStatus> =
    OrderStatus.entries.filter { next -> canTransition(current, next, role) }
