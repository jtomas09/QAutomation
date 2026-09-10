package pages.ios;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.common.CinemasHelper;

import java.time.Duration;
import java.util.List;

/**
 * Responsabilidad iOS: PromosGuard, ClubGuard, ZoneGuard/popup de zona y MainNav
 * relacionado con selección de cine (TAREA 32 — migración real, sobre la fachada de
 * TAREA 30/31).
 *
 * ALCANCE DE LA MIGRACIÓN (ver reporte de TAREA 32 para el detalle completo del
 * análisis de dependencias, Fases 1-2):
 *
 *  MIGRADO (implementación real, copiada verbatim de {@link CinemasHelper}, mismo
 *  orden/loops/límites/sleeps/waits/mensajes/manejo de excepciones — ver métodos
 *  privados de esta clase):
 *    - dismissPromosGuard()/dismissPromosGuard(where) → dismissTransientPromosGuardImpl
 *    - dismissClubWhenAlreadyVisible, isMarioPromoVisible, dismissMarioPromoIfPresent,
 *      marioPromoLocator, tryGenericOverlayDismiss, esperarMainNavRapido, smartWait,
 *      safeSleep, findInstant/tapInstant (variante By) — todos exclusivos de este
 *      flujo, sin otros consumidores (confirmado por grep en TAREA 32).
 *    - Caché por-ejecución (clubClosedThisRun/noPromosThisRun) — copia INDEPENDIENTE
 *      de la de {@link CinemasHelper} (ver nota en resetRunCache() más abajo).
 *
 *  NO MIGRADO — se sigue delegando en la MISMA instancia de {@link CinemasHelper}
 *  (documentado explícitamente por qué, no es un olvido):
 *    - isClubLoginVisible()/dismissClubLoginIfPresent(): PÚBLICOS y usados también por
 *      ensureCinemaSelectedFromAlimentos()/goToAlimentosTab() (Android + iOS, fuera de
 *      alcance de esta tarea) — duplicarlos arriesgaría divergencia de un detector
 *      reutilizado por otro flujo real.
 *    - isMainNavVisible()/isLocationChangePopupVisible()/dismissLocationChangePopupIfPresent():
 *      sus locators (TAB_CARTELERA/TAB_HORARIOS/TAB_ALIMENTOS/TAB_ALIMENTOS_ALT,
 *      POPUP_ZONA_DETECTION/BTN_NO_CAMBIAR) también son usados por la navegación a
 *      Alimentos — TAREA 32 amplió su visibilidad a public en CinemasHelper.java (CERO
 *      cambio de comportamiento, ver ese archivo) en vez de duplicar los locators.
 *    - dismissLocationPopupIfPresent(): usado también por
 *      tests.Argentina/España/Chile.NoAfectacion* — genuinamente compartido entre
 *      países y plataformas, no se toca.
 *    - ensureMexicoCinemaSelected()/isMexicoCinemaPreSelected()/ensureCinemaSelectedFromAlimentos():
 *      entrelazados con la maquinaria compartida del selector de cines (usada también
 *      por Alimentos) — migrarlos de forma segura excede el alcance de TAREA 32.
 *
 *  LIMITACIÓN CONOCIDA (documentada, no resuelta aquí): {@code BaseTest.beforeEach()/
 *  afterEach()} sigue invocando {@code new CinemasHelper(driver).dismissTransientPromosGuard(...)}
 *  directamente, para TODAS las plataformas — esta clase (y flujos.ios.IOSAsientosFlow)
 *  NO están conectados a ese punto de entrada (fuera de alcance de TAREA 31/32, que
 *  solo tocaron tests.México.asientos.SeleccionAsientos). Por eso esta implementación
 *  migrada, aunque real, todavía no es la que se ejecuta en una corrida real de la
 *  suite — y por eso NO se eliminó la rama iOS de {@code CinemasHelper}: sigue siendo
 *  la única ruta live para iOS hoy. Ver Fase 5 del reporte de TAREA 32.
 */
public class IOSCinemasHelper extends IOSBasePage {

    private static final Logger log = LoggerFactory.getLogger(IOSCinemasHelper.class);

    private final CinemasHelper legacy;

    public IOSCinemasHelper(AppiumDriver driver) {
        super(driver);
        this.legacy = new CinemasHelper(driver);
    }

    // ── Caché por-ejecución (copia independiente de la de CinemasHelper — ver
    // Javadoc de clase, sección "LIMITACIÓN CONOCIDA") ──────────────────────────
    private static volatile boolean clubClosedThisRun = false;
    private static volatile boolean noPromosThisRun    = false;

    /**
     * Reinicia el caché por-ejecución PROPIO de esta clase (independiente del de
     * {@link CinemasHelper#resetRunCache()}, que sigue reseteándose desde
     * BaseTest.beforeAllSuite() sin cambios). Nadie invoca este método todavía en el
     * flujo real (ver LIMITACIÓN CONOCIDA) — queda listo para cuando BaseTest.java se
     * actualice para usar esta clase en iOS.
     */
    public static void resetRunCache() {
        clubClosedThisRun = false;
        noPromosThisRun   = false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PromosGuard — implementación real migrada (copia verbatim del algoritmo de
    // CinemasHelper.dismissTransientPromosGuardImpl(), incluidas sus llamadas a
    // isIOS() — se conservan intactas a propósito para minimizar el riesgo de una
    // transcripción distinta al original, aunque esta clase es exclusivamente iOS).
    // ═══════════════════════════════════════════════════════════════════════════

    /** PromosGuard — descarta Club/Mario/zona en cascada y espera a MainNav. */
    public void dismissPromosGuard(String where) {
        utils.PerfMetrics.measure("PromosGuard", () -> dismissTransientPromosGuardImpl(where));
    }

    /** Variante sin tag de origen. */
    public void dismissPromosGuard() {
        dismissPromosGuard("unknown");
    }

    private void dismissTransientPromosGuardImpl(String where) {
        log.info("[TRACE] Inicio PromosGuard (iOS) | hilo={} where={} hora={}",
                Thread.currentThread().getName(), where, System.currentTimeMillis());
        log.info("[IOSCinemasHelper][PromosGuard] ENTER where={}", where);
        long tTotal = System.currentTimeMillis();

        long msClub    = 0;
        long msZona    = 0;
        long msMainNav = 0;

        boolean clubAlreadyDismissed = isIOS() && clubClosedThisRun;
        if (clubAlreadyDismissed) {
            log.debug("[IOSCinemasHelper][PromosGuard] Club ya cerrado en una llamada previa de esta ejecución (cache) — skip total");
        }

        for (int pass = 1; pass <= 5; pass++) {
            boolean dismissed = false;

            // ── Club Cinépolis ────────────────────────────────────────────────────
            try {
                if (!clubAlreadyDismissed) {
                    long t0Club = System.currentTimeMillis();
                    boolean clubVisible = legacy.isClubLoginVisible();
                    if (clubVisible) {
                        log.info("[IOSCinemasHelper][PromosGuard] pass={} Club visible -> dismiss", pass);
                        dismissClubWhenAlreadyVisible(where + ":club");
                        dismissed = true;
                        clubAlreadyDismissed = true;
                        if (isIOS()) clubClosedThisRun = true;

                        long tNav0 = System.currentTimeMillis();
                        if (esperarMainNavRapido(5000)) {
                            msClub    += System.currentTimeMillis() - t0Club;
                            msMainNav += System.currentTimeMillis() - tNav0;
                            log.info("[PromosGuard] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | EXIT pass={} where={}",
                                    msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, pass, where);
                            log.info("[TRACE] Fin PromosGuard (via MainNav tras Club) | hilo={} where={} duracionMs={}",
                                    Thread.currentThread().getName(), where, System.currentTimeMillis() - tTotal);
                            return;
                        }
                        log.debug("[IOSCinemasHelper][PromosGuard] Main nav no visible en 5s tras cerrar Club ({}ms)",
                                System.currentTimeMillis() - tNav0);
                        msMainNav += System.currentTimeMillis() - tNav0;
                    }
                    msClub += System.currentTimeMillis() - t0Club;
                } else {
                    log.debug("[IOSCinemasHelper][PromosGuard] pass={} Club ya resuelto — skip check", pass);
                }
            } catch (Exception e) {
                log.error("[IOSCinemasHelper][PromosGuard] Club guard error: {}", e.getMessage());
            }

            // ── Mario Promo ───────────────────────────────────────────────────────
            try {
                if (isIOS() && noPromosThisRun) {
                    log.debug("[IOSCinemasHelper][PromosGuard] Mario ya resuelto en esta ejecución (cache) — skip check");
                } else if (isMarioPromoVisible()) {
                    log.info("[IOSCinemasHelper][PromosGuard] pass={} Mario visible -> dismiss", pass);
                    dismissMarioPromoIfPresent();
                    dismissed = true;
                    if (isIOS()) noPromosThisRun = true;
                } else if (isIOS() && pass == 1) {
                    noPromosThisRun = true;
                }
            } catch (Exception e) {
                log.error("[IOSCinemasHelper][PromosGuard] Mario guard error: {}", e.getMessage());
            }

            // ── Popup zona/ubicación ──────────────────────────────────────────────
            try {
                long t0Zona = System.currentTimeMillis();
                if (legacy.isLocationChangePopupVisible()) {
                    log.info("[IOSCinemasHelper][PromosGuard] pass={} Zona visible -> dismiss", pass);
                    legacy.dismissLocationChangePopupIfPresent(where + ":zona");
                    dismissed = true;
                    safeSleep(700);
                }
                msZona += System.currentTimeMillis() - t0Zona;
            } catch (Exception e) {
                log.error("[IOSCinemasHelper][PromosGuard] Zona guard error: {}", e.getMessage());
            }

            // ── Salida por Main Nav visible ───────────────────────────────────────
            try {
                long t0Nav = System.currentTimeMillis();
                if (legacy.isMainNavVisible()) {
                    msMainNav += System.currentTimeMillis() - t0Nav;
                    log.info("[PERF][BeforeEach] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | EXIT pass={} where={}",
                            msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, pass, where);
                    log.info("[TRACE] Fin PromosGuard (MainNav visible) | hilo={} where={} duracionMs={}",
                            Thread.currentThread().getName(), where, System.currentTimeMillis() - tTotal);
                    return;
                }
                msMainNav += System.currentTimeMillis() - t0Nav;
            } catch (Exception e) {
                log.warn("[IOSCinemasHelper][PromosGuard] isMainNavVisible error (pass={}): {}", pass, e.getMessage());
            }

            boolean genericDismissedSomething = false;
            if (!dismissed) {
                log.debug("[IOSCinemasHelper][PromosGuard] pass={} nada cerrado, prueba dismiss genérico", pass);
                genericDismissedSomething = tryGenericOverlayDismiss();
            }

            // PERF (TAREA 35 — evidencia lógica, sin cambiar timeout/intervalo): si NADA
            // cambió la UI desde el chequeo "Salida por Main Nav visible" de arriba (ni
            // Club/Mario/Zona cerraron algo, ni tryGenericOverlayDismiss() tocó nada),
            // isMainNavVisible() YA es sabido false — se acaba de confirmar microsegundos
            // antes, sin ninguna acción de por medio que pudiera cambiar el resultado. Se
            // evita ÚNICAMENTE esa primera evaluación garantizada-redundante; la ventana
            // total (500ms) y el intervalo de poll (100ms) quedan idénticos a smartWait()
            // — ver smartWaitSkippingKnownFalseFirstCheck() para la prueba de equivalencia.
            // Cuando SÍ hubo un cambio (dismissed=true o genericDismissedSomething=true),
            // se usa smartWait() completo, sin modificar, exactamente como antes.
            if (dismissed || genericDismissedSomething) {
                smartWait(() -> legacy.isMainNavVisible(), 500, 100);
            } else {
                smartWaitSkippingKnownFalseFirstCheck(() -> legacy.isMainNavVisible(), 500, 100);
            }
        }

        log.warn("[PERF][BeforeEach] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | Max passes where={}",
                msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, where);
        log.info("[IOSCinemasHelper][PromosGuard] EXIT where={}", where);
        log.info("[TRACE] Fin PromosGuard (5 passes agotados, MainNav NUNCA detectado) | hilo={} where={} duracionMs={}",
                Thread.currentThread().getName(), where, System.currentTimeMillis() - tTotal);
    }

    /** ClubGuard — ya sabemos que Club está visible; cierra sin el chequeo previo redundante. */
    private void dismissClubWhenAlreadyVisible(String where) {
        utils.PerfMetrics.startPhase("ClubGuard");
        try {
            log.info("[IOSCinemasHelper][ClubGuard] ENTER where={}", where);
            long t0 = System.currentTimeMillis();

            boolean closedReturn = legacy.dismissClubLoginIfPresent();
            boolean stillVisible = !closedReturn && legacy.isClubLoginVisible();
            log.info("[IOSCinemasHelper][ClubGuard] closedReturn={} stillVisible={} tiempo={}ms",
                    closedReturn, stillVisible, System.currentTimeMillis() - t0);
            utils.PerfMetrics.attempt("ClubGuard", 1, where, System.currentTimeMillis() - t0,
                    closedReturn ? "OK" : (stillVisible ? "FAIL" : "OK"));

            if (stillVisible) {
                log.warn("[IOSCinemasHelper][ClubGuard] STILL visible -> last resort navigate.back()");
                try { driver.navigate().back(); safeSleep(700); } catch (Exception ignored) {}
            }

            log.info("[IOSCinemasHelper][ClubGuard] EXIT where={}", where);
        } catch (Exception e) {
            log.error("[IOSCinemasHelper][ClubGuard] ERROR where={} msg={}", where, e.getMessage());
        } finally {
            utils.PerfMetrics.endPhase("ClubGuard");
        }
    }

    /** Locator del CTA "CONSULTA CARTELERA" de la promo Mario. */
    private By marioPromoLocator() {
        return isIOS()
                ? AppiumBy.iOSNsPredicateString("label == 'CONSULTA CARTELERA' OR label CONTAINS 'CONSULTA CARTELERA' " +
                        "OR value == 'CONSULTA CARTELERA' OR value CONTAINS 'CONSULTA CARTELERA'")
                : By.xpath("//*[normalize-space(@text)='CONSULTA CARTELERA' or contains(@text,'CONSULTA CARTELERA')]");
    }

    private boolean isMarioPromoVisible() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            boolean found = !driver.findElements(marioPromoLocator()).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return found;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return false;
        }
    }

    private void dismissMarioPromoIfPresent() {
        try {
            if (!isMarioPromoVisible()) {
                return;
            }

            log.info("[IOSCinemasHelper] Promo Mario detectada. Cerrando...");

            List<WebElement> ctas = driver.findElements(marioPromoLocator());
            if (!ctas.isEmpty()) {
                ctas.get(0).click();
                smartWait(() -> !isMarioPromoVisible(), 800, 100);
                return;
            }

            driver.navigate().back();
            smartWait(() -> !isMarioPromoVisible(), 600, 100);

        } catch (Exception e) {
            log.warn("[IOSCinemasHelper] No se pudo cerrar promo Mario (safe ignore)");
        }
    }

    /**
     * Intenta cerrar cualquier overlay desconocido usando patrones comunes de dismiss.
     *
     * TAREA 35: cambia de {@code void} a {@code boolean} (único cambio de firma de esta
     * tarea) para que el llamador sepa si de verdad se tocó algo — antes ese dato se
     * descartaba. No cambia NINGÚN comportamiento propio del método (mismos locators,
     * mismo orden, mismo {@code safeSleep(500)}); solo expone su resultado, que ya
     * calculaba internamente. Único call site: {@code dismissTransientPromosGuardImpl()}.
     */
    private boolean tryGenericOverlayDismiss() {
        By[] dismissLocatorsAndroid = {
            By.xpath("//*[@content-desc='Close' or @content-desc='Cerrar' or @content-desc='close']"),
            By.xpath("//android.widget.Button[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.TextView[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.ImageButton[@content-desc='Atrás' or @content-desc='Atras' or @content-desc='Navigate up']"),
        };
        By[] dismissLocatorsIOS = {
            AppiumBy.iOSNsPredicateString("name == 'Close' OR name == 'Cerrar' OR name == 'close'"),
            AppiumBy.iOSNsPredicateString("label == 'Cerrar' OR label == 'No gracias' OR label == 'Omitir' OR label == 'Saltar'"),
            AppiumBy.iOSNsPredicateString("name == 'Atrás' OR name == 'Atras' OR name == 'Navigate up' OR name == 'Back'"),
        };
        By[] dismissLocators = isIOS() ? dismissLocatorsIOS : dismissLocatorsAndroid;
        for (By loc : dismissLocators) {
            if (tapInstant(loc)) {
                log.info("[IOSCinemasHelper][PromosGuard] Overlay genérico cerrado con: {}", loc);
                safeSleep(500);
                return true;
            }
        }
        return false;
    }

    /** Polling rápido de Main Nav tras cerrar Club (evita 4×10s de isMainNavVisible()). */
    private boolean esperarMainNavRapido(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() < end) {
            if (legacy.isMainNavVisible()) {
                log.info("[IOSCinemasHelper] Main nav detectado en {} ms",
                        System.currentTimeMillis() - t0);
                return true;
            }
            safeSleep(200);
        }
        return legacy.isMainNavVisible();
    }

    // ── Helpers genéricos (copia verbatim de los privados equivalentes en
    // CinemasHelper — solo la variante By, la única que este archivo necesita) ──

    private WebElement findInstant(By locator) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            List<WebElement> els = driver.findElements(locator);
            return (els == null || els.isEmpty()) ? null : els.get(0);
        } catch (Exception e) {
            return null;
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    private boolean tapInstant(By locator) {
        WebElement el = findInstant(locator);
        if (el == null) return false;
        try { if (!el.isDisplayed()) return false; } catch (Exception ignored) {}
        try { el.click(); return true; } catch (Exception ignored) {}
        try { legacy.tapCenter(el); return true; } catch (Exception ignored) {}
        return false;
    }

    private static void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private boolean smartWait(java.util.function.BooleanSupplier condition, long maxMs, long pollMs) {
        long end = System.currentTimeMillis() + maxMs;
        boolean lastResult;
        do {
            lastResult = condition.getAsBoolean();
            if (lastResult) return true;
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) break;
            safeSleep(Math.min(pollMs, remaining));
        } while (System.currentTimeMillis() < end);
        return lastResult;
    }

    /**
     * TAREA 35 — idéntico a {@link #smartWait} EXCEPTO que omite su primera evaluación
     * (la de t≈0), porque el único llamador (dismissTransientPromosGuardImpl) ya la
     * demostró {@code false} microsegundos antes, sin ninguna acción de UI en medio.
     * A partir de ahí replica exactamente el mismo cuerpo (mismo remaining/sleep/check/
     * while) — mismos checks en t≈100/200/300/400 para (500,100), misma ventana total
     * (maxMs), mismo intervalo de poll (pollMs), mismo valor de retorno (el último
     * chequeo antes de que se agote la ventana). Usar SOLO cuando esa precondición sea
     * demostrable — no es un smartWait "más rápido" genérico.
     */
    private boolean smartWaitSkippingKnownFalseFirstCheck(
            java.util.function.BooleanSupplier condition, long maxMs, long pollMs) {
        long end = System.currentTimeMillis() + maxMs;
        boolean lastResult = false;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) return false;
        safeSleep(Math.min(pollMs, remaining));
        while (System.currentTimeMillis() < end) {
            lastResult = condition.getAsBoolean();
            if (lastResult) return true;
            remaining = end - System.currentTimeMillis();
            if (remaining <= 0) break;
            safeSleep(Math.min(pollMs, remaining));
        }
        return lastResult;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NO MIGRADO — delegación intencional en CinemasHelper (ver Javadoc de clase)
    // ═══════════════════════════════════════════════════════════════════════════

    /** ClubGuard — cierra el login de Club Cinépolis si está visible ahora mismo.
     *  Compartido con ensureCinemaSelectedFromAlimentos() (Android + iOS) — no se duplica. */
    public boolean dismissClubLoginIfPresent() {
        return legacy.dismissClubLoginIfPresent();
    }

    /** ClubGuard — true si el login de Club Cinépolis está visible en este instante. */
    public boolean isClubLoginVisible() {
        return legacy.isClubLoginVisible();
    }

    /** Popup de cambio de zona/ciudad — variante pública compartida con
     *  tests.Argentina/España/Chile.NoAfectacion*; no se duplica. */
    public void dismissLocationPopupIfPresent() {
        legacy.dismissLocationPopupIfPresent();
    }

    /** Selección de cine México (una vez por suite) — entrelazado con la maquinaria
     *  compartida del selector de cines (también usada por Alimentos); no se migra. */
    public void ensureMexicoCinemaSelected() {
        legacy.ensureMexicoCinemaSelected();
    }

    public boolean isMexicoCinemaPreSelected() {
        return legacy.isMexicoCinemaPreSelected();
    }

    /** Selección de cine para menús de Alimentos — fuera del alcance de TAREA 32. */
    public void ensureCinemaSelectedFromAlimentos(String targetCinema) {
        legacy.ensureCinemaSelectedFromAlimentos(targetCinema);
    }
}
