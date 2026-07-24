package pages.common;
import org.openqa.selenium.interactions.Pause;
import org.opentest4j.TestAbortedException;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Attachment;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.PointerInput.Kind;
import org.openqa.selenium.interactions.PointerInput.MouseButton;
import org.openqa.selenium.interactions.PointerInput.Origin;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.*;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datos.Constantes;
import utils.Reintento;
import utils.Waits;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openqa.selenium.interactions.PointerInput.Kind;
import static org.openqa.selenium.interactions.PointerInput.MouseButton;
import static org.openqa.selenium.interactions.PointerInput.Origin;

public class BasePage {

    private static final Logger log = LoggerFactory.getLogger(BasePage.class);

    // ✅ No tragarse SKIPPED (TestAbortedException) en catch(Exception)
    protected static void rethrowIfAborted(Throwable t) {
        if (t instanceof org.opentest4j.TestAbortedException) {
            throw (org.opentest4j.TestAbortedException) t;
        }
    }
    protected static final AtomicInteger AGOTADOS_SKIPPED_COUNT =
            new AtomicInteger(0);
    // ====== ESTRUCTURA "BasePage" (driver final + waits centralizados) ======
    protected final AppiumDriver driver;
    protected final WebDriverWait wait;
    protected final FluentWait<AppiumDriver> fluentWait;

    // Waits custom
    protected final Waits waits;

    // ====== TIMEOUTS BASE (estilo BasePage.txt) ======
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SCROLL_WAIT_TIMEOUT  = Duration.ofSeconds(30);
    private static final Duration POLLING_INTERVAL     = Duration.ofMillis(500);

    // ====== CONSTANTES / CONFIG ======
    private static final String APP_PACKAGE = Constantes.APP_PACKAGE;
    private static final int APP_GUARD_TIMEOUT_SEC = 8;

    private static long LAST_APP_RECOVER_MS = 2L;
    private static final long APP_RECOVER_COOLDOWN_MS = 2L;

    private static final By FIRST_VISIBLE_TEXTVIEW =
            By.xpath("(//android.widget.TextView[@text and string-length(@text)>0])[1]");

    // Fast/slow swipes — valores centralizados en Constantes
    private static final int SLOW_SWIPE_WAIT_MS = Constantes.SWIPE_LENTO_MS;
    private static final int SLOW_SWIPE_SLEEP_MS = Constantes.PAUSA_POST_SWIPE_MS;
    private static final int FAST_SWIPE_WAIT_MS = Constantes.SWIPE_RAPIDO_MS;
    private static final int FAST_SWIPE_SLEEP_MS = 30;

    // Presupuestos centralizados en Constantes
    private static final int TOTAL_VERTICAL_SWIPE_BUDGET   = Constantes.SWIPES_VERTICAL_MAX;
    private static final int TOTAL_HORIZONTAL_SWIPE_BUDGET = Constantes.SWIPES_HORIZONTAL_MAX;

    public BasePage(AppiumDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
        this.fluentWait = new FluentWait<>(driver)
                .withTimeout(SCROLL_WAIT_TIMEOUT)
                .pollingEvery(POLLING_INTERVAL)
                .ignoring(NoSuchElementException.class);
        this.waits = new Waits(driver);
    }

    /** Returns true when the current session is running on an iOS device. */
    protected boolean isIOS() {
        try {
            Object cap = driver.getCapabilities().getCapability("platformName");
            return cap != null && "ios".equalsIgnoreCase(String.valueOf(cap));
        } catch (Exception e) {
            return config.DriverFactory.isIOS();
        }
    }

    // =========================================================
    // =========== PlatformLocator — capa multiplataforma =======
    // =========================================================
    //
    // Estos overloads son el único punto donde el código de flujo (page objects,
    // helpers) deja de necesitar isIOS()/if-android-if-ios: reciben un
    // PlatformLocator, lo resuelven contra la plataforma real de ESTA sesión, y
    // delegan en la implementación existente basada en By — sin duplicar ninguna
    // lógica de espera/reintento/telemetría ya presente en esos métodos.

    protected void click(PlatformLocator locator) {
        click(locator.resolve(isIOS()));
    }

    protected boolean clickIfPresent(PlatformLocator locator) {
        return clickIfPresent(locator.resolve(isIOS()));
    }

    protected boolean isVisibleQuick(PlatformLocator locator) {
        return isVisibleQuick(locator.resolve(isIOS()));
    }

    protected WebElement waitForVisibility(PlatformLocator locator) {
        return waitForVisibility(locator.resolve(isIOS()));
    }

    protected boolean tryClickIfAlreadyVisible(PlatformLocator locator, int timeoutSeconds) {
        return tryClickIfAlreadyVisible(locator.resolve(isIOS()), timeoutSeconds);
    }

    protected boolean oneShotVerticalSearch(PlatformLocator locator, int maxDownSwipes) {
        return oneShotVerticalSearch(locator.resolve(isIOS()), maxDownSwipes);
    }

    /** Click por texto visible exacto (@text en Android, @label/@name/@value en iOS). */
    protected void clickByExactText(String text) {
        click(PlatformLocator.byExactText(text));
    }

    /** Como {@link #clickByExactText(String)} pero tomando el N-ésimo resultado (1-based). */
    protected void clickByExactText(String text, int index) {
        click(PlatformLocator.byExactText(text, index));
    }

    /** Click por identificador de accesibilidad (@content-desc en Android, @name en iOS). */
    protected void clickByAccessibilityId(String id) {
        click(PlatformLocator.byAccessibilityId(id));
    }

    // =========================================================
    // =============== REINTENTOS (anti-fragilidad) =============
    // =========================================================

    /**
     * Ejecuta la acción con reintentos automáticos.
     * Los SKIP (TestAbortedException) se propagan sin reintentar.
     *
     * Ejemplo:
     *   conReintento(3, () -> click(BTN_AGREGAR));
     */
    protected void conReintento(int intentos, Runnable accion) {
        Reintento.intentar(intentos, accion);
    }

    /**
     * Ejecuta la acción con reintentos y pausa configurable entre intentos.
     */
    protected void conReintento(int intentos, long pausaMs, Runnable accion) {
        Reintento.conPausa(intentos, pausaMs).ejecutar(accion);
    }

    /**
     * Click con reintento automático. Útil para elementos que tardan en aparecer
     * o que pueden ser stale entre la localización y el clic.
     */
    protected void clickConReintento(By locator) {
        conReintento(Constantes.REINTENTOS_CLICK, () -> click(locator));
    }

    /**
     * Espera a que un elemento sea visible; reintenta si la condición no se cumple.
     * Devuelve true si el elemento fue encontrado antes de agotar los intentos.
     */
    protected boolean esperarVisible(By locator, int intentos) {
        return Reintento.hasta(intentos,
            () -> isVisibleQuick(locator));
    }

    // =========================================================
    // =============== GUARD / RECOVERY (BasePage2) =============
    // =========================================================

    protected void ensureAppIsInForegroundOrRecover() {
        try {
            driver.getSessionId();

            // iOS does not have getCurrentPackage(); skip the foreground guard there.
            if (isIOS()) return;

            String currentPackage = null;
            try {
                currentPackage = ((io.appium.java_client.android.AndroidDriver) driver).getCurrentPackage();
            } catch (Exception ignore) {}

            if (!APP_PACKAGE.equals(currentPackage)) {
                long now = System.currentTimeMillis();

                if (now - LAST_APP_RECOVER_MS < APP_RECOVER_COOLDOWN_MS) {
                    log.debug("[GUARD] Recuperación de app ignorada (cooldown activo)");
                    return;
                }

                LAST_APP_RECOVER_MS = now;

                config.DriverFactory.activateApp(driver, APP_PACKAGE);

                long end = System.currentTimeMillis() + (APP_GUARD_TIMEOUT_SEC * 1000L);

                io.appium.java_client.android.AndroidDriver androidDriver =
                    (io.appium.java_client.android.AndroidDriver) driver;

                while (System.currentTimeMillis() < end) {
                    try {
                        String p = androidDriver.getCurrentPackage();
                        if (APP_PACKAGE.equals(p)) return;
                    } catch (Exception ignore) {}

                    config.DriverFactory.terminateApp(driver, APP_PACKAGE);
                    config.DriverFactory.activateApp(driver, APP_PACKAGE);

                    sleep(400L);
                }
            }
        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new AssertionError("App no estable / posible crash.", e);
        }
    }

    // ─── Platform-aware app control wrappers ───────────────────

    protected void terminateApp(String appId) { config.DriverFactory.terminateApp(driver, appId); }
    protected void activateApp(String appId)  { config.DriverFactory.activateApp(driver, appId); }
    protected void hideKeyboard()             { config.DriverFactory.hideKeyboard(driver); }
    protected void setClipboardText(String t) { config.DriverFactory.setClipboardText(driver, t); }

    // =========================================================
    // ================== SCREENSHOTS (Allure) =================
    // =========================================================

    @Attachment(value = "Screenshot", type = "image/png")
    protected byte[] takeScreenshot() {
        try {
            return driver.getScreenshotAs(OutputType.BYTES);
        } catch (Exception ignore) {
            return new byte[0];
        }
    }

    protected void takeScreenshotOnFailure() {
        try { driver.getScreenshotAs(OutputType.BYTES); } catch (Exception ignore) {}
    }

    // =========================================================
    // =================== WAITS / VISIBILITY ==================
    // =========================================================

    protected WebElement waitForVisibility(By locator) {
        ensureAppIsInForegroundOrRecover();
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
    public void waitAndClick(By locator, int timeoutSeconds) {
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, Duration.ofSeconds(timeoutSeconds));
            // Espera a que el elemento sea visible y esté habilitado
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.click();
        } catch (Exception e) {
            // Si el clic normal falla, intentamos un clic por coordenadas (W3C Tap)
            // Esto es muy útil en elementos de Compose o listas rebeldes
            WebElement element = this.driver.findElement(locator);
            this.tapCenterW3C(element);
        }
    }
    public void verificarYAbortarSiAgotado(String nombreProducto) {
        // 1. XPath más robusto para capturar el contenedor en Compose (usando View)
        // Buscamos el ancestro que agrupa la tarjeta del producto
        String xpathEstado = "//*[@text='" + nombreProducto + "']/ancestor::android.view.View[1]//*[contains(@text, 'Agotado') or contains(@text, 'No disponible')]";

        try {
            // 2. Verificación por texto visual (Lo más confiable en tu App)
            if (this.isVisibleQuick(By.xpath(xpathEstado))) {
                log.debug("DEBUG: Producto {} detectado como AGOTADO visualmente.", nombreProducto);
                throw new org.opentest4j.TestAbortedException("El alimento \"" + nombreProducto + "\" se encuentra agotado o no disponible");
            }

            // 3. Verificación por atributos en el contenedor REAL (el padre del texto)
            // En Compose, el TextView casi siempre tiene clickable=false, por eso no debemos usarlo para abortar
            String xpathContenedor = "//*[@text='" + nombreProducto + "']/..";
            WebElement contenedor = this.driver.findElement(By.xpath(xpathContenedor));

            // Solo abortamos si 'enabled' es false. Ignoramos 'clickable' porque en Compose suele ser false por defecto en textos.
            if ("false".equals(contenedor.getAttribute("enabled"))) {
                throw new org.opentest4j.TestAbortedException("El alimento \"" + nombreProducto + "\" está deshabilitado en el sistema.");
            }

        } catch (org.openqa.selenium.NoSuchElementException e) {
            // Es normal que no encuentre el texto "Agotado", el test debe seguir.
        } catch (org.opentest4j.TestAbortedException e) {
            // RE-LANZAMOS la excepción de aborto.
            // Si no haces esto, el catch genérico de arriba o del método que lo llama podría consumirla.
            throw e;
        }
    }

    // ── Diálogo "producto no disponible en el cine" (post-selección) ───────────
    // Aparece justo después de seleccionar un producto desde el buscador cuando
    // el artículo no existe para el cine configurado. Debe tratarse como SKIPPED
    // (nunca FAILED) y la suite debe continuar con el siguiente caso — mismo
    // mecanismo (TestAbortedException) que "producto agotado" arriba.
    // NOTA-MIGRACION: usaba @text sin condicional de plataforma (bare attribute, sin
    // prefijo android.widget.* — no lo capturó el barrido inicial). Se resuelve vía
    // PlatformLocator porque este método es alcanzado desde click(), en la ruta crítica.
    // iOS vía NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    // Consultado desde click() en la ruta crítica de cada interacción fallida.
    private static final PlatformLocator PRODUCTO_NO_DISPONIBLE_DIALOG = PlatformLocator.of(
            By.xpath("//*[contains(@text,'no está disponible en el cine') "
                  + "or contains(@text,'no esta disponible en el cine') "
                  + "or contains(@text,'elige otro artículo') "
                  + "or contains(@text,'elige otro articulo')]"),
            AppiumBy.iOSNsPredicateString(
                    "label CONTAINS 'no está disponible en el cine' "
                  + "OR label CONTAINS 'no esta disponible en el cine' "
                  + "OR label CONTAINS 'elige otro artículo' "
                  + "OR label CONTAINS 'elige otro articulo' "
                  + "OR value CONTAINS 'no está disponible en el cine' "
                  + "OR value CONTAINS 'no esta disponible en el cine' "
                  + "OR value CONTAINS 'elige otro artículo' "
                  + "OR value CONTAINS 'elige otro articulo'"));

    /**
     * Si el diálogo "Este producto actualmente no está disponible en el cine.
     * Por favor, elige otro artículo." está visible, lo cierra presionando
     * Aceptar, registra el motivo (log + Allure) y lanza TestAbortedException
     * para que el caso se reporte como SKIPPED — nunca FAILED — sin detener el
     * resto de la suite. Si el diálogo no está presente, no hace nada (no-op).
     *
     * Centralizado en BasePage para que todas las pruebas de alimentos lo
     * hereden automáticamente, sin duplicar lógica ni tocar SelectorPage/tests.
     * Se invoca reactivamente desde click() cuando el elemento esperado no
     * aparece — que es justo lo que ocurre cuando este diálogo cubre la
     * pantalla e impide continuar con el flujo normal (personalizar, etc.).
     */
    protected void abortSiProductoNoDisponibleDialog() {
        if (!isVisibleQuick(PRODUCTO_NO_DISPONIBLE_DIALOG)) return;

        log.info("[Producto] Producto no disponible para el cine seleccionado.");
        log.info("[Producto] Caso marcado como SKIPPED.");
        try {
            Allure.step("Producto no disponible para el cine seleccionado — caso omitido (SKIPPED)");
        } catch (Exception ignored) {}

        // Cerrar el diálogo — best-effort: el SKIPPED se reporta igual aunque
        // el botón no se encuentre o el tap falle.
        try {
            PlatformLocator aceptar = PlatformLocator.of(
                    By.xpath("//*[@text='Aceptar' or @text='ACEPTAR' or @text='OK']"),
                    AppiumBy.iOSNsPredicateString(
                            "label == 'Aceptar' OR label == 'ACEPTAR' OR label == 'OK' " +
                            "OR name == 'Aceptar' OR name == 'ACEPTAR' OR name == 'OK' " +
                            "OR value == 'Aceptar' OR value == 'ACEPTAR' OR value == 'OK'"));
            if (isVisibleQuick(aceptar)) {
                WebElement btn = driver.findElement(aceptar.resolve(isIOS()));
                try { btn.click(); } catch (Exception e) { tapCenterW3C(btn); }
            }
        } catch (Exception ignored) {}

        throw new org.opentest4j.TestAbortedException(
                "Producto no disponible para el cine seleccionado — diálogo detectado y cerrado automáticamente.");
    }

    public void validarElementoVisible(By locator) {
        try {
            waitForVisibility(locator);
        } catch (Exception e) {
            Assertions.fail("No se pudo encontrar o validar la visibilidad del elemento: " + locator, e);
        }
    }

    public void validarElementoVisible(PlatformLocator locator) {
        validarElementoVisible(locator.resolve(isIOS()));
    }

    protected WebElement waitAndGet(By locator) {
        ensureAppIsInForegroundOrRecover();
        try {
            return waits.waitClickableFast(locator);
        } catch (Exception ignore) {
            return waits.waitClickable(locator);
        }
    }
    protected void resetCarouselFromAnchorY(int anchorCenterY, int swipes) {
        for(int i = 0; i < swipes; ++i) {
            this.fastSwipeRightAtY(anchorCenterY);
        }

    }
    protected boolean sweepCatalogRightFromAnchorY(By target, int anchorCenterY, int maxSwipes) {
        for(int i = 0; i < maxSwipes; ++i) {
            if (this.isVisible(target)) {
                return true;
            }

            this.fastSwipeLeftAtY(anchorCenterY);
        }

        return this.isVisible(target);
    }
    // =========================================================
    // ===================== CLICKS (compat) ====================
    // =========================================================
    protected boolean scrollSlowDownThenUpUntilVisible(PlatformLocator locator, int maxSwipesEachDirection) {
        return scrollSlowDownThenUpUntilVisible(locator.resolve(isIOS()), maxSwipesEachDirection);
    }

    protected boolean scrollSlowDownThenUpUntilVisible(By locator, int maxSwipesEachDirection) {
        if (isVisible(locator)) return true;

        String lastFinger = "";
        for (int i = 0; i < maxSwipesEachDirection; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeUp();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        lastFinger = "";
        for (int i = 0; i < maxSwipesEachDirection; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeDown();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        return isVisible(locator);
    }
    protected void click(By locator) {
        ensureAppIsInForegroundOrRecover();
        if (IOSLocatorDebug.isEnabled() && isIOS()) IOSLocatorDebug.beforeInteraction(driver, "click", locator);
        try {
            WebElement el = waitAndGet(locator);
            el.click();
            takeScreenshot();
        } catch (org.opentest4j.TestAbortedException aborted) {
            throw aborted; // ya es un SKIPPED explícito — no interceptar
        } catch (RuntimeException e) {
            if (IOSLocatorDebug.isEnabled() && isIOS()) IOSLocatorDebug.onFailure(driver, "click", locator, e);
            // El elemento esperado no apareció — antes de reportar el fallo tal cual,
            // se descarta la causa más común y benigna: el diálogo de "producto no
            // disponible" cubriendo la pantalla. Si no es eso, se relanza intacto.
            abortSiProductoNoDisponibleDialog();
            throw e;
        }
    }

    protected boolean clickIfPresent(By locator) {
        // NOTA: clickIfPresent() es "best effort" por diseño — no encontrar el
        // elemento es un resultado normal y frecuente (docenas de llamadas por test
        // en guards de overlay), no una falla a diagnosticar. Por eso NO se conecta
        // a IOSLocatorDebug aquí: hacerlo generaría capturas de pageSource en cada
        // miss esperado, con costo real en cada corrida. El diagnóstico vive en
        // click(By) — la variante bloqueante, donde "no se encontró" sí es una falla.
        ensureAppIsInForegroundOrRecover();
        try {
            List<WebElement> elements = findElementsFast(locator);
            if (elements != null && !elements.isEmpty()) {
                WebElement el = elements.get(0);
                if (safeDisplayed(el) && el.isEnabled()) {
                    el.click();
                    takeScreenshot();
                    return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    protected boolean isVisibleQuick(By locator) {
        try {
            List<WebElement> els = findElementsFast(locator);
            return els != null && !els.isEmpty() && safeDisplayed(els.get(0));
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * driver.findElements() con implicitlyWait=0 forzado durante la consulta —
     * causa raíz de los minutos observados en scroll vertical, búsqueda de
     * producto y cambio de cine (ver logs: "scroll-v EXIT ... total=429216ms",
     * "findVisibleOrScrollToXpathAndClick 898360ms", "Cine configurado
     * correctamente ... Total=433434ms").
     *
     * isVisibleQuick() y clickIfPresent() llamaban a driver.findElements(locator)
     * SIN forzar implicitlyWait=0. El resto del framework (tryClickByXpathContains,
     * isVisibleInstantaneamente, CinemasHelper.firstOrNull/isVisibleInstant, etc.)
     * ya sigue la convención de dejar implicitlyWait=10s como "ambiente" fuera de
     * sus propios chequeos instantáneos — ese ambiente de 10s se activa desde el
     * primer chequeo de la suite (p. ej. el primer PromosGuard del @BeforeEach) y
     * permanece así el resto de la sesión (REUSE_DRIVER=true). isVisibleQuick()/
     * clickIfPresent() heredaban ese ambiente sin saberlo: cada "miss" (el elemento
     * buscado aún no está en pantalla — el caso NORMAL en cualquier scroll) bloqueaba
     * findElements() el implicitlyWait completo antes de poder devolver una lista
     * vacía.
     *
     * isVisibleQuick() es la base de oneShotVerticalSearch / oneShotHorizontalSearch /
     * oneShotVerticalAndHorizontal / oneShotVerticalWithRowPeek /
     * scrollSlowDownThenUpUntilVisible — se llama en CADA iteración de CADA scroll,
     * hasta 20+ veces por búsqueda. clickIfPresent() es el primer intento de
     * CinemasHelper.pickCinemaFromResults() (hasta 2 veces) antes de
     * scrollSlowDownThenUpUntilVisible (hasta 24 chequeos más). El costo del "miss"
     * se multiplicaba linealmente por cada swipe/intento — de ahí los minutos
     * observados: son la MISMA espera artificial acumulada sobre estos dos métodos
     * compartidos, alcanzada desde rutas de llamada distintas (búsqueda de producto
     * vs. selección de cine).
     *
     * Un elemento PRESENTE se encuentra igual de rápido sin importar el valor de
     * implicitlyWait — el wait solo retrasa el caso de "no encontrado". Forzarlo a 0
     * aquí no cambia qué se puede o no encontrar; solo elimina la espera artificial
     * cuando el elemento todavía no está en pantalla, que es exactamente cuándo el
     * propio bucle de scroll ya va a reintentar en la siguiente iteración.
     */
    private List<WebElement> findElementsFast(By locator) {
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            return driver.findElements(locator);
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    private boolean isVisible(By locator) {
        return isVisibleQuick(locator);
    }

    private boolean safeDisplayed(WebElement el) {
        try { return el.isDisplayed(); } catch (Exception ignore) { return false; }
    }

    // =========================================================
    // ================= SCROLL UIAUTOMATOR2 ====================
    // =========================================================

    protected void scrollToDescriptionAndClick(String partialDesc) {
        ensureAppIsInForegroundOrRecover();

        WebElement el;
        if (isIOS()) {
            // iOS: swipe until the element with matching label/value is visible
            By locator = By.xpath(
                "//*[contains(@label,'" + partialDesc + "') or contains(@value,'" + partialDesc + "')]"
            );
            oneShotVerticalSearch(locator, 10);
            el = driver.findElement(locator);
        } else {
            String uiScrollable =
                    "new UiScrollable(new UiSelector().scrollable(true))" +
                            ".scrollIntoView(new UiSelector().descriptionContains(\"" +
                            Quotes.escape(partialDesc) + "\"))";
            el = driver.findElement(AppiumBy.androidUIAutomator(uiScrollable));
        }

        new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT)
                .until(ExpectedConditions.elementToBeClickable(el))
                .click();

        takeScreenshot();
    }

    // =========================================================
    // ================== W3C TAP / SWIPE ======================
    // =========================================================

    public void tapW3C(int x, int y) {
        ensureAppIsInForegroundOrRecover();
        Dimension screen = driver.manage().window().getSize();
        int safeX = Math.max(1, Math.min(x, screen.width  - 1));
        int safeY = Math.max(1, Math.min(y, screen.height - 1));

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);

        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), safeX, safeY));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(new Pause(finger, Duration.ofMillis(120)));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(Collections.singletonList(tap));
    }

    protected void swipeW3C(int startX, int startY, int endX, int endY, long durationMs) {
        ensureAppIsInForegroundOrRecover();
        PointerInput finger = new PointerInput(Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ZERO, Origin.viewport(), startX, startY));
        swipe.addAction(finger.createPointerDown(MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(Math.max(0, durationMs)), Origin.viewport(), endX, endY));
        swipe.addAction(finger.createPointerUp(MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(swipe));
    }

    // Firma "BasePage.txt"
    protected void swipe(int startX, int startY, int endX, int endY) {
        swipeW3C(startX, startY, endX, endY, 700L);
    }

    protected void swipeUp() {
        Dimension size = driver.manage().window().getSize();
        int startY = (int) (size.height * 0.8);
        int endY   = (int) (size.height * 0.2);
        int startX = size.width / 2;
        swipe(startX, startY, startX, endY);
    }

    // Swipes "slow" como BasePage2
    protected void slowSwipeUp() {
        ensureAppIsInForegroundOrRecover();
        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.70);
        int endY   = (int) (size.getHeight() * 0.45);
        swipeW3C(x, startY, x, endY, SLOW_SWIPE_WAIT_MS);
        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void slowSwipeDown() {
        ensureAppIsInForegroundOrRecover();
        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.45);
        int endY   = (int) (size.getHeight() * 0.70);
        swipeW3C(x, startY, x, endY, SLOW_SWIPE_WAIT_MS);
        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void slowSwipeLeft() {
        ensureAppIsInForegroundOrRecover();
        Dimension size = driver.manage().window().getSize();
        int width  = size.getWidth();
        int height = size.getHeight();
        int y      = (int) (height * 0.70);
        int startX = (int) (width  * 0.70);
        int endX   = (int) (width  * 0.35);
        swipeW3C(startX, y, endX, y, SLOW_SWIPE_WAIT_MS);
        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void fastSwipeLeftAtY(int y) {
        ensureAppIsInForegroundOrRecover();
        Dimension size = driver.manage().window().getSize();
        int width  = size.getWidth();
        int startX = (int) (width * 0.78);
        int endX   = (int) (width * 0.36);
        swipeW3C(startX, y, endX, y, FAST_SWIPE_WAIT_MS);
        sleep(FAST_SWIPE_SLEEP_MS);
    }

    protected void fastSwipeRightAtY(int y) {
        ensureAppIsInForegroundOrRecover();
        Dimension size = driver.manage().window().getSize();
        int width  = size.getWidth();
        int startX = (int) (width * 0.36);
        int endX   = (int) (width * 0.78);
        swipeW3C(startX, y, endX, y, FAST_SWIPE_WAIT_MS);
        sleep(FAST_SWIPE_SLEEP_MS);
    }

    protected void tapCenterW3C(WebElement el) {
        ensureAppIsInForegroundOrRecover();
        int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
        int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;
        tapW3C(cx, cy - 12);
        takeScreenshot();
    }
protected List<WebElement> safeFindElements(By locator) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return driver.findElements(locator);
            } catch (org.openqa.selenium.WebDriverException e) {
                if (attempt < 2
                        && e.getMessage() != null
                        && e.getMessage().contains("socket hang up")) {
                    System.out.println("[safeFindElements] socket hang up detectado, reintento "
                            + (attempt + 1) + "/2 en " + (2 * (attempt + 1)) + "s");
                    sleep(2000L * (attempt + 1));
                    continue;
                }
                throw e;
            }
        }
        return java.util.Collections.emptyList();
    }
    // =========================================================
    // ==================== CLICK SMART =========================
    // =========================================================

    protected void clickOrTapByXpath(String xpath, int timeoutSeconds) {
        ensureAppIsInForegroundOrRecover();
        By by = By.xpath(xpath);

        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = w.until(ExpectedConditions.visibilityOfElementLocated(by));
            try {
                el.click();
                takeScreenshot();
            } catch (Exception ignore) {
                tapCenterW3C(el);
            }
        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new RuntimeException("No se pudo click/tap por xpath: " + xpath, e);
        }
    }

    protected void clickSmart(String xpath, int timeoutSeconds) {
        ensureAppIsInForegroundOrRecover();
        By by = By.xpath(xpath);

        WebElement base;
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            base = w.until(ExpectedConditions.visibilityOfElementLocated(by));
        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new RuntimeException("No se pudo encontrar visible para clickSmart: " + xpath, e);
        }

        if (tryClickElement(base)) return;
        if (tryTapElement(base)) return;
        if (tryClickGestureElement(base)) return;

        WebElement container = findBestCardContainer(base);
        if (container != null && container != base) {
            if (tryClickElement(container)) return;
            if (tryTapElement(container)) return;
            if (tryClickGestureElement(container)) return;
        }

        WebElement p = base;
        for (int i = 0; i < 3; i++) {
            try {
                p = p.findElement(By.xpath("..")); // en BasePage2 venía mal; aquí es el parent real
                if (tryClickElement(p)) return;
                if (tryTapElement(p)) return;
                if (tryClickGestureElement(p)) return;
            } catch (Exception ignore) {}
        }

        takeScreenshotOnFailure();
        throw new RuntimeException("No se pudo hacer click/tap con ningún método para xpath: " + xpath);
    }

    private boolean tryClickElement(WebElement el) {
        try {
            el.click();
            takeScreenshot();
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean tryTapElement(WebElement el) {
        try {
            tapCenterW3C(el);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean tryClickGestureElement(WebElement el) {
        try {
            int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
            int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;
            if (isIOS()) {
                // iOS: use W3C tap (mobile: clickGesture is Android-specific)
                tapW3C(cx, cy);
            } else {
                Map<String, Object> args = new HashMap<>();
                args.put("x", cx);
                args.put("y", cy);
                driver.executeScript("mobile: clickGesture", args);
            }
            takeScreenshot();
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    protected boolean tryClickIfAlreadyVisible(By locator, int timeoutSeconds) {
        ensureAppIsInForegroundOrRecover();
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = w.until(ExpectedConditions.visibilityOfElementLocated(locator));
            try {
                el.click();
                takeScreenshot();
            } catch (Exception ignore) {
                tapCenterW3C(el);
            }
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    // =========================================================
    // ============ PRODUCTOS: DETECTAR INHABILITADO ============
    // =========================================================

    /**
     * Variante de tryClickIfAlreadyVisible para PRODUCTOS.
     * Si encuentra el elemento pero está INHABILITADO / AGOTADO / NO DISPONIBLE:
     *  - adjunta evidencia en Allure
     *  - marca el test como SKIPPED (Assumptions.abort)
     */
    protected boolean tryClickIfAlreadyVisibleProducto(By locator, int timeoutSeconds, String nombreProducto) {
        ensureAppIsInForegroundOrRecover();
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = w.until(ExpectedConditions.visibilityOfElementLocated(locator));

            // ✅ Si el producto está deshabilitado, aborta como SKIPPED
            abortIfProductoNoDisponible(el, nombreProducto);

            try {
                el.click();
                takeScreenshot();
            } catch (Exception ignore) {
                tapCenterW3C(el);
            }
            return true;

        } catch (org.opentest4j.TestAbortedException aborted) {
            throw aborted; // deja pasar SKIPPED
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Variante de findVisibleOrScrollToXpathAndClick para PRODUCTOS.
     * Mantiene tu lógica FAST-FAIL de 1 pasada, pero si encuentra el card y está inhabilitado -> SKIPPED.
     */
    protected void findVisibleOrScrollToXpathAndClickProducto(String xpath, int maxSwipesEachDirection, String nombreProducto) {
        ensureAppIsInForegroundOrRecover();
        By locator = By.xpath(xpath);

        if (!clickIfPresent(locator)) {
            boolean found = oneShotVerticalSearch(locator, maxSwipesEachDirection);
            if (!found) {
                takeScreenshotOnFailure();

                // ✅ Si NO se encontró el elemento, pero el producto aparece como AGOTADO en pantalla, entonces SKIP.
                try {
                    if (isAgotadoOnScreenApprox(nombreProducto)) {
                        abortProductoAgotado(nombreProducto, "FAST-FAIL (no encontrado) pero se detectó 'Agotado' en pantalla.");
                    }
                } catch (org.opentest4j.TestAbortedException aborted) {
                    throw aborted;
                } catch (Exception ignored) {
                    rethrowIfAborted(ignored);
                }

                throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada. XPath: " + xpath);
            }

            WebElement el = driver.findElement(locator);
            abortIfProductoNoDisponible(el, nombreProducto);

            // ✅ Click directo para evitar waitClickable (producto puede venir clickable=false)
            try {
                el.click();
            } catch (Exception ignore) {
                tapCenterW3C(el);
            }
            try { takeScreenshot(); } catch (Exception ignored) {}

        } else {
            // Si ya estaba presente y clickIfPresent hizo click, al menos valida estado (si aún es visible)
            try {
                WebElement el = driver.findElement(locator);
                abortIfProductoNoDisponible(el, nombreProducto);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Variante de findVisibleOrScrollDownAndRightSlowToXpathAndClick para PRODUCTOS.
     * Mantiene tu lógica FAST-FAIL de 1 pasada V/H, pero si encuentra el card y está inhabilitado -> SKIPPED.
     */
    protected void findVisibleOrScrollDownAndRightSlowToXpathAndClickProducto(String xpath, int maxVerticalSwipes, int maxRightSwipesPerRow, String nombreProducto) {
        ensureAppIsInForegroundOrRecover();
        By locator = By.xpath(xpath);

        if (!clickIfPresent(locator)) {
            boolean found = oneShotVerticalAndHorizontal(locator, maxVerticalSwipes, maxRightSwipesPerRow);
            if (!found) {
                takeScreenshotOnFailure();

                // ✅ Si NO se encontró el elemento, pero el producto aparece como AGOTADO en pantalla, entonces SKIP.
                try {
                    if (isAgotadoOnScreenApprox(nombreProducto)) {
                        abortProductoAgotado(nombreProducto, "FAST-FAIL (V/H no encontrado) pero se detectó 'Agotado' en pantalla.");
                    }
                } catch (org.opentest4j.TestAbortedException aborted) {
                    throw aborted;
                } catch (Exception ignored) {
                    rethrowIfAborted(ignored);
                }

                throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada (V/H). XPath: " + xpath);
            }

            WebElement el = driver.findElement(locator);
            abortIfProductoNoDisponible(el, nombreProducto);

            // ✅ Click directo para evitar waitClickable (producto puede venir clickable=false)
            try {
                el.click();
            } catch (Exception ignore) {
                tapCenterW3C(el);
            }
            try { takeScreenshot(); } catch (Exception ignored) {}

        } else {
            try {
                WebElement el = driver.findElement(locator);
                abortIfProductoNoDisponible(el, nombreProducto);
            } catch (Exception ignored) {}
        }
    }


    protected void abortIfProductoNoDisponible(WebElement el, String nombreProducto) {
        try {
            // Revisamos el elemento y varios ancestros (Compose suele poner estado en el contenedor)
            WebElement node = el;

            String enabledBest = null;
            String clickableBest = null;
            String focusableBest = null;

            for (int i = 0; i < 5; i++) { // elemento + padres
                String enabled = safeGetAttr(node, "enabled");
                String clickable = safeGetAttr(node, "clickable");
                String focusable = safeGetAttr(node, "focusable");

                if ("false".equalsIgnoreCase(enabled)) enabledBest = enabled;
                if ("false".equalsIgnoreCase(clickable)) clickableBest = clickable;
                if ("false".equalsIgnoreCase(focusable)) focusableBest = focusable;

                try {
                    node = node.findElement(By.xpath(".."));
                } catch (Exception e) {
                    break;
                }
            }

            if (enabledBest == null) enabledBest = safeGetAttr(el, "enabled");
            if (clickableBest == null) clickableBest = safeGetAttr(el, "clickable");
            if (focusableBest == null) focusableBest = safeGetAttr(el, "focusable");

            boolean disabled =
                    "false".equalsIgnoreCase(enabledBest)
                            || "false".equalsIgnoreCase(clickableBest)
                            || "false".equalsIgnoreCase(focusableBest);

            // 🔎 Badge SOLO dentro de la card (helper que ya tenías)
            String badge = detectBadgeEnCard(el);

            // 📎 Adjuntar estado UI a Allure
            try {
                io.qameta.allure.Allure.addAttachment(
                        "Estado UI - " + nombreProducto,
                        "enabled=" + enabledBest
                                + "\nclickable=" + clickableBest
                                + "\nfocusable=" + focusableBest
                                + "\nbadge=" + (badge == null ? "N/A" : badge)
                );
            } catch (Exception ignored) {}

            // ✅ SKIP inmediato si está agotado / no disponible
            if (disabled || badge != null) {
                try { takeScreenshot(); } catch (Exception ignored) {}

                org.junit.jupiter.api.Assumptions.abort(
                        "Falló en seleccionar producto '" + nombreProducto + "' porque se encuentra AGOTADO"
                                + (badge != null ? (" (Estado detectado: " + badge + ")") : "")
                );
            }

        } catch (org.opentest4j.TestAbortedException aborted) {
            throw aborted; // deja pasar SKIPPED
        } catch (Exception ignored) {
            // no rompe el flujo si falla la detección
        }
    }
    // ✅ Helper local con nombre distinto para evitar el conflicto de safeGetAttr duplicado
    private String safeGetAttr2(WebElement el, String attr) {
        try { return el.getAttribute(attr); } catch (Exception e) { return "N/A"; }
    }

    private String safeGetAttr(WebElement el, String attr) {
        try { return el.getAttribute(attr); } catch (Exception e) { return "N/A"; }
    }
    private String detectBadgeEnCard(WebElement el) {
        String[] keywords = new String[]{"Agotado", "Inhabilitado", "No disponible", "Próximamente", "Proximamente"};
        try {
            // Intentamos subir a contenedor inmediato (card)
            WebElement container = el;
            try { container = el.findElement(By.xpath("..")); } catch (Exception ignored) {}

            for (String k : keywords) {
                try {
                    // Buscamos SOLO dentro de la card (evita falsos positivos en pantalla)
                    if (!container.findElements(By.xpath(".//*[contains(@text,'" + k + "') or contains(@content-desc,'" + k + "')]")).isEmpty()) {
                        return k;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }




    // =========================================================
    // =========== VIEWPORT FINGERPRINT (BasePage2) ============
    // =========================================================

    /**
     * Locator "primeros N StaticText con valor no vacío", usado por el fingerprint de
     * viewport en CADA iteración de scroll (oneShotVerticalSearch/oneShotHorizontalSearch
     * — hasta 20+ veces por búsqueda, ver comentario de findElementsFast() arriba).
     *
     * PERF (solo iOS): antes XPath con position()<=N — XCUITest debe serializar el
     * árbol de accesibilidad COMPLETO a XML para evaluar cualquier XPath, incluida la
     * limitación por posición; en pantallas con muchos nodos esto se paga en CADA
     * swipe. Ahora se filtra con NSPredicate (WDA evalúa contra el árbol nativo, sin
     * ese volcado) y el recorte a los primeros N elementos se hace en Java sobre la
     * lista ya obtenida — mismo resultado (mismo orden de documento que ya devuelve
     * driver.findElements()), sin la evaluación XPath de por medio. Android (XPath +
     * position()) se deja exactamente igual.
     */
    private List<WebElement> firstNStaticTextsWithValue(int n) {
        if (isIOS()) {
            List<WebElement> all = driver.findElements(
                    AppiumBy.iOSNsPredicateString("value != nil AND value != ''"));
            return all.size() <= n ? all : all.subList(0, n);
        }
        return driver.findElements(By.xpath(
                "(//android.widget.TextView[@text and string-length(@text)>0])[position() <= " + n + "]"));
    }

    private String viewportFingerPrint() {
        try {
            List<WebElement> texts = firstNStaticTextsWithValue(6);
            if (texts == null || texts.isEmpty()) return "EMPTY";

            StringBuilder sb = new StringBuilder();
            for (WebElement el : texts) {
                try { sb.append(el.getText().trim()).append("|"); } catch (Exception ignore) {}
            }
            return sb.toString();
        } catch (Exception ignore) {
            return "ERR";
        }
    }

    public String viewportFingerPrintPublic() {
        try {
            List<WebElement> texts = firstNStaticTextsWithValue(6);
            StringBuilder sb = new StringBuilder();
            for (WebElement el : texts) {
                try { sb.append(el.getText().trim()).append("|"); } catch (Exception ignore) {}
            }
            return sb.toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    /**
     * Locator "primeros N nodos con @value o @label no vacío" para richFingerPrint().
     * Misma optimización que firstNStaticTextsWithValue() — ver esa nota para el porqué
     * (llamado en cada iteración de scroll, junto con viewportFingerPrint()).
     */
    private List<WebElement> firstNNodesWithValueOrLabel(int n) {
        if (isIOS()) {
            List<WebElement> all = driver.findElements(AppiumBy.iOSNsPredicateString(
                    "(value != nil AND value != '') OR (label != nil AND label != '')"));
            return all.size() <= n ? all : all.subList(0, n);
        }
        return driver.findElements(By.xpath(
                "(//*[string-length(@text)>0 or string-length(@content-desc)>0])[position()<=" + n + "]"));
    }

    /**
     * Fingerprint enriquecido para scroll en pantallas Compose/LazyColumn.
     * Captura @text Y @content-desc de los primeros 10 elementos visibles,
     * cubriendo tanto views nativos como nodos Compose que no exponen TextView.
     */
    private String richFingerPrint() {
        try {
            List<WebElement> els = firstNNodesWithValueOrLabel(10);
            if (els == null || els.isEmpty()) return "EMPTY";
            StringBuilder sb = new StringBuilder();
            for (WebElement el : els) {
                try {
                    String txt;
                    if (isIOS()) {
                        txt = el.getAttribute("value");
                        if (txt == null || txt.isBlank()) txt = el.getAttribute("label");
                    } else {
                        txt = el.getAttribute("text");
                        if (txt == null || txt.isBlank()) txt = el.getAttribute("content-desc");
                    }
                    if (txt != null && !txt.isBlank()) sb.append(txt.trim()).append("|");
                } catch (Exception ignore) {}
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";
        } catch (Exception ignore) {
            return "ERR";
        }
    }

    // =========================================================
    // =========== BÚSQUEDAS "ONE SHOT" (FAST-FAIL) =============
    // =========================================================

    protected boolean ensureVisibleByXpathNoClick(String xpath, int maxVerticalSwipes) {
        ensureAppIsInForegroundOrRecover();
        By locator = By.xpath(xpath);
        return isVisible(locator) || oneShotVerticalSearch(locator, maxVerticalSwipes);
    }

    // Cuántos fingerprints iguales consecutivos se necesitan para declarar fin real de lista.
    // Con 1 (original) se producían falsos positivos en Compose/LazyColumn.
    // Con 2 se tolera 1 swipe "sin cambio aparente" antes de rendirse.
    private static final int SCROLL_STALL_THRESHOLD = 2;

    protected boolean oneShotVerticalSearch(By locator, int maxDownSwipes) {
        long tOSV = System.currentTimeMillis();
        int allowed = Math.min(maxDownSwipes, TOTAL_VERTICAL_SWIPE_BUDGET);
        int stalledCount = 0;

        log.info("[scroll-v] ENTER {} | maxSwipes={} implicitlyWait=10s-por-miss", locator, allowed);

        // 'after' de la iteración N es el 'before' de la iteración N+1 — reutilizar evita
        // un round-trip de driver por ciclo (el screen no cambia entre iteraciones sin swipe).
        String cachedFP = null;

        for (int i = 0; i < allowed; i++) {
            if (isVisible(locator)) {
                log.debug("[scroll-v] encontrado en swipe {}/{}", i + 1, allowed);
                return true;
            }

            String before = (cachedFP != null) ? cachedFP : richFingerPrint();
            log.debug("[scroll-v] swipe {}/{} | antes=[{}] stalled={}", i + 1, allowed, before, stalledCount);

            slowSwipeUp();

            String after = richFingerPrint();
            cachedFP = after;
            log.debug("[scroll-v] swipe {}/{} | despues=[{}]", i + 1, allowed, after);

            if (after.equals(before)) {
                stalledCount++;
                if (stalledCount >= SCROLL_STALL_THRESHOLD) {
                    log.debug("[scroll-v] fin real de lista – {} fingerprints iguales consecutivos en swipe {}/{}",
                            SCROLL_STALL_THRESHOLD, i + 1, allowed);
                    break;
                }
            } else {
                stalledCount = 0;
            }
        }

        boolean found = isVisible(locator);
        log.info("[scroll-v] EXIT encontrado={} | total={}ms", found, System.currentTimeMillis() - tOSV);
        return found;
    }

    private boolean oneShotHorizontalSearch(By locator, int maxRightSwipes, int[] budgetH) {
        long tOSH = System.currentTimeMillis();
        int allowed = maxRightSwipes;
        if (budgetH != null) {
            allowed = Math.min(maxRightSwipes, budgetH[0]);
            if (allowed <= 0) return isVisible(locator);
        }

        log.info("[scroll-h] ENTER {} | maxSwipes={}", locator, allowed);
        int stalledCount = 0;
        String cachedFP = null;

        for (int i = 0; i < allowed; i++) {
            if (isVisible(locator)) return true;

            String before = (cachedFP != null) ? cachedFP : richFingerPrint();
            slowSwipeLeft();
            String after = richFingerPrint();
            cachedFP = after;

            log.debug("[scroll-h] swipe {}/{} | antes=[{}] despues=[{}]", i + 1, allowed, before, after);

            if (after.equals(before)) {
                stalledCount++;
                if (stalledCount >= SCROLL_STALL_THRESHOLD) {
                    log.debug("[scroll-h] fin de fila – {} fingerprints iguales en swipe {}/{}",
                            SCROLL_STALL_THRESHOLD, i + 1, allowed);
                    break;
                }
            } else {
                stalledCount = 0;
            }

            if (budgetH != null) {
                budgetH[0]--;
                if (budgetH[0] <= 0) break;
            }
        }
        boolean foundH = isVisible(locator);
        log.info("[scroll-h] EXIT encontrado={} | total={}ms", foundH, System.currentTimeMillis() - tOSH);
        return foundH;
    }

    private boolean oneShotVerticalAndHorizontal(By locator, int maxDownSwipes, int maxRightSwipesPerRow) {
        // Delega en la variante con "peek" por fila (misma firma, mismo contrato,
        // mismos presupuestos TOTAL_VERTICAL_SWIPE_BUDGET / TOTAL_HORIZONTAL_SWIPE_BUDGET).
        // Antes: fase 1 vertical completa + fase 2 horizontal SOLO en la posición final
        // — un producto dentro de un carrusel intermedio (p. ej. "Destacados" a media
        // pantalla) se perdía por completo si no quedaba visible al terminar el descenso.
        // Ahora: cada fila/carrusel se revisa (con presupuesto acotado) a medida que
        // aparece en pantalla durante el propio descenso, sin dejar de avanzar hacia
        // abajo y sin volver jamás a una fila ya procesada.
        return oneShotVerticalWithRowPeek(locator, maxDownSwipes, maxRightSwipesPerRow);
    }

    /**
     * Búsqueda vertical determinista con "peek" horizontal acotado por fila.
     *
     * En cada paso del descenso:
     *   1. ¿El target ya es visible sin tocar ningún carrusel? → listo.
     *   2. Peek acotado (oneShotHorizontalSearch, con su propio stall-detection y el
     *      mismo presupuesto horizontal global TOTAL_HORIZONTAL_SWIPE_BUDGET) SOLO en
     *      la fila/carrusel actualmente en pantalla. Si el producto no aparece ahí, se
     *      abandona esa fila de inmediato — nunca se vuelve a ella.
     *   3. Un solo swipe vertical hacia la siguiente sección (mismo slowSwipeUp() y
     *      mismos tiempos que el resto del archivo) y se repite.
     *
     * Garantiza el orden exigido: una única dirección de recorrido (arriba→abajo),
     * scroll horizontal únicamente para inspeccionar la fila actual, y jamás scroll
     * horizontal mientras el presupuesto vertical no esté agotado por completo. Los
     * presupuestos totales (vertical y horizontal) son idénticos a los ya usados por
     * el resto de los métodos one-shot — no se modifica ningún tiempo de espera.
     */
    protected boolean oneShotVerticalWithRowPeek(By locator, int maxDownSwipes, int maxPeekSwipesPerRow) {
        long t0        = System.currentTimeMillis();
        int allowedV   = Math.min(maxDownSwipes, TOTAL_VERTICAL_SWIPE_BUDGET);
        int peekBudget = Math.max(1, maxPeekSwipesPerRow);
        int[] budgetH  = new int[]{TOTAL_HORIZONTAL_SWIPE_BUDGET};
        int stalledCount = 0;

        log.info("[scroll-v+peek] ENTER {} | maxV={} peekPorFila={} maxH={}",
                locator, allowedV, peekBudget, TOTAL_HORIZONTAL_SWIPE_BUDGET);

        String cachedFP = null;
        for (int i = 0; i < allowedV; i++) {
            if (isVisible(locator)) {
                log.debug("[scroll-v+peek] encontrado sin scroll horizontal en paso {}/{}", i + 1, allowedV);
                return true;
            }

            // Peek acotado en la fila actualmente en pantalla — nunca se repite.
            if (budgetH[0] > 0) {
                int rowBudget = Math.min(peekBudget, budgetH[0]);
                if (oneShotHorizontalSearch(locator, rowBudget, budgetH)) {
                    log.debug("[scroll-v+peek] encontrado en peek horizontal, fila del paso {}/{}", i + 1, allowedV);
                    return true;
                }
                // El peek movió el carrusel (slowSwipeLeft) — el fingerprint cacheado
                // ya no refleja la pantalla actual; forzar recálculo antes de comparar.
                cachedFP = null;
            }

            String before = (cachedFP != null) ? cachedFP : richFingerPrint();
            log.debug("[scroll-v+peek] swipe-v {}/{} | antes=[{}] stalled={}", i + 1, allowedV, before, stalledCount);

            slowSwipeUp();

            String after = richFingerPrint();
            cachedFP = after;
            log.debug("[scroll-v+peek] swipe-v {}/{} | despues=[{}]", i + 1, allowedV, after);

            if (after.equals(before)) {
                stalledCount++;
                if (stalledCount >= SCROLL_STALL_THRESHOLD) {
                    log.debug("[scroll-v+peek] fin real de catálogo en swipe {}/{}", i + 1, allowedV);
                    break;
                }
            } else {
                stalledCount = 0;
            }
        }

        // Última fila alcanzada: chequeo directo + un último peek acotado — cubre la
        // posición final igual que antes hacía la fase 2, además de cada fila previa.
        if (isVisible(locator)) {
            log.debug("[scroll-v+peek] encontrado tras descenso vertical completo");
            return true;
        }
        if (budgetH[0] > 0 && oneShotHorizontalSearch(locator, Math.min(peekBudget, budgetH[0]), budgetH)) {
            log.debug("[scroll-v+peek] encontrado en peek horizontal final");
            return true;
        }

        boolean found = isVisible(locator);
        log.info("[scroll-v+peek] EXIT encontrado={} | total={}ms", found, System.currentTimeMillis() - t0);
        return found;
    }

    protected void findVisibleOrScrollToXpathAndClick(String xpath, int maxSwipesEachDirection) {
        findVisibleOrScrollToXpathAndClick(By.xpath(xpath), maxSwipesEachDirection);
    }

    /**
     * Variante By — permite a los llamadores iOS pasar un locator NSPredicate
     * (AppiumBy.iOSNsPredicateString) en vez de forzar XPath, sin duplicar la lógica de
     * scroll/click. Android sigue pasando By.xpath(...) a través del overload de String
     * de arriba, sin ningún cambio de comportamiento.
     */
    protected void findVisibleOrScrollToXpathAndClick(By locator, int maxSwipesEachDirection) {
        long t0 = System.currentTimeMillis();
        log.info("[BasePage] findVisibleOrScrollToXpathAndClick ENTER maxSwipes={} locator={}",
                maxSwipesEachDirection, locator);
        ensureAppIsInForegroundOrRecover();

        if (!clickIfPresent(locator)) {
            boolean found = oneShotVerticalSearch(locator, maxSwipesEachDirection);
            if (!found) {
                log.warn("[BasePage] findVisibleOrScrollToXpathAndClick EXIT not found ({}ms)", System.currentTimeMillis() - t0);
                takeScreenshotOnFailure();
                throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada. Locator: " + locator);
            }
            click(locator);
        }
        log.info("[BasePage] findVisibleOrScrollToXpathAndClick EXIT found ({}ms)", System.currentTimeMillis() - t0);
    }

    protected void findVisibleOrScrollDownAndRightSlowToXpathAndClick(String xpath, int maxVerticalSwipes, int maxRightSwipesPerRow) {
        findVisibleOrScrollDownAndRightSlowToXpathAndClick(By.xpath(xpath), maxVerticalSwipes, maxRightSwipesPerRow);
    }

    /** Variante By — ver nota en {@link #findVisibleOrScrollToXpathAndClick(By, int)}. */
    protected void findVisibleOrScrollDownAndRightSlowToXpathAndClick(By locator, int maxVerticalSwipes, int maxRightSwipesPerRow) {
        long t0 = System.currentTimeMillis();
        log.info("[BasePage] findVisibleOrScrollDownAndRight ENTER maxV={} maxH={} locator={}",
                maxVerticalSwipes, maxRightSwipesPerRow, locator);
        ensureAppIsInForegroundOrRecover();

        if (!clickIfPresent(locator)) {
            boolean found = oneShotVerticalAndHorizontal(locator, maxVerticalSwipes, maxRightSwipesPerRow);
            if (!found) {
                log.warn("[BasePage] findVisibleOrScrollDownAndRight EXIT not found ({}ms)", System.currentTimeMillis() - t0);
                takeScreenshotOnFailure();
                throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada (V/H). Locator: " + locator);
            }
            click(locator);
        }
        log.info("[BasePage] findVisibleOrScrollDownAndRight EXIT found ({}ms)", System.currentTimeMillis() - t0);
    }

    // =========================================================
    // ============ FLAVORS / CONTENT-DESC CONTROLADO ===========
    // =========================================================

    public void seleccionarSaborPorContentDesc2(String contentDesc) {
        ensureAppIsInForegroundOrRecover();

        String xpath = isIOS()
            ? "//*[@label=\"" + contentDesc + "\" or @value=\"" + contentDesc + "\" or @name=\"" + contentDesc + "\"]"
            : "//android.view.View[@content-desc=\"" + contentDesc + "\"]";
        By locator = By.xpath(xpath);

        // Conserva la intención de BasePage2 (búsqueda controlada y luego clickSmart)
        final int maxRounds = 1;
        final int scrollsPerRound = 2;

        String lastFinger = "";

        for (int round = 0; round < maxRounds; round++) {
            for (int s = 0; s < scrollsPerRound; s++) {
                String before = viewportFingerPrintPublic();
                slowSwipeUp();
                String after = viewportFingerPrintPublic();
                if (after.equals(before) || after.equals(lastFinger)) break;
                lastFinger = after;
            }

            if (isVisibleQuick(locator)) {
                clickSmart(xpath, 2);
                return;
            }
        }

        try {
            clickSmart(xpath, 6);
        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new RuntimeException(
                    "No se encontró el sabor con content-desc: '" + contentDesc + "' tras búsqueda controlada. XPath: " + xpath,
                    e
            );
        }
    }
    public void clickProductoOskip(String nombreProducto) {
        // 1) Encontrar elemento por accessibilityId (rápido si existe)
        WebElement el = null;
        try {
            el = driver.findElement(io.appium.java_client.AppiumBy.accessibilityId(nombreProducto));
        } catch (Exception ignored) {}

        // 2) Fallback por texto/content-desc en cualquier View/TextView (Compose friendly)
        if (el == null) {
            List<WebElement> candidates = driver.findElements(By.xpath(
                    "//*[(@text='" + escapeXpath(nombreProducto) + "') or (@content-desc='" + escapeXpath(nombreProducto) + "')]"
            ));
            if (!candidates.isEmpty()) el = candidates.get(0);
        }

        // Si no se encontró -> aquí tú decides: fallar o skip.
        if (el == null) {
            takeScreenshot("NO_ENCONTRADO - " + nombreProducto);
            Assumptions.abort("SKIPPED: No se encontró el producto '" + nombreProducto + "' en pantalla.");
            return;
        }

        // 3) Detectar inhabilitado por atributos
        String enabled = safeAttr(el, "enabled");
        String clickable = safeAttr(el, "clickable");
        String focusable = safeAttr(el, "focusable");

        boolean disabled =
                "false".equalsIgnoreCase(enabled)
                        || "false".equalsIgnoreCase(clickable)
                        || "false".equalsIgnoreCase(focusable);

        // 4) Detectar badges/labels comunes (Agotado, Próximamente, No disponible...)
        String badge = detectarBadgeNoDisponible();

        Allure.addAttachment("Estado UI - " + nombreProducto,
                "enabled=" + enabled +
                        "\nclickable=" + clickable +
                        "\nfocusable=" + focusable +
                        "\nbadgeDetectado=" + (badge == null ? "N/A" : badge)
        );

        if (disabled || badge != null) {
            takeScreenshot("ITEM_INHABILITADO - " + nombreProducto);
            Assumptions.abort("SKIPPED: '" + nombreProducto + "' está INHABILITADO/NO DISPONIBLE. " +
                    (badge != null ? ("Badge: " + badge) : ""));
            return;
        }

        // 5) Si está disponible -> click normal
        el.click();
    }

    private String detectarBadgeNoDisponible() {
        // Agrega aquí todas las palabras que tu app use en diferentes módulos
        String[] keywords = new String[]{
                "Agotado", "Inhabilitado", "No disponible", "Próximamente", "Proximamente"
        };

        for (String k : keywords) {
            try {
                List<WebElement> hits = driver.findElements(By.xpath(
                        "//*[contains(@text,'" + k + "') or contains(@content-desc,'" + k + "')]"
                ));
                if (!hits.isEmpty()) return k;
            } catch (Exception ignored) {}
        }
        return null;
    }
    public void takeScreenshot(String nombre) {
        try {
            // ✅ Reutiliza tu método actual sin parámetros
            byte[] bytes = takeScreenshot();

            // ✅ Adjunta el screenshot con nombre en Allure
            if (bytes != null) {
                Allure.getLifecycle().addAttachment(
                        nombre,
                        "image/png",
                        "png",
                        bytes
                );
            }
        } catch (Exception e) {
            log.warn("[BasePage] No se pudo adjuntar screenshot: {}", e.getMessage());
        }
    }

    private String safeAttr(WebElement el, String attr) {
        try { return el.getAttribute(attr); } catch (Exception e) { return "N/A"; }
    }

    private String escapeXpath(String s) {
        // Simple escape para comillas simples en XPath
        return s.replace("'", "\\'");
    }


    protected void ensureVisibleNoClick(By locator, int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
                    .until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new AssertionError("Elemento no visible tras " + timeoutSeconds + "s: " + locator, e);
        }
    }

    protected void verificarSinErrorApp() {
        ensureAppIsInForegroundOrRecover();
        String textAttr = isIOS() ? "@value" : "@text";
        By[] errorLocators = {
            By.xpath("//*[contains(" + textAttr + ",'Algo salió mal') or contains(" + textAttr + ",'Ocurrió un error') or contains(" + textAttr + ",'Error inesperado') or contains(" + textAttr + ",'Sin conexión')]"),
            By.xpath("//*[contains(" + textAttr + ",'something went wrong') or contains(" + textAttr + ",'error')]")
        };
        for (By loc : errorLocators) {
            try {
                List<WebElement> found = driver.findElements(loc);
                if (found != null && !found.isEmpty() && safeDisplayed(found.get(0))) {
                    takeScreenshotOnFailure();
                    throw new AssertionError("Error detectado en la app: " + found.get(0).getText());
                }
            } catch (AssertionError ae) {
                throw ae;
            } catch (Exception ignored) {}
        }
    }

    protected void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    private WebElement findBestCardContainer(WebElement base) {
        try {
            return base.findElement(By.xpath("./ancestor::*[@clickable='true'][1]"));
        } catch (Exception ignore) {
            String view = isIOS() ? "XCUIElementTypeOther" : "android.view.View";
            try {
                return base.findElement(By.xpath("./ancestor::" + view + "[2]"));
            } catch (Exception ignore2) {
                try {
                    return base.findElement(By.xpath("./ancestor::" + view + "[3]"));
                } catch (Exception ignore3) {
                    return base;
                }
            }
        }
    }

    // =========================================================
    // ============== AGOTADO: DETECCIÓN EN PANTALLA ============
    // =========================================================

    /**
     * Extrae el valor de @text="..." de un xpath (si existe).
     * Útil para cuando solo tienes el xpath y necesitas el nombre del producto para SKIP.
     */
    protected String extractTextFromXpath(String xpath) {
        try {
            if (xpath == null) return null;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("@text\\s*=\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(xpath);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            rethrowIfAborted(e);
            return null;
        }
    }

    /**
     * Detecta si el producto (por nombre) está marcado como "Agotado" en pantalla.
     * Funciona con Compose subiendo ancestros y buscando el label "Agotado".
     */
    protected boolean isAgotadoOnScreenApprox(String nombreProducto) {
        try {
            if (nombreProducto == null || nombreProducto.trim().isEmpty()) return false;

            String n = nombreProducto.trim().replace("®", "").replace("™", "");

            String xpName = isIOS()
                ? "//XCUIElementTypeStaticText[normalize-space(@value)=\"" + n + "\" or contains(@value,\"" + n + "\")]"
                : "//android.widget.TextView[normalize-space(@text)=\"" + n + "\" or contains(@text,\"" + n + "\")]";

            List<WebElement> names = driver.findElements(By.xpath(xpName));
            if (names == null || names.isEmpty()) return false;

            By byAgotado = isIOS()
                ? By.xpath(".//XCUIElementTypeStaticText[contains(@value,'Agotado') or normalize-space(@value)='Agotado']")
                : By.xpath(".//android.widget.TextView[contains(@text,'Agotado') or normalize-space(@text)='Agotado']");

            for (WebElement nameEl : names) {
                WebElement node = nameEl;
                for (int i = 0; i < 12; i++) {
                    try {
                        List<WebElement> ag = node.findElements(byAgotado);
                        if (ag != null && !ag.isEmpty()) return true;
                        node = node.findElement(By.xpath(".."));
                    } catch (Exception ex) {
                        rethrowIfAborted(ex);
                        break;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            rethrowIfAborted(e);
            return false;
        }
    }
    // ===============================
// ALLURE LABELS
// ===============================
    protected void addAllureLabelAgotado() {
        try {
            io.qameta.allure.Allure.getLifecycle().updateTestCase(tc -> {
                tc.getLabels().add(
                        new io.qameta.allure.model.Label()
                                .setName("tag")
                                .setValue("agotado")
                );
            });
        } catch (Exception ignored) {
            rethrowIfAborted(ignored);
        }
    }
    /**
     * Marca el caso como SKIPPED (Allure/JUnit) con el mensaje exacto requerido.
     * NO cambia tu lógica; solo convierte a SKIPPED cuando se confirma "Agotado".
     */
    protected void abortProductoAgotado(String nombreProducto, String debugReason) {
        final String msg = "El producto \"" + nombreProducto + "\" se encuentra agotado";

        try {
            try { takeScreenshot(); } catch (Exception ignored) { rethrowIfAborted(ignored); }

            try { io.qameta.allure.Allure.step(msg); } catch (Exception ignored) { rethrowIfAborted(ignored); }

            try {
                io.qameta.allure.Allure.addAttachment(
                        "Agotado - debug",
                        "text/plain",
                        (debugReason == null ? "" : debugReason)
                );
            } catch (Exception ignored) { rethrowIfAborted(ignored); }

            try { AGOTADOS_SKIPPED_COUNT.incrementAndGet(); } catch (Exception ignored) { rethrowIfAborted(ignored); }
            try { addAllureLabelAgotado(); } catch (Exception ignored) { rethrowIfAborted(ignored); }

        } finally {
            org.junit.jupiter.api.Assumptions.abort(msg);
        }
    }

    protected void abortNoHayMasHorariosEnAsientos(String debugReason) {
        final String msg = "No hay más horarios disponibles para cambiar en la pantalla de asientos";

        try {
            try { takeScreenshot(); } catch (Exception ignored) { rethrowIfAborted(ignored); }

            try { io.qameta.allure.Allure.step(msg); } catch (Exception ignored) { rethrowIfAborted(ignored); }

            try {
                io.qameta.allure.Allure.addAttachment(
                        "Sin más horarios - debug",
                        "text/plain",
                        (debugReason == null ? "" : debugReason)
                );
            } catch (Exception ignored) { rethrowIfAborted(ignored); }

        } finally {
            org.junit.jupiter.api.Assumptions.abort(msg);
        }
    }

}
