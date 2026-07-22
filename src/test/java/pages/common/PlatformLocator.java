package pages.common;

import org.openqa.selenium.By;

import java.util.Objects;

/**
 * Locator agnóstico de plataforma: encapsula el {@link By} correcto para Android
 * (UiAutomator2 / XML de vista nativo) y para iOS (XCUITest / árbol de accesibilidad)
 * detrás de una única declaración, y resuelve el que corresponde en tiempo de
 * ejecución según la plataforma real de la sesión Appium.
 *
 * El código de flujo (page objects, helpers, clases de test) nunca debe preguntar
 * isIOS() ni construir XPaths condicionales para decidir QUÉ buscar — eso vive aquí,
 * una sola vez por elemento. BasePage expone overloads de sus métodos de interacción
 * (click, clickIfPresent, isVisibleQuick, etc.) que aceptan PlatformLocator y resuelven
 * internamente contra {@code isIOS()} antes de delegar en la implementación existente
 * basada en {@link By} — ningún método de bajo nivel se duplica.
 *
 * Android usa tipos de elemento nativos (android.widget.*, android.view.*) y expone
 * texto visible en el atributo @text y accesibilidad en @content-desc. iOS (XCUITest)
 * usa tipos XCUIElementType* y expone el mismo contenido en @label, @name o @value
 * según el tipo de nodo de accesibilidad — de ahí que los factory methods de texto
 * consulten los tres atributos con "or".
 */
public final class PlatformLocator {

    private final By android;
    private final By ios;

    private PlatformLocator(By android, By ios) {
        this.android = android;
        this.ios = ios;
    }

    /**
     * Locator explícito por plataforma — usar cuando la estructura difiere por completo.
     *
     * Ambos lados son obligatorios: un PlatformLocator con un lado null significa que
     * ESA plataforma nunca podrá resolver el elemento (siempre falla en tiempo de
     * ejecución) — se rechaza aquí, en el punto de construcción, en vez de dejar que
     * el fallo aparezca más tarde como un locator "resuelto a null" dentro de un
     * método de interacción.
     */
    public static PlatformLocator of(By android, By ios) {
        Objects.requireNonNull(android, "PlatformLocator.of(): el locator Android no puede ser null");
        Objects.requireNonNull(ios, "PlatformLocator.of(): el locator iOS no puede ser null");
        return new PlatformLocator(android, ios);
    }

    /** Mismo By en ambas plataformas (ya cross-platform, p. ej. By.id de un resource-id compartido). */
    public static PlatformLocator same(By locator) {
        Objects.requireNonNull(locator, "PlatformLocator.same(): el locator no puede ser null");
        return new PlatformLocator(locator, locator);
    }

    /**
     * Texto visible EXACTO. Android: @text (UiAutomator2). iOS: @label, @name o @value
     * (Compose Multiplatform puede exponer el mismo nodo semántico bajo cualquiera de
     * los tres, según el tipo de control) — se consultan los tres con "or".
     */
    public static PlatformLocator byExactText(String text) {
        String t = escapeXpath(text);
        return of(
                By.xpath("//*[@text='" + t + "']"),
                By.xpath("//*[@label='" + t + "' or @name='" + t + "' or @value='" + t + "']")
        );
    }

    /** Como {@link #byExactText(String)} pero tomando el N-ésimo resultado (1-based, como XPath). */
    public static PlatformLocator byExactText(String text, int index) {
        String t = escapeXpath(text);
        return of(
                By.xpath("(//*[@text='" + t + "'])[" + index + "]"),
                By.xpath("(//*[@label='" + t + "' or @name='" + t + "' or @value='" + t + "'])[" + index + "]")
        );
    }

    /** Texto visible que CONTIENE el fragmento dado (contains()), mismo mapeo de atributos que byExactText. */
    public static PlatformLocator byTextContains(String text) {
        String t = escapeXpath(text);
        return of(
                By.xpath("//*[contains(@text,'" + t + "')]"),
                By.xpath("//*[contains(@label,'" + t + "') or contains(@name,'" + t + "') or contains(@value,'" + t + "')]")
        );
    }

    /**
     * Identificador de accesibilidad. Android: @content-desc (UiAutomator2).
     * iOS: @name (XCUITest expone el accessibility identifier / label ahí).
     */
    public static PlatformLocator byAccessibilityId(String id) {
        String v = escapeXpath(id);
        return of(
                By.xpath("//*[@content-desc='" + v + "']"),
                By.xpath("//*[@name='" + v + "']")
        );
    }

    /**
     * Último botón "de acción" VISIBLE en la pantalla actual — usado como equivalente
     * portable de locators puramente POSICIONALES dentro del árbol de Compose
     * (p. ej. "el 7º android.view.View descendiente de ComposeView") que no tienen
     * ancla de texto ni de accesibilidad. Android: último android.widget.Button del
     * árbol. iOS: último XCUIElementTypeButton VISIBLE del árbol (se filtra por
     * @visible='true' — atributo real expuesto por el driver XCUITest de Appium —
     * para no capturar botones fuera de pantalla/ocultos que también cuenten como
     * "último" en el orden del documento).
     *
     * RIESGO CONOCIDO (auditoría de endurecimiento iOS): esta misma función es usada
     * por MÚLTIPLES botones distintos sin ancla de texto/accesibilidad propia —
     * AlimentosLocators.BTN_REGRESARMENU, CinemasHelper.BTN_MODAL_ANY_BUTTON,
     * CinemasHelper.BTN_SI_CAMBIAR_CINE_BUTTON_ABS, y en SelectorPage: personalizar(),
     * agregarCarrito(), Siguiente(), Regresar(), Mas(). En Android cada uno tiene su
     * propio XPath posicional (nodos distintos del árbol Compose); en iOS los ocho
     * colapsan al MISMO locator ("último botón visible"). Si más de un botón sin
     * nombre/label queda visible simultáneamente en la misma pantalla, esta función
     * no puede garantizar cuál de los dos se toca. NO se resolvió inventando un
     * name/label/value sin evidencia — requiere una sesión con dispositivo iOS real
     * (Appium Inspector / WebDriverAgent /source) para descubrir el identificador de
     * accesibilidad real de cada botón, o que el equipo de app agregue un
     * accessibilityIdentifier/testTag estable en el código Compose Multiplatform.
     * Ver reporte de endurecimiento para el detalle por sitio de uso.
     */
    public static PlatformLocator lastActionButton() {
        return of(
                By.xpath("(//android.widget.Button)[last()]"),
                By.xpath("(//XCUIElementTypeButton[@visible='true'])[last()]")
        );
    }

    /** Resuelve el {@link By} correcto para la plataforma indicada. */
    public By resolve(boolean isIOS) {
        return isIOS ? ios : android;
    }

    public By android() { return android; }
    public By ios() { return ios; }

    @Override
    public String toString() {
        return "PlatformLocator{android=" + android + ", ios=" + ios + "}";
    }

    // Misma normalización que CinemasHelper.escapeXpath(): strip de apóstrofos para
    // evitar romper el literal XPath de comilla simple (p. ej. nombres con apóstrofo).
    private static String escapeXpath(String s) {
        return (s == null) ? "" : s.replace("'", "");
    }
}
