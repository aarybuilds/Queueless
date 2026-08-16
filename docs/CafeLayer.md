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
