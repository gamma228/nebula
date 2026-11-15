# Nebula Performance Benchmark Script
param (
    [string]$AppPath = "desktop/build/compose/binaries/main/app/Nebula/Nebula.exe"
)

$ErrorActionPreference = "Continue"

Write-Host "=== NEBULA BENCHMARK SCRIPT ===" -ForegroundColor Cyan

# 1. Check binaries
if (!(Test-Path $AppPath)) {
    Write-Warning "App executable not found at $AppPath"
} else {
    $exeSize = (Get-Item $AppPath).Length
    Write-Host "Executable Size: $([math]::Round($exeSize / 1KB, 2)) KB"
}

# 2. Installed size
$dirPath = Split-Path $AppPath -Parent
if (Test-Path $dirPath) {
    $dirSize = (Get-ChildItem -Recurse $dirPath | Measure-Object -Property Length -Sum).Sum
    Write-Host "Total Installed Directory Size: $([math]::Round($dirSize / 1MB, 2)) MB"
}

# 3. Running process metrics
$nebulaProcs = Get-Process -Name "*Nebula*" -ErrorAction SilentlyContinue
if ($nebulaProcs) {
    Write-Host "Active Nebula Processes Found:" -ForegroundColor Green
    foreach ($p in $nebulaProcs) {
        $wsMB = [math]::Round($p.WorkingSet64 / 1MB, 2)
        $pmMB = [math]::Round($p.PrivateMemorySize64 / 1MB, 2)
        Write-Host " - PID: $($p.Id) ($($p.ProcessName)): WS = $wsMB MB, Private = $pmMB MB, CPU = $($p.CPU)s"
    }
} else {
    Write-Host "No active Nebula process running."
}

# 4. Sing-box core metrics
$coreProcs = Get-Process -Name "*sing-box*" -ErrorAction SilentlyContinue
if ($coreProcs) {
    Write-Host "Active sing-box Core Processes:" -ForegroundColor Green
    foreach ($cp in $coreProcs) {
        $wsMB = [math]::Round($cp.WorkingSet64 / 1MB, 2)
        Write-Host " - PID: $($cp.Id) ($($cp.ProcessName)): WS = $wsMB MB, CPU = $($cp.CPU)s"
    }
} else {
    Write-Host "No active sing-box process running."
}
