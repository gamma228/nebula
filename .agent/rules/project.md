# Nebula Project Rules & Constraints

## Core Architecture & Tech Stack
- Package: `app.nebula.vpn`
- 100% Kotlin, Jetpack Compose + Material 3, Clean Architecture + MVVM/MVI, Coroutines/Flow, Room, DataStore, Hilt, WorkManager.
- minSdk: 26, targetSdk: 35, compileSdk: 35.
- Real proxy core: sing-box / libbox (GPL-3.0) pinned at v1.14.4 (SHA-256: `A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996`).
- Native TUN hand-off via platform callback (`VpnService.Builder` built inside callback).

## Operating Rules
1. **Reproduce First**: For every reported problem, write a failing automated test or scripted reproduction BEFORE changing code. Record cause, fix, and proof in `BUGLOG.md`.
2. **Evidence Over Claims**: Maintain `VERIFICATION.md` with exact commands and real outputs.
3. **No Silent Stubs**: Any incomplete feature must be documented in `PROGRESS.md` under "Known limitations".
4. **Reliable Disconnect Priority**: Disconnect must complete within 3 seconds in the worst case, release all resources (TUN fd, notification, stopSelf, START_NOT_STICKY), never block main thread, and set `userRequestedDisconnect=true`.
5. **No Long Scrolling for Primary Actions**: No screen may hide primary actions at the bottom of a long scroll. Use bottom navigation, segmented tabs, sticky headers, or pinned bottom bars.
6. **Privacy**: Zero analytics, telemetry, crash-reporting SDKs, ad SDKs, or Google Play Services. HTTPS-only subscriptions. Secrets encrypted with Android Keystore.
7. **Licensing**: GPL-3.0. `LICENSE`, `THIRD_PARTY_NOTICES.md`, and notices in `README.md`.
8. **Localization**: English (default), Russian, Ukrainian, Simplified Chinese, German. Parity tests required across all 5 locales.
9. **UI & Customization**: Palette: Deep Black, Slate Grey, Pure White, Electric Violet, Crimson Red. Themes: OLED Crimson & Shadow, Cyber Violet & Ash, Monochrome Minimalist, plus Custom Theme Creator.
