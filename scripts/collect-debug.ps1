# Nebula Diagnostics Collection Script (PowerShell)
param(
    [string]$PackageName = "app.nebula.vpn.debug",
    [string]$OutputDir = "$PSScriptRoot\..\..\diagnostics"
)

$ErrorActionPreference = "Continue"
$Adb = "C:\Users\User\Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $Adb)) {
    $Adb = "adb"
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$TargetDir = "$OutputDir\nebula_diag_$Timestamp"
New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

Write-Host "=== Nebula Diagnostics Collector ===" -ForegroundColor Cyan
Write-Host "Target: $PackageName"
Write-Host "Output: $TargetDir"

$Device = & $Adb get-serialno
Write-Host "Device: $Device"

# 1. Device Info
Write-Host "[1/6] Collecting device info..."
$deviceInfo = @"
=== DEVICE SPECIFICATION ===
Manufacturer: $(& $Adb shell getprop ro.product.manufacturer)
Model: $(& $Adb shell getprop ro.product.model)
Android: $(& $Adb shell getprop ro.build.version.release) (API $(& $Adb shell getprop ro.build.version.sdk))
ABI: $(& $Adb shell getprop ro.product.cpu.abi)
Security Patch: $(& $Adb shell getprop ro.build.version.security_patch)
"@
$deviceInfo | Out-File -FilePath "$TargetDir\device_info.txt" -Encoding utf8

# 2. Package Info
Write-Host "[2/6] Inspecting package dumpsys..."
& $Adb shell dumpsys package $PackageName | Out-File -FilePath "$TargetDir\package_dumpsys.txt" -Encoding utf8

# 3. Network Routes & Interfaces
Write-Host "[3/6] Inspecting network routes & interfaces..."
$netInfo = @"
=== INTERFACES ===
$(& $Adb shell ip addr)

=== ROUTES ===
$(& $Adb shell ip route)

=== RULES ===
$(& $Adb shell ip rule)
"@
$netInfo | Out-File -FilePath "$TargetDir\network_routes.txt" -Encoding utf8

# 4. VPN State & Battery Optimization
Write-Host "[4/6] Inspecting VPN & battery states..."
$vpnInfo = @"
=== BATTERY OPTIMIZATION WHITELIST ===
$(& $Adb shell dumpsys deviceidle whitelist | Select-String $PackageName)

=== CONNECTIVITY VPN STATE ===
$(& $Adb shell dumpsys connectivity | Select-String "VPN")
"@
$vpnInfo | Out-File -FilePath "$TargetDir\vpn_system_state.txt" -Encoding utf8

# 5. Meminfo
Write-Host "[5/6] Measuring process memory..."
& $Adb shell dumpsys meminfo $PackageName | Out-File -FilePath "$TargetDir\meminfo.txt" -Encoding utf8

# 6. Sanitized Logcat
Write-Host "[6/6] Collecting sanitized logcat..."
$rawLogs = & $Adb logcat -d -v time | Select-String "$PackageName|Libbox|GoLog|VpnService|SingBox"
$sanitized = $rawLogs | ForEach-Object {
    $line = $_.Line
    $line = [regex]::Replace($line, '("password":\s*")[^"]+', '$1[REDACTED]')
    $line = [regex]::Replace($line, '(pbk=)[^& \r\n]+', '$1[REDACTED]')
    $line = [regex]::Replace($line, '(sid=)[^& \r\n]+', '$1[REDACTED]')
    $line = [regex]::Replace($line, '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', '[UUID_REDACTED]')
    $line
}
$sanitized | Out-File -FilePath "$TargetDir\sanitized_logcat.txt" -Encoding utf8

Write-Host "Diagnostics saved to: $TargetDir" -ForegroundColor Green
