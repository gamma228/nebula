# Nebula - Next-Generation Android Proxy & VPN Client

Nebula is a modern, high-performance Android proxy and VPN client powered by the official [sing-box](https://github.com/SagerNet/sing-box) core (`libbox`). Engineered with 100% Kotlin and Jetpack Compose Material 3, Nebula provides state-of-the-art leak protection, split tunneling, deep customization, and zero surveillance.

---

## Key Features

- **Real Proxy Core (`libbox` v1.14.4):**
  - Integrated via gomobile with native TUN hand-off (`VpnService.Builder` constructed dynamically inside the platform callback).
  - Protocols supported: **VLESS** (Reality / Vision), **VMess**, **Trojan**, **Shadowsocks**, **Hysteria 2**, **WireGuard**, Direct, and Block outbounds.
  - Proxy groups: URLTest (auto-fallback / lowest latency), Selector, and Load Balance.

- **Universal Import & Subscriptions:**
  - Share link import (`vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`).
  - Base64 subscription lists.
  - sing-box JSON and Xray/V2Ray JSON format support.
  - Clash & Mihomo YAML configurations (proxies, proxy-groups, rule-providers).
  - Offline QR scanner powered by CameraX and ZXing (100% on-device, zero Google Play Services).
  - Background subscription updates via WorkManager with quota inspection.

- **Advanced Routing & Split Tunneling:**
  - Per-app routing modes: Global, Bypass (Blacklist), and Forward (Whitelist) with strict mutual exclusivity.
  - Mass package name import parser.
  - Domain suffix, keyword, regex, IP CIDR, and sing-box `rule_set` routing with drag-and-drop rule ordering.
  - Direct routing presets for popular censorship environments.

- **Zero Surveillance & Hardened Security:**
  - Zero analytics, zero telemetry, zero advertising libraries, and zero Google Play Services dependencies.
  - `cleartextTrafficPermitted=false` and `allowBackup=false`.
  - Android Keystore AES-GCM encrypted preferences (`SecretBox`).
  - Biometric lock on application resume.
  - `FLAG_SECURE` screen protection against task-switching snapshots and screen recording.
  - Instant Panic Wipe: one-tap emergency purge of all profiles, credentials, logs, and database records.

- **Deep Customization Engine:**
  - Built-in visual themes: *OLED Crimson & Shadow*, *Cyber Violet & Ash*, *Monochrome Minimalist*.
  - Interactive Custom Theme Creator with live color palette builder.
  - Custom app wallpaper support with adjustable blur and opacity overlays.
  - 5 dynamic launcher icon aliases (`.Default`, `.Crimson`, `.Violet`, `.Monochrome`, `.Stealth`).

- **Diagnostics & Connection Doctor:**
  - Real-time Connection Doctor performing multi-point diagnosis: Core binary, Config validity, Local DNS, Gateway reachability, Remote server handshake, and TUN routing interface.
  - Active live connections monitor and live log buffer with level filtering.

- **System Integration:**
  - Persistent foreground notification with live upload and download speed meters.
  - Android Quick Settings `VpnTileService` for instantaneous one-tap connection.
  - Homescreen app shortcuts.

- **100% 5-Locale Parity:**
  - Complete native translations for: **English** (`en`), **Russian** (`ru`), **Ukrainian** (`uk`), **Simplified Chinese** (`zh-CN`), and **German** (`de`).
  - Verified by strict automated unit test key parity assertions.

---

## Architecture & Technology Stack

- **Language:** 100% Kotlin
- **UI Framework:** Jetpack Compose + Material 3 (Edge-to-Edge)
- **Architecture:** Clean Architecture + MVVM / MVI pattern
- **Dependency Injection:** Hilt
- **Local Persistence:** Room Database (6 entities, 6 DAOs) + DataStore Preferences
- **Background Tasks:** Android WorkManager
- **Core Engine:** SagerNet `libbox.aar` (v1.14.4) built with Go 1.22.4 & Android NDK 28
- **Target SDK:** 35 (Android 15) | **Min SDK:** 26 (Android 8.0)
- **ABI Coverage:** `arm64-v8a`, `armeabi-v7a`, `x86_64` (emulator)

---

## Building from Source

### Prerequisites
1. OpenJDK 17 (e.g. Eclipse Temurin 17)
2. Android SDK (API 35, Build Tools 35.0.0, NDK 28.0.13004108)
3. Go 1.22+ and `gomobile` (for rebuilding `libbox.aar` if desired)

### Setup
Create `local.properties` in the project root:
```properties
sdk.dir=/path/to/Android/Sdk
ndk.dir=/path/to/Android/Sdk/ndk/28.0.13004108
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run All Unit Tests
```bash
./gradlew testDebugUnitTest testReleaseUnitTest
```

### Build Signed Release APK
To build release APKs signed with your release keystore, provide the signing properties in `local.properties` or environment variables:
```properties
nebula.release.keystore=/path/to/release.jks
nebula.release.store_password=YourStorePassword
nebula.release.key_alias=nebula
nebula.release.key_password=YourKeyPassword
```
Then run:
```bash
./gradlew assembleRelease
```
The output APKs will be located in `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk`
- `app-armeabi-v7a-release.apk`
- `app-universal-release.apk`

---

## Verification & Reproducibility
Refer to [VERIFICATION.md](VERIFICATION.md) for full execution logs, live emulator test outputs, and `apksigner` cryptographic signature verification results.
Refer to [PROGRESS.md](PROGRESS.md) for the complete phase-by-phase development record.

---

## License

Nebula is licensed under the **GNU General Public License v3.0 (GPL-3.0)** due to the inclusion of sing-box / libbox.
See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full license texts and acknowledgments.
