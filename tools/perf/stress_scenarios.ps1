# Automated Stress & ANR Reproduction Scenarios for Nebula Android
param(
    [string]$PackageName = "app.nebula.vpn",
    [string]$ActivityName = "app.nebula.vpn.ui.MainActivity",
    [string]$AdbPath = "C:\Users\User\Android\Sdk\platform-tools\adb.exe",
    [int]$Cycles = 20
)

Write-Host "=================================================="
Write-Host "  NEBULA STRESS & ANR REPRODUCTION SCENARIOS"
Write-Host "=================================================="

# Scenario 1: Memory Pressure Injection
Write-Host "`n[Scenario 1] Low-Memory Pressure Injection..."
& $AdbPath shell "am send-trim-memory $PackageName RUNNING_CRITICAL"
Start-Sleep -Seconds 1
& $AdbPath shell "am send-trim-memory $PackageName COMPLETE"
Write-Host "  Trim-memory signals dispatched (RUNNING_CRITICAL, COMPLETE)."

# Scenario 2: Background CPU Contention
Write-Host "`n[Scenario 2] Inducing CPU Contention (10s burner)..."
& $AdbPath shell "sh -c '(while true; do :; done) &' ; BURNER_PID=\$!; sleep 8; kill -9 \$BURNER_PID"
Write-Host "  CPU contention burner finished."

# Scenario 3: Rapid Screen Off / Screen On Transition
Write-Host "`n[Scenario 3] Screen Off / On Cycle..."
& $AdbPath shell input keyevent 26 # Screen off
Start-Sleep -Seconds 2
& $AdbPath shell input keyevent 26 # Screen on
& $AdbPath shell input keyevent 82 # Unlock
Write-Host "  Screen toggled successfully."

# Scenario 4: Rapid Navigation & Taps Stress
Write-Host "`n[Scenario 4] Rapid UI Navigation & Tab Stress ($Cycles cycles)..."
for ($i = 1; $i -le $Cycles; $i++) {
    # Tap Settings (291, 616) -> Tap Tools (225, 616) -> Tap Routing (159, 616) -> Tap Home (28, 616)
    & $AdbPath shell input tap 291 616
    & $AdbPath shell input tap 225 616
    & $AdbPath shell input tap 159 616
    & $AdbPath shell input tap 28 616
}
Write-Host "  Completed $Cycles navigation loops."

# Scenario 5: Check Watchdog and System Exit Reasons
Write-Host "`n[Scenario 5] Checking Exit Reasons and Watchdog Dumps..."
$exitInfo = & $AdbPath shell "dumpsys activity exit-info $PackageName | grep -E 'REASON_|subreason|timestamp' | head -n 15"
Write-Host "Recent Exit Reasons:"
$exitInfo | Out-String | Write-Host

$stallFiles = & $AdbPath shell "ls -l /data/data/$PackageName/files/stability/ 2>/dev/null"
Write-Host "Watchdog Stall Files:"
$stallFiles | Out-String | Write-Host

Write-Host "=================================================="
Write-Host "Stress Scenarios Complete."
