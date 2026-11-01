TASK: BUILD A WINDOWS DESKTOP CLIENT BASED ON THE EXISTING ANDROID VPN/PROXY APP.
The Android app is finished and works (Kotlin, Jetpack Compose, Material 3, sing-box core, multi-protocol subscriptions, split tunneling, custom routing rules, Connection Doctor, five UI languages: ru, en, uk, zh, de, custom themes/wallpapers). The Windows client must feel like the same product: same design language, same features where they make sense on desktop, same behavior. No forced routing presets or limits: the user configures routing himself. Keep everything universal for mixed-protocol subscriptions (VLESS with Reality / TLS / gRPC / WS / TCP / xhttp, VMess, Trojan, Shadowsocks, Hysteria2, TUIC).

HARD RULES
1. Do NOT break the Android app. Every refactor that touches existing Android code must keep the Android build compiling and all its existing tests green. Run them after every extraction step and record the output. If something breaks Android, revert that step.
2. Evidence first. No claim without a command and its real output. If you cannot run something (for example you are not on Windows, or have no admin rights), mark it UNVERIFIED and say exactly why and what the user must run.
3. No new features beyond parity and what is listed here. No telemetry, no analytics, no ads, no network calls except to the user's own servers, the subscription URLs, and the diagnostic endpoints the Doctor uses.
4. Secrets (UUIDs, passwords, keys, subscription URLs) never go into logs, crash reports, or diagnostic reports.

=== MILESTONE W1: ARCHITECTURE AND SHARED CORE ===
- Inspect the repo first. Write docs/WINDOWS_PLAN.md: current module structure, what is Android-specific, what is portable.
- Stack: Kotlin Multiplatform with Compose Multiplatform for Desktop (JVM, Windows target). Reason: reuse the share-link parsers, profile models, sing-box config generator, routing model, Doctor logic, localization strings and theme tokens from Android. If you find a serious reason this is the wrong choice, explain it in the plan with evidence before deviating.
- Create a shared module (for example :core) with ONLY platform-independent code: URI parsers, profile model, sing-box config generator, routing rules model, subscription fetching/merging, Doctor logic that does not depend on Android APIs, localization resources. Move code step by step. After each move: Android builds, Android tests pass, shared module tests pass.
- Android-only things (VpnService, Android network callbacks, notifications, Room if Android-specific) stay in the Android module behind interfaces. Define the interfaces the desktop will implement: CoreController (start, stop, status), StorageRepository, SystemIntegration (autostart, system proxy, notifications), TrafficStats.
- Storage on desktop: SQLite or JSON in %APPDATA%\<AppName>\. Profiles and subscription URLs encrypted at rest with Windows DPAPI (via JNA or equivalent). Settings in a plain JSON file. Migration versioning from day one.
- Add the parser, round-trip and config-generation test suite (from the Android project) to the shared module and make sure it runs on the JVM.

=== MILESTONE W2: SING-BOX ON WINDOWS ===
- Use the official sing-box Windows binary (amd64, plus arm64 if easy) as a separate child process. Do not embed it as a library. Pin the version, record its SHA256 in a file, verify the hash at build time and at first run. Document where it comes from. Do the same for wintun.dll.
- Modes:
  * TUN mode (full tunnel): needs administrator rights. Use wintun, auto_route, strict_route as in the Android config logic, IPv6 off by default as in Android.
  * System proxy mode: sing-box mixed inbound on 127.0.0.1 only (random free port), set the Windows system proxy through the registry/WinINET and notify the system. This mode needs no admin rights.
- Privileges: do NOT make the user click UAC on every connect. Preferred design: a small helper Windows service (or scheduled task running with highest privileges) installed once by the installer with one UAC prompt, which starts and stops the sing-box TUN process on request from the UI over a locked-down local channel (named pipe with ACL limited to the current user). The UI itself runs unprivileged. If this is too large, implement the fallback: the app asks for elevation once at TUN start, and document the limitation. Record which one you built.
- Safety and cleanup (critical): the system proxy setting and any routes/DNS changes MUST be restored on normal exit, on disconnect, on crash, and on next start (detect a stale state file and clean it). A user must never be left without internet because our app died. Test this by killing the process.
- Loop protection and DNS: same rules as Android. Proxy server hostname resolved via bootstrap resolver by IP outside the tunnel, DoH through the proxy outbound, no DNS loop, QUIC (UDP 443) blocked by default like on Android so browsers fall back to TCP, own sing-box process excluded from the tunnel. Local control API (clash/v2ray stats API) bound to 127.0.0.1 only with a random secret and port.
- Dump the generated config to a debug file (secrets masked) on every connect, like on Android. Run `sing-box check` on the generated config before starting; show a clear error if it fails.
- Per-app split tunneling on Windows: routing rules by process name and by executable path (sing-box process_name / process_path rules), with a UI to pick running processes or browse for an .exe. Domain, IP/CIDR and geo rules work exactly as on Android.

=== MILESTONE W3: DESKTOP UI ===
- Same visual language as Android: colors, typography, shapes, dark/light and the custom theme/wallpaper system adapted to desktop. Use the shared theme tokens.
- Layout: left navigation rail or sidebar with Home, Servers, Routing, Tools (Doctor, logs, speed test), Settings. Window minimum size 900x600, resizable, remembers size and position.
- Home: big connect/disconnect button, current node, live upload/download speed and total traffic, connection time, mode switch (TUN / system proxy). Stats must update continuously, not only when the window is focused.
- Servers: subscription management (add by URL, paste from clipboard, import from file, drag-and-drop a vless:// link or text), group by subscription, real health check (HTTPS request through the node with hard per-node timeout, bounded parallelism, cancelable), sort by delay, favorites, search, edit node fields, copy/export share link, QR code display.
- Routing: rule list with drag-to-reorder, add/edit/delete, process picker, import/export rules. No presets forced on the user.
- Tools: Connection Doctor (see W4), live log viewer with level filter and copy/save, speed test.
- Settings: language (ru, en, uk, zh, de), theme, autostart with Windows (registry Run key or Task Scheduler, minimized to tray), start minimized, auto-connect on launch, auto-update subscriptions interval, TUN stack/MTU advanced options, core version info, export/import all settings and profiles as an encrypted backup file, clear data.
- System tray: icon with connected/disconnected state, menu (connect/disconnect, switch mode, open window, quit). Closing the window minimizes to tray (configurable). Quit really stops the core and restores the system state. Single-instance: a second launch brings the first window to front.
- Deep links: register the vless://, vmess://, trojan://, ss://, hysteria2:// and a subscription URL scheme (for example via a custom scheme) so links open the app and offer to import.
- Windows notifications for connect, disconnect, errors. Keyboard shortcuts for connect/disconnect and search. Accessibility: keyboard navigation, readable contrast, scaling for 125% / 150% DPI.

=== MILESTONE W4: CONNECTION DOCTOR AND HEALTH CHECKS ===
- Reuse the fixed Android Doctor logic. Rules: never run tunnel tests when not connected; a node counts as working ONLY if a real HTTPS request through it succeeds; TCP-open is shown as "port open, data not verified"; the verdict comes from real results; every failure shows the underlying error text; Copy Diagnostic Report includes sanitized outbound JSON and the last 100 sing-box log lines.
- Windows-specific checks: administrator/helper service status, wintun driver present, firewall blocking sing-box, conflicting VPN adapters, system proxy value matches ours, another app holding the local port, antivirus quarantined sing-box.exe, system DNS leak check, IPv6 leak check, clock skew (breaks Reality/TLS).
- "Check all servers" can never hang: hard timeouts, bounded parallelism, cancel on screen leave.

=== MILESTONE W5: PERFORMANCE, STABILITY, SECURITY ===
- Resource budget: idle RAM and CPU of the UI while connected and minimized must be low (measure and record; the UI must not poll when hidden; stats polled at a reduced rate when the window is hidden). Startup to usable window under 3 seconds on a normal machine (record your measurement).
- Core crash handling: if sing-box exits unexpectedly, restore system state, notify the user, offer reconnect, optional auto-reconnect with backoff.
- Network changes: Wi-Fi/Ethernet switch, sleep/wake, VPN adapter appearing/disappearing, laptop lid close. Test reconnect after sleep/wake.
- Security: local API and any IPC locked to the current user, no secrets in logs, no world-readable files, updater (if any) verifies signatures. No auto-update server calls unless the user enables update checks. Do not store anything in the registry except autostart, deep-link registration and the system proxy.
- Subscription refresh runs in the background, tolerates failures, never wipes good nodes when a refresh returns empty or an error.

=== MILESTONE W6: BUILD, INSTALLER, AND FULL VERIFICATION ===
- Build: Gradle task producing a Windows installer. Prefer an MSI or EXE built with jpackage (with bundled JRE so the user needs no Java), or Inno Setup/WiX if clearly better. The installer: installs per machine, installs the helper service with one UAC prompt, registers deep links, creates Start Menu shortcut, optional desktop shortcut, optional autostart, and a clean uninstaller that removes the service, the registry entries, restores the system proxy, and asks whether to delete user data.
- Also produce a portable ZIP (system proxy mode works without install; TUN needs admin).
- Code signing: prepare the pipeline with a placeholder certificate step and document how the user plugs in a real certificate. State plainly in the README that unsigned builds trigger SmartScreen warnings and that antivirus products may flag any VPN/proxy binary, with instructions to add exclusions.
- README.md: how to build, run, test, and package; known limitations; how to update sing-box.
- Verification (record command + output for each; mark UNVERIFIED anything you could not run on real Windows):
  1. Android: full build and all tests still green after the shared-core extraction.
  2. Shared module and desktop unit tests green (parsers, round-trip, config generation, `sing-box check` for every fixture).
  3. On Windows: connect in system proxy mode and in TUN mode with 3 different nodes of different transports. For each: exit IP through the tunnel, DoH, gen204, GitHub, Telegram, YouTube.
  4. Kill the app process while connected in each mode: confirm the system proxy and routes are restored and the next start cleans stale state.
  5. Sleep/wake, network switch, disconnect/reconnect, quit from tray, start with Windows minimized.
  6. Per-app split tunneling: one app via proxy, one direct, show the exit IP of each.
  7. Doctor while disconnected shows the correct state and no false verdict.
  8. Install, upgrade over an existing install (data preserved), uninstall (clean).
  9. Resource measurements: startup time, idle RAM, idle CPU while connected.

FINAL REPORT
What was built, the architecture (modules, what is shared with Android), which privilege design you implemented (helper service or elevation fallback), test results with real output, what could NOT be verified and why, known limitations, security notes, and the exact next steps for the user (for example certificate, real-device test list). Update PROGRESS.md and README.md.
