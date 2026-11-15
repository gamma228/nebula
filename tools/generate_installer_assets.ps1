# Generate high-DPI installer artwork for Inno Setup
Add-Type -AssemblyName System.Drawing

$currentDir = (Get-Location).Path
$outDir = Join-Path $currentDir "packaging\windows"
if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

# 1. Wizard Side Banner (328 x 628, 200% High-DPI)
$sideWidth = 328
$sideHeight = 628
$bmpSide = New-Object System.Drawing.Bitmap($sideWidth, $sideHeight)
$gSide = [System.Drawing.Graphics]::FromImage($bmpSide)
$gSide.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gSide.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

# Gradient background: #07090E to #002214
$rectSide = New-Object System.Drawing.Rectangle(0, 0, $sideWidth, $sideHeight)
$brushBg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rectSide,
    [System.Drawing.Color]::FromArgb(255, 7, 9, 14),
    [System.Drawing.Color]::FromArgb(255, 0, 36, 20),
    [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
)
$gSide.FillRectangle($brushBg, $rectSide)

# Draw decorative cyber grid lines
$penGrid = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(30, 0, 208, 132), 1)
for ($y = 0; $y -lt $sideHeight; $y += 32) {
    $gSide.DrawLine($penGrid, 0, $y, $sideWidth, $y)
}
for ($x = 0; $x -lt $sideWidth; $x += 32) {
    $gSide.DrawLine($penGrid, $x, 0, $x, $sideHeight)
}

# Draw Shield Icon in upper center
$shieldX = 164
$shieldY = 180
$shieldW = 100
$shieldH = 120

$pathShield = New-Object System.Drawing.Drawing2D.GraphicsPath
$pathShield.AddLine($shieldX, $shieldY - 50, $shieldX + 45, $shieldY - 35)
$pathShield.AddLine($shieldX + 45, $shieldY - 35, $shieldX + 45, $shieldY + 15)
$pathShield.AddBezier($shieldX + 45, $shieldY + 15, $shieldX + 35, $shieldY + 55, $shieldX + 15, $shieldY + 70, $shieldX, $shieldY + 80)
$pathShield.AddBezier($shieldX, $shieldY + 80, $shieldX - 15, $shieldY + 70, $shieldX - 35, $shieldY + 55, $shieldX - 45, $shieldY + 15)
$pathShield.AddLine($shieldX - 45, $shieldY + 15, $shieldX - 45, $shieldY - 35)
$pathShield.CloseFigure()

$brushGlow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(50, 0, 208, 132))
$gSide.FillPath($brushGlow, $pathShield)
$penShield = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 208, 132), 4)
$gSide.DrawPath($penShield, $pathShield)

# Draw checkmark inside shield
$penCheck = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 208, 132), 5)
$gSide.DrawLine($penCheck, $shieldX - 20, $shieldY + 10, $shieldX - 5, $shieldY + 25)
$gSide.DrawLine($penCheck, $shieldX - 5, $shieldY + 25, $shieldX + 22, $shieldY - 12)

# Typography
$fontTitle = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
$fontSub = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Regular)
$fontDetail = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Regular)

$brushWhite = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$brushMuted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(200, 180, 200, 190))
$brushEmerald = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 208, 132))

$sfCenter = New-Object System.Drawing.StringFormat
$sfCenter.Alignment = [System.Drawing.StringAlignment]::Center

$gSide.DrawString("NEBULA", $fontTitle, $brushWhite, [float]164, [float]310, $sfCenter)
$gSide.DrawString("Universal VPN Client", $fontSub, $brushEmerald, [float]164, [float]360, $sfCenter)
$gSide.DrawString("Fast • Secure • Private`nWindows Desktop Edition", $fontDetail, $brushMuted, [float]164, [float]410, $sfCenter)

$sidePath = Join-Path $outDir "wizard_side.bmp"
$bmpSide.Save($sidePath, [System.Drawing.Imaging.ImageFormat]::Bmp)
$gSide.Dispose()
$bmpSide.Dispose()
Write-Host "Created $sidePath" -ForegroundColor Green

# 2. Wizard Header Icon (110 x 116, 200% High-DPI)
$smallWidth = 110
$smallHeight = 116
$bmpSmall = New-Object System.Drawing.Bitmap($smallWidth, $smallHeight)
$gSmall = [System.Drawing.Graphics]::FromImage($bmpSmall)
$gSmall.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

$rectSmall = New-Object System.Drawing.Rectangle(0, 0, $smallWidth, $smallHeight)
$brushSmallBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 12, 16, 24))
$gSmall.FillRectangle($brushSmallBg, $rectSmall)

# Draw miniature shield
$smX = 55
$smY = 58
$pathSm = New-Object System.Drawing.Drawing2D.GraphicsPath
$pathSm.AddLine($smX, $smY - 30, $smX + 26, $smY - 20)
$pathSm.AddLine($smX + 26, $smY - 20, $smX + 26, $smY + 10)
$pathSm.AddBezier($smX + 26, $smY + 10, $smX + 20, $smY + 32, $smX + 10, $smY + 40, $smX, $smY + 46)
$pathSm.AddBezier($smX, $smY + 46, $smX - 10, $smY + 40, $smX - 20, $smY + 32, $smX - 26, $smY + 10)
$pathSm.AddLine($smX - 26, $smY + 10, $smX - 26, $smY - 20)
$pathSm.CloseFigure()

$brushSmShield = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, 0, 208, 132))
$penSmShield = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 208, 132), 3)
$gSmall.FillPath($brushSmShield, $pathSm)
$gSmall.DrawPath($penSmShield, $pathSm)

$penSmCheck = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 208, 132), 3)
$gSmall.DrawLine($penSmCheck, $smX - 12, $smY + 6, $smX - 3, $smY + 15)
$gSmall.DrawLine($penSmCheck, $smX - 3, $smY + 15, $smX + 13, $smY - 8)

$smallPath = Join-Path $outDir "wizard_small.bmp"
$bmpSmall.Save($smallPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
$gSmall.Dispose()
$bmpSmall.Dispose()
Write-Host "Created $smallPath" -ForegroundColor Green

# 3. Copy Icon
Copy-Item "desktop/build/compose/binaries/main/app/Nebula/Nebula.ico" "$outDir/app.ico" -Force
Write-Host "Copied $outDir/app.ico" -ForegroundColor Green
