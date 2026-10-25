# Nebula Design System Specification (DESIGN.md)

## 1. Visual Direction & Concept

### The "Quantum Core & Deep Void" Aesthetic
Nebula rejects generic AI-templated UI (cream backgrounds, terracotta accents, floating rounded SaaS cards with mushy drop-shadows, and meaningless all-caps eyebrow labels).

Instead, Nebula embodies a **high-precision, dark-mode technical instrument**:
- **The Living Quantum Orb (Hero Centerpiece):** The central connection reactor on the Home screen is the single bold, memorable signature of the application (*per `frontend-design` rule: "Spend your boldness in one place"*). It breathes calmly when disconnected, contracts and spins energetic orbital rings during the handshake, and glows with rock-solid stability when encrypted.
- **Deep Void Layering:** Depth is achieved not through heavy drop shadows, but through deliberate luminance stepping (*per `ui-ux-pro-max`*):
  - Tier 0 (Canvas): True OLED Pitch Black (`#000000`).
  - Tier 1 (Surface Containers): Matte Slate Grey (`#12161F`).
  - Tier 2 (Elevated / Active): Elevated Slate (`#1A2230`) with a razor-thin 1dp structural border (`rgba(255, 255, 255, 0.08)`).
- **Restrained Neon & High Substance:** Neon accents (`Electric Violet` and `Crimson Red`) are reserved exclusively for active connection states, critical security alerts, and primary interactive feedback.
- **Information Density for Real Work:** Lists (Servers, Rules, Connections) are compact, dense, and instantly scannable without wasted padding (*per `design-taste-frontend`*).

---

## 2. Token Architecture & Roles

### 2.1 The 5-Color Mandatory Palette
| Color Name | Hex Code | Compose Value | Primary Roles |
| :--- | :--- | :--- | :--- |
| **Deep Black** | `#000000` | `Color(0xFF000000)` | OLED background, canvas base, scrim underlays |
| **Slate Grey** | `#12161F` | `Color(0xFF12161F)` | Surface containers, card backgrounds, list items |
| **Pure White** | `#FFFFFF` | `Color(0xFFFFFFFF)` | Primary text, active icons, high-contrast highlights |
| **Electric Violet**| `#8A2BE2` | `Color(0xFF8A2BE2)` | Cyber Violet primary, proxy route action, connected aura |
| **Crimson Red** | `#E60049` | `Color(0xFFE60049)` | OLED Crimson primary, disconnect/block action, error states |

Supporting Semantic Colors:
- **Status Green:** `#00D084` (Connected status, direct route action, ping < 100ms)
- **Status Yellow / Amber:** `#FFB703` (Reconnecting, rule conflict warning, ping 100-300ms)
- **Text Secondary:** `#94A3B8` (Subtitles, metadata, ports, protocols)
- **Border Subtle:** `rgba(255, 255, 255, 0.08)` (Crisp 1dp structural outline)

---

### 2.2 The 3 Mandatory Theme Presets

```
+------------------------------------+----------------------------------+----------------------------------+
| OLED Crimson & Shadow              | Cyber Violet & Ash               | Monochrome Minimalist            |
+------------------------------------+----------------------------------+----------------------------------+
| Background: #000000 (Pure Pitch)   | Background: #0D0E14 (Deep Ash)   | Background: #000000 / #090A0D    |
| Surface: #100C12 (Slate/Crimson)   | Surface: #151221 (Violet Tint)   | Surface: #14171C (Neutral Slate) |
| Primary: #E60049 (Crimson Red)     | Primary: #8A2BE2 (Electric Violet| Primary: #FFFFFF (Pure White)    |
| Accent: #FF3366 (Neon Rose)        | Accent: #00FFFF (Cyan Highlight) | Accent: #718096 (Cool Grey)      |
| Text: Pure White (#FFFFFF)         | Text: Pure White (#FFFFFF)       | Text: Pure White (#FFFFFF)       |
| Accents: Crimson & Violet          | Accents: Violet & Neon Glow      | Accents: Red/Violet ONLY alerts  |
+------------------------------------+----------------------------------+----------------------------------+
```

---

### 2.3 Spacing Scale
Nebula enforces an 8-point typographic and layout grid with 4dp sub-steps:
- `xxs`: **4dp** (Badge padding, icon-text gap)
- `xs`: **8dp** (Dense list item inner padding, chip spacing)
- `sm`: **12dp** (Standard card internal padding on mobile 360dp)
- `md`: **16dp** (Screen margin, standard container padding)
- `lg`: **24dp** (Section spacing, hero vertical separation)
- `xl`: **32dp** (Major screen block transitions)

---

### 2.4 Corner Radii & Shapes
Configurable via `ThemeConfig.buttonShape`:
- **ROUNDED:** Cards 24dp, Buttons 50% (Pill), Chips 50% (Pill).
- **SQUIRCLE:** Cards 16dp, Buttons 14dp, Chips 10dp.
- **SHARP:** Cards 4dp, Buttons 4dp, Chips 2dp (Technical / Industrial).

---

### 2.5 Glassmorphism & Elevation
- **Android 12+ (API 31+):** `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(16f, 16f, TileMode.CLAMP).asComposeRenderEffect() }` with `surface.copy(alpha = 0.70f)`.
- **Legacy Fallback (API 26-30):** High-opacity solid surface overlay `surface.copy(alpha = 0.92f)` with 1dp border, ensuring 100% legibility with zero performance penalty.

---

### 2.6 Motion & Animation Standards (*per `ui-animation`*)
- **Durations:**
  - Button touch feedback / press: **120 ms**
  - Toggle / Selection chip: **180 ms**
  - Card expansion / Sheet entry: **250 ms**
  - State choreography (Connecting -> Connected): **300 ms**
- **Easing:** `FastOutSlowInEasing` / `Cubic(0.22f, 1.0f, 0.36f, 1.0f)`.
- **Accessibility (`reduceMotion`):**
  When `ThemeConfig.reduceMotion = true` or Android system "Remove animations" is active:
  - All continuous pulsing and breathing loops stop immediately.
  - State transitions snap at 0ms.
  - Zero camera shake or parallax effects.

---

### 2.7 Typography Scale
System default font family with fallback to high-legibility sans-serif:
- **Display Large:** 32sp / Line-height 40sp / Weight: Bold
- **Title Large:** 20sp / Line-height 26sp / Weight: SemiBold
- **Title Medium:** 16sp / Line-height 22sp / Weight: Medium
- **Body Large:** 14sp / Line-height 20sp / Weight: Normal
- **Body Medium:** 13sp / Line-height 18sp / Weight: Normal (Dense list rows)
- **Label Small:** 11sp / Line-height 14sp / Weight: SemiBold (Caps avoided; sentence case used)

---

## 3. Skill-to-Decision Mapping

| Design Decision | Driving Skill | Source Rule |
| :--- | :--- | :--- |
| **Palette & Tokens** | `ui-ux-pro-max` | Strict semantic color mapping; 3-tier elevation luminance. |
| **Hero Orb & Anti-Generic Identity** | `frontend-design` | "Spend boldness in one place"; avoid generic AI templates. |
| **Mobile App Ergonomics** | `sleek-design-mobile-apps` | Pinned bottom navigation, sticky headers, 48dp touch targets. |
| **Restraint & Substance** | `design-taste-frontend` | Quiet 1dp borders, high information density, real telemetry. |
| **Motion Physics & Timings** | `ui-animation` | Asymmetric 120-300ms transitions; `reduceMotion` compliance. |
| **Contrast & WCAG AA** | `web-design-guidelines` | Numerical contrast verification (>= 4.5:1 text, >= 3:1 graphical). |
| **UX Flow & Smell Elimination** | `uxui-evaluator` & `interface-auditor` | Zero overloaded screens, zero click cemeteries, explicit error recovery. |
