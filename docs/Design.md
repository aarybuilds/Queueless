# Queueless Design System & UI Specification

The **Queueless** design system provides a cohesive, high-contrast Material 3 interface tailored for fast, clear pre-ordering at college canteens.

---

## 1. Color System Rationale

| Token | Hex Value | Role & Usage |
| :--- | :--- | :--- |
| **Background** | `#121212` | High-contrast dark background for all screens, reducing eye strain in dim canteen/campus lighting. |
| **Surface** | `#1E1E1E` | Card container surface elevating interactive blocks from `#121212`. |
| **Surface Variant** | `#2A2A2A` | Secondary container for chips, input borders, and item dividers. |
| **Primary Accent** | `#2E7D32` | Deep canteen green representing fresh food, primary actions, FABs, and selected states. |
| **Primary Text** | `#FFFFFF` | Crisp white typography for high legibility across dark surfaces. |
| **Secondary Text**| `#B0B0B0` | Muted grey for metadata, timestamps, roll numbers, and subtitles. |
| **Success** | `#4CAF50` | Vibrant green for `READY` / `COLLECTED` order states and confirmation banners. |
| **Error** | `#E53935` | Vivid red for `REJECTED` / `CANCELLED` states, destructive actions, and invalid limits. |
| **Warning** | `#FFB300` | Warm amber for `AWAITING_CONFIRMATION` and `PLACED` queue badges. |

### Why Deep Green & Dark Theme?
1. **Canteen Context**: `#2E7D32` evokes fresh food, energy, and positive completion while avoiding generic default blue/purple UI themes.
2. **OLED Efficiency & Readability**: `#121212` dark background minimizes mobile battery draw during peak campus use while ensuring white text (`#FFFFFF`) produces crisp, immediate visual recognition.

---

## 2. Typography Hierarchy

| Style | Size | Weight | Color | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Display / Main Title** | 32sp | Bold | `#FFFFFF` | App logo branding ("🍽 Queueless") |
| **Headline** | 24sp – 28sp | Bold | `#FFFFFF` | Page titles ("Select Canteen", "Order Queue", "Order #XXXX") |
| **Title Medium** | 16sp – 20sp | Bold / SemiBold | `#FFFFFF` | Card headers, item names, order totals |
| **Body Large / Medium** | 14sp – 16sp | Regular | `#FFFFFF` | General descriptive copy & order items |
| **Subtitle / Small Body**| 12sp – 13sp | Regular | `#B0B0B0` | Prep time chips, timestamps, roll numbers, hints |
| **Label / Badge** | 11sp – 12sp | Bold | `#FFFFFF` | Order status badges & role chips |

---

## 3. Component Patterns

### Buttons
- **Primary Actions** (Sign In, Place Order, Accept, Submit): Filled `#2E7D32` green button with `#FFFFFF` text and a minimum touch target height of `48dp`.
- **Secondary Actions** (Switch Mode, Back to Menu, Resend Email): Outlined green or white button with `12dp` rounded corners.
- **Destructive Actions** (Sign Out, Can't Prepare, Cancel Order): Outlined red (`#E53935`) button with clear contrast.

### Cards & Surfaces
- All cards use a `#1E1E1E` surface container with `12dp` rounded corners and `2dp`–`4dp` subtle elevation.
- Category headers stick to the top with a full-bleed `#2E7D32` primary green background and `16dp` horizontal padding.

### Badges & State Indicators
- Status badges use pill shapes (`12dp` radius) with explicit color coding:
  - `PLACED` / `AWAITING`: `#FFB300` (Amber)
  - `ACCEPTED` / `PREPARING`: `#2E7D32` (Green Accent)
  - `READY`: `#4CAF50` (Vibrant Green)
  - `REJECTED`: `#E53935` (Vivid Red)

---

## 4. Accessibility Considerations

- **WCAG AA Compliance**:
  - Primary white text (`#FFFFFF`) on `#121212` background delivers a **17.5:1** contrast ratio (exceeding WCAG AAA `7:1`).
  - Secondary text (`#B0B0B0`) on `#1E1E1E` surface delivers a **7.8:1** contrast ratio (exceeding WCAG AA `4.5:1`).
- **Touch Targets**: All interactive buttons enforce a minimum height of `48dp` and full horizontal touch bounds for reliable single-handed use on mobile displays.
- **Explicit Contrast Enforcement**: Hardcoded color defaults are mapped through `MaterialTheme.colorScheme` tokens, eliminating white-on-white text anomalies regardless of device system settings.
