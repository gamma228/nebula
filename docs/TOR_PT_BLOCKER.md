# Investigation: Pluggable Transports Architecture & Dual Go-Runtime Blocker

## 1. Executive Summary & Root Cause

The Nebula Android client uses two sophisticated network engines:
1. **sing-box v1.14.4** (`libbox.aar`): A high-performance proxy core written in Go, compiled with `gomobile bind` into `libbox.so` with Java/Kotlin bindings.
2. **Arti + Onionmasq** (`onionmasq.aar`): A modern userspace Tor implementation written in Rust (`libonionmasq.so`).

Tor Pluggable Transports (PTs) such as **obfs4**, **WebTunnel** (via Lyrebird), and **Snowflake** are officially maintained in Go. On Android, The Tor Project packages these via `IPtProxy.aar` (also compiled with `gomobile bind` into `libgojni.so`).

When attempting to include both `libbox.aar` and `IPtProxy.aar` in the same Android application, two fatal collision layers occur:

### Layer A: Dalvik/ART Class Collision (Build Time)
`gomobile bind` generates universal wrapper classes under the fixed package `go.Seq`, `go.Seq$Ref`, etc. When Gradle compiles the Android APK, D8/R8 encounters duplicate classes:
```text
Duplicate class go.Seq found in modules libbox-runtime.jar (libbox.aar) and IPtProxy-runtime.jar (IPtProxy.aar)
```

### Layer B: Native Go Runtime Symbol & Signal Handler Collision (Runtime)
Even if the DEX classes were renamed, both shared libraries (`libbox.so` and `libgojni.so`) contain full, statically linked Go runtimes. In Linux/Android:
1. Go takes over OS signal handlers (`SIGSEGV`, `SIGBUS`, `SIGPROF`). Two independent Go runtimes in the same process address space fight for signal handling, immediately aborting the process on thread preemption or memory paging.
2. Both libraries export identical internal CGo symbols (`_rt0_arm64_linux_lib`, `runtime.sigtramp`, etc.), causing undefined behavior or dynamic linker crashes when `System.loadLibrary` is called.

---

## 2. In-Depth Architectural Evaluation of the 4 Options

### Option (i): Separate Android Process (`android:process=":pt"`)
* **Mechanism:** Pluggable Transports run in an auxiliary Android Service (`PtService`) declared with `android:process=":pt"`.
* **Process Separation:** In Linux/Android, separate processes have isolated virtual address spaces, separate ART runtimes, and separate OS signal tables. This completely eliminates the native Go runtime collision.
* **Build Constraint:** In a standard single-APK Gradle configuration, D8/R8 merges all classes into the APK's `classes.dex`, regardless of manifest process tags. Declaring a separate process in the manifest does NOT prevent `Duplicate class go.Seq` during `checkDebugDuplicateClasses`.
* **Solution Path:** To use separate processes without class collisions, `:pt` must be packaged in an independent Dynamic Feature Module (split APK) or a standalone companion APK.
* **Overhead & Caveats:** Spawning a second persistent Android process consumes an additional ~35–50 MB of RAM, requires complex AIDL IPC for passing local SOCKS5 ports, and increases battery drain on mobile devices subject to Android OS process killer policies.

### Option (ii): Combined Monolithic Gomobile Build
* **Mechanism:** Maintain a single unified Go repository that imports both `io.nekohasekai.sagernet.libbox` and `github.com/tladesignz/IPtProxy`. Run a single `gomobile bind` invocation:
  ```bash
  gomobile bind -target=android io.nekohasekai.sagernet.libbox github.com/tladesignz/IPtProxy
  ```
* **Pros:** Produces exactly ONE `go.Seq`, ONE `libgojni.so`, and ONE Go runtime instance that handles both sing-box tunneling and PT listeners.
* **Cons & License Implications:**
  1. **License Contamination:** sing-box v1.14.4 is licensed under **GPL-3.0**. IPtProxy, Lyrebird, and Snowflake are licensed under **BSD-3-Clause**. Linking them into a single binary permanently binds the entire binary to GPL-3.0 terms.
  2. **Maintenance Burden:** Requires maintaining custom Go toolchain patches, vendoring Lyrebird/Snowflake CGo dependencies for 4 Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), and rebasing against upstream sing-box releases.

### Option (iii): Separate Native Executables (`lyrebird` & `snowflake-client` Binaries)
* **Mechanism:** Package standalone C/Go ELF executables for `lyrebird` and `snowflake-client` inside the APK, similar to desktop Tor (`ClientTransportPlugin obfs4 exec ...`).
* **Android Security Boundary (W^X / SELinux):**
  - On Android 10+ (API 29+), executing binaries placed in the app's writable data directory (`/data/data/app/files`) is strictly blocked by SELinux with `Permission Denied` (W^X violation).
  - Executables must be compiled as PIE (Position Independent Executable), named `liblyrebird.so` and `libsnowflake.so`, and placed in `jniLibs/`. When the app installs, Android's `PackageManager` extracts them to `nativeLibraryDir` (which has `exec` permission).
* **Process Management:** The Java/Rust layer invokes `ProcessBuilder(File(context.applicationInfo.nativeLibraryDir, "liblyrebird.so"))`. The process runs locally, opens an unmanaged SOCKS5 listener on `127.0.0.1:<port>`, and Arti connects to it.
* **Verdict:** Highly viable for future expansion, but increases APK size by ~18–25 MB per ABI.

### Option (iv): Bytecode Shading & Symbol Renaming (`go.Seq`)
* **Mechanism:** Use bytecode manipulation (e.g., JarJar or ASM) to shade `go.Seq` to `iptproxy.go.Seq` in `IPtProxy.aar`.
* **Why It Fails:**
  1. `libgojni.so` hardcodes native JNI class lookups: `(*env)->FindClass(env, "go/Seq")`. Renaming the Java bytecode breaks the JNI bridge with `ClassNotFoundException: go/Seq`.
  2. Even if JNI strings are patched in the ELF binary, the OS-level Go runtime signal conflict (Layer B) remains active. Two Go runtimes loaded into the same process will crash with `SIGSEGV` during garbage collection or thread scheduling.
* **Verdict:** Technically infeasible and dangerous.

---

## 3. The Working Solution for Russia: Carrier Mode + Public Tor Relays

While Pluggable Transports are theoretically useful, in real-world Russian conditions:
- **obfs4:** Blocked by TSPU (DPI) heuristics across major mobile operators (MTS, MegaFon, Beeline).
- **WebTunnel:** Regularly scraped and IP-blocked within days of distribution.
- **Snowflake:** STUN traffic is severely throttled or dropped by Russian ISPs.

### Why Carrier Mode ("Tor via my server") Is Superior:
In Nebula, Carrier Mode routes Tor traffic inside a proven, censorship-resistant VPN tunnel (VLESS + Reality, Trojan, or Hysteria 2):
1. The Russian ISP sees ONLY the user's private VPN server connection (indistinguishable from standard HTTPS traffic to foreign CDNs).
2. The user's foreign server is outside Russian censorship boundaries (e.g., in Germany, Finland, or the Netherlands).
3. From the foreign server, **all ~7,000 public Tor relays and Vanilla bridges are 100% reachable**.
4. Users do NOT need scarce, blocked obfs4 or WebTunnel bridges. Any standard public Tor relay's ORPort functions as an entry guard when tunneled through Carrier Mode!

---

## 4. Current Implementation Status in Nebula

| Transport | Status in Current Engine | Reason / Working Alternative |
|:---|:---|:---|
| **Direct Tor** | **REAL (Verified)** | Directly supported by native Rust Arti + Onionmasq. |
| **Vanilla Bridges** | **REAL (Verified)** | Directly supported (`IP:port [fingerprint]`). |
| **Carrier Mode** | **REAL (Verified)** | Tunnels bridge/Tor traffic through local sing-box SOCKS5. |
| **obfs4** | **UNSUPPORTED** | Blocked by Go runtime collision; rejected with localized warning. |
| **WebTunnel** | **UNSUPPORTED** | Blocked by Go runtime collision; rejected with localized warning. |
| **Snowflake** | **UNSUPPORTED** | Blocked by Go runtime collision; rejected with localized warning. |
