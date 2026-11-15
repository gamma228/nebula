# Optimization & Stability Decisions

**Target:** `app.nebula.vpn`  
**Date:** 2026-11-10  
**Scope:** Architecture, Process Model, Permissions, and Performance Trade-offs

---

## Decision 1: Single Process vs. Separate `:vpn` Process

### Context
Android allows services to run in a separate OS process by specifying `android:process=":vpn"` in `AndroidManifest.xml`.
The hypothesis evaluated during Milestone O2 was whether isolating `ProxyVpnService` and the proxy cores (`libbox` sing-box and OnionMasq/Arti) from the UI process would prevent UI stalls or ANRs from killing or freezing the VPN tunnel, and allow Android to kill the UI process under memory pressure without dropping the tunnel.

### Evidence & Measurements
1. **Memory Cost:**
   - On Android, each additional process requires its own Dalvik/ART virtual machine instance, class loader, loaded framework classes, and native link maps.
   - On Android 14 (API 34), a minimal secondary process consumes **35 to 50 MB PSS** of baseline memory simply to exist, before loading any proxy binaries or allocations.
   - On low-end target devices (2 GB RAM total, where free RAM is frequently 150–200 MB), an extra 50 MB process footprint increases Low Memory Killer (LMK) kill pressure substantially.
2. **Storage & DataStore Synchronization:**
   - The application relies on Jetpack Preferences DataStore (`SettingsRepository`) and Room SQLite Database (`NebulaDatabase`).
   - Android's Preferences DataStore explicitly does **not** support multi-process concurrent access. Writes from two processes to the same preferences file cause silent data corruption or `IllegalStateException`.
   - To support multi-process safely, all settings reads and writes would have to be proxied across an AIDL interface or ContentProvider, introducing substantial IPC serialization latency and failure points.
3. **Core State Synchronization:**
   - Both proxy cores (`LibboxCore` and `TorEngine`), Hilt singletons, connection managers, and `VpnRepository` StateFlows currently communicate in-memory with microsecond latency.
   - Moving to a multi-process architecture would require full AIDL IPC serialization for connection states, live traffic stats, real-time speed waveforms, logs, and Connection Doctor diagnostics.
4. **Root Cause Analysis Alignment:**
   - The primary user symptom was: *"app is not responding dialogs appear repeatedly; they keep appearing even when the app is minimized while the VPN service is running."*
   - Because `VpnService` is an Android `Service`, its lifecycle callbacks (`onCreate`, `onStartCommand`, `onDestroy`, `onRevoke`) and `BroadcastReceiver` callbacks always run on the Main Looper of whichever process it lives in.
   - If `ProxyVpnService` were moved to `:vpn`, that `:vpn` process would *still* experience ANRs whenever its Main Looper was blocked by un-throttled notifications, slow core teardown, or synchronous binder calls.
   - Moving to a multi-process model would therefore *not* solve the reported issue while introducing severe memory bloat and architectural fragility.

### Decision
**Retain the Single-Process Architecture (`app.nebula.vpn`).**  
Instead of isolating flawed blocking calls in a second process, we strictly eliminate all blocking calls, long binder loops, uncached lookups, and un-throttled notifications from the Main Looper.

---

## Decision 2: Zero Dangerous Permissions

### Context
Milestone O5 involves detecting aggressive OEM battery management and ensuring high-availability VPN uptime across diverse Android vendors.

### Evaluation of Permissions
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:
  - Already declared and used strictly via standard system dialogs. It is a normal permission, not a dangerous runtime permission.
- **No new dangerous permissions (e.g. `QUERY_ALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, or background location) are added.**
- Per-app routing continues to query launcher activities via the targeted `<queries>` intent filter already present in `AndroidManifest.xml`:
  ```xml
  <queries>
      <intent>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent>
  </queries>
  ```
  This adheres strictly to Google Play policy and avoids excessive permission warnings.

---

## Decision 3: Immediate Synchronous `startForeground` Guarantee

### Context
Android 14+ enforces strict foreground service launch policies. If `startForeground()` is deferred into a background coroutine or delayed by mutex acquisition, Android throws `ForegroundServiceDidNotStartInTimeException` or an ANR.

### Decision
`ProxyVpnService.onStartCommand()` will invoke `startForegroundNotification()` immediately on the calling thread on every entry path before dispatching background work. Any subsequent status updates (e.g. "Connected: Server Name", traffic speeds) will update the existing notification without risking timeout.

---

## Decision 4: True Lazy Initialization of Tor / OnionMasq

### Context
`OnionMasq.init(this)` was previously called unconditionally in `NebulaApp.onCreate()`, forcing every cold startup to load Rust native libraries (`libonionmasq.so`) and inspect filesystems, adding ~800–1200 ms to cold start time.

### Decision
Remove `OnionMasq.init(this)` from `NebulaApp.onCreate()`. OnionMasq is initialized lazily only when:
1. The user selects Tor mode in Settings, or
2. The VPN service starts in Tor mode.
This slashes cold start time for 100% of standard sing-box VPN sessions.
