package pages.common;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.appium.java_client.touch.WaitOptions.waitOptions;
import static io.appium.java_client.touch.offset.PointOption.point;

public class CinemasHelper extends BasePage {

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
            By.xpath("//*[contains(@text,'Inicia sesión') or contains(@text,'Inicia sesion')]");
    private static final By CLUB_LOGIN_LOGO =
            By.xpath("//*[contains(@text,'CLUB') and (contains(@text,'cinépolis') or contains(@text,'cinepolis'))]");
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

    private static final By BTN_APLICAR_SELECCION =
            By.xpath("//android.widget.TextView[@text='Aplicar selección' or @text='Aplicar seleccion']");

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

    public void ensureCinemaSelectedFromAlimentos(String targetCinema) {
        System.out.println("[CinemasHelper] ensureCinemaSelectedFromAlimentos -> " + targetCinema);

        goToAlimentosTab();
        openSelectorFromAlimentosIfNeeded();
        waitSelectorScreenOrThrow();

        // ✅ Tu escritura robusta (misma lógica, solo más compatible con Compose/acentos)
        typeInSearchBoxULTRA(targetCinema);

        pickCinemaFromResults(targetCinema);
        acceptAlertsIfPresent();

        clickIfPresent(BTN_APLICAR_SELECCION);
        acceptAlertsIfPresent();

        // ✅ Si después de aplicar aparece Club Cinépolis, ciérralo y regresa a Alimentos
        dismissClubLoginIfPresent();
        goToAlimentosTab();

        System.out.println("[CinemasHelper] Cine seleccionado OK -> " + targetCinema);
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
            System.out.println("[CinemasHelper] WARNING: No se logró cambiar a Alimentos; aún estamos en Películas. Forzando tap final...");
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
            System.out.println("[CinemasHelper] Tap icono Cines intento: " + i);

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
            System.out.println("[CinemasHelper] typeInSearchBoxULTRA intento " + attempt);

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
                    System.out.println("[CinemasHelper] Texto escrito OK (ADB safe)");
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
                        System.out.println("[CinemasHelper] Texto escrito OK (sendKeys)");
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("[CinemasHelper] fallback sendKeys falló: " + e.getMessage());
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
                            System.out.println("[CinemasHelper] Texto escrito OK (clipboard paste)");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[CinemasHelper] clipboard fallback falló: " + e.getMessage());
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
            System.out.println("[CinemasHelper] adbClearText warning: " + e.getMessage());
        }
    }

    private boolean typeViaAdbInput(String text) {
        try {
            // ✅ ADB input crashea con algunos unicode/acentos -> siempre sin acentos
            String safe = stripAccents(text);
            mobileShell("input", new String[]{"text", escapeForAdbInput(safe)});
            return true;
        } catch (Exception e) {
            System.out.println("[CinemasHelper] ADB input falló: " + e.getMessage());
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
            System.out.println("[CinemasHelper] setClipboardText falló: " + e.getMessage());
            return false;
        }
    }

    private boolean pasteFromClipboardKeyEvent() {
        try {
            // KEYCODE_PASTE = 279 (no siempre disponible, pero en muchos Samsung sí)
            mobileShell("input", new String[]{"keyevent", "279"});
            return true;
        } catch (Exception e) {
            System.out.println("[CinemasHelper] paste keyevent 279 falló: " + e.getMessage());
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

    private void acceptAlertsIfPresent() {
        long end = System.currentTimeMillis() + 4500;

        while (System.currentTimeMillis() < end) {

            if (isVisibleNow(ALERT_CAMBIAR_CIUDAD_TITLE)) {
                System.out.println("[CinemasHelper] Alerta ciudad -> Aceptar (last())");
                if (!tapIfPresent(ALERT_ACEPTAR_LAST)) clickIfPresent(ALERT_ACEPTAR_LAST);
                sleep(450);
                return;
            }

            if (isVisibleNow(ALERT_CAMBIAR_CINE_TITLE)) {
                System.out.println("[CinemasHelper] Alerta cambiar cine -> Sí, cambiar de cine (CLICKABLE ANCESTOR)");

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

                new TouchAction(driver)
                        .press(point(cx, cy))
                        .waitAction(waitOptions(Duration.ofMillis(140)))
                        .release()
                        .perform();
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

    public void tapCenter(WebElement el) {
        try {
            int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
            int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);

            new TouchAction(driver)
                    .press(point(cx, cy))
                    .waitAction(waitOptions(Duration.ofMillis(140)))
                    .release()
                    .perform();
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
    private boolean isClubLoginVisible() {
        return exists(CLUB_LOGIN_TITLE, 1) || exists(CLUB_LOGIN_LOGO, 1);
    }

    /**
     * Cierra la pantalla de Club Cinépolis SIN usar BACK del sistema (porque puede cerrar la app).
     * Usa: botón back de la UI (si existe) y fallback por coordenadas (top-left).
     */
    private boolean dismissClubLoginIfPresent() {
        if (!isClubLoginVisible()) return false;

        System.out.println("[CinemasHelper] Detectada pantalla Club Cinépolis. Intentando cerrarla...");

        for (int i = 1; i <= 3; i++) {
            try {
                // 1) Intento más seguro: tap al botón BACK real (por bounds del elemento)
                if (tapBackFromClubUI()) {
                    sleep(700);
                }

                // 2) Intento por botón con content-desc (Atrás / Navigate up)
                if (isClubLoginVisible() && tapIfPresent(CLUB_BACK_BUTTON_A11Y)) {
                    sleep(700);
                }

                // ⚠️ OJO: evitamos tap "a ciegas" por coordenadas porque puede pegarle a Notificaciones en Cartelera
                // (si tu app re-renderiza rápido y la pantalla ya cambió).

                if (!isClubLoginVisible()) {
                    System.out.println("[CinemasHelper] Pantalla Club cerrada OK.");
                    return true;
                }
            } catch (Exception e) {
                // no reventar tu flujo
            }
        }

        System.out.println("[CinemasHelper] No se pudo cerrar Club Cinépolis en reintentos; se continúa flujo.");
        return false;
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

                    // sanity: debe estar en el cuadrante superior-izquierdo
                    org.openqa.selenium.Dimension d = driver.manage().window().getSize();
                    if (cx > d.width * 0.35 || cy > d.height * 0.25) {
                        continue;
                    }

                    new TouchAction(driver)
                            .tap(point(cx, cy))
                            .waitAction(waitOptions(Duration.ofMillis(120)))
                            .perform();
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

}
