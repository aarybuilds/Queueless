# Navigation Layer — Design & Reference
*Covers: `QueuelessNavGraph.kt`, `MainActivity.kt`, and navigation-related extensions in `AuthRepository.kt`.*

---

## 1. Why Navigation Lives in Its Own File and Not in `MainActivity`

Placing `NavHost` and route definitions inside `MainActivity.kt` creates several architectural issues:

1. **Activity Bloat & Coupling**: `MainActivity` is an Android framework entry point responsible for lifecycle initialization, window edge-to-edge configuration, and theme wrapping. Mixing navigation graphs, route parameters, and destination composables into `MainActivity` turns it into a God Class.
2. **Testability & Previews**: A standalone `QueuelessNavGraph` composable can be instantiated inside Compose UI tests or test harnesses by injecting a fake or test `AppContainer`. If navigation logic is tightly coupled to `MainActivity`, testing navigation flows requires launching the full `ComponentActivity`.
3. **Single Responsibility Principle**: `MainActivity` manages activity lifecycle events; `QueuelessNavGraph` declares the application's navigation topology and route transitions. Keeping them separated ensures that changes to navigation structure do not touch Activity code.

---

## 2. How the Auth State Observer Drives the Initial Route

Auth state in Queueless is exposed reactively as a `Flow<Boolean>` from `AuthRepository.observeAuthState()`, which wraps Firebase's `AuthStateListener` inside a `callbackFlow`.

In `QueuelessNavGraph`:

```kotlin
val isLoggedInState by appContainer.authRepository.observeAuthState()
    .collectAsStateWithLifecycle(initialValue = null)

LaunchedEffect(isLoggedInState) {
    when (isLoggedInState) {
        true -> {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == Routes.LOGIN || currentRoute == null) {
                navController.navigate(Routes.CAFETERIA_SELECTION) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
        }
        false -> {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        null -> { /* Loading state */ }
    }
}
```

- **App Launch**: `isLoggedInState` initially emits `null` while Firebase Auth determines session validity. Once evaluated, it emits `true` if a session exists or `false` otherwise.
- **Auto-Redirection**: If `true`, the graph automatically redirects from `login` to `cafeteria_selection`. If `false` (or when a user signs out), it pops the entire back stack and returns to `login`.

---

## 3. Why the Login Back Stack Is Cleared on Successful Login

When a user successfully logs in or registers, navigating to `cafeteria_selection` with a standard `navController.navigate()` call would leave `login` on top of the back stack.

If the user then presses the hardware Back button from `cafeteria_selection`, the back stack would pop to `login`, presenting an authenticated user with a sign-in screen. This violates standard Android navigation UX.

By configuring `popUpTo`:

```kotlin
navController.navigate(Routes.CAFETERIA_SELECTION) {
    popUpTo(Routes.LOGIN) { inclusive = true }
}
```

The `login` destination and any intermediate auth screens are popped off the back stack. `cafeteria_selection` becomes the new root destination of the stack. Pressing Back from `cafeteria_selection` exits the app rather than returning to sign-in.

---

## 4. How Role-Based Routing Works

In Queueless, users self-register as `STUDENT`, while `CAFE_STAFF` accounts are managed via Firebase Console. The user's role is stored in their Firestore document (`users/{uid}`).

When a user selects a canteen on `CafeteriaSelectionScreen`:

```kotlin
onCafeteriaSelected = { cafeteriaId ->
    scope.launch {
        val uid = appContainer.authRepository.getCurrentUserId()
        if (uid != null) {
            val role = appContainer.authRepository.getUserRole(uid)
            if (role == UserRole.STUDENT) {
                navController.navigate(Routes.menu(cafeteriaId))
            } else {
                navController.navigate(Routes.cafeQueue(cafeteriaId))
            }
        }
    }
}
```

1. **Role Fetching**: `getUserRole(uid)` reads the `User` document directly from Firestore `users/{uid}`.
2. **Branching**:
   - If `role == UserRole.STUDENT`, the user is routed to the customer menu (`menu/{cafeteriaId}`).
   - If `role == UserRole.CAFE_STAFF`, the user is routed to the staff order queue (`cafe_queue/{cafeteriaId}`).

This guarantees that authorization routing is derived directly from the backend Firestore document rather than mutable client-side state.
