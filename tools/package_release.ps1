Set-Location 'C:\Users\User\.gemini\antigravity\scratch\nebula'

$releaseDir = "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}

$appDir = "desktop\build\compose\binaries\main\app\Nebula"
$portableZip = Join-Path $releaseDir "Nebula-Portable-1.2.1.zip"

Write-Host "Creating Portable ZIP: $portableZip from $appDir"
if (Test-Path $portableZip) {
    Remove-Item -Force $portableZip
}
Compress-Archive -Path "$appDir\*" -DestinationPath $portableZip -CompressionLevel Optimal

Write-Host "Portable ZIP created. Size: $((Get-Item $portableZip).Length) bytes"

Write-Host "Generating SHA256SUMS for release artifacts..."
$filesToHash = @(
    "release\Nebula-Setup-1.2.1.exe",
    "release\Nebula-Portable-1.2.1.zip",
    "app\build\outputs\apk\release\app-arm64-v8a-release.apk",
    "app\build\outputs\apk\release\app-universal-release.apk"
)

$hashLines = @()
foreach ($relPath in $filesToHash) {
    if (Test-Path $relPath) {
        $hash = (Get-FileHash -Path $relPath -Algorithm SHA256).Hash.ToLower()
        $name = Split-Path $relPath -Leaf
        $hashLines += "$hash  $name"
        Write-Host "$hash  $name"
    } else {
        Write-Warning "File not found: $relPath"
    }
}

$sumsFile = "release\SHA256SUMS"
$hashLines | Out-File -FilePath $sumsFile -Encoding ascii
Write-Host "Wrote SHA256SUMS to $sumsFile"
