#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  Automation QA Runner — Instalador macOS v2.3.0
#  Configura el Runner como LaunchAgent (inicio automatico al login)
# ═══════════════════════════════════════════════════════════════

set -e

BACKEND_URL="https://qautomation-production.up.railway.app"
RUNNER_TOKEN="runner-local-token"
INSTALL_DIR="$HOME/Library/Application Support/AutomationQA/runner"
PLIST_PATH="$HOME/Library/LaunchAgents/com.automationqa.runner.plist"
PLIST_LABEL="com.automationqa.runner"
JAR_DST="$INSTALL_DIR/automationqa-runner.jar"

echo ""
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   Automation QA Runner — Instalacion macOS v2.3.0"
echo "   Configurando el servicio automaticamente..."
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

mkdir -p "$INSTALL_DIR"
mkdir -p "$HOME/Library/LaunchAgents"
mkdir -p "$HOME/Library/Logs"

# ── 1. Verificar entorno de ejecucion ───────────────────────────────────────
echo "  [1/4] Verificando entorno de ejecucion..."

if ! java -version &>/dev/null 2>&1; then
    echo ""
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Se requiere instalar un componente del sistema"
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "  El Runner de Automation QA necesita el entorno de ejecucion"
    echo "  Java para funcionar. Este componente es gratuito y seguro."
    echo ""
    echo "  1. Descarga el instalador desde: https://adoptium.net"
    echo "  2. Selecciona la version LTS para macOS y completa la instalacion"
    echo "  3. Abre una nueva ventana de Terminal"
    echo "  4. Vuelve a ejecutar este instalador"
    echo ""
    open "https://adoptium.net" 2>/dev/null || true
    exit 1
fi

JAVA_BIN=$(which java)
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVA_BIN="$JAVA_HOME/bin/java"
echo "  [OK] Entorno de ejecucion verificado."

# ── 2. Obtener componente principal del Runner ───────────────────────────────
echo "  [2/4] Descargando componentes del Runner..."

RUNNER_JAR=""

# Si ya esta instalada una version anterior, reutilizarla
if [ -f "$JAR_DST" ]; then
    RUNNER_JAR="$JAR_DST"
    echo "  [OK] Componentes ya presentes — actualizando configuracion."
else
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

    # Buscar JAR junto a este instalador (paquete completo)
    for candidate in \
        "$SCRIPT_DIR/cinepolis-runner.jar" \
        "$SCRIPT_DIR/automationqa-runner.jar" \
        "$SCRIPT_DIR/runner/cinepolis-runner.jar"; do
        if [ -f "$candidate" ]; then
            RUNNER_JAR="$candidate"
            break
        fi
    done

    # Descargar automaticamente desde el servidor de Automation QA
    if [ -z "$RUNNER_JAR" ]; then
        echo "  Conectando al servidor para descargar componentes..."
        JAR_TMP="$(mktemp /tmp/automationqa-runner-XXXXXX.jar)"
        if curl -fsSL --max-time 120 \
                "$BACKEND_URL/api/runner/download/jar" \
                -o "$JAR_TMP" 2>/dev/null; then
            RUNNER_JAR="$JAR_TMP"
        fi
    fi

    if [ -z "$RUNNER_JAR" ] || [ ! -f "$RUNNER_JAR" ]; then
        echo ""
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "   El paquete de instalacion esta incompleto"
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
        echo "  No se pudieron obtener los componentes necesarios."
        echo ""
        echo "  Por favor:"
        echo "    1. Verifica tu conexion a internet"
        echo "    2. Descarga nuevamente el instalador desde el Dashboard"
        echo "    3. Ejecuta el nuevo instalador descargado"
        echo ""
        exit 1
    fi

    echo "  Instalando componentes..."
    cp "$RUNNER_JAR" "$JAR_DST"
fi

echo "  [OK] Componentes del Runner listos."

# ── 3. Construir PATH (ADB + Xcode) ─────────────────────────────────────────
echo "  [3/4] Configurando LaunchAgent (inicio automatico)..."

RUNNER_PATH="$PATH:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin"
[ -n "$ANDROID_HOME" ] && RUNNER_PATH="$ANDROID_HOME/platform-tools:$RUNNER_PATH"

for adb_dir in \
    "$HOME/Library/Android/sdk/platform-tools" \
    "/opt/homebrew/bin" \
    "/usr/local/bin"; do
    [ -x "$adb_dir/adb" ] && RUNNER_PATH="$adb_dir:$RUNNER_PATH" && break
done

_HOST=$(hostname -s | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
RUNNER_ID="mac-${_HOST}"

# ── 4. Instalar LaunchAgent ──────────────────────────────────────────────────
launchctl unload -w "$PLIST_PATH" 2>/dev/null || true

cat > "$PLIST_PATH" <<PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
    "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${PLIST_LABEL}</string>

    <key>ProgramArguments</key>
    <array>
        <string>${JAVA_BIN}</string>
        <string>-Dfile.encoding=UTF-8</string>
        <string>-DBACKEND_URL=${BACKEND_URL}</string>
        <string>-DRUNNER_TOKEN=${RUNNER_TOKEN}</string>
        <string>-DRUNNER_ID=${RUNNER_ID}</string>
        <string>-DPOLL_INTERVAL_MS=30000</string>
        <string>-jar</string>
        <string>${JAR_DST}</string>
    </array>

    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>${RUNNER_PATH}</string>
    </dict>

    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>ThrottleInterval</key>
    <integer>15</integer>

    <key>StandardOutPath</key>
    <string>${HOME}/Library/Logs/automationqa-runner.log</string>
    <key>StandardErrorPath</key>
    <string>${HOME}/Library/Logs/automationqa-runner-error.log</string>
</dict>
</plist>
PLIST_EOF

launchctl load -w "$PLIST_PATH"
echo "  [OK] Servicio de inicio automatico configurado."

# ── Resultado ────────────────────────────────────────────────────────────────
echo ""
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   Automation QA Runner instalado correctamente"
echo ""
echo "   El Runner se conectara automaticamente al iniciar sesion."
echo "   Aparecera en el Dashboard en aproximadamente 15 segundos."
echo ""
echo "   Detecta automaticamente:"
echo "   • Dispositivos Android via USB (si ADB esta disponible)"
echo "   • Dispositivos iOS via USB (si Xcode esta instalado)"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
