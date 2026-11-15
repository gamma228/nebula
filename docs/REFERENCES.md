# Popular Proxy/VPN Client Reference Research (Milestone D1)

**Research Scope:** Reference analysis of popular open-source and commercial VPN/proxy desktop clients to extract architectural patterns, standout UX paradigms, and common user pain points.

> [!IMPORTANT]
> **Strict Licensing Rule:**
> Code may be copied ONLY from projects under permissive licenses (**MIT**, **Apache-2.0**, **BSD**), keeping copyright notices in `THIRD_PARTY_NOTICES.md`.
> Code from **GPL / AGPL / Proprietary** projects is **NEVER** copied; only architectural concepts, design principles, and UX ideas may be independently reimplemented.

---

## 1. Feature Comparison Matrix

| Client | License | Tech Stack | Standout Strengths | Common User Complaints | Clean Ideas to Adopt |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **v2rayN** | GPL-3.0 | C# / .NET 8 / WPF | Mature feature set, mass latency testing, export options, routing lists | Outdated WinForms/WPF UI, steep learning curve, bloated options | Mass speed testing, sparkline ping history, running process selector |
| **Clash Verge Rev** | GPL-3.0 | Tauri / Rust / React | Gorgeous modern dark UI, traffic chart, active connection killer | High RAM from Webview2, complex Clash YAML overrides, system proxy leaks on crash | Real-time traffic chart, active connection viewer, kill connection button |
| **FlClash** | GPL-3.0 | Flutter / Dart | Consistent cross-platform UI, Material 3 theming, clean layout | Flutter desktop text rendering artifacts, large runtime bundle | Visual subscription cards, intuitive rule test simulator |
| **Hiddify** | GPL-3.0 | Flutter / sing-box | Official sing-box core integration, multi-protocol, 1-click connect | Cluttered dashboard, confusing navigation, unstable TUN on some Win 10 builds | Auto-select lowest ping (`urltest`), seamless TUN/system-proxy toggle |
| **NekoRay** | GPL-3.0 | C++ / Qt 5 | Extremely low CPU/RAM, deep sing-box & xray support | Ugly 90s-era interface, non-intuitive routing configuration | Low-overhead core child-process management, direct WinINET registry hooks |
| **singbox-launcher** | MIT | Go / Win32 | Tiny binary size (<5 MB), zero background CPU, clean portable mode | No graphical configuration, raw JSON editing, zero customization | Lightweight process monitoring, clean crash recovery, WinINET restore |
| **INCY** | Proprietary | Kotlin / sing-box | Single-outbound architecture (<40 MB core RAM), TLS hello fragmentation | Closed-source, no desktop client, locked down configuration | In-place config reload without closing TUN, anti-censorship fragmentation |

---

## 2. Prioritized "Steal the Idea" List (Reimplemented from Scratch)

1. **Material 3 Dynamic Theming with Custom Wallpapers (FlClash + Android Nebula)**:
   - Full color wheel & palette generator with WCAG contrast protection.
   - User wallpaper support (PNG/JPG/WebP) with adaptive glass/mica transparency.
2. **Live Active Connections Inspector (Clash Verge Rev)**:
   - List running network connections routed through sing-box with remote IP, domain, protocol, download/upload bytes, and matched rule.
   - 1-click action: "Close connection" and "Add rule for this domain/IP/process".
3. **Animated Real-Time Traffic Graph (Clash Verge Rev / v2rayN)**:
   - Smooth canvas-rendered throughput chart (download in primary accent, upload in secondary accent).
4. **Subscription Quota & Auto-Update Management (Hiddify / v2rayN)**:
   - Read and render `subscription-userinfo` headers (`upload`, `download`, `total`, `expire`) as clean progress bars.
   - Screen QR-code scanner (taking a screenshot region of desktop to decode proxy links).
5. **Interactive Routing Rule Simulator (Nebula Android + FlClash)**:
   - "Test Packet" input field: enter a domain, IP, or process name to visually trace which rule matches and which outbound handles it.
6. **Windows Inno Setup Installer with Desktop Shortcut (v2rayN / Standard Windows)**:
   - High-DPI installer wizard with explicit desktop shortcut checkbox, Start Menu entry, protocol handler registration (`vless://`), and clean uninstallation.

---

## 3. Tor Engine & Pluggable Transports References (Milestones T1-T9)

| Component | Version | License | Upstream Repository / Source | Role in Nebula |
| :--- | :--- | :--- | :--- | :--- |
| **Onionmasq** | 1.9.1Beta | Apache-2.0 / MIT | `https://gitlab.torproject.org/tpo/core/onionmasq` | Android user-space network stack (`onion-tunnel` + `smoltcp` + JNI bindings) routing TUN IP packets into Tor |
| **Arti** | 1.3.x (pinned in Onionmasq) | Apache-2.0 / MIT | `https://gitlab.torproject.org/tpo/core/arti` | Next-generation official Tor implementation in Rust by The Tor Project |
| **IPtProxy** | 5.4.2 / 5.7.0 | MIT (wrapper) / BSD-3 (Snowflake) / GPL-3 (Lyrebird) | `https://github.com/tladesignz/IPtProxy` | Pluggable transports wrapper managing obfs4, WebTunnel, and Snowflake local SOCKS listeners |
| **Lyrebird** | 0.5.x (bundled in IPtProxy) | GPL-3.0 | `https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird` | Obfs4 and WebTunnel pluggable transport client |
| **Snowflake** | 2.10.x (bundled in IPtProxy) | BSD-3-Clause | `https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake` | WebRTC data-channel transport with domain fronting / AMP rendezvous |
| **Tor Pluggable Transport Spec** | v2.1 | Creative Commons CC0 / Public Domain | `https://spec.torproject.org/pt-spec/index.html` | Specification for PT environment variables and upstream proxying (`TOR_PT_PROXY`) |
