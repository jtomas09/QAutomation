package pages.common;

import io.appium.java_client.android.AndroidDriver;
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

    // ==========================
    // ✅ TAB ALIMENTOS
    // ==========================
    private static final By TAB_ALIMENTOS =
            By.xpath("//android.widget.TextView[@text='Alimentos']");

    // ✅ Detectores para evitar quedarnos en Películas (Cartelera/Horarios)
    private static final By TAB_CARTELERA = By.xpath("//android.widget.TextView[@text='Cartelera']");
    private static final By TAB_HORARIOS  = By.xpath("//android.widget.TextView[@text='Horarios']");
    // ✅ Tab 'Alimentos' como seleccionado (cuando el bottom nav expone selected/checked)
    private static final By TAB_ALIMENTOS_SELECTED = By.xpath(
            "//android.widget.TextView[@text='Alimentos' and (@selected='true' or @checked='true')]" +
                    " | //android.widget.TextView[@text='Alimentos']/..[@selected='true' or @checked='true']");


    // ==========================
    // ✅ GUARD: PANTALLA CLUB CINÉPOLIS (LOGIN)
    // ==========================
    private static final By CLUB_LOGIN_TITLE =
            By.xpath("//*[contains(@text,'Inicia sesi\u00f3n') or contains(@text,'Inicia sesion')]");
    private static final By CLUB_LOGIN_LOGO =
            By.xpath("//*[contains(@text,'CLUB') and (contains(@text,'cin\u00e9polis') or contains(@text,'cinepolis'))]");
    // Flecha/back de la pantalla (puede variar por device)
    private static final By CLUB_BACK_BUTTON_A11Y =
            By.xpath("//android.widget.ImageButton[contains(@content-desc,'Atrás') or contains(@content-desc,'Atras') or contains(@content-desc,'Navigate up')]" +
                    " | //android.widget.ImageView[contains(@content-desc,'Atrás') or contains(@content-desc,'Atras') or contains(@content-desc,'Navigate up')]");

    // En tu inspector, esta pantalla trae un android.widget.Button (instance(0)) que representa la flecha/back
    private static final By CLUB_BACK_BUTTON_UIAUTO =
            AppiumBy.androidUIAutomator("new UiSelector().className(\"android.widget.Button\").instance(0)");

    // Fallback xpath simple (primer botón en la pantalla)
    private static final By CLUB_BACK_BUTTON_XPATH =
            By.xpath("(//android.widget.Button)[1]");
    // Tab alterno por content-desc (bottom nav en algunos builds)
    private static final By TAB_ALIMENTOS_ALT =
            By.xpath("//*[@content-desc='Alimentos' or @text='Alimentos']");

    // ==========================
    // ✅ ICONO/CHIP REAL DE CINES EN HEADER (Compose)
    // ==========================
    private static final By CINES_ICON_VIEW =
            By.xpath("//android.view.View[contains(@content-desc,'Selecciona uno o más cines') or contains(@content-desc,'cines') or contains(@content-desc,'Cines')]");

    private static final By CINES_TEXT =
            By.xpath("//android.widget.TextView[@text=\"Cines\"]");

    private static final By CINES_TEXT_PARENT =
            By.xpath("//android.widget.TextView[@text=\"Cines\"]/ancestor::*[self::android.view.View or self::android.view.ViewGroup][1]");

    // Pantalla "Elige un cine para tus alimentos"
    private static final By BTN_SELECCIONAR_UBICACION_TEXT =
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]");

    private static final By BTN_SELECCIONAR_UBICACION_BUTTON_NEAR_TEXT =
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]/parent::*/android.widget.Button");

    private static final By BTN_SELECCIONAR_UBICACION_CLICKABLE_ANCESTOR =
            By.xpath("(//android.widget.TextView[@text='Seleccionar ubicación' or @text='Seleccionar ubicacion'])[1]/ancestor::*[@clickable='true'][1]");

    // ==========================
    // ✅ Selector de cines
    // ==========================
    private static final By TITLE_SELECCIONAR_CINES =
            By.xpath("//android.widget.TextView[@text='Seleccionar cines']");

    private static final By SEARCH_HINT =
            By.xpath("//android.widget.TextView[@text='Busca tu ciudad o tu cine' or @text='Escribe tu ciudad o cine' or @text='Escribe tu ciudad o cine']");

    private static final By SEARCH_PARENT_ROUNDED =
            By.xpath("//android.view.View[@content-desc='Rounded.Search']");

    private static final By SEARCH_INPUT =
            By.xpath("//android.widget.EditText");

    private static final By SEARCH_INNER_VIEW =
            By.xpath("//android.widget.EditText/android.view.View[2]");

    // TextView inside button (clickable=false) — used only to find the real Button
    private static final By BTN_APLICAR_SELECCION_LABEL =
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']");

    // Sibling Button right next to the label (Compose layout)
    private static final By BTN_APLICAR_SELECCION_SIBLING =
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']"
                   + "/following-sibling::android.widget.Button"
                   + " | //android.widget.TextView[@text='Aplicar seleccion']"
                   + "/following-sibling::android.widget.Button");

    // Clickable ancestor of the label (catches any wrapper View)
    private static final By BTN_APLICAR_SELECCION_ANCESTOR =
            By.xpath("(//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion'])"
                   + "/ancestor::*[@clickable='true'][1]");

    // UiAutomator fallback — finds any clickable element whose text matches
    private static final String UA_APLICAR_SELECCION =
            "new UiSelector().clickable(true).textContains(\"Aplicar\")";

    // kept for backward compat (label-only, used as last resort)
    private static final By BTN_APLICAR_SELECCION =
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']");

    // ==========================
    // ✅ DETECCIÓN CINE NO SELECCIONADO (México)
    // ==========================
    private static final By CINES_SIN_SELECCION = By.xpath(
            "//android.widget.TextView[@text='Selecciona uno o más cines']" +
            " | //android.widget.TextView[contains(@text,'Selecciona uno o m')]");

    // Ancestro clickable del chip (android.view.View clickable=true que envuelve el TextView)
    private static final By CINES_CHIP_CLICKABLE = By.xpath(
            "//android.widget.TextView[@text='Selecciona uno o más cines']/ancestor::android.view.View[@clickable='true'][1]" +
            " | //android.widget.TextView[contains(@text,'Selecciona uno o m')]/ancestor::android.view.View[@clickable='true'][1]");

    private static final String MEXICO_CINEMA_CONFIG = "mexico-cinema.txt";

    // ==========================
    // ✅ POPUP CAMBIO DE ZONA/UBICACIÓN (aparece al inicio, no siempre)
    // ==========================
    private static final By POPUP_ZONA_DETECTION = By.xpath(
            "//*[contains(@text,'lejos de') or contains(@text,'cambiar tu cartelera') " +
            "or contains(@text,'cambiar la cartelera') or contains(@text,'Cambiar zona')]");

    private static final By BTN_NO_CAMBIAR = By.xpath(
            "//android.widget.TextView[@text='No cambiar']" +
            " | //android.widget.Button[@text='No cambiar']");

    // ==========================
    // ✅ Alertas
    // ==========================
    private static final By ALERT_CAMBIAR_CIUDAD_TITLE =
            By.xpath("//android.widget.TextView[contains(@text,'¿Quieres cambiar la ciudad') or contains(@text,'Quieres cambiar la ciudad')]");

    private static final By ALERT_ACEPTAR_LAST =
            By.xpath("(//android.widget.TextView[@text='Aceptar'])[last()]");

    private static final By ALERT_CAMBIAR_CINE_TITLE =
            By.xpath("//android.widget.TextView[contains(@text,'¿Estás seguro que deseas cambiar de cine') or contains(@text,'Estas seguro que deseas cambiar de cine')]");

    private static final By BTN_SI_CAMBIAR_CINE_TEXT =
            By.xpath("//android.widget.TextView[contains(@text,'Sí, cambiar de cine') or contains(@text,'Si, cambiar de cine') or contains(@text,'cambiar de cine')]");

    private static final By BTN_SI_CAMBIAR_CINE_CLICKABLE_ANCESTOR =
            By.xpath("(//android.widget.TextView[contains(@text,'Sí, cambiar de cine') or contains(@text,'Si, cambiar de cine')])[1]/ancestor::*[@clickable='true'][1]");

    private static final By BTN_SI_CAMBIAR_CINE_BUTTON_ABS =
            By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.widget.Button");

    private static final By BTN_MODAL_ANY_BUTTON =
            By.xpath("(//android.widget.Button)[last()]");

    public CinemasHelper(AndroidDriver driver) {
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
        // 3. UiAutomator: clickable + texto "Aplicar"
        try {
            WebElement btn = driver.findElement(AppiumBy.androidUIAutomator(UA_APLICAR_SELECCION));
            tapCenter(btn);
            log.info("[CinemasHelper] Aplicar selección → UiAutomator OK");
            return true;
        } catch (Exception ignored) {}
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
        log.info("[CinemasHelper] ensureCinemaSelectedFromAlimentos -> '{}'", targetCinema);

        // Track cinema for PDF and Allure reporting
        TestSteps.setCinema(targetCinema);
        try { Allure.label("cinema", targetCinema); } catch (Exception ignored) {}

        // Navegar a Alimentos primero (desde cualquier pantalla) para poder leer el cine actual
        goToAlimentosTab();

        // Validar si el cine ya está correctamente seleccionado antes de abrir el selector
        boolean cinemaSelected = !isVisibleNow(CINES_SIN_SELECCION);
        if (cinemaSelected) {
            // Intento 1: leer el nombre del cine del chip
            String currentCinema = getCurrentCinemaName();
            if (currentCinema != null && !currentCinema.isBlank()) {
                log.info("[CinemasHelper] Cine actual detectado: '{}'", currentCinema);
                if (cinemaMatches(currentCinema, targetCinema)) {
                    log.info("[CinemasHelper] El cine ya coincide con '{}' — continuando flujo.", targetCinema);
                    return;
                }
                log.info("[CinemasHelper] Cambiando cine de '{}' a '{}'", currentCinema, targetCinema);
            } else {
                // Intento 2: verificar si el texto del cine objetivo ya es visible en el chip
                String xpathTarget = "//android.widget.TextView[contains(@text,'"
                        + escapeXpath(targetCinema) + "')]"
                        + " | //android.view.View[contains(@content-desc,'"
                        + escapeXpath(targetCinema) + "')]";
                if (isVisibleNow(By.xpath(xpathTarget))) {
                    log.info("[CinemasHelper] Cine '{}' ya visible en pantalla — continuando flujo.", targetCinema);
                    return;
                }
                log.info("[CinemasHelper] Hay cine seleccionado pero no se pudo leer su nombre — " +
                         "procediendo con selección de '{}'.", targetCinema);
            }
        } else {
            log.info("[CinemasHelper] No hay cine seleccionado — configurando: '{}'", targetCinema);
        }

        // El cine no coincide o no hay uno seleccionado — abrir selector y seleccionar
        openSelectorFromAlimentosIfNeeded();
        waitSelectorScreenOrThrow();

        typeInSearchBoxULTRA(targetCinema);

        pickCinemaFromResults(targetCinema);
        acceptAlertsIfPresent();

        clickAplicarSeleccion();
        acceptAlertsIfPresent();

        dismissClubLoginIfPresent();
        goToAlimentosTab();

        log.info("[CinemasHelper] Cine configurado correctamente -> '{}'", targetCinema);
    }

    /**
     * Intenta leer el nombre del cine actualmente seleccionado desde el chip del menú de alimentos.
     * El chip muestra el nombre del cine como TextView hermano o ancestro de la etiqueta "Cines".
     */
    private String getCurrentCinemaName() {
        // 1) TextView hermano anterior o posterior a la etiqueta "Cines"
        try {
            List<WebElement> siblings = driver.findElements(By.xpath(
                "//android.widget.TextView[@text='Cines']/preceding-sibling::android.widget.TextView" +
                " | //android.widget.TextView[@text='Cines']/following-sibling::android.widget.TextView"
            ));
            for (WebElement el : siblings) {
                try {
                    String t = el.getText();
                    if (t != null && !t.trim().isEmpty() && !t.equals("Cines")) return t.trim();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 2) content-desc del contenedor padre del chip de cines
        try {
            WebElement cinesLabel = firstOrNull(By.xpath("//android.widget.TextView[@text='Cines']"));
            if (cinesLabel != null) {
                WebElement parent = cinesLabel.findElement(By.xpath(".."));
                String desc = parent.getAttribute("content-desc");
                if (desc != null && !desc.isBlank()) {
                    String cleaned = desc.replace("Cines", "").replace(",", "").trim();
                    if (!cleaned.isEmpty()) return cleaned;
                }
            }
        } catch (Exception ignored) {}

        // 3) Cualquier TextView visible dentro del chip de cines (excluye "Cines")
        try {
            List<WebElement> candidates = driver.findElements(By.xpath(
                "//android.widget.TextView[@text='Cines']/ancestor::android.view.View[1]" +
                "//android.widget.TextView[@text != 'Cines']"
            ));
            for (WebElement el : candidates) {
                try {
                    String t = el.getText();
                    if (t != null && !t.trim().isEmpty()) return t.trim();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    /** Compara dos nombres de cine ignorando acentos, mayúsculas y espacios extra. */
    private boolean cinemaMatches(String current, String target) {
        if (current == null || target == null) return false;
        return normalize(stripAccents(current)).contains(normalize(stripAccents(target))) ||
               normalize(stripAccents(target)).contains(normalize(stripAccents(current)));
    }

    private void goToAlimentosTab() {
        // ✅ Antes de navegar, quita pantalla Club si apareció
        dismissClubLoginIfPresent();

        // ✅ Reintentos para garantizar que realmente quedamos en Alimentos
        for (int i = 1; i <= 6; i++) {
            if (clickIfPresent(TAB_ALIMENTOS) || tapIfPresent(TAB_ALIMENTOS) || tapIfPresent(TAB_ALIMENTOS_ALT)) {
                sleep(750);
            } else {
                // a veces el tab no es visible inmediatamente
                sleep(500);
            }

            // 🔒 Intento duro: buscar el item del bottom-nav por UiSelector y tap (por si el xpath no pegó)
            try {
                WebElement alimentos = driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"Alimentos\")"
                ));
                tapCenter(alimentos);
                sleep(650);
            } catch (Exception ignored) {}

            // ✅ Si ya vemos el header/elementos de Alimentos, salimos
            if (isOnAlimentosHome()) return;

            // si falló, espera un poco y reintenta
            sleep(650);
        }

        // 🚨 Si aún vemos Cartelera/Horarios, NO logramos cambiar a Alimentos. Reintentamos una vez más con un tap fuerte.
        if (isVisibleNow(TAB_CARTELERA) || isVisibleNow(TAB_HORARIOS)) {
            log.warn("[CinemasHelper] WARNING: No se logró cambiar a Alimentos; aún estamos en Películas. Forzando tap final...");
            try {
                WebElement alimentos = driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"Alimentos\")"
                ));
                tapCenter(alimentos);
                sleep(900);
            } catch (Exception ignored) {}
        }
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

            try {
                WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiSelector().text(\"Cines\")"
                ));
                tapCenter(el);
            } catch (Exception ignored) {}

            sleep(550);
            if (isAfterCinesTapScreenOpen()) return;
        }
    }

    private boolean isSelectorOpen() {
        return isVisibleNow(TITLE_SELECCIONAR_CINES) || isVisibleNow(SEARCH_HINT);
    }

    private boolean isChangeCinemaAlertOpen() {
        return isVisibleNow(ALERT_CAMBIAR_CINE_TITLE);
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
            driver.setClipboardText(text);
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
        By resultAny = By.xpath("//android.widget.TextView[contains(@text,'" + w1 + "')]");
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

            try { t = el.getAttribute("content-desc"); } catch (Exception ignored) {}
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void pickCinemaFromResults(String targetCinema) {
        By exact = By.xpath("//android.widget.TextView[@text='" + targetCinema + "']");
        if (clickIfPresent(exact) || tapIfPresent(exact)) return;

        By contains = By.xpath("//android.widget.TextView[contains(@text,'" + escapeXpath(targetCinema) + "')]");
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
        try {
            log.info("[CinemasHelper][ClubGuard] ENTER where={}", where);

            // Espera única de 600ms para que la pantalla de Club aparezca si va a aparecer
            safeSleep(600);
            boolean visible = isClubLoginVisible();

            if (!visible) {
                log.debug("[CinemasHelper][ClubGuard] No visible -> SKIP where={}", where);
                return;
            }

            log.info("[CinemasHelper][ClubGuard] Visible -> attempting dismiss...");
            boolean closedReturn = dismissClubLoginIfPresent();

            // Solo chequeamos stillVisible si dismissClubLoginIfPresent() falló (return false)
            // para evitar una consulta de elemento lenta cuando ya sabemos que cerró.
            boolean stillVisible = !closedReturn && isClubLoginVisible();

            log.info("[CinemasHelper][ClubGuard] closedReturn={} stillVisible={}", closedReturn, stillVisible);

            if (stillVisible) {
                log.warn("[CinemasHelper][ClubGuard] STILL visible -> last resort navigate.back()");
                try {
                    driver.navigate().back();
                    safeSleep(700);
                } catch (Exception ignored) {}
                log.debug("[CinemasHelper][ClubGuard] after last resort stillVisible={}", isClubLoginVisible());
            }

            log.info("[CinemasHelper][ClubGuard] EXIT where={}", where);

        } catch (Exception e) {
            log.error("[CinemasHelper][ClubGuard] ERROR where={} msg={}", where, e.getMessage());
        }
    }

    private static void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
    private void acceptAlertsIfPresent() {
        long end = System.currentTimeMillis() + 4500;

        while (System.currentTimeMillis() < end) {

            if (isVisibleNow(ALERT_CAMBIAR_CIUDAD_TITLE)) {
                log.info("[CinemasHelper] Alerta ciudad -> Aceptar (last())");
                if (!tapIfPresent(ALERT_ACEPTAR_LAST)) clickIfPresent(ALERT_ACEPTAR_LAST);
                sleep(450);
                return;
            }

            if (isVisibleNow(ALERT_CAMBIAR_CINE_TITLE)) {
                log.info("[CinemasHelper] Alerta cambiar cine -> Sí, cambiar de cine (CLICKABLE ANCESTOR)");

                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_CLICKABLE_ANCESTOR)) { sleep(650); return; }
                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_TEXT)) { sleep(650); return; }
                if (tapIfPresent(BTN_SI_CAMBIAR_CINE_BUTTON_ABS)) { sleep(650); return; }
                if (tapIfPresent(BTN_MODAL_ANY_BUTTON)) { sleep(650); return; }

                try {
                    WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                            "new UiSelector().text(\"Sí, cambiar de cine\")"
                    ));
                    tapCenter(el);
                } catch (Exception ignored) {}

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

            return false;
        } catch (Exception e) {
            return false;
        }
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

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String escapeXpath(String s) {
        return (s == null) ? "" : s.replace("'", "");
    }


    // ==========================
    // ✅ CLUB CINÉPOLIS GUARD
    // ==========================

    /** Verificación rápida (sin espera) — usar solo cuando ya esperamos antes de llamar. */
    public boolean isClubLoginVisible() {
        // El menú de Alimentos muestra "Club Cinépolis" como sección y "Ingresa tu folio" como campo.
        // Si estamos en esa pantalla, no es la pantalla de login de Club → salir inmediatamente.
        if (isVisibleNow(By.xpath("//android.widget.TextView[@text='Ingresa tu folio']"))) return false;

        if (isVisibleNow(CLUB_LOGIN_TITLE)) return true;
        if (isVisibleNow(CLUB_LOGIN_LOGO))  return true;

        // Fallbacks: patrones sin acento para resistir cambios de encoding o de texto en la app
        try {
            List<WebElement> els = driver.findElements(By.xpath(
                "//*[contains(@text,'Inicia sesi')" +
                " or contains(@text,'CLUB Cin')" +
                " or contains(@text,'Club Cin')" +
                " or contains(@text,'Correo electr')" +
                " or contains(@text,'Contrase')]"
            ));
            for (WebElement el : els) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Cierra la pantalla de Club Cinépolis.
     * Intenta: botón back UI → content-desc → navigate().back().
     * navigate().back() es seguro: Club tiene su propia Activity y back vuelve al main sin cerrar la app.
     */
    public boolean dismissClubLoginIfPresent() {
        if (!isClubLoginVisible()) return false;

        log.info("[CinemasHelper] Detectada pantalla Club Cinépolis. Intentando cerrarla...");

        for (int i = 1; i <= 3; i++) {
            try {
                // 1) Tap botón back real (por bounds del elemento en la UI)
                tapBackFromClubUI();
                sleep(400);
                if (!isClubLoginVisible()) {
                    log.info("[CinemasHelper] Pantalla Club cerrada OK (tapBackFromClubUI).");
                    return true;
                }

                // 2) Tap por content-desc (Atrás / Navigate up)
                tapIfPresent(CLUB_BACK_BUTTON_A11Y);
                sleep(400);
                if (!isClubLoginVisible()) {
                    log.info("[CinemasHelper] Pantalla Club cerrada OK (A11Y).");
                    return true;
                }

                // 3) navigate().back() — tecla Back del sistema; Club tiene su propia Activity
                //    así que vuelve al main sin cerrar la app.
                try {
                    driver.navigate().back();
                    sleep(700);
                } catch (Exception ignored) {}
                if (!isClubLoginVisible()) {
                    log.info("[CinemasHelper] Pantalla Club cerrada OK (navigate.back).");
                    return true;
                }

            } catch (Exception e) {
                // no reventar el flujo
            }
        }

        log.warn("[CinemasHelper] No se pudo cerrar Club Cinépolis en reintentos; se continúa flujo.");
        return false;
    }
    private boolean isMarioPromoVisible() {
        try {
            // ✅ SOLO si existe el CTA específico de la promo
            return !driver.findElements(By.xpath(
                    "//*[normalize-space(@text)='CONSULTA CARTELERA' or contains(@text,'CONSULTA CARTELERA')]"
            )).isEmpty();
        } catch (Exception e) {
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
            List<WebElement> ctas = driver.findElements(By.xpath(
                    "//*[contains(@text,'CONSULTA CARTELERA')]"
            ));
            if (!ctas.isEmpty()) {
                ctas.get(0).click();
                Thread.sleep(800);
                return;
            }

            // Intento 2: back (fallback)
            driver.navigate().back();
            Thread.sleep(600);

        } catch (Exception e) {
            log.warn("[CinemasHelper] No se pudo cerrar promo Mario (safe ignore)");
        }
    }
    public void dismissTransientPromosGuard() {
        dismissTransientPromosGuard("unknown");
    }

    // Guard con logs (Club + Promos tipo Mario + Popup zona/ubicación)
    // Corre en loop hasta que el bottom nav sea accesible o se alcancen los intentos máximos.
    public void dismissTransientPromosGuard(String where) {
        log.info("[CinemasHelper][PromosGuard] ENTER where={}", where);

        for (int pass = 1; pass <= 5; pass++) {
            boolean dismissed = false;

            try {
                if (isClubLoginVisible()) {
                    log.info("[CinemasHelper][PromosGuard] pass={} Club visible -> dismiss", pass);
                    dismissClubLoginGuard(where + ":club");
                    dismissed = true;
                }
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Club guard error: {}", e.getMessage());
            }

            try {
                if (isMarioPromoVisible()) {
                    log.info("[CinemasHelper][PromosGuard] pass={} Mario visible -> dismiss", pass);
                    dismissMarioPromoIfPresent();
                    dismissed = true;
                }
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Mario guard error: {}", e.getMessage());
            }

            try {
                if (isLocationChangePopupVisible()) {
                    log.info("[CinemasHelper][PromosGuard] pass={} Zona visible -> dismiss", pass);
                    dismissLocationChangePopupIfPresent(where + ":zona");
                    dismissed = true;
                    safeSleep(700); // espera a que la app asiente tras cerrar el popup de zona
                }
            } catch (Exception e) {
                log.error("[CinemasHelper][PromosGuard] Zona guard error: {}", e.getMessage());
            }

            // Si el bottom nav ya es visible, el guard terminó
            if (isMainNavVisible()) {
                log.info("[CinemasHelper][PromosGuard] Main nav visible, EXIT pass={} where={}", pass, where);
                return;
            }

            // Si no se cerró nada y el nav aún no aparece, intento genérico de dismiss
            if (!dismissed) {
                log.debug("[CinemasHelper][PromosGuard] pass={} nada cerrado, prueba dismiss genérico", pass);
                tryGenericOverlayDismiss();
            }

            safeSleep(500);
        }

        log.warn("[CinemasHelper][PromosGuard] Max passes alcanzados, nav={} where={}", isMainNavVisible(), where);
        log.info("[CinemasHelper][PromosGuard] EXIT where={}", where);
    }

    /** Devuelve true si el bottom nav principal es accesible (Cartelera o Alimentos visible). */
    private boolean isMainNavVisible() {
        return isVisibleNow(TAB_CARTELERA)
            || isVisibleNow(TAB_HORARIOS)
            || isVisibleNow(TAB_ALIMENTOS)
            || isVisibleNow(TAB_ALIMENTOS_ALT);
    }

    /** Intenta cerrar cualquier overlay desconocido usando patrones comunes de dismiss. */
    private void tryGenericOverlayDismiss() {
        // Botones de cierre más comunes en promos/modales de Cinépolis
        By[] dismissLocators = {
            By.xpath("//*[@content-desc='Close' or @content-desc='Cerrar' or @content-desc='close']"),
            By.xpath("//android.widget.Button[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.TextView[@text='Cerrar' or @text='No gracias' or @text='Omitir' or @text='Saltar']"),
            By.xpath("//android.widget.ImageButton[@content-desc='Atrás' or @content-desc='Atras' or @content-desc='Navigate up']"),
        };
        for (By loc : dismissLocators) {
            if (tapIfPresent(loc)) {
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
        try {
            List<WebElement> els = driver.findElements(POPUP_ZONA_DETECTION);
            for (WebElement el : els) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void dismissLocationChangePopupIfPresent(String where) {
        if (!isLocationChangePopupVisible()) {
            log.debug("[CinemasHelper][ZonaGuard] Popup zona no visible -> SKIP where={}", where);
            return;
        }

        log.info("[CinemasHelper][ZonaGuard] Popup cambio de zona detectado -> tap 'No cambiar' where={}", where);

        for (int i = 1; i <= 3; i++) {
            // Intento 1: tap directo por locator
            if (tapIfPresent(BTN_NO_CAMBIAR)) {
                safeSleep(600);
                if (!isLocationChangePopupVisible()) {
                    log.info("[CinemasHelper][ZonaGuard] Popup cerrado OK (tapIfPresent) intento={}", i);
                    return;
                }
            }

            // Intento 2: UiAutomator (más robusto con Compose)
            try {
                WebElement btn = driver.findElement(
                        AppiumBy.androidUIAutomator("new UiSelector().text(\"No cambiar\")"));
                tapCenter(btn);
                safeSleep(600);
                if (!isLocationChangePopupVisible()) {
                    log.info("[CinemasHelper][ZonaGuard] Popup cerrado OK (UiAutomator) intento={}", i);
                    return;
                }
            } catch (Exception ignored) {}

            safeSleep(300);
        }

        log.warn("[CinemasHelper][ZonaGuard] No se pudo cerrar popup de zona; se continúa flujo.");
    }

    /**
     * Encuentra el botón back de la pantalla Club (android.widget.Button instance(0) / primer Button)
     * y hace tap al centro del elemento (sin coordenadas fijas).
     */
    private boolean tapBackFromClubUI() {
        if (!isClubLoginVisible()) return false;

        try {
            List<WebElement> candidates = driver.findElements(CLUB_BACK_BUTTON_UIAUTO);
            if (candidates == null || candidates.isEmpty()) {
                candidates = driver.findElements(CLUB_BACK_BUTTON_XPATH);
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

                    // sanity: debe estar en la mitad superior-izquierda (relaxed para Galaxy A56/Compose)
                    org.openqa.selenium.Dimension d = driver.manage().window().getSize();
                    if (cx > d.width * 0.50 || cy > d.height * 0.40) {
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

    private boolean exists(By by, int seconds) {
        try {
            if (seconds <= 0) return !driver.findElements(by).isEmpty();
            long end = System.currentTimeMillis() + (seconds * 1000L);
            while (System.currentTimeMillis() < end) {
                if (!driver.findElements(by).isEmpty()) return true;
                sleep(150);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================
    // ✅ SELECCIÓN DE CINE PARA MÉXICO (data-driven desde archivo)
    // ==========================

    /**
     * Escenario 1: "Selecciona uno o más cines" visible → no hay cine → selecciona desde config.
     * Escenario 2: cine ya seleccionado → no hace nada.
     * Flujo: navega a Horarios → toca el chip "Selecciona uno o más cines" → busca → aplica.
     */
    public void ensureMexicoCinemaSelected() {
        log.info("[CinemasHelper][MxCinema] Verificando cine seleccionado para México...");

        // Navegar a Horarios donde vive el chip de selección de cines
        tapIfPresent(TAB_HORARIOS);
        sleep(600);

        if (isMexicoCinemaPreSelected()) {
            log.info("[CinemasHelper][MxCinema] Escenario 2: cine ya seleccionado -> continua con tests.");
            return;
        }

        log.info("[CinemasHelper][MxCinema] Escenario 1: sin cine seleccionado -> leyendo config...");
        String cinemaName = readMexicoCinemaFromConfig();

        if (cinemaName == null || cinemaName.isBlank()) {
            log.warn("[CinemasHelper][MxCinema] Archivo '{}' vacío o no encontrado -> se omite selección.",
                    MEXICO_CINEMA_CONFIG);
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
        } catch (Exception e) {
            log.error("[CinemasHelper][MxCinema] Error al seleccionar cine '{}': {}", cinemaName, e.getMessage());
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

            // 3) UiAutomator fallback (ignora acentos para compatibilidad)
            try {
                WebElement el = driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"Selecciona uno o\")"));
                tapCenter(el);
                sleep(700);
            } catch (Exception ignored) {}
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
