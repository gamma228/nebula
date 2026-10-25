#!/usr/bin/env bash
# Nebula Diagnostics Collection Script
# Collects system info, VPN status, network routing, and sanitized logs for troubleshooting.

set -euo pipefail

PACKAGE_NAME="${1:-app.nebula.vpn}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUT_DIR="nebula_diagnostics_${TIMESTAMP}"

echo "=========================================="
echo "Nebula Diagnostics Collector"
echo "Target Package: ${PACKAGE_NAME}"
echo "Output Directory: ${OUT_DIR}"
echo "=========================================="

mkdir -p "${OUT_DIR}"

# Check ADB
if ! command -v adb &> /dev/null; then
    echo "ERROR: adb command not found on PATH."
    exit 1
fi

DEVICE=$(adb get-serialno 2>/dev/null || echo "unknown")
echo "Connected Device: ${DEVICE}"

echo "[1/6] Collecting device specifications..."
{
    echo "=== DEVICE METADATA ==="
    echo "Manufacturer: $(adb shell getprop ro.product.manufacturer)"
    echo "Model: $(adb shell getprop ro.product.model)"
    echo "Android Version: $(adb shell getprop ro.build.version.release)"
    echo "SDK Level: $(adb shell getprop ro.build.version.sdk)"
    echo "ABI: $(adb shell getprop ro.product.cpu.abi)"
    echo "Kernel: $(adb shell uname -a)"
    echo "Security Patch: $(adb shell getprop ro.build.version.security_patch)"
} > "${OUT_DIR}/device_info.txt"

echo "[2/6] Inspecting package installation & permissions..."
adb shell dumpsys package "${PACKAGE_NAME}" > "${OUT_DIR}/package_dumpsys.txt" || true

echo "[3/6] Inspecting network & routing configuration..."
{
    echo "=== NETWORK INTERFACES ==="
    adb shell ip addr || adb shell ifconfig || true
    echo ""
    echo "=== IP ROUTING TABLE ==="
    adb shell ip route || true
    echo ""
    echo "=== IP RULES ==="
    adb shell ip rule || true
    echo ""
    echo "=== DNS PROPERTIES ==="
    adb shell getprop | grep -i "dns" || true
} > "${OUT_DIR}/network_routes.txt"

echo "[4/6] Inspecting VPN status & power management..."
{
    echo "=== BATTERY OPTIMIZATION WHITELIST ==="
    adb shell dumpsys deviceidle whitelist | grep -i "${PACKAGE_NAME}" || echo "Not in battery optimization whitelist (Background execution may be killed)"
    echo ""
    echo "=== CONNECTIVITY SERVICE VPN STATE ==="
    adb shell dumpsys connectivity | grep -E -A 10 "VPN|NetworkAgentInfo.*VPN" || true
} > "${OUT_DIR}/vpn_system_state.txt"

echo "[5/6] Measuring process memory & thread usage..."
adb shell dumpsys meminfo "${PACKAGE_NAME}" > "${OUT_DIR}/meminfo.txt" 2>&1 || true

echo "[6/6] Collecting sanitized logcat..."
# Redact sensitive parameters like passwords, private keys, uuid tokens
adb logcat -d -v time | grep -E "${PACKAGE_NAME}|Libbox|GoLog|VpnService|SingBox" | \
    sed -E 's/("password":\s*")[^"]+/\1[REDACTED]/g' | \
    sed -E 's/(pbk=)[^&[:space:]]+/\1[REDACTED]/g' | \
    sed -E 's/(sid=)[^&[:space:]]+/\1[REDACTED]/g' | \
    sed -E 's/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/[UUID_REDACTED]/g' \
    > "${OUT_DIR}/sanitized_logcat.txt" || true

echo "Packaging diagnostics bundle..."
tar -czf "${OUT_DIR}.tar.gz" "${OUT_DIR}"
echo "Diagnostics collected successfully: ${OUT_DIR}.tar.gz"
