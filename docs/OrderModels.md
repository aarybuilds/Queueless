# Order Models — Design & Reference
*Covers: `UserRole`, `MenuItem`, `OrderItem`, `Order`, `User`*

## Architecture Layer

All five files live in `data/model/`, the **domain / data-model layer**. This is the innermost layer of Clean Architecture — it depends on nothing except the Kotlin standard library and other files in the same package. Every other layer (Repository, ViewModel, UI) can import these files; these files import none of them.

This unidirectional dependency rule has a practical consequence: you can instantiate and test any of these classes in a plain JVM unit test without Android, without Firebase, and without any test doubles. That means the core data shapes of the application — and the business rules embedded in them — are continuously and cheaply verifiable.

---

## UserRole

### Design Rationale

`UserRole` is an enum with two values: `STUDENT` and `CAFE_STAFF`.

The alternative is storing role as a raw `String` in Firestore and reading it back at runtime. The problem with that approach is that the role is then an untyped value throughout the app. A typo in a comparison (`"CAFE_STAFF"` vs `"cafe_staff"`) silently grants or denies access. An IDE can't autocomplete it. A `when` expression can't be exhaustiveness-checked for it.

By converting the Firestore string to a `UserRole` enum at the Repository boundary (the point where Firestore data enters the app), the rest of the codebase works with a type-safe value. The compiler catches missing cases in `when` expressions; the IDE offers completions; tests are trivially parameterisable over `UserRole.entries`.

### Non-obvious Parts

None — two enum values is the simplest possible design, and the naming is self-explanatory.

---

## MenuItem

### Design Rationale

`MenuItem` represents a **live menu entry** — what the café currently offers. It is the source of truth for price and availability at browse time, but it is deliberately *not* embedded in orders. Orders embed `OrderItem` (see below), which snapshots only the fields relevant to billing.

`prepTimeMinutes` is included so the UI can display estimated wait time per item and so a future feature (e.g., "your order will be ready in approximately X minutes") can sum prep times without touching Firestore again.

### Why all defaults?

Firestore's `DocumentSnapshot.toObject<MenuItem>()` uses Java reflection to call the no-argument constructor. Kotlin generates a no-arg constructor automatically only when every constructor parameter has a default value. If even one field lacks a default, Firestore throws `RuntimeException: no-arg constructor not found` at runtime — a failure that won't appear in any compile-time check.

---

## OrderItem

### Design Rationale

`OrderItem` is a **value-object snapshot** of a `MenuItem` at the moment an order is placed. It captures `priceAtOrder` and `name` directly rather than storing just an `itemId` and fetching the live `MenuItem` later.

This design choice has two consequences:

1. **Historical accuracy**: If the café changes a menu price after an order is placed, the receipt still shows the correct amount. An order history that re-reads live menu prices would silently show wrong totals for past orders.

2. **Resilience to menu deletion**: If a `MenuItem` document is deleted from Firestore (the item is discontinued), existing `OrderItem` records are unaffected — they carry all the data they need.

`isAvailable` is included on `OrderItem` because a café staff member might mark an ordered item as unavailable mid-preparation (they ran out of stock). The `Order.availableTotal` computed property uses this flag to calculate an adjusted total.

---

## Order

### Design Rationale

`Order` is the central aggregate of the application. It ties together a student identity (`studentUid`, `studentName`, `studentRollNumber`), a cafeteria (`cafeteriaId`), a list of snapshotted items, and a lifecycle (`status`).

**`totalAmount` vs `availableTotal`**

`totalAmount` is set when the order is created and persisted in Firestore. It represents the original agreed amount.

`availableTotal` is a computed property that sums `priceAtOrder * quantity` only for items where `isAvailable == true`. It is declared inside the class body as a `val` with a custom `get()`, which means:

- It is **not a constructor parameter**, so Firestore's deserializer ignores it. Computed properties do not become Firestore fields.
- It is **recomputed on every access**, so it always reflects the current state of `items` without any possibility of stale cache.

This separation gives the UI two distinct values: the original committed amount and the adjusted amount after any item availability changes.

**Nullable timestamps**

`claimedAt`, `readyAt`, and `collectedAt` are `Long?` (nullable). `null` means the event has not occurred yet. The alternative — using `0L` as a sentinel — is ambiguous: does `0L` mean "not yet happened" or "January 1, 1970"? Nullable types express intent precisely and the Kotlin compiler enforces null-checks at every use site.

**`claimedBy` / `claimedByName`**

These fields record which staff member accepted the order. They exist for accountability and for future features (e.g., performance dashboards per staff member).

### Non-obvious Parts

`sumOf { it.priceAtOrder * it.quantity }` is a stdlib extension function that folds the filtered collection into a `Double` by applying the lambda to each element and accumulating the results. It is equivalent to a reduce-with-transform and produces no intermediate collection.

---

## User

### Design Rationale

`User` is the app's internal representation of an authenticated account. It mirrors the Firestore `users` collection document structure.

`uid` duplicates the Firestore document ID. This is intentional: when a `User` object is passed around in memory, it carries its own identity, so any receiver can reference its Firestore document without needing to track the document ID separately.

`noShowCount` records how many times a student placed an order and did not collect it. This field exists for a planned feature (restricting ordering privileges after repeated no-shows). Initialising it to `0` in the model ensures that freshly created user documents start at the correct baseline.

`role` defaults to `UserRole.STUDENT` because students self-register through the app. Staff accounts are created manually in Firebase Console with a `role: "CAFE_STAFF"` field, and the Repository layer maps that string back to `UserRole.CAFE_STAFF` when fetching the document.

---

## Interview Questions

**Q1. Why do all data class fields have default values, and what goes wrong if one doesn't?**

Firestore's `toObject<T>()` uses Java reflection to construct objects. It calls the **no-argument constructor** and then sets each field. Kotlin generates a no-arg constructor automatically only when all parameters have defaults. If even one parameter lacks a default — say, `val uid: String` (no default) — Kotlin generates a constructor that requires `uid` to be passed. Firestore then fails at runtime with `java.lang.RuntimeException: Could not deserialize object. No-arg constructor found` (the exact wording varies by SDK version). This failure only appears at runtime, not at compile time, so it is easy to miss in testing.

**Q2. What is the conceptual difference between `MenuItem` and `OrderItem`? Why not just store `MenuItem` directly inside `Order`?**

`MenuItem` is **current truth** — it reflects what the café offers right now. `OrderItem` is **historical truth** — it records what the customer ordered and what they agreed to pay. Embedding `MenuItem` inside `Order` would mean the order's price and name update whenever the menu changes, which would corrupt order history. The snapshot approach (`OrderItem.priceAtOrder`, `OrderItem.name`) ensures a receipt is immutable after placement, just like a paper receipt.

**Q3. `Order.availableTotal` is declared with `val ... get() = ...` rather than as a regular property. What is the exact difference and why does it matter for Firestore?**

A regular `val` (`val availableTotal: Double = computeIt()`) stores its value in a backing field, which is a constructor parameter from Firestore's perspective. A `val` with a custom `get()` has **no backing field**: the getter body runs on every access, and the property is never stored or serialised. Firestore's `@DocumentId` annotation aside, the SDK serialises only fields that have backing storage. A custom getter is therefore invisible to Firestore — it is purely a client-side derived value, which is exactly what we want.

**Q4. `claimedAt`, `readyAt`, and `collectedAt` are `Long?` not `Long`. Why?**

Using `0L` as "not yet happened" is a sentinel pattern — a magic value that means something other than its literal meaning. Sentinel patterns are error-prone: a developer who forgets the convention might treat `0L` as epoch time. `Long?` (`null` = not yet happened) is unambiguous: the Kotlin compiler will not let you call `.toInstant()` or format a `null` timestamp without an explicit null check. The intent is in the type.

**Q5. Why is `UserRole` an enum rather than a sealed class or a string constant?**

Three reasons: (1) **Exhaustiveness**: a `when (role)` expression on an enum must cover all values or include an `else`; the compiler enforces this. A sealed class would also give exhaustiveness, but adds more boilerplate than warranted for exactly two cases with no associated data. (2) **Serialisation**: Firestore stores enums as their `name` string (`"STUDENT"`, `"CAFE_STAFF"`), and deserialises them back via `Enum.valueOf()`. This is built into the Firestore SDK and requires no custom adapter. (3) **Simplicity**: two values, no state — an enum is the minimum-complexity correct tool.
