# Nebula - Next-Generation Multi-Platform Proxy & VPN Client (Android & Windows)

Nebula is a modern, high-performance proxy and VPN client for Android and Windows powered by the official [sing-box](https://github.com/SagerNet/sing-box) core. Engineered with 100% Kotlin, Jetpack Compose, and Compose Multiplatform for Desktop, Nebula provides state-of-the-art leak protection, split tunneling, deep customization, and zero surveillance.

---

## Key Features

- **Real Proxy Core (sing-box v1.14.4):**
  - **Android:** Integrated via gomobile (`libbox.aar`) with native Cgo TUN hand-off (`VpnService.Builder`).
  - **Windows:** Native official `sing-box.exe` child process supervision with Wintun kernel driver (`wintun.dll`) and unprivileged WinINET system proxy mode.
  - Protocols supported: **VLESS** (Reality / Vision), **VMess**, **Trojan**, **Shadowsocks**, **Hysteria 2**, **TUIC v5**, Direct, and Block outbounds.
  - Real-time traffic polling via sing-box Clash API (`/connections` and `/traffic`) and Linux kernel metrics.

- **Universal Import & Subscriptions:**
  - Share link import (`vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `tuic://`).
  - Base64 subscription lists with automatic decoding.
  - sing-box JSON and Xray/V2Ray JSON configuration format support.
  - Clash & Mihomo YAML configurations (proxies, proxy-groups, rule-providers).
  - Offline QR scanner on Android powered by CameraX and ZXing.
  - Desktop clipboard paste, file drag-and-drop, and deep-link protocol handlers.

- **Advanced Routing & Split Tunneling:**
  - Dedicated Routing manager with rules list and priority re-ordering.
  - Smart multi-line rule detection (`SmartRuleParser`): pastes and auto-classifies domains, suffixes (`.ru`), IPv4 CIDRs, and application process names.
  - Live rule conflict and shadowing detection (`RuleConflictDetector`).
  - **Android Split Tunneling:** Per-app routing modes (Global, Bypass / Blacklist, Forward / Whitelist) with mass package import.
  - **Windows Split Tunneling:** Per-process routing by process name (`process_name`) and executable path (`process_path`) with interactive running process selector.
  - Direct routing presets for popular censorship environments (`Russia Direct`, `AdBlock`, `Socials Proxy`).
  - Anti-censorship QUIC (UDP 443) reject rules to force immediate TCP HTTP/2 fallback.

- **Zero Surveillance & Hardened Security:**
  - Zero analytics, zero telemetry, zero advertising libraries, and zero tracking dependencies.
  - Android Keystore AES-GCM encrypted preferences (`SecretBox`).
  - Windows Data Protection API (DPAPI) encryption at rest for profiles, subscriptions, and settings (`%APPDATA%\Nebula\`).
  - WinINET registry and system proxy crash resilience: backup file (`system_proxy_state.json`) guarantees immediate restoration on disconnect, crash, or reboot.
  - Biometric lock on Android; single-instance local socket mutex guard on Windows.
  - `FLAG_SECURE` screen protection against task-switching snapshots on Android.
  - Instant Panic Wipe: one-tap emergency purge of all profiles, credentials, logs, and database records.

- **Deep Customization Engine:**
  - Built-in visual themes: *OLED Crimson & Shadow*, *Cyber Violet & Ash*, *Monochrome Minimalist*.
  - Full AMOLED (pure `#000000`), Dark Mode (charcoal slate), and Light Mode support.
  - Interactive Custom Theme Creator with live color palette builder.
  - Custom app wallpaper support with adjustable blur and opacity overlays.

- **Diagnostics & Connection Doctor:**
  - Real-time Connection Doctor performing multi-point diagnosis: Core binary hash integrity, Config validity, Local DNS, Gateway reachability, Remote server handshake, and TUN routing interface.
  - True end-to-end data-path reachability testing via Cloudflare HTTPS 204 probe (never falsely blames disconnected servers).
  - One-tap Redacted Diagnostics Bundle export (masking passwords, UUIDs, private keys).
  - Active live connections monitor and live log buffer with level filtering.

- **100% 5-Locale Parity:**
  - Complete native translations for: **English** (`en`), **Russian** (`ru`), **Ukrainian** (`uk`), **Simplified Chinese** (`zh-CN`), and **German** (`de`).
  - Verified by strict automated unit test key parity assertions across both platforms.

---

## Architecture & Technology Stack

```
[Nebula Project Root]
 ├── :core                <-- Pure Kotlin JVM Shared Module (Zero Android dependencies)
 │    ├── model           (ProxyProfile, RoutingRule, ConnectionState, TrafficStats, ThemeConfig)
 │    ├── parser          (LinkParser, ClashYamlParser, SubscriptionDecoder, SmartRuleParser)
 │    ├── builder         (SingBoxConfigBuilder)
 │    ├── proxy           (LatencyTester, PingTester, OptimizationFlags)
 │    ├── doctor          (DiagnosticCheckItem, DiagnosticVerdict, DoctorLogicCore)
 │    ├── windows         (WindowsCoreController, WindowsSystemProxy, SingBoxBinaryVerifier, ProcessHelper)
 │    └── contract        (CoreController, StorageRepository, SystemIntegration, TrafficStats)
 │
 ├── :app                 <-- Android Application (Jetpack Compose + Hilt)
 │    ├── Room Database (6 entities, 6 DAOs) + Keystore SecretBox DataStore
 │    ├── LibboxCore (libbox.aar gomobile TUN passing)
 │    └── Material 3 Edge-to-Edge UI
 │
 └── :desktop             <-- Windows Desktop Application (Compose Multiplatform JVM)
      ├── Windows child process supervision (sing-box.exe amd64 + wintun.dll)
      ├── Windows DPAPI encryption via JNA
      ├── WinINET System Proxy management & crash recovery
      ├── Desktop System Tray, Notifications & Single Instance Mutex
      └── Compose Multiplatform Desktop UI (Material 3 Dark/AMOLED/Light)
```

---

## Windows Desktop Client

### Pre-requisites
- Windows 10 (version 1809+) or Windows 11 (64-bit).
- Java 17+ (e.g. Eclipse Temurin 17) for building from source. Packaged installers and the portable ZIP bundle their own self-contained JRE.

### Running the Desktop Client
From the repository root:
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat :desktop:run
```

### Packaging Windows Installers & Portable Distribution
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"

# Build standalone unpacked application:
.\gradlew.bat :desktop:createDistributable

# Build Windows EXE installer:
.\gradlew.bat :desktop:packageExe

# Build Windows MSI installer:
.\gradlew.bat :desktop:packageMsi

# Build Windows Portable ZIP:
.\gradlew.bat :desktop:packageZip
```

### Built Desktop Artifacts
The build outputs are placed in `desktop/build/compose/binaries/main/`:
- **EXE Installer:** `desktop/build/compose/binaries/main/exe/Nebula-1.2.0.exe` (~100 MB, self-contained)
- **MSI Installer:** `desktop/build/compose/binaries/main/msi/Nebula-1.2.0.msi` (~99 MB, enterprise-ready)
- **Portable ZIP:** `desktop/build/compose/binaries/main/Nebula-v1.2.0-windows-portable.zip` (~129 MB, extract and run `Nebula.exe` anywhere)
- **Unpacked Distributable:** `desktop/build/compose/binaries/main/app/Nebula/Nebula.exe`

### Windows Connection Modes & Privileges
1. **System Proxy Mode (Default, Recommended for general browsing):**
   - Requires **NO administrator privileges**.
   - Runs `sing-box.exe` with a local mixed HTTP/SOCKS inbound on `127.0.0.1`.
   - Modifies `HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings` via JNA and broadcasts `InternetSetOption` (39 & 37).
   - Automatically restored on disconnect, normal exit, or crash recovery.
2. **TUN Mode (Full-system tunnel, Recommended for UDP/gaming/global traffic):**
   - Requires administrator privileges (UAC prompt on first start) to load the kernel-level `wintun.dll` network driver.
   - Sing-box sets up `wintun` interface with `auto_route: true` and `strict_route: true`.

### Code Signing & Antivirus Notes
- **Code Signing:**
  The current build is compiled without an Authenticode certificate for local development and testing. When running `Nebula-1.2.0.exe` or installing `Nebula-1.2.0.msi` on Windows, Windows Defender SmartScreen may display an *"Unknown Publisher"* warning.
  - To bypass SmartScreen: Click **"More info"** -> **"Run anyway"**.
  - To plug in an official code signing certificate:
    Set the signing arguments in `desktop/build.gradle.kts`:
    ```kotlin
    windows {
        // certificate.set(file("path/to/authenticode.pfx"))
        // certificatePassword.set("...")
    }
    ```
- **Antivirus Exclusions:**
  Like most open-source proxy and VPN tools utilizing `sing-box` and `wintun.dll`, heuristic scanners in third-party antivirus software may flag the binaries due to their low-level network redirect and TUN interface drivers. If necessary, whitelist the installation directory `%LOCALAPPDATA%\Nebula` or the portable folder.

### Updating sing-box and Wintun Binaries
The Windows client pins SHA-256 hashes of `sing-box.exe` and `wintun.dll` in `SingBoxBinaryVerifier.kt` to prevent binary tampering:
1. Download official `sing-box-*-windows-amd64.zip` from [sing-box releases](https://github.com/SagerNet/sing-box/releases).
2. Download official `wintun-*.zip` from [wintun.net](https://www.wintun.net/).
3. Compute the SHA-256 hashes:
   ```powershell
   Get-FileHash -Algorithm SHA256 sing-box.exe
   Get-FileHash -Algorithm SHA256 wintun.dll
   ```
4. Update `EXPECTED_SING_BOX_HASH` and `EXPECTED_WINTUN_HASH` in `core/src/main/kotlin/app/nebula/vpn/core/windows/SingBoxBinaryVerifier.kt`.
5. Place the binaries into `desktop/src/main/resources/bin/`.

---

## Building Android from Source

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
  - Dedicated top-level Routing tab with segmented views: `[Rules | Apps | Presets | Test]`.
  - Smart multi-line rule detection (`SmartRuleParser`): pastes and auto-classifies domains, suffixes (`.ru`), IPv4 CIDRs, and package names.
  - Live rule conflict and shadowing detection (`RuleConflictDetector`).
  - Interactive Route Trace Simulator for testing DNS/outbound matching before traffic dispatch.
  - Per-app routing modes: Global, Bypass (Blacklist), and Forward (Whitelist) with strict mutual exclusivity.
  - Mass package name import parser.
  - Domain suffix, keyword, regex, IP CIDR, and sing-box `rule_set` routing with priority indexing and bulk actions.
  - Direct routing presets for popular censorship environments (`Russia Direct`, `AdBlock`, `Socials Proxy`).

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

- **Diagnostics, Soak Testing & Connection Doctor:**
  - Real-time Connection Doctor performing multi-point diagnosis: Core binary, Config validity, Local DNS, Gateway reachability, Remote server handshake, and TUN routing interface.
  - One-tap Redacted Diagnostics Bundle export (masking passwords, UUIDs, private keys).
  - Physical device automated diagnostics collection via `scripts/collect-debug.sh` (bash) and `scripts/collect-debug.ps1` (PowerShell).
  - Automated soak testing harness (`scripts/soak/run_soak_test.ps1`) tracking PSS/RSS drift, Dalvik heap, and open file descriptors over continuous UI cycles.
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
