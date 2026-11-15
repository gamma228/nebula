$installer = "C:\Users\User\.gemini\antigravity\scratch\nebula\release\Nebula-Setup-1.2.1.exe"
Write-Host "Installing fresh build from $installer..."
Start-Process -FilePath $installer -ArgumentList "/CURRENTUSER /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS=`"desktopicon,startmenuicon`"" -Wait
Start-Sleep -Seconds 3

Write-Host "`nInstalled app jars:"
Get-ChildItem 'C:\Users\User\AppData\Local\Programs\Nebula VPN\app' -Filter "desktop-*.jar" | Format-List Name, LastWriteTime, Length
Get-ChildItem 'C:\Users\User\AppData\Local\Programs\Nebula VPN\app' -Filter "core-*.jar" | Format-List Name, LastWriteTime, Length
