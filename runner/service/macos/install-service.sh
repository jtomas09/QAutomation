#!/bin/bash
# ============================================================
#  Automation QA — macOS LaunchAgent Installer
#  Registra el Universal Runner como LaunchAgent del usuario:
#  - Arranca automaticamente al iniciar sesion
#  - Se reinicia si falla (KeepAlive: true)
#  - Detecta Android + iOS automaticamente
#  - Sin necesidad de abrir Terminal manualmente
# ============================================================
set -e

# ── Colores ──────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

# ── Rutas ────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNNER_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LABEL="com.automationqa.runner"
PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST_PATH="$PLIST_DIR/$LABEL.plist"
INSTALL_DIR="$HOME/Library/Application Support/AutomationQA/runner"
JAR_DST="$INSTALL_DIR/cinepolis-runner.jar"
LOG_DIR="$HOME/Library/Logs/AutomationQA"
LOG_OUT="$LOG_DIR/runner.log"
LOG_ERR="$LOG_DIR/runner-error.log"

# ── Config (lee de runner.properties si existe) ──────────────
BACKEND_URL="https://qautomation-production.up.railway.app"
RUNNER_TOKEN="runner-local-token"
POLL_INTERVAL_MS="30000"

if [ -f "$RUNNER_DIR/runner.properties" ]; then
    while IFS='=' read -r key val; do
        [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
        key=$(echo "$key" | xargs); val=$(echo "$val" | xargs)
        case "$key" in
            BACKEND_URL)       BACKEND_URL="$val"       ;;
            RUNNER_TOKEN)      RUNNER_TOKEN="$val"       ;;
            POLL_INTERVAL_MS)  POLL_INTERVAL_MS="$val"  ;;
        esac
    done < "$RUNNER_DIR/runner.properties"
fi

# Detectar RUNNER_ID desde hostname
HOSTNAME_CLEAN=$(hostname | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
RUNNER_ID="${RUNNER_ID:-mac-${HOSTNAME_CLEAN}}"

# ── Detectar Java ────────────────────────────────────────────
find_java() {
    # Prioridad: JAVA_HOME, /usr/bin/java, Homebrew, SDKMAN
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"; return
    fi
    if [ -x "/usr/bin/java" ]; then echo "/usr/bin/java"; return; fi
    if [ -x "/opt/homebrew/opt/openjdk/bin/java" ]; then
        echo "/opt/homebrew/opt/openjdk/bin/java"; return
    fi
    if [ -x "/usr/local/opt/openjdk/bin/java" ]; then
        echo "/usr/local/opt/openjdk/bin/java"; return
    fi
    if [ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]; then
        echo "$HOME/.sdkman/candidates/java/current/bin/java"; return
    fi
    command -v java 2>/dev/null || true
}

# ── Banner ───────────────────────────────────────────────────
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   Automation QA — macOS LaunchAgent Installer           ║${NC}"
echo -e "${CYAN}║   Universal Runner (Auto-Start Enterprise)              ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── PASO 1: Java ─────────────────────────────────────────────
echo -e "${BOLD}[1/5] Verificando Java...${NC}"
JAVA_BIN=$(find_java)
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    echo -e "${RED}[ERROR] Java no encontrado.${NC}"
    echo "        Instala JDK 17+:"
    echo "        brew install openjdk@17"
    echo "        O descarga desde: https://adoptium.net"
    exit 1
fi
JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -1)
echo -e "${GREEN}[OK]${NC}    $JAVA_VER ($JAVA_BIN)"
echo ""

# ── PASO 2: Compilar JAR ─────────────────────────────────────
echo -e "${BOLD}[2/5] Preparando cinepolis-runner.jar...${NC}"
cd "$RUNNER_DIR"

JAR_SRC="$RUNNER_DIR/target/cinepolis-runner.jar"
if [ -f "$JAR_SRC" ]; then
    echo -e "${GREEN}[OK]${NC}    JAR encontrado: $JAR_SRC"
else
    echo -e "${YELLOW}[Build]${NC} JAR no encontrado. Compilando con Maven..."
    if ! command -v mvn &>/dev/null; then
        echo -e "${RED}[ERROR] Maven no encontrado.${NC}"
        echo "        Instala Maven: brew install maven"
        echo "        O copia cinepolis-runner.jar a: $RUNNER_DIR/target/"
        exit 1
    fi
    mvn package -q -DskipTests
    echo -e "${GREEN}[OK]${NC}    Compilacion exitosa."
fi
echo ""

# ── PASO 3: Instalar en directorio estable ───────────────────
echo -e "${BOLD}[3/5] Instalando en directorio permanente...${NC}"
mkdir -p "$INSTALL_DIR"
mkdir -p "$LOG_DIR"
mkdir -p "$PLIST_DIR"

cp "$JAR_SRC" "$JAR_DST"
echo -e "${GREEN}[OK]${NC}    JAR instalado: $JAR_DST"
echo -e "${GREEN}[OK]${NC}    Logs en:       $LOG_DIR"
echo ""

# ── PASO 4: Generar plist ────────────────────────────────────
echo -e "${BOLD}[4/5] Generando LaunchAgent plist...${NC}"

# ── Preservar SMTP_* de una instalacion previa ────────────────
# Mas abajo el plist se sobrescribe por completo (cat > "$PLIST_PATH").
# Sin este paso, cada reinstalacion/actualizacion del Runner borraria en
# silencio cualquier EnvironmentVariables SMTP_* agregado manualmente.
# Los valores solo viven en variables de shell en memoria — nunca se
# imprimen ni se escriben en ningun log.
PLISTBUDDY="/usr/libexec/PlistBuddy"
SMTP_HOST_PREV=""; SMTP_PORT_PREV=""; SMTP_USER_PREV=""; SMTP_PASS_PREV=""; SMTP_FROM_PREV=""

if [ -f "$PLIST_PATH" ] && [ -x "$PLISTBUDDY" ]; then
    SMTP_HOST_PREV=$("$PLISTBUDDY" -c "Print :EnvironmentVariables:SMTP_HOST" "$PLIST_PATH" 2>/dev/null)
    SMTP_PORT_PREV=$("$PLISTBUDDY" -c "Print :EnvironmentVariables:SMTP_PORT" "$PLIST_PATH" 2>/dev/null)
    SMTP_USER_PREV=$("$PLISTBUDDY" -c "Print :EnvironmentVariables:SMTP_USER" "$PLIST_PATH" 2>/dev/null)
    SMTP_PASS_PREV=$("$PLISTBUDDY" -c "Print :EnvironmentVariables:SMTP_PASS" "$PLIST_PATH" 2>/dev/null)
    SMTP_FROM_PREV=$("$PLISTBUDDY" -c "Print :EnvironmentVariables:SMTP_FROM" "$PLIST_PATH" 2>/dev/null)
fi

# Detener agente anterior si existe
if launchctl list | grep -q "$LABEL" 2>/dev/null; then
    launchctl unload "$PLIST_PATH" 2>/dev/null || true
    echo -e "${YELLOW}[INFO]${NC}  Agente anterior detenido."
fi

# Construir PATH extendido con rutas comunes de ADB y Xcode
EXTENDED_PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
EXTENDED_PATH="$EXTENDED_PATH:$HOME/Library/Android/sdk/platform-tools"
EXTENDED_PATH="$EXTENDED_PATH:/opt/homebrew/opt/android-platform-tools/bin"
[ -n "$ANDROID_HOME" ] && EXTENDED_PATH="$EXTENDED_PATH:$ANDROID_HOME/platform-tools"

cat > "$PLIST_PATH" <<PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${LABEL}</string>

    <key>ProgramArguments</key>
    <array>
        <string>${JAVA_BIN}</string>
        <string>-Dfile.encoding=UTF-8</string>
        <string>-DBACKEND_URL=${BACKEND_URL}</string>
        <string>-DRUNNER_TOKEN=${RUNNER_TOKEN}</string>
        <string>-DRUNNER_ID=${RUNNER_ID}</string>
        <string>-DPOLL_INTERVAL_MS=${POLL_INTERVAL_MS}</string>
        <string>-DWORK_DIR=${RUNNER_DIR}</string>
        <string>-DAPPIUM_HUB=http://127.0.0.1:4723</string>
        <string>-jar</string>
        <string>${JAR_DST}</string>
    </array>

    <!-- Arrancar automaticamente al iniciar sesion -->
    <key>RunAtLoad</key>
    <true/>

    <!-- Reiniciar si el proceso muere -->
    <key>KeepAlive</key>
    <true/>

    <!-- Delay inicial para que el sistema termine de arrancar (15s) -->
    <key>ThrottleInterval</key>
    <integer>15</integer>

    <!-- Variables de entorno (PATH para ADB y xcrun) -->
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>${EXTENDED_PATH}</string>
        <key>HOME</key>
        <string>${HOME}</string>
        <key>RUNNER_ID</key>
        <string>${RUNNER_ID}</string>
    </dict>

    <!-- Directorio de trabajo -->
    <key>WorkingDirectory</key>
    <string>${INSTALL_DIR}</string>

    <!-- Logs -->
    <key>StandardOutPath</key>
    <string>${LOG_OUT}</string>
    <key>StandardErrorPath</key>
    <string>${LOG_ERR}</string>

    <!-- Sin sesion de usuario interactiva -->
    <key>SessionCreate</key>
    <false/>
</dict>
</plist>
PLIST_EOF

echo -e "${GREEN}[OK]${NC}    Plist generado: $PLIST_PATH"
echo ""

# ── PASO 5: Cargar LaunchAgent ───────────────────────────────
echo -e "${BOLD}[5/5] Cargando LaunchAgent...${NC}"

# Fijar permisos
chmod 644 "$PLIST_PATH"

# ── Reinsertar SMTP_* preservadas (nunca se imprime el valor) ─
if [ -n "$SMTP_HOST_PREV$SMTP_PORT_PREV$SMTP_USER_PREV$SMTP_PASS_PREV$SMTP_FROM_PREV" ] && [ -x "$PLISTBUDDY" ]; then
    "$PLISTBUDDY" -c "Add :EnvironmentVariables dict" "$PLIST_PATH" >/dev/null 2>&1 || true
    [ -n "$SMTP_HOST_PREV" ] && { "$PLISTBUDDY" -c "Add :EnvironmentVariables:SMTP_HOST string $SMTP_HOST_PREV" "$PLIST_PATH" >/dev/null 2>&1 || true; }
    [ -n "$SMTP_PORT_PREV" ] && { "$PLISTBUDDY" -c "Add :EnvironmentVariables:SMTP_PORT string $SMTP_PORT_PREV" "$PLIST_PATH" >/dev/null 2>&1 || true; }
    [ -n "$SMTP_USER_PREV" ] && { "$PLISTBUDDY" -c "Add :EnvironmentVariables:SMTP_USER string $SMTP_USER_PREV" "$PLIST_PATH" >/dev/null 2>&1 || true; }
    [ -n "$SMTP_PASS_PREV" ] && { "$PLISTBUDDY" -c "Add :EnvironmentVariables:SMTP_PASS string $SMTP_PASS_PREV" "$PLIST_PATH" >/dev/null 2>&1 || true; }
    [ -n "$SMTP_FROM_PREV" ] && { "$PLISTBUDDY" -c "Add :EnvironmentVariables:SMTP_FROM string $SMTP_FROM_PREV" "$PLIST_PATH" >/dev/null 2>&1 || true; }

    echo "  Configuracion SMTP de instalacion previa:"
    if [ -n "$SMTP_HOST_PREV" ]; then echo "    SMTP_HOST: PRESERVED"; else echo "    SMTP_HOST: MISSING"; fi
    if [ -n "$SMTP_PORT_PREV" ]; then echo "    SMTP_PORT: PRESERVED"; else echo "    SMTP_PORT: MISSING"; fi
    if [ -n "$SMTP_USER_PREV" ]; then echo "    SMTP_USER: PRESERVED"; else echo "    SMTP_USER: MISSING"; fi
    if [ -n "$SMTP_PASS_PREV" ]; then echo "    SMTP_PASS: PRESERVED"; else echo "    SMTP_PASS: MISSING"; fi
    if [ -n "$SMTP_FROM_PREV" ]; then echo "    SMTP_FROM: PRESERVED"; else echo "    SMTP_FROM: MISSING"; fi
fi

# Cargar el agente
launchctl load -w "$PLIST_PATH" 2>/dev/null
sleep 2

# Verificar que este corriendo
if launchctl list | grep -q "$LABEL"; then
    echo -e "${GREEN}[OK]${NC}    LaunchAgent cargado y activo."
else
    echo -e "${YELLOW}[WARN]${NC}  El agente se cargo pero aun no aparece en launchctl list."
    echo "        Esto es normal, el runner puede tardar unos segundos en iniciar."
fi

echo ""
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Instalacion completada con exito.${NC}"
echo ""
echo "  El Runner arrancara AUTOMATICAMENTE al iniciar sesion."
echo "  No necesitas abrir Terminal ni ejecutar ningun script."
echo ""
echo "  Runner ID:  $RUNNER_ID"
echo "  Backend:    $BACKEND_URL"
echo "  JAR:        $JAR_DST"
echo "  Logs:       $LOG_OUT"
echo ""
echo "  Comandos utiles:"
echo "    Ver logs:      tail -f \"$LOG_OUT\""
echo "    Detener:       launchctl unload \"$PLIST_PATH\""
echo "    Reiniciar:     launchctl unload \"$PLIST_PATH\" && launchctl load -w \"$PLIST_PATH\""
echo "    Desinstalar:   bash uninstall-service.sh"
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  El runner aparecera en el Dashboard en ~15 segundos."
echo ""
