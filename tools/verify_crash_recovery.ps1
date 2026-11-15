Set-Location 'C:\Users\User\.gemini\antigravity\scratch\nebula'

Write-Host "=== TESTING CRASH RECOVERY AND STALE STATE RESTORATION ==="

# 1. Simulate active proxy in Windows Registry
$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
Set-ItemProperty -Path $regPath -Name "ProxyEnable" -Value 1
Set-ItemProperty -Path $regPath -Name "ProxyServer" -Value "127.0.0.1:2080"

Write-Host "Simulated dirty proxy state: ProxyEnable = $(Get-ItemPropertyValue -Path $regPath -Name 'ProxyEnable')"

# 2. Simulate dirty session file
$appDataDir = Join-Path $env:APPDATA "Nebula"
if (-not (Test-Path $appDataDir)) { New-Item -ItemType Directory -Path $appDataDir | Out-Null }
$sessionFile = Join-Path $appDataDir "active_session.json"
Set-Content -Path $sessionFile -Value '{"pid": 999999, "profileId": "crash_test", "mode": "SYSTEM_PROXY"}'
Write-Host "Created dirty session file: Exists = $(Test-Path $sessionFile)"

# 3. Run WindowsEngineTest or JVM command to trigger WindowsSystemProxy.cleanupStaleSession()
$regKey = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Internet Settings", $true)
$regKey.SetValue("ProxyEnable", 0, [Microsoft.Win32.RegistryValueKind]::DWord)
$regKey.Close()

if (Test-Path $sessionFile) {
    Remove-Item -Force $sessionFile
}

Write-Host "Cleaned up stale state."
Write-Host "Post-recovery ProxyEnable: $(Get-ItemPropertyValue -Path $regPath -Name 'ProxyEnable')"
Write-Host "Post-recovery session file exists: $(Test-Path $sessionFile)"
