# Fuzzing & Hostile Input Workflow (/fuzz)

Run property-based and hostile-input fuzz tests against parsers and decoders:
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat testDebugUnitTest --tests "app.nebula.vpn.FuzzTest"
```

Targets:
1. Link parsers (vless, vmess, trojan, ss, hy2)
2. Subscription decoder (Base64, YAML, JSON)
3. Package name parser
4. Config builder under hostile input combinations
