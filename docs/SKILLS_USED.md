# Design Skills Installed & Applied

This document records the external design skills installed from GitHub, their exact repository source and commit hash, and the concrete design rules and principles mapped to the Nebula Android UI architecture.

---

## Installed Skills Registry

| Skill Name | Repository | Commit Hash | Core Responsibility |
| :--- | :--- | :--- | :--- |
| **`frontend-design`** | `anthropics/skills` | `dbd4588f9e1033efb41dad4bef2f7947c8993d44` | Bold, intentional aesthetics; non-templated hero treatment; avoiding generic AI tropes. |
| **`ui-ux-pro-max`** | `nextlevelbuilder/ui-ux-pro-max-skill` | `d14efff8ed1faa4187f004f79926f428c8074ec7` | Systematic design tokens; palette roles; typography hierarchy; design system generator. |
| **`sleek-design-mobile-apps`** | `sleekdotdesign/agent-skills` | `fb90438ab2263b7a87cdce1ca7747d371fccb438` | Mobile phone layout structure, density, touch targets, and viewport constraints. |
| **`web-design-guidelines`** | `vercel-labs/agent-skills` | `063bee94c3f4df8453406c830b0a7df0f2860278` | Accessibility compliance (contrast >= 4.5:1, touch target >= 48dp), interaction rules. |
| **`design-taste-frontend`** | `Leonxlnx/taste-skill` | `12af471f627f866411b4bd785b19646d3672784c` | Anti-generic discipline; app screen density; restrained neon/accents; high substance. |
| **`ui-animation`** | `mblode/agent-skills` | `193fa28cba62506457a815141f37b41396b0ad91` | Motion physics, easing curves (150-300ms), reduce-motion honoring, interruptibility. |
| **`interface-auditor` & `uxui-evaluator`** | `uxuiprinciples/agent-skills` | `bc3f52deb45e3f8638d9ad0e82149182a6c0e410` | UX smell detection: avoiding overloaded screens, dead clicks, and uncommunicative states. |

---

## Quoted Rules & Concrete Applications

### 1. `frontend-design`
> *"Spend your boldness in one place. Let one element be the memorable thing, keep everything around it quiet and disciplined, and cut any decoration that does not serve the brief."*
- **Application in Nebula:** The central Hero Reactor / Connection Orb on the Home screen is the primary visual centerpiece. Surrounding telemetry cards and server selectors remain disciplined, clean, and unobtrusive.

### 2. `ui-ux-pro-max`
> *"Dark mode requires elevation to be expressed through lightness layers rather than heavy drop shadows: base background at deepest black, surface containers at elevated slate luminance."*
- **Application in Nebula:** Built strict 3-tier dark elevation tokens: `Deep Black` (#000000) for OLED background, `Slate Grey` (#12161F / #1A202C) for cards, and restrained `Electric Violet` (#7C3AED / #A855F7) and `Crimson Red` (#EF4444 / #DC2626) for state feedback.

### 3. `sleek-design-mobile-apps`
> *"Element hierarchy, flex direction, spacing, sizing, colors, typography, radii... reproduce these as working features, not static mockups."*
- **Application in Nebula:** Applied a strict 4/8/12/16/24/32dp grid system, pinned bottom navigation, sticky segmented headers, and zero hidden primary actions behind long scrolling.

### 4. `web-design-guidelines`
> *"All text must achieve WCAG AA contrast ratio of at least 4.5:1 against its background (3:1 for large text); all interactive touch targets must be at least 48x48dp."*
- **Application in Nebula:** Numerically verified contrast for all 3 themes across 5 locales; all clickable chips, rows, buttons, and switches enforced with minimum 48dp touch bounds.

### 5. `design-taste-frontend`
> *"An app screen is worked in: dashboards, settings, lists with filters... quiet borders, small labels and repeated panels are normal there; gradients, glows, icons in tinted squares and invented vanity numbers are not."*
- **Application in Nebula:** Replaced generic neon washes with crisp 1dp subtle borders (`rgba(255,255,255,0.08)`), high information density in Server and Routing lists, and meaningful telemetry (speed, ping, quota).

### 6. `ui-animation`
> *"Keep routine UI under 300ms... Movement uses transform and opacity only... Tappable controls press at 0ms; disable transitions during theme switches or when reduce-motion is enabled."*
- **Application in Nebula:** Implemented Compose `animateFloatAsState` and `tween(200, easing = FastOutSlowInEasing)` using only graphics layers; built global `LocalReduceMotion` disabling continuous breathing or particle loops when requested.

### 7. `interface-auditor` & `uxui-evaluator`
> *"Smell: Overloaded Screen — more than 7 distinct action areas visible... Smell: Click Cemetery — users must click 3+ times to complete a simple task."*
- **Application in Nebula:** Flattened navigation into 5 direct top-level tabs; 1-tap connect; 1-tap server switch; 1-tap ping cancel; modal bottom sheets with zero nested dialogs.
