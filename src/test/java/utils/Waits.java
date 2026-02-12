package utils;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Wrapper simple para esperas.
 * ✅ Optimizado para suites largas (menos tiempo muerto).
 *
 * NOTA: No cambia tu lógica; solo reduce defaults y agrega helpers.
 */
public class Waits {

    private final AndroidDriver driver;

    // ✅ Antes: 8s (muy alto para default en suites grandes)
    // 4s suele ser buen balance. Ajustable.
    private final Duration defaultTimeout = Duration.ofSeconds(6);

    // ✅ Fast path para acciones comunes (cuando la UI suele responder rápido)
    private final Duration fastTimeout = Duration.ofSeconds(6);

    public Waits(AndroidDriver driver) {
        this.driver = driver;
    }

    // -------------------------------------------------------------------------
    // CLICKABLE
    // -------------------------------------------------------------------------
    public WebElement waitClickable(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public WebElement waitClickable(By locator) {
        return waitClickable(locator, defaultTimeout);
    }

    // ✅ Fast clickable (útil cuando ya esperaste pantalla y solo quieres tap rápido)
    public WebElement waitClickableFast(By locator) {
        return waitClickable(locator, fastTimeout);
    }

    // -------------------------------------------------------------------------
    // VISIBLE / PRESENT (más barato que clickable en muchos casos)
    // -------------------------------------------------------------------------
    public WebElement waitVisible(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitVisible(By locator) {
        return waitVisible(locator, defaultTimeout);
    }

    public WebElement waitPresent(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public WebElement waitPresent(By locator) {
        return waitPresent(locator, defaultTimeout);
    }

    // -------------------------------------------------------------------------
    // Opcional: esperar que desaparezca (loaders/spinners)
    // -------------------------------------------------------------------------
    public boolean waitGone(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public boolean waitGone(By locator) {
        return waitGone(locator, defaultTimeout);
    }
}
