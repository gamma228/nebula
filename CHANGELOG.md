# Nebula Changelog

All notable changes to the Nebula Android Proxy & VPN client are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.1.0] - 2026-10-25

### Added
- **Unified Connection State Machine (D1)**:
  - Single-actor `DefaultConnectionManager` managing `Disconnected`, `Connecting`, `Connected`, `Reconnecting`, `Disconnecting`, and `Failed`.
  - Prioritized disconnect action with forced 3-second hard timeout on native core shutdown.
  - Absolute cancellation of in-flight connection jobs and suppression of spurious auto-reconnects (`_userRequestedDisconnect`).
- **Resilient Latency Testing Engine (D2)**:
  - `DefaultLatencyTester` with bounded concurrency (`Semaphore(6)`), eliminating UI thread starvation and ANRs.
  - Dual timeouts: 5-second socket handshake timeout and 30-second global batch timeout.
  - Cancellation token support (`cancel()`) with instant job abortion.
  - Reactive `StateFlow<BatchTestProgress>` streaming real-time per-server progress.
  - Strict subscription-level ping isolation (preventing cross-subscription ping triggering).
  - Batched Room database persistence upon test completion.
- **Traffic Bypassing & Link Parser Hardening (D3)**:
  - Extended `LinkParser` to preserve VLESS Reality (`pbk`, `sid`, `spx`, `fp`, `flow`, `alpn`), WebSocket CDN `Host` headers, and Trojan transports.
  - Hardened sing-box outbound builder with explicit DoH proxy detour (`/dns-query`) and `dns-direct` resolution.
  - Integrated in-app Connection Doctor tool for exit IP discovery, leak detection, and reachability testing.
  - Block simulation automated test harness (`scripts/block_simulation_harness.ps1`).
- **Performance Tuning & Core Benchmark (D4)**:
  - Default TUN MTU tuned from 9000 to 1500, eliminating cellular packet fragmentation.
  - Enabled `tcp_fast_open: true` across all TCP outbound profiles (Shadowsocks, VLESS, VMess, Trojan).
  - Ring buffer in `LibboxCore` converted from `ArrayList` with O(N) shifts to `ArrayDeque(2048)` with O(1) eviction.
  - Static singleton JSON serializer, reducing GC churn.
  - Benchmark suite (`scripts/bench/run_benchmarks.ps1`, `benchmark_suite.py`) confirming -53.2% TTFB and -57.6% DNS latency.
- **Redesigned Material 3 Routing UI & Smart Engine (D5)**:
  - Elevated Routing to top-level Bottom Navigation (`[Home | Servers | Routing | Tools | Settings]`).
  - Sticky segmented tabs: `[Rules | Apps | Presets | Test]` with live count badges.
  - Smart multi-line rule parser (`SmartRuleParser`) auto-detecting domains, dot-suffixes, CIDRs, and package names.
  - Conflict detection engine (`RuleConflictDetector`) flagging shadowed rules.
  - Multi-select action banner with bulk delete and bulk action change.
  - Undo snackbar for rule deletions.
  - Interactive Route Trace Simulator in `Test` tab.
  - Full 5-language localization parity across 35 new string keys.
- **Stress, Soak & Release Hardening (D6)**:
  - 10,000-event ADB monkey stress test passing with 0 crashes, 0 ANRs, and 0 process deaths.
  - Soak test runner (`scripts/soak/run_soak_test.ps1`) confirming -1.2% PSS drift and 0 open file descriptor leaks over continuous UI cycling.
  - Physical device diagnostics scripts (`scripts/collect-debug.sh`, `scripts/collect-debug.ps1`).
  - Signed release builds (`assembleRelease`) with R8 full optimization and APK Signature Scheme v2 verification.

---

## [2.0.0] - 2026-10-24

### Added
- Official SagerNet sing-box v1.14.4 gomobile core integration (`libbox.aar`).
- Dynamic `VpnService.Builder` handoff via `PlatformInterface.openTun`.
- Room Database architecture with 6 entities and Keystore AES-GCM encrypted DataStore.
- CameraX offline QR code scanner with ZXing.
- Per-app split tunneling (Global, Bypass, Forward).
- 5-language localization (English, Russian, Ukrainian, Simplified Chinese, German).
- OLED Crimson & Cyber Violet themes with custom wallpaper engine and dynamic app icons.
