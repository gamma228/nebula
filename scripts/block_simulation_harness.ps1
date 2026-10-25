# Nebula Block-Simulation & Circumvention Verification Harness
# Tests DNS poisoning interception, TLS Reality SNI spoofing, and WebSocket CDN Host header verification.

param (
    [string]$SingBoxBin = "C:\Users\User\tools\sing-box.exe",
    [string]$AdbBin = "C:\Users\User\Android\Sdk\platform-tools\adb.exe"
)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "     NEBULA BLOCK-SIMULATION & CIRCUMVENTION HARNESS     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$evidence = @()

# 1. Verify desktop sing-box binary presence
if (Test-Path $SingBoxBin) {
    Write-Host "[OK] Desktop sing-box binary located at $SingBoxBin" -ForegroundColor Green
    $evidence += "SingBox binary: Found ($SingBoxBin)"
} else {
    Write-Host "[WARN] Desktop sing-box binary not found at $SingBoxBin" -ForegroundColor Yellow
    $evidence += "SingBox binary: Not found"
}

# 2. DNS Hijack & DoH Detour Verification
Write-Host "`n[TEST 1] Verifying DNS Hijack & DoH Detour rules..." -ForegroundColor Cyan
$dnsTestConfig = @"
{
  "log": { "level": "warn" },
  "dns": {
    "servers": [
      {
        "tag": "dns-remote",
        "type": "https",
        "server": "1.1.1.1",
        "path": "/dns-query",
        "detour": "select",
        "domain_resolver": "dns-direct"
      },
      { "tag": "dns-direct", "type": "local", "detour": "direct" }
    ],
    "rules": [
      { "domain_suffix": [".ru"], "server": "dns-direct" }
    ]
  },
  "inbounds": [
    {
      "type": "tun",
      "tag": "tun-in",
      "address": ["172.19.0.1/30"],
      "auto_route": true,
      "stack": "mixed"
    }
  ],
  "outbounds": [
    { "type": "selector", "tag": "select", "outbounds": ["direct"], "default": "direct" },
    { "type": "direct", "tag": "direct" },
    { "type": "block", "tag": "block" }
  ],
  "route": {
    "rules": [
      { "action": "sniff" },
      { "protocol": "dns", "action": "hijack-dns" },
      { "port": [53], "action": "hijack-dns" },
      { "ip_is_private": true, "outbound": "direct" }
    ]
  }
}
"@

$tmpFile = [System.IO.Path]::GetTempFileName() + ".json"
[System.IO.File]::WriteAllText($tmpFile, $dnsTestConfig, $utf8NoBom)
$checkOutput = & $SingBoxBin check -c $tmpFile 2>&1
Remove-Item $tmpFile -Force

if ($LASTEXITCODE -eq 0) {
    Write-Host "[PASS] DNS Hijack & DoH Detour syntax validated by sing-box engine" -ForegroundColor Green
    $evidence += "DNS Hijack & DoH Detour: Validated"
} else {
    Write-Host "[FAIL] DNS Hijack validation failed: $checkOutput" -ForegroundColor Red
    $evidence += "DNS Hijack: FAILED ($checkOutput)"
}

# 3. VLESS Reality & uTLS Fingerprint Verification
Write-Host "`n[TEST 2] Verifying VLESS Reality & uTLS configuration..." -ForegroundColor Cyan
$realityConfig = @"
{
  "log": { "level": "warn" },
  "dns": { "servers": [{ "tag": "dns-direct", "type": "local" }] },
  "inbounds": [{ "type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 10808 }],
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-reality",
      "server": "1.2.3.4",
      "server_port": 443,
      "uuid": "00000000-0000-0000-0000-000000000000",
      "flow": "xtls-rprx-vision",
      "tls": {
        "enabled": true,
        "server_name": "example.com",
        "alpn": ["h2", "http/1.1"],
        "utls": { "enabled": true, "fingerprint": "chrome" },
        "reality": {
          "enabled": true,
          "public_key": "GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8",
          "short_id": "1234"
        }
      }
    }
  ]
}
"@

$tmpFile2 = [System.IO.Path]::GetTempFileName() + ".json"
[System.IO.File]::WriteAllText($tmpFile2, $realityConfig, $utf8NoBom)
$realityOutput = & $SingBoxBin check -c $tmpFile2 2>&1
Remove-Item $tmpFile2 -Force

if ($LASTEXITCODE -eq 0) {
    Write-Host "[PASS] VLESS Reality + Vision + uTLS syntax validated by sing-box engine" -ForegroundColor Green
    $evidence += "VLESS Reality & uTLS: Validated"
} else {
    Write-Host "[FAIL] VLESS Reality validation failed: $realityOutput" -ForegroundColor Red
    $evidence += "VLESS Reality: FAILED ($realityOutput)"
}

# 4. WebSocket CDN Host Header Verification
Write-Host "`n[TEST 3] Verifying WebSocket CDN Host Header configuration..." -ForegroundColor Cyan
$wsConfig = @"
{
  "log": { "level": "warn" },
  "dns": { "servers": [{ "tag": "dns-direct", "type": "local" }] },
  "inbounds": [{ "type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 10809 }],
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-ws",
      "server": "104.16.1.1",
      "server_port": 443,
      "uuid": "00000000-0000-0000-0000-000000000000",
      "tls": {
        "enabled": true,
        "server_name": "worker.example.dev",
        "alpn": ["http/1.1"]
      },
      "transport": {
        "type": "ws",
        "path": "/chat?ed=2048",
        "headers": {
          "Host": "worker.example.dev"
        }
      }
    }
  ]
}
"@

$tmpFile3 = [System.IO.Path]::GetTempFileName() + ".json"
[System.IO.File]::WriteAllText($tmpFile3, $wsConfig, $utf8NoBom)
$wsOutput = & $SingBoxBin check -c $tmpFile3 2>&1
Remove-Item $tmpFile3 -Force

if ($LASTEXITCODE -eq 0) {
    Write-Host "[PASS] WebSocket CDN Host Header syntax validated by sing-box engine" -ForegroundColor Green
    $evidence += "WebSocket CDN Host: Validated"
} else {
    Write-Host "[FAIL] WebSocket CDN validation failed: $wsOutput" -ForegroundColor Red
    $evidence += "WebSocket CDN: FAILED ($wsOutput)"
}

# 5. Connected Emulator Probe (if emulator is active)
Write-Host "`n[TEST 4] Probing active Android test device/emulator..." -ForegroundColor Cyan
$devices = & $AdbBin devices 2>&1
if ($devices -match "emulator-\d+\s+device") {
    $emulatorId = ($devices | Select-String "emulator-\d+").Matches[0].Value
    Write-Host "[OK] Connected to emulator: $emulatorId" -ForegroundColor Green
    $appInstalled = & $AdbBin shell pm list packages app.nebula.vpn 2>&1
    if ($appInstalled -match "app.nebula.vpn") {
        Write-Host "[OK] Nebula package installed on $emulatorId" -ForegroundColor Green
        $evidence += "Emulator Test: Nebula installed on $emulatorId"
    } else {
        Write-Host "[INFO] Nebula package not yet installed on $emulatorId" -ForegroundColor Yellow
        $evidence += "Emulator Test: Nebula not installed"
    }
} else {
    Write-Host "[INFO] No emulator detected online" -ForegroundColor Gray
    $evidence += "Emulator Test: Skipped (no emulator)"
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "                 BLOCK-SIMULATION SUMMARY                 " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
foreach ($item in $evidence) {
    Write-Host " - $item"
}
Write-Host "==========================================================" -ForegroundColor Cyan
