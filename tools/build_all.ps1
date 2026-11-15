Set-Location 'C:\Users\User\.gemini\antigravity\scratch\nebula'

Write-Host "=========================================="
Write-Host "      NEBULA FULL RELEASE BUILD           "
Write-Host "=========================================="

# 1. Clean old application staging directory and jar libs to ensure zero stale jars
$appDir = "desktop\build\compose\binaries\main\app"
if (Test-Path $appDir) {
    Write-Host "Cleaning old distributable staging at $appDir..."
    Remove-Item -Recurse -Force $appDir
}
if (Test-Path "desktop\build\libs") {
    Remove-Item -Recurse -Force "desktop\build\libs"
}
if (Test-Path "core\build\libs") {
    Remove-Item -Recurse -Force "core\build\libs"
}

# 2. Ensure release directory exists
$releaseDir = "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}

# 3. Build Compose Multiplatform Desktop Distributable
Write-Host "`n>>> [1/5] Building Desktop Distributable (:desktop:createDistributable)..."
$gradleCmd = ".\gradlew.bat"
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"

& $gradleCmd :desktop:compileKotlin :core:compileKotlin :desktop:createDistributable --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# 4. Compile Inno Setup 6 Installer
Write-Host "`n>>> [2/5] Compiling Windows Installer (Inno Setup 6)..."
$iscc = "C:\Users\User\AppData\Local\Programs\Inno Setup 6\ISCC.exe"
$issScript = "packaging\windows\nebula_installer.iss"

& $iscc $issScript
if ($LASTEXITCODE -ne 0) {
    Write-Error "Inno Setup compilation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# 5. Create Standalone Portable ZIP
Write-Host "`n>>> [3/5] Creating Standalone Portable ZIP..."
$nebulaApp = "desktop\build\compose\binaries\main\app\Nebula"
$portableZip = Join-Path $releaseDir "Nebula-Portable-1.2.1.zip"
if (Test-Path $portableZip) {
    Remove-Item -Force $portableZip
}
Compress-Archive -Path "$nebulaApp\*" -DestinationPath $portableZip -CompressionLevel Optimal

# 6. Checksums and Inventory
Write-Host "`n>>> [4/5] Computing SHA-256 Checksums..."
$filesToHash = @(
    "release\Nebula-Setup-1.2.1.exe",
    "release\Nebula-Portable-1.2.1.zip",
    "app\build\outputs\apk\release\app-arm64-v8a-release.apk",
    "app\build\outputs\apk\release\app-universal-release.apk"
)

$hashLines = @()
foreach ($relPath in $filesToHash) {
    if (Test-Path $relPath) {
        $item = Get-Item $relPath
        $hash = (Get-FileHash -Path $relPath -Algorithm SHA256).Hash.ToLower()
        $name = Split-Path $relPath -Leaf
        $hashLines += "$hash  $name"
        $mb = [math]::Round($item.Length / 1MB, 2)
        Write-Host "   $name ($mb MB) -> $hash"
    } else {
        Write-Warning "File not found: $relPath"
    }
}

$sumsFile = "release\SHA256SUMS"
$hashLines | Out-File -FilePath $sumsFile -Encoding ascii

# 7. Update User's Local Installation
Write-Host "`n>>> [5/5] Updating Local Installed App at %LOCALAPPDATA%\Programs\Nebula VPN..."
$installer = "release\Nebula-Setup-1.2.1.exe"
Start-Process -FilePath $installer -ArgumentList "/CURRENTUSER /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS=`"desktopicon,startmenuicon`"" -Wait
Start-Sleep -Seconds 2

Write-Host "`n=========================================="
Write-Host "        BUILD COMPLETED SUCCESSFULLY!     "
Write-Host "=========================================="
