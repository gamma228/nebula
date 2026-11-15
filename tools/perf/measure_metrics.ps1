# Automated Performance Measurement Script for Nebula Android
param(
    [string]$PackageName = "app.nebula.vpn",
    [string]$ActivityName = "app.nebula.vpn.ui.MainActivity",
    [string]$AdbPath = "C:\Users\User\Android\Sdk\platform-tools\adb.exe"
)

Write-Host "=================================================="
Write-Host "  NEBULA ANDROID PERFORMANCE MEASUREMENT SETUP"
Write-Host "=================================================="

# Check ADB
if (-not (Test-Path $AdbPath)) {
    Write-Error "ADB not found at $AdbPath"
    exit 1
}

$devices = & $AdbPath devices
Write-Host "Connected devices:"
$devices | Out-String | Write-Host

# 1. Measure Cold Start Time (avg of 3 runs)
Write-Host "`n[1/4] Measuring Cold Start Time..."
$coldStarts = @()
for ($i = 1; $i -le 3; $i++) {
    & $AdbPath shell am force-stop $PackageName
    Start-Sleep -Seconds 1
    $startOut = & $AdbPath shell am start -W -n "$PackageName/$ActivityName"
    $totalTimeLine = $startOut | Select-String "TotalTime:"
    if ($totalTimeLine) {
        $timeMs = [int]($totalTimeLine -replace "TotalTime:\s*", "").Trim()
        Write-Host "  Run $i TotalTime: ${timeMs}ms"
        $coldStarts += $timeMs
    }
}
$avgColdStart = ($coldStarts | Measure-Object -Average).Average
Write-Host "Average Cold Start: $([math]::Round($avgColdStart, 1)) ms"

# Wait for UI to stabilize
Start-Sleep -Seconds 3

# 2. Get Process PID
$pidStr = (& $AdbPath shell "pidof $PackageName").Trim()
Write-Host "`nProcess PID: $pidStr"

# 3. Measure RAM Usage (Dumpsys meminfo)
Write-Host "`n[2/4] Measuring RAM Usage (Foreground Idle)..."
$memInfo = & $AdbPath shell dumpsys meminfo $PackageName
$totalPssLine = $memInfo | Select-String "TOTAL PSS:"
$nativeHeapLine = $memInfo | Select-String "Native Heap" | Select-Object -First 1
$dalvikHeapLine = $memInfo | Select-String "Dalvik Heap" | Select-Object -First 1

Write-Host "  $totalPssLine"
Write-Host "  Native: $nativeHeapLine"
Write-Host "  Dalvik: $dalvikHeapLine"

# 4. Measure CPU Utilization
Write-Host "`n[3/4] Measuring CPU Utilization (3 samples)..."
$cpuSamples = @()
for ($i = 1; $i -le 3; $i++) {
    $topLine = & $AdbPath shell "top -b -n 1 | grep $PackageName" | Select-Object -First 1
    Write-Host "  Sample ${i}: $topLine"
    Start-Sleep -Seconds 1
}

# 5. Measure UI Jank (dumpsys gfxinfo)
Write-Host "`n[4/4] Measuring Frame Rendering & Jank..."
& $AdbPath shell dumpsys gfxinfo $PackageName reset | Out-Null
# Simulate some scrolling/interaction
& $AdbPath shell input swipe 160 500 160 200 300
& $AdbPath shell input swipe 160 200 160 500 300
Start-Sleep -Seconds 1
$gfxInfo = & $AdbPath shell dumpsys gfxinfo $PackageName
$totalFramesLine = $gfxInfo | Select-String "Total frames rendered:"
$jankyFramesLine = $gfxInfo | Select-String "Janky frames:"
$percentileLine = $gfxInfo | Select-String "90th percentile:"

Write-Host "  $totalFramesLine"
Write-Host "  $jankyFramesLine"
Write-Host "  $percentileLine"

# 6. Check MainThreadWatchdog Stalls
Write-Host "`nChecking for Watchdog Stall Dumps..."
$stallFiles = & $AdbPath shell "ls /data/data/$PackageName/files/stability/ 2>/dev/null"
if ($stallFiles) {
    Write-Host "  Stall dumps found: $stallFiles"
} else {
    Write-Host "  Zero main-thread stall dumps detected (OK)."
}

Write-Host "`n=================================================="
Write-Host "Measurement Complete."
