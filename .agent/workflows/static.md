# Static Analysis & Quality Guards Workflow (/static)

Run static checks and enforce quality guards:
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat lintDebug
```

Verify static rules across production code:
1. No `GlobalScope`
2. No `Thread.sleep` in production code
3. All threads/dispatchers named or structured
4. No unbounded `while(true)` without suspension
5. Zero Lint errors for Correctness, Security, Performance
