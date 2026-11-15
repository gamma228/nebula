# Tor Engine Integration Decision & Architectural Selection

**Date:** November 2026  
**Status:** Approved & Implemented: **Embedded Arti + Onionmasq Native Engine**

---

## 1. Options Evaluated

### Option A: Embedded Arti + Onionmasq (Selected Approach)
- **Architecture:** 
  - Direct integration of the Tor Project's official `org.torproject.onionmasq` native library (`libonionmasq_mobile.so`) built on Rust `arti-client` + `onion-tunnel` + `smoltcp` userspace network stack.
  - Owns the Android `VpnService` TUN file descriptor when Tor mode is active.
- **Advantages:**
  - **Single Autonomous APK:** User does not need to install third-party apps like Orbot.
  - **Fail-Closed by Design:** `onion-tunnel` intercepts all IP packets directly from the TUN; non-DNS UDP and raw packets are dropped inside userspace, preventing any clearnet leakage.
  - **Per-App Circuit Isolation:** Uses Android `ConnectivityManager.getConnectionOwnerUid` to isolate circuits per application UID.
  - **Zero Background Clearnet Traffic:** Fully controlled lifecycle.
- **Trade-offs:**
  - Adds ~8 MB per ABI to APK size.
  - Requires Android minSdk >= 24 (Nebula's minSdk is 26, fully compatible).

### Option B: External Orbot IPC / Intent Binding (Evaluated - Rejected)
- **Architecture:** 
  - Rely on user installing Orbot from F-Droid or Google Play. Nebula's `VpnService` routes TCP/DNS packets to Orbot's local SOCKS5 (`127.0.0.1:9050`) and DNS (`127.0.0.1:5400`) ports.
- **Drawbacks:**
  - Poor user experience (requires managing two separate apps).
  - Fragile lifecycle (if Orbot is killed or battery-optimized, VPN leaks or deadlocks).
  - Cannot enforce per-app circuit isolation directly from Nebula.
- **Conclusion:** Rejected in favor of Option A.

---

## 2. Pluggable Transports & IPtProxy Status (STUB Shim Documented)

### The In-Process Go Runtime Blocker
`libbox.aar` (sing-box core) and `IPtProxy.aar` (Lyrebird/Snowflake) are both compiled with `gomobile`. When both are loaded in the same Android process:
1. **DEX Class Collision:** Both AARs package the identical runtime package `go.Seq` and `go.LoadLibrary`, causing DEX collision or runtime crash.
2. **CGo Native Signal Collisions:** Both shared objects install duplicate POSIX signal handlers (`SIGSEGV`, `SIGBUS`, `SIGPROF`). The second runtime overwrites the signal stack of the first, causing instant segmentation faults when coroutines or garbage collectors trigger.

### Status of `app/src/main/java/IPtProxy`
- **Classes under `app/src/main/java/IPtProxy/` (`IPtProxy.java`, `Controller.java`) are a compile-time STUB shim.**
- They satisfy the compile-time imports of `onionmasq.aar` without loading `libIPtProxy.so` into the process.
- **Pluggable Transports (obfs4, WebTunnel, Snowflake) are currently NOT supported.**
- The application UI, parser (`BridgeLineParser`), and pool manager explicitly detect and reject obfs4, WebTunnel, and Snowflake lines across all 5 languages, alerting the user to use Direct Tor, Vanilla Bridges, or Carrier Mode.
- Full architectural analysis of long-term PT solutions (multi-process IPC, monolithic single-gomobile build, or standalone PT binaries) is documented in `docs/TOR_PT_BLOCKER.md`.

---

## 3. Russia Circumvention: Carrier Mode & Public Tor Relays

### How Carrier Mode Operates ("Tor via my server")
To bypass strict Russian TSPU blocks and whitelist-style ("белые списки") restrictions:
1. **Loopback Carrier Inbound:**  
   Nebula runs a lightweight, loopback-bound sing-box instance on `127.0.0.1:<socksPort>` configured with a SOCKS5 inbound (no TUN, random auth) and routing out through the user's selected proxy node (VLESS Reality, Trojan, Shadowsocks).
2. **Direct / Vanilla Bridges via Carrier:**  
   Nebula creates a local loopback TCP port forwarder (`LocalCarrierForwarder`) on `127.0.0.1:<local_port>` that connects through the carrier's SOCKS5 proxy to the bridge's target `host:port`, and passes `127.0.0.1:<local_port> <fingerprint>` to `OnionMasqJni.runProxy`.
3. **Using Normal Public Tor Relays as Vanilla Bridges:**
   Because Russian ISPs block direct connections to Tor directory authorities and public relays, any standard public Tor relay (~7,000 relays in the Tor consensus) can be entered as a custom vanilla bridge line (`IP:ORPort [Fingerprint]`). When tunneled through Carrier Mode, the ISP sees only the VLESS Reality connection to the user's server; the server forwards the TCP stream to the Tor relay ORPort, allowing Tor to bootstrap without requiring scarce or blocked PT bridges.
4. **Privacy Implications of Carrier Mode (Honest Analysis):**
   - **What your ISP sees:** Only the encrypted connection to your VPN server (e.g., standard TLS / Reality). The ISP cannot see Tor signatures, directory requests, or destination onion packets.
   - **What your VPN server operator sees:** The server operator sees that you are establishing TCP connections to Tor relay ORPorts (ports 443, 9001, etc.). They know that you are connecting into Tor, but they *cannot* see your destination websites, DNS queries, or inner circuit traffic because Tor's multi-hop link encryption protects the payload from the guard relay to the exit node.
5. **Leak Isolation:**  
   The carrier socket is protected via `VpnService.protect()`. The app's central `NetworkPolicy` prohibits any clearnet traffic except loopback and the registered carrier endpoint.

---

## 4. Cost & Footprint Analysis

| Metric | Sing-Box Only | With Tor Engine (Arti + Onionmasq) | Delta |
| :--- | :--- | :--- | :--- |
| **arm64-v8a Release APK** | 78.8 MB | ~87.2 MB | +8.4 MB |
| **Universal Release APK** | 227 MB | ~258 MB | +31 MB |
| **Permissions Added** | None | None | 0 new permissions |
| **minSdk** | 26 | 26 | 0 change |
| **targetSdk** | 35 | 35 | 0 change |
| **Foreground RAM (Idle)** | ~55 MB | ~72 MB | +17 MB |

---

## 5. Architectural Summary

Nebula adopts a unified `TunnelEngine` abstraction:
- In **sing-box mode**, `SingBoxEngine` handles multi-protocol subscriptions, custom routing rules, and high-performance TUN routing.
- In **Tor mode**, `TorEngine` (Arti + Onionmasq) assumes full ownership of the TUN file descriptor, enqueuing bridge connections through the optional carrier, dropping all non-Tor traffic, and isolating circuits per app.
