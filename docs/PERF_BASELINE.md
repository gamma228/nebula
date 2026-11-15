# Nebula Performance & Stability Baseline (Milestone O1)

**Date:** November 2026  
**Environment:** Android Emulator API 34 (UpsideDownCake x86_64, Google APIs)  
**AVD Profile:** 4 CPU cores, 2048 MB RAM, 64 MB heap size, SwiftShader software rendering  
**Target Package:** `app.nebula.vpn`

---

## 1. Baseline Performance Metrics

| Metric | Target / Budget | Measured Baseline | Status |
| :--- | :--- | :--- | :--- |
| **Cold Start Time (TotalTime)** | < 1500 ms | **3834.7 ms** (Run 1: 3848ms, Run 2: 3733ms, Run 3: 3923ms) | ⚠️ SLOW (3.8s) |
| **Foreground Idle RAM (Total PSS)** | < 100 MB | **178,552 KB (~174.4 MB)** | ⚠️ HIGH |
| **Total RSS Footprint** | < 150 MB | **268,432 KB (~262.1 MB)** | ⚠️ HIGH |
| **Native Heap PSS** | < 30 MB | **16,096 KB (~15.7 MB)** | OK |
| **Dalvik Heap PSS** | < 20 MB | **7,758 KB (~7.6 MB)** | OK |
| **UI Frame Jank (dumpsys gfxinfo)** | < 2.0% | **3.23%** (2 janky frames / 62 frames rendered) | ⚠️ JANKY |
| **90th Percentile Frame Time** | < 16.6 ms (60 fps) | **27.0 ms** | ⚠️ EXCEEDS 60FPS BUDGET |
| **Background Service Kill Reason** | None | Detected: `reason=9 (EXCESSIVE RESOURCE USAGE) subreason=7 (EXCESSIVE CPU USAGE)` | 🔴 CRITICAL |

---

## 2. Measurement Tooling (`tools/perf/`)

The following automated benchmarking scripts were created and validated:
1. `tools/perf/measure_metrics.ps1`:
   - Automates cold start measurement over 3 runs with force-stop cycles.
   - Extracts PSS, RSS, Native Heap, Dalvik Heap via `dumpsys meminfo`.
   - Records frame jank and 90th percentile rendering latency via `dumpsys gfxinfo`.
   - Checks watchdog dump directory.
2. `tools/perf/stress_scenarios.ps1`:
   - Simulates OS low-memory trimming (`am send-trim-memory`).
   - Generates CPU contention burner.
   - Executes rapid screen off/on transitions.
   - Runs 20 rapid UI tab switching cycles.
   - Inspects `dumpsys activity exit-info` for `ApplicationExitInfo` history.
3. `tools/perf/README.md`:
   - Documents hardware profile approximation (2 GB RAM, 2 cores, API 26-34).

---

## 3. Main-Thread Watchdog & StrictMode

- **Implementation:** Added `app.nebula.vpn.core.watchdog.MainThreadWatchdog.kt`.
- **Mechanism:** Posts canaries to the Android main looper every 300 ms. If the main thread fails to execute the canary for >= 1500 ms, the watchdog thread dumps all thread stack traces to `/data/data/app.nebula.vpn/files/stability/anr_stall_<timestamp>.txt` and preserves the last 10 dumps in memory for user diagnostics.
- **StrictMode:** Enabled in `NebulaApp.kt` for debug builds:
  - `ThreadPolicy.detectAll().penaltyLog()`
  - `VmPolicy.detectAll().penaltyLog()`
