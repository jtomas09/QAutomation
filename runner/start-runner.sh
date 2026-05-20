#!/bin/bash
echo "================================================"
echo " Cinepolis QA Runner Agent v2.0"
echo "================================================"

# ── Backend ────────────────────────────────────────────
export BACKEND_URL="${BACKEND_URL:-https://qautomation-production.up.railway.app}"
export RUNNER_TOKEN="${RUNNER_TOKEN:-runner-local-token}"
export POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-5000}"

# ── Directorio raiz del proyecto Maven de pruebas ──────
# Cambia esta ruta al directorio donde esta tu pom.xml de Appium
export WORK_DIR="${WORK_DIR:-..}"

# ── Appium ─────────────────────────────────────────────
export APPIUM_HUB="${APPIUM_HUB:-http://127.0.0.1:4723}"

# ── Allure (opcional) ──────────────────────────────────
# export ALLURE_BASE_URL="https://mi-servidor/allure"

echo "Backend:   $BACKEND_URL"
echo "WorkDir:   $WORK_DIR"
echo "Appium:    $APPIUM_HUB"
echo ""
echo "Requisitos:"
echo "  - Appium corriendo en puerto 4723"
echo "  - adb devices debe mostrar un dispositivo"
echo "  - mvn en PATH"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

mvn package -q -DskipTests
if [ $? -ne 0 ]; then
    echo "ERROR: Fallo al compilar el runner"
    exit 1
fi

java -jar target/cinepolis-runner.jar
