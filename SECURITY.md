# Security Policy & Architecture

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 2.1.x   | :white_check_mark: |
| 2.0.x   | :x:                |
| < 2.0   | :x:                |

---

## Security Architecture & Privacy Safeguards

Nebula is designed under a **Zero Trust / Zero Surveillance** model for users residing in or traveling through high-surveillance and adverse network environments.

### 1. Zero Telemetry & Privacy Guarantees
- **No Analytics / Tracking SDKs:** Nebula contains zero Google Analytics, Firebase Analytics, Crashlytics, adjust, AppsFlyer, or commercial tracking libraries.
- **No Google Play Services Dependency:** Core features (including offline QR scanning and background updates) operate entirely with on-device libraries (CameraX, ZXing, WorkManager).
- **Cleartext Traffic Policy:** Cleartext traffic is disabled globally by default (`android:usesCleartextTraffic="false"`). Subscription downloads enforce HTTPS with strict TLS validation.
- **Auto-Redacting Diagnostics:** Diagnostic export bundles automatically scrub passwords, tokens, UUIDs, and private keys (`pbk`, `sid`) from log dumps before export.

### 2. Cryptographic Storage & Android Keystore
- **SecretBox Encryption:** All sensitive configuration preferences (including proxy credentials, private keys, and user tokens) are stored in DataStore encrypted via the Android Keystore (`SecretBox`). Keys are stored in hardware-backed TEE / StrongBox whenever available on the host device.
- **Database Sanitization:** Room database holds local routing rules, server profiles, and usage statistics. Cleartext passwords or private keys are never logged to logcat.

### 3. Traffic Leak & DNS Hijacking Shields
- **Strict DNS Hijacking:** DNS queries on UDP/TCP port 53 are redirected to the sing-box core DNS router.
- **Remote DoH Tunneling:** Remote DNS queries utilize DNS-over-HTTPS (DoH)Detours routed exclusively through the encrypted proxy tunnel (`dns-remote` -> `detour: "select"`).
- **Kill Switch & System Lockdown:** Supports native Android `VpnService` lockdown to block any non-VPN network traffic when the tunnel disconnects.

### 4. Application Hardening
- **FLAG_SECURE:** Optional toggle to prevent OS screenshots and conceal application windows in the Android recent-tasks switcher.
- **Biometric Authentication Gate:** Optional biometric (fingerprint/face) unlock prompted upon resuming the app.
- **Panic Wipe:** One-tap emergency purge that securely unlinks and truncates all database records, Keystore entries, and cached files.
- **R8 Minification & Code Obfuscation:** Production release builds are fully minified and obfuscated with R8, stripping unused code, metadata, and debugging artifacts.

---

## Reporting a Vulnerability

If you discover a security vulnerability or potential privacy leak in Nebula, please report it privately:

1. **Email:** `security@nebula-vpn.app` (or submit a private security advisory on GitHub).
2. **PGP Key:** Available upon request or via keyserver for encrypted communication.
3. **Response Window:** We acknowledge reports within 48 hours and strive to deliver invariant-level security patches within 7 days.
4. **Public Disclosure:** Please allow reasonable time for a patch release before public disclosure.
