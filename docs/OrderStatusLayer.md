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
