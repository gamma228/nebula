# 00-MASTER: Nebula VPN Agent Protocol

## Single Source of Truth
At the start of EVERY session and immediately after any context compaction or long tool output:
1. Re-read `docs/MASTER_PROMPT.md` (the master specification).
2. Re-read `PROGRESS.md` (current status, tasks done, in progress, next).
3. Re-read `BUGLOG.md` (tracked issues and regression tests).

## Critical Execution Rules (A1 - A15)
- **A1. CAPABILITY CHECK FIRST**: State real capabilities in message 1 of the run. Never claim anything works unless actually executed.
- **A2. RESUME PROTOCOL**: Update `PROGRESS.md` after EVERY step. Commit often with clear messages.
- **A3. ONE MILESTONE PER RUN**: Work strictly ONE milestone per run (M1 -> M2 -> M3 -> M4 -> M5). Do NOT begin the next milestone until the user explicitly says "continue".
- **A4. REPRODUCE FIRST**: For every bug, write a failing test before the fix. Fix root causes, not symptoms. Keep all regression tests.
- **A5. EVIDENCE**: Record all commands and outputs in `VERIFICATION.md`. Mark unverified items explicitly as NOT VERIFIED.
- **A6. NO PLACEHOLDERS**: Unfinished work belongs under "Known limitations". Zero known P0-P2 bugs.
- **A7. SAFETY & PINNING**: Official sources only. Pin all dependencies and SHA-256 hashes.
- **A8. LICENSES**: GPL-3.0 for app (sing-box integration). Maintain `LICENSE` and `THIRD_PARTY_NOTICES.md`.
- **A9. USER CONTROL IS SACRED**: Never add forced routing presets or silent overrides. Automatic features only adjust technical parameters and must be reversible and disableable.
- **A10. CONTEXT DISCIPLINE**: Do not paste massive files into chat. Use targeted reads/edits.
- **A11. NO SHORTCUTS**: Check every acceptance criteria individually with evidence.
- **A12. THINKING EFFORT**: Deep reasoning on state machine, TUN hand-off, concurrency, and battery spinning.
- **A13. BUDGET & HANDOFF**: Complete the assigned milestone, provide an honest handoff note, and STOP.
- **A14. FACTS & APIs**: Verify real APIs from official docs or artifacts (javap/source).
- **A15. EMULATOR/DEVICE FALLBACK**: If emulator or physical device is unavailable, execute all JVM tests (`./gradlew test`) and mark UI/device items NOT VERIFIED with exact verification commands.
