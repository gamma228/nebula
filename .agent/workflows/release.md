# Release Workflow (/release)
Build production release APKs:
```bash
./gradlew assembleRelease
```
- Verify Proguard/R8 shrinking and obfuscation rules
- Verify ABI splits (`arm64-v8a`, `armeabi-v7a`)
- Check APK with `apksigner verify`
- Test installation and connectivity on test device or emulator
