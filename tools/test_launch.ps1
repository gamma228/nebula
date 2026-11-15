$exe = Join-Path $env:LOCALAPPDATA 'Programs\Nebula VPN\Nebula.exe'
Write-Host "Checking executable: $exe"
if (Test-Path $exe) {
    $proc = Start-Process -FilePath $exe -PassThru
    Start-Sleep -Seconds 5
    Write-Host "Process Id: $($proc.Id)"
    Write-Host "Process Responding: $($proc.Responding)"
    Write-Host "Process HasExited: $($proc.HasExited)"
    if (-not $proc.HasExited) {
        Write-Host "Nebula.exe successfully launched and running!"
        # Take a screenshot if possible
        Add-Type -AssemblyName System.Windows.Forms
        Add-Type -AssemblyName System.Drawing
        $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
        $bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $screenshotPath = "C:\Users\User\.gemini\antigravity\scratch\nebula\docs\screenshots\installed_app_launch.png"
        New-Item -ItemType Directory -Force -Path (Split-Path $screenshotPath) | Out-Null
        $bitmap.Save($screenshotPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $graphics.Dispose()
        $bitmap.Dispose()
        Write-Host "Screenshot saved to: $screenshotPath"
        Stop-Process -Id $proc.Id -Force
        Write-Host "Cleaned up process."
    } else {
        Write-Host "Process exited prematurely. ExitCode: $($proc.ExitCode)"
    }
}
