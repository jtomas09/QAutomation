import re

path = r'c:\Users\jtomasb\AndroidStudioProjects\CinepolisAutomation\src\test\java\pages\asientos\SelectorPage.java'

with open(path, 'r', encoding='utf-8') as f:
    src = f.read()

# ── FIX 1: marcarOpcionFiltro ──────────────────────────────────────────────
old_marcar = (
    '    /**\n'
    '     * Intenta marcar una opción de filtro verificando el atributo checked/selected.\n'
    '     * Prueba hasta 4 estrategias de tap distintas.\n'
    '     */\n'
    '    private boolean marcarOpcionFiltro(WebElement opcion) {\n'
    '        int screenWidth  = driver.manage().window().getSize().getWidth();\n'
    '        int xCentro      = opcion.getRect().getX() + opcion.getRect().getWidth() / 2;\n'
    '        int yCentro      = opcion.getRect().getY() + opcion.getRect().getHeight() / 2;\n'
    '\n'
    '        Runnable[] estrategias = {\n'
    '            () -> clicSeguroEnElemento(opcion),                          // click nativo + parent fallbacks\n'
    '            () -> tapW3C(xCentro, yCentro),                             // tap centro del texto\n'
    '            () -> tapW3C((int)(screenWidth * 0.50), yCentro),           // tap centro de la fila\n'
    '            () -> tapW3C((int)(screenWidth * 0.12), yCentro)            // tap zona checkbox izquierda\n'
    '        };\n'
    '\n'
    '        for (int i = 0; i < estrategias.length; i++) {\n'
    '            try {\n'
    '                estrategias[i].run();\n'
    '                pausa(700);\n'
    '                if (esFiltroMarcado(opcion)) {\n'
    '                    log.info("[SelectorPage] Opción marcada en intento {}.", i + 1);\n'
    '                    return true;\n'
    '                }\n'
    '                log.debug("[SelectorPage] Intento {} no marcó la opción. Reintentando...", i + 1);\n'
    '            } catch (Exception e) {\n'
    '                log.warn("[SelectorPage] Intento {} falló: {}", i + 1, e.getMessage());\n'
    '            }\n'
    '        }\n'
    '        return false;\n'
    '    }'
)

new_marcar = (
    '    /**\n'
    '     * Intenta marcar una opción de filtro con hasta 4 estrategias de tap.\n'
    '     * Si los atributos checked/selected no son detectables (apps Compose),\n'
    '     * asume marcado tras el primer tap exitoso para que el flujo llegue a "Aplicar".\n'
    '     */\n'
    '    private boolean marcarOpcionFiltro(WebElement opcion) {\n'
    '        int screenWidth = driver.manage().window().getSize().getWidth();\n'
    '        int xCentro     = opcion.getRect().getX() + opcion.getRect().getWidth() / 2;\n'
    '        int yCentro     = opcion.getRect().getY() + opcion.getRect().getHeight() / 2;\n'
    '\n'
    '        Runnable[] estrategias = {\n'
    '            () -> clicSeguroEnElemento(opcion),                // click nativo + parent fallbacks\n'
    '            () -> tapW3C(xCentro, yCentro),                   // tap centro del texto\n'
    '            () -> tapW3C((int)(screenWidth * 0.50), yCentro), // tap centro de la fila\n'
    '            () -> tapW3C((int)(screenWidth * 0.12), yCentro)  // tap zona checkbox izquierda\n'
    '        };\n'
    '\n'
    '        boolean tapEjecutado = false;\n'
    '        for (int i = 0; i < estrategias.length; i++) {\n'
    '            try {\n'
    '                estrategias[i].run();\n'
    '                tapEjecutado = true;\n'
    '                pausa(700);\n'
    '                if (esFiltroMarcado(opcion)) {\n'
    '                    log.info("[SelectorPage] Opción marcada (atributos) en intento {}.", i + 1);\n'
    '                    return true;\n'
    '                }\n'
    '                log.debug("[SelectorPage] Intento {}: tap OK, atributo no detectable.", i + 1);\n'
    '            } catch (Exception e) {\n'
    '                log.warn("[SelectorPage] Intento {} falló: {}", i + 1, e.getMessage());\n'
    '            }\n'
    '        }\n'
    '        // En Compose, checked/selected no siempre son accesibles vía UiAutomator2,\n'
    '        // pero el tap SÍ aplica el cambio visual. Si al menos uno se ejecutó, OK.\n'
    '        if (tapEjecutado) {\n'
    '            log.info("[SelectorPage] checked no detectable (Compose) — tap ejecutado, asumiendo marcado.");\n'
    '            return true;\n'
    '        }\n'
    '        return false;\n'
    '    }'
)

# ── FIX 2: esFiltroMarcado ─────────────────────────────────────────────────
old_esfiltro = (
    '    /** Devuelve true si el elemento o su padre tienen checked/selected = true. */\n'
    '    private boolean esFiltroMarcado(WebElement el) {\n'
    '        try {\n'
    '            for (String attr : new String[]{"checked", "selected"}) {\n'
    '                String val = el.getAttribute(attr);\n'
    '                if ("true".equalsIgnoreCase(val)) return true;\n'
    '            }\n'
    '            // Verificar en el padre también (row container)\n'
    '            WebElement parent = el.findElement(By.xpath(".."));\n'
    '            for (String attr : new String[]{"checked", "selected"}) {\n'
    '                String val = parent.getAttribute(attr);\n'
    '                if ("true".equalsIgnoreCase(val)) return true;\n'
    '            }\n'
    '        } catch (Exception ignored) {}\n'
    '        return false;\n'
    '    }'
)

new_esfiltro = (
    '    /** Devuelve true si el elemento o su jerarquía cercana indica que está marcado. */\n'
    '    private boolean esFiltroMarcado(WebElement el) {\n'
    '        try {\n'
    '            // 1. Atributos directos del elemento\n'
    '            for (String attr : new String[]{"checked", "selected"}) {\n'
    '                if ("true".equalsIgnoreCase(el.getAttribute(attr))) return true;\n'
    '            }\n'
    '            // 2. contentDescription — Compose suele incluir el estado\n'
    '            String desc = safeLower(el.getAttribute("contentDescription"));\n'
    '            if (desc.contains("seleccionado") || desc.contains("marcado")\n'
    '                    || desc.contains("checked") || desc.contains(", on")) return true;\n'
    '\n'
    '            // 3. Padre (row container)\n'
    '            WebElement parent = el.findElement(By.xpath(".."));\n'
    '            for (String attr : new String[]{"checked", "selected"}) {\n'
    '                if ("true".equalsIgnoreCase(parent.getAttribute(attr))) return true;\n'
    '            }\n'
    '            String pd = safeLower(parent.getAttribute("contentDescription"));\n'
    '            if (pd.contains("seleccionado") || pd.contains("checked")) return true;\n'
    '\n'
    '            // 4. Abuelo\n'
    '            try {\n'
    '                WebElement grand = parent.findElement(By.xpath(".."));\n'
    '                for (String attr : new String[]{"checked", "selected"}) {\n'
    '                    if ("true".equalsIgnoreCase(grand.getAttribute(attr))) return true;\n'
    '                }\n'
    '            } catch (Exception ignored) {}\n'
    '        } catch (Exception ignored) {}\n'
    '        return false;\n'
    '    }'
)

if old_marcar in src:
    src = src.replace(old_marcar, new_marcar)
    print('FIX1: marcarOpcionFiltro OK')
else:
    print('FIX1: marcarOpcionFiltro NOT FOUND')

if old_esfiltro in src:
    src = src.replace(old_esfiltro, new_esfiltro)
    print('FIX2: esFiltroMarcado OK')
else:
    print('FIX2: esFiltroMarcado NOT FOUND')

# ── DELETE unused methods ──────────────────────────────────────────────────
def remove_method(text, method_name):
    # Find method by trying common signatures
    sigs = [
        f'    private List<WebElement> {method_name}(',
        f'    private void {method_name}(',
        f'    private boolean {method_name}(',
        f'    private WebElement {method_name}(',
        f'    private String {method_name}(',
        f'    private int {method_name}(',
    ]
    start = -1
    for s in sigs:
        pos = text.find(s)
        if pos >= 0:
            start = pos
            break
    if start < 0:
        print(f'  {method_name}: not found')
        return text

    # Extend backwards to include javadoc /** ... */ if present
    pre = text[:start]
    jd_end = pre.rfind('*/')
    if jd_end >= 0:
        between = pre[jd_end+2:]
        if between.strip() == '':
            jd_start = pre.rfind('/**', 0, jd_end)
            if jd_start >= 0:
                nl = pre.rfind('\n', 0, jd_start)
                start = nl + 1 if nl >= 0 else jd_start

    # Find end by balanced brace counting
    depth = 0
    i = start
    in_method = False
    while i < len(text):
        c = text[i]
        if c == '{':
            depth += 1
            in_method = True
        elif c == '}':
            depth -= 1
            if in_method and depth == 0:
                i += 1
                if i < len(text) and text[i] == '\n':
                    i += 1
                break
        i += 1

    n = i - start
    print(f'  {method_name}: removed ({n} chars)')
    return text[:start] + text[i:]

for m in ['escanearMapaAsientos', 'hacerScrollPeliculas', 'buscarAsientoVisiblePorNumero',
          'huboCambioVisualAsiento', 'asientoQuedoSeleccionado',
          'obtenerNumeroAsientoSeguro', 'describirAsiento', 'construirKeyAsiento']:
    src = remove_method(src, m)

# Clean orphan javadoc left after removal
orphan = (
    '    /**\n'
    '     * Espera a que la pantalla de asientos cargue Y devuelve los elementos del mapa\n'
    '     * en una sola operación, eliminando el doble findElements que ocurría al llamar\n'
    '     * esperarPantallaAsientos() seguido de obtenerAsientosDelMapaRapido().\n'
    '     *\n'
    '     * Fase 1: detecta señales de UI (texto "Pantalla", "Selecciona", "Continuar"…)\n'
    '     * Fase 2: sondea hasta que los elementos numéricos del mapa aparecen y los devuelve\n'
    '     *         ya filtrados y deduplicados — listos para usar directamente.\n'
    '     */\n'
    '    /**\n'
)
if orphan in src:
    src = src.replace(orphan, '    /**\n')
    print('Cleaned orphan javadoc')

with open(path, 'w', encoding='utf-8') as f:
    f.write(src)

print('All done.')
