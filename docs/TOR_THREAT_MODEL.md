# Nebula Tor Mode Threat Model & Security Boundaries

**Date:** November 2026  
**Status:** Mandatory Reference for UI & Codebase

---

## 1. Guiding Principles & Honesty

> [!IMPORTANT]
> Nebula **never** claims "100% anonymity", "untraceable browsing", or "military-grade privacy". The Tor Project explicitly documents that experimental mobile VPNs have inherent privacy trade-offs and cannot eliminate all device or application fingerprinting. Nebula communicates these boundaries transparently in the UI and documentation.

---

## 2. What Tor Mode PROTECTS

1. **Local Network & ISP Surveillance:**
   - All TCP and DNS traffic from selected apps is routed exclusively through the Tor network.
   - The local network administrator, Wi-Fi operator, and ISP cannot see which websites, IP addresses, or services you access.
2. **Bridge Obfuscation:**
   - When using Pluggable Transports (WebTunnel, obfs4, Snowflake), deep packet inspection (TSPU) cannot readily identify that the traffic is Tor.
   - In **Carrier Mode ("Tor via my server")**, the ISP sees only a connection to your trusted VPN server (e.g., standard TLS/Reality); Tor handshakes and bridge addresses are completely invisible to the ISP.
3. **Per-App Circuit Isolation:**
   - Separate applications (e.g., Telegram, Web Browser, Mail) are allocated distinct Tor circuits.
   - Activity in one app cannot be linked to activity in another app by exit nodes or correlation attacks.
4. **DNS Leak Prevention:**
   - DNS requests are resolved entirely through Tor's virtual FakeIP mechanism inside userspace.
   - No DNS queries reach the ISP, local router, or cellular DNS resolvers.
5. **Fail-Closed Enforcement:**
   - If the Tor engine stalls, crashes, or loses connectivity, the TUN interface remains active and drops all packets (state `Blocking`). Clearnet fallback is mathematically impossible.

---

## 3. What Tor Mode DOES NOT Protect

1. **Device & OS Identifiers:**
   - Android operating system telemetry, Google Play Services, and hardware identifiers (IMEI, MAC address, Android ID) are not masked by Tor.
   - Android system captive portal checks or connectivity pings bypass or run outside per-app VPN routing.
2. **Application-Level Fingerprinting & Logins:**
   - If you log into a personal account (Google, Telegram, VK, Yandex) inside an app, the service provider knows your identity regardless of Tor.
   - Browsers without anti-fingerprinting defenses can be tracked across sessions via canvas fingerprinting, WebGL, cookies, and screen resolution.
3. **Unencrypted HTTP Traffic at the Exit Node:**
   - If an app communicates over plain HTTP (without TLS), the malicious or monitored Tor exit node can inspect the payload. Users must ensure HTTPS/TLS is used.
4. **Unsupported Protocols (UDP / QUIC / Voice Calls):**
   - Tor natively supports TCP only. Non-DNS UDP traffic, including WebRTC audio/video calls and QUIC (HTTP/3), is dropped. Applications that rely exclusively on UDP will not function in Tor mode.
5. **Global Passive Adversaries:**
   - Sophisticated adversaries monitoring both ends of the connection simultaneously can perform statistical traffic timing correlation.
6. **High-Risk Political / Critical Anonymity Scenarios:**
   - The Tor Project advises that mobile VPNs should not be used by high-risk whistleblowers or political dissidents who face state prosecution; dedicated air-gapped systems (such as Tails OS) are required for extreme threat models.

---

## 4. What the Carrier Server Operator Can See (In Carrier Mode)

When "Tor via my server" is enabled:
- **What they CAN see:**
  - That your IP address is connecting to their server.
  - The volume and timing of data transferred.
  - That the outbound connections from their server are heading towards Tor bridge IP addresses.
- **What they CANNOT see:**
  - The contents of your traffic (protected by Tor's multi-hop onion encryption).
  - Your final destination websites or IP addresses (handled by Tor middle and exit relays).
  - Your DNS queries (resolved within Tor).

---

## 5. Summary Matrix

| Threat Category | Protected? | Notes |
| :--- | :---: | :--- |
| ISP DNS logging | **YES** | Intercepted in TUN, resolved through Tor |
| ISP website tracking | **YES** | Encrypted through Tor 3-hop circuits |
| DPI protocol blocking | **YES** | Bypassed via Bridges / Carrier VLESS Reality |
| Cross-app correlation | **YES** | Isolated circuits per application UID |
| Clearnet leak on disconnect | **YES** | Fail-closed TUN drops packets in Blocking state |
| Device IMEI / Google ID | **NO** | OS-level identifiers remain accessible to apps |
| App accounts & logins | **NO** | Logging into accounts identifies the user |
| UDP VoIP calls & gaming | **NO** | UDP is blocked by design (TCP only) |
| Global traffic correlation | **NO** | Out of scope for onion routing |
