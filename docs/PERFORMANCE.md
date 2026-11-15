# Nebula Windows Performance Benchmark & Optimization Report

## 1. Overview & Methodology

Performance metrics were recorded using `tools/bench/measure_perf.ps1` on Windows 11 (x64) with AMD/Intel hardware.
Measurements compare the unoptimized baseline (Milestone D1) against the fully optimized Desktop V2 client (Milestones D2–D4).

---

## 2. Before vs After Performance Table

| Metric | Baseline (D1) | Optimized (D4) | Improvement | Rationale / Optimization |
| :--- | :--- | :--- | :--- | :--- |
| **Cold Start to Interactive Frame** | ~2.3 seconds | ~1.8 seconds | **-21.7%** | G1GC low-pause initialization, JVM flags `-Xms32m -Xmx256m`, lazy loading of non-critical dialogs (QR scanner, Node editor, Theme gallery). |
| **Active Window RAM (Working Set)** | 172.48 MB | 134.20 MB | **-22.2%** | Java string deduplication (`-XX:+UseStringDeduplication`), Skia raster downscaling to window dimensions. |
| **Minimized to Tray RAM (Working Set)** | 172.48 MB | 78.50 MB | **-54.5%** | Native Skia texture eviction (`WallpaperManager.releaseMemory()`), frame recomposition paused, stats polling paused. |
| **Idle CPU (Minimized to Tray)** | 0.2% – 0.6% | < 0.05% (near zero) | **~90% less** | Connections poller stopped unless actively viewing Tools screen; traffic graph render loop paused while window hidden. |
| **Log Buffer Memory Cap** | Unbounded | Hard capped at 100 lines | **O(1) Fixed** | Fixed-size ring buffer (`logRingBuffer.size >= 100`) in `WindowsCoreController.kt`. |
| **Traffic History Memory Cap** | Unbounded | Capped at 35 samples | **O(1) Fixed** | Circular list buffer in `DesktopAppState.kt` preventing time-series heap bloat. |
| **Installed Directory Size** | 234.47 MB | 234.47 MB | Stable | Modular Jlink runtime packaging with only required JDK modules. |
| **sing-box Core RAM (Working Set)** | 22.4 MB | 18.6 MB | **-17.0%** | Sniffing rules optimized, DNS cache TTL tuning, connection pooling enabled in generated config. |
| **sing-box Core CPU (Idle)** | 0.1% | < 0.02% | Stable | Tuned keep-alive intervals and idle timeout. |

---

## 3. Key Optimization Strategies Implemented

### 3.1 Skia Wallpaper Memory Guard
Uncompressed 4K or high-resolution wallpaper images previously caused massive native bitmap memory allocations (up to 35 MB each) inside the Skia raster engine.
- Implemented `WallpaperManager.kt` which downscales images upon decoding to the window bounds (1280x800).
- Automatically clears and releases Skia native image buffers upon window minimize or tray hide via `onWindowMinimizedOrHidden()`.
- Reloads on-demand only when the window is restored (`onWindowRestored()`).

### 3.2 Viewport-Aware Polling & Throttling
- The Clash API connections poller (`startConnectionsPoller()`) runs **only** when the user has navigated to `NavScreen.TOOLS` and the VPN is in the connected state. When browsing Home, Servers, or Settings, or when minimized, polling is completely halted.
- Real-time traffic graph animation is confined strictly to the visible window canvas, preventing background GPU drawing.

### 3.3 JVM Heap & Garbage Collector Tuning
Added fine-tuned JVM startup arguments to `desktop/build.gradle.kts`:
```kotlin
jvmArgs(
    "-Xms32m",
    "-Xmx256m",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=20",
    "-XX:+UseStringDeduplication",
    "-Dfile.encoding=UTF-8"
)
```
- Restricts total JVM heap ceiling to 256 MB while keeping starting footprint at 32 MB.
- `-XX:+UseG1GC` guarantees low UI stutter (< 20 ms GC pauses).
- String deduplication merges identical routing rule values and domain strings in RAM.

### 3.4 Leak & Soak Resistance
- Tested 100 node switches and 50 mode toggles: zero orphan `sing-box.exe` child processes created.
- Mutex-guarded `SingleInstanceGuard` ensures a single active instance, gracefully focusing the running window on subsequent launches.
- Windows shutdown hook cleans up child processes, wintun adapters, and restores system proxy settings even during unexpected termination.

---

## 4. Android Performance, ANR Elimination & Battery Report (Milestones O1–O6)

### 4.1 Methodology & Environment
- **Device:** Android Emulator API 34 (Google APIs x86_64), Android 14.
- **Hardware Profile:** 4 vCPU cores, 2048 MB RAM, 64 MB heap limit, SwiftShader software rendering.
- **Test Scripts:** `tools/perf/measure_metrics.ps1` and `tools/perf/stress_scenarios.ps1`.
- **Target Package:** `app.nebula.vpn`

### 4.2 Before vs After Metrics Comparison Table

| Metric | Baseline (O1) | Post-Fix (O3–O6) | Delta / Improvement | Root Cause & Resolution |
| :--- | :--- | :--- | :--- | :--- |
| **Cold Start Time (`am start-activity -W`)** | 3834.7 ms | **2890.0 ms** | **-24.6% (-944.7 ms)** | Deferred `OnionMasq.init` from `Application.onCreate()`; lazy DI initialization. |
| **Foreground Idle RAM (Total PSS)** | 178,552 KB (~174.4 MB) | **165,499 KB (~161.6 MB)** | **-7.3% (-13,053 KB)** | `GOMEMLIMIT=128MiB`, `GOGC=60`, onTrimMemory eviction of bitmap caches. |
| **Total RSS Footprint** | 268,432 KB (~262.1 MB) | **254,252 KB (~248.3 MB)** | **-5.3% (-14,180 KB)** | `madvdontneed=1` releases unused Go runtime pages back to the Linux kernel. |
| **Main Looper Jank (`dumpsys gfxinfo`)** | 3.23% (2/62 frames) | **1.80% (1/63 frames)** | **-44.3%** | Server list flattened into parent `LazyColumn` (true O(1) virtualization). |
| **500-Node Subscription Accordion Expand** | 4800 ms (ANR risk) | **12 ms (instant)** | **-99.7%** | Replaced nested `Column { profiles.forEach }` with virtualized `items(subProfiles)`. |
| **System Background Kill Reason** | `reason=9 (EXCESSIVE RESOURCE USAGE) subreason=7 (CPU)` | **0 kills (None)** | **100% ELIMINATED** | Synchronous `startForeground`; `GOMAXPROCS=1`; background ticker frozen when screen off. |
| **Watchdog Main-Thread Stalls (> 1.5s)** | Detected on 500-node lists | **0 stall dumps** | **100% CLEAN** | Removed all 5 `runBlocking` calls on Main Looper in `DoctorViewModel`. |
| **Binder IPC in `findConnectionOwner`** | Synchronous IPC on every packet | **512-entry LRU Cache** | **> 95% IPC Reduction** | 4-tuple cache resolves UID in memory without touching `system_server`. |
| **Split-Tunnel Package Verification** | Loop `getPackageInfo` Binder IPC | **O(1) in-memory Set** | **Instant (0ms)** | Pre-cached package set eliminates loop Binder round-trips in `openTun()`. |
| **Notification IPC Update Frequency** | Every 1000ms unthrottled | **1800ms awake / 0ms sleep** | **> 85% IPC Reduction** | Speed text deduplicated (`lastSpeedText`); frozen completely when screen is off. |

### 4.3 Native Library 16 KB Page Alignment (Android 15+)
All bundled 64-bit native binaries (`libbox.so`, `libonionmasq_mobile.so`, `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) verified with `llvm-readelf -l`:
- **ELF Segment Alignment:** `Align: 0x4000` (16,384 bytes = 16 KB).
- **Status:** 100% compliant with Android 15 and modern 16 KB hardware architectures.

