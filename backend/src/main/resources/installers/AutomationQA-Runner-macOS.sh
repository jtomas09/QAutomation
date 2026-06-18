#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  Automation QA Runner — Instalador macOS v2.2.0
#  Configura el Runner como LaunchAgent (auto-inicio al login)
#
#  [PROVISIONAL] Requiere: Java 17+ instalado en el equipo
#                          y cinepolis-runner.jar en la misma carpeta
# ═══════════════════════════════════════════════════════════════

set -e

BACKEND_URL="https://qautomation-production.up.railway.app"
RUNNER_TOKEN="runner-local-token"
INSTALL_DIR="$HOME/Library/Application Support/AutomationQA/runner"
PLIST_PATH="$HOME/Library/LaunchAgents/com.automationqa.runner.plist"
PLIST_LABEL="com.automationqa.runner"

echo ""
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   Automation QA Runner — Instalador macOS v2.2.0"
echo "   [PROVISIONAL] Instalador de configuracion"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ── 1. Verificar Java ────────────────────────────────────────────────────────
echo "  [1/5] Verificando Java..."

if ! java -version &>/dev/null 2>&1; then
    echo ""
    echo "  [ERROR] Java 17+ no encontrado."
    echo "  Descarga e instala Java desde: https://adoptium.net"
    echo "  Luego vuelve a ejecutar este instalador."
    echo ""
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "  [OK] Java detectado: $JAVA_VERSION"

# Localizar java bin
JAVA_BIN=$(which java)
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVA_BIN="$JAVA_HOME/bin/java"

# ── 2. Buscar JAR ────────────────────────────────────────────────────────────
echo "  [2/5] Buscando runner JAR..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNNER_JAR=""

for candidate in \
    "$SCRIPT_DIR/cinepolis-runner.jar" \
    "$SCRIPT_DIR/automationqa-runner.jar" \
    "$SCRIPT_DIR/target/cinepolis-runner.jar" \
    "$SCRIPT_DIR/../runner/target/cinepolis-runner.jar"; do
    if [ -f "$candidate" ]; then
        RUNNER_JAR="$(cd "$(dirname "$candidate")" && pwd)/$(basename "$candidate")"
        break
    fi
done

if [ -z "$RUNNER_JAR" ]; then
    echo ""
    echo "  [AVISO] No se encontro cinepolis-runner.jar."
    echo ""
    echo "  Para completar la instalacion necesitas el archivo JAR del Runner."
    echo "  Opciones:"
    echo "    A) Compila el proyecto desde la carpeta runner/: mvn package -DskipTests"
    echo "    B) Copia cinepolis-runner.jar a la misma carpeta que este script"
    echo ""
    echo "  Una vez tengas el JAR, vuelve a ejecutar este instalador."
    echo ""
    exit 1
fi

echo "  [OK] JAR encontrado: $RUNNER_JAR"

# ── 3. Instalar JAR ──────────────────────────────────────────────────────────
echo "  [3/5] Instalando en $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
cp "$RUNNER_JAR" "$INSTALL_DIR/automationqa-runner.jar"
echo "  [OK] Archivo copiado."

# ── 4. Construir PATH (ADB + Xcode) ─────────────────────────────────────────
echo "  [4/5] Configurando LaunchAgent (auto-inicio al login)..."

RUNNER_PATH="$PATH:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin"
[ -n "$ANDROID_HOME" ] && RUNNER_PATH="$ANDROID_HOME/platform-tools:$RUNNER_PATH"

# Buscar ADB en ubicaciones conocidas de macOS
for adb_dir in \
    "$HOME/Library/Android/sdk/platform-tools" \
    "/opt/homebrew/bin" \
    "/usr/local/bin"; do
    [ -x "$adb_dir/adb" ] && RUNNER_PATH="$adb_dir:$RUNNER_PATH" && break
done

# Detectar RUNNER_ID a partir del hostname
_HOST=$(hostname -s | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
RUNNER_ID="mac-${_HOST}"

# ── 5. Instalar LaunchAgent ──────────────────────────────────────────────────
# Descargar si ya estaba cargado
launchctl unload -w "$PLIST_PATH" 2>/dev/null || true

mkdir -p "$HOME/Library/LaunchAgents"
mkdir -p "$HOME/Library/Logs"

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
        <string>${INSTALL_DIR}/automationqa-runner.jar</string>
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
echo "  [OK] LaunchAgent instalado y activo."

# ── Resultado ────────────────────────────────────────────────────────────────
echo ""
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   Automation QA Runner instalado correctamente"
echo ""
echo "   El Runner arrancara automaticamente al iniciar sesion."
echo "   Aparecera en el Dashboard en ~15 segundos."
echo ""
echo "   Detecta automaticamente:"
echo "   • Dispositivos Android via USB (si ADB esta disponible)"
echo "   • Dispositivos iOS via USB (si Xcode esta instalado)"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
