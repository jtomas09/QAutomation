#!/bin/bash
# ============================================================
#  Automation QA — One-click macOS/Linux Installer
#  Detecta el sistema operativo y ejecuta el instalador correcto.
#
#  Uso: bash runner/install.sh
#  (desde el directorio raiz del proyecto)
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OS=$(uname -s)

echo ""
echo "  Automation QA — Instalador Enterprise"
echo "  OS detectado: $OS"
echo ""

case "$OS" in
    Darwin)
        echo "  → macOS: instalando LaunchAgent (auto-start al login)..."
        bash "$SCRIPT_DIR/service/macos/install-service.sh"
        ;;
    Linux)
        echo "  → Linux: configurando systemd user service..."

        # Comprobar systemd
        if ! command -v systemctl &>/dev/null; then
            echo "  [WARN] systemd no disponible. Usando cron como fallback."
            RUNNER_JAR="$SCRIPT_DIR/target/cinepolis-runner.jar"
            if [ ! -f "$RUNNER_JAR" ]; then
                cd "$SCRIPT_DIR" && mvn package -q -DskipTests
            fi
            CRON_ENTRY="@reboot sleep 20 && java -DBACKEND_URL=https://qautomation-production.up.railway.app -DRUNNER_TOKEN=runner-local-token -jar $RUNNER_JAR >> $SCRIPT_DIR/logs/runner.log 2>&1 &"
            (crontab -l 2>/dev/null | grep -v "cinepolis-runner"; echo "$CRON_ENTRY") | crontab -
            echo "  [OK] Entrada cron instalada. El runner arrancara al reiniciar."
            exit 0
        fi

        # systemd user service
        RUNNER_JAR="$SCRIPT_DIR/target/cinepolis-runner.jar"
        if [ ! -f "$RUNNER_JAR" ]; then
            echo "  Compilando JAR..."
            cd "$SCRIPT_DIR" && mvn package -q -DskipTests
        fi

        HOSTNAME_CLEAN=$(hostname | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
        RUNNER_ID="${RUNNER_ID:-linux-${HOSTNAME_CLEAN}}"
        JAVA_BIN=$(command -v java)

        mkdir -p "$HOME/.config/systemd/user"
        cat > "$HOME/.config/systemd/user/automationqa-runner.service" <<EOF
[Unit]
Description=Automation QA Universal Runner
After=network.target
Wants=network.target

[Service]
Type=simple
ExecStart=${JAVA_BIN} -Dfile.encoding=UTF-8 -DBACKEND_URL=https://qautomation-production.up.railway.app -DRUNNER_TOKEN=runner-local-token -DRUNNER_ID=${RUNNER_ID} -DPOLL_INTERVAL_MS=30000 -jar ${RUNNER_JAR}
Restart=always
RestartSec=15
StandardOutput=append:${HOME}/.local/share/automationqa/runner.log
StandardError=append:${HOME}/.local/share/automationqa/runner-error.log
WorkingDirectory=${SCRIPT_DIR}

[Install]
WantedBy=default.target
EOF

        mkdir -p "$HOME/.local/share/automationqa"
        systemctl --user daemon-reload
        systemctl --user enable automationqa-runner
        systemctl --user start automationqa-runner

        echo "  [OK] Servicio systemd instalado y activo."
        echo "  Ver logs: journalctl --user -u automationqa-runner -f"
        ;;
    *)
        echo "  [ERROR] OS '$OS' no soportado por este instalador."
        echo "          Para Windows usa: runner\\service\\windows\\install-service.bat"
        exit 1
        ;;
esac
