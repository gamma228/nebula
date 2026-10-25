# /soak Workflow: Long-Run Stability & Soak Test

Validates connection stability, memory leak absence, and background lifecycle endurance over extended duration.

## Execution
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
& .\scripts\soak\run_soak_test.ps1 -DurationMinutes 60
```

## Checks
1. Continuous traffic generation through active TUN
2. Memory (RSS) trend sampling — zero upward drift (LeakCanary verification)
3. Background Doze and network transition survival
4. Reconnection resilience on simulated packet loss
