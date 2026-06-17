#!/bin/bash
set -e

echo "============================================================"
echo "  Cinepolis QA - Runner Agent v2.2.0"
echo "  Device Farm Enterprise - Auto Discovery"
echo "============================================================"
echo ""

# ── Configuracion ──────────────────────────────────────────────
export BACKEND_URL="${BACKEND_URL:-https://qautomation-production.up.railway.app}"
export RUNNER_TOKEN="${RUNNER_TOKEN:-runner-local-token}"
export RUNNER_PLATFORM="${RUNNER_PLATFORM:-android}"
export POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-5000}"
export WORK_DIR="${WORK_DIR:-..}"
export APPIUM_HUB="${APPIUM_HUB:-http://127.0.0.1:4723}"

# Runner ID unico por maquina
_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
_HOST=$(hostname | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')
case "$_OS" in
  darwin)  _PREFIX="mac" ;;
  linux*)  _PREFIX="linux" ;;
  *)       _PREFIX="runner" ;;
esac
export RUNNER_ID="${RUNNER_ID:-${_PREFIX}-${_HOST}}"

echo "  Runner ID:  $RUNNER_ID"
echo "  Platform:   $RUNNER_PLATFORM"
echo "  Backend:    $BACKEND_URL"
echo "  WorkDir:    $WORK_DIR"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Detectar ADB ───────────────────────────────────────────────
echo "[Check] Buscando ADB..."

find_adb() {
  # 1. PATH del sistema
  if command -v adb &>/dev/null; then echo "PATH"; return; fi

  # 2. ANDROID_HOME
  if [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    echo "$ANDROID_HOME/platform-tools"
    return
  fi

  # 3. Rutas comunes macOS / Linux
  for candidate in \
      "$HOME/Library/Android/sdk/platform-tools" \
      "$HOME/Android/Sdk/platform-tools" \
      "/usr/local/android-sdk/platform-tools" \
      "/opt/android-sdk/platform-tools"; do
    if [ -x "$candidate/adb" ]; then echo "$candidate"; return; fi
  done

  echo ""
}

ADB_DIR=$(find_adb)
if [ -n "$ADB_DIR" ]; then
  if [ "$ADB_DIR" != "PATH" ]; then
    export PATH="$ADB_DIR:$PATH"
  fi
  echo "[OK] ADB encontrado"
  echo ""
  echo "[ADB] Dispositivos detectados:"
  adb devices -l
  echo ""
else
  echo "[WARN] ADB no encontrado. Dispositivos Android no seran descubiertos."
  echo "       Instala Android SDK o agrega platform-tools al PATH."
  echo ""
fi

# ── Compilar si no existe el JAR ───────────────────────────────
if [ -f "target/cinepolis-runner.jar" ]; then
  echo "[OK] Usando JAR existente: target/cinepolis-runner.jar"
  echo "     Para recompilar: rm target/cinepolis-runner.jar"
else
  echo "[Build] JAR no encontrado. Compilando con Maven..."
  if ! command -v mvn &>/dev/null; then
    echo "[ERROR] Maven no encontrado en PATH."
    echo ""
    echo "Opciones:"
    echo "  1. Instala Apache Maven 3.8+: brew install maven"
    echo "  2. Copia manualmente cinepolis-runner.jar a runner/target/"
    exit 1
  fi
  mvn package -DskipTests -q
  echo "[OK] Compilacion exitosa."
fi

echo ""
echo "------------------------------------------------------------"
echo " Runner iniciando. El dispositivo aparecera en el Dashboard"
echo " tras el primer heartbeat (aprox. 5-10 segundos)."
echo " Ctrl+C para detener."
echo "------------------------------------------------------------"
echo ""

exec java \
  -Dfile.encoding=UTF-8 \
  -DBACKEND_URL="$BACKEND_URL" \
  -DRUNNER_TOKEN="$RUNNER_TOKEN" \
  -DRUNNER_ID="$RUNNER_ID" \
  -DRUNNER_PLATFORM="$RUNNER_PLATFORM" \
  -DPOLL_INTERVAL_MS="$POLL_INTERVAL_MS" \
  -DWORK_DIR="$WORK_DIR" \
  -DAPPIUM_HUB="$APPIUM_HUB" \
  -jar target/cinepolis-runner.jar
