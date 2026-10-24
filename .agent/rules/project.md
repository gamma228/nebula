# Nebula Project Rules & Constraints

## Core Architecture & Tech Stack
- Package: `app.nebula.vpn`
- 100% Kotlin, Jetpack Compose + Material 3, Clean Architecture + MVVM/MVI, Coroutines/Flow, Room, DataStore, Hilt, WorkManager, CameraX.
- minSdk: 26, targetSdk: 35, compileSdk: 35.
- ABI splits: `arm64-v8a`, `armeabi-v7a` (plus `x86_64` for debug/emulator).
- R8 enabled for release with verified keep rules for libbox.
- `android:allowBackup="false"`.
- Real proxy core: sing-box / libbox (GPL-3.0). TUN hand-off via platform callback (`VpnService.Builder` built inside callback).

## Operating Rules
1. **Autonomy**: Execute phases systematically. After EACH phase: run build and test, fix until green, `git commit` with descriptive message, update `PROGRESS.md`.
2. **Evidence Over Claims**: Maintain `VERIFICATION.md` with exact commands and real outputs.
3. **No Silent Stubs**: Any incomplete feature must be documented in `PROGRESS.md` under "Known limitations".
4. **Security / Supply Chain**: Pinned dependency versions. Official sources only. Sing-box built from pinned official tag (`v1.14.4`), SHA-256 recorded.
5. **Privacy**: Zero analytics, telemetry, crash-reporting SDKs, ad SDKs, or Google Play Services. Network calls restricted to user proxy traffic and user-triggered updates. Cleartext forbidden (`cleartextTrafficPermitted=false`). No secrets in logs.
6. **Licensing**: GPL-3.0. `LICENSE`, `THIRD_PARTY_NOTICES.md`, and notices in `README.md`.
7. **Clean Architecture**: Sealed `Result` types, no silent catch blocks, no global mutable singletons (all state through Hilt repositories and Flow).
8. **Localization**: English (default), Russian, Ukrainian, Simplified Chinese, German. Parity tests required across all 5 locales. Plural and positional argument formatting respected.
9. **UI & Customization**: Deep Black, Slate Grey, Pure White, Electric Violet, Crimson Red. Themes: OLED Crimson & Shadow, Cyber Violet & Ash, Monochrome Minimalist, plus Custom Theme Creator.
