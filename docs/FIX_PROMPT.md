TASK: FIX THE BROKEN VPN CONNECTION. No new features. No new optimizations. No UI redesign. Touch only what is needed for the fix.

SYMPTOMS (evidence from the app's own diagnostics bundle, Samsung SM-S938B, Android 17)
- The VPN connects (key icon, TUN up, server TCP port reachable in 61 ms) but no traffic passes. Doctor reports: DoH via proxy fails, exit IP cannot be determined, Cloudflare / Google gen204 / GitHub CDN fail, Telegram and YouTube fail.
- The same server and the same subscription work in v2rayNG on the same phone. So the server is fine. Our app builds or runs the connection wrongly.
- It broke after the last battery/performance optimization pass.
- Active profile in the report: Protocol VLESS, server 87.242.127.115:443, TLS enabled, SNI river-3-312.rtbcdn.ru, ALPN [], "Reality PubKey: [NONE]", Transport GRPC, transport host and path empty. This looks like parameters lost in parsing or storage (reality block missing, gRPC serviceName missing, ALPN empty).
- The diagnostics bundle was captured with "App State: Disconnected", yet Doctor still gave the verdict "server not passing data". Doctor logic is also wrong.
- Subscription is mixed-protocol (VLESS with Reality / TLS / gRPC / WS / TCP / xhttp, VMess, Trojan, Shadowsocks, Hysteria2, TUIC). Keep everything universal. Do NOT add forced routing presets or restrictions: the user configures routing themselves.

GLOBAL RULES
1. Evidence first. Write a failing test, show it fail, fix, show it pass. Quote real output.
2. Do not guess and do not rewrite working parts. If you cannot run something, mark it UNVERIFIED and say why.
3. Keep a VERIFICATION.md with every command run and its output.
4. If the project has git history, use it. If not, create a baseline commit now.

=== MILESTONE F1: FIND THE REGRESSION ===
- Run `git log` and `git diff` from the last known working state to HEAD. List every change that touches: core start/stop, generated sing-box config, DNS, routing, VpnService, network callbacks, screen-off / Doze handling, stats polling, lazy start, idle pause, socket protection.
- For each optimization, add a debug flag to switch it off. Bisect: connect with all optimizations off. If traffic flows, re-enable one by one and find the culprit. Fix the culprit so it keeps the saving but no longer breaks the data path. If it cannot be fixed safely, revert it and record why.
- Capture the real sing-box log at debug level and logcat for ONE connect attempt. Quote the failing lines.
- Check specifically:
  * Own core sockets bypass the TUN (VpnService.protect, or auto_detect_interface / default_network_callback working). A missing protect gives exactly "TCP connects, nothing flows".
  * The default network callback is registered, survives screen-off, and updates the core on Wi-Fi/mobile change.
  * The core is never paused, throttled or lazily started while connected.
  * TUN settings: stack, MTU, auto_route, strict_route, address, sniff / sniff_override were not removed.
  * The selected outbound tag exists and route.final points to the proxy outbound, not direct or block. Rules were not reordered or dropped.
  * DNS: every DNS server and the DoH server uses a working detour (the proxy outbound). The proxy server's own hostname resolves through a bootstrap resolver by IP, outside the tunnel. No DNS loop. IPv6 stays off.

=== MILESTONE F2: FIX PROFILE PARSING, STORAGE, AND CONFIG GENERATION ===
- Build a table-driven test suite with real-world share-link fixtures (redact secrets) for every protocol and transport. Required VLESS cases: security=reality with pbk, sid, spx, fp, flow=xtls-rprx-vision, sni, type=tcp; security=tls with type=grpc, serviceName, mode=gun, alpn=h2; type=ws with path and host; type=xhttp and httpupgrade; security=none; URL-encoded values; IPv6 host; missing optional params; names with emoji or empty names. Add VMess (base64 JSON), Trojan, Shadowsocks, Hysteria2, TUIC cases.
- Make sure every query parameter is parsed into the model: security, sni, fp, alpn, pbk, sid, spx, flow, type, headerType, host, path, serviceName, mode, authority, allowInsecure, packetEncoding. Never drop a parameter silently: log a warning for unknown ones.
- Fixes expected: Reality must produce a reality block, not a plain TLS block. gRPC must map serviceName correctly. ALPN defaults must be right per transport when the link gives none (h2 for gRPC).
- Round-trip test: link -> model -> Room DB -> model -> sing-box outbound JSON. Check the Room entities, type converters, migrations, and subscription refresh: a field may be parsed correctly and then lost on save or on update.
- Generate the outbound JSON for every fixture and assert exact fields (tls.reality.public_key, short_id, utls.fingerprint, flow, transport.type and service_name, tls.alpn, server_name). Run `sing-box check` on the full generated config for each fixture.
- At connect time dump the final full config to a debug file (secrets masked). Compare it with what v2rayNG/sing-box would produce for the same link and list every difference.
- Add a "view parsed profile" debug screen or log section showing every parsed field, so the user can compare with v2rayNG.

=== MILESTONE F3: FIX CONNECTION DOCTOR AND SERVER CHECKS ===
- Never run tunnel tests when the VPN is not connected. If disconnected, show "VPN is not connected" and run only pre-checks (permission, battery, server TCP reachability).
- A node counts as working ONLY if a real HTTPS request through that outbound succeeds (GET to a generate_204 endpoint, 5 s timeout). TCP connect alone must show "port open, data not verified", never a pass.
- The verdict must come from real results. Blame the server only if a real request through that node failed while connected. If the profile looks suspicious (grpc without serviceName, reality expected but missing, empty ALPN), say "profile problem" and list the suspicious fields.
- Every failed check shows the underlying error (timeout, connection reset, DNS failure, TLS failure, handshake failure) in the UI and in Copy Diagnostic Report. The report also includes the sanitized generated outbound JSON (secrets masked) and the last 100 lines of the sing-box log.
- "Check all servers": hard per-node timeout, bounded parallelism, cancel when the user leaves the screen, so it can never hang. Nodes that open a port but pass no data are marked failed and skipped by auto-select.
- The Disconnect button must stop the core, release the TUN, and remove the key icon within 2 seconds, in every state (connecting, connected, reconnecting).

=== MILESTONE F4: FULL VERIFICATION ===
Run and record command + output for each:
1. All unit tests, parser tests, round-trip tests green.
2. `sing-box check` on every generated config.
3. On a device or emulator: connect with 3 different nodes of different transports from the subscription. For each record exit IP through the tunnel, DoH, gen204, GitHub, Telegram, YouTube.
4. Repeat after: screen off for 2 minutes, Wi-Fi to mobile switch, disconnect and reconnect, app swipe-away, battery saver on.
5. Doctor while disconnected shows the correct state and no false verdict.
6. Disconnect button timing under 2 seconds.
7. List which battery optimizations remain active after the fix and which had to be reverted, and why.

FINAL REPORT
Root cause (file, line, commit, which parameters were lost or wrong), what was changed, test results with real output, what could NOT be verified (real ISP blocking, real battery use, anything you could not run) and why, open bugs, known limitations, and the exact next step.