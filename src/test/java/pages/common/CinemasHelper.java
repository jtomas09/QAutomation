package pages.common;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.interactions.Pause;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.qameta.allure.Allure;
import utils.TestSteps;

public class CinemasHelper extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(CinemasHelper.class);

    // ─── Cache por ejecución — SOLO iOS ──────────────────────────────────────────
    // "¿Este cambio afecta Android?": SÍ (cambia cuántas veces se verifica Club/Mario
    // dentro del mismo run) → implementación exclusiva iOS, gateada por isIOS() en
    // cada punto de uso. Android conserva su comportamiento de siempre: re-verifica
    // Club y Mario en cada llamada a dismissTransientPromosGuard(), sin cache.
    //
    // Se reinicia en BaseTest.beforeAllSuite() junto con MEXICO_CINEMA_CHECKED — es
    // cache POR EJECUCIÓN/SUITE, nunca permanente entre corridas distintas.
    private static volatile boolean iosClubClosedThisRun  = false;
    private static volatile boolean iosNoPromosThisRun    = false;

    /** Reinicia el cache por-ejecución de iOS. Llamar SOLO desde BaseTest.beforeAllSuite(). */
    public static void resetRunCache() {
        iosClubClosedThisRun = false;
        iosNoPromosThisRun   = false;
    }

    // ─── Helpers de visibilidad instantánea ──────────────────────────────────────
    //
    // PROBLEMA: firstOrNull→findElements usa implicitlyWait activo (10 s).
    //   isMainNavVisible() llamaba isVisibleNow×4 → si Nav no visible: 4×10 s=40 s.
    //   isClubLoginVisible() fallback → 10 s cuando Club ya se cerró.
    //
    // SOLUCIÓN: findInstant/isVisibleInstant → implicitlyWait=0 temporal.
    //   Elemento presente → respuesta inmediata.
    //   Elemento ausente  → 0 ms (antes: 10.000 ms).
    //   Los métodos originales (firstOrNull, isVisibleNow) no se modifican.

    /**
     * Busca el primer elemento coincidente SIN esperar (implicitlyWait=0 transitorio).
     * Retorna null inmediatamente si el elemento no está en el DOM actual.
     */
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

    private WebElement findInstant(PlatformLocator locator) {
        return findInstant(locator.resolve(isIOS()));
    }

    /** True si el elemento existe en el DOM actual y está visible (sin esperar). */
    private boolean isVisibleInstant(By locator) {
        try {
            WebElement el = findInstant(locator);
            return el != null && el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVisibleInstant(PlatformLocator locator) {
        return isVisibleInstant(locator.resolve(isIOS()));
    }

    /**
     * Toca un elemento solo si está INSTANTÁNEAMENTE visible (implicitlyWait=0 transitorio).
     * Idéntico a tapIfPresent() pero usa findInstant() en lugar de firstOrNull().
     * Retorna false inmediatamente si el elemento no está en el DOM actual.
     * Usar en tryGenericOverlayDismiss() para evitar 4×10s de espera cuando no hay overlay.
     */
    private boolean tapInstant(By locator) {
        WebElement el = findInstant(locator);
        if (el == null) return false;
        try { if (!el.isDisplayed()) return false; } catch (Exception ignored) {}
        try { el.click(); return true; } catch (Exception ignored) {}
        try { tapCenter(el); return true; } catch (Exception ignored) {}
        return false;
    }

    private boolean tapInstant(PlatformLocator locator) {
        return tapInstant(locator.resolve(isIOS()));
    }

    /**
     * Polling rápido de Main Nav tras cerrar Club.
     * Evita las 4×10s de isMainNavVisible() cuando la app aún está animando.
     * @param timeoutMs tiempo máximo de espera en ms (recomendado 5000)
     */
    private boolean esperarMainNavRapido(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() < end) {
            if (isMainNavVisible()) {
                log.info("[CinemasHelper] Main nav detectado en {} ms",
                        System.currentTimeMillis() - t0);
                return true;
            }
            safeSleep(200);
        }
        return isMainNavVisible();
    }

    // ==========================
    // ✅ TAB ALIMENTOS
    // ==========================
    private static final PlatformLocator TAB_ALIMENTOS = PlatformLocator.byExactText("Alimentos");

    // ✅ Detectores para evitar quedarnos en Películas (Cartelera/Horarios)
    private static final PlatformLocator TAB_CARTELERA = PlatformLocator.byExactText("Cartelera");
    private static final PlatformLocator TAB_HORARIOS  = PlatformLocator.byExactText("Horarios");
    // ✅ Tab 'Alimentos' como seleccionado (cuando el bottom nav expone selected/checked).
    // iOS: XCUITest también expone @selected en controles tipo tab — mismo atributo.
    private static final PlatformLocator TAB_ALIMENTOS_SELECTED = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Alimentos' and (@selected='true' or @checked='true')]" +
                    " | //android.widget.TextView[@text='Alimentos']/..[@selected='true' or @checked='true']"),
            By.xpath("//*[(@label='Alimentos' or @name='Alimentos' or @value='Alimentos') and @selected='true']"));


    // ==========================
    // ✅ GUARD: PANTALLA CLUB CINÉPOLIS (LOGIN)
    // ==========================
    // Lado iOS v\u00eda NSPredicate (AppiumBy.iOSNsPredicateString) en lugar de XPath \u2014 WDA lo
    // eval\u00faa contra el \u00e1rbol nativo sin serializar el pageSource completo a XML primero.
    // Ver nota de rendimiento en PlatformLocator.byExactText(). Android sin cambios.
    private static final PlatformLocator CLUB_LOGIN_TITLE = PlatformLocator.of(
            By.xpath("//*[contains(@text,'Inicia sesi\u00f3n') or contains(@text,'Inicia sesion')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS 'Inicia sesi\u00f3n' OR label CONTAINS 'Inicia sesion' " +
                    "OR value CONTAINS 'Inicia sesi\u00f3n' OR value CONTAINS 'Inicia sesion' " +
                    "OR name CONTAINS 'Inicia sesi\u00f3n' OR name CONTAINS 'Inicia sesion'"));
    private static final PlatformLocator CLUB_LOGIN_LOGO = PlatformLocator.of(
            By.xpath("//*[contains(@text,'CLUB') and (contains(@text,'cin\u00e9polis') or contains(@text,'cinepolis'))]"),
            AppiumBy.iOSNsPredicateString(
                    "(label CONTAINS 'CLUB' OR value CONTAINS 'CLUB' OR name CONTAINS 'CLUB') " +
                    "AND (label CONTAINS 'cin\u00e9polis' OR label CONTAINS 'cinepolis' " +
                    "OR value CONTAINS 'cin\u00e9polis' OR value CONTAINS 'cinepolis' " +
                    "OR name CONTAINS 'cin\u00e9polis' OR name CONTAINS 'cinepolis')"));
    // Flecha/back de la pantalla — orden de prioridad:
    // 1. ImageButton/ImageView con content-desc "Atrás/Navigate up" (nativo Android) /
    //    @name en iOS (XCUITest no distingue ImageButton/ImageView, se usa * genérico).
    // iOS v\u00eda NSPredicate \u2014 ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator CLUB_BACK_BUTTON_A11Y = PlatformLocator.of(
            By.xpath("//android.widget.ImageButton[contains(@content-desc,'Atr\u00e1s') or contains(@content-desc,'Atras') or contains(@content-desc,'Navigate up')]" +
                    " | //android.widget.ImageView[contains(@content-desc,'Atr\u00e1s') or contains(@content-desc,'Atras') or contains(@content-desc,'Navigate up')]"),
            AppiumBy.iOSNsPredicateString(
                    "name CONTAINS 'Atr\u00e1s' OR name CONTAINS 'Atras' OR name CONTAINS 'Navigate up' OR name CONTAINS 'Back'"));

    // 2. android.widget.Button con texto VACÍO (la flecha ← no tiene texto;
    //    "Inicia sesión" y "Crear tu Cuenta" SÍ tienen texto → quedan excluidos)
    //    UiAutomator2 es una API exclusiva de Android — no existe equivalente iOS,
    //    por eso permanece como By (no PlatformLocator) y solo se usa en la rama Android.
    private static final By CLUB_BACK_BUTTON_UIAUTO =
            AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.Button\").text(\"\").instance(0)");

    // 3. Fallback: primer Button sin texto/label por XPath.
    // NOTA-MIGRACION (bug pre-existente corregido): tapBackFromClubUI() usaba esta
    // constante (100% Android — android.widget.Button) como rama "iOS" del ternario
    // isIOS()?CLUB_BACK_BUTTON_XPATH:CLUB_BACK_BUTTON_UIAUTO — nunca podía resolver
    // en iOS. Se separa en variante Android (sin cambios) y variante iOS genúina.
    private static final By CLUB_BACK_BUTTON_XPATH =
            By.xpath("(//android.widget.Button[not(@text) or @text=''])[1]");
    // Justificación del locator posicional (no hay alternativa por accesibilidad):
    // la flecha "back" de esta pantalla es un ícono sin accessibility identifier
    // conocido (el equivalente Android tampoco tiene @text — CLUB_BACK_BUTTON_XPATH
    // usa el mismo criterio "primer botón sin texto"). Se filtra por @visible='true'
    // para no capturar botones ocultos que también cumplan "sin name/label".
    private static final By CLUB_BACK_BUTTON_XPATH_IOS =
            By.xpath("(//XCUIElementTypeButton[@visible='true' and (not(@name) or @name='' or not(@label) or @label='')])[1]");
    // Tab alterno por content-desc (bottom nav en algunos builds) / @name en iOS.
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator TAB_ALIMENTOS_ALT = PlatformLocator.of(
            By.xpath("//*[@content-desc='Alimentos' or @text='Alimentos']"),
            AppiumBy.iOSNsPredicateString("name == 'Alimentos' OR label == 'Alimentos' OR value == 'Alimentos'"));

    // ==========================
    // ✅ ICONO/CHIP REAL DE CINES EN HEADER (Compose)
    // ==========================
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator CINES_ICON_VIEW = PlatformLocator.of(
            By.xpath("//android.view.View[contains(@content-desc,'Selecciona uno o más cines') or contains(@content-desc,'cines') or contains(@content-desc,'Cines')]"),
            AppiumBy.iOSNsPredicateString(
                    "name CONTAINS 'Selecciona uno o más cines' OR name CONTAINS 'cines' OR name CONTAINS 'Cines'"));

    private static final PlatformLocator CINES_TEXT = PlatformLocator.byExactText("Cines");

    // Ancestro del label "Cines" — Android: android.view.View/ViewGroup. iOS: no hay
    // ViewGroup nativo; XCUIElementTypeOther es el contenedor genérico equivalente.
    private static final PlatformLocator CINES_TEXT_PARENT = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text=\"Cines\"]/ancestor::*[self::android.view.View or self::android.view.ViewGroup][1]"),
            By.xpath("//*[@label=\"Cines\" or @name=\"Cines\" or @value=\"Cines\"]/ancestor::XCUIElementTypeOther[1]"));

    // Pantalla "Elige un cine para tus alimentos"
    private static final PlatformLocator BTN_SELECCIONAR_UBICACION_TEXT = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]"),
            By.xpath("(//*[@label='Seleccionar ubicación' or @label='Seleccionar ubicacion' " +
                    "or @name='Seleccionar ubicación' or @name='Seleccionar ubicacion' " +
                    "or @value='Seleccionar ubicación' or @value='Seleccionar ubicacion'])[1]"));

    private static final PlatformLocator BTN_SELECCIONAR_UBICACION_BUTTON_NEAR_TEXT = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]/parent::*/android.widget.Button"),
            By.xpath("(//*[@label='Seleccionar ubicación' or @label='Seleccionar ubicacion'])[1]/parent::*/XCUIElementTypeButton"));

    private static final PlatformLocator BTN_SELECCIONAR_UBICACION_CLICKABLE_ANCESTOR = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]/ancestor::*[@clickable='true'][1]"),
            By.xpath("(//*[@label='Seleccionar ubicación' or @label='Seleccionar ubicacion'])[1]/ancestor::XCUIElementTypeOther[1]"));

    // ==========================
    // ✅ Selector de cines
    // ==========================
    private static final PlatformLocator TITLE_SELECCIONAR_CINES = PlatformLocator.byExactText("Seleccionar cines");

    // iOS vía NSPredicate — polling en cada iteración de waitSelectorScreenOrThrow()
    // (hasta 9s / 150ms por vuelta) — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator SEARCH_HINT = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Busca tu ciudad o tu cine' or @text='Escribe tu ciudad o cine' or @text='Escribe tu ciudad o cine']"),
            AppiumBy.iOSNsPredicateString(
                    "label == 'Busca tu ciudad o tu cine' OR label == 'Escribe tu ciudad o cine' " +
                    "OR value == 'Busca tu ciudad o tu cine' OR value == 'Escribe tu ciudad o cine' " +
                    "OR name == 'Busca tu ciudad o tu cine' OR name == 'Escribe tu ciudad o cine'"));

    private static final PlatformLocator SEARCH_PARENT_ROUNDED = PlatformLocator.byAccessibilityId("Rounded.Search");

    // Campo de búsqueda de cines. Android: EditText nativo. iOS: XCUITest no tiene ese
    // tipo — se acepta SearchField o TextField (misma decisión que SelectorPage.java,
    // sin verificar en dispositivo real).
    private static final PlatformLocator SEARCH_INPUT = PlatformLocator.of(
            By.xpath("//android.widget.EditText"),
            By.xpath("//XCUIElementTypeSearchField | //XCUIElementTypeTextField"));

    // NOTA-MIGRACION: posicional dentro del EditText (segundo hijo View) — sin ancla de
    // texto/accesibilidad. Equivalente iOS mejor-esfuerzo: el propio campo de búsqueda.
    private static final PlatformLocator SEARCH_INNER_VIEW = PlatformLocator.of(
            By.xpath("//android.widget.EditText/android.view.View[2]"),
            By.xpath("//XCUIElementTypeSearchField | //XCUIElementTypeTextField"));

    // TextView inside button (clickable=false) — used only to find the real Button
    private static final PlatformLocator BTN_APLICAR_SELECCION_LABEL = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']"),
            PlatformLocator.byExactText("Aplicar selección").ios());

    // Sibling Button right next to the label (Compose layout)
    private static final PlatformLocator BTN_APLICAR_SELECCION_SIBLING = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']"
                   + "/following-sibling::android.widget.Button"
                   + " | //android.widget.TextView[@text='Aplicar seleccion']"
                   + "/following-sibling::android.widget.Button"),
            By.xpath("//*[@label='Aplicar selección' or @label='Aplicar seleccion']"
                   + "/following-sibling::XCUIElementTypeButton"));

    // Clickable ancestor of the label (catches any wrapper View)
    private static final PlatformLocator BTN_APLICAR_SELECCION_ANCESTOR = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion'])"
                   + "/ancestor::*[@clickable='true'][1]"),
            By.xpath("(//*[@label='Aplicar selección' or @label='Aplicar seleccion'])"
                   + "/ancestor::XCUIElementTypeOther[1]"));

    // UiAutomator fallback — finds any clickable element whose text matches
    // (exclusivo de Android — no tiene equivalente iOS, sin cambios).
    private static final String UA_APLICAR_SELECCION =
            "new UiSelector().clickable(true).textContains(\"Aplicar\")";

    // kept for backward compat (label-only, used as last resort)
    private static final PlatformLocator BTN_APLICAR_SELECCION = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']"),
            PlatformLocator.byExactText("Aplicar selección").ios());

    // ==========================
    // ✅ DETECCIÓN CINE NO SELECCIONADO (México)
    // ==========================
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator CINES_SIN_SELECCION = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Selecciona uno o más cines']" +
            " | //android.widget.TextView[contains(@text,'Selecciona uno o m')]"),
            AppiumBy.iOSNsPredicateString(
                    "label == 'Selecciona uno o más cines' OR value == 'Selecciona uno o más cines' OR name == 'Selecciona uno o más cines' " +
                    "OR label CONTAINS 'Selecciona uno o m' OR value CONTAINS 'Selecciona uno o m' OR name CONTAINS 'Selecciona uno o m'"));

    // Ancestro clickable del chip (android.view.View clickable=true que envuelve el TextView) —
    // iOS: XCUIElementTypeOther es el contenedor genérico equivalente.
    private static final PlatformLocator CINES_CHIP_CLICKABLE = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='Selecciona uno o más cines']/ancestor::android.view.View[@clickable='true'][1]" +
            " | //android.widget.TextView[contains(@text,'Selecciona uno o m')]/ancestor::android.view.View[@clickable='true'][1]"),
            By.xpath("//*[@label='Selecciona uno o más cines' or contains(@label,'Selecciona uno o m')]/ancestor::XCUIElementTypeOther[1]"));

    private static final String MEXICO_CINEMA_CONFIG = "mexico-cinema.txt";

    // ==========================
    // ✅ POPUP CAMBIO DE ZONA/UBICACIÓN (aparece al inicio, no siempre)
    // ==========================
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator POPUP_ZONA_DETECTION = PlatformLocator.of(
            By.xpath("//*[contains(@text,'lejos de') or contains(@text,'cambiar tu cartelera') " +
            "or contains(@text,'cambiar la cartelera') or contains(@text,'Cambiar zona')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS 'lejos de' OR label CONTAINS 'cambiar tu cartelera' " +
                    "OR label CONTAINS 'cambiar la cartelera' OR label CONTAINS 'Cambiar zona' " +
                    "OR value CONTAINS 'lejos de' OR value CONTAINS 'cambiar tu cartelera' " +
                    "OR value CONTAINS 'cambiar la cartelera' OR value CONTAINS 'Cambiar zona'"));

    private static final PlatformLocator BTN_NO_CAMBIAR = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text='No cambiar']" +
            " | //android.widget.Button[@text='No cambiar']"),
            PlatformLocator.byExactText("No cambiar").ios());

    // ==========================
    // ✅ Alertas
    // ==========================
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator ALERT_CAMBIAR_CIUDAD_TITLE = PlatformLocator.of(
            By.xpath("//android.widget.TextView[contains(@text,'¿Quieres cambiar la ciudad') or contains(@text,'Quieres cambiar la ciudad')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS '¿Quieres cambiar la ciudad' OR label CONTAINS 'Quieres cambiar la ciudad' " +
                    "OR value CONTAINS '¿Quieres cambiar la ciudad' OR value CONTAINS 'Quieres cambiar la ciudad'"));

    private static final PlatformLocator ALERT_ACEPTAR_LAST = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[@text='Aceptar'])[last()]"),
            By.xpath("(//*[@label='Aceptar' or @name='Aceptar' or @value='Aceptar'])[last()]"));

    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final PlatformLocator ALERT_CAMBIAR_CINE_TITLE = PlatformLocator.of(
            By.xpath("//android.widget.TextView[contains(@text,'¿Estás seguro que deseas cambiar de cine') or contains(@text,'Estas seguro que deseas cambiar de cine')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS '¿Estás seguro que deseas cambiar de cine' OR label CONTAINS 'Estas seguro que deseas cambiar de cine' " +
                    "OR value CONTAINS '¿Estás seguro que deseas cambiar de cine' OR value CONTAINS 'Estas seguro que deseas cambiar de cine'"));

    private static final PlatformLocator BTN_SI_CAMBIAR_CINE_TEXT = PlatformLocator.of(
            By.xpath("//android.widget.TextView[contains(@text,'Sí, cambiar de cine') or contains(@text,'Si, cambiar de cine') or contains(@text,'cambiar de cine')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS 'Sí, cambiar de cine' OR label CONTAINS 'Si, cambiar de cine' OR label CONTAINS 'cambiar de cine' " +
                    "OR value CONTAINS 'Sí, cambiar de cine' OR value CONTAINS 'Si, cambiar de cine' OR value CONTAINS 'cambiar de cine'"));

    private static final PlatformLocator BTN_SI_CAMBIAR_CINE_CLICKABLE_ANCESTOR = PlatformLocator.of(
            By.xpath("(//android.widget.TextView[contains(@text,'Sí, cambiar de cine') or contains(@text,'Si, cambiar de cine')])[1]/ancestor::*[@clickable='true'][1]"),
            By.xpath("(//*[contains(@label,'Sí, cambiar de cine') or contains(@label,'Si, cambiar de cine')])[1]/ancestor::XCUIElementTypeOther[1]"));

    // NOTA-MIGRACION: posicional sin ancla de texto — Android preservado exacto;
    // equivalente iOS mejor-esfuerzo (último botón), sin verificar en dispositivo real.
    private static final PlatformLocator BTN_SI_CAMBIAR_CINE_BUTTON_ABS = PlatformLocator.of(
            By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.widget.Button"),
            PlatformLocator.lastActionButton().ios());

    private static final PlatformLocator BTN_MODAL_ANY_BUTTON = PlatformLocator.lastActionButton();

    public CinemasHelper(AppiumDriver driver) {
        super(driver);
    }

    /**
     * Toca "Aplicar selección" de forma robusta.
     * El TextView tiene clickable=false en Compose; el Button real es hermano o ancestro.
     */
    private boolean clickAplicarSeleccion() {
        // 1. Sibling Button (layout Compose más común)
        if (tapIfPresent(BTN_APLICAR_SELECCION_SIBLING)) {
            log.info("[CinemasHelper] Aplicar selección → sibling Button OK");
            return true;
        }
        // 2. Ancestro clickable del label
        if (tapIfPresent(BTN_APLICAR_SELECCION_ANCESTOR)) {
            log.info("[CinemasHelper] Aplicar selección → clickable ancestor OK");
            return true;
        }
        // 3. UiAutomator: clickable + texto "Aplicar" (Android only)
        if (!isIOS()) {
            try {
                WebElement btn = driver.findElement(AppiumBy.androidUIAutomator(UA_APLICAR_SELECCION));
                tapCenter(btn);
                log.info("[CinemasHelper] Aplicar selección → UiAutomator OK");
                return true;
            } catch (Exception ignored) {}
        }
        // 4. Tap por coordenadas del label (funciona aunque no sea clickable)
        try {
            WebElement label = firstOrNull(BTN_APLICAR_SELECCION_LABEL);
            if (label != null) {
                tapCenter(label);
                log.info("[CinemasHelper] Aplicar selección → tap label center OK");
                return true;
            }
        } catch (Exception ignored) {}
        // 5. Último recurso: el locator original
        log.warn("[CinemasHelper] Aplicar selección → fallback original locator");
        return tapIfPresent(BTN_APLICAR_SELECCION) || clickIfPresent(BTN_APLICAR_SELECCION);
    }

    public void ensureCinemaSelectedFromAlimentos(String targetCinema) {
        log.info("[TRACE] Inicio ensureCinemaSelectedFromAlimentos('{}') | hilo={} plataforma={} hora={}",
                targetCinema, Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", System.currentTimeMillis());
        log.info("[CinemasHelper] ensureCinemaSelectedFromAlimentos -> '{}'", targetCinema);
        long t0Total = System.currentTimeMillis();

        // Track cinema for PDF and Allure reporting
        TestSteps.setCinema(targetCinema);
        try { Allure.label("cinema", targetCinema); } catch (Exception ignored) {}

        // Navegar a Alimentos primero (desde cualquier pantalla) para poder leer el cine actual
        goToAlimentosTab();

        // Validar si el cine ya está correctamente seleccionado antes de abrir el selector.
        // isVisibleInstant(wait=0): evita 10s de espera cuando el chip "Selecciona uno o más cines"
        // NO está presente (caso normal en que ya hay un cine seleccionado).
        boolean cinemaSelected = !isVisibleInstant(CINES_SIN_SELECCION);
        if (cinemaSelected) {
            // Intento 1: leer el nombre del cine del chip (ahora con implicitlyWait=0 interno)
            String currentCinema = getCurrentCinemaName();
            if (currentCinema != null && !currentCinema.isBlank()) {
                log.info("[CinemasHelper] Cine actual detectado: '{}'", currentCinema);
                if (cinemaMatches(currentCinema, targetCinema)) {
                    log.info("[CinemasHelper] El cine ya coincide con '{}' — continuando flujo. | Total: {}ms",
                            targetCinema, System.currentTimeMillis() - t0Total);
                    log.info("[TRACE] Fin ensureCinemaSelectedFromAlimentos (cine ya coincidía) | duracionMs={}",
                            System.currentTimeMillis() - t0Total);
                    return;
                }
                log.info("[CinemasHelper] Cambiando cine de '{}' a '{}'", currentCinema, targetCinema);
            } else {
                // Intento 2: verificar si el texto del cine objetivo ya es visible en el chip.
                // isVisibleInstant(wait=0): evita 10s de espera adicional cuando el nombre no está
                By xpathTarget = isIOS()
                        ? By.xpath("//*[contains(@label,'" + escapeXpath(targetCinema) + "') or contains(@name,'"
                                + escapeXpath(targetCinema) + "') or contains(@value,'" + escapeXpath(targetCinema) + "')]")
                        : By.xpath("//android.widget.TextView[contains(@text,'" + escapeXpath(targetCinema) + "')]"
                                + " | //android.view.View[contains(@content-desc,'" + escapeXpath(targetCinema) + "')]");
                if (isVisibleInstant(xpathTarget)) {
                    log.info("[CinemasHelper] Cine '{}' ya visible en pantalla — continuando flujo.", targetCinema);
                    log.info("[TRACE] Fin ensureCinemaSelectedFromAlimentos (cine visible por texto) | duracionMs={}",
                            System.currentTimeMillis() - t0Total);
                    return;
                }
                log.info("[CinemasHelper] Hay cine seleccionado pero no se pudo leer su nombre ({}ms) — " +
                         "procediendo con selección de '{}'.",
                         System.currentTimeMillis() - t0Total, targetCinema);
            }
        } else {
            log.info("[CinemasHelper] No hay cine seleccionado — configurando: '{}'", targetCinema);
        }

        // El cine no coincide o no hay uno seleccionado — abrir selector y seleccionar
        long t0Search = System.currentTimeMillis();
        log.info("[PERF] Paso: Inicio búsqueda cine | Inicio: {}", t0Search);
        log.info("[TRACE] Esperando locator selector 'Seleccionar cines' (waitSelectorScreenOrThrow) | hilo={} plataforma={}",
                Thread.currentThread().getName(), isIOS() ? "iOS" : "Android");
        openSelectorFromAlimentosIfNeeded();
        waitSelectorScreenOrThrow();
        log.info("[TRACE] Locator selector 'Seleccionar cines' encontrado | duracionMs={}",
                System.currentTimeMillis() - t0Search);
        typeInSearchBoxULTRA(targetCinema);
        log.info("[PERF] Paso: Fin búsqueda cine | Fin: {} | Duración: {}ms",
                System.currentTimeMillis(), System.currentTimeMillis() - t0Search);

        long t0Pick = System.currentTimeMillis();
        log.info("[PERF] Paso: Inicio aplicación selección | Inicio: {}", t0Pick);
        pickCinemaFromResults(targetCinema);
        acceptAlertsIfPresent();
        clickAplicarSeleccion();
        acceptAlertsIfPresent();
        log.info("[PERF] Paso: Fin aplicación selección | Fin: {} | Duración: {}ms",
                System.currentTimeMillis(), System.currentTimeMillis() - t0Pick);

        dismissClubLoginIfPresent();
        goToAlimentosTab();

        log.info("[CinemasHelper] Cine configurado correctamente -> '{}' | Total: {}ms",
                targetCinema, System.currentTimeMillis() - t0Total);
        log.info("[TRACE] Fin ensureCinemaSelectedFromAlimentos (cine configurado) | duracionMs={}",
                System.currentTimeMillis() - t0Total);
    }

    /**
     * Intenta leer el nombre del cine actualmente seleccionado desde el chip del menú de alimentos.
     * El chip muestra el nombre del cine como TextView hermano o ancestro de la etiqueta "Cines".
     *
     * OPTIMIZACIÓN: todos los findElements usan implicitlyWait=0 para evitar 3×10s de espera
     * cuando el chip existe pero las estrategias de lectura no encuentran el nombre.
     * Si el elemento "Cines" SÍ existe, retorna inmediatamente con el resultado.
     */
    private String getCurrentCinemaName() {
        long t0 = System.currentTimeMillis();
        log.info("[PERF] Paso: Inicio lectura cine actual | Inicio: {}", t0);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

            boolean ios = isIOS();

            // 1) TextView hermano anterior o posterior a la etiqueta "Cines"
            try {
                By siblingsLocator = ios
                        ? By.xpath("//*[@label='Cines' or @name='Cines' or @value='Cines']/preceding-sibling::*" +
                                " | //*[@label='Cines' or @name='Cines' or @value='Cines']/following-sibling::*")
                        : By.xpath("//android.widget.TextView[@text='Cines']/preceding-sibling::android.widget.TextView" +
                                " | //android.widget.TextView[@text='Cines']/following-sibling::android.widget.TextView");
                List<WebElement> siblings = driver.findElements(siblingsLocator);
                for (WebElement el : siblings) {
                    try {
                        String t = el.getText();
                        if (t != null && !t.trim().isEmpty() && !t.equals("Cines")) return t.trim();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // 2) content-desc (Android) / name (iOS) del contenedor padre del chip de cines
            try {
                By cinesLabelLocator = ios
                        ? By.xpath("//*[@label='Cines' or @name='Cines' or @value='Cines']")
                        : By.xpath("//android.widget.TextView[@text='Cines']");
                List<WebElement> cinesLabels = driver.findElements(cinesLabelLocator);
                if (cinesLabels != null && !cinesLabels.isEmpty()) {
                    WebElement parent = cinesLabels.get(0).findElement(By.xpath(".."));
                    String desc = parent.getAttribute(ios ? "name" : "content-desc");
                    if (desc != null && !desc.isBlank()) {
                        String cleaned = desc.replace("Cines", "").replace(",", "").trim();
                        if (!cleaned.isEmpty()) return cleaned;
                    }
                }
            } catch (Exception ignored) {}

            // 3) Cualquier texto visible dentro del chip de cines (excluye "Cines")
            try {
                By candidatesLocator = ios
                        ? By.xpath("//*[@label='Cines' or @name='Cines' or @value='Cines']/ancestor::XCUIElementTypeOther[1]" +
                                "//*[@label != 'Cines' and @name != 'Cines' and @value != 'Cines']")
                        : By.xpath("//android.widget.TextView[@text='Cines']/ancestor::android.view.View[1]" +
                                "//android.widget.TextView[@text != 'Cines']");
                List<WebElement> candidates = driver.findElements(candidatesLocator);
                for (WebElement el : candidates) {
                    try {
                        String t = el.getText();
                        if (t != null && !t.trim().isEmpty()) return t.trim();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            return null;
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            log.info("[PERF] Paso: Fin lectura cine actual | Fin: {} | Duración: {}ms",
                    System.currentTimeMillis(), System.currentTimeMillis() - t0);
        }
    }

    /** Compara dos nombres de cine ignorando acentos, mayúsculas y espacios extra. */
    private boolean cinemaMatches(String current, String target) {
        if (current == null || target == null) return false;
        return normalize(stripAccents(current)).contains(normalize(stripAccents(target))) ||
               normalize(stripAccents(target)).contains(normalize(stripAccents(current)));
    }

    private void goToAlimentosTab() {
        long t0 = System.currentTimeMillis();
        // ✅ Antes de navegar, quita pantalla Club si apareció
        dismissClubLoginIfPresent();

        // ✅ Reintentos para garantizar que realmente quedamos en Alimentos
        for (int i = 1; i <= 6; i++) {
            if (clickIfPresent(TAB_ALIMENTOS) || tapIfPresent(TAB_ALIMENTOS) || tapIfPresent(TAB_ALIMENTOS_ALT)) {
                // Espera inteligente: sale en cuanto estamos en Alimentos (máx 750ms)
                smartWait(this::isOnAlimentosHome, 750, 100);
            } else {
                // tab no visible — espera breve a que aparezca o la app se estabilice
                smartWait(this::isOnAlimentosHome, 500, 100);
            }

            // 🔒 Intento duro: buscar el item del bottom-nav por UiSelector/XPath y tap
            if (!isIOS()) {
                try {
                    WebElement alimentos = driver.findElement(AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"Alimentos\")"
                    ));
                    tapCenter(alimentos);
                    // Espera inteligente: sale en cuanto estamos en Alimentos (máx 650ms)
                    smartWait(this::isOnAlimentosHome, 650, 100);
                } catch (Exception ignored) {}
            }

            // ✅ Si ya vemos el header/elementos de Alimentos, salimos
            if (isOnAlimentosHome()) {
                log.debug("[CinemasHelper][MainNav] goToAlimentosTab OK intento={} total={}ms", i, System.currentTimeMillis() - t0);
                return;
            }

            // si falló, espera un poco y reintenta
            smartWait(this::isOnAlimentosHome, 650, 100);
        }

        // 🚨 Si aún vemos Cartelera/Horarios, forzamos tap final.
        if (isVisibleNow(TAB_CARTELERA) || isVisibleNow(TAB_HORARIOS)) {
            log.warn("[CinemasHelper] WARNING: No se logró cambiar a Alimentos. Forzando tap final...");
            if (!isIOS()) {
                try {
                    WebElement alimentos = driver.findElement(AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"Alimentos\")"
                    ));
                    tapCenter(alimentos);
                    // Espera inteligente: máx 900ms
                    smartWait(this::isOnAlimentosHome, 900, 100);
                } catch (Exception ignored) {}
            }
        }
        log.info("[PERF][MainNav] goToAlimentosTab total={}ms", System.currentTimeMillis() - t0);
    }

    private void openSelectorFromAlimentosIfNeeded() {
        // ✅ Asegura que estamos en Alimentos antes de tocar Cines (evita que lo haga desde Películas)
        goToAlimentosTab();
        dismissClubLoginIfPresent();

        if (isSelectorOpen()) return;

        if (tapIfPresent(BTN_SELECCIONAR_UBICACION_CLICKABLE_ANCESTOR)) { sleep(900); return; }
        if (tapIfPresent(BTN_SELECCIONAR_UBICACION_BUTTON_NEAR_TEXT)) { sleep(900); return; }
        if (tapIfPresent(BTN_SELECCIONAR_UBICACION_TEXT)) { sleep(900); return; }

        openCinesIconWithRetries();
    }

    private void openCinesIconWithRetries() {
        for (int i = 1; i <= 5; i++) {
            log.debug("[CinemasHelper] Tap icono Cines intento: {}", i);

            if (tapIfPresent(CINES_ICON_VIEW)) { sleep(450); }
            if (isAfterCinesTapScreenOpen()) return;

            if (tapIfPresent(CINES_TEXT_PARENT)) { sleep(450); }
            if (isAfterCinesTapScreenOpen()) return;

            if (tapIfPresent(CINES_TEXT)) { sleep(450); }
            if (isAfterCinesTapScreenOpen()) return;

            if (!isIOS()) {
                try {
                    WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                            "new UiSelector().text(\"Cines\")"
                    ));
                    tapCenter(el);
                } catch (Exception ignored) {}
            }

            sleep(550);
            if (isAfterCinesTapScreenOpen()) return;
        }
    }

    private boolean isSelectorOpen() {
        // isVisibleInstant(wait=0): retorna en <10ms en lugar de 2×10s cuando aún no se abrió
        return isVisibleInstant(TITLE_SELECCIONAR_CINES) || isVisibleInstant(SEARCH_HINT);
    }

    private boolean isChangeCinemaAlertOpen() {
        return isVisibleInstant(ALERT_CAMBIAR_CINE_TITLE);
    }

    private boolean isAfterCinesTapScreenOpen() {
        return isSelectorOpen() || isChangeCinemaAlertOpen();
    }

    private void waitSelectorScreenOrThrow() {
        long end = System.currentTimeMillis() + 9000;

        while (System.currentTimeMillis() < end) {
            if (isSelectorOpen()) return;

            if (isChangeCinemaAlertOpen()) {
                acceptAlertsIfPresent();
                sleep(700);
            }

            sleep(150);
        }

        openCinesIconWithRetries();

        if (isChangeCinemaAlertOpen()) {
            acceptAlertsIfPresent();
            sleep(700);
        }

        if (!isSelectorOpen()) {
            log.error("[TRACE] waitSelectorScreenOrThrow: locators Android-only (TITLE_SELECCIONAR_CINES/SEARCH_HINT) " +
                    "nunca coincidieron | hilo={} plataforma={} duracionMs={}",
                    Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", System.currentTimeMillis() - (end - 9000));
            throw new RuntimeException("No se abrió la pantalla 'Seleccionar cines'. Evité escribir en 'Ingresa tu folio'.");
        }
    }

    // ==========================================================
    // ✅ ESCRITURA ULTRA (MISMA LÓGICA, ADB SAFE + SENDKEYS + CLIPBOARD)
    // ==========================================================
    private void typeInSearchBoxULTRA(String text) {
        String desired = text == null ? "" : text.trim();
        if (desired.isEmpty()) return;

        for (int attempt = 1; attempt <= 6; attempt++) {
            log.debug("[CinemasHelper] typeInSearchBoxULTRA intento {}", attempt);

            tapIfPresent(SEARCH_PARENT_ROUNDED);
            sleep(200);

            tapIfPresent(SEARCH_INNER_VIEW);
            sleep(200);

            tapIfPresent(SEARCH_INPUT);
            sleep(250);

            adbClearText();
            sleep(200);

            // ✅ 1) ADB input SAFE (sin acentos) para evitar NPE del InputShellCommand
            if (typeViaAdbInput(desired)) {
                sleep(550);

                if (looksTypedOrFiltered(desired)) {
                    log.info("[CinemasHelper] Texto escrito OK (ADB safe)");
                    return;
                }
            }

            // ✅ 2) Fallback sendKeys (mejor para unicode si Compose lo acepta)
            try {
                WebElement input = firstOrNull(SEARCH_INPUT);
                if (input != null) {
                    try { input.click(); } catch (Exception ignored) {}
                    sleep(120);

                    try { input.sendKeys(desired); } catch (Exception ignored) {}
                    sleep(500);

                    if (looksTypedOrFiltered(desired)) {
                        log.info("[CinemasHelper] Texto escrito OK (sendKeys)");
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("[CinemasHelper] fallback sendKeys falló: {}", e.getMessage());
            }

            // ✅ 3) Clipboard paste (muy confiable en Compose cuando input text falla)
            try {
                WebElement input = firstOrNull(SEARCH_INPUT);
                if (input != null && setClipboardTextSafe(desired)) {
                    try { input.click(); } catch (Exception ignored) {}
                    sleep(150);

                    if (pasteFromClipboardKeyEvent()) {
                        sleep(450);
                        if (looksTypedOrFiltered(desired)) {
                            log.info("[CinemasHelper] Texto escrito OK (clipboard paste)");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[CinemasHelper] clipboard fallback falló: {}", e.getMessage());
            }

            sleep(250);
        }

        throw new RuntimeException("No se pudo escribir en el buscador (Compose / foco inestable).");
    }

    private void adbClearText() {
        try {
            mobileShell("input", new String[]{"keyevent", "123"});
            for (int i = 0; i < 40; i++) {
                mobileShell("input", new String[]{"keyevent", "67"});
            }
        } catch (Exception e) {
            log.warn("[CinemasHelper] adbClearText warning: {}", e.getMessage());
        }
    }

    private boolean typeViaAdbInput(String text) {
        try {
            // ✅ ADB input crashea con algunos unicode/acentos -> siempre sin acentos
            String safe = stripAccents(text);
            mobileShell("input", new String[]{"text", escapeForAdbInput(safe)});
            return true;
        } catch (Exception e) {
            log.warn("[CinemasHelper] ADB input falló: {}", e.getMessage());
            return false;
        }
    }

    private void mobileShell(String command, String[] args) {
        if (isIOS()) return; // mobile: shell is Android-only
        Map<String, Object> shellArgs = Map.of(
                "command", command,
                "args", args,
                "includeStderr", true,
                "timeout", 8000
        );
        driver.executeScript("mobile: shell", shellArgs);
    }

    private String escapeForAdbInput(String s) {
        if (s == null) return "";
        String out = s.trim();

        out = out.replace(" ", "%s");
        out = out.replace("\"", "");
        out = out.replace("'", "");

        // sanitiza caracteres problemáticos para adb input
        out = out.replace("&", "");
        out = out.replace("|", "");
        out = out.replace(";", "");
        out = out.replace("\n", "");
        out = out.replace("\r", "");

        return out;
    }

    private String stripAccents(String input) {
        if (input == null) return "";
        String norm = Normalizer.normalize(input, Normalizer.Form.NFD);
        return norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private boolean setClipboardTextSafe(String text) {
        try {
            setClipboardText(text);
            return true;
        } catch (Exception e) {
            log.warn("[CinemasHelper] setClipboardText falló: {}", e.getMessage());
            return false;
        }
    }

    private boolean pasteFromClipboardKeyEvent() {
        try {
            // KEYCODE_PASTE = 279 (no siempre disponible, pero en muchos Samsung sí)
            mobileShell("input", new String[]{"keyevent", "279"});
            return true;
        } catch (Exception e) {
            log.warn("[CinemasHelper] paste keyevent 279 falló: {}", e.getMessage());
            return false;
        }
    }

    // ✅ Validación robusta: Compose a veces no refleja getText() en EditText
    private boolean looksTypedOrFiltered(String desired) {
        try {
            WebElement input = firstOrNull(SEARCH_INPUT);
            String current = safeGetInputValue(input);
            if (current != null && !current.trim().isEmpty()) {
                if (normalize(current).contains(normalize(desired)) ||
                        normalize(stripAccents(current)).contains(normalize(stripAccents(desired)))) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // ✅ Si no refleja el input, valida por resultados filtrados
        String[] words = (desired == null ? "" : desired.trim()).split("\\s+");
        if (words.length == 0) return false;

        String w1 = escapeXpath(words[0]);
        By resultAny = isIOS()
                ? By.xpath("//*[contains(@label,'" + w1 + "') or contains(@name,'" + w1 + "') or contains(@value,'" + w1 + "')]")
                : By.xpath("//android.widget.TextView[contains(@text,'" + w1 + "')]");
        return isVisibleNow(resultAny);
    }

    private String safeGetInputValue(WebElement el) {
        if (el == null) return null;
        try {
            String t = null;
            try { t = el.getText(); } catch (Exception ignored) {}
            if (t != null && !t.trim().isEmpty()) return t;

            try { t = el.getAttribute("text"); } catch (Exception ignored) {}
            if (t != null && !t.trim().isEmpty()) return t;

            try { t = el.getAttribute("value"); } catch (Exception ignored) {}
            if (t != null && !t.trim().isEmpty()) return t;

            try { t = el.getAttribute("label"); } catch (Exception ignored) {}
            if (t != null && !t.trim().isEmpty()) return t;

            try { t = el.getAttribute("content-desc"); } catch (Exception ignored) {}
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void pickCinemaFromResults(String targetCinema) {
        PlatformLocator exact = PlatformLocator.byExactText(targetCinema);
        if (clickIfPresent(exact) || tapIfPresent(exact)) return;

        PlatformLocator contains = PlatformLocator.byTextContains(escapeXpath(targetCinema));
        if (clickIfPresent(contains) || tapIfPresent(contains)) return;

        boolean found = scrollSlowDownThenUpUntilVisible(contains, 12);
        if (!found) throw new RuntimeException("No se encontró el cine en resultados: " + targetCinema);

        if (!tapIfPresent(contains)) click(contains);
    }
    public void dismissClubLoginGuard() {
        dismissClubLoginGuard("unknown");
    }

    // ✅ Guard con logs para verificar que sí se ejecuta y qué hizo
    public void dismissClubLoginGuard(String where) {
        long t0 = System.currentTimeMillis();
        try {
            log.info("[CinemasHelper][ClubGuard] ENTER where={}", where);

            // Espera inteligente: sale en cuanto Club aparece (máx 600ms) — no consume el timeout completo.
            long t0Espera = System.currentTimeMillis();
            boolean visible = smartWait(this::isClubLoginVisible, 600, 100);
            log.debug("[CinemasHelper][ClubGuard] espera inicial: {}ms visible={}", System.currentTimeMillis() - t0Espera, visible);

            if (!visible) {
                log.debug("[CinemasHelper][ClubGuard] No visible -> SKIP where={}", where);
                log.info("[PERF][ClubGuard] where={} total={}ms", where, System.currentTimeMillis() - t0);
                return;
            }

            log.info("[CinemasHelper][ClubGuard] Visible -> attempting dismiss...");
            long t0Dismiss = System.currentTimeMillis();
            boolean closedReturn = dismissClubLoginIfPresent();

            // Solo chequeamos stillVisible si dismissClubLoginIfPresent() falló (return false)
            // para evitar una consulta de elemento lenta cuando ya sabemos que cerró.
            boolean stillVisible = !closedReturn && isClubLoginVisible();
            log.info("[CinemasHelper][ClubGuard] closedReturn={} stillVisible={} dismiss={}ms",
                    closedReturn, stillVisible, System.currentTimeMillis() - t0Dismiss);

            if (stillVisible) {
                log.warn("[CinemasHelper][ClubGuard] STILL visible -> last resort navigate.back()");
                try {
                    driver.navigate().back();
                    smartWait(() -> !isClubLoginVisible(), 700, 100);
                } catch (Exception ignored) {}
                log.debug("[CinemasHelper][ClubGuard] after last resort stillVisible={}", isClubLoginVisible());
            }

            log.info("[CinemasHelper][ClubGuard] EXIT where={}", where);
            log.info("[PERF][ClubGuard] where={} total={}ms", where, System.currentTimeMillis() - t0);

        } catch (Exception e) {
            log.error("[CinemasHelper][ClubGuard] ERROR where={} msg={} total={}ms", where, e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    private static void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /**
     * Espera activa: retorna en cuanto la condición es verdadera o se agota el timeout.
     * Sale inmediatamente cuando el estado de la app cambia — no consume el timeout completo.
     * Reemplaza Thread.sleep fijos donde la duración real depende del estado de la app.
     *
     * @param condition condición a evaluar (true = condición cumplida)
     * @param maxMs     tiempo máximo de espera en ms
     * @param pollMs    intervalo de polling en ms
     * @return true si la condición se cumplió antes del timeout
     */
    // PERF (Iteración 2 — auditoría de smartWait, confirmada por evidencia, no supuesta):
    // el `return condition.getAsBoolean()` final del código original volvía a invocar la
    // condición COMPLETA al salir del bucle, sin importar que la última iteración ya la
    // hubiera evaluado (con resultado false, sin que nada cambiara entre medio) — una
    // llamada WDA duplicada e innecesaria en cada timeout que expira. Se reemplaza por
    // `do-while` que guarda el último resultado calculado y lo reutiliza en vez de
    // reevaluar — preserva la garantía de "al menos una evaluación" para el caso borde
    // maxMs≈0 (donde un `while` normal no correría ni una vez), sin la reevaluación extra.
    //
    // Limitación NO corregida aquí (fuera de alcance — requeriría tocar el cliente HTTP
    // de DriverFactory, otra clase): el deadline solo se revisa ENTRE invocaciones de
    // `condition`, nunca la interrumpe. Si una sola llamada a `condition.getAsBoolean()`
    // tarda más que `maxMs` (p. ej. isClubLoginVisible() con varios findElements lentos),
    // el presupuesto ya se excedió en esa única invocación — el cap es un límite de
    // "cuántas veces reintentar", no un techo absoluto de tiempo transcurrido.
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
    private void acceptAlertsIfPresent() {
        long end = System.currentTimeMillis() + 4500;

        while (System.currentTimeMillis() < end) {
            // isVisibleInstant(wait=0): el polling ya está acotado a 4500ms;
            // sin esto, cada isVisibleNow bloqueaba hasta 10s y desbordaba el timeout.
            if (isVisibleInstant(ALERT_CAMBIAR_CIUDAD_TITLE)) {
                log.info("[CinemasHelper] Alerta ciudad -> Aceptar (last())");
                if (!tapIfPresent(ALERT_ACEPTAR_LAST)) clickIfPresent(ALERT_ACEPTAR_LAST);
                sleep(450);
                return;
            }

            if (isVisibleInstant(ALERT_CAMBIAR_CINE_TITLE)) {
                log.info("[CinemasHelper] Alerta cambiar cine -> Sí, cambiar de cine (CLICKABLE ANCESTOR)");

                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_CLICKABLE_ANCESTOR)) { sleep(650); return; }
                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_TEXT)) { sleep(650); return; }
                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_BUTTON_ABS)) { sleep(650); return; }
                if (tapIfPresent(BTN_MODAL_ANY_BUTTON)) { sleep(650); return; }

                if (!isIOS()) {
                    try {
                        WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                                "new UiSelector().text(\"Sí, cambiar de cine\")"
                        ));
                        tapCenter(el);
                    } catch (Exception ignored) {}
                }

                sleep(700);
                if (isSelectorOpen()) return;
            }

            sleep(120);
        }
    }

    // ==========================
    // Helpers
    // ==========================
    private boolean tapIfPresent(By locator) {
        try {
            WebElement el = firstOrNull(locator);
            if (el == null || !el.isDisplayed()) return false;

            try {
                el.click();
                return true;
            } catch (Exception ignored) {}

            try {
                int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
                int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);

                w3cTap(cx, cy, 140);
                return true;
            } catch (Exception ignored) {}

            if (!isIOS()) {
                try {
                    String id = ((RemoteWebElement) el).getId();
                    Map<String, Object> args = Map.of("elementId", id);
                    driver.executeScript("mobile: clickGesture", args);
                    return true;
                } catch (Exception ignored) {}

                try {
                    int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
                    int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);
                    Map<String, Object> args = Map.of("x", cx, "y", cy);
                    driver.executeScript("mobile: clickGesture", args);
                    return true;
                } catch (Exception ignored) {}
            } else {
                try {
                    int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
                    int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);
                    tapW3C(cx, cy);
                    return true;
                } catch (Exception ignored) {}
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tapIfPresent(PlatformLocator locator) {
        return tapIfPresent(locator.resolve(isIOS()));
    }

    // ✅ W3C tap (reemplaza TouchAction para Appium 2 / Selenium 4)
    private void w3cTap(int x, int y, long holdMs) {
        try {
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seq = new Sequence(finger, 1);
            seq.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
            seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            seq.addAction(new Pause(finger, Duration.ofMillis(Math.max(0, holdMs))));
            seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(seq));
        } catch (Exception ignored) {}
    }

    public void tapCenter(WebElement el) {
        try {
            int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
            int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);

            w3cTap(cx, cy, 140);
        } catch (Exception ignored) {}
    }

    private WebElement firstOrNull(By locator) {
        try {
            List<WebElement> els = driver.findElements(locator);
            if (els == null || els.isEmpty()) return null;
            return els.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private WebElement firstOrNull(PlatformLocator locator) {
        return firstOrNull(locator.resolve(isIOS()));
    }

    private String safeGetText(WebElement el) {
        try { return el == null ? null : el.getText(); } catch (Exception e) { return null; }
    }

    private boolean isVisibleNow(By locator) {
        try {
            WebElement el = firstOrNull(locator);
            return el != null && el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVisibleNow(PlatformLocator locator) {
        return isVisibleNow(locator.resolve(isIOS()));
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String escapeXpath(String s) {
        return (s == null) ? "" : s.replace("'", "");
    }


    // ==========================
    // ✅ CLUB CINÉPOLIS GUARD
    // ==========================

    /**
     * Verificación rápida de pantalla Club Cinépolis.
     * Usa isVisibleInstant (wait=0) para los checks principales:
     *   - Club ausente → retorna false en ~0 ms (antes: hasta 10 s por check)
     *   - Club presente → retorna true en ~0 ms (sin cambio funcional)
     * El fallback XPath también usa implicitlyWait=0 para evitar 10 s adicionales.
     */
    public boolean isClubLoginVisible() {
        // El menú de Alimentos tiene "Ingresa tu folio" — no es la pantalla de login.
        if (isVisibleInstant(PlatformLocator.byExactText("Ingresa tu folio"))) return false;

        if (isVisibleInstant(CLUB_LOGIN_TITLE)) return true;
        if (isVisibleInstant(CLUB_LOGIN_LOGO))  return true;

        // Fallback con patrones sin acento — también con wait=0 (era el mayor cuello de botella).
        // NOTA-MIGRACION: usaba @text sin condicional de plataforma (bare attribute, sin
        // prefijo android.widget.* — por eso no lo capturó el barrido inicial). En iOS
        // ningún nodo expone @text, por lo que este fallback nunca encontraba nada.
        // PERF (solo iOS): NSPredicate en vez de XPath — ver nota en PlatformLocator.byExactText().
        // Este método es el PRIMER check de isClubLoginVisible(), evaluado en cada uno de
        // los hasta 5 passes de PromosGuard — el fallback más caliente de todo el guard.
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            By fallback = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                            "label CONTAINS 'Inicia sesi' OR label CONTAINS 'CLUB Cin' OR label CONTAINS 'Club Cin' " +
                            "OR label CONTAINS 'Correo electr' OR label CONTAINS 'Contrase'")
                    : By.xpath(
                            "//*[contains(@text,'Inicia sesi')" +
                            " or contains(@text,'CLUB Cin')" +
                            " or contains(@text,'Club Cin')" +
                            " or contains(@text,'Correo electr')" +
                            " or contains(@text,'Contrase')]");
            List<WebElement> els = driver.findElements(fallback);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            for (WebElement el : els) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        return false;
    }

    /**
     * Descarta la pantalla de Club asumiendo que YA es visible (no agrega el sleep de 600 ms
     * de dismissClubLoginGuard, que sería redundante cuando ya se verificó la visibilidad).
     * Solo llamar desde dismissTransientPromosGuard tras isClubLoginVisible()=true.
     */
    private void dismissClubWhenAlreadyVisible(String where) {
        utils.PerfMetrics.startPhase("ClubGuard");
        try {
            log.info("[CinemasHelper][ClubGuard] ENTER where={}", where);
            long t0 = System.currentTimeMillis();

            // Llamar directo a dismissClubLoginIfPresent sin sleep previo (ya sabemos que está visible)
            boolean closedReturn = dismissClubLoginIfPresent();

            // Solo re-verificar si el dismiss falló (evita consulta extra cuando cerró OK)
            boolean stillVisible = !closedReturn && isClubLoginVisible();
            log.info("[CinemasHelper][ClubGuard] closedReturn={} stillVisible={} tiempo={}ms",
                    closedReturn, stillVisible, System.currentTimeMillis() - t0);
            utils.PerfMetrics.attempt("ClubGuard", 1, where, System.currentTimeMillis() - t0,
                    closedReturn ? "OK" : (stillVisible ? "FAIL" : "OK"));

            if (stillVisible) {
                log.warn("[CinemasHelper][ClubGuard] STILL visible -> last resort navigate.back()");
                try { driver.navigate().back(); safeSleep(700); } catch (Exception ignored) {}
            }

            log.info("[CinemasHelper][ClubGuard] EXIT where={}", where);
        } catch (Exception e) {
            log.error("[CinemasHelper][ClubGuard] ERROR where={} msg={}", where, e.getMessage());
        } finally {
            utils.PerfMetrics.endPhase("ClubGuard");
        }
    }

    private boolean intentoNavigateBack(long waitMs) {
        try {
            driver.navigate().back();
            smartWait(() -> !isClubLoginVisible(), waitMs, 100);
        } catch (Exception ignored) {}
        return !isClubLoginVisible();
    }

    private boolean intentoTapBackUI(long waitMs) {
        if (!tapBackFromClubUI()) return false;
        smartWait(() -> !isClubLoginVisible(), waitMs, 100);
        return !isClubLoginVisible();
    }

    private boolean intentoTapA11y(long waitMs) {
        tapIfPresent(CLUB_BACK_BUTTON_A11Y);
        smartWait(() -> !isClubLoginVisible(), waitMs, 100);
        return !isClubLoginVisible();
    }

    /**
     * Cierra la pantalla de Club Cinépolis.
     *
     * PERF (Problema 3 — ClubGuard >20s) — CORRECCIÓN basada en evidencia real de
     * ejecución (métricas [CinemasHelper][ClubGuard]): un primer intento de este fix
     * reordenó iOS para probar el tap de UI (tapBackFromClubUI/A11Y) antes que
     * navigate().back(), asumiendo que el tap nativo sería más rápido — la métrica real
     * mostró lo CONTRARIO: 44160ms en "vuelta=1" antes de cerrar, con
     * "Pantalla Club cerrada OK (navigate.back)" como mecanismo que realmente funcionó.
     * tapBackFromClubUI() usa CLUB_BACK_BUTTON_XPATH_IOS, un XPath posicional
     * (deliberadamente no convertido a NSPredicate por ser posicional — ver
     * PlatformLocator) que se evalúa DOS veces por intento (bug preexistente en
     * tapBackFromClubUI, no introducido aquí) y aparentemente nunca encuentra el botón
     * correcto en iOS — cada intento fallido paga el costo completo de ese XPath antes
     * de llegar a lo que sí funciona. Se revierte el orden: navigate().back() primero
     * en AMBAS plataformas (igual que el código original, antes de este Problema 3),
     * que es lo que la evidencia real confirma que funciona rápido en este dispositivo.
     * Se conserva la reducción de 3 a 2 vueltas (sigue siendo válida por sí sola).
     */
    public boolean dismissClubLoginIfPresent() {
        if (!isClubLoginVisible()) return false;

        log.info("[CinemasHelper] Detectada pantalla Club Cinépolis. Intentando cerrarla...");
        long tTotal = System.currentTimeMillis();
        boolean ios = isIOS();

        for (int i = 1; i <= 2; i++) {
            long tVuelta = System.currentTimeMillis();
            try {
                if (ios) {
                    if (intentoNavigateBack(600)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (navigate.back) | vuelta={} total={}ms", i, System.currentTimeMillis() - tTotal);
                        return true;
                    }
                    if (intentoTapBackUI(400)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (tapBackFromClubUI) | vuelta={} total={}ms", i, System.currentTimeMillis() - tTotal);
                        return true;
                    }
                    if (intentoTapA11y(400)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (A11Y) | vuelta={} total={}ms", i, System.currentTimeMillis() - tTotal);
                        return true;
                    }
                } else {
                    if (intentoNavigateBack(600)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (navigate.back).");
                        return true;
                    }
                    if (intentoTapBackUI(400)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (tapBackFromClubUI).");
                        return true;
                    }
                    if (intentoTapA11y(400)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (A11Y).");
                        return true;
                    }
                    if (intentoNavigateBack(700)) {
                        log.info("[CinemasHelper] Pantalla Club cerrada OK (navigate.back).");
                        return true;
                    }
                }
                log.debug("[CinemasHelper][ClubGuard] vuelta={} sin éxito ({}ms)", i, System.currentTimeMillis() - tVuelta);
            } catch (Exception e) {
                // no reventar el flujo
            }
        }

        log.warn("[CinemasHelper] No se pudo cerrar Club Cinépolis en reintentos; se continúa flujo.");
        return false;
    }
    // NOTA-MIGRACION: usaba @text sin condicional de plataforma (bare attribute, sin
    // prefijo android.widget.* — por eso no lo capturó el barrido inicial). En iOS
    // ningún nodo expone @text, por lo que la promo Mario nunca se detectaba/cerraba ahí.
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    // Llamado en cada pass de PromosGuard (hasta 5 veces por @BeforeEach) — el
    // candidato de mayor impacto para esta conversión.
    private By marioPromoLocator() {
        return isIOS()
                ? AppiumBy.iOSNsPredicateString("label == 'CONSULTA CARTELERA' OR label CONTAINS 'CONSULTA CARTELERA' " +
                        "OR value == 'CONSULTA CARTELERA' OR value CONTAINS 'CONSULTA CARTELERA'")
                : By.xpath("//*[normalize-space(@text)='CONSULTA CARTELERA' or contains(@text,'CONSULTA CARTELERA')]");
    }

    private boolean isMarioPromoVisible() {
        // implicitlyWait=0: evita 10s de espera por pass cuando la promo no está presente.
        // Era el principal cuello de botella en PromosGuard (5 passes × 10s = 50s extra).
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            // ✅ SOLO si existe el CTA específico de la promo
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

            log.info("[CinemasHelper] Promo Mario detectada. Cerrando...");

            // Intento 1: tap al CTA "CONSULTA CARTELERA"
            List<WebElement> ctas = driver.findElements(marioPromoLocator());
            if (!ctas.isEmpty()) {
                ctas.get(0).click();
                // Espera inteligente: sale en cuanto la promo desaparece (máx 800ms)
                smartWait(() -> !isMarioPromoVisible(), 800, 100);
                return;
            }

            // Intento 2: back (fallback)
            driver.navigate().back();
            // Espera inteligente: sale en cuanto la promo desaparece (máx 600ms)
            smartWait(() -> !isMarioPromoVisible(), 600, 100);

        } catch (Exception e) {
            log.warn("[CinemasHelper] No se pudo cerrar promo Mario (safe ignore)");
        }
    }
    public void dismissTransientPromosGuard() {
        dismissTransientPromosGuard("unknown");
    }

    /**
     * Guard de overlays transitorios (Club + Mario + Popup zona).
     *
     * OPTIMIZACIONES:
     *  1. clubAlreadyDismissed → evita re-verificar Club en passes posteriores
     *     (antes: 5 passes × isClubLoginVisible ~10 s = hasta 50 s extra)
     *  2. dismissClubWhenAlreadyVisible → omite el sleep de 600 ms de ClubGuard
     *     cuando Club ya es conocidamente visible (detectado por PromosGuard)
     *  3. esperarMainNavRapido(5000) tras cerrar Club → polling 200 ms max 5 s
     *     (antes: isMainNavVisible con 4×wait=10 s = hasta 40 s)
     *  4. isMainNavVisible usa isVisibleInstant → retorna en <10 ms
     *
     * Comportamiento funcional: IDÉNTICO al anterior. PromosGuard y ClubGuard
     * coexisten; ninguno se elimina.
     */
    /** Envuelve el guard real con instrumentación de fase (ver utils.PerfMetrics) — el endPhase() se garantiza vía try/finally sin importar cuál de los múltiples "return" internos se tome. */
    public void dismissTransientPromosGuard(String where) {
        utils.PerfMetrics.measure("PromosGuard", () -> dismissTransientPromosGuardImpl(where));
    }

    private void dismissTransientPromosGuardImpl(String where) {
        log.info("[TRACE] Inicio PromosGuard | hilo={} plataforma={} where={} hora={}",
                Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", where, System.currentTimeMillis());
        log.info("[CinemasHelper][PromosGuard] ENTER where={}", where);
        long tTotal = System.currentTimeMillis();

        // Acumuladores de tiempo por componente (para el reporte final)
        long msClub    = 0;
        long msZona    = 0;
        long msMainNav = 0;

        // Flag local: una vez cerrado Club no volvemos a verificarlo en este guard.
        // Esto evita que los passes 2-5 gasten ~10 s cada uno en isClubLoginVisible().
        // iOS: se inicializa en true si un llamado ANTERIOR (otro test de la misma
        // suite) ya confirmó que Club se cerró — cache por-ejecución (Problema:
        // "Club ya cerrado → no volver a calcularlo"). Android nunca lee este cache;
        // conserva el comportamiento de siempre (re-verifica en cada llamada).
        boolean clubAlreadyDismissed = isIOS() && iosClubClosedThisRun;
        if (clubAlreadyDismissed) {
            log.debug("[CinemasHelper][PromosGuard] Club ya cerrado en una llamada previa de esta ejecución (cache iOS) — skip total");
        }

        for (int pass = 1; pass <= 5; pass++) {
            boolean dismissed = false;

            // ── Club Cinépolis ────────────────────────────────────────────────────
            try {
                if (!clubAlreadyDismissed) {
                    long t0Club = System.currentTimeMillis();
                    boolean clubVisible = isClubLoginVisible();
                    if (clubVisible) {
                        log.info("[CinemasHelper][PromosGuard] pass={} Club visible -> dismiss", pass);
                        // dismissClubWhenAlreadyVisible: ya sabemos que está visible → sin sleep 600 ms
                        dismissClubWhenAlreadyVisible(where + ":club");
                        dismissed = true;
                        clubAlreadyDismissed = true;  // no repetir en passes siguientes
                        if (isIOS()) iosClubClosedThisRun = true;  // no repetir en llamadas futuras de esta ejecución

                        // Poll rápido de Main Nav tras cierre de Club (evita las 4×10 s lentas)
                        long tNav0 = System.currentTimeMillis();
                        if (esperarMainNavRapido(5000)) {
                            msClub    += System.currentTimeMillis() - t0Club;
                            msMainNav += System.currentTimeMillis() - tNav0;
                            log.info("[PromosGuard] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | EXIT pass={} where={}",
                                    msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, pass, where);
                            log.info("[TRACE] Fin PromosGuard (via MainNav tras Club) | hilo={} plataforma={} where={} duracionMs={}",
                                    Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", where, System.currentTimeMillis() - tTotal);
                            return;
                        }
                        log.debug("[CinemasHelper][PromosGuard] Main nav no visible en 5s tras cerrar Club ({}ms)",
                                System.currentTimeMillis() - tNav0);
                        msMainNav += System.currentTimeMillis() - tNav0;
                    }
                    msClub += System.currentTimeMillis() - t0Club;
                } else {
                    log.debug("[CinemasHelper][PromosGuard] pass={} Club ya resuelto — skip check", pass);
                }
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Club guard error: {}", e.getMessage());
            }

            // ── Mario Promo ───────────────────────────────────────────────────────
            // iOS: cache por-ejecución — una vez resuelta (dismisses o confirmada
            // ausente) en un pase de CUALQUIER llamada previa de esta ejecución, se
            // asume resuelta para el resto (mismo criterio que Club: overlay de
            // sesión fresca, no reaparece). Android sin cambios: siempre re-verifica.
            try {
                if (isIOS() && iosNoPromosThisRun) {
                    log.debug("[CinemasHelper][PromosGuard] Mario ya resuelto en esta ejecución (cache iOS) — skip check");
                } else if (isMarioPromoVisible()) {
                    log.info("[CinemasHelper][PromosGuard] pass={} Mario visible -> dismiss", pass);
                    dismissMarioPromoIfPresent();
                    dismissed = true;
                    if (isIOS()) iosNoPromosThisRun = true;
                } else if (isIOS() && pass == 1) {
                    // Confirmado ausente en el primer pase real de esta llamada — cachear.
                    iosNoPromosThisRun = true;
                }
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Mario guard error: {}", e.getMessage());
            }

            // ── Popup zona/ubicación ──────────────────────────────────────────────
            try {
                long t0Zona = System.currentTimeMillis();
                if (isLocationChangePopupVisible()) {
                    log.info("[CinemasHelper][PromosGuard] pass={} Zona visible -> dismiss", pass);
                    dismissLocationChangePopupIfPresent(where + ":zona");
                    dismissed = true;
                    safeSleep(700);
                }
                msZona += System.currentTimeMillis() - t0Zona;
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Zona guard error: {}", e.getMessage());
            }

            // ── Salida por Main Nav visible ───────────────────────────────────────
            try {
                long t0Nav = System.currentTimeMillis();
                if (isMainNavVisible()) {
                    msMainNav += System.currentTimeMillis() - t0Nav;
                    log.info("[PERF][BeforeEach] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | EXIT pass={} where={}",
                            msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, pass, where);
                    log.info("[TRACE] Fin PromosGuard (MainNav visible) | hilo={} plataforma={} where={} duracionMs={}",
                            Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", where, System.currentTimeMillis() - tTotal);
                    return;
                }
                msMainNav += System.currentTimeMillis() - t0Nav;
            } catch (Exception e) {
                log.warn("[CinemasHelper][PromosGuard] isMainNavVisible error (pass={}): {}", pass, e.getMessage());
            }

            if (!dismissed) {
                log.debug("[CinemasHelper][PromosGuard] pass={} nada cerrado, prueba dismiss genérico", pass);
                tryGenericOverlayDismiss();
            }

            // Espera inteligente: sale en cuanto Main Nav es visible (máx 500ms) en lugar de sleep fijo.
            smartWait(this::isMainNavVisible, 500, 100);
        }

        log.warn("[PERF][BeforeEach] ClubGuard={}ms ZonaGuard={}ms MainNav={}ms Total={}ms | Max passes where={}",
                msClub, msZona, msMainNav, System.currentTimeMillis() - tTotal, where);
        log.info("[CinemasHelper][PromosGuard] EXIT where={}", where);
        log.info("[TRACE] Fin PromosGuard (5 passes agotados, MainNav NUNCA detectado) | hilo={} plataforma={} where={} duracionMs={}",
                Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", where, System.currentTimeMillis() - tTotal);
    }

    /**
     * Devuelve true si el bottom nav principal es accesible.
     * Usa isVisibleInstant (wait=0) para evitar 4×10 s de espera cuando la app
     * aún está animando tras cerrar Club — diferencia crítica de rendimiento.
     */
    private boolean isMainNavVisible() {
        return isVisibleInstant(TAB_CARTELERA)
            || isVisibleInstant(TAB_HORARIOS)
            || isVisibleInstant(TAB_ALIMENTOS)
            || isVisibleInstant(TAB_ALIMENTOS_ALT);
    }

    /**
     * Intenta cerrar cualquier overlay desconocido usando patrones comunes de dismiss.
     *
     * OPTIMIZACIÓN: usa tapInstant() (implicitlyWait=0) en lugar de tapIfPresent() (10s wait).
     * Sin esto, 4 locators × 10s = 40s por llamada cuando no hay overlay presente.
     * Con tapInstant(), retorna en <10ms si ningún elemento existe en el DOM actual.
     */
    private void tryGenericOverlayDismiss() {
        // Botones de cierre más comunes en promos/modales de Cinépolis.
        // Android: @content-desc/@text. iOS: @name/@label (XCUITest).
        By[] dismissLocatorsAndroid = {
            By.xpath("//*[@content-desc='Close' or @content-desc='Cerrar' or @content-desc='close']"),
            By.xpath("//android.widget.Button[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.TextView[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.ImageButton[@content-desc='Atrás' or @content-desc='Atras' or @content-desc='Navigate up']"),
        };
        // NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
        By[] dismissLocatorsIOS = {
            AppiumBy.iOSNsPredicateString("name == 'Close' OR name == 'Cerrar' OR name == 'close'"),
            AppiumBy.iOSNsPredicateString("label == 'Cerrar' OR label == 'No gracias' OR label == 'Omitir' OR label == 'Saltar'"),
            AppiumBy.iOSNsPredicateString("name == 'Atrás' OR name == 'Atras' OR name == 'Navigate up' OR name == 'Back'"),
        };
        By[] dismissLocators = isIOS() ? dismissLocatorsIOS : dismissLocatorsAndroid;
        for (By loc : dismissLocators) {
            if (tapInstant(loc)) {
                log.info("[CinemasHelper][PromosGuard] Overlay genérico cerrado con: {}", loc);
                safeSleep(500);
                return;
            }
        }
    }

    // Espera hasta 5 s a que aparezca el popup de cambio de zona/ubicación y lo cierra
    // si se presenta. Pensado para llamarse en @BeforeAll justo después de lanzar la app.
    public void dismissLocationPopupIfPresent() {
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            if (isLocationChangePopupVisible()) {
                dismissLocationChangePopupIfPresent("beforeAll");
                return;
            }
            safeSleep(400);
        }
    }

    private boolean isLocationChangePopupVisible() {
        // implicitlyWait=0: evita 10s de espera por pass cuando el popup no está presente.
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            List<WebElement> els = driver.findElements(POPUP_ZONA_DETECTION.resolve(isIOS()));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            for (WebElement el : els) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        return false;
    }

    private void dismissLocationChangePopupIfPresent(String where) {
        long t0 = System.currentTimeMillis();
        if (!isLocationChangePopupVisible()) {
            log.debug("[CinemasHelper][ZonaGuard] Popup zona no visible -> SKIP where={}", where);
            return;
        }

        log.info("[CinemasHelper][ZonaGuard] Popup cambio de zona detectado -> tap 'No cambiar' where={}", where);

        for (int i = 1; i <= 3; i++) {
            // Intento 1: tap directo por locator
            if (tapIfPresent(BTN_NO_CAMBIAR)) {
                // Espera inteligente: sale en cuanto el popup desaparece (máx 600ms)
                smartWait(() -> !isLocationChangePopupVisible(), 600, 100);
                if (!isLocationChangePopupVisible()) {
                    log.info("[CinemasHelper][ZonaGuard] Popup cerrado OK (tapIfPresent) intento={}", i);
                    log.info("[PERF][ZonaGuard] where={} total={}ms", where, System.currentTimeMillis() - t0);
                    return;
                }
            }

            // Intento 2: UiAutomator (Android) / XPath (iOS)
            if (!isIOS()) {
                try {
                    WebElement btn = driver.findElement(
                            AppiumBy.androidUIAutomator("new UiSelector().text(\"No cambiar\")"));
                    tapCenter(btn);
                    // Espera inteligente: sale en cuanto el popup desaparece (máx 600ms)
                    smartWait(() -> !isLocationChangePopupVisible(), 600, 100);
                    if (!isLocationChangePopupVisible()) {
                        log.info("[CinemasHelper][ZonaGuard] Popup cerrado OK (UiAutomator) intento={}", i);
                        log.info("[PERF][ZonaGuard] where={} total={}ms", where, System.currentTimeMillis() - t0);
                        return;
                    }
                } catch (Exception ignored) {}
            }

            safeSleep(300);
        }

        log.warn("[CinemasHelper][ZonaGuard] No se pudo cerrar popup de zona ({}ms); se continúa flujo.",
                System.currentTimeMillis() - t0);
    }

    /**
     * Encuentra el botón back de la pantalla Club (android.widget.Button instance(0) / primer Button)
     * y hace tap al centro del elemento (sin coordenadas fijas).
     * El caller ya verificó isClubLoginVisible() — no se repite aquí para evitar latencia.
     */
    private boolean tapBackFromClubUI() {
        try {
            // PERF: en iOS ambas ramas consultaban el MISMO locator posicional
            // (CLUB_BACK_BUTTON_XPATH_IOS) dos veces cuando la primera no encontraba
            // nada — consulta redundante, nunca podía dar un resultado distinto la
            // segunda vez. Se consulta una sola vez en iOS; Android conserva su
            // fallback real (UIAutomator → XPath, dos locators genuinamente distintos).
            List<WebElement> candidates;
            if (isIOS()) {
                candidates = driver.findElements(CLUB_BACK_BUTTON_XPATH_IOS);
            } else {
                candidates = driver.findElements(CLUB_BACK_BUTTON_UIAUTO);
                if (candidates == null || candidates.isEmpty()) {
                    candidates = driver.findElements(CLUB_BACK_BUTTON_XPATH);
                }
            }

            for (WebElement el : candidates) {
                if (el == null) continue;
                try {
                    if (!el.isDisplayed()) continue;
                } catch (Exception ignored) {}

                try {
                    // tap al centro según bounds del elemento
                    org.openqa.selenium.Rectangle r = el.getRect();
                    int cx = r.getX() + (r.getWidth() / 2);
                    int cy = r.getY() + (r.getHeight() / 2);

                    // Sanity: la flecha ← está en la esquina superior-izquierda.
                    // cx ≈ 4% del ancho, cy ≈ 3% del alto (Galaxy A56 5G 1080×2340).
                    // Límite amplio (20% × 12%) para cubrir distintos dispositivos sin
                    // aceptar "Inicia sesión" ni "Crear tu Cuenta" (botones centrados).
                    org.openqa.selenium.Dimension d = driver.manage().window().getSize();
                    if (cx > d.width * 0.20 || cy > d.height * 0.12) {
                        log.debug("[ClubBack] Botón descartado: posición ({},{}) fuera del área back", cx, cy);
                        continue;
                    }

                    w3cTap(cx, cy, 120);
                    return true;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return false;
    }

    // Heurística ligera para confirmar que estamos en Alimentos (sin quitar tu lógica)
    private boolean isOnAlimentosHome() {
        // 🚫 Si vemos Cartelera/Horarios estamos en Películas, NO en Alimentos
        if (isVisibleNow(TAB_CARTELERA) || isVisibleNow(TAB_HORARIOS)) return false;

        // ✅ Si está el chip/icono de Cines del módulo Alimentos, ya estamos ahí
        if (exists(CINES_ICON_VIEW, 1)) return true;

        // ✅ O si el bottom nav marca Alimentos como seleccionado
        if (exists(TAB_ALIMENTOS_SELECTED, 1)) return true;

        // Fallback: si al menos el tab existe y NO estamos en Cartelera/Horarios, lo damos por bueno
        return exists(TAB_ALIMENTOS, 1) || exists(TAB_ALIMENTOS_ALT, 1);
    }

    /**
     * CAUSA RAÍZ (Verificación del cine ~13s / isOnAlimentosHome): driver.findElements()
     * aquí NO forzaba implicitlyWait=0 antes de cada intento, a diferencia de
     * isVisibleInstant/findInstant en este mismo archivo. Cuando el locator NO está
     * presente (el caso normal — p. ej. cine ya seleccionado, chip "sin selección"
     * ausente), esa única llamada heredaba el implicitlyWait ambiental de 10s activo
     * desde el primer chequeo de la suite, bloqueando esos 10s completos DENTRO de la
     * primera vuelta del while — el parámetro "seconds" (pensado como tope de 1s) nunca
     * se llegaba a respetar. Con implicitlyWait=0 forzado, cada intento retorna en
     * milisegundos si el elemento no está, y el while sí cumple su propio tope real.
     */
    private boolean exists(By by, int seconds) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            try {
                if (seconds <= 0) return !driver.findElements(by).isEmpty();
                long end = System.currentTimeMillis() + (seconds * 1000L);
                while (System.currentTimeMillis() < end) {
                    if (!driver.findElements(by).isEmpty()) return true;
                    sleep(150);
                }
                return false;
            } finally {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean exists(PlatformLocator locator, int seconds) {
        return exists(locator.resolve(isIOS()), seconds);
    }

    // ==========================
    // ✅ SELECCIÓN DE CINE PARA MÉXICO (data-driven desde archivo)
    // ==========================

    /**
     * Escenario 1: "Selecciona uno o más cines" visible → no hay cine → selecciona desde config.
     * Escenario 2: cine ya seleccionado → no hace nada.
     * Flujo: navega a Horarios → toca el chip "Selecciona uno o más cines" → busca → aplica.
     */
    /** CinemaValidation instrumentado (utils.PerfMetrics) — ver ensureMexicoCinemaSelectedImpl() para la lógica real. */
    public void ensureMexicoCinemaSelected() {
        utils.PerfMetrics.measure("CinemaValidation", this::ensureMexicoCinemaSelectedImpl);
    }

    private void ensureMexicoCinemaSelectedImpl() {
        log.info("[CinemasHelper][MxCinema] Verificando cine seleccionado para México...");
        long t0 = System.currentTimeMillis();

        // Navegar a Horarios donde vive el chip de selección de cines
        tapIfPresent(TAB_HORARIOS);
        sleep(600);

        if (isMexicoCinemaPreSelected()) {
            utils.PerfMetrics.attempt("CinemaValidation", 1, "cine-ya-seleccionado",
                    System.currentTimeMillis() - t0, "OK");
            log.info("[CinemasHelper][MxCinema] Escenario 2: cine ya seleccionado -> continua con tests.");
            return;
        }

        log.info("[CinemasHelper][MxCinema] Escenario 1: sin cine seleccionado -> leyendo config...");
        String cinemaName = readMexicoCinemaFromConfig();

        if (cinemaName == null || cinemaName.isBlank()) {
            log.warn("[CinemasHelper][MxCinema] Archivo '{}' vacío o no encontrado -> se omite selección.",
                    MEXICO_CINEMA_CONFIG);
            utils.PerfMetrics.attempt("CinemaValidation", 1, "sin-config",
                    System.currentTimeMillis() - t0, "SKIP");
            return;
        }

        log.info("[CinemasHelper][MxCinema] Seleccionando cine: '{}'", cinemaName);
        try {
            tapCinesChipToOpenSelector();
            waitSelectorScreenOrThrow();
            typeInSearchBoxULTRA(cinemaName);
            pickCinemaFromResults(cinemaName);
            acceptAlertsIfPresent();
            clickAplicarSeleccion();
            acceptAlertsIfPresent();
            log.info("[CinemasHelper][MxCinema] Cine '{}' seleccionado exitosamente.", cinemaName);
            utils.PerfMetrics.attempt("CinemaValidation", 1, cinemaName, System.currentTimeMillis() - t0, "OK");
        } catch (Exception e) {
            log.error("[CinemasHelper][MxCinema] Error al seleccionar cine '{}': {}", cinemaName, e.getMessage());
            utils.PerfMetrics.attempt("CinemaValidation", 1, cinemaName, System.currentTimeMillis() - t0, "FAIL");
        }
    }

    /** Toca el chip "Selecciona uno o más cines" en la tab de Horarios para abrir el selector. */
    private void tapCinesChipToOpenSelector() {
        if (isSelectorOpen()) return;

        for (int i = 1; i <= 4; i++) {
            log.debug("[CinemasHelper][MxCinema] Tap chip cines intento {}", i);

            // 1) Ancestro clickable del chip (android.view.View clickable=true)
            if (tapIfPresent(CINES_CHIP_CLICKABLE)) { sleep(700); }
            if (isSelectorOpen()) return;

            // 2) Tap directo al TextView del chip
            if (tapIfPresent(CINES_SIN_SELECCION)) { sleep(700); }
            if (isSelectorOpen()) return;

            // 3) UiAutomator fallback (Android) / XPath (iOS)
            if (!isIOS()) {
                try {
                    WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"Selecciona uno o\")"));
                    tapCenter(el);
                    sleep(700);
                } catch (Exception ignored) {}
            }
            if (isSelectorOpen()) return;

            sleep(400);
        }
    }

    /** Devuelve true si ya hay un cine seleccionado (chip "Selecciona uno o más cines" NO visible). */
    public boolean isMexicoCinemaPreSelected() {
        boolean sinSeleccion = exists(CINES_SIN_SELECCION, 1);
        log.debug("[CinemasHelper][MxCinema] isCinemaPreSelected: sinSeleccionVisible={} -> preSelected={}",
                sinSeleccion, !sinSeleccion);
        return !sinSeleccion;
    }

    /** Lee la primera línea no vacía y no comentada del archivo de config. */
    private String readMexicoCinemaFromConfig() {
        // 1. Directorio de trabajo del ejecutable (build/launch4j/ cuando corre el .exe)
        java.io.File exeDir = new java.io.File(System.getProperty("user.dir"), MEXICO_CINEMA_CONFIG);
        log.debug("[CinemasHelper][MxCinema] Buscando config en: {}", exeDir.getAbsolutePath());
        if (exeDir.exists()) {
            String name = readFirstNonBlankLine(exeDir);
            if (name != null) {
                log.info("[CinemasHelper][MxCinema] Cine leído desde directorio launcher: '{}'", name);
                return name;
            }
        }

        // 2. Classpath (cuando corre desde IDE con el archivo en src/test/resources/)
        try (java.io.InputStream is = CinemasHelper.class.getClassLoader()
                .getResourceAsStream(MEXICO_CINEMA_CONFIG)) {
            if (is != null) {
                String raw = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String name = firstNonBlankLine(raw);
                if (name != null) {
                    log.info("[CinemasHelper][MxCinema] Cine leído desde classpath: '{}'", name);
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("[CinemasHelper][MxCinema] Error leyendo classpath resource: {}", e.getMessage());
        }

        log.warn("[CinemasHelper][MxCinema] No se encontró '{}' en '{}' ni en classpath.",
                MEXICO_CINEMA_CONFIG, System.getProperty("user.dir"));
        return null;
    }

    private String readFirstNonBlankLine(java.io.File file) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader(file, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) return line;
            }
        } catch (Exception e) {
            log.warn("[CinemasHelper][MxCinema] Error leyendo archivo {}: {}", file.getAbsolutePath(), e.getMessage());
        }
        return null;
    }

    private String firstNonBlankLine(String content) {
        if (content == null) return null;
        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) return line;
        }
        return null;
    }

}
