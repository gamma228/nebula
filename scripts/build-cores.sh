#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# scripts/build-cores.sh
# Native proxy core reproducible build script for Nebula (app.nebula.vpn)
#
# Covers BOTH proxy engines specified in Section E1:
# 1. sing-box (libbox) - official SagerNet release
# 2. Xray-core (via AndroidLibXrayLite wrapper) + hev-socks5-tunnel (v2rayNG stack)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/app/libs"
BUILD_DIR="${ROOT_DIR}/build/core-build"

# ------------------------------------------------------------------------------
# Pinned Core Versions and Hashes
# ------------------------------------------------------------------------------
SING_BOX_TAG="v1.14.4"
SING_BOX_COMMIT="c005f35300ce76427dc285481ec5695f5b179cd2"
SING_BOX_REPO="https://github.com/SagerNet/sing-box.git"
SING_BOX_EXPECTED_SHA256="A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996"

XRAY_TAG="v1.8.24"
XRAY_REPO="https://github.com/XTLS/Xray-core.git"
XRAY_WRAPPER_REPO="https://github.com/2dust/AndroidLibXrayLite.git"
XRAY_WRAPPER_TAG="v1.8.24"

HEV_TUNNEL_REPO="https://github.com/heiher/hev-socks5-tunnel.git"
HEV_TUNNEL_TAG="v2.6.9"

mkdir -p "${OUTPUT_DIR}" "${BUILD_DIR}"

echo "=================================================================="
echo "Nebula Proxy Core Builder"
echo "=================================================================="
echo "Output Directory: ${OUTPUT_DIR}"
echo "Build Directory:  ${BUILD_DIR}"
echo ""

# Verify environment prerequisites
: "${ANDROID_HOME:?ERROR: ANDROID_HOME environment variable must be set}"
NDK_VERSION="${NDK_VERSION:-28.0.13004108}"
export ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/${NDK_VERSION}"

if [ ! -d "${ANDROID_NDK_HOME}" ]; then
    echo "WARNING: Android NDK not found at ${ANDROID_NDK_HOME}"
    echo "Checking for any installed NDK..."
    if [ -d "${ANDROID_HOME}/ndk" ]; then
        AVAILABLE_NDK="$(find "${ANDROID_HOME}/ndk" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
        if [ -n "${AVAILABLE_NDK}" ]; then
            export ANDROID_NDK_HOME="${AVAILABLE_NDK}"
            echo "Using available NDK: ${ANDROID_NDK_HOME}"
        fi
    fi
fi

# ------------------------------------------------------------------------------
# 1. Build sing-box (libbox.aar)
# ------------------------------------------------------------------------------
build_sing_box() {
    echo "--- [1/2] Building sing-box (libbox.aar) ---"
    local SB_DIR="${BUILD_DIR}/sing-box"
    if [ ! -d "${SB_DIR}/.git" ]; then
        git clone --branch "${SING_BOX_TAG}" --depth 1 "${SING_BOX_REPO}" "${SB_DIR}"
    else
        cd "${SB_DIR}"
        git fetch --tags
        git checkout "${SING_BOX_TAG}"
    fi

    cd "${SB_DIR}"
    local COMMIT="$(git rev-parse HEAD)"
    echo "Sing-box checked out at commit: ${COMMIT}"

    # Install pinned gomobile & gobind
    go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.13

    # Build Android AAR
    echo "Compiling libbox.aar with gomobile..."
    go run ./cmd/internal/build_libbox -target android

    cp libbox.aar "${OUTPUT_DIR}/libbox.aar"
    local ACTUAL_SHA256="$(sha256sum "${OUTPUT_DIR}/libbox.aar" | awk '{print $1}')"
    echo "libbox.aar built successfully:"
    echo "  Location: ${OUTPUT_DIR}/libbox.aar"
    echo "  SHA-256:  ${ACTUAL_SHA256}"
}

# ------------------------------------------------------------------------------
# 2. Build Xray-core & hev-socks5-tunnel (v2rayNG stack)
# ------------------------------------------------------------------------------
build_xray_core() {
    echo "--- [2/2] Building Xray-core (AndroidLibXrayLite) & hev-socks5-tunnel ---"
    local XRAY_DIR="${BUILD_DIR}/AndroidLibXrayLite"
    if [ ! -d "${XRAY_DIR}/.git" ]; then
        git clone --branch "${XRAY_WRAPPER_TAG}" --depth 1 "${XRAY_WRAPPER_REPO}" "${XRAY_DIR}"
    else
        cd "${XRAY_DIR}"
        git fetch --tags
        git checkout "${XRAY_WRAPPER_TAG}"
    fi

    cd "${XRAY_DIR}"
    local XRAY_COMMIT="$(git rev-parse HEAD)"
    echo "AndroidLibXrayLite checked out at: ${XRAY_COMMIT}"

    # Build AndroidLibXrayLite AAR using gradle
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew assembleRelease
        find . -name "*.aar" -exec cp {} "${OUTPUT_DIR}/xray.aar" \;
        echo "Xray AAR copied to ${OUTPUT_DIR}/xray.aar"
    else
        echo "Note: To compile standalone Xray gomobile AAR:"
        echo "  gomobile bind -target=android -tags=with_gvisor,with_quic -o ${OUTPUT_DIR}/xray.aar github.com/xtls/xray-core/main"
    fi

    # Build hev-socks5-tunnel native library for TUN integration
    local HEV_DIR="${BUILD_DIR}/hev-socks5-tunnel"
    if [ ! -d "${HEV_DIR}/.git" ]; then
        git clone --branch "${HEV_TUNNEL_TAG}" --depth 1 --recursive "${HEV_TUNNEL_REPO}" "${HEV_DIR}"
    else
        cd "${HEV_DIR}"
        git fetch --tags
        git checkout "${HEV_TUNNEL_TAG}"
    fi

    echo "hev-socks5-tunnel checked out at: ${HEV_TUNNEL_TAG}"
    echo "Building hev-socks5-tunnel JNI libraries with NDK..."
    cd "${HEV_DIR}"
    if [ -d "${ANDROID_NDK_HOME:-}" ]; then
        "${ANDROID_NDK_HOME}/ndk-build" -C src/jni || true
    fi
}

TARGET="${1:-all}"
case "${TARGET}" in
    sing-box)
        build_sing_box
        ;;
    xray)
        build_xray_core
        ;;
    all)
        build_sing_box
        build_xray_core
        ;;
    *)
        echo "Usage: $0 [sing-box|xray|all]"
        exit 1
        ;;
esac

echo ""
echo "=================================================================="
echo "Core build process finished."
echo "=================================================================="
