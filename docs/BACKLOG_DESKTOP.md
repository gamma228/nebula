# Nebula Desktop Prioritized Backlog (Milestone D1)

**Status:** Active  
**Milestones Scope:** D2 to D7

---

## Milestone D2: Customization Engine
- [ ] **D2.1: Dynamic Palette Generator**:
  - Implement 16 curated seed colors with semantic names (Cyber Emerald, Violet Neon, Crimson OLED, Azure Sky, Amber Flame, Mint Glow, Rose Gold, Solar Flare, Arctic Ice, Cobalt Blue, Royal Amethyst, Sunset Coral, Emerald Forest, Electric Indigo, Slate Steel, Pitch Minimal).
  - Implement custom color picker with HSV wheel / square, Hue slider, Hex input, RGB sliders.
  - Implement HCT / tonal palette generator for complete Material 3 `ColorScheme` (Light, Dark, AMOLED).
  - Implement automated WCAG AA contrast guard adjusting text/background contrast.
- [ ] **D2.2: Wallpaper Engine**:
  - Image file picker (PNG, JPG, WebP), paste, drag-and-drop onto window.
  - Downscaled bitmap decoder caching to disk with automatic texture release on window minimize/hide.
  - Controls: blur slider (0..25 dp), dim/scrim slider (0..100%), opacity, fit mode (Fill, Fit, Center).
  - Adaptive luminance scrim maintaining WCAG AA readability over any wallpaper.
- [ ] **D2.3: Surface Styles & Custom Window Frame**:
  - Styles: Solid vs Glass (`alpha 0.72` translucent cards over wallpaper) vs Mica.
  - Custom integrated Title Bar (drag window, snap layout, minimize, maximize/restore, close).
  - Mini Mode window toggle (compact floating status widget).
- [ ] **D2.4: Theme Packs & Gallery**:
  - `.nebulatheme` JSON export/import with corruption fallback.
  - In-app Theme Gallery with 8 built-in themes.

---

## Milestone D3: More Features
- [ ] **D3.1: Subscriptions Deep Integration**:
  - Multiple subscriptions management, userinfo quota header parsing (used, total, expiry).
  - Per-subscription auto-update interval, custom User-Agent, safe refresh (preserve nodes on network error).
  - Desktop screen QR-code scanner (crop screen area to decode proxy links).
- [ ] **D3.2: Advanced Node Management**:
  - Sparkline latency history, protocol & country filters, search, bulk delete/favorite.
  - Manual node editor dialog with validation for VLESS, VMess, Trojan, SS, Hy2, TUIC.
  - Lowest-latency auto-select (`urltest`) group support.
- [ ] **D3.3: Routing & Rules Visualizer**:
  - Custom rules editor with drag-to-reorder, domain, IP CIDR, geosite, geoip, process name and process path picker.
  - Interactive rule simulator ("Test Packet / Rule Matcher").
  - Sing-box DNS settings editor (detours, DoH, strategy, FakeIP).
- [ ] **D3.4: Live Active Connections & Traffic Graph**:
  - Real-time active connections table via Clash API with process name, destination, bytes, matched rule, and "Kill Connection" action.
  - Smooth animated real-time throughput chart (live speed & session history).
- [ ] **D3.5: Core Management & Windows System Integration**:
  - Core version display, official in-app sing-box core updater with SHA-256 verification and rollback.
  - Global hotkeys, Windows autostart, deep-link protocol associations (`vless://`, etc.).
  - Encrypted backup/restore (password-based KDF).

---

## Milestone D4: PC Optimization
- [ ] **D4.1: Render & Polling Throttle**:
  - Pause Skia animations and suspend background polling when window is minimized or hidden.
  - Ring buffer logs with hard 2048-line memory cap.
  - Virtualized lazy lists for nodes and connections.
- [ ] **D4.2: JVM & Runtime Tuning**:
  - Configure `-XX:+UseG1GC`, string deduplication, optimized heap flags.
  - Fast cold startup (< 2.0s).
- [ ] **D4.3: Stability & Leak Verification**:
  - 100-cycle node switch, 50-cycle mode toggle, sleep/wake soak testing without memory drift or orphan processes.

---

## Milestone D5: Beautiful Installer with Desktop Shortcut
- [ ] **D5.1: Inno Setup 6 Script & Assets**:
  - Multi-resolution icon (`.ico` containing 16x16 to 256x256).
  - Modern wizard side banner and header branding.
  - Tasks: "Create a desktop shortcut" (checked by default), Start Menu shortcut, Autostart, File/Protocol associations.
  - Localized installer in 5 languages (ru, en, uk, zh, de).
- [ ] **D5.2: Safe Upgrade & Clean Uninstaller**:
  - Running process detection and graceful shutdown.
  - In-place upgrade preserving `%APPDATA%\Nebula\` settings.
  - Clean uninstaller restoring WinINET proxy and optional user data deletion.
- [ ] **D5.3: Portable Distribution & WinGet Manifest**:
  - `Nebula-v<ver>-windows-portable.zip` and winget draft manifest.

---

## Milestone D6: Quality, Security, Accessibility
- [ ] **D6.1: Automated Test Suite**:
  - Unit tests for theme generation, contrast guard, `.nebulatheme` serialization, rules matcher.
  - 5-language completeness test for every string key.
- [ ] **D6.2: Security & Crash Handling**:
  - DPAPI encryption at rest, 127.0.0.1 bound Clash API, random secret tokens.
  - Friendly error dialogues for missing driver, port conflict, or network failure.

---

## Milestone D7: Final Verification & Delivery
- [ ] **D7.1: Full Matrix Execution**:
  - Android build and tests green.
  - Desktop unit tests 100% green.
  - Live Windows tunnel verification with 3 nodes.
  - Screenshot review of installer, themes, and screens.
  - Final performance before/after table.
