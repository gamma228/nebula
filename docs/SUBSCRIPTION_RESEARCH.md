# Subscription Protocols, Panel Mechanics & HWID Architecture

**Date:** 2026-11-13  
**Audited Panels & Ecosystems:** Remnawave, Marzban, 3x-ui, Hiddify, sing-box, Clash/Mihomo.

---

## 1. Client Identification (User-Agent Sniffing)

VPN subscription panels perform server-side content-negotiation based on the `User-Agent` HTTP request header. Because different VPN applications historically supported different format dialects, modern panels do not use standard HTTP `Accept` headers; instead they inspect the `User-Agent` string:

| User-Agent Keyword | Content-Type Returned | Body Format | Used By |
| :--- | :--- | :--- | :--- |
| `sing-box` | `application/json` | sing-box native JSON (`{"outbounds": [...]}`) | sing-box Android/Desktop, SagerNet |
| `v2rayN` / `v2rayNG` / `Xray` | `application/json` or `text/plain` | Xray JSON Array (`[{"outbounds": [...]}]`) or Base64 share links | v2rayNG, v2rayN |
| `Clash` / `ClashMeta` / `mihomo` | `text/yaml` | Clash YAML (`proxies:`, `proxy-groups:`) | Clash Verge, Mihomo Party, Clash for Android |
| `Nebula` / Generic / Unknown | `text/plain` | Base64-encoded share links (`vless://`, etc.) | General clients |
| `Mozilla` / Browser | `text/html` | User dashboard / Web panel login | Web browsers |

### Fallback Ladder Strategy
When an import fails with 0 proxies or returns an HTML landing page, Nebula executes a bounded, cancelable client identification ladder:
1. **Nebula Default:** `Nebula/1.2.1 (Android; sing-box/1.14.4)` (Standard identity).
2. **sing-box Native:** `sing-box/1.14.4` (Requests native sing-box routing and modern VLESS Reality outbounds).
3. **Clash/Mihomo:** `ClashMeta/1.18.0` (Requests Clash YAML).
4. **v2rayNG:** `v2rayNG/1.8.5` (Requests Xray JSON or raw link feeds).

Nebula stops at the first preset that returns >= 1 valid proxy profile, records the winning preset for that subscription, and persists it for subsequent background refreshes.

---

## 2. Remnawave HWID & Device-Binding Architecture

### Overview
Remnawave is a modern, high-performance Xray/sing-box management panel featuring device limitation enforcement (HWID) to prevent credential sharing on single-user subscriptions.

### Request Headers Sent by Clients Supporting Remnawave HWID
When HWID is enabled by the user for a subscription, the client provides:
- **`X-HWID`:** A persistent unique client identifier. In Nebula, this is a **cryptographically random UUIDv4 generated locally per installation** (e.g. `c7b8d9e0-1234-4a5b-9c8d-e1f2a3b4c5d6`), with an option for the user to regenerate at will. **NEVER** use `ANDROID_ID`, IMEI, MAC address, or hardware serial numbers to protect user privacy.
- **`X-Device-OS`:** Operating system identifier (e.g. `Android`).
- **`X-Ver-OS`:** OS version (e.g. `14`, `15`).
- **`X-Device-Model`:** Device model representation (e.g. `Nebula Client`).

### Response Signals from Remnawave
When a client requests a subscription from a Remnawave panel:
1. **`x-hwid-active: true`**  
   Indicates that HWID tracking is enabled for this subscription key.
2. **`x-hwid-not-supported: true`**  
   Indicates that the subscription requires HWID headers, but the client did not send `X-HWID`. The panel returns 3 stub VLESS placeholder nodes pointing to `0.0.0.0:1` titled with instructions to use a supported client or enable HWID.
3. **`x-hwid-limit: true`**  
   Indicates that the maximum number of devices (e.g. 1 device) allocated to this subscription has been reached. Any additional device attempting to fetch without an existing slot is blocked or returned stub nodes.

### Nebula Safety & User Consent Policy
- HWID headers are **OFF by default** to prevent unexpected device slot consumption.
- If a panel response contains `x-hwid-not-supported: true` or returns stub nodes indicating HWID is required, Nebula presents an interactive dialog explaining:
  - What HWID is (a random client ID binding this phone to the subscription).
  - A warning that enabling it will consume one of the provider's device slots.
  - Buttons to "Enable Device Binding" or "Cancel".
- If `x-hwid-limit: true` is returned, Nebula warns the user that their device limit is reached and advises them to disconnect/reset the device in their provider's bot or other client.

---

## 3. Standard Subscription Metadata Headers

Nebula extracts and parses standard RFC and community headers:
- **`Subscription-Userinfo`:** Standard traffic quota header:
  `upload=<bytes>; download=<bytes>; total=<bytes>; expire=<epoch_seconds>`
  - Handles `total=0` (unlimited bandwidth).
  - Handles missing `expire` or expired epoch timestamps.
- **`Profile-Title`:** Display title of the subscription. Decodes UTF-8 and `base64:<data>` formats.
- **`Profile-Update-Interval`:** Recommended auto-refresh interval in hours (clamped to a safe 1h..168h range in Nebula).
- **`Support-Url`:** Contact / Telegram support link for the provider (sanitized, plain text only, no automatic opening).

---

## 4. Privacy & Security Invariants
- All server-provided text is sanitized and rendered strictly as plaintext (zero HTML injection).
- Subscriptions are fetched directly through the user-specified network policy; under Tor mode, subscriptions are blocked fail-closed to prevent clearnet identity leaks.
- Zero tracking or analytics headers are ever injected into subscription requests.
