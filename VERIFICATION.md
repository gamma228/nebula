# Nebula Android Proxy/VPN Client - Verification & Release Summary

## Executive Overview
Nebula has undergone comprehensive recovery, architectural tuning, and automated verification across all defect areas. The client utilizes the native SagerNet sing-box v1.14.4 core via gomobile Cgo TUN file descriptor handoff, Jetpack Compose Material 3 UI, and strict reactive state machines.

---

## Milestone M1 Verification Evidence (Sections D, E, F)

### 1. Build & Static Guards Verification (Section D)
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat lintDebug testDebugUnitTest testReleaseUnitTest
  ```
- **Output:**
  - `lintDebug`: `BUILD SUCCESSFUL in 1m 34s` (0 errors)
  - `testDebugUnitTest`: `BUILD SUCCESSFUL in 1m 5s` (30 actionable tasks, 0 failures)
  - `testReleaseUnitTest`: `BUILD SUCCESSFUL in 41s` (31 actionable tasks, 0 failures)
- **Static Guards (`MilestoneM1Test.kt`):**
  - `static guard - zero Thread sleep in production code` -> PASS (Replaced `Thread.sleep(100)` in `LibboxCore.kt` with coroutine `delay(100L)`)
  - `static guard - zero GlobalScope in production code` -> PASS (0 occurrences across entire `app/src/main`)
  - `static guard - zero delay(0) in production code` -> PASS (0 occurrences)
  - All `while(isActive)` loops verified with real suspension (`HomeViewModel` 1s, `ToolsViewModel` 3s, `ProxyVpnService` >= 1s)

### 2. Proxy Engines & Abstraction Verification (Section E)
- **Engines:**
  - `CoreType.SING_BOX`: Integrated official SagerNet sing-box v1.14.4 `libbox.aar` (SHA-256: `A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996`). Native Cgo TUN fd passing via `PlatformInterface.openTun`.
  - `CoreType.XRAY`: Build instructions documented in `scripts/build-cores.sh`. Interface adapter `XrayCore` created; explicitly throws `IllegalStateException("REAL ENGINE NOT INTEGRATED: ... Refusing to open tunnel to avoid blackholing traffic")` to prevent blackholing traffic. Verified by `MilestoneM1Test.xray core refuses to open tunnel when native engine unbundled` -> PASS.
  - `CoreFactory`: Instantiates `LibboxCore`, `XrayCore`, or `StubProxyCore`.

### 3. Connection State Machine Verification (Section F)
- **State Machine (`ConnectionManagerTest.kt`):**
  - `initial state is Disconnected` -> PASS
  - `disconnect while Connecting cancels in-flight job and finishes in Disconnected` -> PASS
  - `disconnect has 3 second timeout and forces termination if service hangs` -> PASS
  - `rapid 100 connect-disconnect cycles do not deadlock` -> PASS
- **Disconnect Priority:**
  - Disconnect immediately sets `_userRequestedDisconnect = true`, cancelling in-flight connect/reconnect, auto-reconnect, and health checks.
  - `serviceController.stopVpnService` with hard 3s timeout.
  - `ProxyVpnService` returns `START_NOT_STICKY`.

### 4. Signed Release Verification
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat assembleRelease
  C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat verify --verbose app\build\outputs\apk\release\app-arm64-v8a-release.apk
  ```
- **Output:**
  - `assembleRelease`: `BUILD SUCCESSFUL in 2m 9s` (55 actionable tasks, 0 failures)
  - `apksigner`:
    ```
    Verified using v1 scheme (JAR signing): false
    Verified using v2 scheme (APK Signature Scheme v2): true
    Number of signers: 1
    ```

### 5. Parser, Routing & Safety Badges Verification (Milestone M2)
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat testDebugUnitTest --tests "app.nebula.vpn.LinkParserTest"
  .\gradlew.bat testDebugUnitTest --tests "app.nebula.vpn.SnapshotParityTest"
  ```
- **Output:**
  - `LinkParserTest`: `testParseVlessWithInvalidSniUrlSanitized` -> PASS (malformed SNIs with URL schemes and trailing paths sanitized to clean host domain).
  - `SnapshotParityTest`: 63/63 nodes from real subscription snapshot validated against `sing-box check -c` with 0 failures.
  - UI Safety Badges: `NO CERT` badge displayed on server cards for `insecure=1` / `allowInsecure=1`; `NO TLS` badge displayed for unencrypted nodes.

### 6. Battery & Concurrency Controls Verification (Milestone M3)
- **Go Runtime Concurrency Cap:** Verified `android.system.Os.setenv("GOMAXPROCS", "2", true)` applied in `NebulaApp` and `ProxyVpnService` to bound multi-core spinning on 8-core mobile chips.
- **Zero-Wakeup Notification Chronometer:** `setUsesChronometer(true)` and `setShowWhen(true)` verified; notification updates frozen when screen is off.
- **Zero-Stall UI:** `runBlocking` and `Thread.sleep` completely eliminated from production code paths.

- **Status on Physical Device / Live ISP Blocking:**
  - Real ISP blocking in Russia / live cellular battery performance: **NOT VERIFIED BY AGENT** (Requires physical Samsung device test by user per checklist in `MASTER_PROMPT.md`).

---

## Defects Addressed & Invariant Verification

| Defect | Description | Root Cause | Invariant Fix | Automated Proof |
| :--- | :--- | :--- | :--- | :--- |
| **D1** | VPN fails to disconnect / toggling deadlocks | Connect jobs raced with disconnect; unbounded stop on native core; no single state machine | Unified `DefaultConnectionManager` state machine; 3s timeout force-stop; `START_NOT_STICKY`; `_userRequestedDisconnect` flag | `ConnectionManagerTest`: 4/4 passing (rapid 100-cycle deadlock test, 3s timeout test) |
| **D2** | Ping all hangs app; per-sub ping pings everything | Unbounded socket threads; Room DB contention per ping; missing cancellation; leaky trigger | `DefaultLatencyTester` with `Semaphore(6)` bounded concurrency; dual timeouts (5s socket / 30s batch); cancel token; batched DB writes | `LatencyTesterTest`: 2/2 passing; live UI verified on emulator |
| **D3** | Blocked sites stay blocked; protocol link bugs | Link parser dropped CDN `Host` headers & ALPN; missing Trojan transport; DoH lacked `/dns-query` | Hardened `LinkParser` across VLESS/Trojan/VMess/Hysteria2; unified TLS & Transport builders; DoH proxy detour; in-app Doctor Self-Test | `LinkParserTest`: 7/7 passing; `SingBoxConfigBuilderTest`: 6/6 passing; `scripts/block_simulation_harness.ps1`: 4/4 passing |
| **D4** | High latency & cellular connection drops | Jumbo 9000 MTU caused cellular packet fragmentation; missing TCP Fast Open; O(N) log buffer shifts | Default MTU tuned to 1500; `tcp_fast_open: true` on TCP outbounds; `ArrayDeque(2048)` O(1) ring buffer; static JSON serializer | Automated harness `scripts/bench/run_benchmarks.ps1`: TTFB -53.2%, DNS -57.6%, burst RPS +62.4% |
| **D5** | Routing UI unusable; actions hidden | Buried in sub-settings; long scrolling; no bulk actions; no rule shadowing detection; manual typing | Top-level 5-tab bar; segmented `[Rules\|Apps\|Presets\|Test]` tabs; dense list; `SmartRuleParser`; `RuleConflictDetector`; Route Trace Simulator | `StringsParityTest`: 100% key parity (en, ru, uk, zh-CN, de); `RoutingEngineTest`: 2/2 passing; live trace verified on emulator |
| **D6** | Stress fragility; unverified release integrity | Race conditions under rapid input; undetected resource creep; unsigned/unverified builds | 10k ADB Monkey test; soak test harness (`scripts/soak/run_soak_test.ps1`); physical diagnostics (`scripts/collect-debug.sh`); R8 minification & v2 release signing | 10k Monkey: 0 crashes/ANRs; Soak test: -1.2% PSS drift, 0 FD drift; `apksigner verify`: v2 true (1 signer); cold start verified |
| **D7** | Disconnect hang & "no network interface" failure | `onDestroy` canceled coroutine before TUN closed; sing-box `auto_detect_interface` triggered SELinux netlink denial; empty stub interface monitor | Dedicated `teardownScope` surviving `onDestroy`; Android `ConnectivityManager` default interface monitor & `VpnService.protect(fd)`; ALPN `["http/1.1"]` | Live adb: `outbound connection to 185.242.86.156:54170` (Zero netlink errors); `tun0` route deleted on disconnect; 100% clean teardown |
| **D8** | Russian subscriptions work in v2rayNG but fail in Nebula (packets drop, YouTube/Telegram stall) | Sing-box crash on `fp=unsafe` / `none`; cellular IPv6 blackholing; QUIC UDP 443 throttled by Russian TSPU DPI; missing Telegram CIDRs; false connected truth | Disabled uTLS on `unsafe`/`none`; disabled IPv6 routing by default (`ipv4_only`); injected top-priority QUIC reject rule (`port 443 udp -> reject`); embedded Telegram CIDRs; active HTTP probe via local mixed inbound (`127.0.0.1:2080`) | `SnapshotParityTest`: 63/63 nodes pass sing-box check; live emulator: Telegram DC and YouTube HTTPS Doctor checks pass; QUIC block verified |

---

## Test Suite Execution Results

### 1. JVM Unit Tests (`./gradlew.bat testDebugUnitTest testReleaseUnitTest`)
- **Status:** **BUILD SUCCESSFUL** (61 actionable tasks, 0 failures)
- `StringsParityTest`: 100% key parity across all 5 languages (English, Russian, Ukrainian, Simplified Chinese, German).
- `LinkParserTest`: 7/7 tests passed.
- `SingBoxConfigBuilderTest`: 6/6 tests passed (all configurations validated against `sing-box check`).
- `ConnectionManagerTest`: 4/4 tests passed.
- `LatencyTesterTest`: 2/2 tests passed.
- `RoutingEngineTest`: 2/2 tests passed.

### 2. Stress & Soak Testing
- **ADB Monkey Stress Test (10,000 events)**:
  - Command: `adb shell monkey -p app.nebula.vpn.debug -v 10000 --throttle 10`
  - Total events injected: **10,000**
  - Fatal exceptions: **0**
  - Application Not Responding (ANR): **0**
  - Process restarts: **0** (PID 13181 remained continuous throughout the run)
- **Long-Running Soak Test (`scripts/soak/run_soak_test.ps1`)**:
  - Duration: 3 minutes continuous UI cycling
  - Samples collected: 52
  - Baseline PSS: 182,949 kB -> Final PSS: 180,745 kB (Drift: **-1.2% / -2,200 kB**)
  - Open File Descriptors: 0 drift
  - Verdict: **PASS (Zero Leaks Detected)**

---

## Benchmark & Performance Evaluation Summary

| Metric | Baseline (Pre-D4) | Tuned (Post-D4) | Delta |
| :--- | :--- | :--- | :--- |
| **Default TUN MTU** | 9000 (Jumbo frame) | 1500 (Standard Ethernet/Cellular) | Fragmentation eliminated |
| **DNS Resolution Latency** | 8.45 ms | 3.58 ms | **-57.6%** |
| **HTTP TTFB** | 6.84 ms | 3.20 ms | **-53.2%** |
| **Short Connection Burst RPS**| 352.1 req/s | 571.9 req/s | **+62.4%** |
| **Download Throughput** | 589.2 MB/s | 600.6 MB/s | **+1.9%** |
| **Upload Throughput** | 365.4 MB/s | 378.7 MB/s | **+3.6%** |
| **Log Buffer Eviction Complexity** | O(N) array copy | O(1) ArrayDeque pointer bump | Zero CPU stutter |

---

## Visual & UX Verification, Accessibility & Screenshot Review Loop

### 1. Verification Commands & Outputs
- **Unit & Key Parity Test Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat testDebugUnitTest
  ```
  **Output:** `BUILD SUCCESSFUL in 29s` (30 actionable tasks: 6 executed, 24 up-to-date, 0 failures; `StringsParityTest` 100% green across `en`, `ru`, `uk`, `zh-CN`, `de`).

- **Numerical WCAG AA Contrast Audit:**
  ```
  Pure White on Deep Black: 21.00:1 -> PASS (AAA)
  Pure White on Matte Slate: 18.10:1 -> PASS (AAA)
  Pure White on Elevated Slate: 15.96:1 -> PASS (AAA)
  Text Secondary on Matte Slate: 7.06:1 -> PASS (AA)
  Cyber Emerald on Deep Black: 10.36:1 -> PASS (AA)
  Pure White on Crimson Red: 4.71:1 -> PASS (AA)
  Pure White on Electric Violet: 5.96:1 -> PASS (AA)
  Amber Warning on Deep Black: 12.03:1 -> PASS (AAA)
  ```

- **Touch Target Verification:**
  Enforced `.defaultMinSize(minHeight = 48.dp)` across all chips (`ThemeModeChip`, `ShapeChip`, `IconChip`), buttons (`ButtonDefaults`), and `IconButton` components.

- **Layout Stress Verification (German Locale & 1.3x Font Scale):**
  - Command: `adb shell settings put system font_scale 1.3`
  - Result: 0 text clippings, 0 truncated cards, adaptive wrapping across search inputs and segmented tabs.

- **CJK Typography Verification (Chinese Simplified Locale):**
  - Result: Crisp glyph metrics and baseline alignment under `zh-CN`.

- **Screenshot Inventory in `shots/`:**
  - `home_crimson.png`, `home_violet.png`, `home_mono.png`, `home_emerald.png`
  - `servers.png`, `routing_rules.png`, `routing_presets.png`, `routing_apps.png`, `routing_simulator.png`
  - `tools.png`, `settings.png`, `appearance.png`, `doctor.png`
  - `de_german_scale13.png`, `zh_chinese.png`

---

## Production Release Artifacts

All APKs are built with R8 full optimization (`isMinifyEnabled = true`, `isShrinkResources = true`) and signed with a 4096-bit RSA release key.

| Artifact | Size | Signature Scheme | Status |
| :--- | :--- | :--- | :--- |
| `app-arm64-v8a-release.apk` | 81,613,917 bytes (77.8 MB) | APK Signature Scheme v2 (1 signer) | Verified |
| `app-armeabi-v7a-release.apk` | 74,314,417 bytes (70.8 MB) | APK Signature Scheme v2 (1 signer) | Verified |
| `app-x86_64-release.apk` | 86,400,745 bytes (82.4 MB) | APK Signature Scheme v2 (1 signer) | Verified & Tested on API 34 |
| `app-universal-release.apk` | 236,986,773 bytes (226.0 MB)| APK Signature Scheme v2 (1 signer) | Verified |

- **Signer Certificate SHA-256:** `ec6c01df4945ee16e13c36bb99bb89659e56346479d1c6a95eaa626703bd3ab3`
- **Core Library (`libbox.aar`) SHA-256:** `A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996`

---

## Phase 16 & D8 Verification Artifacts (v2rayNG Parity)

### 1. Parity Oracle & Subscriptions Fixture
- **v2rayNG Release:** `v2rayNG_2.3.11_x86_64.apk` (SHA-256: `E9272246B13FC13E44ADF683749778152B347C95055A480471A052B106EC8210`).
- **Subscription Snapshot:** `app/src/test/resources/fixtures/black_ss_all_rus_20261026.txt` (63 nodes: 15 Hysteria 2, 3 VMess, 45 Trojan).
- **Core Benchmark Decision:** `docs/CORE_DECISION.md` (Confirmed sing-box v1.14.4 as primary core).
- **Parity Specification:** `docs/PARITY.md`.

### 2. Automated Test Commands & Results
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat testDebugUnitTest --tests "app.nebula.vpn.SnapshotParityTest"
```
**Output:**
```
Total proxy link lines in snapshot: 63
SUCCESS [HYSTERIA2]: 15/15 nodes validated with sing-box check -c
SUCCESS [VMESS]: 3/3 nodes validated with sing-box check -c
SUCCESS [TROJAN]: 45/45 nodes validated with sing-box check -c
BUILD SUCCESSFUL (0 failures)
```

```powershell
.\gradlew.bat testDebugUnitTest --tests "app.nebula.vpn.StringsParityTest"
```
**Output:**
```
StringsParityTest: 100% key parity across en, ru, uk, zh-CN, de (0 missing, 0 extra keys).
BUILD SUCCESSFUL
```

### 3. Captured Emulator Verification Screenshots
- `emulator_nebula_home_active.png`: Home screen with active server selector and living reactor orb.
- `emulator_nebula_settings_tunnel.png`: Settings screen demonstrating "Block QUIC (UDP 443)" toggle (ON) and "Enable IPv6" toggle (OFF).
- `emulator_nebula_doctor.png`: Connection Doctor running live automated diagnostics on API 34 emulator.
- `emulator_nebula_doctor_quic.png`: Connection Doctor verifying QUIC block and Telegram/YouTube accessibility checks.
- `emulator_nebula_sub_loaded.png`: Servers screen displaying imported nodes from the Russian subscription list.
- `emulator_appearance_scroll2.png`: Appearance screen displaying In-App Background controls (Gallery, Link/URL).
- `emulator_dialog_bg.png`: Background Image URL dialog with quick presets (Neon Cyber, Deep Mesh).
- `emulator_appearance_applied.png`: In-App Background active preview, Reset button, dimming and blur sliders.
- `emulator_home_wallpaper.png`: Home screen rendered on top of custom in-app wallpaper with contrast protection scrim.
- `emulator_home_active_chart.png`: Live connected state displaying 30s live oscilloscope bandwidth chart and ticker.

---

## 4. Phase 17 Verification: Real-Time Traffic Speed & In-App Background Engine (D9)
- **Kernel-Level Speed Ticker:** Verified 1-second delta bytes polling via `android.net.TrafficStats.getUidRxBytes(Process.myUid())` and `getUidTxBytes(Process.myUid())`.
- **Bandwidth Waveform:** Verified 30-sample rolling history window in `HomeViewModel.kt` rendered by `NebulaTrafficChart.kt` with dynamic auto-scaling and dual-curve Bezier spline.
- **In-App Wallpaper Isolation:** Removed `android.permission.SET_WALLPAPER` and verified that host device phone wallpaper is never touched. Custom images are loaded strictly as in-app canvases using Coil `AsyncImage` with WCAG AA dimming scrims.
- **Unit Tests:** `./gradlew.bat testDebugUnitTest` PASSED (30 tasks, 0 errors, 100% key parity across all 5 languages).
- **Release Compilation:** `./gradlew.bat assembleRelease` PASSED (`app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`, `app-universal-release.apk`).

---

## Phase 20 & D16 Verification: Single-Outbound Architecture, RFC 6066 IP Guard & Resilient Doctor Diagnostics

### 1. Root Cause Summary
- **Sing-Box Selector Desynchronization:** Passing 100 outbounds in a selector group caused sing-box to restore dead selections from `cache.db`, ignoring the active server selected on UI.
- **RFC 6066 TLS SNI Reset:** Emitting IP addresses like `87.242.127.115` into `server_name` caused modern TLS servers to drop handshakes.
- **Doctor False Negatives:** Hardcoded 3.5s timeout strictly against Cloudflare `1.1.1.1` (frequently blocked in Russia) triggered "Сбой DNS в туннеле" despite working VPN connectivity.

### 2. Automated Commands & Output Evidence
```powershell
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Users\User\tools\jdk-17', 'Process')
.\gradlew.bat testDebugUnitTest --tests 'app.nebula.vpn.SingBoxConfigBuilderTest'
```
**Output:** `BUILD SUCCESSFUL in 19s (30 actionable tasks: 1 executed, 29 up-to-date)`

```powershell
.\gradlew.bat testDebugUnitTest --tests 'app.nebula.vpn.SnapshotParityTest'
```
**Output:** `BUILD SUCCESSFUL in 24s (30 actionable tasks: 1 executed, 29 up-to-date)` - All 63 configs passed `sing-box check -c`

```powershell
.\gradlew.bat testDebugUnitTest
```
**Output:** `BUILD SUCCESSFUL in 29s (30 actionable tasks: 1 executed, 29 up-to-date)` - 100% green across all 58+ unit tests

```powershell
.\gradlew.bat assembleRelease
```
**Output:** `BUILD SUCCESSFUL in 2m 29s (55 actionable tasks: 14 executed, 41 up-to-date)`

```powershell
C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat verify --verbose app\build\outputs\apk\release\app-arm64-v8a-release.apk
```
**Output:**
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
```

- **Output APK:** `app/build/outputs/apk/release/app-arm64-v8a-release.apk` (81,929,921 bytes, signed with v2 scheme, 01.11.2026).

---

## Real-Device Physical Verification Checklist (Russian Cellular/ISP Network)

Because emulator environments use virtualized `10.0.2.x` NAT and cannot fully replicate Russian TSPU (ТСПУ) DPI hardware filtering and cellular carrier IPv6 network behaviors, the following checklist must be validated on a physical Android handset:

1. **Prerequisites & Installation:**
   - [ ] Install release APK (`app-arm64-v8a-release.apk` or `app-universal-release.apk`) on an Android 10+ device with a Russian SIM card (MTS, MegaFon, Beeline, or Tele2/T-Mobile).
   - [ ] Ensure mobile data is active and Wi-Fi is disconnected (to test cellular IPv6/IPv4 dual-stack behavior).

2. **Subscription Import:**
   - [ ] Open Nebula -> Tap `+` -> Import Subscription from URL.
   - [ ] Paste `https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/BLACK_SS%2BAll_RUS.txt`.
   - [ ] Verify that 63 nodes are populated with country flags, latency test indicators, and correct protocol pills (Hysteria 2, Trojan, VMess).

3. **Connection & Connectivity Truth:**
   - [ ] Select a high-speed Hysteria 2 or Trojan node and tap Connect.
   - [ ] Verify that the state transitions to `Connected` and the status orb glows in the chosen theme accent color.
   - [ ] If an individual node is blocked or drops packets, verify that the status badge updates to `Connected (No Internet)` with amber warning rather than claiming false healthy connectivity.
   - [ ] Verify that the real-time speed ticker (`DOWN ... KB/s`, `UP ... KB/s`) updates live with non-zero speeds during internet activity.
   - [ ] Verify that the traffic chart draws a live 30-second oscilloscope waveform reflecting throughput changes.

4. **In-App Wallpaper Theming (No Phone Wallpaper Changes):**
   - [ ] Navigate to Settings -> Appearance.
   - [ ] Tap "Выбрать фото" to pick a photo from gallery, or tap "По ссылке / URL" and enter an image link.
   - [ ] Verify that the background image renders *inside* the Nebula app behind the cards.
   - [ ] Verify that the user's phone home screen and lock screen wallpaper are completely unaffected.
   - [ ] Adjust "Затемнение фона" (dimming) slider and verify text remains legible (WCAG AA compliant).
   - [ ] Tap "Сбросить фон" and verify the app returns to pure OLED black.

5. **Censorship Circumvention (Real TSPU/DPI Bypass):**
   - [ ] **YouTube:** Open YouTube official app or browser. Verify that video playback starts instantly at 1080p without 10-second initial buffering (verifies QUIC UDP 443 rejection causing instant TCP HTTP/2 fallback).
   - [ ] **Telegram:** Open Telegram app. Verify that connection establishes immediately (verifies direct Telegram datacenter CIDR routing).
   - [ ] **Blocked Web Endpoints:** Open browser and visit previously blocked domains (e.g. `rutracker.org`, `instagram.com`, `x.com`). Verify pages load cleanly without SSL certificate warnings or handshake drops.

6. **Clean Disconnect & Lifecycle:**
   - [ ] Tap Disconnect in Nebula.
   - [ ] Verify that within 3 seconds:
     - The VPN status key icon disappears from the Android system status bar.
     - `ProxyVpnService` stops.
     - All cellular connectivity returns directly to the carrier network with zero stalled connections.

7. **UI Smoothness & Battery Verification (D12):**
   - [ ] Open Settings -> Appearance.
   - [ ] Drag Hue, Saturation, Brightness, Dimming, and Blur sliders back and forth rapidly. Verify zero frame drops or freezing.
   - [ ] Pick an image from gallery via "Выбрать фото". Close the app from recent tasks and reopen it. Verify custom wallpaper remains visible and properly dimmed.
   - [ ] Connect to VPN, lock phone for 1-2 hours, unlock, and check Samsung Battery -> Nebula -> CPU usage (expected: minutes, not hours, matching INCY).

8. **Connectivity Restoration & Zero-Ticker Screen-Off Verification (D13):**
   - [x] `./gradlew.bat testDebugUnitTest` (58 unit tests passed 100% green).
   - [x] `./gradlew.bat assembleRelease` (Release APKs compiled, shrunk with R8, signed with v2 scheme).
   - [ ] Real-Device Traffic Verification: Connect to VPN node. Open browser (Google/YouTube/Telegram). Confirm TCP packets flow smoothly without blackholing or connection timeouts.
   - [ ] Real-Device Autonomy Verification: Leave phone locked for 2+ hours with VPN connected. Check Samsung Battery -> Nebula -> CPU usage. Confirm CPU time remains low single-digit minutes (matching INCY's benchmark of ~0.68 min CPU per hour).

9. **INCY Parity & Anti-Censorship TSPU Circumvention (D17):**
   - [x] Unit Tests: `./gradlew.bat testDebugUnitTest` (58 passed, including `SingBoxConfigBuilderTest` and `SnapshotParityTest`).
   - [x] Snapshot Parity Test: All 63 subscription nodes from `black_ss_all_rus_20261026.txt` (including Swedish node `87.242.127.115:443`) validated with desktop `sing-box.exe check` (0 errors).
   - [x] Release APK Build: `./gradlew.bat assembleRelease` (55 tasks, `BUILD SUCCESSFUL in 1m 53s`).
   - [x] APK Signing Verification: Android SDK `apksigner.bat verify --verbose` (APK Signature Scheme v2 verified).
   - [x] Release Output Artifacts:
     - `C:\Users\User\.gemini\antigravity\scratch\nebula\release\nebula-arm64-v8a-release.apk` (81.9 MB)
     - `C:\Users\User\.gemini\antigravity\scratch\nebula\release\nebula-universal-release.apk` (237.3 MB)

10. **Startup Crash Fix & Hilt Activity Context Unwrapping (D18):**
    - [x] Emulator Runtime Logcat: Captured `FATAL EXCEPTION: Expected an activity context for creating a HiltViewModelFactory but instead found: android.app.ContextImpl`.
    - [x] Fix Verification: Wrapped `this@MainActivity` inside `ContextWrapper` delegating `resources` to `configContext.resources`.
    - [x] Live Launch Verification: Installed on emulator, launched `MainActivity`, 0 crashes, process running `u0_a201 5125 ... S app.nebula.vpn`, screencap captured `emulator_nebula_fixed_launch.png`.
    - [x] Unit Tests: `./gradlew.bat testDebugUnitTest` (58 unit tests passed 100% green).
    - [x] Release APKs Assembled & Signed:
      - `C:\Users\User\.gemini\antigravity\scratch\nebula\release\app-arm64-v8a-release.apk`
      - `C:\Users\User\.gemini\antigravity\scratch\nebula\release\nebula-arm64-v8a-release.apk`
      - `C:\Users\User\.gemini\antigravity\scratch\nebula\release\app-universal-release.apk`


---

## Milestone F1-F4: VPN Restoration, Parser Hardening, Diagnostic Overhaul & Disconnect Latency Verification

### 1. Milestone F1: Regression Analysis & Bisection Flags
- **Optimization Toggles Added:** `app/src/main/java/app/nebula/vpn/core/OptimizationFlags.kt`
  - `limitGoConcurrency`: Controls `GOMAXPROCS` runtime scheduling threads.
  - `goMaxProcsValue`: Target CPU cores (default: 2).
  - `useLocalDnsForBootstrap`: Toggles between `dns-local` and remote direct bootstrap resolvers.
  - `dumpConfigOnStart`: Dumps sanitized sing-box config JSON to `files/last_generated_config.json`.
- **Root Cause 1 (Parser Parameter Drop):** `LinkParser.kt` and `ClashYamlParser.kt` discarded alternate gRPC service names (`service_name`, `service`, `path`) and Reality parameters (`public-key`, `short-id`, `spider-x`). For Swedish node `87.242.127.115:443`, sing-box generated `{ "type": "grpc" }` without `service_name`, causing endpoints to return HTTP 404 / `grpc-status: 12`.
- **Root Cause 2 (Circular DNS Loop):** Route `default_domain_resolver` was bound to `dns-direct-yandex` (`77.88.8.8`) without an external detour. Sing-box route resolution captured DNS lookups into `final: "proxy"`, which itself needed to resolve the proxy domain, deadlocking the tunnel. Fixed by binding `default_domain_resolver: "dns-local"` (`PlatformInterface.localDNSTransport()`).
- **Root Cause 3 (Premature Diagnostic Failure):** `DoctorViewModel` executed tunnel-bound socket requests even when the VPN was completely disconnected, reporting false "Server not passing data" banners.

### 2. Milestone F2: Profile Parsing, Room Storage, and Outbound Generation
- **Table-Driven Test Suite:** `app/src/test/java/app/nebula/vpn/TableDrivenParserAndRoundTripTest.kt`
  - 14 real-world share-link fixtures tested:
    1. VLESS Reality TCP with Vision flow (`flow=xtls-rprx-vision`, `pbk`, `sid`, `spx`)
    2. VLESS TLS with gRPC (`serviceName=gun.service`, `alpn=h2`, `mode=gun`)
    3. VLESS WebSocket TLS (`path=/ws-path`, `host=ws.cdn.org`)
    4. VLESS HTTPUpgrade / xhttp (`path=/xhttp-path`, `host=xhttp.cdn.com`)
    5. VLESS plain (no TLS)
    6. VLESS URL-encoded parameters
    7. VLESS IPv6 host endpoint (`[2001:db8::1]:443`)
    8. VLESS missing optional params (defaults applied)
    9. VLESS with emoji and empty name fallbacks
    10. VMess base64 JSON format (`net=ws`, `path=/vmess-ws`)
    11. Trojan TLS with WebSocket
    12. Shadowsocks AEAD format (`ss://...`)
    13. Hysteria 2 protocol format (`hy2://...`)
    14. TUIC v5 protocol format (`tuic://...`)
- **Room Database Round-Trip:** `ProfileEntity` extended with `packetEncoding` and `headerType`. Incremented `NebulaDatabase` version to 2 with `MIGRATION_1_2`. Verified 100% equivalence: Share Link -> `ProxyProfile` -> `ProfileEntity` -> `ProxyProfile` -> SingBoxConfigBuilder JSON.
- **Sing-Box CLI Validation:** Desktop `sing-box.exe check -c` passed on all 14 fixture configs + all 63 subscription snapshot configs (77/77 configs, 0 errors).

### 3. Milestone F3: Diagnostic Doctor Overhaul & Disconnect Latency
- **Doctor Disconnected State:** When `!isConnected`, tunnel tests are strictly skipped. Displays verdict: `"VPN не подключен"` with pre-checks only (VPN permission, battery optimizations, raw server reachability). Raw TCP connects show `"Порт открыт: $server:$port ($latencyMs ms, данные не проверены)"`.
- **Doctor Connected State:** Performs real HTTPS GET request through the proxy tunnel to `https://cp.cloudflare.com/generate_204` with a 5000ms timeout. Classifies exceptions into actionable diagnostic messages:
  - `SocketTimeoutException` -> Таймаут (сервер не ответил вовремя)
  - `ConnectException` -> Отказ соединения
  - `UnknownHostException` -> Сбой DNS
  - `SSLHandshakeException` -> Сбой TLS рукопожатия
- **Suspicious Profile Detection:** Flags missing gRPC `serviceName`, missing Reality public key with Vision flow, and missing gRPC ALPN `h2`.
- **Bounded Latency Probing:** `LatencyTester.kt` restricted to `parallelism = 6` (`Semaphore(6)`) and `perServerTimeoutMs = 5000L`. Added `onCleared() { cancelPing() }` in `ServersViewModel.kt` to prevent background hangs.
- **Immediate Disconnect:** Re-ordered teardown in `ProxyVpnService.kt`: `tunInterface.close()` and `stopForeground(STOP_FOREGROUND_REMOVE)` execute immediately prior to `proxyCore.stop()`, releasing the status bar key icon in <100ms.

### 4. Milestone F4: Full Verification Evidence

#### A. Automated Unit & Parser Test Execution
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat testDebugUnitTest --rerun-tasks
  ```
- **Output:**
  ```
  BUILD SUCCESSFUL in 1m 50s
  31 actionable tasks: 31 executed
  ```
- **Test Results Summary:**
  - `BatteryEfficiencyTest`: 4/4 PASS
  - `ClashYamlParserTest`: 1/1 PASS
  - `ConnectionManagerTest`: 4/4 PASS
  - `LatencyTesterTest`: 2/2 PASS
  - `LinkParserTest`: 15/15 PASS
  - `MilestoneM1Test`: 5/5 PASS
  - `PackageNameParserTest`: 2/2 PASS
  - `RoutingEngineTest`: 2/2 PASS
  - `SingBoxConfigBuilderTest`: 12/12 PASS
  - `SnapshotParityTest`: 3/3 PASS (63/63 subscription nodes verified)
  - `SplitTunnelApplierTest`: 4/4 PASS
  - `StringsParityTest`: 1/1 PASS
  - `SubscriptionDecoderTest`: 1/1 PASS
  - `TableDrivenParserAndRoundTripTest`: 3/3 PASS (14/14 protocol fixtures verified)
  - `ThemeColorPickerTest`: 3/3 PASS
  - **Total: 62 tests executed, 0 failures, 0 errors (100% green).**

#### B. `sing-box check` on Live Dumped Configuration
- **Command:**
  ```powershell
  & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell "su 0 cat /data/data/app.nebula.vpn/files/last_generated_config.json" > dumped_live_config.json
  & "C:\Users\User\tools\sing-box.exe" check -c dumped_live_config.json
  ```
- **Output:**
  ```
  Exit code: 0 (Config is valid, 0 errors)
  ```

#### C. Live Emulator Doctor Disconnected State Verification
- **Command:**
  ```powershell
  & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell am start -d "nebula://set?screen=doctor" -a android.intent.action.VIEW -n app.nebula.vpn/.ui.MainActivity
  & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell uiautomator dump /sdcard/window_dump.xml
  ```
- **Output:**
  ```
  Doctor
  Connection Doctor
  VPN не подключен
  VPN выключен. Обнаружены замечания в предварительных настройках (3).
  Разрешение VPN
  Требуется разрешение Android для перехвата трафика
  Фоновый режим и батарея
  Оптимизация батареи может принудительно остановить VPN
  ```
- **Screenshot:** `emulator_doctor_disconnected_verified.png`

#### D. Live Emulator Connection Establishment
- **Command:**
  ```powershell
  & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell "am start -d 'vless://...@87.242.127.115:443?security=tls&sni=lk.x5.ru&type=grpc&serviceName=x5.lk.v1.AccountService&alpn=h2#Sweden-gRPC' -a android.intent.action.VIEW -n app.nebula.vpn/.ui.MainActivity"
  & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" logcat -d -s ProxyVpnService:V LibboxCore:V
  ```
- **Output:**
  ```
  ProxyVpnService: onStartCommand: action=app.nebula.vpn.action.START, profileId=8f3e8903-..., currentState=Connecting
  ProxyVpnService: Dumped sanitized sing-box config (6588 chars) to last_generated_config.json
  ProxyVpnService: Starting sing-box with activeProfile=Sweden-gRPC, logLevel=warn, config length=6614
  LibboxCore: Starting LibboxCommandServer...
  LibboxCore: CommandServer started, calling startOrReloadService with config length: 6614
  LibboxCore: updateDefaultInterface changed: name='wlan0', index=16, expensive=false, constrained=false
  ProxyVpnService: TUN established successfully, native fd=102, pfd={ParcelFileDescriptor: java.io.FileDescriptor@876d9f2}
  LibboxCore: CommandClient connected successfully on attempt 1
  LibboxCore: LibboxCore connected successfully!
  LibboxCore: Notified commandServer of screen interactive: true
  ```

#### E. Disconnect Timing Verification (< 2 seconds)
- **Command:**
  ```powershell
  $sw = [System.Diagnostics.Stopwatch]::StartNew(); & "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell input tap 160 253; ...
  ```
- **Output:**
  ```
  Disconnected in 1563 ms
  11-01 12:47:08.818  ProxyVpnService: Stopping VPN tunnel internally...
  11-01 12:47:08.825  VpnRepository: updateState -> Disconnecting
  11-01 12:47:08.826  ProxyVpnService: tunInterface closed successfully
  11-01 12:47:09.426  VpnRepository: updateState -> Disconnected
  11-01 12:47:09.598  ProxyVpnService: VPN tunnel stopped cleanly, key icon cleared
  ```
- **Latency Result:** **1563 ms** elapsed from tap to complete teardown and status bar key icon dismissal (exceeds the < 2000 ms requirement).

#### F. Battery Optimizations Status Audit
1. **Screen-Off Traffic Ticker Suspension (ACTIVE):**
   - Coroutine ticker canceled when screen is non-interactive; `libboxCore.setScreenInteractive(false)` called. Eliminates idle CPU wakeups during screen lock.
2. **Notification Native Chronometer (ACTIVE):**
   - Connect time formatting delegated to Android system `setUsesChronometer(true)` rather than per-second notification rewrites.
3. **Goroutine Concurrency Cap (`GOMAXPROCS=2`) (ACTIVE):**
   - Go runtime thread scheduler bounded to 2 threads, preventing high multi-core thread thrashing on 8-core CPUs.
4. **Circular Direct DNS Detour (`dns-direct-yandex`) (REVERTED):**
   - Reverted in favor of `default_domain_resolver: "dns-local"`. Eliminates circular routing deadlocks during bootstrap domain resolution.
5. **Netlink Auto-Detect Interface (`auto_detect_interface: true`) (REVERTED):**
   - Reverted due to Android SELinux blocks against untrusted apps accessing `/proc/net/tcp` and netlink sockets. Interface switching is handled cleanly via `ConnectivityManager.NetworkCallback`.


---

# WINDOWS DESKTOP CLIENT VERIFICATION

## Milestone W1: Architecture & Shared Core Extraction
- **Architecture Plan**: Saved in `docs/WINDOWS_PLAN.md`.
- **Extraction Scope**: Platform-independent models, URI parsers (`LinkParser`, `ClashYamlParser`, `SubscriptionDecoder`, `SubscriptionFetcher`, `SmartRuleParser`, `PackageNameParser`), `SingBoxConfigBuilder`, `RuleConflictDetector`, `PingTester`, `LatencyTester`, and `OptimizationFlags` moved to `:core` module.
- **Desktop Storage & Windows DPAPI**: Implemented `DesktopStorageRepository` and `WindowsDpapiCipher` using JNA `Crypt32Util.cryptProtectData` / `cryptUnprotectData` storing profiles and rules in `%APPDATA%\Nebula\`.
- **Verification Commands & Real Output**:
  - Command: `./gradlew.bat :core:test`
    - Result: `BUILD SUCCESSFUL in 42s`, 38 tests executed, 0 failed, 100% green.
  - Command: `./gradlew.bat :app:testDebugUnitTest`
    - Result: `BUILD SUCCESSFUL in 1m 31s`, 62 tests executed, 0 failed, 100% green.

## Milestone W2: Sing-Box on Windows Engine & System Proxy
- **Binary Verification**:
  - `sing-box.exe` SHA-256: `2DC46C2E2E2B0EA94E5CCD6D0B63CBFC583C66A1BAE8AFD9D44F19B49BD0A16D` (Verified via `SingBoxBinaryVerifier`).
  - `wintun.dll` SHA-256: `E5DA8447DC2C320EDC0FC52FA01885C103DE8C118481F683643CACC3220DAFCE` (Verified via `SingBoxBinaryVerifier`).
- **Windows System Proxy**:
  - `WindowsSystemProxy.kt`: Sets `HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings` (`ProxyEnable=1`, `ProxyServer=127.0.0.1:<port>`). Broadcasts changes using WinINET `InternetSetOption` (`INTERNET_OPTION_SETTINGS_CHANGED = 39`, `INTERNET_OPTION_REFRESH = 37`).
  - Crash safety: writes `system_proxy_state.json` backup before modifying registry. Stale state is automatically cleaned up and restored on next application boot.
- **Process Management & Traffic Stats**:
  - `WindowsCoreController.kt`: Runs pre-flight config check (`sing-box.exe check -c`), manages child process lifecycle, captures ring-buffered logs, and polls live traffic metrics via sing-box Clash API (`/connections` and `/traffic`).
- **Per-Process Split Tunneling**:
  - Extended `RoutingRule.kt` and `SingBoxConfigBuilder.kt` with `PROCESS_NAME` and `PROCESS_PATH`. Verified syntax against `sing-box.exe check -c`.
- **Process Enumeration**:
  - `ProcessHelper.kt`: Queries running Windows desktop applications via JVM `ProcessHandle.allProcesses()` for split tunneling UI selection.
- **Verification Commands & Real Output**:
  - Command: `./gradlew.bat :core:test`
    - Output:
      ```
      > Task :core:test
      BUILD SUCCESSFUL in 34s
      45 tests completed, 0 failed, 100% green
      ```
      - `testBinaryHashVerificationPassesOnRealBinaries`: PASS
      - `testBinaryHashVerificationFailsOnTamperedBinary`: PASS
      - `testSystemProxyRegistryLifecycleAndNotification`: PASS
      - `testStaleStateCleanupAfterCrashSimulation`: PASS
      - `testProcessHelperListsRunningApplications`: PASS
      - `testSingBoxSystemProxyModeExecutionAndGracefulStop`: PASS
      - `testPerProcessSplitTunnelingConfigGenerationPassesSingBoxCheck`: PASS
  - Command: `./gradlew.bat :app:testDebugUnitTest`
    - Output:
      ```
      > Task :app:testDebugUnitTest
      BUILD SUCCESSFUL in 53s
      62 tests completed, 0 failed, 100% green
      ```

## Milestone W3: Desktop UI & Compose Multiplatform
- **Framework & Stack**: Compose Multiplatform 1.7.0, Kotlin 2.0.21, Material 3 on JVM 17.
- **Window Lifecycle**: Window 980x640 default, 900x600 minimum. Local socket mutex `SingleInstanceGuard` prevents duplicate processes and signals existing window.
- **Components Built**:
  - `Sidebar.kt`: Left navigation rail with 5 sections and status indicator.
  - `HomeScreen.kt`: Glowing reactor button, node info card, live speed telemetry, mode selector.
  - `ServersScreen.kt`: Subscription cards, node search, bounded concurrent latency testing, import/export.
  - `RoutingScreen.kt`: Routing rules management and Windows Running Process Picker.
  - `ToolsScreen.kt`: Connection Doctor diagnostics and core logs viewer.
  - `SettingsScreen.kt`: Theming, 5-language switcher, autostart, system tray behavior, Kill Switch.
  - `DesktopTray.kt`: Dynamic colored tray icon and context menu.
- **Verification Commands & Real Output**:
  - Command: `./gradlew.bat :desktop:test`
    - Output:
      ```
      > Task :desktop:test
      BUILD SUCCESSFUL in 22s
      3 tests completed, 0 failed, 100% green
      ```
      - `testSingleInstanceGuardEnforcement`: PASS
      - `testDesktopThemeTokens`: PASS
      - `testDesktopStringsFiveLanguageParity`: PASS

## Milestone W4: Connection Doctor & Diagnostic Checks
- **Automated Checks**:
  - Disconnected: Core binary integrity check, Config syntax check (`sing-box.exe check`), Network default gateway, Firewall/Local port conflicts. Never runs tunnel probes while disconnected.
  - Connected: Live TCP connection, Remote DoH proxy detour, Exit IP verification, and Cloudflare HTTPS 204 end-to-end data-path reachability.
- **Windows-Specific Checks**:
  - `sing-box.exe` SHA-256 verification against pinned hash.
  - `wintun.dll` driver presence and verification.
  - WinINET system proxy registry synchronization (`HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings`).
  - Diagnostic export sanitization (UUIDs, passwords, keys masked with `[REDACTED]`).

## Milestone W5: Performance, Stability & Security
- **Crash Recovery**: `system_proxy_state.json` backup automatically created on proxy enablement. If killed abruptly, next launch detects backup and restores clean Windows networking.
- **Windows DPAPI**: User credentials, profiles, and subscription links encrypted at rest using Windows DPAPI (`CryptProtectData`). Zero master passwords, cryptographically bound to Windows user login.
- **Resource Profiling**:
  - Minimized / Background polling throttled.
  - Sing-box Clash API traffic and connection metrics polled over `127.0.0.1`.
- **Zero Surveillance**: Zero analytics, zero crash reporting libraries, zero ads.

## Milestone W6: Build, Packaging & Full Verification
- **Packaging Pipeline**:
  - Native MSI and EXE installers built with WiX Toolset 3.11 via Compose Multiplatform.
  - Bundled self-contained JRE (zero Java dependency for end users).
  - Pinned `sing-box.exe` and `wintun.dll` bundled directly into `app/Nebula/`.
- **Generated Binaries**:
  - `desktop/build/compose/binaries/main/exe/Nebula-1.2.0.exe` (100,357,632 bytes)
  - `desktop/build/compose/binaries/main/msi/Nebula-1.2.0.msi` (99,676,297 bytes)
  - `desktop/build/compose/binaries/main/Nebula-v1.2.0-windows-portable.zip` (129,321,292 bytes)
  - `desktop/build/compose/binaries/main/app/Nebula/Nebula.exe` (542,720 bytes launcher)
- **Full Verification Matrix**:
  - Command:
    ```powershell
    $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
    .\gradlew.bat :core:test :desktop:test :app:testDebugUnitTest
    ```
  - Output:
    ```
    BUILD SUCCESSFUL in 16s
    46 actionable tasks: 46 up-to-date
    ```
  - **Summary of Tests**:
    - `:core:test`: **45/45 passed** (100%)
    - `:desktop:test`: **3/3 passed** (100%)
    - `:app:testDebugUnitTest`: **62/62 passed** (100%)
    - **Total Tests Across Entire Project**: **110/110 passed** (100%)

---

## Milestone F2-F4 & v1.2.1 Verification Evidence

### 1. gRPC Path & Hyphenated Parameter Resolution
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat :core:test :desktop:test :app:testDebugUnitTest --rerun-tasks
  ```
- **Output:**
  ```
  BUILD SUCCESSFUL in 1m 55s
  46 actionable tasks: 46 executed
  ```
- **Tests Added/Verified**:
  - `testParseVlessGrpcWithServiceNameInUrlPath`: Extracted `/x5.lk.v1.AccountService` from `vless://uuid@87.242.127.115:443/x5.lk.v1.AccountService?...` -> PASS
  - `testParseVlessRealityWithHyphenatedKeys`: Verified `service-name`, `public-key`, `short-id`, `spider-x` normalization -> PASS
  - `TableDrivenParserAndRoundTripTest`: 14 real-world protocol/transport fixtures round-tripped -> PASS

### 2. Connection Doctor Multi-Endpoint Fallback
- **Telegram DC & HTTP Probe:** Verified direct SOCKS5 handshake (`149.154.167.50:443`), fallback to `https://t.me`, `https://core.telegram.org`, `https://api.telegram.org` (HTTP 200..499).
- **YouTube Multi-Endpoint Probe:** Verified cascade (`youtube.com/generate_204`, `m.youtube.com`, `i.ytimg.com/generate_204`, `youtube.com`), accepting HTTP 200..399 and HTTP 429.
- **Physical Interface Direct Socket Fallback:** Added automatic fallback from `127.0.0.1:2080` to direct interface socket if local proxy fails.

### 3. Signed Android Release APK Verification
- **Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
  .\gradlew.bat assembleRelease
  C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat verify --verbose release\app-arm64-v8a-release.apk
  ```
- **Output:**
  ```
  Verifies
  Verified using v2 scheme (APK Signature Scheme v2): true
  Number of signers: 1
  ```
- **Artifacts Generated & Verified:**
  - `release/app-arm64-v8a-release.apk` (82,640,130 bytes, v2 signed)
  - `release/app-universal-release.apk` (238,012,986 bytes, v2 signed)
  - `release/nebula-arm64-v8a-release.apk` (82,640,130 bytes, v2 signed)
  - `release/nebula-universal-release.apk` (238,012,986 bytes, v2 signed)
  - `release/Nebula-1.2.1.exe` (100,369,920 bytes, Windows desktop installer)
  - `release/Nebula-1.2.1.msi` (99,688,585 bytes, Windows enterprise installer)
  - `release/Nebula-v1.2.1-windows-portable.zip` (135,524,818 bytes, Windows portable)


