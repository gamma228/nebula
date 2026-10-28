# Release Integrity Check Workflow (/release-check)

Verify signed release artifacts with R8 minification and APK Signature Scheme v2:
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat assembleRelease

# Verify signatures with Android SDK apksigner
C:\Users\User\Android\Sdk\build-tools\35.0.0\apksigner.bat verify --verbose app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Verification gates:
1. R8 optimization enabled with full keep rules
2. ABI splits (arm64-v8a, armeabi-v7a, x86_64, universal)
3. APK Signature Scheme v2 verified
4. Zero secret keys or plaintext passwords committed
