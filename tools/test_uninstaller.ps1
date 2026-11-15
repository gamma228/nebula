$uninstaller = Join-Path $env:LOCALAPPDATA 'Programs\Nebula VPN\unins000.exe'
Write-Host "Running uninstaller: $uninstaller /VERYSILENT /SUPPRESSMSGBOXES /NORESTART"
if (Test-Path $uninstaller) {
    Start-Process -FilePath $uninstaller -ArgumentList "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART" -Wait
    Start-Sleep -Seconds 2
}

$desktopLnk = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Nebula VPN.lnk'
$startLnk = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Nebula VPN\Nebula VPN.lnk'
$appDir = Join-Path $env:LOCALAPPDATA 'Programs\Nebula VPN'

Write-Host "Desktop Shortcut Exists: $(Test-Path $desktopLnk)"
Write-Host "Start Menu Shortcut Exists: $(Test-Path $startLnk)"
Write-Host "App Dir Exists: $(Test-Path $appDir)"

# Reinstall so the system has the verified installed build ready
$installer = "C:\Users\User\.gemini\antigravity\scratch\nebula\release\Nebula-Setup-1.2.1.exe"
Write-Host "Reinstalling cleanly for deployment verification..."
Start-Process -FilePath $installer -ArgumentList "/CURRENTUSER /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS=`"desktopicon,startmenuicon`"" -Wait
Start-Sleep -Seconds 2
Write-Host "Reinstalled. Desktop Shortcut Exists: $(Test-Path $desktopLnk)"
