#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-v1.14.4}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/app/libs"
BUILD_DIR="${ROOT_DIR}/build/sing-box-build"

echo "=== Building libbox.aar for sing-box tag: ${TAG} ==="
mkdir -p "${OUTPUT_DIR}" "${BUILD_DIR}"

if [ ! -d "${BUILD_DIR}/.git" ]; then
    git clone --branch "${TAG}" --depth 1 https://github.com/SagerNet/sing-box.git "${BUILD_DIR}"
else
    cd "${BUILD_DIR}"
    git fetch --tags
    git checkout "${TAG}"
fi

cd "${BUILD_DIR}"
COMMIT="$(git rev-parse HEAD)"
echo "Pinned commit: ${COMMIT}"

# Verify NDK and Go
: "${ANDROID_HOME:?ANDROID_HOME must be set}"
NDK_VERSION="28.0.13004108"
export ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/${NDK_VERSION}"

# Install gomobile and gobind
go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.13

# Run build
go run ./cmd/internal/build_libbox -target android

# Copy artifact
cp libbox.aar "${OUTPUT_DIR}/libbox.aar"

CHECKSUM="$(sha256sum "${OUTPUT_DIR}/libbox.aar" | awk '{print $1}')"
echo "libbox.aar successfully built:"
echo "Tag:      ${TAG}"
echo "Commit:   ${COMMIT}"
echo "SHA-256:  ${CHECKSUM}"
echo "Location: ${OUTPUT_DIR}/libbox.aar"
