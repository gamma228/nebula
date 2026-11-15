# Nebula Desktop Audit (Milestone D1)

**Audit Date:** 2026-11-01  
**Target:** Windows Desktop Client (`:desktop` module on Compose Multiplatform / JVM)  
**Baseline Version:** v1.2.1  
**Operating Environment:** Windows 11 x64, OpenJDK 17.0.9 (Temurin)

---

## 1. What Currently Exists

### Architecture & Shared Core (`:core`)
- Multiplatform data models: `ProxyProfile`, `TlsConfig`, `TransportConfig`, `RoutingRule`, `TrafficStats`, `ConnectionState`.
- Parsers: `LinkParser` (VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC), `ClashYamlParser`, `SubscriptionDecoder`, `SubscriptionFetcher`.
- Config Generation: `SingBoxConfigBuilder` generating valid sing-box JSON with TUN inbound (`wintun`), Mixed inbound (`127.0.0.1:2080`), DNS detours, FakeIP, and Clash API.
- Desktop Storage: `DesktopStorageRepository` encrypting sensitive profile credentials and subscription URLs at rest via Windows DPAPI (`Crypt32Util.cryptProtectData`).
- Windows System Integration:
  - `WindowsSystemProxy.kt`: JNA registry integration for WinINET system proxy with automatic backup to `system_proxy_state.json`.
  - `WindowsCoreController.kt`: Process supervisor launching pinned official `sing-box.exe` child process, monitoring output streams into a circular log buffer.
  - `SingleInstanceGuard.kt`: Local socket mutex preventing duplicate windows and focusing existing window on launch.
  - `DesktopTray.kt`: System tray icon dynamically colored by connection status with context menu.

### UI Screens (`:desktop/src/main/kotlin/app/nebula/vpn/desktop/ui/`)
- `Sidebar.kt`: Left navigation rail with tabs: Home, Servers, Routing, Tools, Settings, and connection status indicator.
- `HomeScreen.kt`: Connect/Disconnect button with glowing pulse animation, active node badge, live upload/download speed counters, total session traffic, and TUN vs System Proxy mode switch.
- `ServersScreen.kt`: Subscription cards, server search filter, concurrent latency ping testing, add link/subscription dialogs, node deletion, and share link export.
- `RoutingScreen.kt`: Custom rules table, priority indicators, rule creation dialog with Windows Running Process Picker (`ProcessHelper`).
- `ToolsScreen.kt`: Connection Doctor diagnostics runner and live core logs viewer with severity filtering and copy diagnostic report button.
- `SettingsScreen.kt`: Language switch (5 languages), theme selector, default connection mode, autostart with Windows toggle, minimize to system tray on close toggle, Kill Switch toggle, MTU, and custom DoH URL.

---

## 2. Comparison with Android App & Desktop Gaps

| Feature / Capability | Android App | Existing Desktop | Gap / Missing on Desktop |
| :--- | :--- | :--- | :--- |
| **Color Customization** | OLED Crimson, Cyber Violet, Monochrome presets + full dynamic color picker | 3 static theme presets (`Default`, `Emerald`, `Violet`) | **Missing:** Arbitrary seed color picker (HSV wheel/Hex), HCT tonal palette generation, automatic contrast guard (WCAG AA). |
| **Wallpaper & Background** | Custom image picker, blur slider, opacity slider, system wallpaper extraction | Static dark background color | **Missing:** Custom image upload (PNG/JPG/WebP), blur & dim controls, downscaled bitmap caching, glass card backdrop style. |
| **Surface Styles** | Solid elevated cards | Solid elevated cards | **Missing:** Glassmorphic translucent cards (`alpha 0.72`), Windows 11 Mica/Acrylic support with Win 10 fallback. |
| **Window Frame** | Android Edge-to-Edge with system insets | Default OS window frame | **Missing:** Theme-integrated custom title bar with drag, snap layouts, min/max/close, and Mini-Mode window. |
| **Theme Packs** | In-app theme creator | Fixed hardcoded themes | **Missing:** Export/import `.nebulatheme` bundles, in-app Theme Gallery with live preview. |
| **Subscriptions** | Multi-subscription, auto-update interval, quota headers | Basic subscription list | **Missing:** Traffic used/total quota bar, expiration countdown, header parsing, custom User-Agent, drag & drop link import, QR scanning from screen. |
| **Nodes** | Latency badge, search, duplicate, delete | Basic list & latency ping | **Missing:** Sparkline latency history, protocol/country filters, bulk actions, node editor dialog, urltest auto-select group. |
| **Routing & Rules** | Apps, domain, IP, presets, rule sets, test simulator | Process name & path, basic domain | **Missing:** Geosite/geoip rule sets with auto-update, drag-to-reorder rules, interactive rule test tool ("simulate packet"). |
| **Active Connections** | Connection Doctor only | Speed counters only | **Missing:** Live active connections table (process, remote host, rule matched, data transferred, kill connection action). |
| **Traffic Graphs** | Session total | Session total | **Missing:** Real-time animated traffic chart (live speed & historical daily breakdown). |
| **Core Management** | Embedded libbox.aar | Pinned `sing-box.exe` | **Missing:** In-app core updater from official GitHub releases with SHA256 verification and rollback. |
| **Installer** | Single signed APK | WiX MSI / EXE | **Missing:** Premium Inno Setup installer, Desktop shortcut creation task, Start Menu entry, deep-link protocol associations (`vless://`), clean uninstaller. |

---

## 3. Known Weak Spots & Technical Debt
1. **Memory Footprint**:
   - Desktop JVM currently runs with default heap bounds, consuming ~172 MB working set and ~249 MB private memory on idle.
   - Can be significantly reduced with `-XX:+UseG1GC` / `-XX:+UseZGC`, memory tuning flags, trimmed JRE runtime, and unbinding heavy textures when minimized.
2. **Missing Desktop Shortcut**:
   - Previous WiX installer did not create a desktop shortcut icon, confusing users who expected an icon on their desktop.
3. **No Dynamic Theme Contrast Validation**:
   - Hardcoded color values do not dynamically recalculate text contrast if a user chooses an extremely light or dark custom seed.
4. **Window Resizing and Title Bar**:
   - Default OS window title bar breaks visual immersion of dark / AMOLED themes.
5. **No Screen QR Code Scanning**:
   - Mobile has CameraX QR scanner; desktop needs a screen screenshot area QR decoder for 1-tap import of QR codes displayed on web pages or mobile screens.

---

## 4. Baseline Performance Measurements

Recorded via `tools/bench/measure_perf.ps1` on 2026-11-01 23:39:
- **Executable Size**: 530 KB launcher (`desktop/build/compose/binaries/main/app/Nebula/Nebula.exe`)
- **Installed Directory Size**: 234.47 MB
- **Idle UI Memory (Active Window)**: Working Set = 172.48 MB, Private = 249.04 MB
- **Idle CPU Usage**: ~0.0% when idle; spikes during unthrottled recomposition.
- **Cold Startup Time to Frame**: ~2.3 seconds.
