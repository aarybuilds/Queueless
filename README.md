# 🍽 Queueless
### Campus Cafeteria Pre-Ordering App — IIIT Lucknow

> **No more queues. Order ahead, collect when it's ready.**

Queueless is an Android app that lets IIIT Lucknow students pre-order food from campus cafeterias (Nescafe and Amul) and track their order in real time — eliminating physical queues entirely.

Built for the **OverEngineered App Wing** selection task.

---

## 📱 Download

| Release | APK |
|---------|-----|
| v1.0.0 | [Download APK](https://github.com/aarybuilds/Queueless/releases/tag/v1.0.0) |

---

## ✨ Features

### 🎓 Student Side
- **Email-Verified Sign Up** — only `@iiitl.ac.in` addresses allowed; Firebase sends a verification link before access is granted.
- **Live Menu Sync** — real-time menu from Firestore; unavailable items are greyed out instantly when staff marks them.
- **Inline Cart Controls** — add/remove items directly from the menu screen with real-time total computation.
- **Active Order Guard** — max 3 active orders enforced to prevent queue spamming and canteen overload.
- **Live Order Tracking** — real-time status progression (`Placed` → `Accepted` → `Preparing` → `Ready` → `Collected`).
- **Partial Order Confirmation** — if certain items run out of stock, students view modified orders and can confirm or cancel.
- **Order History** — full record of past orders with timestamps, canteen labels, and total receipts.
- **Suggestion Box** — submit feedback or dish requests directly to canteen management (500-character limit).
- **Profile & Account Management** — view profile, change password via authenticated dialog, sign out.

### 👨‍🍳 Cafe Staff Side
- **Locked Cafeteria Queue** — staff accounts are automatically locked to their assigned cafeteria (`assignedCafeteriaId` from Firestore), skipping selection and ensuring single-canteen access control.
- **Atomic Order Claiming** — Firestore transaction-based claiming prevents two staff members from taking the same order simultaneously.
- **Partial Availability Adjustments** — adjust individual item quantities; student is notified in real time to confirm or cancel.
- **Direct Rejection** — if all items are out of stock, reject the order directly without unnecessary student confirmation steps.
- **One-Tap Status Progression** — `Accept` → `Start Preparing` → `Mark Ready` → `Collected`.
- **No-Show Management** — mark orders as `Expired` if students fail to collect within the designated window.

---

## 🏗 Architecture

**MVVM + Repository Pattern + Manual Dependency Injection**

```
UI Layer          — Jetpack Compose screens + ViewModels
Repository Layer  — Single source of truth, domain validation
DataSource Layer  — Pure Firebase SDK operations
DI Layer          — AppContainer (Manual DI root)
```

### Why Manual DI (`AppContainer`) over Hilt?
Hilt generates code that abstracts container creation. Building `AppContainer` manually forces an explicit understanding of dependency lifecycles — ViewModel factories, singleton scoping, lazy initialization — before relying on annotations.

---

## 🗂 Project Structure

```
com.iiitl.canteen/
├── data/
│   ├── model/          # Pure Kotlin data classes (User, MenuItem, Order, OrderItem, OrderStatus)
│   ├── remote/         # Direct Firebase SDK implementations (Auth, Menu, Order, CafeOrder, Suggestion)
│   └── repository/     # Business logic & domain validation (Auth, Menu, Order, CafeOrder)
├── ui/
│   ├── auth/           # Login, email verification screens + LoginViewModel
│   ├── cafeteria/      # Cafeteria selection screen
│   ├── menu/           # Menu & Cart screens + ViewModels
│   ├── order/          # Order status & history screens + ViewModels
│   ├── cafe/           # Cafe staff queue screen + CafeQueueViewModel
│   ├── profile/        # Profile & Change Password dialog
│   ├── suggestion/     # Suggestion box screen
│   ├── navigation/     # QueuelessNavGraph.kt (Zero white-flash transitions)
│   └── theme/          # Color system, typography hierarchy, QueuelessTheme
├── AppContainer.kt     # Manual DI root
└── MainActivity.kt
```

---

## 🔥 Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Authentication | Firebase Authentication (Email/Password) |
| Database | Cloud Firestore (Real-time listeners) |
| Navigation | Jetpack Navigation Compose |
| Concurrency | Kotlin Coroutines + StateFlow / SharedFlow |
| Dependency Injection | Manual (`AppContainer`) |
| Build Tooling | Gradle 8.14.5, JDK 21, compileSdk 36 |

---

## 🗄 Firestore Schema

```
cafeterias/{cafeteriaId}
  name: String
  isOpen: Boolean
  staffUids: List<String>

  menuItems/{itemId}
    name: String
    price: Double
    category: String
    isAvailable: Boolean
    prepTimeMinutes: Int

orders/{orderId}
  cafeteriaId: String
  studentUid: String
  studentName: String
  studentRollNumber: String
  items: List<OrderItem>
    itemId, name, priceAtOrder, quantity, isAvailable
  totalAmount: Double
  status: OrderStatus
  orderNumber: Int          # 4-digit human-readable alias (#3847)
  claimedBy: String
  claimedByName: String
  placedAt: Long
  claimedAt: Long
  readyAt: Long
  collectedAt: Long

users/{uid}
  email: String
  name: String
  rollNumber: String
  role: String              # STUDENT | CAFE_STAFF
  noShowCount: Int
  assignedCafeteriaId: String

suggestions/{suggestionId}
  studentUid: String
  studentName: String
  message: String
  submittedAt: Long
```

---

## ⚙️ Order State Machine

```
PLACED → ACCEPTED               (staff, all items available)
PLACED → AWAITING_CONFIRMATION  (staff, some items unavailable)
PLACED → REJECTED               (staff, all items unavailable)
PLACED → CANCELLED              (student)

AWAITING_CONFIRMATION → ACCEPTED   (student confirms reduced order)
AWAITING_CONFIRMATION → CANCELLED  (student rejects modification)

ACCEPTED → PREPARING → READY → COLLECTED  (staff)
READY → EXPIRED                 (staff, student no-show)
```

---

## 🔐 Security & Integrity

### Firestore Security Rules
All reads and writes are validated server-side:
- Students can only read their own orders.
- Students can only create orders where `studentUid == auth.uid`.
- Only `CAFE_STAFF` role can mutate order statuses.
- Cafeteria menus are read-only from the app (writable via Console).
- Suggestions are write-only for students, read-only for staff.

### Identity Verification
1. Email must match `@iiitl.ac.in` domain — enforced in `AuthRepository` before API invocation.
2. Firebase sends an inbox verification link — unverified users are blocked at `EmailVerificationScreen`.
3. `@iiitl.ac.in` is a university-controlled domain, preventing unauthorized registrations.

### Concurrency & Race Condition Prevention
Order claiming uses a Firestore **transaction** (atomic read-check-write). Two staff members attempting to claim the same order simultaneously will result in one success and one `"Order already claimed"` failure — guaranteeing zero duplicate claims.

---

## 🎨 Design System

Designed around a locked Material 3 dark color palette tailored for campus canteens. See full design document at [`docs/Design.md`](docs/Design.md).

| Token | Hex Value | Role & Purpose |
|-------|-----------|----------------|
| Background | `#121212` | High-contrast dark background |
| Surface | `#1E1E1E` | Card containers & dialog surfaces |
| Primary Accent | `#2E7D32` | Canteen green (primary buttons, FAB, headers) |
| Primary Text | `#FFFFFF` | Crisp white typography |
| Secondary Text | `#B0B0B0` | Muted grey metadata & timestamps |
| Success | `#4CAF50` | Vibrant green for `READY` / `COLLECTED` states |
| Error | `#E53935` | Red for `REJECTED` states & destructive actions |
| Warning | `#FFB300` | Amber for `PLACED` & `AWAITING` states |

---

## 🚀 Running Locally

### Prerequisites
- Android Studio Hedgehog or later
- JDK 21
- Android device or emulator (API 26+)
- Firebase project with Auth and Cloud Firestore enabled

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/aarybuilds/Queueless.git
   ```
2. Open the project in Android Studio.
3. Place your `google-services.json` inside the `app/` directory (gitignored for security).
4. Enable Email/Password authentication in Firebase Console.
5. Create a Cloud Firestore database in `asia-south1`.
6. Build and run on device.

### Staff Account Setup
1. Firebase Console → Authentication → Create user.
2. Firestore → `users/{uid}` → set `role: "CAFE_STAFF"` and `assignedCafeteriaId: "nescafe"`.

---

## 📐 Key Engineering Decisions

### Why `callbackFlow` for Firestore Listeners?
Firestore snapshot listeners operate via callbacks. `callbackFlow` bridges them to Kotlin Flow safely — the `awaitClose` block automatically unregisters the listener when flow collection cancels, preventing memory leaks.

### Why `Result<T>` Instead of Call-Site `try/catch`?
`Result<T>` forces call sites to handle both success and failure explicitly. It eliminates silent exception swallowing and ensures errors surface cleanly to UI state.

### Why Denormalize `studentName` into Orders?
Firestore does not support server-side joins. Storing `studentName` directly on the order document avoids secondary reads per order item in the staff queue.

### Why Timestamp-Based `orderNumber`?
`(System.currentTimeMillis() % 9000 + 1000).toInt()` generates a 4-digit human-readable alias (e.g. `#3847`) for verbal canteen calls. The document UUID remains the primary key.

### Why Domain Validation in `AuthRepository`?
The `@iiitl.ac.in` restriction is a domain invariant. Placing validation inside `AuthRepository` ensures all entry points enforce identity restrictions regardless of UI changes.

---

## 🔮 Future Scope

- **Push Notifications (FCM)** — instant alerts when an order transitions to `READY`.
- **Google Sign-In** — seamless authentication tied to IIIT Lucknow Google Workspace accounts.
- **Payment Gateway Integration** — online pre-payment to streamline instant collection.
- **Analytics & Peak Hour Prediction** — canteen load monitoring for staff.

---

## 👤 Author

**Aary Garge** — [@aarybuilds](https://github.com/aarybuilds)  
B.Tech Computer Science & Business, IIIT Lucknow  
Built for OverEngineered App Wing Selection, August 2026

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
