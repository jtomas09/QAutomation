package pages.common;

import static org.openqa.selenium.support.ui.Quotes.escape;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Attachment;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.Waits;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static io.appium.java_client.touch.WaitOptions.waitOptions;
import static io.appium.java_client.touch.offset.PointOption.point;

/**
 * BasePage con utilidades comunes: click seguro, screenshots, waits.
 * ✅ Optimizada para reducir tiempos SIN quitar tu lógica.
 * ✅ Controla intentos vertical/horizontal con presupuesto total (anti-bucles).
 * ✅ App Guard: si la app se cierra, recupera o falla rápido (evita taps fuera de la app).
 */
public class BasePage {

    private static long LAST_APP_RECOVER_MS = 2;
    private static final long APP_RECOVER_COOLDOWN_MS = 2 ;
    protected AndroidDriver driver;
    protected Waits waits;

    // =========================
    // ✅ CONFIG APP (CAMBIA ESTO)
    // =========================
    private static final String APP_PACKAGE = "com.cinepolis.go"; // <-- CAMBIA a tu appPackage real
    private static final int APP_GUARD_TIMEOUT_SEC = 8;

    private static final By FIRST_VISIBLE_TEXTVIEW =
            By.xpath("(//android.widget.TextView[@text and string-length(@text)>0])[1]");

    // ✅ Activa fast-fail (solo 1 intento) cuando NO se encuentre
    private static final boolean FAST_FAIL_FIRST_ATTEMPT_ONLY = true;

    // -------------------------------------------------------------------------
    // ✅ EVIDENCIA (optimización fuerte)
    // -------------------------------------------------------------------------
    private static final boolean CAPTURE_SCREENSHOT_EACH_ACTION = false;
    private static final boolean CAPTURE_SCREENSHOT_ON_FAILURE = true;

    // -------------------------------------------------------------------------
    // ✅ SWIPES (más despacio vertical)
    // -------------------------------------------------------------------------
    private static final int SLOW_SWIPE_WAIT_MS = 580;
    private static final int SLOW_SWIPE_SLEEP_MS = 80;

    // (se mantiene por compatibilidad)
    private static final int FAST_FAIL_MAX_SWIPES = 30;

    private static final int FAST_SWIPE_WAIT_MS = 270;
    private static final int FAST_SWIPE_SLEEP_MS = 30;

    // -------------------------------------------------------------------------
    // ✅ LIMITES REALES (ANTI-BUCLES V/H)
    // -------------------------------------------------------------------------
    private static final boolean LIMIT_TOTAL_SWIPE_BUDGET = true;

    // Presupuesto TOTAL (no por fila)
    private static final int TOTAL_VERTICAL_SWIPE_BUDGET = 20;     // ✅ 20 swipes
    private static final int TOTAL_HORIZONTAL_SWIPE_BUDGET = 20;

    // Para sabores: en fast-fail evita rondas múltiples
    private static final boolean FAST_FAIL_FLAVORS_ONE_ROUND = true;

    public BasePage(AndroidDriver driver) {
        this.driver = driver;
        this.waits = new Waits(driver);
    }

    protected void ensureAppIsInForegroundOrRecover() {
        try {
            driver.getSessionId();

            String currentPackage = null;
            try { currentPackage = driver.getCurrentPackage(); } catch (Exception ignored) {}

            // ✅ si ya está en la app correcta → salir
            if (APP_PACKAGE.equals(currentPackage)) {
                return;
            }

            long now = System.currentTimeMillis();

            // 🔒 fusible anti-loop
            if (now - LAST_APP_RECOVER_MS < APP_RECOVER_COOLDOWN_MS) {
                System.out.println("[GUARD] Recuperación de app ignorada (cooldown activo)");
                return;
            }
            LAST_APP_RECOVER_MS = now;

            // intento suave
            try { driver.activateApp(APP_PACKAGE); } catch (Exception ignored) {}

            long end = System.currentTimeMillis() + (APP_GUARD_TIMEOUT_SEC * 1000L);
            while (System.currentTimeMillis() < end) {
                try {
                    String p = driver.getCurrentPackage();
                    if (APP_PACKAGE.equals(p)) return;
                } catch (Exception ignored) {}

                // ❗ SOLO relanza UNA VEZ
                try { driver.terminateApp(APP_PACKAGE); } catch (Exception ignored) {}
                try { driver.activateApp(APP_PACKAGE); } catch (Exception ignored) {}

                sleep(400);
                break; // ⛔ rompe loop
            }

        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new AssertionError("App no estable / posible crash.", e);
        }
    }

    @Attachment(value = "Screenshot", type = "image/png")
    protected byte[] takeScreenshot() {
        if (!CAPTURE_SCREENSHOT_EACH_ACTION) return new byte[0];
        try {
            return driver.getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    protected void takeScreenshotOnFailure() {
        if (!CAPTURE_SCREENSHOT_ON_FAILURE) return;
        try {
            driver.getScreenshotAs(OutputType.BYTES);
        } catch (Exception ignored) {}
    }

    protected WebElement waitAndGet(By locator) {
        try {
            return waits.waitClickableFast(locator);
        } catch (Exception ignored) {
            return waits.waitClickable(locator);
        }
    }

    protected void click(By locator) {
        ensureAppIsInForegroundOrRecover();
        WebElement el = waitAndGet(locator);
        el.click();
        takeScreenshot();
    }

    /**
     * Hace clic solo si el elemento está presente y visible.
     * No lanza excepción si no está.
     */
    protected boolean clickIfPresent(By locator) {
        ensureAppIsInForegroundOrRecover();
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (elements != null && !elements.isEmpty()) {
                WebElement el = elements.get(0);
                if (safeDisplayed(el) && el.isEnabled()) {
                    el.click();
                    takeScreenshot();
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // -------------------------------------------------------------------------
    // ✅ VISIBILIDAD
    // -------------------------------------------------------------------------
    protected boolean isVisibleQuick(By locator) {
        try {
            List<WebElement> els = driver.findElements(locator);
            return els != null && !els.isEmpty() && safeDisplayed(els.get(0));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVisible(By locator) {
        return isVisibleQuick(locator);
    }

    private boolean safeDisplayed(WebElement el) {
        try {
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    protected void scrollToDescriptionAndClick(String partialDesc) {
        ensureAppIsInForegroundOrRecover();

        String uiScrollable =
                "new UiScrollable(new UiSelector().scrollable(true))" +
                        ".scrollIntoView(new UiSelector().descriptionContains(\"" + escape(partialDesc) + "\"))";

        WebElement el = driver.findElement(AppiumBy.androidUIAutomator(uiScrollable));
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.elementToBeClickable(el))
                .click();

        takeScreenshot();
    }

    // -------------------------------------------------------------------------
    // ✅ SCROLL / SWIPES (TU LÓGICA SE QUEDA)
    // -------------------------------------------------------------------------
    protected void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    protected void slowSwipeUp() { // baja (contenido sube)
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.70);
        int endY   = (int) (size.getHeight() * 0.45);

        new TouchAction<>(driver)
                .press(point(x, startY))
                .waitAction(waitOptions(Duration.ofMillis(SLOW_SWIPE_WAIT_MS)))
                .moveTo(point(x, endY))
                .release()
                .perform();

        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void slowSwipeDown() { // sube (contenido baja)
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.45);
        int endY   = (int) (size.getHeight() * 0.70);

        new TouchAction<>(driver)
                .press(point(x, startY))
                .waitAction(waitOptions(Duration.ofMillis(SLOW_SWIPE_WAIT_MS)))
                .moveTo(point(x, endY))
                .release()
                .perform();

        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void slowSwipeLeft() {
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int width = size.getWidth();
        int height = size.getHeight();

        int y = (int) (height * 0.70);
        int startX = (int) (width * 0.70);
        int endX   = (int) (width * 0.35);

        new TouchAction<>(driver)
                .press(point(startX, y))
                .waitAction(waitOptions(Duration.ofMillis(SLOW_SWIPE_WAIT_MS)))
                .moveTo(point(endX, y))
                .release()
                .perform();

        sleep(SLOW_SWIPE_SLEEP_MS);
    }

    protected void fastSwipeLeftAtY(int y) {
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int width = size.getWidth();

        int startX = (int) (width * 0.78);
        int endX   = (int) (width * 0.36);

        new TouchAction<>(driver)
                .press(point(startX, y))
                .waitAction(waitOptions(Duration.ofMillis(FAST_SWIPE_WAIT_MS)))
                .moveTo(point(endX, y))
                .release()
                .perform();

        sleep(FAST_SWIPE_SLEEP_MS);
    }

    protected void fastSwipeRightAtY(int y) {
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int width = size.getWidth();

        int startX = (int) (width * 0.36);
        int endX   = (int) (width * 0.78);

        new TouchAction<>(driver)
                .press(point(startX, y))
                .waitAction(waitOptions(Duration.ofMillis(FAST_SWIPE_WAIT_MS)))
                .moveTo(point(endX, y))
                .release()
                .perform();

        sleep(FAST_SWIPE_SLEEP_MS);
    }

    protected boolean sweepCatalogRightFromAnchorY(By target, int anchorCenterY, int maxSwipes) {
        for (int i = 0; i < maxSwipes; i++) {
            if (isVisible(target)) return true;
            fastSwipeLeftAtY(anchorCenterY);
        }
        return isVisible(target);
    }

    protected void resetCarouselFromAnchorY(int anchorCenterY, int swipes) {
        for (int i = 0; i < swipes; i++) {
            fastSwipeRightAtY(anchorCenterY);
        }
    }

    private String viewportFingerPrint() {
        try {
            List<WebElement> texts = driver.findElements(
                    By.xpath("(//android.widget.TextView[@text and string-length(@text)>0])[position() <= 6]")
            );

            if (texts == null || texts.isEmpty()) return "EMPTY";

            StringBuilder sb = new StringBuilder();
            for (WebElement el : texts) {
                try { sb.append(el.getText().trim()).append("|"); }
                catch (Exception ignored) {}
            }
            return sb.toString();

        } catch (Exception e) {
            return "ERR";
        }
    }

    // -------------------------------------------------------------------------
    // ✅ TU MÉTODO ROBUSTO (se queda igual)
    // -------------------------------------------------------------------------
    protected boolean ensureVisibleByXpathNoClickDownThenUpIfEnd(String xpath,
                                                                 int maxDownSwipes,
                                                                 int maxUpSwipes) {
        By locator = By.xpath(xpath);

        if (isVisible(locator)) return true;

        int down = maxDownSwipes;
        int up   = maxUpSwipes;

        String lastFinger = "";
        boolean reachedEnd = false;

        for (int i = 0; i < down; i++) {
            if (isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeUp();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) {
                reachedEnd = true;
                break;
            }
            lastFinger = after;
        }

        if (!reachedEnd) return false;

        lastFinger = "";
        for (int i = 0; i < up; i++) {
            if (isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeDown();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        return isVisible(locator);
    }

    // -------------------------------------------------------------------------
    // ✅ ONE-SHOT helpers (ADAPTADOS)
    // -------------------------------------------------------------------------
    private boolean oneShotVerticalSearch(By locator, int maxDownSwipes) {
        if (isVisible(locator)) return true;

        String lastFinger = "";
        int allowed = LIMIT_TOTAL_SWIPE_BUDGET
                ? Math.min(maxDownSwipes, TOTAL_VERTICAL_SWIPE_BUDGET)
                : maxDownSwipes;

        for (int i = 0; i < allowed; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeUp();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) {
                break; // ya no avanza
            }
            lastFinger = after;
        }

        return isVisible(locator);
    }

    private boolean oneShotHorizontalSearch(By locator, int maxRightSwipes) {
        return oneShotHorizontalSearch(locator, maxRightSwipes, null);
    }

    private boolean oneShotHorizontalSearch(By locator, int maxRightSwipes, int[] budgetH) {
        if (isVisible(locator)) return true;

        String lastFinger = "";
        int allowed = maxRightSwipes;

        if (LIMIT_TOTAL_SWIPE_BUDGET && budgetH != null) {
            allowed = Math.min(allowed, budgetH[0]);
            if (allowed <= 0) return isVisible(locator);
        }

        for (int i = 0; i < allowed; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeLeft();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;

            if (LIMIT_TOTAL_SWIPE_BUDGET && budgetH != null) {
                budgetH[0]--;
                if (budgetH[0] <= 0) break;
            }
        }

        return isVisible(locator);
    }

    private boolean oneShotVerticalAndHorizontal(By locator, int maxDownSwipes, int maxRightSwipesPerRow) {
        if (isVisible(locator)) return true;

        String lastFinger = "";

        int allowedV = LIMIT_TOTAL_SWIPE_BUDGET
                ? Math.min(maxDownSwipes, TOTAL_VERTICAL_SWIPE_BUDGET)
                : maxDownSwipes;

        int[] budgetH = new int[]{
                LIMIT_TOTAL_SWIPE_BUDGET ? TOTAL_HORIZONTAL_SWIPE_BUDGET : Integer.MAX_VALUE
        };

        for (int i = 0; i < allowedV; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            if (oneShotHorizontalSearch(locator, maxRightSwipesPerRow, budgetH)) return true;

            if (LIMIT_TOTAL_SWIPE_BUDGET && budgetH[0] <= 0) break;

            String before = viewportFingerPrint();
            slowSwipeUp();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        return isVisible(locator);
    }

    // -------------------------------------------------------------------------
    // ✅ Buscadores con scroll (ADAPTADOS: 1 pasada y STOP)
    // -------------------------------------------------------------------------
    protected void findVisibleOrScrollToXpathAndClick(String xpath, int maxSwipesEachDirection) {
        ensureAppIsInForegroundOrRecover();

        By locator = By.xpath(xpath);

        if (clickIfPresent(locator)) return;

        boolean found;
        if (FAST_FAIL_FIRST_ATTEMPT_ONLY) {
            found = oneShotVerticalSearch(locator, maxSwipesEachDirection);
        } else {
            found = scrollSlowDownThenUpUntilVisible(locator, maxSwipesEachDirection);
        }

        if (!found) {
            takeScreenshotOnFailure();
            throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada. XPath: " + xpath);
        }

        click(locator);
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

    protected void findVisibleOrScrollDownAndRightSlowToXpathAndClick(String xpath,
                                                                      int maxVerticalSwipesEachDirection,
                                                                      int maxRightSwipesPerRow) {
        ensureAppIsInForegroundOrRecover();

        By locator = By.xpath(xpath);

        if (clickIfPresent(locator)) return;

        boolean found;
        if (FAST_FAIL_FIRST_ATTEMPT_ONLY) {
            found = oneShotVerticalAndHorizontal(locator, maxVerticalSwipesEachDirection, maxRightSwipesPerRow);
        } else {
            found = scrollDownUpAndRightSweepUntilVisible(locator, maxVerticalSwipesEachDirection, maxRightSwipesPerRow);
        }

        if (!found) {
            takeScreenshotOnFailure();
            throw new AssertionError("FAST-FAIL: Elemento NO encontrado tras 1 pasada (V/H). XPath: " + xpath);
        }

        click(locator);
    }

    protected boolean scrollDownUpAndRightSweepUntilVisible(By locator,
                                                            int maxVerticalSwipesEachDirection,
                                                            int maxRightSwipesPerRow) {
        if (isVisible(locator)) return true;

        String lastFinger = "";
        for (int i = 0; i < maxVerticalSwipesEachDirection; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            if (sweepRightUntilVisible(locator, maxRightSwipesPerRow)) return true;

            String before = viewportFingerPrint();
            slowSwipeUp();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        lastFinger = "";
        for (int i = 0; i < maxVerticalSwipesEachDirection; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            if (sweepRightUntilVisible(locator, maxRightSwipesPerRow)) return true;

            String before = viewportFingerPrint();
            slowSwipeDown();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        return isVisible(locator);
    }

    protected boolean sweepRightUntilVisible(By locator, int maxRightSwipes) {
        if (isVisible(locator)) return true;

        String lastFinger = "";
        for (int i = 0; i < maxRightSwipes; i++) {
            if (i % 2 == 0 && isVisible(locator)) return true;

            String before = viewportFingerPrint();
            slowSwipeLeft();
            String after = viewportFingerPrint();

            if (after.equals(before) || after.equals(lastFinger)) break;
            lastFinger = after;
        }

        return isVisible(locator);
    }

    // -------------------------------------------------------------------------
    // ✅ TAP Compose-friendly: W3C
    // -------------------------------------------------------------------------
    protected void tapW3C(int x, int y) {
        ensureAppIsInForegroundOrRecover();

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);

        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(Collections.singletonList(tap));
    }

    protected void tapCenterW3C(WebElement el) {
        ensureAppIsInForegroundOrRecover();

        int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
        int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;
        tapW3C(cx, cy - 12);
        takeScreenshot();
    }

    protected void clickOrTapByXpath(String xpath, int timeoutSeconds) {
        ensureAppIsInForegroundOrRecover();

        By by = By.xpath(xpath);

        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = w.until(ExpectedConditions.visibilityOfElementLocated(by));

            try {
                el.click();
                takeScreenshot();
                return;
            } catch (Exception ignore) {}

            tapCenterW3C(el);

        } catch (Exception e) {
            takeScreenshotOnFailure();
            throw new RuntimeException("No se pudo click/tap por xpath: " + xpath, e);
        }
    }

    // -------------------------------------------------------------------------
    // ✅ clickSmart (TU LÓGICA SE QUEDA)
    // -------------------------------------------------------------------------
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
                p = p.findElement(By.xpath(".."));
                if (tryClickElement(p)) return;
                if (tryTapElement(p)) return;
                if (tryClickGestureElement(p)) return;
            } catch (Exception ignored) {
                break;
            }
        }

        takeScreenshotOnFailure();
        throw new RuntimeException("No se pudo hacer click/tap con ningún método para xpath: " + xpath);
    }

    private boolean tryClickElement(WebElement el) {
        try {
            el.click();
            takeScreenshot();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryTapElement(WebElement el) {
        try {
            tapCenterW3C(el);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryClickGestureElement(WebElement el) {
        try {
            int cx = el.getLocation().getX() + (el.getSize().getWidth() / 2);
            int cy = el.getLocation().getY() + (el.getSize().getHeight() / 2);

            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("x", cx);
            args.put("y", cy);

            driver.executeScript("mobile: clickGesture", args);
            takeScreenshot();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // ✅ Intento rápido (para otros flows)
    // -------------------------------------------------------------------------
    protected boolean tryClickIfAlreadyVisible(By locator, int timeoutSeconds) {
        ensureAppIsInForegroundOrRecover();

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

            try {
                el.click();
                takeScreenshot();
                return true;
            } catch (Exception ignored) {}

            tapCenterW3C(el);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    protected void scrollDownSmall() {
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int startX = size.width / 2;
        int startY = (int) (size.height * 0.70);
        int endY   = (int) (size.height * 0.50);

        new TouchAction<>(driver)
                .press(point(startX, startY))
                .waitAction(waitOptions(Duration.ofMillis(200)))
                .moveTo(point(startX, endY))
                .release()
                .perform();
    }

    protected void scrollUpSmall() {
        ensureAppIsInForegroundOrRecover();

        Dimension size = driver.manage().window().getSize();
        int startX = size.width / 2;
        int startY = (int) (size.height * 0.50);
        int endY   = (int) (size.height * 0.70);

        new TouchAction<>(driver)
                .press(point(startX, startY))
                .waitAction(waitOptions(Duration.ofMillis(200)))
                .moveTo(point(startX, endY))
                .release()
                .perform();
    }

    protected boolean ensureVisibleByXpathNoClick(String xpath, int maxVerticalSwipesEachDirection) {
        ensureAppIsInForegroundOrRecover();

        By locator = By.xpath(xpath);

        if (isVisible(locator)) return true;

        if (FAST_FAIL_FIRST_ATTEMPT_ONLY) {
            return oneShotVerticalSearch(locator, maxVerticalSwipesEachDirection);
        }

        return scrollSlowDownThenUpUntilVisible(locator, maxVerticalSwipesEachDirection);
    }

    // -------------------------------------------------------------------------
    // ✅ TU MÉTODO DE SABORES (SE QUEDA)
    // -------------------------------------------------------------------------
    public void seleccionarSaborPorContentDesc2(String contentDesc) {
        ensureAppIsInForegroundOrRecover();

        String xpath = "//android.view.View[@content-desc=\"" + contentDesc + "\"]";
        By locator = By.xpath(xpath);

        int maxRounds = 12;
        int scrollsPerRound = 2;

        if (FAST_FAIL_FIRST_ATTEMPT_ONLY && FAST_FAIL_FLAVORS_ONE_ROUND) {
            maxRounds = 1;
        }

        String lastFinger = "";

        for (int round = 0; round < maxRounds; round++) {

            for (int s = 0; s < scrollsPerRound; s++) {
                String before = viewportFingerPrintPublic();
                slowSwipeUp();
                String after  = viewportFingerPrintPublic();

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
                    "No se encontró el sabor con content-desc: '" + contentDesc +
                            "' tras búsqueda controlada. XPath: " + xpath, e
            );
        }
    }

    public String viewportFingerPrintPublic() {
        try {
            List<WebElement> texts = driver.findElements(
                    By.xpath("(//android.widget.TextView[@text and string-length(@text)>0])[position() <= 6]")
            );

            StringBuilder sb = new StringBuilder();
            for (WebElement el : texts) {
                try { sb.append(el.getText().trim()).append("|"); } catch (Exception ignored) {}
            }
            return sb.toString();

        } catch (Exception e) {
            return "";
        }
    }

    private WebElement findBestCardContainer(WebElement base) {
        try {
            return base.findElement(By.xpath("./ancestor::*[@clickable='true'][1]"));
        } catch (Exception ignored) {}

        try {
            return base.findElement(By.xpath("./ancestor::android.view.View[2]"));
        } catch (Exception ignored) {}

        try {
            return base.findElement(By.xpath("./ancestor::android.view.View[3]"));
        } catch (Exception ignored) {}

        return base;
    }
}
