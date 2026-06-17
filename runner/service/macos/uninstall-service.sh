#!/bin/bash
# ============================================================
#  Automation QA — macOS LaunchAgent Uninstaller
# ============================================================

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

LABEL="com.automationqa.runner"
PLIST_PATH="$HOME/Library/LaunchAgents/$LABEL.plist"
INSTALL_DIR="$HOME/Library/Application Support/AutomationQA"
LOG_DIR="$HOME/Library/Logs/AutomationQA"

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║    Automation QA Runner — Desinstalador de Servicio     ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

echo -e "${YELLOW}[1/3] Deteniendo LaunchAgent...${NC}"
if launchctl list 2>/dev/null | grep -q "$LABEL"; then
    launchctl unload "$PLIST_PATH" 2>/dev/null
    echo -e "${GREEN}[OK]${NC}    Agente detenido."
else
    echo "      (El agente no estaba corriendo)"
fi

echo -e "${YELLOW}[2/3] Eliminando plist...${NC}"
if [ -f "$PLIST_PATH" ]; then
    rm -f "$PLIST_PATH"
    echo -e "${GREEN}[OK]${NC}    $PLIST_PATH eliminado."
else
    echo "      (Plist no encontrado)"
fi

echo -e "${YELLOW}[3/3] Limpiando archivos de instalacion...${NC}"
read -p "  Eliminar archivos en ~/Library/Application Support/AutomationQA? (s/N): " CONFIRM
if [[ "$CONFIRM" =~ ^[sS]$ ]]; then
    [ -d "$INSTALL_DIR" ] && rm -rf "$INSTALL_DIR"
    echo -e "${GREEN}[OK]${NC}    Directorio eliminado."
fi

read -p "  Eliminar logs en ~/Library/Logs/AutomationQA? (s/N): " CONFIRM_LOGS
if [[ "$CONFIRM_LOGS" =~ ^[sS]$ ]]; then
    [ -d "$LOG_DIR" ] && rm -rf "$LOG_DIR"
    echo -e "${GREEN}[OK]${NC}    Logs eliminados."
fi

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Desinstalacion completada."
echo "  El Runner ya no arrancara automaticamente."
echo "══════════════════════════════════════════════════════════"
echo ""
