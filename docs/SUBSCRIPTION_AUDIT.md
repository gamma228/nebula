# Nebula Subscription Subsystem Audit

**Date:** 2026-11-13  
**Audited Components:**  
- `core/src/main/kotlin/app/nebula/vpn/core/parser/SubscriptionFetcher.kt`
- `core/src/main/kotlin/app/nebula/vpn/core/parser/SubscriptionDecoder.kt`
- `core/src/main/kotlin/app/nebula/vpn/core/parser/ClashYamlParser.kt`
- `core/src/main/kotlin/app/nebula/vpn/core/parser/LinkParser.kt`
- `app/src/main/java/app/nebula/vpn/data/repository/SubscriptionRepository.kt`
- `app/src/main/java/app/nebula/vpn/ui/servers/ServersViewModel.kt`

---

## 1. Request Pipeline & Headers Sent

### Headers Sent by `SubscriptionFetcher`
```http
User-Agent: v2rayN/6.23 sing-box/1.14.4 ClashMeta/1.18.0
Accept: */*
Accept-Encoding: gzip, deflate
```
- **User-Agent:** Currently hardcoded to `"v2rayN/6.23 sing-box/1.14.4 ClashMeta/1.18.0"`. This violates Hard Rule 4 ("Do not pretend to be another commercial client by default. Identify as Nebula. A manual per-subscription choice of User-Agent for the user is fine").
- **Missing Standard Headers:** Does not send `Accept-Language`, client hints (`sec-ch-ua`), or device headers.
- **Device / HWID Headers:** Zero hardware or device identification headers (`X-HWID`, `X-Device-OS`, `X-Device-Model`, `X-Ver-OS`) are sent. Common panels like Remnawave, Marzban, or 3x-ui that enforce single-device slots or require client registration return either an empty list, an HTML web portal, or a 400/403 "Device limit reached" error.
- **Cookies:** Stateless. `HttpURLConnection` does not persist or forward session cookies across redirects.

### Redirects & Timeouts
- **Redirects:** Up to 10 redirects (`MAX_REDIRECTS = 10`) followed manually via `Location` header resolution (supporting relative and absolute URIs).
- **Timeouts:** Connect timeout = 15,000 ms (15s); Read timeout = 20,000 ms (20s).

### Compression & Encoding
- **GZIP:** Handled if `content-encoding` contains `"gzip"` via `java.util.zip.GZIPInputStream`.
- **Deflate:** Announced in `Accept-Encoding: gzip, deflate`, but `InflaterInputStream` is NOT implemented; if a server returns raw `deflate`, stream decoding corrupts or fails.
- **Brotli (`br`):** Not supported or announced.
- **Character Set:** Hardcoded to `StandardCharsets.UTF_8`. UTF-8 with BOM (Byte Order Mark `\uFEFF`) is not stripped, which breaks YAML and JSON parsers.

### Network Path & Tor Policy
- Directly invokes `NetworkPolicy.checkAllowed(targetUrl.host, port)`.
- When Tor mode is active, clearnet fetches fail-closed with `SecurityException`.
- Standard Android `HttpURLConnection` routes via system default network (or active VPN tunnel interface if VPN is active and not bypassed).

---

## 2. Formats Accepted vs Missing

### Formats Currently Handled
1. **Clash / ClashMeta YAML:**
   - Detects if text contains `proxies:` or `proxy-groups:`.
   - Delegates to `ClashYamlParser`.
2. **Base64 or Plain Text Share Link List:**
   - Tries base64 decode; falls back to raw text.
   - Iterates lines; strips comments starting with `#`.
   - Parses lines starting with supported link schemes (`vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`).

### Formats Currently UNHANDLED (Cause Silent Failures or "No Proxies Found")
1. **sing-box JSON Configs:**
   - Output from panels formatted as full sing-box JSON (`{"outbounds": [...]}`) is treated as plain text; line-by-line parsing fails because JSON lines are not `vless://` URLs.
2. **Xray / V2Ray JSON Configs:**
   - Single or array JSON configs with `outbounds` (e.g. `vnext`, `streamSettings`).
3. **SIP008 Shadowsocks JSON:**
   - Standard Shadowsocks server specification (`{"servers": [...]}`).
4. **HTML Error / Web Pages:**
   - Panels returning a web page (e.g. `<!DOCTYPE html>` or `<html>`) because the link is expired, requires device binding, or represents a user web panel are passed to the base64/link parser, which finds 0 links and returns "No valid proxy profiles found in subscription".
5. **Modern Protocols / Transports:**
   - `tuic://` links are not handled in `LinkParser`.
   - `xhttp` / `httpupgrade` transports in VLESS.
   - IPv6 literal hosts formatted as `[2001:db8::1]` in URL authority.

---

## 3. Where "No Proxies Found" is Produced

1. **`SubscriptionDecoder.kt#L68-L70`:**
   ```kotlin
   if (profiles.isEmpty()) {
       return Result.Error(IllegalArgumentException("No valid proxy profiles found in subscription"))
   }
   ```
   This single generic error masks all failure causes:
   - Server returned a 200 OK with HTML error page ("Device limit reached", "Subscription inactive").
   - Server returned a valid sing-box or Xray JSON config.
   - Server returned unsupported link schemes or malformed lines.
   - Server returned an empty list.

2. **`ServersViewModel.kt#L343`:**
   ```kotlin
   onComplete(false, "Не удалось загрузить подписку: ${result.exception.message}")
   ```
   Renders the raw English exception message in a toast/status bar without explaining *why* 0 proxies were found or what action the user should take.

---

## 4. Silent Node Dropping (Loss of Legitimate Servers)

The following locations silently drop nodes without logging or notifying the user:

1. **`SubscriptionDecoder.kt#L60-L65`:**
   ```kotlin
   val parsed = LinkParser.parse(candidate)
   if (parsed is Result.Success) {
       profiles.add(parsed.data)
   }
   // Result.Error is completely discarded with NO count or error logging!
   ```
   If a subscription contains 10 nodes and 3 have minor formatting quirks, the 3 are silently dropped, giving the user 7 nodes without notice. If all 10 fail, it reports "No valid proxy profiles found".

2. **`ClashYamlParser.kt`:**
   Unsupported proxy types or transport configurations in YAML are skipped during iteration without collecting or reporting dropped counts.

3. **`SubscriptionRepository.kt#L93-L98` on Refresh:**
   ```kotlin
   profileRepository.deleteProfilesBySubscription(id)
   profileRepository.saveProfiles(linkedProfiles)
   ```
   If a refreshed subscription returns fewer nodes (e.g. server maintenance), previously saved nodes are wiped from the database. Furthermore, node IDs are regenerated, destroying user custom settings, favorites, and latency test history.

---

## 5. Audit Summary & Action Items

| Component | Defect | Solution (Milestones S2–S5) |
| :--- | :--- | :--- |
| **Headers** | Spoofs 3 commercial clients; lacks device headers. | Set default UA to `Nebula/<version> (Android; sing-box/<core>)`. Add per-sub UA presets and fallback ladder. Add optional random UUID HWID device headers (Remnawave standard). |
| **Parser** | Missing JSON formats (sing-box, Xray, SIP008), TUIC, HTML detection. | Multi-format content sniffer with dedicated JSON, YAML, Base64, and HTML analyzers. |
| **Error Reporting** | Single vague `No valid proxy profiles found` error. | Granular error classification (HTML page, device limit, empty, invalid format, network timeout) with localized diagnostics. |
| **Silent Drops** | Discarded lines without count. | Diagnostic counter tracking `totalEntries`, `parsedNodes`, `skippedEntries` with granular reasons. |
| **Refresh Safety** | Wipes nodes and IDs on refresh. | Non-destructive update preserving stable IDs, user favorites, and retaining previous nodes on refresh failure. |
