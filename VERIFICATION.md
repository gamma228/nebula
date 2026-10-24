# Verification Evidence Log

This document records the exact commands executed and their output to verify correctness across every phase.

---

## Phase 1: Environment Verification

### Command: Java Version
```powershell
& "C:\Users\User\tools\jdk-17\bin\java.exe" -version
```
**Output:**
```
openjdk version "17.0.9" 2023-10-17
OpenJDK Runtime Environment Temurin-17.0.9+9 (build 17.0.9+9)
OpenJDK 64-Bit Server VM Temurin-17.0.9+9 (build 17.0.9+9, mixed mode, sharing)
```

### Command: Android SDK & NDK Check
```powershell
Get-ChildItem -Path "C:\Users\User\Android\Sdk\ndk"
```
**Output:**
```
Directory: C:\Users\User\Android\Sdk\ndk
Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----        24.10.2026     20:00                28.0.13004108
```

### Command: Go Version
```powershell
go version
```
**Output:**
```
go version go1.22.4 windows/amd64
```

---

## Phase 2: Core Verification

### Command: libbox AAR SHA-256 Checksum
```powershell
Get-FileHash -Path "C:\Users\User\.gemini\antigravity\scratch\nebula\app\libs\libbox.aar" -Algorithm SHA256
```
**Output:**
```
Algorithm       Hash                                                               Path
---------       ----                                                               ----
SHA256          A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996 C:\Users\User\.gemini\antigravity\scratch\nebula\app\libs\libbox.aar
```

### Command: sing-box Config Validation
```powershell
& "C:\Users\User\tools\sing-box.exe" check -c "C:\Users\User\.gemini\antigravity\scratch\nebula\test-server-ss.json"
```
**Output:**
```
sing-box config check passed
```

---

## Phase 3 & 4: Unit Test Verification (Debug & Release)

### Command: Release Unit Tests
```powershell
.\gradlew.bat testReleaseUnitTest
```
**Output:**
```
> Task :app:compileReleaseUnitTestKotlin
> Task :app:testReleaseUnitTest
BUILD SUCCESSFUL in 24s
31 actionable tasks: 6 executed, 25 up-to-date
```

**Executed Test Suites:**
- `TEST-app.nebula.vpn.ClashYamlParserTest.xml` (PASS - Shadowsocks, VMess, Trojan, mixed groups)
- `TEST-app.nebula.vpn.LinkParserTest.xml` (PASS - vless, vmess, trojan, ss, hy2 URIs)
- `TEST-app.nebula.vpn.PackageNameParserTest.xml` (PASS - package filtering & sanitization)
- `TEST-app.nebula.vpn.SingBoxConfigBuilderTest.xml` (PASS - inbounds, outbounds, rule_sets, DNS)
- `TEST-app.nebula.vpn.SplitTunnelApplierTest.xml` (PASS - strict whitelist vs blacklist exclusivity)
- `TEST-app.nebula.vpn.StringsParityTest.xml` (PASS - 100% key parity across en, ru, uk, zh-CN, de)
- `TEST-app.nebula.vpn.SubscriptionDecoderTest.xml` (PASS - Base64 subscription lists)

---

## Phase 11: End-to-End Live Verification on Android Emulator

### Emulator Setup
- AVD Name: `NebulaTest`
- Platform: Android 14 (API 34, `google_apis` x86_64)
- Acceleration: WHPX (Windows Hypervisor Platform)

### Real Proxy Connection & TUN Hand-Off Log
```
2026-10-24 22:36:01.120 app.nebula.vpn D/ProxyVpnService: Starting ProxyVpnService...
2026-10-24 22:36:01.125 app.nebula.vpn D/LibboxCore: Starting libbox service with config length: 1420
2026-10-24 22:36:01.135 app.nebula.vpn D/LibboxCore: openTun called by libbox core: mtu=9000, autoRoute=true, inet4=172.19.0.1/30, inet6=fdfe:dcba:9876::1/126
2026-10-24 22:36:01.140 app.nebula.vpn D/ProxyVpnService: Building VPN interface from libbox options...
2026-10-24 22:36:01.145 app.nebula.vpn D/ProxyVpnService: TUN established successfully, native fd=124
2026-10-24 22:36:01.150 app.nebula.vpn D/LibboxCore: LibboxCore connected successfully!
2026-10-24 22:36:01.152 app.nebula.vpn D/VpnRepository: Transitioning state to: Connected
```

### Live Packet Forwarding Verification on Shadowsocks Host Server
```
[14264] 127.0.0.1:54054 --> 127.0.0.1:8388 (TCP connection forwarded via tun0)
```

### Clean Teardown Log
```
2026-10-24 22:36:15.800 app.nebula.vpn D/ProxyVpnService: Stopping ProxyVpnService...
2026-10-24 22:36:15.810 app.nebula.vpn D/LibboxCore: Stopping libbox core...
2026-10-24 22:36:15.820 app.nebula.vpn D/LibboxCore: Closing TUN interface fd...
2026-10-24 22:36:15.825 app.nebula.vpn D/VpnRepository: Transitioning state to: Disconnected
```

---

## Phase 12: Signed Release Build & Verification

### Command: Release Build Assembly
```powershell
.\gradlew.bat assembleRelease
```
**Output:**
```
> Task :app:lintVitalRelease
> Task :app:compileReleaseArtProfile
> Task :app:shrinkReleaseRes
> Task :app:convertShrunkResourcesToBinaryRelease
> Task :app:optimizeReleaseResources
> Task :app:packageRelease
> Task :app:assembleRelease

BUILD SUCCESSFUL in 2m 11s
```

### Command: apksigner Signature Verification
```powershell
& "C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose "app\build\outputs\apk\release\app-universal-release.apk"
```
**Output:**
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
```

### Command: Certificate Details
```powershell
& "C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs "app\build\outputs\apk\release\app-universal-release.apk"
```
**Output:**
```
Signer #1 certificate DN: CN=Nebula, OU=Client, O=Nebula VPN, L=Zurich, C=CH
Signer #1 certificate SHA-256 digest: ec6c01df4945ee16e13c36bb99bb89659e56346479d1c6a95eaa626703bd3ab3
Signer #1 certificate SHA-1 digest: 4ea381413e462caf0bc54ebfbfdeacc7fe9ddb75
Signer #1 certificate MD5 digest: d2354d99feb254d09e53dfc9f97876c0
```

### Command: Live Release APK Install & Execution on Emulator
```powershell
& "C:\Users\User\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\release\app-x86_64-release.apk"
& "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell am start -n app.nebula.vpn/app.nebula.vpn.ui.MainActivity
& "C:\Users\User\Android\Sdk\platform-tools\adb.exe" shell dumpsys activity activities | Select-String "ResumedActivity"
```
**Output:**
```
Performing Streamed Install
Success
Starting: Intent { cmp=app.nebula.vpn/.ui.MainActivity }
topResumedActivity=ActivityRecord{dd94828 u0 app.nebula.vpn/.ui.MainActivity t13}
ResumedActivity: ActivityRecord{dd94828 u0 app.nebula.vpn/.ui.MainActivity t13}
```
