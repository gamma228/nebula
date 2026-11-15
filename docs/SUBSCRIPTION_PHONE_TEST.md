# Nebula Subscription Import Phone Verification Guide

This guide provides step-by-step instructions for verifying subscription import on a physical Android device, particularly for panels enforcing Device Limits or Client Identification (such as Remnawave, Marzban, 3x-ui, and Hiddify).

---

## 1. Install the New Release APK

1. Transfer the signed release APK to your Android device:
   - For 64-bit ARM devices (most modern phones): `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
   - Universal APK (compatible with all devices): `app/build/outputs/apk/release/app-universal-release.apk`
2. Install via ADB or file manager:
   ```bash
   adb install -r app/build/outputs/apk/release/app-universal-release.apk
   ```
3. Launch Nebula.

---

## 2. Import the Subscription Link

Nebula now supports multiple entry points:
- **Pasted URL:** Open the **Servers** tab, tap `+` (Add), select **Add Subscription**, paste your link, and tap **Import**.
- **Share Intent:** Share the subscription link text directly from your browser, messenger, or notes app into Nebula. A confirmation dialog will appear with the URL token safely masked.
- **Deep Links:** Links with schemes `nebula://subscribe?url=...`, `clash://install-config?url=...`, or `sing-box://import-remote-profile?url=...` open Nebula directly.
- **QR Code / File:** Scan the QR code or import a local `.json` / `.yaml` / `.txt` file.

---

## 3. Running the Subscription Inspector

To diagnose any subscription import without guessing:
1. Open the **Tools** tab in Nebula.
2. Under Diagnostics, tap **Subscription Inspector** (or tap the **Tune** icon on the subscription header on the Servers screen and choose **Inspect**).
3. Paste the subscription URL and select a User-Agent preset (e.g. `Nebula`, `sing-box`, `v2rayNG`, or `ClashMeta`).
4. Tap **Run Inspector**.

### How to Read the Inspector Output
- **HTTP Status:** 
  - `200 OK`: Successful fetch.
  - `401 / 403`: Link expired, revoked, or unauthorized.
  - `404`: Invalid URL or subscription deleted.
  - `429`: Rate limit exceeded (wait before retrying).
- **Format Detected:** Indicates whether the content was identified as `SING_BOX_JSON`, `XRAY_JSON`, `BASE64_SHARE_LINKS`, `SIP008_SS`, `CLASH_YAML`, `HTML_PAGE`, or `STUB_WARNING`.
- **HWID & Device Headers:**
  - `x-hwid-active: true` — The panel requires device identification.
  - `x-hwid-limit: true` — The subscription's device slot limit is active.
  - `x-hwid-not-supported: true` — The current client request was not recognized as an authorized client.
- **Node Breakdown:** Total count of valid proxy nodes parsed vs. skipped nodes.
- **Masked Snippet:** First 200 characters of the payload with all tokens, UUIDs, passwords, and server IPs strictly redacted (`[TOKEN]`, `[UUID]`, `[REDACTED_IP]`).

---

## 4. Handling the Single-Device Limit (Remnawave / Other Panels)

If the subscription slot is currently occupied by another client:

### Option A: Send Device Headers (HWID) from Nebula
1. In the **Servers** tab, locate the subscription accordion card.
2. Tap the **Tune / Settings** icon (`⚙` / sliders) in the subscription header.
3. Toggle **Send Device Headers (HWID)** to **ON**.
   > **Privacy Note:** Nebula generates a randomized, isolated installation UUID (`crypto/rand`). Nebula NEVER accesses or sends your `ANDROID_ID`, IMEI, MAC address, serial number, or phone model. You can tap **Regenerate HWID** at any time to issue a new random identifier.
4. Select **User-Agent Preset**: choose **Nebula** or **sing-box**.
5. Tap **Save & Refresh**.

### Option B: Reset Device Slot in Provider Dashboard
If the panel reports a device slot conflict (`⚠️ Device limit reached` or 3 stub nodes pointing to `0.0.0.0:1`):
1. Open your VPN provider's Telegram bot or personal web dashboard.
2. Select **Reset Devices / Disconnect Other Devices** to free the active slot.
3. In Nebula, tap **Refresh Subscription**.
4. The real proxy nodes (VLESS Reality / Trojan / Shadowsocks) will now populate and replace any stub warnings.

---

## 5. Sharing Diagnostics Safely

If you encounter any issues during testing:
1. In the subscription card or error dialog, tap **Copy Sanitized Debug Info**.
2. Paste the result into the chat.
3. **Verified Safe:** The debug bundle automatically strips all secret tokens, passwords, server IP addresses, and the subscription URL itself.
