# Café Staff Layer — Design & Reference
*Covers: `CafeOrderDataSource.kt`, `CafeOrderRepository.kt`, `CafeOrderQueueViewModel.kt`, `CafeOrderQueueScreen.kt`*

---

## 1. Why `claimOrder` Uses a Transaction and `updateOrderStatus` Does Not

### `claimOrder` — transaction required

When an order arrives in `PLACED` status, **any staff member at the counter can see it**. Two staff members at different screens will both see the same `PLACED` order in their queue. If either of them can tap "Accept" and write `status = ACCEPTED` directly, both writes succeed — Firestore is a document store with no built-in optimistic locking. The result: two staff members are now "working" the same order, both preparing it, both expecting the student to collect from them.

A Firestore transaction solves this by making the **read-check-write sequence atomic**:

```kotlin
firestore.runTransaction { transaction ->
    val snapshot = transaction.get(docRef)          // 1. Read
    if (snapshot.getString("status") != "PLACED")   // 2. Check
        throw Exception("Already claimed")
    transaction.update(docRef, ...)                 // 3. Write
}
```

Firestore tracks all documents read within a transaction. Before committing, it verifies that none of those documents were modified by a concurrent write since the read. If they were, it aborts and retries the transaction. The second staff member's transaction will read the already-`ACCEPTED` document, fail the check, and throw the exception — which `runCatching` converts to `Result.failure`. The UI then surfaces this as a `claimError` snackbar.

### `updateOrderStatus` — no transaction needed

Once an order is `ACCEPTED`, exactly one staff member holds it: the one whose uid is stored in `claimedBy`. That staff member's device is the only one showing the "Start preparing" → "Mark Ready" → "Collected" button sequence. No other device will attempt a competing write on that order's status at the same moment. A plain `document.update(...)` is safe because **exclusivity is already established** by the claim step.

Using a transaction for `updateOrderStatus` would add 30–100ms of network latency for no benefit. It would also consume more Firestore read quota unnecessarily.

---

## 2. The Exact Race Condition `claimOrder` Prevents

**Setup**: Two staff members — Alice and Bob — both have the Nescafe queue open. An order (#4231) from Priya arrives in `PLACED` status.

### Without a transaction

| Time | Alice's device | Bob's device |
|------|---------------|--------------|
| T=0ms | Reads order #4231: `status = PLACED` | Reads order #4231: `status = PLACED` |
| T=50ms | Taps "Accept" | Taps "Accept" |
| T=80ms | Writes `status = ACCEPTED, claimedBy = alice` | — |
| T=90ms | — | Writes `status = ACCEPTED, claimedBy = bob` |
| T=91ms | **Bob's write overwrites Alice's.** `claimedBy = bob` | |

Outcome: The Firestore document says Bob claimed the order, but Alice is already preparing it. Both staff members receive the real-time update showing `status = ACCEPTED`. There is no indication that a conflict occurred. Both prepare the food. The student gets one order; the other is wasted. The queue system has lost integrity.

### With a transaction

| Time | Alice's device | Bob's device |
|------|---------------|--------------|
| T=0ms | Transaction starts: reads `status = PLACED` | Transaction starts: reads `status = PLACED` |
| T=50ms | Alice taps Accept | Bob taps Accept |
| T=80ms | Transaction commits: `status = PLACED` ✓ → writes `ACCEPTED, claimedBy = alice` | — |
| T=82ms | — | Firestore detects document changed since read → **aborts Bob's transaction** |
| T=85ms | — | `runCatching` catches the abort → `Result.failure("Order already claimed")` |
| T=86ms | — | `claimError` state updated → snackbar shows "Order already claimed" |

Outcome: Alice prepares one order. Bob sees the snackbar and moves to the next order. No waste, no confusion.

---

## 3. Why READY Orders Are Visually Separated from Active Orders

An active order (PLACED → ACCEPTED → PREPARING) requires **staff attention and physical work**. A READY order requires **one interaction** — confirm collection or mark expired. These are fundamentally different workflows.

If READY orders were mixed into the same list, a busy counter staff member would have to scan the entire queue to find which orders a student is asking about. In peak hours with 10+ simultaneous orders, this friction is unacceptable.

Grouping READY orders at the bottom with a distinct background achieves two things:

1. **Scan target**: Staff can instantly look at the "Ready for Collection" section when a student arrives. No scrolling, no status labels to read.
2. **Action clarity**: The buttons available in the READY group ("Collected" / "Not collected") are different from those in the active group. Separating them reduces the risk of accidentally tapping the wrong action on the wrong order.

The light green background (`Color(0xFFE8F5E9)`) is a deliberate visual affordance — the green communicates "complete / positive" without requiring the staff member to read the status label.

---

## Interview Questions

**Q1. Why does `CafeOrderQueueViewModel` fetch `staffName` in `init` rather than at the point of claiming an order?**

`getStaffName` makes a Firestore network call (`users/{uid}.get()`). If it were called inside `claimOrder`, every claim attempt would add a sequential network round-trip before the transaction even starts — increasing latency and Firestore read quota usage by one read per claim. Since the staff member's name does not change during a session, fetching it once in `init` is correct. The name is then available synchronously for every subsequent claim call, making each claim a single Firestore transaction instead of two sequential operations.

**Q2. `observeActiveOrders` uses `whereIn("status", activeStatuses)`. What Firestore index does this require?**

Combining `whereEqualTo("cafeteriaId", ...)` with `whereIn("status", [...])` and `orderBy("placedAt", ASCENDING)` requires a composite index on three fields:
- `cafeteriaId` (Ascending)
- `status` (Ascending)
- `placedAt` (Ascending)

Without it, Firestore returns a `FAILED_PRECONDITION` error with a link to create the index in the console. The `whereIn` operator is treated as an equality filter on an array of values, and the multi-field combination triggers the composite index requirement. The index should be created in `firestore.indexes.json` and deployed via `firebase deploy --only firestore:indexes`.

**Q3. `markItemsUnavailable` also uses a transaction. Why, given that `updateOrderStatus` doesn't?**

`markItemsUnavailable` modifies the `items` array — a nested list inside the document. Firestore does not have a native "update one element of an array field by index" operation. We must: (1) read the full items array, (2) map over it with the updated `isAvailable` flags, (3) write the entire array back. If two concurrent writes performed this read-modify-write without a transaction, the second write would overwrite the first with a stale version of the array. The transaction ensures the array cannot be modified between the read and write by any other concurrent operation.

**Q4. The `showUnavailableChooser` and `unavailableSelections` states are local to `OrderCard` composable, not in the ViewModel. Why?**

These are pure UI transience — they exist only while the staff member is deciding which items to mark unavailable. They have no business meaning until the staff member taps "Notify student". If the screen rotates or the composable leaves composition while the chooser is open, losing this state is acceptable (the staff member simply taps "Can't prepare" again). Moving this state to the ViewModel would add complexity (clearing it after use, preventing it from leaking across different orders) with no benefit. The rule: if losing the state on recomposition is acceptable, use `remember`; if it must survive rotation, use the ViewModel.

**Q5. `CafeQueueUiState` has two separate error fields: `errorMessage` and `claimError`. What would break if there were only one?**

A single `errorMessage` field serves as the persistent error display (e.g., "Failed to load orders"). If `claimError` used the same field, the snackbar auto-dismiss (`delay(3000); onClearClaimError()`) would also clear a real loading error. More importantly, the claim error is ephemeral — it should disappear after 3 seconds. A loading error should remain visible until the user retries. Using one field for both semantics requires tagging each error with its type, which is more complex than two nullable fields.

**Q6. The `claimOrder` transaction checks `status != PLACED` before writing. Could there be any state where `status == PLACED` but the order should still not be claimed?**

In the current state machine, `PLACED` is the only claimable state, so the check is complete. However, if a student cancels the order (transitions `PLACED → CANCELLED`) between the time the staff member sees it in their queue and the time the transaction reads the document, the transaction reads `status = CANCELLED`, fails the check, and returns `Result.failure("Order already claimed or no longer available")`. The snackbar message is slightly inaccurate for this case ("already claimed" vs "student cancelled"), but the outcome is correct: the staff member does not accept a cancelled order. A more precise implementation would read the actual status and return a tailored message.

**Q7. Why does `CafeOrderQueueScreen` not receive the full `ViewModel` as a parameter?**

Same reason as all other screens in this codebase: stateless composables are previewable with static `CafeQueueUiState` values, testable without a ViewModel, and reusable in hypothetical future scenarios (e.g., an admin view). The ViewModel is wired at the call site (in `MainActivity` or a navigation host), and the screen is a pure function of its parameters. This is the unidirectional data flow pattern: state flows down as parameters, events flow up as callbacks.

**Q8. What happens to the Firestore listener in `observeActiveOrders` if the staff member closes the app entirely (process killed)?**

When the process is killed, the Android system destroys all objects without calling lifecycle methods. `awaitClose` does not run. However, Firestore's SDK maintains listeners on a per-connection basis. When the TCP connection drops (because the process is gone), Firestore's servers automatically clean up the associated listener registrations server-side. The next time the app opens and `addSnapshotListener` is called, a fresh connection and listener is established. There is no resource leak on the server. On the client side, the `callbackFlow` channel and its reference to the lambda are garbage collected with the rest of the process.
