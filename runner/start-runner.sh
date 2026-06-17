#!/bin/bash
set -e

echo "============================================================"
echo "  Cinepolis QA Universal Runner v2.2.0"
echo "  Auto-detects: OS, ADB, Xcode, Android + iOS devices"
echo "============================================================"
echo ""

# ── Core config ────────────────────────────────────────────────
export BACKEND_URL="${BACKEND_URL:-https://qautomation-production.up.railway.app}"
export RUNNER_TOKEN="${RUNNER_TOKEN:-runner-local-token}"

# Runner ID: auto-generated from OS prefix + hostname
_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
_HOST=$(hostname | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')
case "$_OS" in
  darwin) _PREFIX="mac" ;;
  linux*) _PREFIX="linux" ;;
  *)      _PREFIX="runner" ;;
esac
export RUNNER_ID="${RUNNER_ID:-${_PREFIX}-${_HOST}}"

# Capacidades: el Runner detecta automaticamente Android/iOS segun el OS.
# En Mac → intenta Android (ADB) + iOS (Xcode/xcrun)
# En Linux → solo Android (ADB)
#
# Para sobreescribir: export RUNNER_PLATFORM=android
#
export WORK_DIR="${WORK_DIR:-..}"
export POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-5000}"
export APPIUM_HUB="${APPIUM_HUB:-http://127.0.0.1:4723}"

echo "  Runner ID:  $RUNNER_ID"
echo "  OS:         $(uname -s) (auto-detectado)"
echo "  Backend:    $BACKEND_URL"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Detectar ADB ───────────────────────────────────────────────
echo "[Check] Buscando ADB..."

find_adb() {
  command -v adb &>/dev/null && { echo "PATH"; return; }
  [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ] && {
    echo "$ANDROID_HOME/platform-tools"; return
  }
  for d in \
      "$HOME/Library/Android/sdk/platform-tools" \
      "$HOME/Android/Sdk/platform-tools" \
      "/usr/local/android-sdk/platform-tools"; do
    [ -x "$d/adb" ] && { echo "$d"; return; }
  done
  echo ""
}

ADB_DIR=$(find_adb)
if [ -n "$ADB_DIR" ]; then
  [ "$ADB_DIR" != "PATH" ] && export PATH="$ADB_DIR:$PATH"
  echo "[OK] ADB encontrado"
  echo ""
  echo "[ADB] Dispositivos conectados ahora:"
  adb devices -l
  echo ""
else
  echo "[WARN] ADB no encontrado. Dispositivos Android no seran descubiertos."
  echo "       brew install android-platform-tools  o instala Android Studio"
  echo ""
fi

# ── Detectar Xcode/xcrun (macOS) ───────────────────────────────
if [ "$_OS" = "darwin" ]; then
  echo "[Check] Verificando Xcode (iOS support)..."
  if command -v xcrun &>/dev/null; then
    echo "[OK] Xcode/xcrun disponible → iOS sera descubierto automaticamente"
  else
    echo "[INFO] xcrun no disponible → solo Android"
    echo "       Para iOS: instala Xcode desde App Store"
  fi
  echo ""
fi

# ── Compilar si no existe el JAR ───────────────────────────────
if [ -f "target/cinepolis-runner.jar" ]; then
  echo "[OK] Usando JAR existente: target/cinepolis-runner.jar"
else
  echo "[Build] Compilando con Maven..."
  if ! command -v mvn &>/dev/null; then
    echo "[ERROR] Maven no en PATH."
    echo "  macOS: brew install maven"
    echo "  O copia cinepolis-runner.jar a runner/target/"
    exit 1
  fi
  mvn package -DskipTests -q
  echo "[OK] Compilacion exitosa."
fi

echo ""
echo "------------------------------------------------------------"
echo " Universal Runner iniciando..."
echo " El dispositivo aparecera en Device Farm en ~5 segundos."
echo " Ctrl+C para detener."
echo "------------------------------------------------------------"
echo ""

exec java \
  -Dfile.encoding=UTF-8 \
  -DBACKEND_URL="$BACKEND_URL" \
  -DRUNNER_TOKEN="$RUNNER_TOKEN" \
  -DRUNNER_ID="$RUNNER_ID" \
  -DPOLL_INTERVAL_MS="$POLL_INTERVAL_MS" \
  -DWORK_DIR="$WORK_DIR" \
  -DAPPIUM_HUB="$APPIUM_HUB" \
  -jar target/cinepolis-runner.jar
