#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  Automation QA Agent — Instalador macOS Enterprise v5.0.0
#
#  Autosuficiente: Java 17, Node.js, Appium, ADB — todo embebido.
#  El usuario NO necesita instalar Java, Node, Android Studio, ni Appium.
#
#  V5: Appium y drivers pre-empaquetados en el bundle (.app/Contents/Appium/).
#      npm install NUNCA se ejecuta en produccion.
#      Solo se usa el JRE embebido — JAVA_HOME y java del sistema ignorados.
#      APPIUM_HOME exportado al LaunchAgent para drivers pre-instalados.
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

# Directorio donde los drivers pre-instalados (APPIUM_HOME) se almacenan
APPIUM_HOME_DIR="$RUNTIME_DIR/appium-home"

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
# [V5] SHA256 Checksum Validation
#
# Flujo: Descarga → calcular SHA256 → comparar → instalar (o reintentar)
# ══════════════════════════════════════════════════════════════════════════════

# Calcula SHA256 de un archivo usando shasum (disponible en macOS por default)
compute_sha256() {
    shasum -a 256 "$1" 2>/dev/null | awk '{print $1}'
}

# Valida un archivo contra un SHA256 esperado.
# Retorna 0 (ok) o 1 (mismatch). Pasa si expected_sha esta vacio.
validate_sha256() {
    local file="$1"
    local expected="$2"
    [ -z "$expected" ] && return 0
    local actual
    actual=$(compute_sha256 "$file")
    if [ "$actual" = "$expected" ]; then
        return 0
    else
        err "SHA256 invalido para $(basename "$file")"
        info "  esperado: $expected"
        info "  actual:   $actual"
        return 1
    fi
}

# Escribe un sidecar {file}.sha256 compatible con ChecksumValidator.matchesBaseline()
# Formato: "hexhash  filename"  (igual al que escribe Java)
write_sha256_sidecar() {
    local file="$1"
    [ -f "$file" ] || return 0
    local hash
    hash=$(compute_sha256 "$file")
    printf '%s  %s\n' "$hash" "$(basename "$file")" > "${file}.sha256" 2>/dev/null || true
}

# Descarga el SHA256 oficial de Node.js desde nodejs.org SHASUMS256.txt
fetch_node_sha256() {
    local shasums_url="https://nodejs.org/dist/$NODE_VERSION/SHASUMS256.txt"
    curl -fsSL --max-time 30 "$shasums_url" 2>/dev/null \
        | grep "node-$NODE_VERSION-$NODE_ARCH\.tar\.gz$" \
        | awk '{print $1}'
}

# Descarga el SHA256 del JRE 17 desde la API de Adoptium
fetch_jre_sha256() {
    local api_url="https://api.adoptium.net/v3/assets/latest/17/ga?architecture=${JRE_ARCH}&image_type=jre&jvm_impl=hotspot&os=mac&project=jdk&vendor=eclipse"
    curl -fsSL --max-time 30 "$api_url" 2>/dev/null \
        | grep -o '"checksum":"[^"]*"' \
        | head -1 \
        | cut -d'"' -f4
}

# Intenta descargar un archivo con validacion SHA256 y reintento automatico.
# Uso: download_with_sha256 <url> <dest_tmp> <expected_sha> <label>
# Retorna 0 si el archivo es valido, 1 si falla ambos intentos.
download_with_sha256() {
    local url="$1"
    local dest="$2"
    local expected_sha="$3"
    local label="$4"

    info "Descargando $label..."
    if curl -fL --max-time 360 --progress-bar "$url" -o "$dest" 2>/dev/null; then
        if [ -n "$expected_sha" ] && ! validate_sha256 "$dest" "$expected_sha"; then
            warn "SHA256 invalido para $label — reintentando..."
            rm -f "$dest"
            if curl -fL --max-time 360 --progress-bar "$url" -o "$dest" 2>/dev/null \
               && validate_sha256 "$dest" "$expected_sha"; then
                ok "SHA256 $label verificado (reintento)."
                return 0
            else
                warn "SHA256 invalido en segundo intento. $label no sera instalado."
                rm -f "$dest"
                return 1
            fi
        fi
        [ -n "$expected_sha" ] && ok "SHA256 $label verificado."
        return 0
    fi
    return 1
}

# ══════════════════════════════════════════════════════════════════════════════
print_header() {
    echo ""
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Automation QA Agent — Instalador Enterprise v5.0.0"
    echo "   Sin dependencias manuales. Runtimes embebidos."
    echo "   Appium + drivers pre-empaquetados. Sin npm en produccion."
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

    # [V5] Obtener SHA256 esperado antes de la descarga
    local expected_sha=""
    info "Obteniendo SHA256 de Adoptium..."
    expected_sha=$(fetch_jre_sha256 2>/dev/null || echo "")
    [ -n "$expected_sha" ] && info "SHA256 esperado: ${expected_sha:0:16}..."

    if download_with_sha256 "$url" "$tmp" "$expected_sha" "JRE 17"; then
        info "Extrayendo JRE 17..."
        if tar -xzf "$tmp" -C "$JRE_DIR" --strip-components=1 2>/dev/null; then

            # Adoptium macOS tarballs (ARM64 y x64) usan estructura de bundle .jre:
            #   --strip-components=1 deja:  $JRE_DIR/Contents/Home/bin/java
            #   pero el script espera:      $JRE_DIR/bin/java
            #
            # Solucion: detectar el binario real con find y crear un symlink en
            # la ruta canonica.  No se borran ni mueven archivos — solo un symlink.
            if [ ! -x "$JRE_DIR/bin/java" ]; then
                info "Detectando estructura interna del JRE..."
                local actual_java
                actual_java=$(find "$JRE_DIR" -name "java" -type f 2>/dev/null | head -1)

                if [ -n "$actual_java" ]; then
                    info "Binario encontrado: $actual_java"
                    mkdir -p "$JRE_DIR/bin"
                    ln -sf "$actual_java" "$JRE_DIR/bin/java"
                    info "Symlink creado: $JRE_DIR/bin/java -> $actual_java"
                else
                    warn "No se encontro binario java en la estructura extraida."
                    info "Contenido de $JRE_DIR (4 niveles):"
                    find "$JRE_DIR" -maxdepth 4 2>/dev/null | while IFS= read -r f; do info "  $f"; done
                fi
            fi

            chmod +x "$JRE_DIR/bin/java" 2>/dev/null || true

            if [ -x "$JRE_DIR/bin/java" ]; then
                local ver
                ver=$("$JRE_DIR/bin/java" -version 2>&1 | head -1)
                ok "JRE 17 instalado: $ver"
                JRE_OK=true
                write_sha256_sidecar "$JRE_DIR/bin/java"
            else
                warn "JRE extraido pero binario no ejecutable."
                info "Rutas java encontradas en $JRE_DIR:"
                find "$JRE_DIR" -name "java" -maxdepth 6 2>/dev/null | while IFS= read -r f; do info "  $f"; done
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

    # [V5] Obtener SHA256 oficial de nodejs.org SHASUMS256.txt
    local expected_sha=""
    info "Obteniendo SHA256 desde nodejs.org..."
    expected_sha=$(fetch_node_sha256 2>/dev/null || echo "")
    [ -n "$expected_sha" ] && info "SHA256 esperado: ${expected_sha:0:16}..."

    if download_with_sha256 "$url" "$tmp" "$expected_sha" "Node.js $NODE_VERSION"; then
        info "Extrayendo Node.js..."
        if tar -xzf "$tmp" -C "$NODE_DIR" --strip-components=1 2>/dev/null; then
            if [ -x "$NODE_DIR/bin/node" ]; then
                local ver
                ver=$("$NODE_DIR/bin/node" --version 2>/dev/null)
                ok "Node.js instalado: $ver"
                NODE_OK=true
                write_sha256_sidecar "$NODE_DIR/bin/node"
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
# [3/6] Appium Server 2 + drivers — pre-empaquetado (V5: sin npm en produccion)
#
# Orden de busqueda:
#   1. Bundle .app (Contents/Appium/) — PREFERIDO para distribuciones enterprise
#   2. Descarga tarball pre-construido desde el backend
#   3. npm install como FALLBACK de emergencia (solo si lo anterior falla)
# ══════════════════════════════════════════════════════════════════════════════
install_appium() {
    echo "  [3/6] Appium Server 2 + drivers..."

    local appium_bin="$APPIUM_DIR/node_modules/.bin/appium"

    # ── Ya instalado ─────────────────────────────────────────────────────────
    if [ -f "$appium_bin" ]; then
        local ver
        ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
        ok "Appium ya instalado: $ver"
        APPIUM_OK=true
        setup_appium_home
        return
    fi

    # ── [V5] Opcion 1: Bundle pre-empaquetado en .app ────────────────────────
    # Cuando se distribuye como AutomationQA-Agent.app, Appium viene pre-instalado
    # en Contents/Appium/ con todos sus drivers listos.
    local script_dir
    script_dir="$(cd "$(dirname "$0")" && pwd)"
    local bundle_appium="$script_dir/../Appium/node_modules/.bin/appium"
    local bundle_drivers="$script_dir/../Drivers"

    if [ -f "$bundle_appium" ]; then
        info "Appium pre-empaquetado encontrado — copiando bundle..."
        mkdir -p "$APPIUM_DIR"
        cp -r "$(dirname "$(dirname "$bundle_appium")")" "$RUNTIME_DIR/appium_tmp"
        rsync -a --delete "$RUNTIME_DIR/appium_tmp/" "$APPIUM_DIR/" 2>/dev/null \
            || cp -rf "$RUNTIME_DIR/appium_tmp/." "$APPIUM_DIR/"
        rm -rf "$RUNTIME_DIR/appium_tmp"

        # Copiar drivers pre-instalados si existen en el bundle
        if [ -d "$bundle_drivers" ]; then
            info "Copiando drivers pre-instalados..."
            mkdir -p "$APPIUM_HOME_DIR"
            rsync -a --delete "$bundle_drivers/" "$APPIUM_HOME_DIR/" 2>/dev/null \
                || cp -rf "$bundle_drivers/." "$APPIUM_HOME_DIR/"
            ok "Drivers copiados desde bundle."
        fi

        if [ -f "$appium_bin" ]; then
            local ver
            ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
            ok "Appium instalado desde bundle: $ver"
            APPIUM_OK=true
            setup_appium_home
            return
        fi
        warn "Bundle encontrado pero Appium bin no copiado correctamente."
    fi

    # ── [V5] Opcion 2: Descarga tarball pre-construido desde backend ──────────
    local tarball_url="$BACKEND_URL/api/runner/download/appium-bundle/macos"
    local tmp_tar
    tmp_tar="$(mktemp /tmp/qa_appium_XXXXXX.tar.gz)"
    info "Descargando bundle pre-construido de Appium..."
    if curl -fL --max-time 300 --progress-bar "$tarball_url" -o "$tmp_tar" 2>/dev/null; then
        local sz
        sz=$(wc -c < "$tmp_tar" | tr -d ' ')
        if [ "$sz" -gt 1000000 ]; then
            mkdir -p "$APPIUM_DIR"
            if tar -xzf "$tmp_tar" -C "$RUNTIME_DIR" 2>/dev/null; then
                if [ -f "$appium_bin" ]; then
                    local ver
                    ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
                    ok "Appium instalado desde tarball del servidor: $ver"
                    APPIUM_OK=true
                    rm -f "$tmp_tar"
                    setup_appium_home
                    return
                fi
            fi
        fi
    fi
    rm -f "$tmp_tar"

    # ── [V5] Opcion 3: npm install — fallback de emergencia ──────────────────
    # Esto solo deberia ejecutarse en entornos de desarrollo o si el bundle
    # no esta disponible. En produccion el bundle SIEMPRE debe estar presente.
    if [ ! -x "$NODE_DIR/bin/node" ]; then
        warn "Node.js no disponible — Appium omitido. El Agent iniciara en modo DEGRADED."
        return
    fi

    warn "Bundle no encontrado. Instalando Appium via npm (puede tardar 2-4 min)..."
    warn "AVISO: npm install en produccion indica que el bundle no esta configurado."
    mkdir -p "$APPIUM_DIR"
    if "$NODE_DIR/bin/npm" install \
            --prefix "$APPIUM_DIR" \
            appium@2 \
            --no-audit \
            --no-fund \
            2>&1 | grep -E "(added|error|warn)" | head -10; then
        if [ -f "$appium_bin" ]; then
            local ver
            ver=$("$NODE_DIR/bin/node" "$appium_bin" --version 2>/dev/null)
            ok "Appium instalado via npm (fallback): $ver"
            APPIUM_OK=true
            install_drivers_fallback "$appium_bin"
            setup_appium_home
        else
            warn "npm exit OK pero appium bin no encontrado."
        fi
    else
        warn "Error al instalar Appium via npm. El Agent iniciara en modo DEGRADED."
    fi
}

# Establece APPIUM_HOME apuntando a los drivers pre-instalados.
# Se llama despues de cualquier ruta de instalacion exitosa.
setup_appium_home() {
    mkdir -p "$APPIUM_HOME_DIR"
    ok "APPIUM_HOME: $APPIUM_HOME_DIR"
}

# Instala drivers via 'appium driver install' — solo como fallback cuando npm
# tuvo que instalar Appium. En produccion los drivers vienen en el bundle.
#
# Appium 2.x y 3.x usan versiones de driver incompatibles entre si:
#   Appium 2.x  →  uiautomator2@2  /  xcuitest@7
#   Appium 3.x+ →  uiautomator2    /  xcuitest  (latest)
install_drivers_fallback() {
    local appium_bin="$1"
    local node_bin="$NODE_DIR/bin/node"
    local UA2_OK=false
    local XCUI_OK=false

    # Detectar version instalada de Appium para seleccionar drivers compatibles
    local appium_ver appium_major ua2_spec xcui_spec
    appium_ver=$("$node_bin" "$appium_bin" --version 2>/dev/null | head -1 | tr -d '[:space:]')
    appium_major=$(echo "$appium_ver" | cut -d'.' -f1)
    info "Appium version detectada: $appium_ver (major: $appium_major)"

    if [ "$appium_major" = "2" ]; then
        ua2_spec="uiautomator2@2"
        xcui_spec="xcuitest@7"
        info "Seleccionando drivers compatibles con Appium 2.x"
    else
        ua2_spec="uiautomator2"
        xcui_spec="xcuitest"
        info "Seleccionando drivers latest para Appium ${appium_major}.x"
    fi

    # Instalar uiautomator2 (Android — obligatorio)
    info "Instalando driver $ua2_spec (Android)..."
    if APPIUM_HOME="$APPIUM_HOME_DIR" "$node_bin" "$appium_bin" driver install "$ua2_spec" 2>&1 | tail -5; then
        if APPIUM_HOME="$APPIUM_HOME_DIR" "$node_bin" "$appium_bin" driver list --installed 2>&1 | grep -qi "uiautomator2"; then
            ok "uiautomator2 instalado."
            UA2_OK=true
        else
            warn "uiautomator2 FAIL — no aparece en 'driver list --installed'."
        fi
    else
        warn "uiautomator2 FAIL — error durante la instalacion."
    fi

    # Instalar xcuitest (iOS — opcional si no hay Xcode)
    info "Instalando driver $xcui_spec (iOS — requiere Xcode)..."
    if APPIUM_HOME="$APPIUM_HOME_DIR" "$node_bin" "$appium_bin" driver install "$xcui_spec" 2>&1 | tail -5; then
        if APPIUM_HOME="$APPIUM_HOME_DIR" "$node_bin" "$appium_bin" driver list --installed 2>&1 | grep -qi "xcuitest"; then
            ok "xcuitest instalado."
            XCUI_OK=true
        else
            warn "xcuitest FAIL — no aparece en 'driver list --installed'."
        fi
    else
        warn "xcuitest FAIL — error durante la instalacion."
    fi

    # Appium solo es funcional si uiautomator2 (Android) esta presente
    if [ "$UA2_OK" = true ]; then
        ok "Drivers instalados (uiautomator2=$UA2_OK xcuitest=$XCUI_OK)."
    else
        warn "Driver uiautomator2 no disponible. Agent iniciara en modo DEGRADED."
        APPIUM_OK=false
    fi
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
                write_sha256_sidecar "$PLATFORM_TOOLS_DIR/adb"
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
            write_sha256_sidecar "$JAR_DST"
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
            write_sha256_sidecar "$JAR_DST"
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

    # [V5] Solo se acepta el JRE embebido — JAVA_HOME y java del sistema ignorados.
    # Esto garantiza que el agent siempre corre con JRE 17 validado,
    # independientemente del entorno del usuario.
    local java_bin="$JRE_DIR/bin/java"
    if [ ! -x "$java_bin" ]; then
        echo ""
        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "   [ERROR] JRE 17 embebido requerido pero no disponible."
        echo "   No se encontro: $java_bin"
        echo ""
        echo "   Solucion: ejecutar el instalador en un equipo con acceso a"
        echo "   internet para que JRE 17 pueda descargarse de Adoptium."
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
        <string>-DAPPIUM_HOME=${APPIUM_HOME_DIR}</string>
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
    echo ""
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   Resumen de instalacion v5.0.0"
    echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if $JRE_OK;    then echo "   Java 17 embebido      [OK]"
    else                echo "   Java 17 embebido      [ERROR] requerido — reinstala con internet"; fi
    if $NODE_OK;   then echo "   Node.js embebido      [OK]"
    else                echo "   Node.js embebido      [WARN] Appium no disponible"; fi
    if $APPIUM_OK; then echo "   Appium 2 + drivers    [OK]  (pre-empaquetado)"
    else                echo "   Appium 2 + drivers    [WARN] Agent modo DEGRADED — reintentara"; fi
    if $ADB_OK;    then echo "   Android ADB           [OK]"
    else                echo "   Android ADB           [WARN] Agent modo DEGRADED — reintentara"; fi
    if $JAR_OK;    then echo "   Runner JAR            [OK]"; fi
    echo ""
    echo "   APPIUM_HOME:  $APPIUM_HOME_DIR"
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
