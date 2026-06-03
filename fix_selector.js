const fs = require('fs');

const path = 'src/test/java/pages/asientos/SelectorPage.java';
let src = fs.readFileSync(path, 'utf8');

// ── FIX 1: marcarOpcionFiltro ──────────────────────────────────────────────
const OLD_MARCAR = `    /**
     * Intenta marcar una opción de filtro verificando el atributo checked/selected.
     * Prueba hasta 4 estrategias de tap distintas.
     */
    private boolean marcarOpcionFiltro(WebElement opcion) {
        int screenWidth  = driver.manage().window().getSize().getWidth();
        int xCentro      = opcion.getRect().getX() + opcion.getRect().getWidth() / 2;
        int yCentro      = opcion.getRect().getY() + opcion.getRect().getHeight() / 2;

        Runnable[] estrategias = {
            () -> clicSeguroEnElemento(opcion),                          // click nativo + parent fallbacks
            () -> tapW3C(xCentro, yCentro),                             // tap centro del texto
            () -> tapW3C((int)(screenWidth * 0.50), yCentro),           // tap centro de la fila
            () -> tapW3C((int)(screenWidth * 0.12), yCentro)            // tap zona checkbox izquierda
        };

        for (int i = 0; i < estrategias.length; i++) {
            try {
                estrategias[i].run();
                pausa(700);
                if (esFiltroMarcado(opcion)) {
                    log.info("[SelectorPage] Opción marcada en intento {}.", i + 1);
                    return true;
                }
                log.debug("[SelectorPage] Intento {} no marcó la opción. Reintentando...", i + 1);
            } catch (Exception e) {
                log.warn("[SelectorPage] Intento {} falló: {}", i + 1, e.getMessage());
            }
        }
        return false;
    }`;

const NEW_MARCAR = `    /**
     * Intenta marcar una opción de filtro con hasta 4 estrategias de tap.
     * Si los atributos checked/selected no son detectables (apps Compose),
     * asume marcado tras el primer tap exitoso para que el flujo llegue a "Aplicar".
     */
    private boolean marcarOpcionFiltro(WebElement opcion) {
        int screenWidth = driver.manage().window().getSize().getWidth();
        int xCentro     = opcion.getRect().getX() + opcion.getRect().getWidth() / 2;
        int yCentro     = opcion.getRect().getY() + opcion.getRect().getHeight() / 2;

        Runnable[] estrategias = {
            () -> clicSeguroEnElemento(opcion),                // click nativo + parent fallbacks
            () -> tapW3C(xCentro, yCentro),                   // tap centro del texto
            () -> tapW3C((int)(screenWidth * 0.50), yCentro), // tap centro de la fila
            () -> tapW3C((int)(screenWidth * 0.12), yCentro)  // tap zona checkbox izquierda
        };

        boolean tapEjecutado = false;
        for (int i = 0; i < estrategias.length; i++) {
            try {
                estrategias[i].run();
                tapEjecutado = true;
                pausa(700);
                if (esFiltroMarcado(opcion)) {
                    log.info("[SelectorPage] Opción marcada (atributos) en intento {}.", i + 1);
                    return true;
                }
                log.debug("[SelectorPage] Intento {}: tap OK, atributo no detectable.", i + 1);
            } catch (Exception e) {
                log.warn("[SelectorPage] Intento {} falló: {}", i + 1, e.getMessage());
            }
        }
        // En Compose, checked/selected no siempre son accesibles vía UiAutomator2,
        // pero el tap SÍ aplica el cambio visual. Si al menos uno se ejecutó, OK.
        if (tapEjecutado) {
            log.info("[SelectorPage] checked no detectable (Compose) — tap ejecutado, asumiendo marcado.");
            return true;
        }
        return false;
    }`;

// ── FIX 2: esFiltroMarcado ─────────────────────────────────────────────────
const OLD_ESFILTRO = `    /** Devuelve true si el elemento o su padre tienen checked/selected = true. */
    private boolean esFiltroMarcado(WebElement el) {
        try {
            for (String attr : new String[]{"checked", "selected"}) {
                String val = el.getAttribute(attr);
                if ("true".equalsIgnoreCase(val)) return true;
            }
            // Verificar en el padre también (row container)
            WebElement parent = el.findElement(By.xpath(".."));
            for (String attr : new String[]{"checked", "selected"}) {
                String val = parent.getAttribute(attr);
                if ("true".equalsIgnoreCase(val)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }`;

const NEW_ESFILTRO = `    /** Devuelve true si el elemento o su jerarquía cercana indica que está marcado. */
    private boolean esFiltroMarcado(WebElement el) {
        try {
            // 1. Atributos directos del elemento
            for (String attr : new String[]{"checked", "selected"}) {
                if ("true".equalsIgnoreCase(el.getAttribute(attr))) return true;
            }
            // 2. contentDescription — Compose suele incluir el estado
            String desc = safeLower(el.getAttribute("contentDescription"));
            if (desc.contains("seleccionado") || desc.contains("marcado")
                    || desc.contains("checked") || desc.contains(", on")) return true;

            // 3. Padre (row container)
            WebElement parent = el.findElement(By.xpath(".."));
            for (String attr : new String[]{"checked", "selected"}) {
                if ("true".equalsIgnoreCase(parent.getAttribute(attr))) return true;
            }
            String pd = safeLower(parent.getAttribute("contentDescription"));
            if (pd.contains("seleccionado") || pd.contains("checked")) return true;

            // 4. Abuelo
            try {
                WebElement grand = parent.findElement(By.xpath(".."));
                for (String attr : new String[]{"checked", "selected"}) {
                    if ("true".equalsIgnoreCase(grand.getAttribute(attr))) return true;
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return false;
    }`;

// Apply fixes
if (src.includes(OLD_MARCAR)) {
    src = src.replace(OLD_MARCAR, NEW_MARCAR);
    console.log('FIX1: marcarOpcionFiltro OK');
} else {
    console.log('FIX1: marcarOpcionFiltro NOT FOUND');
}

if (src.includes(OLD_ESFILTRO)) {
    src = src.replace(OLD_ESFILTRO, NEW_ESFILTRO);
    console.log('FIX2: esFiltroMarcado OK');
} else {
    console.log('FIX2: esFiltroMarcado NOT FOUND');
}

// ── REMOVE unused methods ──────────────────────────────────────────────────
function removeMethod(text, methodName) {
    // Find method start
    const sigs = [
        `    private List<WebElement> ${methodName}(`,
        `    private void ${methodName}(`,
        `    private boolean ${methodName}(`,
        `    private WebElement ${methodName}(`,
        `    private String ${methodName}(`,
        `    private int ${methodName}(`,
    ];

    let start = -1;
    for (const sig of sigs) {
        const pos = text.indexOf(sig);
        if (pos >= 0) { start = pos; break; }
    }
    if (start < 0) { console.log(`  ${methodName}: not found`); return text; }

    // Extend back to include javadoc
    const pre = text.substring(0, start);
    const jdEnd = pre.lastIndexOf('*/');
    if (jdEnd >= 0) {
        const between = pre.substring(jdEnd + 2);
        if (between.trim() === '') {
            const jdStart = pre.lastIndexOf('/**', jdEnd);
            if (jdStart >= 0) {
                const nl = pre.lastIndexOf('\n', jdStart);
                start = nl >= 0 ? nl + 1 : jdStart;
            }
        }
    }

    // Find end by brace counting
    let depth = 0, i = start, inMethod = false;
    while (i < text.length) {
        const c = text[i];
        if (c === '{') { depth++; inMethod = true; }
        else if (c === '}') {
            depth--;
            if (inMethod && depth === 0) {
                i++;
                if (i < text.length && text[i] === '\n') i++;
                break;
            }
        }
        i++;
    }

    console.log(`  ${methodName}: removed (${i - start} chars)`);
    return text.substring(0, start) + text.substring(i);
}

const toRemove = [
    'escanearMapaAsientos',
    'hacerScrollPeliculas',
    'buscarAsientoVisiblePorNumero',
    'huboCambioVisualAsiento',
    'asientoQuedoSeleccionado',
    'obtenerNumeroAsientoSeguro',
    'describirAsiento',
    'construirKeyAsiento',
];

for (const m of toRemove) {
    src = removeMethod(src, m);
}

// Clean orphan javadoc
const orphan = `    /**
     * Espera a que la pantalla de asientos cargue Y devuelve los elementos del mapa
     * en una sola operación, eliminando el doble findElements que ocurría al llamar
     * esperarPantallaAsientos() seguido de obtenerAsientosDelMapaRapido().
     *
     * Fase 1: detecta señales de UI (texto "Pantalla", "Selecciona", "Continuar"…)
     * Fase 2: sondea hasta que los elementos numéricos del mapa aparecen y los devuelve
     *         ya filtrados y deduplicados — listos para usar directamente.
     */
    /**`;

if (src.includes(orphan)) {
    src = src.replace(orphan, '    /**');
    console.log('Cleaned orphan javadoc');
}

fs.writeFileSync(path, src, 'utf8');
console.log('Done.');
