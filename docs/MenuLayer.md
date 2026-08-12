# Menu Layer — Design & Reference
*Covers: `MenuDataSource.kt`, `MenuRepository.kt`, `MenuViewModel.kt`, `MenuScreen.kt`, `CafeteriaSelectionScreen.kt`*

---

## 1. Why `cafeteriaId` Is a Constructor Parameter of `MenuViewModel`

`MenuViewModel` needs to know which cafeteria's menu to load before it can do any work. There are two ways to supply this information:

**Option A — constructor parameter** (what we do):
```kotlin
class MenuViewModel(
    private val menuRepository: MenuRepository,
    private val cafeteriaId: String
) : ViewModel()
```

**Option B — a setter or `StateFlow` input** (the alternative):
```kotlin
fun setCafeteriaId(id: String) {
    _cafeteriaIdFlow.value = id
}
```

Option B has a fundamental flaw: the ViewModel would start its lifecycle with no cafeteria selected, in an indeterminate state. You'd need to guard against the empty-string case everywhere and handle the possibility that `setCafeteriaId` is called before or after `init` completes. The screen would also flash in a loading state before the ID arrives.

With Option A, the ViewModel is fully configured at construction time. By the time `init` runs, `cafeteriaId` is already set, and the Firestore listener is attached with the correct collection path immediately. The ViewModel is never in an intermediate "no cafeteria chosen" state.

The cost: because the ViewModel holds a specific cafeteria, a student who switches from Nescafe to Amul gets a new ViewModel instance (the old one is discarded). This is intentional — each ViewModel represents exactly one cafeteria's menu, and the composition of responsibility is clear.

**How the factory enables this**: The `ViewModelProvider.Factory` in `AppContainer` receives the `cafeteriaId` at creation time and injects it:

```kotlin
fun menuViewModelFactory(cafeteriaId: String) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MenuViewModel(menuRepository, cafeteriaId) as T
    }
```

---

## 2. Real-Time Listener: End-to-End Trace

**Scenario**: A student has the Nescafe menu open. A staff member changes the price of "Veg Sandwich" from ₹40 to ₹45 in the Firebase Console.

**Step-by-step**:

1. **Firebase Console** writes the updated document to Firestore's servers. The document's `price` field becomes `45.0`.

2. **Firestore SDK** (running on the student's device) detects the change via a persistent WebSocket / gRPC stream it maintains with Firestore's servers. This connection was established when `addSnapshotListener` was called.

3. **`MenuDataSource.observeMenuItems`** — the `addSnapshotListener` callback fires on the main thread. `snapshot` now contains the updated collection. The code runs:
   ```kotlin
   val items = snapshot.documents.mapNotNull { doc ->
       doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
   }
   trySend(items)
   ```
   `trySend` places the updated `List<MenuItem>` into the `callbackFlow`'s channel buffer.

4. **`MenuRepository.getMenuItems`** — passes the flow through unchanged (no transformation at this layer yet).

5. **`MenuViewModel.init` coroutine** — the `collect { }` block resumes. It calls:
   ```kotlin
   _uiState.update {
       it.copy(
           items = items,
           groupedByCategory = items.groupBy { item -> item.category },
           isLoading = false
       )
   }
   ```
   `_uiState` is a `MutableStateFlow`, so setting a new value immediately notifies all active collectors.

6. **`MenuScreen`** — is collecting `uiState` via `collectAsState()`. `StateFlow` emits the new state. Compose's snapshot system detects that a state object read during the last composition has changed.

7. **Recomposition** — Compose schedules a recomposition of `MenuScreen` on the next frame. The `LazyColumn` diffs the old and new `items` using the `key = { it.id }` stable keys. It identifies that the "Veg Sandwich" row changed and recomposes only that `MenuItemRow`, updating the price text to "₹45.00".

**Total latency from Console save to screen update**: typically 200–800 ms, governed by Firestore's real-time delivery latency, not app code.

---

## 3. Why `groupedByCategory` Is Computed in the ViewModel, Not the Composable

`groupedByCategory` requires iterating the full `items` list and constructing a `Map`. If this computation lived inside `MenuScreen`:

```kotlin
// Inside the Composable — bad
val grouped = uiState.items.groupBy { it.category }
```

It would re-run on **every recomposition** of `MenuScreen`. Compose recomposes aggressively — whenever any state read by `MenuScreen` changes, including unrelated state changes in parent composables. Even if `items` is identical, the `groupBy` would execute again and produce a new `Map` object that invalidates the `LazyColumn`'s internal state.

By computing `groupedByCategory` in the ViewModel and storing it in `MenuUiState`:

- The `groupBy` runs exactly once per menu update (each Firestore emission).
- `MenuScreen` receives a pre-built `Map` as a parameter. It is a pure rendering function — it maps data to pixels, it computes nothing.
- A `@Preview` composable can pass any static `MenuUiState` without mocking the ViewModel.

This is a specific application of the general rule: **derive state in the ViewModel, render state in the Composable**.

---

## 4. Why `MenuScreen` Takes State and Lambdas Instead of the ViewModel Directly

The alternative:

```kotlin
@Composable
fun MenuScreen(viewModel: MenuViewModel) {
    val state by viewModel.uiState.collectAsState()
    ...
}
```

This couples the composable to a concrete class. Consequences:

1. **Previews break**: `@Preview` cannot construct a `MenuViewModel` (it needs `MenuRepository`, which needs Firestore, which needs a running Firebase project). The preview shows nothing.

2. **Testing requires a ViewModel**: Even a layout test that just checks "does the spinner show when `isLoading` is true?" must construct the full dependency chain. With state + lambdas, the test passes a `MenuUiState(isLoading = true)` directly.

3. **The composable is harder to reuse**: If a future admin screen needs the same item list UI with a different data source, it cannot reuse `MenuScreen` — it would need its own ViewModel type.

The pattern `fun MenuScreen(uiState: MenuUiState, onItemClick: (MenuItem) -> Unit)` makes `MenuScreen` a **pure function of its parameters**. The ViewModel is wired in at the call site, not inside the composable:

```kotlin
// In the parent composable (future navigation host):
val state by viewModel.uiState.collectAsState()
MenuScreen(uiState = state, onItemClick = { item -> /* navigate */ })
```

---

## Interview Questions

**Q1. What is `callbackFlow` and how does it differ from a regular `flow { }` builder?**

`flow { }` is a **cold, sequential** builder. It executes its lambda body each time a collector subscribes, and it uses `emit()` — a suspending function — to produce values. It cannot call `emit()` from callbacks on other threads because `emit()` is not thread-safe.

`callbackFlow` is designed specifically for callback-based APIs. It uses a `Channel` internally, which is thread-safe. `trySend()` can be called from any thread (e.g., the Firestore SDK's callback thread) without suspending. `awaitClose { }` suspends the producer coroutine until the flow is cancelled, at which point the cleanup block runs synchronously. This pattern converts any listener/callback API into a Flow without losing any emissions.

---

**Q2. What happens if the student's device loses internet connection while the menu is open?**

Firestore's SDK maintains an offline cache. When connectivity is lost:

- The snapshot listener continues to fire but with data from the local cache. The `snapshot.metadata.isFromCache` flag would be `true`.
- Our `addSnapshotListener` callback still fires and `trySend(items)` sends the cached data. The UI remains usable.
- When connectivity is restored, Firestore delivers any missed server-side changes as a new snapshot emission.

Our current code does not distinguish cache vs. server data. A future enhancement could check `snapshot.metadata.isFromCache` and show a "Showing cached data" banner when true.

---

**Q3. Why does `MenuDataSource` call `registration.remove()` in `awaitClose` rather than leaving the listener attached?**

Firestore snapshot listeners hold a reference to the collection path, a callback lambda, and (implicitly) the enclosing scope. If `remove()` is not called:

- The lambda captures `this` (the channel), keeping the `MenuDataSource` and its associated coroutine alive.
- Firestore continues delivering snapshots to a channel that nobody is reading.
- Memory: the undrained channel buffer grows; in the worst case, `trySend` starts dropping values silently.

`awaitClose { registration.remove() }` runs when the `callbackFlow`'s collector is cancelled — either because the screen left composition, or `viewModelScope` was cancelled (when the ViewModel is cleared). The listener is detached promptly, releasing all resources.

---

**Q4. Why does `MenuViewModel` use `catch` between the flow operators rather than a try/catch inside the `collect` lambda?**

```kotlin
menuRepository.getMenuItems(cafeteriaId)
    .catch { error -> /* handle */ }
    .collect { items -> /* use */ }
```

`catch` is a **flow operator** that catches exceptions thrown **upstream** (by the flow itself or by any upstream operator). It does not catch exceptions thrown inside the `collect` lambda — those propagate to the coroutine's `CoroutineExceptionHandler`.

A `try/catch` around the `collect` call would catch both upstream errors and exceptions inside `collect`, making error attribution ambiguous. Using `catch` as an operator is more precise: it handles data-source failures (e.g., Firestore permission denied) without masking programming errors in the collector body.

---

**Q5. `LazyColumn` items are keyed with `key = { it.id }`. What happens if you omit the key?**

Without a key, `LazyColumn` identifies items by their **position** in the list. When a Firestore snapshot arrives with reordered or inserted items, Compose cannot tell which composable corresponds to which `MenuItem`. It discards and recreates all visible items, causing:

- Unnecessary recomposition and layout of every visible row, even unchanged ones.
- Loss of scroll position (the column may jump).
- Loss of any local UI state inside each row (e.g., an expanded/collapsed toggle).

With `key = { it.id }`, Compose maps each `MenuItem.id` to its previously rendered composable. An unchanged item (same `id`, same `price`) is skipped entirely. A price change produces a targeted recomposition of only that row. An insertion adds only the new row.

---

**Q6. `MenuRepository` is a thin wrapper that just delegates to `MenuDataSource`. Is it worth having?**

Yes, for two reasons:

1. **Testing**: `MenuViewModel` tests can use a `FakeMenuRepository` that returns a controlled `Flow<List<MenuItem>>` without needing a real `MenuDataSource` or Firestore. If `MenuViewModel` called `MenuDataSource` directly, tests would need to mock Firestore.

2. **Future change isolation**: When we add category filtering, price-range filtering, or an offline-first cache (e.g., Room), those changes go into `MenuRepository`. The ViewModel's `getMenuItems(cafeteriaId)` call site is unchanged. The `MenuDataSource` API is unchanged. Each layer absorbs the change for which it is responsible.

---

**Q7. What does `doc.toObject(MenuItem::class.java)?.copy(id = doc.id)` do, and why is it structured this way?**

`doc.toObject(MenuItem::class.java)` uses Firestore's reflection-based deserializer to construct a `MenuItem` from the document's fields. It returns `null` if the document is empty or cannot be deserialized (hence the `?.` safe call).

`.copy(id = doc.id)` is then applied on the non-null result. Firestore document IDs are not stored as fields within the document body — they are metadata. If we stored `id` as a separate field in each document, we'd have a denormalization problem: the field value could differ from the actual document ID. Using `doc.id` as the authoritative source ensures the `id` in our `MenuItem` always matches the key used to read or write the Firestore document.

---

**Q8. How would you add a "search by name" feature to `MenuScreen` without modifying `MenuDataSource` or `MenuRepository`?**

1. Add a `searchQuery: String = ""` field to `MenuUiState`.
2. Add an `onSearchQueryChange(query: String)` function to `MenuViewModel` that updates `_uiState` with the new query and recomputes `groupedByCategory`:
   ```kotlin
   fun onSearchQueryChange(query: String) {
       _uiState.update { state ->
           val filtered = if (query.isBlank()) state.items
                          else state.items.filter { it.name.contains(query, ignoreCase = true) }
           state.copy(
               searchQuery = query,
               groupedByCategory = filtered.groupBy { it.category }
           )
       }
   }
   ```
3. Add a `SearchBar` composable to `MenuScreen` that calls the `onSearchQueryChange` lambda (passed from the parent).

`MenuDataSource` still delivers the full menu in real time. Filtering is client-side, in the ViewModel, and is applied on top of every incoming snapshot. The separation of concerns means each layer does exactly what it is responsible for.
