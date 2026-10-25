# Nebula Performance Benchmark Runner
param (
    [string]$Mode = "tuned",
    [string]$OutputFile = "docs/bench/results_tuned.json"
)

$ErrorActionPreference = "Stop"
$AdbPath = "C:\Users\User\Android\Sdk\platform-tools\adb.exe"

Write-Host "=================================================="
Write-Host "  NEBULA VPN PERFORMANCE BENCHMARK HARNESS"
Write-Host "=================================================="

# Check ADB device
& $AdbPath devices
$deviceList = & $AdbPath devices
if ($deviceList -notmatch "device\s*$") {
    Write-Warning "No active emulator or device detected. Running local tests."
}

# Run Python Benchmark Suite
Write-Host "Executing benchmark suite..."
python "scripts\bench\benchmark_suite.py" $Mode $OutputFile $AdbPath

Write-Host "Benchmark completed successfully."
