# Tor in Russia: Censorship Mechanisms, Transports & Circumvention Research

**Date:** November 2026  
**Subject:** Circumvention Analysis for Nebula Android Client

---

## 1. Executive Summary

Since December 2021, the Russian Federal Service for Supervision of Communications, Information Technology and Mass Media (Roskomnadzor / RKN) and the Main Radio Frequency Center (GRFC) have systematically blocked the Tor network across Russian internet service providers using TSPU (Технические средства противодействия угрозам — Technical Means of Counteracting Threats) deep packet inspection (DPI) hardware.

Direct Tor connections are virtually 100% blocked nationwide. Pluggable transports (obfs4, Snowflake, WebTunnel) experience varying levels of degradation, active probing, and IP-level blacklisting. In regional tests and during mobile network restrictions, whitelist-style filtering ("белые списки") drops all unapproved foreign traffic entirely.

To achieve robust connectivity in Russia, Nebula employs a **multi-tier strategy**:
1. Built-in modern Pluggable Transports (WebTunnel, Snowflake with AMP/SQS rendezvous, obfs4).
2. Pluggable Transport rotation from fresh bridge pools.
3. **Carrier Mode ("Tor via my server"):** Tunneling Tor bridge connections inside an existing working circumvention protocol (VLESS Reality, Trojan, Hysteria2, Shadowsocks). The ISP observes only the carrier protocol, while the carrier server observes only Tor's outer encryption.

---

## 2. Analysis of Tor Blocking Mechanisms by Russian TSPU

### 2.1 Direct Tor Protocol (Vanilla Relays)
- **Status:** **Blocked Nationwide (Fixed & Mobile).**
- **Mechanism:**
  - **IP Blacklisting:** Automated scraping of public Tor consensus directory documents (Authority Relays, Fallback Directories, Guard Relays).
  - **DPI Heuristics:** Detection of the Tor TLS handshake (specific TLS cipher suite lists, omission of SNI or randomized server names, characteristic packet size distributions during the initial TLS certificate exchange).
  - **Enforcement:** TSPU injects TCP RST packets or silently drops TCP SYN-ACK / data packets towards relay ports (typically 9001, 443, 9030).

### 2.2 obfs4 (Lyrebird)
- **Status:** **Degraded / Heavily Filtered.**
- **Mechanism:**
  - **Entropy & Size Analysis:** On mobile networks (Megafon, MTS, Beeline, T2/Tele2), TSPU applies protocol whitelisting heuristics that flag high-entropy bidirectional streams without valid TLS headers.
  - **Active Probing:** Russian sensor nodes detect potential obfs4 endpoints and initiate probing packets with random payloads to determine if the port behaves as an obfs4 reflector.
  - **Bridge IP Scraping:** Public obfs4 bridges distributed via BridgeDB / Moat are blocked within 24 to 72 hours of publication.
  - **Verdict:** Works sporadically on some regional fixed ISPs; largely unreliable on mobile data without private bridges.

### 2.3 Snowflake
- **Status:** **Partially Working / Throttled / Slow.**
- **Mechanism:**
  - **Signaling Blockades:** Default domain fronting addresses (`ajax.aspnetcdn.com`, `snowflake-broker.torproject.net`) are blocked via SNI inspection and DNS poisoning.
  - **Alternative Rendezvous:** Snowflake supports AMP Cache rendezvous (`https://cdn.ampproject.org/`) and Amazon SQS queues (`sqs.us-east-1.amazonaws.com`), which remain partially accessible.
  - **STUN/TURN Throttling:** TSPU throttles or drops UDP WebRTC sessions to non-domestic STUN/TURN servers.
  - **Throughput:** Typically bandwidth-constrained (50–300 Kbps), leading to high bootstrap times (60–180 seconds).
  - **Verdict:** Valuable as an emergency fallback, but insufficient as a daily high-speed connection.

### 2.4 WebTunnel
- **Status:** **Best Performing Direct Transport (When Bridges Are Fresh).**
- **Mechanism:**
  - WebTunnel encapsulates Tor traffic inside an HTTP/2 or HTTP/3 HTTPS stream to a reverse-proxy web server (e.g., NGINX / Caddy behind Cloudflare or a dedicated VPS).
  - The TLS handshake is indistinguishable from standard browser HTTPS (valid certificates, legitimate SNI, modern ALPN).
  - Censors cannot distinguish WebTunnel traffic using passive DPI without having the secret path URL.
  - **Attack Vector:** Censors scrape public bridge distributions (Telegram bot `@GetBridgesBot`, email, bridges.torproject.org). Once a bridge IP/domain is flagged, TSPU blocks the IP or SNI.
  - **Shelf Life:** Public bridges last 1–3 weeks; user-deployed private WebTunnel bridges remain unblocked indefinitely.

### 2.5 Whitelist Restrictions ("Белые Списки")
- **Status:** **Observed during regional alerts and testing phases.**
- **Mechanism:** Mobile operators drop all traffic to IP addresses not listed in the National Domain Name System (НСДИ) or approved Russian enterprise ASNs.
- **Impact on Tor:** All foreign bridges and STUN servers are unreachable.
- **Solution:** A domestic or whitelist-passing carrier proxy (e.g. an intermediate server or CDN with domestic peering) over which Tor traffic is routed.

---

## 3. Pluggable Transport Spec & Upstream Proxying

According to the Tor Pluggable Transport specification (PT v2.1):
- A PT client may accept an upstream proxy via the environment variable `TOR_PT_PROXY` or command-line/configuration flags.
- `IPtProxy` (Go wrapper by Benjamin Erhart / Guardian Project / Tor Project):
  - Exposes `Controller.Start(methodName string, proxy string)`.
  - The `proxy` argument accepts `socks5://[user:pass@]host:port` or `http://host:port`.
  - For Lyrebird (`obfs4`, `webtunnel`), incoming client SOCKS connections are dialed through `proxy.FromURL(proxyURL, proxy.Direct).Dial`.
  - For Snowflake, WebRTC signaling and data channel connections can be established via the upstream proxy.

---

## 4. Threat Model Comparison: Direct vs Carrier Tor

| Dimension | Direct Tor / Bridges | Tor via Carrier (Nebula Carrier Mode) |
| :--- | :--- | :--- |
| **ISP Visibility** | Sees connection to Tor Bridge IP or Snowflake broker. Can flag Tor usage. | Sees only connection to Carrier Server (VLESS/Reality/Trojan). Tor is completely invisible to ISP. |
| **Carrier Visibility** | N/A (no carrier) | Sees incoming connection from user; sees outgoing connection to Tor bridge. Cannot see user's unencrypted payload or final destination. |
| **TSPU Resistance** | Vulnerable to bridge scraping, WebRTC throttling, and whitelist bans. | Maximum resistance: inherits anti-censorship resilience of VLESS Reality / Hysteria2. |
| **Censorship Resilience** | Medium (requires frequent bridge rotation) | High (connects anywhere the carrier connects) |

---

## 5. Architectural References & Sources

1. **Tor Project Metrics — Russia:**  
   `https://metrics.torproject.org/userstats-bridge-country.html?country=ru`
2. **OONI Report — The Tor Project Censorship in Russia (2021–2025):**  
   `https://ooni.org/post/2021-russia-tor-blocking/`
3. **Pluggable Transport Specification v2.1:**  
   `https://spec.torproject.org/pt-spec/index.html`
4. **Onionmasq (Tor VPN Android Native Stack):**  
   `https://gitlab.torproject.org/tpo/core/onionmasq`
5. **IPtProxy Pluggable Transports Wrapper:**  
   `https://github.com/tladesignz/IPtProxy`
6. **Arti (The Tor Project Rust Implementation):**  
   `https://gitlab.torproject.org/tpo/core/arti`


---

## 6. Strategic Alternative for Russia: Public Tor Relays via Carrier Mode

### 6.1 The Core Problem with Bridges in Russia
Bridge distribution mechanisms (Telegram bots, email, web mirrors) suffer from rapid censorship cycles:
- TSPU scraping nodes systematically request bridges from `@GetBridgesBot` and BridgeDB, blocking IP addresses within hours or days.
- In-process Go runtime collisions currently prevent bundling Lyrebird/IPtProxy inside the same Android process as sing-box (`libbox.so`).
- Direct vanilla bridges are blocked by IP matching against consensus lists.

### 6.2 The Solution: Any Public Tor Relay as an Entry Bridge
When Carrier Mode is enabled in Nebula:
1. **TSPU Bypass:** The device communicates with the carrier server using VLESS Reality (or Trojan / Shadowsocks), appearing as benign TLS traffic to a legitimate SNI. TSPU cannot inspect the stream.
2. **Access to the Full Tor Network:** From the egress point of the carrier VPS (outside Russia), direct connections to the public Tor network are completely unblocked.
3. **Using Relays as Bridges:** Any of the ~7,000 active Tor relays published in the public Tor consensus directory can be configured as a custom bridge line in Nebula:
   ```text
   <Relay-IP>:<ORPort> <Identity-Fingerprint>
   ```
4. **How It Operates:**
   - Nebula's `LocalCarrierForwarder` listens on loopback (`127.0.0.1:<port>`).
   - `TorEngine` points Arti to `127.0.0.1:<port> <Fingerprint>`.
   - `LocalCarrierForwarder` connects through the carrier server's SOCKS5 inbound, tunneling TCP to `<Relay-IP>:<ORPort>`.
   - Arti performs the standard Tor TLS channel handshake with the public relay. The cryptographic identity fingerprint matches the expected key.
   - Arti downloads the full directory consensus through this entry guard and builds 3-hop circuits across the Tor network.

### 6.3 Honest Privacy & Security Analysis
- **ISP View:** The ISP observes only the user's connection to their personal VPN server (e.g., VLESS Reality to a non-blacklisted foreign IP). The ISP has no knowledge that Tor is being used.
- **Carrier Server View:** The carrier server sees TCP connections to Tor relay ORPorts (ports 443, 9001, etc.). The carrier operator knows that the client is entering the Tor network, but cannot inspect destination URLs, DNS records, or decrypted payload data (since payload data is encrypted end-to-end through Tor's 3 onion layers).
- **Tor Guard / Entry Node View:** The Tor entry relay sees the IP address of the carrier server (not the user's residential Russian IP). The user gains an extra layer of origin IP masking.
