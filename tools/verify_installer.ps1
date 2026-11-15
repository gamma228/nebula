Set-Location 'C:\Users\User\.gemini\antigravity\scratch\nebula'

Write-Host "=== CHECKING INSTALLED FILES ==="
$appPath = Join-Path $env:LOCALAPPDATA 'Programs\Nebula VPN\Nebula.exe'
Write-Host "App Path: $appPath"
Write-Host "Exists: $(Test-Path $appPath)"
if (Test-Path $appPath) {
    Write-Host "Binary Size: $((Get-Item $appPath).Length) bytes"
}

$desktopLnk = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Nebula VPN.lnk'
Write-Host "Desktop Shortcut: $desktopLnk"
Write-Host "Exists: $(Test-Path $desktopLnk)"
if (Test-Path $desktopLnk) {
    $wsh = New-Object -ComObject WScript.Shell
    $sc = $wsh.CreateShortcut($desktopLnk)
    Write-Host "Target: $($sc.TargetPath)"
    Write-Host "Working Dir: $($sc.WorkingDirectory)"
}

$startLnk = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Nebula VPN\Nebula VPN.lnk'
Write-Host "Start Menu Shortcut: $startLnk"
Write-Host "Exists: $(Test-Path $startLnk)"

$uninstaller = Join-Path $env:LOCALAPPDATA 'Programs\Nebula VPN\unins000.exe'
Write-Host "Uninstaller: $uninstaller"
Write-Host "Exists: $(Test-Path $uninstaller)"
