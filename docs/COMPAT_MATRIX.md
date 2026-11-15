# Android Compatibility Matrix & Version Audit

**Project:** Nebula VPN (`app.nebula.vpn`)  
**Target SDK:** 35 (Android 15)  
**Minimum SDK:** 26 (Android 8.0 Oreo)  
**Date:** November 2026  
**Status:** Milestone O7 Completed  

---

## 1. Android Version Compatibility Matrix

| Android Version | API Level | Foreground Service & Notifications | Background Restrictions & Doze | Storage & Package Visibility | 16 KB Page Alignment | Status | Evidence / Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android 8.0 - 8.1 (Oreo)** | 26–27 | Persistent Notification Channel mandatory; `startForeground()` within 5s. | Doze Mode & App Standby introduced. | Standard storage permissions; full package visibility. | N/A (4 KB default) | **REAL** | Verified on API 26 emulator; channel `nebula_vpn_status` created in `NebulaApp.onCreate()`. |
| **Android 9.0 (Pie)** | 28 | `FOREGROUND_SERVICE` normal permission declared in Manifest. | `ActivityManager.isBackgroundRestricted()` & `appStandbyBucket` introduced. | Cleartext HTTP blocked by default (DoH uses TLS). | N/A (4 KB default) | **REAL** | Manifest contains `FOREGROUND_SERVICE`. API 28 standby bucket checked in `StabilityDiagnosticsCollector`. |
| **Android 10 (Q)** | 29 | Foreground service types introduced. | Always-On VPN & Lockdown status APIs (`isAlwaysOn`, `isLockdownEnabled`). | Scoped Storage introduced for shared media. | N/A (4 KB default) | **REAL** | `VpnLockdownDetector` uses official API 29+ methods with fallback. |
| **Android 11 (R)** | 30 | Foreground service location restrictions. | `ApplicationExitInfo` API for process termination & ANR trace analysis. | Package visibility restrictions; `<queries>` tag in Manifest. | N/A (4 KB default) | **REAL** | `StabilityDiagnosticsCollector.readHistoricalExitReasons` reads exit codes & traces on API 30+. |
| **Android 12 - 12L (S)** | 31–32 | Background foreground service start restrictions (`ForegroundServiceStartNotAllowedException`). | Phantom process killer (max 32 child procs); `subReason` in `ApplicationExitInfo`. | Splash Screen API mandatory. | N/A (4 KB default) | **REAL** | Service starts strictly from foreground UI or via user Tile; `subReason` read via safe reflection. |
| **Android 13 (Tiramisu)** | 33 | `POST_NOTIFICATIONS` runtime permission required for status bar notices. | Per-app language settings support (`android:localeConfig`). | Granular media permissions. | N/A (4 KB default) | **REAL** | Manifest declares `POST_NOTIFICATIONS`; service degrades gracefully if notification denied. |
| **Android 14 (UpsideDownCake)** | 34 | `foregroundServiceType="specialUse"` with `<property name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" />` strictly mandatory. Synchronous `startForeground` in `onStartCommand` enforced. | `reason=9 (EXCESSIVE RESOURCE USAGE)` kills background services exceeding CPU limits. | Partial screen sharing & photo picker. | N/A (4 KB default) | **REAL** | Manifest property added; `ProxyVpnService.onStartCommand` calls `startForeground` synchronously on line 198. |
| **Android 15 (VanillaIceCream)** | 35 | Strict 16 KB page-size compatibility for all 64-bit native `.so` shared libraries. | Enhanced App Freezer; foreground service timeout limits. | Edge-to-edge layout default enforced. | **16 KB (0x4000) VERIFIED** | **REAL** | `llvm-readelf -l` verified `Align: 0x4000` (16 KB) for `libbox.so`, `libonionmasq_mobile.so`, and all dependencies. |

---

## 2. 16 KB Page-Size Native Library Audit (Android 15+)

Android 15 introduces support for devices configured with 16 KB page sizes. Any native shared library (`.so`) compiled with 4 KB alignment will fail to load (`dlopen` returns `ELF load command address/offset not page-aligned`).

### Verified ELF Segments (`arm64-v8a`)

Command run:
```powershell
llvm-readelf.exe -l lib/arm64-v8a/*.so | Select-String "LOAD"
```

1. **`libbox.so` (sing-box v1.14.4 Go Core):**
   - `LOAD 0x000000 ... Align: 0x4000` (16 KB)
   - `LOAD 0x2244640 ... Align: 0x4000` (16 KB)
   - `LOAD 0x42359c0 ... Align: 0x4000` (16 KB)
   - `LOAD 0x4a24480 ... Align: 0x4000` (16 KB)
   - **Verdict:** **100% COMPLIANT (0x4000)**

2. **`libonionmasq_mobile.so` (Arti + Onionmasq Rust Core):**
   - `LOAD 0x000000 ... Align: 0x4000` (16 KB)
   - `LOAD 0x197b700 ... Align: 0x4000` (16 KB)
   - `LOAD 0x2431c40 ... Align: 0x4000` (16 KB)
   - `LOAD 0x25543e0 ... Align: 0x4000` (16 KB)
   - **Verdict:** **100% COMPLIANT (0x4000)**

3. **AndroidX Dependencies (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`):**
   - Built with Android NDK r28 with `-z max-page-size=16384`.
   - **Verdict:** **100% COMPLIANT (0x4000)**

---

## 3. OEM Power Management & Background Killers Matrix

| Manufacturer / Skin | Aggressiveness | Custom Killer Behavior | Solution Implemented | Safe Intent Fallback Target |
| :--- | :--- | :--- | :--- | :--- |
| **Xiaomi / Redmi / POCO** (HyperOS / MIUI) | **Extreme** | Kills background services when screen locks; disables autostart; drops network sockets in battery saver. | `OemBackgroundGuideDialog`: directs user to set battery to "No restrictions", enable Autostart, and lock card in recents. | `miui.intent.action.POWER_HIDE_MODE_APP_LIST` -> `com.miui.securitycenter/com.miui.powercenter.PowerSettings` -> Standard Battery Settings |
| **Samsung** (One UI 5/6/7) | **High** | Places apps in "Sleeping apps" or "Deep sleeping apps"; throttles VPN background tickers. | Detects One UI; instructs user to add Nebula to "Never sleeping apps" and set battery to "Unrestricted". | `com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity` -> Standard App Details Settings |
| **Oppo / Realme / OnePlus** (ColorOS / OxygenOS) | **High** | Aggressive "Quick freeze" in background; disables background activity by default. | Detects ColorOS; guides user to enable "Allow background activity" and "Allow auto-startup". | `com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity` -> `com.oplus.battery` -> Standard Battery Settings |
| **Vivo / iQOO** (FuntouchOS / OriginOS) | **High** | Flags high background power consumption; kills non-whitelisted VPN tunnels after 10 minutes screen-off. | Detects Vivo; prompts user to enable "High background power consumption". | `com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity` -> `com.vivo.permissionmanager` -> Standard Battery Settings |
| **Huawei / Honor** (EMUI / MagicOS) | **Extreme** | "App Launch" auto-management kills VPN; deep sleep freezes network sockets. | Detects EMUI; instructs user to change App Launch to "Manage manually" (Auto-launch, Secondary launch, Run in background). | `com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity` -> Standard Battery Settings |
| **Google Pixel** (Stock Android) | **Low** | Standard Doze & App Standby; respects foreground services with low CPU. | Uses standard `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. | `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
