# Physical Device Testing Guide & User Stability Collection

**Document:** `docs/DEVICE_TEST.md`  
**Target:** Physical Android Phones (Xiaomi, Samsung, Oppo, Vivo, Huawei, Pixel, Low-end)  
**Date:** November 2026  
**Purpose:** Step-by-step test script for validating Nebula VPN on real hardware in the field and collecting stability diagnostics.

---

## 1. Test Environment & Prerequisites

1. Obtain the release APK matching your phone's architecture:
   - 64-bit phones (standard modern): `app-arm64-v8a-release.apk`
   - 32-bit / legacy budget phones: `app-armeabi-v7a-release.apk`
   - Universal (all devices): `app-universal-release.apk`
2. Install via ADB or file manager:
   ```bash
   adb install -r app-arm64-v8a-release.apk
   ```

---

## 2. Test Execution Matrix (Step-by-Step)

### Step 1: Clean Install & First Launch (Onboarding)
1. Launch Nebula.
2. Verify onboarding screens render cleanly (edge-to-edge, smooth 60–120 FPS).
3. If on an aggressive OEM (Xiaomi HyperOS, Samsung One UI, Oppo ColorOS, Vivo, Huawei), check if the **Background Stability Guide** appears.
4. Tap **Open Battery Settings** and confirm it jumps to the phone's native battery optimization screen without crashing.

### Step 2: Server List & Virtualization Stress Test
1. Import a large subscription (200–500+ nodes) via subscription URL or clipboard.
2. Navigate to **Servers** tab.
3. Rapidly expand and collapse the subscription accordion.
4. Rapidly fling-scroll through the entire list from top to bottom.
5. **Expected result:** Zero UI stutter or freeze; 60–120 FPS fluid scrolling; no "App Not Responding" popup.

### Step 3: sing-box Mode Connect & Speed Test
1. Select a working proxy profile (VLESS Reality / Shadowsocks / Trojan).
2. Tap the central Connect button on the **Home** screen.
3. Accept Android VPN Permission dialog if prompted.
4. Verify persistent foreground notification appears immediately with status and active speeds.
5. Open browser, load `https://cloudflare.com` and YouTube.
6. Verify speed notifications update smoothly every 2 seconds while screen is awake.

### Step 4: Tor Mode & Bridge Configuration
1. Disconnect VPN and navigate to **Settings** -> **Active Engine** -> select **Tor**.
2. Read and accept the Tor Threat Model dialog.
3. Return to **Home** and tap Connect.
4. Verify bootstrapping progress bar advances (5% -> 50% -> 100%) and notification displays "Connected to Tor Network".
5. Verify fail-closed security: UDP / QUIC traffic is dropped cleanly without leaks.

### Step 5: Screen-Off & Deep Sleep Soak Test (30–60 Minutes)
1. With the VPN connected (sing-box or Tor), lock the phone screen.
2. Leave the device undisturbed on battery power for at least 30 minutes.
3. Wake up the phone and unlock.
4. **Verification points:**
   - Did the key icon remain in the Android status bar?
   - Open Nebula: did the UI resume immediately without an ANR dialog?
   - Verify network connectivity through the tunnel is immediately active without reconnect delay.

### Step 6: 20-Cycle Engine & Tab Stress Test
1. Rapidly switch between tabs: Home -> Servers -> Routing -> Tools -> Settings (20 cycles).
2. Rapidly toggle Connect and Disconnect (10 cycles).
3. Switch active engine from sing-box to Tor, connect, then switch back to sing-box and connect.
4. **Verification points:** Zero crashes, zero ANR dialogs, memory footprint remains bounded.

---

## 3. How to Collect & Export Stability Reports from Users

If a user reports an app freeze, slowdown, or background disconnect on their phone, instruct them to perform the following steps (takes 10 seconds):

1. Open **Nebula VPN**.
2. Navigate to the **Tools** tab (or **Settings** -> **Stability & Diagnostics**).
3. Under the **Stability & Diagnostics** card, check the status badge:
   - **System Running Smoothly (Green):** Normal operation.
   - **UI Freeze / ANR Detected (Red):** A UI hang was captured by Android or the watchdog.
   - **Terminated by System (Yellow):** Android killed the process for CPU/RAM limits.
   - **Background Execution Restricted (Blue):** OEM battery killer is active.
4. Tap the **Copy Stability Report** button (or **Share Report**).
5. Paste the generated text into Telegram, GitHub issue, or email.

### What the Sanitized Stability Report Contains:
- Device Model, Manufacturer, Android Version, RAM, and Primary ABI.
- Active Engine (`SING_BOX` or `TOR`) and power profile.
- System Exit Reasons from Android's `ApplicationExitInfo` (reason codes, PSS/RSS memory, and sanitized ANR stack traces).
- Watchdog stall dumps (any main-thread hangs > 1.5 seconds).
- Sanitized recent logs (all server IPs masked to `x.x.x.x`, URLs redacted, bridge lines masked, zero secrets).
