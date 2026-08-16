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
