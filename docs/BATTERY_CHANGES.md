# Battery & CPU Optimization Log (Parity with INCY)

## Executive Summary
Prior to this release, Samsung battery diagnostics revealed:
- **Nebula (Baseline):** 1 h 42 min total usage (8 min screen on, 1 h 33 min background), **CPU TIME 3 h 32 min** (~2 CPU cores busy continuously), ~64,600 packets, 19.6% battery drain, flagged "intensive background battery usage".
- **Competitor Target (INCY):** 8 h 53 min total (8 min screen on, 8 h 48 min background), **CPU TIME 4 min** (<1% of 1 core), ~230,554 packets (3.5x more data), 5.0% battery drain.

The root cause was not encryption or packet processing, but continuous internal spinning: 1-second foreground notification IPC spam, JNI log streaming across Go and Java to Android logcat, uncancelled UI coroutines looping indefinitely in ViewModel, and busy-polling loops.

All changes maintain complete functional parity, multi-protocol support (VLESS/Reality, VMess, Trojan, Shadowsocks, Hysteria2), user routing rules, and visual aesthetics.

---

## Detailed Audit & Changes

### 1. Foreground Notification IPC Storm
- **File:** `app/src/main/java/app/nebula/vpn/vpn/ProxyVpnService.kt`
- **Root Cause:**
  `trafficTickerJob` executed an un-throttled 1000ms loop:
  `delay(1000L) -> formatSpeed() -> updateForegroundNotification() -> notificationManager.notify(...)`
  On Android, calling `NotificationManager.notify()` triggers a Binder IPC transaction to `system_server`. System server deserializes the notification, updates status bar layout in SystemUI, recalculates notification rank, and prevents the device CPU from entering low-power Doze states. In 1 hour 33 minutes in the background, this caused **5,580 notification IPC dispatches**!
- **Fix:**
  1. Implemented dynamic screen state detection (`BroadcastReceiver` for `Intent.ACTION_SCREEN_ON` and `Intent.ACTION_SCREEN_OFF`, plus `PowerManager.isInteractive`).
  2. **Screen OFF:** Notification speed updates are **completely frozen**. When the screen turns off, the notification is set once to static text: `"Connected: <Profile> • Protected via Nebula"`. Zero notification IPC calls occur while the phone is locked or in the user's pocket.
  3. **Screen ON:** Notification updates are throttled to at most once every **3 seconds** (in Balanced mode) or **5 seconds** (in Battery Saver), and only if content actually changed. Added cached `PendingIntent` references and content comparison to avoid duplicate calls.

---

### 2. Sing-box Core Logging & JNI/Logcat IPC Storm
- **Files:**
  - `app/src/main/java/app/nebula/vpn/core/builder/SingBoxConfigBuilder.kt`
  - `app/src/main/java/app/nebula/vpn/core/proxy/LibboxCore.kt`
- **Root Cause:**
  1. `SingBoxConfigBuilder` hardcoded `"level": "info"`. At `"info"` level, sing-box logs every single socket match, route lookup, DNS lookup, TLS handshake, and connection tear-down.
  2. `LibboxCore.initCommandClient()` subscribed to `addCommand(Libbox.CommandLog)`.
  3. In `writeLogs(logs: LogIterator?)`, every log entry was streamed across the Go-Java JNI bridge, took a synchronized lock, allocated memory, and invoked `android.util.Log.d("SingBoxCore", line)`.
  4. `Log.d` writes directly to Android's `/dev/socket/logdw` socket (IPC to `logd`). Tens of thousands of socket writes pegged mobile CPU cores during active network usage.
- **Fix:**
  1. Changed default `logLevel` in `SingBoxConfigBuilder.BuildConfig` to `"warn"` (and `"error"` in Battery Saver mode).
  2. Filtered `writeLogs`: only actual warnings and errors are logged to logcat (`Log.w`). Normal socket operations no longer cross the JNI log boundary or write to logcat.
  3. Reduced in-memory log ring buffer from 2048 to 250 entries to reduce garbage collection pressure.

---

### 3. Redundant JNI Subscriptions in `CommandClient`
- **File:** `app/src/main/java/app/nebula/vpn/core/proxy/LibboxCore.kt`
- **Root Cause:**
  `LibboxCore.initCommandClient()` registered `addCommand(Libbox.CommandConnections)`.
  The Java callback `writeConnectionEvents` was an empty stub, but the sing-box Go core spent CPU cycles continually serializing active TCP/UDP connection maps across JNI only for Java to discard them.
- **Fix:**
  Removed `CommandConnections` from `clientOptions`. `CommandGroup` is retained for selector outbound management.

---

### 4. Core Status Polling & Speed Ticker Redundancy
- **Files:**
  - `app/src/main/java/app/nebula/vpn/core/proxy/LibboxCore.kt`
  - `app/src/main/java/app/nebula/vpn/vpn/ProxyVpnService.kt`
- **Root Cause:**
  1. `LibboxCore` had `statusInterval = 1000` (1 second), forcing Go to collect telemetry every 1000ms.
  2. In `ProxyVpnService`, both `statsCollectorJob` (collecting `proxyCore.stats`) AND `startTrafficTicker()` (polling UID rx/tx bytes) were running simultaneously and calling `vpnRepository.updateStats(stats)` twice per second.
- **Fix:**
  1. Increased `statusInterval` to `3000ms` in `LibboxCore`.
  2. Removed `statsCollectorJob` in `ProxyVpnService`. `startTrafficTicker` now handles unified telemetry sampling at the PowerProfile rate.
  3. When the screen is OFF, background ticker relaxes to 60s (or is frozen in Battery Saver mode). Cumulative byte accounting is preserved.

---

### 5. Infinite Background Coroutines in `HomeViewModel`
- **File:** `app/src/main/java/app/nebula/vpn/ui/home/HomeViewModel.kt`
- **Root Cause:**
  `HomeViewModel` launched two un-scoped coroutines in `init`:
  1. `while (isActive) { delay(1000); _connectionDurationSeconds.value = elapsed }`
  2. `trafficStats.collect { ... _downloadHistory.value = (_downloadHistory.value.drop(1) + stats.downloadSpeedBps) }`
  Because `HomeViewModel` remains in memory while the VPN foreground service is alive, these coroutines continued ticking every single second even when the app was in the background or the screen was off, constantly allocating new 30-element lists and triggering StateFlow emissions.
- **Fix:**
  1. Removed the two background loops from `init`.
  2. Added `onHomeScreenVisible()` and `onHomeScreenHidden()`.
  3. Connected `HomeScreen` to `DisposableEffect(Unit)`: when `HomeScreen` is composed, ticker and history collectors are launched; when `HomeScreen` is disposed (app backgrounded or navigated to another tab), both jobs are immediately cancelled.
  4. Connection duration is calculated on-demand from `ConnectionState.Connected.connectedAt` without continuous background timer spinning.

---

### 6. Busy-Polling Spin in Disconnection
- **File:** `app/src/main/java/app/nebula/vpn/core/connection/DefaultConnectionManager.kt`
- **Root Cause:**
  Lines 122-126 contained a busy polling loop:
  `while (_state.value !is ConnectionState.Disconnected) { kotlinx.coroutines.delay(50) }`
  This woke up coroutine dispatchers 20 times per second during every disconnection and server switch.
- **Fix:**
  Replaced with non-polling reactive flow suspension:
  `_state.first { it is ConnectionState.Disconnected }`

---

### 7. Quick Settings Tile Coroutine Leak
- **File:** `app/src/main/java/app/nebula/vpn/vpn/VpnTileService.kt`
- **Root Cause:**
  `onStartListening()` launched a collection coroutine on `vpnRepository.connectionState` without cancelling it when `onStopListening()` was called. The coroutine stayed active until the service process was killed.
- **Fix:**
  Tracked `listeningJob: Job?`. Cancelled and nulled in `onStopListening()`.

---

### 8. Removal of TCP Fast Open to Prevent SYN Packet Drops
- **File:** `app/src/main/java/app/nebula/vpn/core/builder/SingBoxConfigBuilder.kt`
- **Root Cause:**
  Enabling `tcp_fast_open` caused mobile cellular carriers in Russia (MTS, MegaFon, Beeline, Tele2) to silently drop SYN packets with data payload, causing connection drops, stalled handshakes, and repeated retries that drained battery.
- **Fix:**
  Completely stripped `tcp_fast_open` from sing-box outbound configurations.

---

### 9. Power Profiles & Settings Control
- **Files:**
  - `app/src/main/java/app/nebula/vpn/core/model/PowerProfile.kt`
  - `app/src/main/java/app/nebula/vpn/data/datastore/SettingsDataStore.kt`
  - `app/src/main/java/app/nebula/vpn/data/repository/SettingsRepository.kt`
- **Features:**
  - **Balanced (Default):** 1s interactive speed updates when visible; 0s background (frozen when screen is off); static notification in sleep; `warn` log level.
  - **Battery Saver:** 2s interactive updates; 0s background (fully frozen in sleep); `error` log level; maximum endurance.
  - **High Performance:** 1s interactive updates; 5s background; `info` log level.
  - **Speed in Notification Toggle:** Allows users to turn off speed readouts in notifications entirely, preventing all notification IPC even while screen is on.

---

### 10. In-App Battery & Performance Diagnostics
- **Files:**
  - `app/src/main/java/app/nebula/vpn/ui/tools/ToolsViewModel.kt`
  - `app/src/main/java/app/nebula/vpn/ui/tools/ToolsScreen.kt`
- **Features:**
  - Direct readout of `android.os.Process.getElapsedCpuTime()` formatted as hours/minutes/seconds.
  - Live active thread count (`Thread.activeCount()`).
  - Total process uptime.
  - Interactive Power Profile switcher chips (`Balanced`, `Battery Saver`, `High Performance`).
  - Instant toggle for Notification Speed.
  - Real-time diagnostic parameters summary.

---

### 11. Elimination of `Thread.sleep` in Production
- **File:** `app/src/main/java/app/nebula/vpn/core/proxy/LibboxCore.kt`
- **Root Cause:**
  `Thread.sleep(100)` was called inside `initCommandClient()` retry loop, blocking thread workers synchronously.
- **Fix:**
  Converted to `suspend fun` using non-blocking coroutine `delay(100L)`.

---

### 12. Native Go Runtime Concurrency Cap (`GOMAXPROCS = 2`)
- **Files:**
  - `app/src/main/java/app/nebula/vpn/NebulaApp.kt`
  - `app/src/main/java/app/nebula/vpn/vpn/ProxyVpnService.kt`
- **Root Cause:**
  On 8-core mobile processors, the Go runtime by default sets `GOMAXPROCS = 8`, allowing background goroutines to wake and spin up to 8 CPU cores concurrently under network load.
- **Fix:**
  Invoked `android.system.Os.setenv("GOMAXPROCS", "2", true)` at application startup and service initialization.

---

### 13. System Chronometer for Zero-Wakeup Lockscreen Telemetry
- **File:** `app/src/main/java/app/nebula/vpn/vpn/ProxyVpnService.kt`
- **Fix:**
  Added `.setUsesChronometer(true)` and `.setShowWhen(true)` to notification builders. Android SystemUI renders the ticking connection duration natively on the lockscreen and status bar without dispatching a single app process wakeup.
