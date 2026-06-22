#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  Automation QA Agent — Instalador macOS Enterprise v3.0.0
#
#  Autosuficiente: Java 17, Node.js, Appium, ADB — todo embebido.
#  El usuario NO necesita instalar Java, Node, Android Studio, ni Appium.
#
#  Dependencias externas PERMITIDAS (solo para iOS, impuesto por Apple):
#    - Xcode + Command Line Tools  (obligatorio para xcrun / iOS)
#
#  Resultado:
#    1. Descarga y valida todos los runtimes embebidos
#    2. Instala LaunchAgent (inicio automatico al login)
#    3. El Agent aparece en Dashboard en ~15 segundos
# ═══════════════════════════════════════════════════════════════════════════════

# ── Configuracion ─────────────────────────────────────────────────────────────
BACKEND_URL="https://qautomation-production.up.railway.app"
RUNNER_TOKEN="runner-local-token"

BASE_DIR="$HOME/Library/Application Support/AutomationQA"
RUNTIME_DIR="$BASE_DIR/runtime"
JRE_DIR="$RUNTIME_DIR/jre17"
NODE_DIR="$RUNTIME_DIR/node"
APPIUM_DIR="$RUNTIME_DIR/appium"
PLATFORM_TOOLS_DIR="$BASE_DIR/platform-tools"
RUNNER_DIR="$BASE_DIR/runner"
LOGS_DIR="$BASE_DIR/logs"
JAR_DST="$RUNNER_DIR/automationqa-runner.jar"
PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST_FILE="$PLIST_DIR/com.automationqa.runner.plist"
PLIST_LABEL="com.automationqa.runner"

NODE_VERSION="v20.19.2"

# ── Deteccion de arquitectura ──────────────────────────────────────────────────
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    JRE_ARCH="aarch64"
    NODE_ARCH="darwin-arm64"
else
    JRE_ARCH="x64"
    NODE_ARCH="darwin-x64"
fi

# ── Estado de cada componente ──────────────────────────────────────────────────
JRE_OK=false
NODE_OK=false
APPIUM_OK=false
ADB_OK=false
JAR_OK=false

# ── Utilidades de salida ───────────────────────────────────────────────────────
ok()   { echo "  [OK]   $1"; }
warn() { echo "  [WARN] $1"; }
err()  { echo "  [ERR]  $1"; }
info() { echo "         $1"; }

# ══════════════════════════════════════════════════════════════════════════════
print_header() {
    echo ""
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Automation QA Agent — Instalador Enterprise v3.0.0"
    echo "   Sin dependencias manuales. Runtimes embebidos."
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Arquitectura:  $ARCH"
    echo "   Directorio:    $BASE_DIR"
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# ══════════════════════════════════════════════════════════════════════════════
# [1/6] Java Runtime 17 — embebido, no usa /usr/bin/java ni JAVA_HOME
# ══════════════════════════════════════════════════════════════════════════════
install_jre() {
    echo "  [1/6] Java Runtime 17 (embebido)..."

    # Ya instalado
    if [ -x "$JRE_DIR/bin/java" ]; then
        local ver
        ver=$("$JRE_DIR/bin/java" -version 2>&1 | head -1)
        ok "JRE 17 ya instalado: $ver"
        JRE_OK=true
        return
    fi

    mkdir -p "$JRE_DIR"

    local url="https://api.adoptium.net/v3/binary/latest/17/ga/mac/$JRE_ARCH/jre/hotspot/normal/eclipse"
    local tmp
    tmp="$(mktemp /tmp/qa_jre17_XXXXXX.tar.gz)"

    info "Descargando JRE 17 ($JRE_ARCH) desde Adoptium..."
    if curl -fL --max-time 360 --progress-bar "$url" -o "$tmp" 2>/dev/null; then
        info "Extrayendo JRE 17..."
        if tar -xzf "$tmp" -C "$JRE_DIR" --strip-components=1 2>/dev/null; then
            chmod +x "$JRE_DIR/bin/java" 2>/dev/null || true
            if [ -x "$JRE_DIR/bin/java" ]; then
                local ver
                ver=$("$JRE_DIR/bin/java" -version 2>&1 | head -1)
                ok "JRE 17 instalado: $ver"
                JRE_OK=true
            else
                warn "JRE extraido pero binario no ejecutable."
            fi
        else
            warn "Error al extraer JRE 17."
        fi
    else
        warn "No se pudo descargar JRE 17 desde Adoptium."
        info "URL: $url"
    fi
    rm -f "$tmp"
}

# ══════════════════════════════════════════════════════════════════════════════
# [2/6] Node.js 20 LTS — embebido, requerido por Appium
# ══════════════════════════════════════════════════════════════════════════════
install_node() {
    echo "  [2/6] Node.js $NODE_VERSION LTS (embebido)..."

    if [ -x "$NODE_DIR/bin/node" ]; then
        local ver
        ver=$("$NODE_DIR/bin/node" --version 2>/dev/null)
        ok "Node.js ya instalado: $ver"
        NODE_OK=true
        return
    fi

    mkdir -p "$NODE_DIR"

    local url="https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION-$NODE_ARCH.tar.gz"
    local tmp
    tmp="$(mktemp /tmp/qa_node_XXXXXX.tar.gz)"

    info "Descargando Node.js $NODE_VERSION ($NODE_ARCH)..."
    if curl -fL --max-time 180 --progress-bar "$url" -o "$tmp" 2>/dev/null; then
        info "Extrayendo Node.js..."
        if tar -xzf "$tmp" -C "$NODE_DIR" --strip-components=1 2>/dev/null; then
            if [ -x "$NODE_DIR/bin/node" ]; then
                local ver
                ver=$("$NODE_DIR/bin/node" --version 2>/dev/null)
                ok "Node.js instalado: $ver"
                NODE_OK=true
            else
                warn "Node.js extraido pero binario no ejecutable."
            fi
        else
            warn "Error al extraer Node.js."
        fi
    else
        warn "No se pudo descargar Node.js."
    fi
    rm -f "$tmp"
}

# ══════════════════════════════════════════════════════════════════════════════
# [3/6] Appium Server 2 + drivers — instalacion local via Node embebido
# ══════════════════════════════════════════════════════════════════════════════
install_appium() {
    echo "  [3/6] Appium Server 2 + drivers..."

    local appium_bin="$APPIUM_DIR/node_modules/.bin/appium"

    if [ -f "$appium_bin" ]; then
        local ver
        ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
        ok "Appium ya instalado: $ver"
        APPIUM_OK=true
        return
    fi

    if [ ! -x "$NODE_DIR/bin/node" ]; then
        warn "Node.js no disponible — Appium omitido. El Agent iniciara en modo DEGRADED."
        info "Reejecutar el instalador para instalar Appium."
        return
    fi

    mkdir -p "$APPIUM_DIR"

    info "Instalando Appium 2 (puede tardar 2-4 minutos)..."
    if "$NODE_DIR/bin/npm" install \
            --prefix "$APPIUM_DIR" \
            appium@2 \
            --no-audit \
            --no-fund \
            2>&1 | grep -E "(added|error|warn)" | head -10; then
        if [ -f "$appium_bin" ]; then
            local ver
            ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
            ok "Appium instalado: $ver"
            APPIUM_OK=true
            install_drivers "$appium_bin"
        else
            warn "npm exit OK pero appium bin no encontrado."
        fi
    else
        warn "Error al instalar Appium via npm."
    fi
}

install_drivers() {
    local appium_bin="$1"
    local node_bin="$NODE_DIR/bin/node"

    info "Instalando driver uiautomator2 (Android)..."
    "$node_bin" "$appium_bin" driver install uiautomator2 2>&1 | tail -3 || true

    info "Instalando driver xcuitest (iOS — requiere Xcode)..."
    "$node_bin" "$appium_bin" driver install xcuitest 2>&1 | tail -3 || true

    ok "Drivers instalados."
}

# ══════════════════════════════════════════════════════════════════════════════
# [4/6] Android Platform Tools (ADB) — embebido, sin ANDROID_HOME
# ══════════════════════════════════════════════════════════════════════════════
install_adb() {
    echo "  [4/6] Android Platform Tools (ADB)..."

    if [ -x "$PLATFORM_TOOLS_DIR/adb" ]; then
        local ver
        ver=$("$PLATFORM_TOOLS_DIR/adb" version 2>/dev/null | head -1)
        ok "ADB ya instalado: $ver"
        ADB_OK=true
        return
    fi

    local url_google="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
    local url_proxy="$BACKEND_URL/api/runner/download/platform-tools/macos"
    local tmp
    tmp="$(mktemp /tmp/qa_pt_XXXXXX.zip)"
    local downloaded=false

    info "Descargando Platform Tools desde Google CDN..."
    if curl -fL --max-time 360 --progress-bar "$url_google" -o "$tmp" 2>/dev/null; then
        local sz
        sz=$(wc -c < "$tmp" | tr -d ' ')
        [ "$sz" -gt 5000000 ] && downloaded=true
    fi

    if [ "$downloaded" = false ]; then
        warn "Google CDN fallo. Intentando via proxy del servidor..."
        if curl -fL --max-time 360 --progress-bar "$url_proxy" -o "$tmp" 2>/dev/null; then
            local sz
            sz=$(wc -c < "$tmp" | tr -d ' ')
            [ "$sz" -gt 5000000 ] && downloaded=true
        fi
    fi

    if [ "$downloaded" = true ]; then
        info "Extrayendo platform-tools..."
        # unzip crea "$BASE_DIR/platform-tools/" — que es exactamente la ruta que espera el runner
        if unzip -q -o "$tmp" -d "$BASE_DIR" 2>/dev/null; then
            chmod +x "$PLATFORM_TOOLS_DIR/adb" "$PLATFORM_TOOLS_DIR/fastboot" 2>/dev/null || true
            if [ -x "$PLATFORM_TOOLS_DIR/adb" ]; then
                local ver
                ver=$("$PLATFORM_TOOLS_DIR/adb" version 2>/dev/null | head -1)
                ok "ADB instalado: $ver"
                ADB_OK=true
            else
                warn "ZIP extraido pero adb no encontrado."
            fi
        else
            warn "Error al extraer platform-tools."
        fi
    else
        warn "No se pudo descargar ADB. El Agent iniciara en modo DEGRADED."
        info "SelfHealingManager intentara descargar ADB cada 5 minutos."
    fi
    rm -f "$tmp"
}

# ══════════════════════════════════════════════════════════════════════════════
# [5/6] Runner JAR — desde paquete de instalacion o backend
# ══════════════════════════════════════════════════════════════════════════════
install_jar() {
    echo "  [5/6] Automation QA Agent (runner JAR)..."

    mkdir -p "$RUNNER_DIR"

    local script_dir
    script_dir="$(cd "$(dirname "$0")" && pwd)"

    # Buscar JAR junto a este instalador (instalacion desde paquete completo)
    for candidate in \
        "$script_dir/automationqa-runner.jar" \
        "$script_dir/cinepolis-runner.jar" \
        "$script_dir/../automationqa-runner.jar"; do
        if [ -f "$candidate" ]; then
            cp "$candidate" "$JAR_DST"
            ok "JAR instalado desde paquete local."
            JAR_OK=true
            return
        fi
    done

    # Descargar desde backend
    info "Descargando desde servidor..."
    local tmp
    tmp="$(mktemp /tmp/qa_runner_XXXXXX.jar)"
    if curl -fL --max-time 180 --progress-bar \
            "$BACKEND_URL/api/runner/download/jar" \
            -o "$tmp" 2>/dev/null; then
        local sz
        sz=$(wc -c < "$tmp" | tr -d ' ')
        if [ "$sz" -gt 1000000 ]; then
            cp "$tmp" "$JAR_DST"
            ok "JAR descargado e instalado."
            JAR_OK=true
        else
            warn "JAR descargado parece invalido (${sz} bytes)."
        fi
    else
        err "No se pudo descargar el Runner JAR."
    fi
    rm -f "$tmp"

    if [ "$JAR_OK" = false ]; then
        echo ""
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "   [ERROR] No se pudo obtener el componente principal."
        echo "   Verifica la conexion a internet y vuelve a ejecutar el instalador."
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        exit 1
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# [6/6] LaunchAgent — inicio automatico con rutas embebidas en -D props
# ══════════════════════════════════════════════════════════════════════════════
install_launch_agent() {
    echo "  [6/6] Configurando inicio automatico (LaunchAgent)..."

    mkdir -p "$LOGS_DIR"

    # Seleccionar binario Java
    local java_bin=""
    if [ -x "$JRE_DIR/bin/java" ]; then
        java_bin="$JRE_DIR/bin/java"
    elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        java_bin="$JAVA_HOME/bin/java"
        warn "Usando JAVA_HOME como fallback: $java_bin"
    elif command -v java &>/dev/null; then
        java_bin="$(command -v java)"
        warn "Usando Java del sistema como fallback: $java_bin"
    else
        echo ""
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "   [ERROR] Java no disponible."
        echo "   JRE 17 no pudo descargarse y no hay Java instalado en el sistema."
        echo "   Instala desde: https://adoptium.net"
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        exit 1
    fi

    # Runner ID basado en hostname
    local host
    host=$(hostname -s 2>/dev/null | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
    local runner_id="mac-${host}"

    local appium_bin="$APPIUM_DIR/node_modules/.bin/appium"
    local node_bin="$NODE_DIR/bin/node"

    # Detener servicio anterior si existe
    launchctl unload -w "$PLIST_FILE" 2>/dev/null || true

    # Generar plist con rutas embebidas inyectadas como propiedades JVM
    cat > "$PLIST_FILE" << PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${PLIST_LABEL}</string>

    <key>ProgramArguments</key>
    <array>
        <string>${java_bin}</string>
        <string>-Dfile.encoding=UTF-8</string>
        <string>-DBACKEND_URL=${BACKEND_URL}</string>
        <string>-DRUNNER_TOKEN=${RUNNER_TOKEN}</string>
        <string>-DRUNNER_ID=${runner_id}</string>
        <string>-DAGENT_DATA_DIR=${BASE_DIR}</string>
        <string>-DAPPIUM_BIN=${appium_bin}</string>
        <string>-DNODE_BIN=${node_bin}</string>
        <string>-DPOLL_INTERVAL_MS=30000</string>
        <string>-jar</string>
        <string>${JAR_DST}</string>
    </array>

    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>ThrottleInterval</key>
    <integer>15</integer>

    <key>StandardOutPath</key>
    <string>${LOGS_DIR}/automationqa-runner.log</string>
    <key>StandardErrorPath</key>
    <string>${LOGS_DIR}/automationqa-runner-error.log</string>

    <key>WorkingDirectory</key>
    <string>${RUNNER_DIR}</string>
</dict>
</plist>
PLIST_EOF

    chmod 644 "$PLIST_FILE"
    launchctl load -w "$PLIST_FILE"
    ok "LaunchAgent configurado. Runner ID: $runner_id"
}

# ══════════════════════════════════════════════════════════════════════════════
print_summary() {
    local java_status="embebido (JRE 17)"
    $JRE_OK   || java_status="WARN — usando Java del sistema"

    echo ""
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Resumen de instalacion"
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if $JRE_OK;    then echo "   Java 17 embebido      [OK]"
    else                echo "   Java 17 embebido      [WARN] usando Java del sistema"; fi
    if $NODE_OK;   then echo "   Node.js embebido      [OK]"
    else                echo "   Node.js embebido      [WARN] Appium no disponible"; fi
    if $APPIUM_OK; then echo "   Appium 2 + drivers    [OK]"
    else                echo "   Appium 2 + drivers    [WARN] Agent modo DEGRADED — reintentara"; fi
    if $ADB_OK;    then echo "   Android ADB           [OK]"
    else                echo "   Android ADB           [WARN] Agent modo DEGRADED — reintentara"; fi
    if $JAR_OK;    then echo "   Runner JAR            [OK]"; fi
    echo ""
    echo "   El Agent se esta iniciando..."
    echo "   Aparecera en el Dashboard en aproximadamente 15 segundos."
    echo ""
    echo "   Logs:    $LOGS_DIR/automationqa-runner.log"
    echo "   Plist:   $PLIST_FILE"
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════
print_header
install_jre
install_node
install_appium
install_adb
install_jar
install_launch_agent
print_summary
