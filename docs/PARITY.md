# Parity Oracle & Behavioral Specification: v2rayNG vs Nebula

## 1. Executive Summary & Objective

The goal is to achieve **functional parity with v2rayNG** for the Android VPN client **Nebula** (`app.nebula.vpn`) on real-world configurations, specifically the public Russian subscription fixture `black_ss_all_rus_20261026.txt` (63 nodes: 15 Hysteria2, 3 VMess, 45 Trojan).

In v2rayNG, these exact nodes allow opening Telegram, YouTube, and blocked resources in Russia on the same physical device and network. In previous builds of Nebula, the UI displayed "Connected", but traffic failed and blocked sites did not open.

This document records the exact behavioral differences, root causes, and architectural invariants between v2rayNG (v2.3.11 reference) and Nebula.

---

## 2. Line-by-Line Architectural Comparison

| Dimension | v2rayNG (Reference v2.3.11) | Nebula (Previous) | Nebula (Parity Target) | Parity Status |
| :--- | :--- | :--- | :--- | :--- |
| **Default IPv6 Behavior** | **OFF** (`PREF_IPV6_ENABLED=false`). No IPv6 address added to `VpnService.Builder`, no IPv6 routes added. DNS strategy forced to `UseIPv4` / `ipv4_only`. | **ON** (`fdfe:dcba:9876::1/126` in TUN, `addRoute("::", 0)` unconditionally forced, `prefer_ipv4`). | **OFF by default**. When disabled: zero IPv6 address, zero IPv6 route, `strategy: "ipv4_only"` in DNS. | Root cause #1 resolved |
| **QUIC (UDP 443) Handling** | `PREF_MUX_XUDP_QUIC = "reject"` by default. UDP 443 packets are actively rejected, forcing instant fallback to HTTP/2 over TCP TLS. | Not blocked or rejected. UDP 443 hangs on throttled Russian ISPs for YouTube/Google. | `route.rules` actively rejects UDP port 443 / QUIC, forcing instant TCP HTTP/2 fallback. | Root cause #2 resolved |
| **Telegram DC Connectivity** | Traffic to Telegram's official IP CIDRs routed through proxy outbound. Sniffing resolves MTProto/TCP directly. | Domain rules only matched domain names (`telegram.org`). Direct Telegram DC IPs (`149.154.x.x`, `91.108.x.x`) relied on fallback. | Official Telegram CIDRs (`core.telegram.org/resources/cidr.txt`) embedded in routing engine and presets. | Root cause #3 resolved |
| **DNS Server in VpnService** | Configures public DNS (`1.1.1.1`, `8.8.8.8`) on `VpnService.Builder`. Port 53 intercepted and detoured to remote DNS. | Configured unroutable `172.19.0.2` on TUN. Did not handle Private DNS (DoT 853) probes. | Configures `1.1.1.1`, `8.8.8.8` on `VpnService.Builder`; intercepts DNS port 53; maps Private DNS domains in hosts. | Root cause #4 resolved |
| **VpnService App Exclusion** | Always calls `builder.addDisallowedApplication(selfPackageName)` when not in per-app proxy mode. Prevents self-loop. | `GLOBAL` and `BYPASS_APPS` omitted `addDisallowedApplication(ownPackageName)`. | Always calls `builder.addDisallowedApplication(ownPackageName)` in `GLOBAL` and `BYPASS_APPS` modes. | Root cause #5 resolved |
| **Hysteria2 Authority Parsing** | Uses `URI(fixIllegalUrl(str))` with path trimming. Port extracted correctly even with trailing slash. | Did not strip trailing `/` before `?`, causing port `8443` to fail `toIntOrNull()` and default to `443`. | `SimpleUri` strips trailing slashes on authority; parses port accurately (`8443`). | Root cause #6 resolved |
| **Hysteria2 Password Decoding** | Decodes `userInfo` via `URLDecoder.decode(userInfo, "UTF-8")`. `gf1%3Agfhysteria2024` -> `gf1:gfhysteria2024`. | Left `userInfo` raw and URL-encoded (`%3A` retained), failing server authentication. | `userInfo` is URL-decoded when parsing Hysteria2 and Trojan links. | Root cause #7 resolved |
| **Insecure Flag Parity** | Checks `insecure`, `allowInsecure`, `allow_insecure` across VMess, Trojan, VLESS, Hysteria2. | Hysteria2 checked only `insecure`; Trojan checked only `insecure` and `allowInsecure`. | All parsers check `insecure`, `allowInsecure`, `allow_insecure`, `skip-cert-verify`, `verify_cert`. | Root cause #8 resolved |
| **Trojan TLS Fingerprint** | Parses `fp` parameter (`android`, `chrome`, `edge`, `firefox`, `ios`, `safari`) into `fingerprint`. | `parseTrojan` completely omitted `fp` parameter, leaving fingerprint blank. | `parseTrojan` parses `fp` and passes to `TlsConfig.utlsFingerprint`. | Root cause #9 resolved |
| **"Connected" Status Truth** | Uses CoreController running state and background speed notification. | Reported `Connected` immediately upon starting command server, before real TCP handshake. | Actively probes real connectivity with fallback probe before setting full Connected state. | Root cause #10 resolved |

---

## 3. Snapshot Analysis (`black_ss_all_rus_20261026.txt`)

- **Total Links:** 63 valid proxy nodes (15 Hysteria2, 3 VMess, 45 Trojan).
- **Comments/Metadata:** Includes `# profile-title: 🏴 ЧЕРНЫЕ СПИСКИ 🏴 BLACK LISTS | Полная • Full | SS, Hy2, Vmess, Trojan`.
- **Legacy Parameters:** Notice in snapshot header:
  `RU: Эта подписка может содержать Trojan/Hysteria2/Hy2 с legacy-параметрами insecure=1 или allowInsecure=1.`
  `RU: Для совместимости используйте Mihomo/Sing-box для Trojan/Hysteria2/Hy2 или Xray-core v26.1.23.`
- **Result:** Sing-box is the officially recommended engine for these exact configs, provided the parser and routing parameters match the specifications detailed above.
