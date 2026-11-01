# Windows Desktop Client Architecture & Implementation Plan

## 1. Executive Summary & Repository Analysis

The Nebula project currently consists of a single Android application module (`:app`), targeting Android 8.0+ (API 26–35) with sing-box v1.14.4 integrated via gomobile AAR (`libbox.aar`).
The desktop client must achieve **100% functional and protocol parity** with the Android application on Windows 10/11 (x64 / ARM64):
- Unified share-link parsing (VLESS Reality/gRPC/WS/xhttp, VMess, Trojan, Shadowsocks, Hysteria2, TUIC v5).
- Single-outbound and routing-rules generation via `SingBoxConfigBuilder`.
- Subscription fetching and Base64/YAML decoding.
- Full Connection Doctor diagnostics (pre-checks, real HTTP 204 tunnel probe, TLS/DNS fault classification).
- Universal routing without forced presets or restrictions.
- Identical OLED dark and custom theming system, visual hierarchy, and 5-language localization (`en`, `ru`, `uk`, `zh-CN`, `de`).

---

## 2. Platform Audit: Portable vs. Android-Specific

| Component | Current Location | Classification | Desktop Strategy |
| :--- | :--- | :--- | :--- |
| **Data Models** (`ProxyProfile`, `RoutingRule`, `ConnectionState`, etc.) | `app/src/main/.../core/model/` | **Portable** | Move to `:core` shared module |
| **Parsers & Decoders** (`LinkParser`, `ClashYamlParser`, `SubscriptionDecoder`, `SmartRuleParser`) | `app/src/main/.../core/parser/` | **Portable** | Move to `:core` shared module |
| **Config Builder** (`SingBoxConfigBuilder`) | `app/src/main/.../core/builder/` | **Portable** | Move to `:core` shared module |
| **Diagnostics Logic** (`LatencyTester`, exception classification, route checks) | `app/src/main/.../core/proxy/` & `ui/doctor/` | **Portable** | Move pure logic to `:core` |
| **VPN Service** (`ProxyVpnService`, `VpnTileService`, `BootReceiver`) | `app/src/main/.../vpn/` | **Android-Specific** | Keep in `:app`. Desktop uses `WindowsCoreController` managing `sing-box.exe` child process + Wintun / WinINET system proxy |
| **Mobile Core Adapter** (`LibboxCore`, gomobile AAR JNI) | `app/src/main/.../core/proxy/` | **Android-Specific** | Keep in `:app`. Desktop uses CLI process controller |
| **Storage & Keystore** (Room DB, DataStore, Android Keystore `SecretBox`) | `app/src/main/.../data/` | **Android-Specific** | Keep in `:app`. Desktop uses JSON / SQLite in `%APPDATA%\Nebula\` encrypted with Windows DPAPI (via JNA) |
| **UI Framework** (Jetpack Compose Android, Activity, Hilt) | `app/src/main/.../ui/` | **Android-Specific** | Keep in `:app`. Desktop uses Compose Multiplatform for Desktop (JVM) in `:desktop` module |
| **Theme & Color Tokens** (`NebulaColors`, typography, spacing) | `app/src/main/.../ui/theme/` | **Portable Tokens** | Shared design system tokens |

---

## 3. Target Module Architecture

```
[Nebula Project Root]
 ├── :core                <-- Pure Kotlin JVM / Multiplatform library (Zero Android dependencies)
 │    ├── model           (ProxyProfile, RoutingRule, ConnectionState, TrafficStats, ThemeConfig)
 │    ├── parser          (LinkParser, ClashYamlParser, SubscriptionDecoder, SmartRuleParser)
 │    ├── builder         (SingBoxConfigBuilder)
 │    ├── proxy           (LatencyTester, PingTester, OptimizationFlags)
 │    ├── doctor          (DiagnosticCheckItem, DiagnosticVerdict, DoctorLogicCore)
 │    └── contract        (CoreController, StorageRepository, SystemIntegration, TrafficMonitor)
 │
 ├── :app                 <-- Existing Android application (Keeps 100% existing functionality)
 │    ├── Depends on: project(":core")
 │    ├── Room DB, DataStore, Android KeyStore SecretBox
 │    ├── LibboxCore (libbox.aar gomobile TUN passing)
 │    └── Jetpack Compose Android UI + Hilt DI
 │
 └── :desktop             <-- Windows Desktop Application (Compose Multiplatform JVM)
      ├── Depends on: project(":core")
      ├── Windows child process manager (sing-box.exe amd64 + wintun.dll)
      ├── Windows DPAPI encryption via JNA
      ├── WinINET System Proxy & Named Pipe TUN Helper Service
      ├── Desktop System Tray, Notifications & Single Instance Guard
      └── Compose Multiplatform Desktop UI (Matching Android Design System)
```

---

## 4. Desktop Technology Stack Justification

* **Language & Runtime:** Kotlin 2.0.21 on JVM 17.
* **UI Framework:** Compose Multiplatform for Desktop (`org.jetbrains.compose` 1.7.0).
  * **Why:** Reuses 100% of Compose state patterns (`StateFlow`, `mutableStateOf`), Material 3 styling tokens, color schemes, animations, and typography from the Android app without rewriting the UI from scratch in C++ or C#.
* **Core Engine:** Official `sing-box.exe` Windows amd64 child process + `wintun.dll`.
  * **Why:** Sing-box on Windows runs natively with Wintun driver and handles auto-routing, strict routing, and DNS hijacking with kernel-grade throughput.
* **Security & Storage:** Windows Data Protection API (DPAPI) via JNA (`CryptProtectData` / `CryptUnprotectData`).
  * **Why:** Zero master passwords needed; keys and credentials are cryptographically protected by the user's Windows login session.

---

## 5. Phased Milestones (W1 – W6)

1. **Milestone W1:** Extraction of `:core` module. Transfer pure models, parsers, and config builder. Ensure Android build and 62 JVM unit tests remain 100% green. Establish desktop repository and storage interfaces.
2. **Milestone W2:** Sing-box child process controller, Wintun TUN mode, unprivileged WinINET system proxy mode, named pipe / elevated helper, safe state restoration upon crash.
3. **Milestone W3:** Compose Multiplatform desktop UI with left navigation rail, system tray, single instance guard, real-time stats, and custom themes.
4. **Milestone W4:** Desktop Connection Doctor with Windows-specific checks (firewall, wintun, helper service, clock skew).
5. **Milestone W5:** Performance, resource minimization, network change resilience, and background refresh safety.
6. **Milestone W6:** Packaging via `jpackage` (bundled JRE installer/portable zip), code signing hooks, and verification matrix.
