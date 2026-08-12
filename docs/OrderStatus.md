# OrderStatus — Design & Reference

## Architecture Layer

`OrderStatus.kt` sits in the **domain / data-model layer** — the innermost ring of Clean Architecture. This layer has one rule: it imports nothing. No Android SDK types, no Firebase types, no third-party libraries. Code at every other layer (Repository, ViewModel, UI) can depend on this file, but this file depends on nothing except the Kotlin standard library and `UserRole` from the same package.

The reason for this strict isolation is testability and longevity. A JVM unit test can import `OrderStatus` and call `canTransition` without booting an Android emulator, connecting to Firebase, or loading any framework. Business rules that can be tested in milliseconds are validated constantly; business rules buried inside Android components get tested rarely and break silently.

---

## Design Rationale

### Why a declarative transition table instead of `when` branches?

The natural first instinct is:

```kotlin
fun canTransition(from: OrderStatus, to: OrderStatus, role: UserRole): Boolean = when (from) {
    PLACED -> when (role) {
        CAFE_STAFF -> to in setOf(ACCEPTED, AWAITING_CONFIRMATION, REJECTED)
        STUDENT    -> to == CANCELLED
    }
    AWAITING_CONFIRMATION -> when (role) { ... }
    // ... and so on
}
```

This works, but it has a serious maintenance problem: the transition rules are fragmented across multiple `when` arms. To audit "what can a STUDENT do?", you have to read the entire function. To add a new transition, you locate the right `from` arm, then the right `role` arm, and you risk introducing a typo that quietly enables an illegal transition.

The Set-of-Triples approach encodes the same information as a flat, readable truth table:

```kotlin
private val allowedTransitions = setOf(
    Triple(PLACED, ACCEPTED,              CAFE_STAFF),
    Triple(PLACED, AWAITING_CONFIRMATION, CAFE_STAFF),
    ...
)
```

Every rule is one line. Adding a rule is one line. Revoking a rule is deleting one line. The lookup — `Triple(from, to, role) in allowedTransitions` — is O(1) thanks to hash-set membership. There is no branching logic to read; the table *is* the specification.

### Why top-level functions instead of enum members or a companion object?

`canTransition` depends on both `OrderStatus` and `UserRole`. Putting it on `OrderStatus` makes `OrderStatus` import `UserRole` (acceptable, they are in the same package), but it signals that `OrderStatus` *owns* the transition logic, which is misleading — the logic is a **policy** that sits above both enums. A future refactor might separate these enums into a `model` package and a `policy` package, and top-level functions make that move trivial.

### Why is `allowedTransitions` a `private val` at file level, not inside the function?

In the original version the Set was rebuilt on every call to `canTransition`. Because `canTransition` runs in the hot path (every status-change attempt by every user), this creates unnecessary allocations. Moving the Set to a `private val` at file scope constructs it exactly once — when the class is first loaded — and all subsequent calls share the same object.

### Why are terminal states absent from the `from` column?

`COLLECTED`, `CANCELLED`, `REJECTED`, and `EXPIRED` are intentional dead-ends. The business requirement is that once an order reaches one of these states, it cannot be changed. The simplest way to enforce this without an explicit guard is to simply not include any Triple where those states appear as the first element. `canTransition` will return `false` for them without any extra code.

---

## Non-obvious Parts

**`Triple` and structural equality**

`Triple(a, b, c)` is a data class from the Kotlin standard library. Because it is a data class, `==` compares all three fields by value, not by reference. This is what makes `Triple(from, to, role) in allowedTransitions` work correctly: it does not check whether the same object is in the Set; it checks whether any element in the Set has the same three field values.

**`OrderStatus.entries`**

`entries` is a Kotlin 1.9+ property on every enum that returns a stable, pre-built `List<E>`. The older `values()` function allocates a fresh array on every call. Because `allowedNextStates` calls `entries.filter { }`, and that call can happen frequently (e.g., when rendering a dropdown of valid next states), preferring `entries` over `values()` avoids repeated allocations.

---

## Traced Example: An Invalid Transition

Suppose a staff member tries to move an order from `READY` to `ACCEPTED` (which makes no logical sense but might happen due to a UI bug or a bad API call):

```kotlin
canTransition(from = OrderStatus.READY, to = OrderStatus.ACCEPTED, role = UserRole.CAFE_STAFF)
```

Execution path:

1. `Triple(READY, ACCEPTED, CAFE_STAFF)` is constructed.
2. `in allowedTransitions` calls `hashCode()` on the Triple and checks the appropriate hash bucket.
3. None of the 10 entries in `allowedTransitions` has `(READY, ACCEPTED, CAFE_STAFF)`.
4. Returns `false`.

No exception is thrown. The caller is responsible for acting on `false` — for example, by showing an error toast or refusing to update Firestore.

---

## Interview Questions

**Q1. Why must terminal states never appear as the `from` argument in the transition table?**

Terminal states (`COLLECTED`, `CANCELLED`, `REJECTED`, `EXPIRED`) represent the end of an order's lifecycle. A physical order that has been collected cannot be un-collected; a rejected order cannot be re-opened. These are business invariants. By omitting terminal states from all `from` positions in `allowedTransitions`, `canTransition` automatically returns `false` for any attempt to move out of them — without needing an explicit `if (from in terminalStates) return false` guard. The absence of a rule is itself the enforcement.

**Q2. What happens if a new `OrderStatus` value — say, `ON_HOLD` — is added but `allowedTransitions` is not updated?**

`canTransition` will return `false` for any transition involving `ON_HOLD` because the Set simply contains no Triple with `ON_HOLD` as any element. `allowedNextStates` will return an empty list for it. The app will not crash, but `ON_HOLD` orders will be effectively frozen — no role can move them forward or backward. This is a *safe default* (nothing breaks) but a silent one. To catch it, you'd add a unit test that asserts `allowedNextStates(ON_HOLD, CAFE_STAFF).isNotEmpty()` as soon as the enum value is added.

**Q3. Could you replace `Triple<OrderStatus, OrderStatus, UserRole>` with a named data class? What are the trade-offs?**

Yes:

```kotlin
private data class Transition(val from: OrderStatus, val to: OrderStatus, val role: UserRole)
```

**Gains**: The field names `from`, `to`, `role` make the call site self-documenting. You can add a `label: String` field for audit logging without changing the lookup logic. The class name `Transition` appears in stack traces and debug output instead of the generic `Triple`.

**Costs**: Three extra lines of boilerplate. For an internal private type used only in one file, `Triple` is arguably sufficient.

**Q4. Why are `canTransition` and `allowedNextStates` top-level functions rather than static members of `OrderStatus` (i.e., inside a `companion object`)?**

A companion object member logically belongs to its enclosing class. Placing `canTransition` inside `OrderStatus` would suggest that `OrderStatus` owns the transition policy — but the policy also references `UserRole`, which is an independent concern. Top-level functions in Kotlin are compiled to static methods on a `<FileName>Kt` JVM class, so they have the same performance characteristics as companion object members. The difference is purely semantic: top-level placement says "this function operates on these types; it belongs to neither."

**Q5. How would you write an exhaustive unit test for `canTransition` without writing one test case per transition?**

```kotlin
@Test
fun canTransition_matchesExpectedTruthTable() {
    // Define the expected truth table
    val allowed = setOf(
        Triple(PLACED, ACCEPTED,              CAFE_STAFF),
        Triple(PLACED, AWAITING_CONFIRMATION, CAFE_STAFF),
        // ... all 10 entries
    )

    // Test every combination of (from, to, role)
    for (from in OrderStatus.entries) {
        for (to in OrderStatus.entries) {
            for (role in UserRole.entries) {
                val expected = Triple(from, to, role) in allowed
                assertEquals(
                    "canTransition($from, $to, $role)",
                    expected,
                    canTransition(from, to, role)
                )
            }
        }
    }
}
```

This single test covers all 9 × 9 × 2 = 162 combinations and will automatically catch regressions if either enum gains new values, because `entries` always reflects the current set of values.
