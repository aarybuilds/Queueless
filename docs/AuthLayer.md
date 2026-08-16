# Auth Layer — Design & Reference
*Covers: `User.kt`, `AppContainer.kt`, `AuthDataSource.kt`, `AuthRepository.kt`, `LoginViewModel.kt`, `LoginScreen.kt`, `MainActivity.kt`*

---

## Full Data Flow

```
LoginScreen
    │  collectAsState(uiState)          [reads StateFlow]
    │  viewModel.submit()               [user taps button]
    ▼
LoginViewModel
    │  authRepository.signIn() / signUp()
    │  updates _uiState (isLoading, errorMessage)
    ▼
AuthRepository
    │  validates email domain           [domain rule, before any network call]
    │  authDataSource.signUp()
    │  firestore.collection("users").document(uid).set(user)
    ▼
AuthDataSource
    │  auth.createUserWithEmailAndPassword().await()
    │  auth.signInWithEmailAndPassword().await()
    │  returns Result<String>           [uid on success, exception on failure]
    ▼
Firebase (Auth + Firestore)

[Response travels back up the same chain]

Firebase → AuthDataSource → Result<T>
AuthDataSource → AuthRepository → Result<Unit>
AuthRepository → LoginViewModel → _uiState.update { errorMessage / isLoading }
LoginViewModel → StateFlow → LoginScreen recomposes
```

The auth **state** (is anyone logged in?) travels on a separate, parallel path:

```
FirebaseAuth.addAuthStateListener
    ▼ (via callbackFlow)
AuthRepository.observeAuthState(): Flow<Boolean>
    ▼ (collected by the root navigation composable, not LoginScreen)
Navigate away from LoginScreen when Flow emits true
```

---

## Why Each Layer Exists

### AuthDataSource — the Firebase boundary

**Purpose**: contain all `FirebaseAuth` imports and all `FirebaseAuth` exception types in a single file.

**What breaks if you skip it**: Without this layer, `AuthRepository` would import Firebase directly. That seems fine until you want to write a unit test for `AuthRepository` — you would need a live Firebase connection or a complex fake to substitute for `FirebaseAuthException`. With `AuthDataSource` as a separate class, you can replace it with a simple fake in tests:

```kotlin
class FakeAuthDataSource : AuthDataSource(...) {
    override suspend fun signUp(...) = Result.success("fake-uid")
}
```

`AuthRepository` tests can then run instantly in any JVM environment.

### AuthRepository — the domain and coordination layer

**Purpose**: enforce business rules (email domain), coordinate between `AuthDataSource` (Firebase Auth) and Firestore (user document creation), and expose a clean `Result<Unit>` API to callers.

**What breaks if you skip it**: If `LoginViewModel` called `AuthDataSource` directly, the ViewModel would have to (a) know about the `@iiitl.ac.in` domain rule, (b) know about Firestore and write the user document, and (c) handle raw Firebase exceptions. The ViewModel would become a God Object. It would also be harder to test because you'd need to mock both Firebase services in every ViewModel test.

### LoginViewModel — the UI state machine

**Purpose**: translate user intent (button taps, text input) into state changes, call the repository, and expose a single `StateFlow<LoginUiState>` that the composable observes.

**What breaks if you skip it**: If `LoginScreen` called `AuthRepository` directly (possible with Compose + coroutines, using `rememberCoroutineScope`), the screen would own both presentation logic and business coordination. That makes the screen impossible to preview with static data, impossible to test without rendering a UI, and hard to navigate away from on success. The ViewModel survives navigation recompositions; a coroutine launched from a composable does not.

### LoginScreen — stateless rendering

**Purpose**: read `uiState` and render it. Call ViewModel functions in response to user events. It does not decide what to show — it maps the state to pixels.

**What breaks if you skip it (i.e., merge screen with ViewModel)**: Composables tied to business logic cannot be previewed cleanly, cannot be tested without a full Compose test environment, and violate the principle that UI should be a pure function of state.

---

## Why `Result<T>` Instead of Try/Catch at the Call Site

`Result<T>` is a Kotlin standard library wrapper that holds either a successful value (`Result.success(value)`) or a failure (`Result.failure(exception)`). It forces callers to handle both cases explicitly.

The alternative — letting Firebase exceptions propagate as thrown exceptions — creates several problems:

1. **Implicit failure paths**: A `suspend fun signIn()` that can throw `FirebaseAuthInvalidCredentialsException` forces every caller to wrap it in `try/catch`. If a caller forgets, the exception propagates up to the coroutine's uncaught exception handler, crashing or silently swallowing the error.

2. **Type leakage**: `FirebaseAuthInvalidCredentialsException` is a Firebase type. If it propagates to the ViewModel, the ViewModel must import `com.google.firebase.auth.*` — violating the constraint that the ViewModel must not directly reference Firebase types.

3. **`Result<T>` makes success and failure equal citizens**: `result.fold(onSuccess = { ... }, onFailure = { ... })` is symmetric. The developer cannot accidentally read the success value without also handling the failure branch.

`runCatching { }` is the standard idiom for wrapping a block that might throw into a `Result`:

```kotlin
runCatching {
    auth.createUserWithEmailAndPassword(email, password).await()
}
// Returns Result<AuthResult>; any thrown exception becomes Result.failure(e)
```

---

## Why Domain Validation Happens in the Repository, Not the ViewModel or the DataSource

The `@iiitl.ac.in` domain restriction is a **business invariant**: it is a rule about which emails are permitted in the system. It is not a UI validation rule (the ViewModel's job is UI state) and it is not a Firebase API constraint (the DataSource's job is talking to Firebase). It belongs in the Repository, which owns business rules for the auth domain.

**Why not in the ViewModel?** Because the same rule would need to be re-enforced in any other entry point that allows registration (e.g., a future admin registration API, a batch import tool). If the rule lives in the ViewModel, it must be duplicated at every entry point. In the Repository, it executes once regardless of which surface triggered the call.

**Why not in the DataSource?** The DataSource's job is mechanical translation between Kotlin coroutines and Firebase callbacks. It should have no knowledge of business rules. A DataSource that validates email domains is mixing two unrelated concerns.

**Fail-fast ordering**: The email check runs *before* calling `authDataSource.signUp()`. This avoids wasting a network round-trip to Firebase just to fail on an invalid domain. The pattern is: validate cheap/local things first, then make the expensive network call.

```kotlin
if (!email.endsWith("@iiitl.ac.in")) {
    return Result.failure(IllegalArgumentException("Only @iiitl.ac.in email addresses can register."))
}
// Only reaches here if domain is valid
val signUpResult = authDataSource.signUp(email, password)
```

---

## What `StateFlow` Is and Why It's Used Instead of LiveData

### `StateFlow<T>`

`StateFlow<T>` is a hot, conflated flow from the `kotlinx.coroutines` library. "Hot" means it holds a value even when no one is collecting it. "Conflated" means that if the value changes faster than the collector processes it, the collector only sees the most recent value — intermediate values are dropped. It always has a current value (the `value` property is never null, unlike `LiveData`).

In a ViewModel, the pattern is:

```kotlin
private val _uiState = MutableStateFlow(LoginUiState())
val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
```

`MutableStateFlow` is writable (private). `asStateFlow()` returns a read-only view that the composable observes, preventing the UI from mutating state directly.

### Why not `LiveData`?

| | `LiveData` | `StateFlow` |
|---|---|---|
| Android dependency | Yes (`androidx.lifecycle`) | No (pure Kotlin coroutines) |
| Value on creation | Starts null | Requires initial value |
| Coroutines integration | Requires `asFlow()` adapter | Native |
| Testability | Requires Android JUnit runner or `InstantTaskExecutorRule` | Plain JVM unit test |
| Composable collection | `observeAsState()` | `collectAsState()` |

The key reason for `StateFlow` here is **testability without Android instrumentation**. A ViewModel unit test can verify that `submit()` correctly updates `isLoading` and `errorMessage` by collecting the `StateFlow` in a `runTest {}` block, with no Android runtime involved.

The secondary reason is that `StateFlow` is idiomatic for Compose. `collectAsState()` integrates cleanly with Compose's recomposition model.

---

## `callbackFlow` — Bridging Callbacks to Flows

Firebase's auth state listener uses a callback pattern:

```kotlin
auth.addAuthStateListener { firebaseAuth ->
    // called whenever auth state changes
}
```

`callbackFlow` is a coroutines builder that lets you convert this push-based callback pattern into a `Flow`:

```kotlin
callbackFlow {
    val listener = auth.addAuthStateListener { trySend(it.currentUser != null) }
    awaitClose { auth.removeAuthStateListener(listener) }
}
```

`trySend` puts a value into the flow's channel without suspending. `awaitClose` is critical: it runs when the flow collector is cancelled (e.g., when the composable leaves composition), and removes the Firebase listener to prevent a memory leak. Without `awaitClose`, the listener would outlive the UI and keep firing callbacks into a dead channel.

---

## `AppContainer` — Manual Dependency Injection

`AppContainer` is not a framework — it is just a class that holds the dependency graph. The pattern is:

1. Construct Firebase singletons (`FirebaseAuth.getInstance()`, `FirebaseFirestore.getInstance()`).
2. Wrap them in `DataSource` objects.
3. Wrap those in `Repository` objects, using `by lazy` so construction is deferred until first access.

`by lazy` is a Kotlin property delegate. It executes the lambda on the first access and caches the result. All subsequent accesses return the cached value. This means `authDataSource` and `authRepository` are constructed exactly once, lazily, on demand.

`AppContainer` is typically instantiated in a custom `Application` class:

```kotlin
class QueuelessApp : Application() {
    val container = AppContainer()
}
```

The `Application` object lives for the entire process lifetime, so `container` is effectively a process-scoped singleton — the same lifetime that Hilt's `@Singleton` scope provides, without the annotation processor.

---

## Manual ViewModel Factories in Compose (No Hilt)

### The problem

Jetpack's `viewModel()` composable function constructs a ViewModel using `ViewModelProvider`. By default, `ViewModelProvider` expects a no-argument constructor. `LoginViewModel` takes `AuthRepository` as a constructor parameter, so the default factory would crash with `IllegalArgumentException: cannot create an instance of LoginViewModel`.

### The solution: `ViewModelProvider.Factory`

`ViewModelProvider.Factory` is a single-method interface:

```kotlin
interface Factory {
    fun <T : ViewModel> create(modelClass: Class<T>): T
}
```

We implement it as an anonymous object in `AppContainer`, wiring the dependency manually:

```kotlin
val loginViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LoginViewModel(authRepository) as T
}
```

The `@Suppress("UNCHECKED_CAST")` is safe here because this factory is dedicated to `LoginViewModel`. The cast will never fail for its intended use. In a larger project you would guard with `if (modelClass.isAssignableFrom(LoginViewModel::class.java))` and throw for unrecognised types.

In `MainActivity`, passing the factory to `viewModel()` delegates construction to our code instead of the framework default:

```kotlin
val loginViewModel: LoginViewModel = viewModel(factory = appContainer.loginViewModelFactory)
```

`viewModel()` then caches the result in the `ViewModelStore` attached to the Activity. Subsequent calls — including after recomposition — return the same instance. Screen rotation recreates the Activity and re-enters `setContent`, but `viewModel()` returns the already-created instance from the store rather than calling the factory again.

### Why `AppContainer` lives on `MainActivity`

`AppContainer` must outlive the factory call. The factory is only invoked once (on first composition), after which the ViewModel is cached. Placing `AppContainer` as a field on `MainActivity` achieves this — it lives as long as the Activity process.

`AppContainer` is **not** placed in the `Application` class at this stage of the project to keep wiring simple. If a second Activity (e.g., a staff-only activity) needed the same container, moving it to `Application` would be the right next step.

### Why `LaunchedEffect(Unit)` for the auth state log

`LaunchedEffect(Unit)` launches a coroutine that lives as long as the composable it is called from. `Unit` as the key means it launches once and is not restarted on recomposition. It collects `observeAuthState()` and logs every emission. This is a temporary scaffolding: when navigation is wired up, this block will be replaced by a `LaunchedEffect` that calls `navController.navigate(...)` on `isLoggedIn == true`.

---

## Email Verification

Queueless requires self-registered students to verify their email address via a link dispatched during sign-up before accessing canteen features. Staff accounts created manually in the Firebase Console bypass this check by default.

### Why `reload()` is required before checking `isEmailVerified`

`auth.currentUser?.isEmailVerified` reads from Firebase Auth's local cached user record on the device. When a student opens their email app and clicks the verification link, Firebase Auth's backend servers update the user's `emailVerified` flag to `true`.

However, the local Firebase Auth SDK on the Android device does **not** automatically sync this change in real time. If the app queries `isEmailVerified` without reloading, it reads the stale cached flag (`false`) and continues to block the user.

Calling `authDataSource.reloadUser()` (which invokes `firebaseAuth.currentUser?.reload()?.await()`) forces the SDK to make a quick network call to Firebase Auth servers and refresh the local user profile token. Once reloaded, `isEmailVerified` reflects the true, live verification status.

### Why a failed verification email send doesn't block account creation

During sign-up (`AuthDataSource.signUp`):

```kotlin
val result = auth.createUserWithEmailAndPassword(email, password).await()
val uid = result.user?.uid ?: error("Firebase returned no UID after sign-up")
runCatching { auth.currentUser?.sendEmailVerification()?.await() }
return Result.success(uid)
```

The initial `sendEmailVerification()` call is wrapped in `runCatching` and its failure is intentionally swallowed.

**Reasoning**: The primary operations — creating the Firebase Auth user account and writing the user profile document to Firestore — succeeded. If the initial email dispatch fails (e.g. due to a transient SMTP rate limit, temporary network flicker, or email provider delay), failing the entire sign-up flow would leave the user in a broken state (an account created in Auth/Firestore but the client app reporting a failure).

By swallowing the email dispatch exception during sign-up, registration completes successfully and the app navigates to `EmailVerificationScreen`. From there, the student can tap "Resend email" to trigger a fresh `sendEmailVerification()` task whenever they are ready.

