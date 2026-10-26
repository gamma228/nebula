# Nebula Android Proxy/VPN Client - Verification & Release Summary

## Executive Overview
Nebula has undergone comprehensive recovery, architectural tuning, and automated verification across all 6 defect areas (D1 through D6). The client utilizes the native SagerNet sing-box v1.14.4 core via gomobile Cgo TUN file descriptor handoff, Jetpack Compose Material 3 UI, and strict reactive state machines.

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
