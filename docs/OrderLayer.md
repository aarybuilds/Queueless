# Order Layer — Design & Reference
*Covers: `CartViewModel.kt`, `CartScreen.kt`, `OrderDataSource.kt`, `OrderRepository.kt`, `PlaceOrderViewModel.kt`*

---

## 1. Why Cart State Lives in a ViewModel and Not Passed Between Screens

The naive alternative is to collect cart data in a `remember { mutableStateOf(...) }` inside the menu composable and pass it as a parameter to `CartScreen`. This breaks as soon as any of the following is true:

**Screen rotation**: Compose state created with `remember` is discarded when the Activity is recreated. A student building a cart, then rotating their phone, loses everything. ViewModel state survives rotation because the ViewModel lives in the `ViewModelStore`, which the framework preserves across configuration changes.

**Navigation back and forth**: If the student goes to `CartScreen`, decides to go back to `MenuScreen` to add something, and then goes to `CartScreen` again — the cart must be the same. With `remember`-based state, navigating back destroys the composable and its remembered state. A ViewModel shared between the two screens (same ViewModel instance, e.g., scoped to the same navigation back-stack entry) retains the cart regardless of navigation.

**Multiple UI entry points**: If a future screen (e.g., a "suggested items" screen) also allows adding to the cart, it can reach the same `CartViewModel` without requiring the cart state to be threaded through every composable parameter.

**Testability**: `CartViewModel` can be unit-tested in isolation — pass an item, assert `uiState.items`. Testing state stored in `remember` requires a full Compose test setup.

---

## 2. Full Place-Order Flow End to End

**Starting point**: Student has tapped "Nescafe", sees the menu, added 2 items to the cart, and taps "Place Order".

1. **`CartScreen`** calls the `onPlaceOrder` callback, passing the current `CartUiState` (which contains `Map<MenuItem, Int>`) and `StudentInfo` (name, rollNumber from the authenticated user profile).

2. **`PlaceOrderViewModel.placeOrder()`** is called with `cafeteriaId`, a `List<CartItem>` (converted from the map), `studentName`, and `studentRollNumber`.
   - It immediately reads `authRepository.getCurrentUserId()`. If null, it sets `errorMessage` and returns — no network call wasted.
   - Sets `isLoading = true`.
   - Launches a coroutine on `viewModelScope`.

3. **`OrderRepository.placeOrder()`** receives the parameters and:
   - Maps `List<CartItem>` to `List<OrderItem>` — snapshotting `priceAtOrder` from the live `MenuItem.price`.
   - Sums `totalAmount`.
   - Generates a random 4-digit `orderNumber` in `1000..9999`.
   - Sets `placedAt = System.currentTimeMillis()`.
   - Constructs a fully formed `Order` object with `status = PLACED`.
   - Calls `orderDataSource.placeOrder(order)`.

4. **`OrderDataSource.placeOrder()`**:
   - Calls `firestore.collection("orders").document()` — generates a Firestore document reference with a client-side unique ID. No network call yet.
   - Copies that ID into the order: `order.copy(id = docRef.id)`.
   - Calls `docRef.set(orderWithId).await()` — this is the single network call that writes the document.
   - Returns `Result.success(docRef.id)`.

5. **`OrderRepository`** returns `Result<String>` (the order ID) to `PlaceOrderViewModel`.

6. **`PlaceOrderViewModel`** sets `placedOrderId = orderId`, `isLoading = false`.

7. **The parent composable** (or `MainActivity`) is observing `uiState.placedOrderId`. When it goes non-null, it navigates to the order tracking screen and passes the `orderId`.

---

## 3. Why `orderNumber` Is a Random 4-Digit Int and Not the Document ID

Firestore auto-generated document IDs look like: `3qhTDiKSe7oZk9F2mRwL`. A staff member at a canteen counter cannot use this to identify a customer's order verbally. It cannot be announced over a speaker or written on a physical ticket.

A 4-digit number (1000–9999) can be:
- Announced: "Order 3417, your food is ready."
- Written on a physical token.
- Remembered by the student long enough to walk to the counter.

The document ID still exists and is used for all programmatic Firestore lookups. The `orderNumber` is a **human-facing alias** that lives alongside the technical ID, not a replacement for it.

**Why random and not sequential?** Sequential order numbers (1, 2, 3, …) require either a Firestore transaction to atomically increment a counter (expensive, requires a dedicated counter document) or a Cloud Function. A random number in a 4-digit range is collision-improbable within a single cafeteria's busy period (a canteen serving 200 orders/day has less than a 2% chance of a collision per day in the 9000-value range). Collisions are benign — the student can quote their full name to disambiguate.

---

## 4. Race Condition: Two Students Order the Last Item Simultaneously

**Scenario**: Only one sandwich is left in stock. Two students open the app at the same time. Both see `isAvailable: true`. Both tap "Place Order" within milliseconds of each other.

**What happens in the current implementation**:

Both orders are written to Firestore successfully. The `orderNumber` is random and the Firestore write is not conditioned on stock level. Firestore accepts both writes. The cafeteria now has two orders for a sandwich with zero sandwiches.

The staff member sees both orders arrive in their queue. They must manually mark one order as `REJECTED` (or `AWAITING_CONFIRMATION` to negotiate a substitution). The student whose order is rejected receives the status update via the real-time `observeOrder` Flow.

**This is the current design's known limitation.** It trades correctness for simplicity — Queueless does not currently enforce stock counts.

**How to fix it properly**:

The correct solution is a **Firestore transaction** that reads the current stock count and writes the order atomically, failing if stock is 0:

```kotlin
firestore.runTransaction { transaction ->
    val itemRef = firestore.collection("cafeterias")
        .document(cafeteriaId).collection("menuItems").document(itemId)
    val snapshot = transaction.get(itemRef)
    val stock = snapshot.getLong("stockCount") ?: 0
    if (stock <= 0) throw Exception("Item out of stock")
    transaction.update(itemRef, "stockCount", stock - 1)
    transaction.set(orderRef, order)
}.await()
```

This requires:
1. A `stockCount` field on `MenuItem` documents.
2. All writes to go through a transaction rather than a bare `.set()`.
3. The UI to display `stockCount` and disable adding items when it reaches 0.

Alternatively, a **Cloud Function** triggered on order creation can validate and reject orders where the item is no longer available, pushing a `REJECTED` status update without any client-side changes.

---

## Interview Questions

**Q1. Why is `CartUiState.items` a `Map<MenuItem, Int>` and not a `List<CartItem>`?**

A `Map<MenuItem, Int>` gives O(1) lookup for "how many of this item are in the cart?" and makes `addItem`/`removeItem` trivially correct — you increment or decrement the map value for the key. With `List<CartItem>`, `addItem` would need to `find { it.item == menuItem }` (O(n)), then replace the element or append a new one. The map structure also guarantees that each distinct `MenuItem` appears exactly once, enforcing the uniqueness invariant structurally rather than through a runtime check.

`MenuItem` works as a map key because it is a `data class`, which auto-generates `equals()` and `hashCode()` based on all its properties. Two `MenuItem` objects with the same `id` and `name` are equal as map keys.

**Q2. How does `CartViewModel.recompute()` work, and why is it a private extension function?**

```kotlin
private fun CartUiState.recompute(): CartUiState = copy(
    totalAmount = items.entries.sumOf { (item, qty) -> item.price * qty },
    itemCount = items.values.sum()
)
```

`recompute()` is an extension function on `CartUiState` because it produces a new `CartUiState` with derived fields filled in. Being `private` to the `CartViewModel` file means only `CartViewModel` can call it — the `CartUiState` data class itself stays pure (no computed logic embedded in a `get()`). The pattern avoids copy-pasting the `sumOf` and `sum()` calls in both `addItem` and `removeItem`.

**Q3. Why does `OrderDataSource.placeOrder` call `document()` with no argument rather than `document(someId)`?**

`firestore.collection("orders").document()` generates a unique document ID **client-side**, using a UUID-like algorithm. It does not make a network call. This has two advantages: (1) the ID is available synchronously, so we can embed it in the `order.id` field before writing; (2) the `.set()` call is then a simple write rather than an upsert that overwrites an existing document. If we passed an explicit ID that already existed, `.set()` would silently overwrite the existing order.

**Q4. Why does `OrderRepository.placeOrder` convert `CartItem` to `OrderItem` rather than storing `MenuItem` directly?**

`MenuItem` is a live menu entry — it changes when the café edits it. `OrderItem` is a historical record. Snapshotting `priceAtOrder`, `name`, and `isAvailable` at order creation time ensures the order receipt is immutable and accurate regardless of future menu changes. An `OrderItem` is the "what you agreed to buy and at what price" record; a `MenuItem` is "what the café sells right now."

**Q5. `PlaceOrderViewModel` reads `authRepository.getCurrentUserId()` synchronously at the start of `placeOrder()`. What are the failure scenarios?**

`getCurrentUserId()` calls `FirebaseAuth.getInstance().currentUser?.uid`. It can return `null` in two cases:
1. The user was never signed in (shouldn't happen in normal app flow, but possible if `MainActivity` was started without going through `LoginScreen`).
2. The user's session was revoked server-side or the token expired while the app was in the background. Firebase Auth typically handles token refresh silently, but a revoked account would return `currentUser == null` on the next check.

In both cases, `PlaceOrderViewModel` sets `errorMessage = "You must be signed in to place an order."` and returns without making a network call. The UI should then navigate back to `LoginScreen`.

**Q6. Why is `CartItem` defined in `OrderRepository.kt` and not in the UI layer?**

`CartItem` is the DTO (Data Transfer Object) that crosses the boundary from the UI layer into the Repository layer. Its definition belongs at the boundary — the Repository file — because that is the consumer that unpacks it into an `Order`. If it were defined in the UI package (e.g., inside `CartScreen.kt`), the Repository would import a UI-layer type, inverting the dependency direction. Clean Architecture requires that the inner layers (Repository, DataSource) never depend on the outer layers (UI).

**Q7. Why does `PlaceOrderUiState.placedOrderId` going non-null trigger navigation rather than a separate one-shot event?**

One-shot events (e.g., a `SharedFlow<NavigationEvent>`) are the common alternative. They have a subtle problem: if the UI is not collecting the flow at the exact moment the event fires (e.g., during a recomposition or a brief lifecycle pause), the event is missed. `StateFlow` with `placedOrderId: String?` is a **state**, not an event. The UI collects it continuously and navigates whenever it sees a non-null value, regardless of when it started collecting. After navigation, the receiving screen can query the order ID from the navigation argument, and `PlaceOrderViewModel` can be cleared or its `placedOrderId` reset.

**Q8. What is the purpose of `viewModelScope.launch` in `PlaceOrderViewModel.placeOrder()`, and what happens to the coroutine if the user closes the screen mid-request?**

`viewModelScope` is a `CoroutineScope` tied to the ViewModel's lifecycle. It is cancelled when `ViewModel.onCleared()` is called, which happens when the screen is permanently removed from the back stack (not on rotation). So:

- **User rotates the screen**: the Activity is recreated, but the ViewModel (and `viewModelScope`) survives. The coroutine keeps running. The new composition collects `uiState` and sees the result when it arrives.
- **User presses Back before the order completes**: the ViewModel is cleared, `viewModelScope` is cancelled, and the `placeOrder` coroutine is cancelled at the next suspension point (`.await()`). The Firestore write may or may not have completed by then — if it did, the order exists in Firestore; if it didn't, it doesn't. This is an edge case that would require a Cloud Function or a retry mechanism to handle robustly.

---

## 5. Max 3 Open Orders Enforcement

To prevent queue flooding, Queueless enforces that a student may have at most 3 active (non-terminal) orders across all cafeterias simultaneously. Active statuses are defined as `PLACED`, `AWAITING_CONFIRMATION`, `ACCEPTED`, and `PREPARING`.

### Why the Check Lives in `OrderRepository` and Not `PlaceOrderViewModel`

1. **Centralized Domain Rule**: `OrderRepository` is the sole entry point for placing orders across the entire application domain. Enforcing business invariants in the Repository ensures that the rule executes regardless of which ViewModel or UI flow initiated order creation (e.g. `PlaceOrderViewModel`, a re-order button, or a batch order API).
2. **ViewModel Separation**: ViewModels own UI state and user event translation. If the validation were placed inside `PlaceOrderViewModel`, any other screen or ViewModel attempting to place an order would need to duplicate the query and validation logic, violating DRY principles and risking inconsistent enforcement.

### Race Condition Risk

The active order count check is performed as a read-then-write sequence in Kotlin:

```kotlin
val activeOrderCount = orderDataSource.checkActiveOrderCount(studentUid)
if (activeOrderCount >= 3) {
    return Result.failure(Exception("You already have 3 active orders..."))
}
return orderDataSource.placeOrder(order)
```

**Known Limitation**: If a student triggers two `placeOrder()` calls almost simultaneously (for example, by rapidly double-tapping a button or triggering parallel coroutines), both invocations may call `checkActiveOrderCount(studentUid)` concurrently. Both reads observe `count = 2`, both pass the `>= 3` check, and both proceed to write a new order document to Firestore. As a result, the student temporarily accumulates 4 active orders.

**Eliminating the Race Condition**: To guarantee strict atomic enforcement at the database level, the check and document creation would need to be executed inside a **Firestore Transaction** or evaluated server-side using **Firestore Security Rules** / a **Cloud Function**.

