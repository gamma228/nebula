# Nebula Desktop UI Guidelines & Design System (Milestone D1)

**Design Language:** Nebula Material 3 Expressive (Unified Android & Desktop)  
**Target Environment:** Windows 10 / 11 Desktop (Compose Multiplatform Desktop, Skia rendering)

---

## 1. Spacing Scale
All layout margins, paddings, and component gaps follow an 8dp / 4dp baseline grid:
- `xxs`: 2 dp (tight borders, badge insets)
- `xs`: 4 dp (badge padding, micro chips)
- `sm`: 8 dp (internal card spacing, row item gap)
- `md`: 12 dp (button content padding, list item horizontal padding)
- `lg`: 16 dp (standard screen margin, card padding, dialog padding)
- `xl`: 24 dp (section separation, hero card padding)
- `xxl`: 32 dp (screen container padding)
- `hero`: 48 dp (home connect button clear area)

---

## 2. Corner Radii Scale
- `None`: 0 dp (full-width header/footer attachments)
- `Small`: 6 dp (chips, small text fields, tooltips)
- `Medium`: 12 dp (cards, list items, action buttons)
- `Large`: 18 dp (hero containers, dialog windows, banners)
- `Full`: 9999 dp (pill badges, circular connect buttons)

---

## 3. Typography Scale
Typography is scaled using clean sans-serif system fonts (`Segoe UI Variable`, `Segoe UI`, `Inter`, `Roboto` fallback) with high rendering clarity:
- **Display Large**: 32 sp, SemiBold, LineHeight 38 sp (Hero status, main speed counters)
- **Title Large**: 20 sp, SemiBold, LineHeight 26 sp (Screen titles, modal headers)
- **Title Medium**: 16 sp, SemiBold, LineHeight 22 sp (Card titles, section headers)
- **Body Large**: 14 sp, Normal, LineHeight 20 sp (Primary list text, form inputs)
- **Body Medium**: 13 sp, Normal, LineHeight 18 sp (Secondary descriptions, table rows)
- **Label Small**: 11 sp, Medium, LineHeight 14 sp (Status badges, ping tags, captions)

---

## 4. Color Roles & Tonal Palette Generation
The UI uses standard Material 3 color roles derived from a single HCT seed color:
- `primary`: Main brand accent (connect button, active tab indicator, key switches).
- `onPrimary`: Text/icon drawn on primary surfaces (ensures >= 4.5:1 contrast).
- `primaryContainer`: Subdued tinted background for active selections.
- `onPrimaryContainer`: High-contrast foreground on primary container.
- `surface`: Background for elevated cards and sidebars.
- `onSurface`: Main body text (high contrast against surface).
- `surfaceVariant`: Secondary neutral card background.
- `onSurfaceVariant`: Secondary helper text and inactive icons.
- `outline`: Subtle 1dp border for cards and inputs (`alpha = 0.15` in dark mode).
- `error`: Warning / failure indicator (red/crimson).

### Modes
1. **Dark Mode**: Deep charcoal dark surfaces (`#121418` surface, `#1E222B` card surface).
2. **AMOLED Mode**: True pitch black background (`#000000`) with high-contrast surfaces (`#111111`) for OLED screens.
3. **Light Mode**: Crisp, clean light surfaces (`#F8F9FA` background, `#FFFFFF` cards) with deep contrast typography.
4. **System Mode**: Dynamically follows Windows 10/11 light or dark system theme.

---

## 5. Contrast Guard & WCAG AA Requirements
- Every text and icon element must pass WCAG AA:
  - **Normal text (< 18 sp)**: >= 4.5:1 contrast ratio against underlying background/card.
  - **Large text (>= 18 sp / 14 sp bold)**: >= 3.0:1 contrast ratio.
- If a custom wallpaper or translucent glass is active, an automatic adaptive scrim (darkening or lightening layer) is applied dynamically based on the measured luminance of the background image.

---

## 6. Window Layout & High-DPI Scaling
- **Default Window Size**: 1000 x 680 dp (Min: 900 x 600 dp).
- **Navigation**: Persistent left Sidebar (width 220 dp) with icon + localized text, collapsing to rail (64 dp) in mini mode.
- **DPI Support**: Native Skia vector rendering crisp at 100%, 125%, 150%, 175%, and 200% Windows display scaling without pixelation.
- **Custom Title Bar**: Integrated directly into window header, including draggable area, theme title, connection status pill, minimize, maximize/restore, and close controls.
