# Order Status Layer — Design & Reference
*Covers: `OrderStatusViewModel.kt`, `OrderStatusScreen.kt`, `OrderHistoryViewModel.kt`, `OrderHistoryScreen.kt`, and the `observeStudentOrders` additions to `OrderDataSource` and `OrderRepository`.*

---

## 1. Why `orderId` Is a Constructor Parameter of `OrderStatusViewModel`

The same reasoning applies here as for `cafeteriaId` in `MenuViewModel`: the ViewModel is the unit of lifecycle management, and it should be fully configured at construction time.

**The alternative** — passing `orderId` via a setter or a `MutableStateFlow<String?>` — creates a window between ViewModel creation and ID assignment where the ViewModel is in an undefined state. The `init` block would either have to do nothing until the ID arrives (requiring a `flatMapLatest` chain and extra state management) or would have to be called explicitly from the UI after construction. Either way, the ViewModel's contract becomes `create → configure → use` instead of `create → use`.

**With a constructor parameter**, the contract is simply `create(orderId) → use`. `init` fires immediately with a valid `orderId`, the Firestore listener is attached in the first frame, and the first snapshot arrives before the screen finishes its initial composition in most network conditions.

**Practical implication**: When navigation provides the `orderId` as a route argument (e.g., `NavBackStackEntry.arguments?.getString("orderId")`), the factory reads it and passes it to the ViewModel constructor. The ViewModel is bound to exactly one order for its entire lifetime. If the student navigates to a different order, a new ViewModel instance (with a different `orderId`) is created.

---

## 2. How `AWAITING_CONFIRMATION` Works End to End

`AWAITING_CONFIRMATION` is the status the café assigns when they accept an order but some items are out of stock. It is a dialogue state — the café has offered a reduced order, and the student must decide.

**Full flow**:

1. **Student places order** → status is `PLACED`, stored in Firestore.

2. **Café staff's app** shows the incoming order. Staff notices "Veg Sandwich" is out of stock. They tap a button that:
   - Sets `status = AWAITING_CONFIRMATION`.
   - Sets `isAvailable = false` on the `OrderItem` for "Veg Sandwich" in the `items` array.
   - Writes the update to Firestore.

3. **Student's `OrderStatusViewModel`** is collecting `observeOrder(orderId)`. The Firestore snapshot listener fires with the updated document. The new `Order` object reaches the `collect` block. `_uiState.update { it.copy(order = order) }`.

4. **`OrderStatusScreen`** recomposes. It detects `order.status == AWAITING_CONFIRMATION` and renders:
   - A list of items where `isAvailable == false` (those the café can't fulfil).
   - A "Confirm reduced order" button and a "Cancel order" button.

5. **Student taps "Confirm reduced order"** → the callback fires → the parent composable (or navigation host) calls the staff-facing `canTransition(AWAITING_CONFIRMATION, ACCEPTED, STUDENT)` which returns `true`, and writes a status update to Firestore.

6. **Student taps "Cancel order"** → `canTransition(AWAITING_CONFIRMATION, CANCELLED, STUDENT)` → true → Firestore updated to `CANCELLED`.

7. **In both cases**, the `OrderStatusViewModel`'s listener fires again with the new status, and the screen recomposes to show the updated state.

The key design property: **the student never sees a stale state**. Because the screen is driven by a real-time Flow, the status displayed is always the live Firestore value.

---

## 3. Why `observeStudentOrders` Needs a Composite Index

Firestore can execute a `whereEqualTo` filter on its own and an `orderBy` clause on its own — but it **cannot combine a field filter with a sort on a different field** without a composite index. The query:

```
collection("orders")
    .whereEqualTo("studentUid", uid)
    .orderBy("placedAt", DESCENDING)
```

filters on `studentUid` and sorts on `placedAt`. Firestore requires a composite index that covers both fields.

**Without the index**, Firestore returns an error at runtime:
```
com.google.firebase.firestore.FirebaseFirestoreException: 
FAILED_PRECONDITION: The query requires an index. You can create it here: https://console.firebase.google.com/...
```

The error message includes a direct link to create the index automatically. Clicking it opens the Firebase Console with the index pre-filled.

**To create it manually**:
1. Firebase Console → Firestore Database → Indexes → Composite → Create Index.
2. Collection: `orders`
3. Fields:
   - `studentUid` — Ascending
   - `placedAt` — Descending
4. Query scope: Collection
5. Click Create.

Index creation takes 1–5 minutes. Until it is ready, queries return the error above.

**Alternatively**, create `firestore.indexes.json` in the project and deploy via Firebase CLI:
```json
{
  "indexes": [
    {
      "collectionGroup": "orders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "studentUid", "order": "ASCENDING" },
        { "fieldPath": "placedAt",   "order": "DESCENDING" }
      ]
    }
  ]
}
```
```bash
firebase deploy --only firestore:indexes
```

This commits the index definition to version control, which is the recommended practice for production projects.

---

## Interview Questions

**Q1. `OrderStatusScreen` receives `onConfirmReducedOrder` and `onCancelOrder` as callbacks rather than calling a ViewModel function directly. Why?**

`OrderStatusScreen` is a stateless composable — it renders state and reports events, it never owns logic. The decision of *what to do* when the student confirms or cancels belongs to the layer above: either the ViewModel (which would call `OrderRepository.updateOrderStatus`) or the navigation host (which might navigate first, then update). If the screen called a ViewModel directly, it would be impossible to preview with a static `OrderStatusUiState`, and any change to the confirmation logic would require modifying the screen. Callback parameters keep the screen a pure rendering function and leave the wiring to the call site.

**Q2. `OrderStatusViewModel` checks `!_uiState.value.isLoading` before emitting "Order not found" for a null document. Why?**

The first emission from `observeOrder` may be `null` if the Firestore SDK serves from cache and the document is not cached yet. At that point, `isLoading` is still `true`. Emitting "Order not found" in that situation would show an error that disappears a fraction of a second later when the real document arrives. The guard ensures "Order not found" is only shown after at least one valid (non-null) document has been received and then subsequently a null arrives — meaning the document was deleted after we started observing it.

**Q3. The `progressSteps` list in `OrderStatusScreen` only includes `PLACED`, `ACCEPTED`, `PREPARING`, `READY`. What happens visually for `CANCELLED`, `REJECTED`, or `EXPIRED`?**

`progressSteps.indexOf(currentStatus)` returns `-1` for statuses not in the list. The condition `it >= 0 && index <= it` is false for all steps, so all dots and connectors render in `outlineVariant` (unfilled). The progress row shows as entirely grey/unfilled. The `StatusBanner` composable then displays the appropriate red explanatory text below the progress row, making it clear to the student that the order did not complete successfully. Terminal states are not forced into the linear progress metaphor because they represent a branch, not a step.

**Q4. Why is `dateFormatter` a file-level `val` in `OrderHistoryScreen.kt` rather than created inside the `OrderHistoryRow` composable?**

`SimpleDateFormat` construction is not trivial — it parses the format string and initialises locale data. If it were created inside `OrderHistoryRow`, it would be recreated on every recomposition of every row. At 20 orders in the history list, that is 20 `SimpleDateFormat` instantiations per recomposition. Moving it to a file-level `val` creates it exactly once when the class is loaded. This is the Compose equivalent of hoisting expensive state out of the render path. (Note: `SimpleDateFormat` is not thread-safe; the file-level `val` is safe here because Compose recomposition always happens on the main thread.)

**Q5. The order history query uses `orderBy("placedAt", DESCENDING)`. What happens to historical orders that were created before the `placedAt` field existed in the schema?**

Firestore excludes documents from query results if the queried field is missing or null. Old orders without a `placedAt` field would simply not appear in the history list. This is the correct behaviour — an order without a `placedAt` is malformed data that cannot be meaningfully sorted. The current `Order` data class defaults `placedAt` to `0L`, so any properly created order has this field. Documents created manually in the Console or by an older app version without this field would be silently excluded.

**Q6. `OrderHistoryViewModel` and `OrderStatusViewModel` both have an `init { viewModelScope.launch { ... .collect { } } }` pattern. If the student opens Order History, then opens Order Status, then navigates back — are both listeners still alive?**

Only the listener that belongs to the currently active ViewModel. When the student navigates back from Order Status to Order History, the Order Status screen is removed from the back stack (assuming it is not retained). This triggers `ViewModel.onCleared()` on `OrderStatusViewModel`, which cancels `viewModelScope`, which cancels the `collect` coroutine. `callbackFlow`'s `awaitClose` block then runs, calling `registration.remove()` to detach the Firestore listener. Meanwhile, `OrderHistoryViewModel` was never cleared (it is still on the back stack), so its listener remains alive and the history updates in real time. Each ViewModel's listener lifetime is exactly bounded by its ViewModel's lifetime.
