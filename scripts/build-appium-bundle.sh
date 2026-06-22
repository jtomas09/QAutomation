#!/usr/bin/env bash
# build-appium-bundle.sh
# Pre-packages Node.js + Appium 2 + drivers into a distributable tarball.
# Run on a macOS machine with internet access (typically in CI/CD).
# Output: build/appium-bundle-macos.tar.gz
#
# The macOS installer checks for this bundle at:
#   $BACKEND_URL/api/runner/download/appium-bundle/macos
# If present, it unpacks it directly instead of running npm install.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/../build"
BUNDLE_DIR="$BUILD_DIR/appium-bundle"
NODE_VERSION="v20.19.2"
ARCH="$(uname -m)"  # arm64 or x86_64
NODE_PLATFORM="darwin-${ARCH}"
NODE_TAR="node-${NODE_VERSION}-${NODE_PLATFORM}"

echo "========================================"
echo "  Appium Bundle Builder — macOS ${ARCH}"
echo "========================================"
echo "  Node:   ${NODE_VERSION}"
echo "  Output: ${BUILD_DIR}/appium-bundle-macos.tar.gz"
echo ""

mkdir -p "$BUILD_DIR" "$BUNDLE_DIR"

# ── Step 1: Node.js ────────────────────────────────────────────────────────
if [[ ! -f "$BUILD_DIR/node.tar.gz" ]]; then
    echo "[1/4] Descargando Node.js ${NODE_VERSION}..."
    curl -fL "https://nodejs.org/dist/${NODE_VERSION}/${NODE_TAR}.tar.gz" \
         -o "$BUILD_DIR/node.tar.gz" \
         --progress-bar
fi

echo "[1/4] Extrayendo Node.js..."
mkdir -p "$BUNDLE_DIR/node"
tar -xzf "$BUILD_DIR/node.tar.gz" -C "$BUNDLE_DIR/node" --strip-components=1

NODE_BIN="$BUNDLE_DIR/node/bin/node"
NPM_BIN="$BUNDLE_DIR/node/bin/npm"
echo "      Node: $("$NODE_BIN" --version)"
echo "      npm:  $("$NPM_BIN"  --version)"

# ── Step 2: Appium 2 ────────────────────────────────────────────────────────
echo "[2/4] Instalando Appium 2..."
mkdir -p "$BUNDLE_DIR/appium"
mkdir -p "$BUNDLE_DIR/appium-home"

export APPIUM_HOME="$BUNDLE_DIR/appium-home"
"$NPM_BIN" install \
    --prefix "$BUNDLE_DIR/appium" \
    appium@2 \
    --no-audit \
    --no-fund \
    --loglevel=warn

APPIUM_BIN="$BUNDLE_DIR/appium/node_modules/.bin/appium"
echo "      Appium: $("$NODE_BIN" "$APPIUM_BIN" --version)"

# ── Step 3: Drivers ─────────────────────────────────────────────────────────
echo "[3/4] Instalando drivers Appium..."
"$NODE_BIN" "$APPIUM_BIN" driver install uiautomator2
"$NODE_BIN" "$APPIUM_BIN" driver install xcuitest
"$NODE_BIN" "$APPIUM_BIN" driver install espresso

echo ""
echo "Drivers instalados:"
"$NODE_BIN" "$APPIUM_BIN" driver list --installed

# ── Step 4: Package ──────────────────────────────────────────────────────────
echo ""
echo "[4/4] Empaquetando bundle..."
tar -czf "$BUILD_DIR/appium-bundle-macos.tar.gz" \
    -C "$BUNDLE_DIR" \
    node \
    appium \
    appium-home

echo ""
echo "========================================"
echo "  Bundle listo:"
ls -lh "$BUILD_DIR/appium-bundle-macos.tar.gz"
echo ""
echo "  Contenido:"
tar -tzf "$BUILD_DIR/appium-bundle-macos.tar.gz" | grep -E '(node/bin/node|appium/node_modules/.bin/appium|.drivers)' | head -10
echo "========================================"
echo ""
echo "  Sube el bundle al backend:"
echo "  cp build/appium-bundle-macos.tar.gz backend/src/main/resources/bundles/"
echo "  O publica en el endpoint:"
echo "  GET /api/runner/download/appium-bundle/macos"
echo "========================================"
