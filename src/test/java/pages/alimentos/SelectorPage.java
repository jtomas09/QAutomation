package pages.alimentos;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.interactions.PointerInput.Kind;
import org.openqa.selenium.interactions.PointerInput.MouseButton;
import org.openqa.selenium.interactions.PointerInput.Origin;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.opentest4j.TestAbortedException;
import pages.common.BasePage;
import pages.common.PlatformLocator;
import java.text.Normalizer;
import java.util.Locale;

public class SelectorPage extends BasePage {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelectorPage.class);
    public static final int FAST_VISIBLE_SECONDS = 2;
    private static final String HEADER_SEGUNDO_SABOR = "Selecciona el segundo sabor";
    private static final String HEADER_EXTRA = "Selecciona tu extra";

    public SelectorPage(AppiumDriver driver) {
        super(driver);
    }

    // Tab "Alimentos" del bottom-nav. Android: UiSelector por texto (rama Android más
    // abajo) con fallback posicional Compose sin tocar. iOS: no existe UiAutomator2 —
    // se usa directamente el texto visible "Alimentos" (misma ancla que la estrategia
    // Android preferida), en vez de intentar adivinar la posición del árbol Compose
    // en iOS, que puede no coincidir con la de Android.
    private static final PlatformLocator TAB_ALIMENTOS_BOTTOMNAV = PlatformLocator.of(
            By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[2]/android.view.View/android.view.View[2]/android.view.View[3]"),
            PlatformLocator.byExactText("Alimentos").ios());

    public void abrirMenu() {
        long t0 = System.currentTimeMillis();
        log.info("[TRACE] Inicio SelectorPage.abrirMenu() | hilo={} plataforma={} hora={}",
                Thread.currentThread().getName(), isIOS() ? "iOS" : "Android", t0);
        // UiSelector works from any screen (Android) — estrategia preferida, sin cambios.
        if (!isIOS()) {
            try {
                driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiSelector().text(\"Alimentos\")"
                )).click();
                sleep(800);
                log.info("[TRACE] Fin SelectorPage.abrirMenu() (UiAutomator, Android) | duracionMs={}",
                        System.currentTimeMillis() - t0);
                return;
            } catch (Exception ignored) {}
        }
        // Fallback multiplataforma: Android conserva su xpath posicional original;
        // iOS resuelve por el texto visible "Alimentos" (ver TAB_ALIMENTOS_BOTTOMNAV).
        try {
            this.click(TAB_ALIMENTOS_BOTTOMNAV);
            log.info("[TRACE] Locator encontrado en abrirMenu() | duracionMs={}", System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[TRACE] Locator NUNCA encontrado en abrirMenu() tras espera bloqueante — plataforma={} " +
                    "duracionMs={} excepcion={}", isIOS() ? "iOS" : "Android",
                    System.currentTimeMillis() - t0, e.getMessage());
            this.fallbackTap(TAB_ALIMENTOS_BOTTOMNAV);
        }
        log.info("[TRACE] Fin SelectorPage.abrirMenu() | duracionMs={}", System.currentTimeMillis() - t0);
    }

    public void buscarDippinDots() {
        buscarProducto("Dippin Dots");
    }

    public void buscarComboNachosPareja2() {
        buscarProducto("Combo Nachos en Pareja");
    }

    public void buscarPalomitasSkinkless() {
        buscarProducto("Palomitas Skwinkles");
    }

    public void buscarComboNachosPareja() {
        buscarProducto("Combo Nachos en Pareja");
    }

    public void buscarPinaColada() {
        buscarProducto("Piña Colada");
    }

    public void buscarPalomitas() {
        buscarProducto("Palomitas");
    }

    public void SinQueso() {
        this.clickCardByTextWithFallback("Sin Extra Queso",10);
    }

    public void FrutosPelonPeloRico() {
        this.clickCardByTextWithFallback("Frutos Pelon pelon rico",10);
    }

    public void Cheetos() {
        this.clickCardByTextWithFallback("Cheetos® Mix",10);
    }

    public void MorasMaracuya() {
        this.clickCardByTextWithFallback("Moras Maracuya",10);
    }

    public void MMsCacahuate() {
        this.clickCardByTextWithFallback("Cacahuate 120 g.",10);
    }
    public void MMsCacahuate2() {
        this.clickCardByTextWithFallback("Cacahuate",10);
    }

    public void ManzanaVerde() {
        this.clickCardByTextWithFallback("Manzana Verde",10);
    }

    public void FresaPelonada() {
        this.clickCardByTextWithFallback("Fresa Pelonada",10);
    }

    public void MokaCaramelo() {
        this.clickCardByTextWithFallback("Moka Caramelo",10);
    }

    public void ChocolateBlanco() {
        this.clickCardByTextWithFallback("Chocolate blanco",10);
    }

    public void ChiclePlatano() {
        this.clickCardByTextWithFallback("Chicle Platano",10);
    }

    public void MMs() {
        this.clickCardByTextWithFallback("M&M's®",10);
    }

    public void JollyRancherRaspberry() {
        this.clickCardByTextWithFallback("Jolly Rancher Raspberry",10);
    }

    public void MMsChocolate() {
        this.clickCardByTextWithFallback("Chocolate",10);
    }

    public void MangoChamoy() {
        this.clickCardByTextWithFallback("Mango Chamoy",10);
    }

    public void SandiaPelonada() {
        this.clickCardByTextWithFallback("Sandía Pelonada",10);
    }

    public void buscarSkwinkles() {
        buscarProducto("Skwinkles® Chunks sandia");
    }

    public void EsenciaMenta() {
        this.clickCardByTextWithFallback("Esencia Menta",10);
    }

    public void Capuccino() {
        this.clickCardByTextWithFallback("Cappuccino",10);
    }

    public void MacchiatoCoco() {
        this.clickCardByTextWithFallback("Macchiato cocó",10);
    }

    public void MacchiatoMenta() {
        this.clickCardByTextWithFallback("Macchiato menta",10);
    }

    public void MacchiatoCremaIrlandesa() {
        this.clickCardByTextWithFallback("Macchiato crema irlandesa",10);
    }

    public void MangoTajin() {
        this.clickCardByTextWithFallback("Mango Tajin",10);
    }

    public void CafeMediano() {
        this.clickCardByTextWithFallback("Mediano",10);
    }

    public void LecheDeslactosada() {
        this.clickCardByTextWithFallback("Leche Deslactosada",10);
    }

    public void SalsaChocolate() {
        this.clickCardByTextWithFallback("Salsa de Chocolate Oscuro",10);
    }

    public void ExtraManzana() {
        this.clickCardByTextWithFallback("Extra Manzana Canela",10);
    }

    public void ExtraQuesoPhiladelphia() {
        this.clickCardByTextWithFallback("Extra Queso Philadelphia®",10);
    }

    public void ExtraMermeladaFresa() {
        this.clickCardByTextWithFallback("Extra Mermelada de Fresa",10);
    }

    public void ExtraMermeladaZarzamora() {
        this.clickCardByTextWithFallback("Extra Mermelada de Zarzamora",10);
    }

    public void TeJamaica() {
        this.clickCardByTextWithFallback("Té Mora Jamaica",10);
    }

    public void Tocino() {
        this.clickCardByTextWithFallback("Extra Tocino",10);
    }

    public void ExtraQuesoManchego() {
        this.clickCardByTextWithFallback("Extra Queso Manchego",10);
    }

    public void Champinon() {
        this.clickCardByTextWithFallback("Extra Champiñon",10);
    }

    public void ExtraJamonPavo() {
        this.clickCardByTextWithFallback("Extra Jamón Pavo",10);
    }

    public void LecheAlmendra() {
        this.clickCardByTextWithFallback("Leche Almendra",10);
    }

    public void EsenciaVainilla() {
        this.clickCardByTextWithFallback("Esencia Vainilla",10);
    }

    public void CremaIrlandesa() {
        this.clickCardByTextWithFallback("Crema Irlandesa",10);
    }

    public void buscarMM() {
        buscarProducto("M&M's®");
    }

    public void buscarCrepasDulces() {
        buscarProducto("Crepas Dulces Premium");
    }

    public void buscarComboICEE() {
        buscarProducto("Combo ICEE® con Skwinkles®");
    }

    public void buscarHotDogTakis() {
        buscarProducto("Hot Dog Takis");
    }

    public void buscarRefresco() {
        buscarProducto("Refresco");
    }

    public void Takis() {
        this.clickCardByTextWithFallback("Takis® Fuego",10);
    }
    public void TakisJr() {
        this.clickCardByTextWithFallback("Takis®",10);
    }

    public void Sidral() {
        this.clickCardByTextWithFallback("Sidral Mundet® Sin Azúcar",10);
    }

    public void FuzeTe() {
        this.clickCardByTextWithFallback("Fuze Tea® Sin Azúcar",10);
    }

    public void Fanta() {
        this.clickCardByTextWithFallback("Fanta® Naranja Sin Azúcar",10);
    }

    public void Sprite() {
        this.clickCardByTextWithFallback("Sprite® Sin Azúcar",10);
    }

    public void CocaColaZero() {
        this.clickCardByTextWithFallback("Coca-Cola® Zero Azúcar",10);
    }

    public void CocaColaLigth() {
        this.clickCardByTextWithFallback("Coca-Cola® Light",10);
    }

    public void Coco() {
        this.clickCardByTextWithFallback("Coco",10);
    }

    public void DelValle() {
        this.clickCardByTextWithFallback("Del Valle Frut® ",10);
    }
    public void DelValle2() {
        this.clickCardByTextWithFallback("Del Valle Frut",10);
    }

    public void HieloRegular() {
        this.clickCardByTextWithFallback("Hielo Regular",10);
    }

    public void PocoHielo() {
        this.clickCardByTextWithFallback("Poco Hielo",10);
    }

    public void SinHielo() {
        this.clickCardByTextWithFallback("Sin Hielo",10);
    }

    public void Caramelo() {
        this.clickCardByTextWithFallback("Caramelo",10);
    }

    public void CheetosMix() {
        this.clickCardByTextWithFallback("Cheetos® Mix",10);
    }

    public void Doritos() {
        this.clickCardByTextWithFallback("Doritos® Nacho",10);
    }
    public void ExtraQueso() {
        this.clickCardByTextWithFallback("Extra Queso",10);
    }

    public void MixTakisFuego() {
        this.clickCardByTextWithFallback("Mix Takis Fuego",10);
    }

    public void MixDoritos() {
        this.clickCardByTextWithFallback("Mix Doritos® Nacho",10);
    }

    public void Toppin() {
        this.clickCardByTextWithFallback("Topping Pelon pelo rico",10);
    }

    public void SkwinklessRellenos() {
        this.clickCardByTextWithFallback("Skwinkles® Rellenos",10);
    }

    public void PelonPelonazo() {
        this.clickCardByTextWithFallback("Pelón Pelonazo®",10);
    }

    public void SkwinklessSpaguetti() {
        this.clickCardByTextWithFallback("Skwinkles® Salsagheti",10);
    }

    public void buscarCrepaFrappe() {
        buscarProducto("Crepa dulce + Frappé agua");
    }

    public void buscarComboPretzelPareja() {
        buscarProducto("Combo Pretzel en Pareja");
    }

    public void buscarComboNachos() {
        buscarProducto("Combo Nachos");
    }

    public void buscarFrappeAgua() {
        buscarProducto("Frappé Agua");
    }

    public void buscarMaxiComboFamiliarJumbo() {
        buscarProducto("Maxicombo Familiar Jumbo");
    }

    public void buscarFrappeLeche() {
        buscarProducto("Frappé Leche");
    }
    public void buscarComboClasico() {
        buscarProducto("Combo Clásico");
    }
    public void buscarComboJunior() {
        buscarProducto("Combo Junior");
    }

    // ── Búsqueda directa vía buscador nativo (estándar único, Fase de refactor) ──
    //
    // Reemplaza la estrategia de ancla + scroll horizontal de carrusel
    // (clickRightFromXXXAnchor, eliminada de este archivo) para TODOS los
    // productos: es más rápida, estable y no depende del orden del catálogo.
    // buscarProducto() centraliza el flujo que antes se duplicaba en cada
    // buscarXxx() individual (abrir lupa → esperar EditText → escribir →
    // esperar resultados → seleccionar, tolerante a acentos/®/mayúsculas).

    /**
     * Búsqueda directa de un producto usando la lupa del menú de alimentos.
     * Flujo: abrir lupa → esperar EditText → escribir nombre → esperar
     * resultados → seleccionar (exacto, o tolerante a variaciones del
     * catálogo: acentos, ®, mayúsculas, espaciado, variantes ortográficas
     * menores). Seleccionar el resultado cierra la búsqueda de forma
     * implícita al navegar a la pantalla del producto — no hace falta un
     * paso adicional de cierre.
     */
    // Campo de búsqueda del menú de Alimentos. Android: el EditText nativo de Compose
    // (android.widget.EditText). iOS: XCUITest no tiene ese tipo — se acepta tanto
    // XCUIElementTypeSearchField como XCUIElementTypeTextField porque, sin inspector
    // en un dispositivo real, no se puede confirmar cuál expone Compose Multiplatform
    // para este control (ver reporte de migración: locator sin verificar en iOS).
    private static final By CAMPO_BUSQUEDA_ALIMENTOS_IOS =
            By.xpath("//XCUIElementTypeSearchField | //XCUIElementTypeTextField");

    private void buscarProducto(String nombreProducto) {
        long t0 = System.currentTimeMillis();
        log.info("[BuscarProducto] ENTER producto='{}'", nombreProducto);

        // El ícono de búsqueda del menú de alimentos tiene accessibility id "Buscar"
        // (Android: @content-desc, iOS: @name) — parent View, no el TextView hijo.
        this.click(PlatformLocator.byAccessibilityId("Buscar"));
        this.sleep(600);

        // Foco en el campo de texto y escritura — sin ®/™/© (algunos teclados/campos
        // no los aceptan bien); el match posterior sí tolera esos caracteres.
        this.click(PlatformLocator.of(By.className("android.widget.EditText"), CAMPO_BUSQUEDA_ALIMENTOS_IOS));
        String consulta = nombreProducto.replaceAll("[®™©]", "").trim();
        // "mobile: type" es un comando cross-platform de Appium (UiAutomator2 y XCUITest
        // lo implementan igual) — no requiere rama de plataforma.
        this.driver.executeScript("mobile: type", Map.of("text", consulta));
        this.sleep(1500);

        // Selección: exacto literal → exacto normalizado → fuzzy validado por tokens
        // (ver encontrarResultadoTolerante). "exacta" (el booleano) es la ÚNICA señal
        // que decide si se hace clic — nunca se hace clic si el log dice NO.
        PlatformLocator exact = PlatformLocator.byExactText(nombreProducto);
        WebElement resultado;
        boolean exacta;
        String textoEncontrado;

        if (isVisibleQuick(exact)) {
            resultado       = null; // ya visible por locator directo, no hace falta el WebElement
            exacta          = true;
            textoEncontrado = nombreProducto;
        } else {
            resultado       = encontrarResultadoTolerante(nombreProducto);
            exacta          = resultado != null;
            textoEncontrado = resultado != null ? textoDeResultado(resultado) : "(ninguno)";
        }

        log.info("[BUSQUEDA] Producto solicitado: {}", nombreProducto);
        log.info("[BUSQUEDA] Producto encontrado: {}", textoEncontrado);
        log.info("[BUSQUEDA] Coincidencia exacta: {}", exacta ? "SI" : "NO");

        if (exacta) {
            if (resultado != null) tapCenterW3C(resultado);
            else this.click(exact);
        } else {
            // No se selecciona ningún producto — el clic sobre el locator exacto original
            // no encontrará nada y lanzará una excepción clara (el flujo existente decide
            // qué hacer con eso, p. ej. SKIPPED si corresponde al guard de "producto no
            // disponible").
            this.click(exact);
        }
        log.info("[BuscarProducto] EXIT producto='{}' | {}ms", nombreProducto, System.currentTimeMillis() - t0);
    }

    /** Texto visible de un resultado de búsqueda: @text en Android, @label/@name/@value en iOS. */
    private String textoDeResultado(WebElement el) {
        if (el == null) return null;
        if (!isIOS()) return el.getAttribute("text");
        String v = el.getAttribute("label");
        if (v == null || v.isBlank()) v = el.getAttribute("name");
        if (v == null || v.isBlank()) v = el.getAttribute("value");
        return v;
    }

    // Similitud mínima POR TOKEN (0-100) para el fallback fuzzy — solo se aplica cuando
    // ambos nombres tienen el MISMO número de tokens y cada token ocupa la MISMA
    // posición; nunca decide por similitud global de la cadena completa. Calibrado
    // contra el caso real "Oscuro"/"Obscuro" (85.7% por token) para que siga
    // aceptándose, mientras cualquier token realmente distinto ("Mix" vs "Familiar",
    // ~12%) queda muy por debajo.
    private static final double SIMILITUD_MINIMA_TOKEN = 80.0;

    /**
     * Selecciona el producto en los resultados visibles, en orden ESTRICTO de
     * prioridad (nunca "el primer parecido", nunca "el de mayor similitud global"):
     *   1. (ya resuelto en buscarProducto(): coincidencia exacta del texto tal cual)
     *   2. Coincidencia EXACTA tras normalizar (®/™/©, apóstrofes/comillas rectas y
     *      tipográficas, acentos, mayúsculas, espacios múltiples) — por IGUALDAD de
     *      la cadena completa, nunca por "contains": "MM's", "MM's®", "MMs", "MM´s"
     *      son el mismo texto normalizado, pero "Maxicombo Mix" NO se considera
     *      "contenido en" "Maxicombo Familiar Jumbo" solo por compartir el prefijo.
     *   3. Solo si NINGÚN candidato coincide exacto: fuzzy validado por tokens —
     *      candidato y producto buscado deben tener el MISMO NÚMERO de tokens
     *      (separados por espacio) y CADA token debe superar SIMILITUD_MINIMA_TOKEN
     *      contra el token de la MISMA posición. Esto es deliberadamente más
     *      estricto que un puntaje de similitud sobre la cadena completa: "Maxicombo
     *      Mix" (2 tokens) nunca puede validar contra "Maxicombo Micha Mix" (3
     *      tokens) sin importar cuán "parecidas" luzcan las cadenas completas,
     *      porque el conteo de tokens ya no coincide.
     */
    // Candidatos con texto visible en los resultados de búsqueda. Android: TextView
    // con @text no vacío. iOS: cualquier nodo con @label o @value no vacío (XCUITest
    // no tiene un único tipo "texto"; Compose Multiplatform puede usar cualquiera).
    // NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final By CANDIDATOS_TEXTO_BUSQUEDA_IOS =
            AppiumBy.iOSNsPredicateString("label.length > 0 OR value.length > 0");

    private WebElement encontrarResultadoTolerante(String nombreProducto) {
        String target = normalizeForSearch(nombreProducto);
        By candidatosLocator = isIOS()
                ? CANDIDATOS_TEXTO_BUSQUEDA_IOS
                : By.xpath("//android.widget.TextView[string-length(@text) > 0]");

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            java.util.List<WebElement> candidatos = driver.findElements(candidatosLocator);

            // ── Tier 2: coincidencia exacta normalizada (igualdad de cadena completa) ──
            for (WebElement candidato : candidatos) {
                try {
                    if (!candidato.isDisplayed()) continue;
                    String texto = textoDeResultado(candidato);
                    if (texto == null || texto.isBlank()) continue;
                    if (normalizeForSearch(texto).equals(target)) return candidato;
                } catch (Exception ignored) {}
            }

            // ── Tier 3: fuzzy validado por tokens (mismo conteo + misma posición) ──
            String[] targetTokens = target.split("\\s+");
            for (WebElement candidato : candidatos) {
                try {
                    if (!candidato.isDisplayed()) continue;
                    String texto = textoDeResultado(candidato);
                    if (texto == null || texto.isBlank()) continue;
                    String[] candidatoTokens = normalizeForSearch(texto).split("\\s+");
                    if (coincidenTokensValidados(targetTokens, candidatoTokens)) return candidato;
                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        return null;
    }

    /**
     * true solo si ambos arreglos tienen la MISMA longitud y cada token en la
     * posición i supera SIMILITUD_MINIMA_TOKEN contra el token i del otro arreglo —
     * "todos los tokens principales presentes y en el mismo orden", nunca una
     * cadena adicional/faltante en medio (eso es exactamente lo que distingue
     * "Maxicombo Mix" de "Maxicombo Micha Mix": 2 tokens vs 3).
     */
    private static boolean coincidenTokensValidados(String[] targetTokens, String[] candidatoTokens) {
        if (targetTokens.length != candidatoTokens.length) return false;
        for (int i = 0; i < targetTokens.length; i++) {
            if (similitudPorcentual(targetTokens[i], candidatoTokens[i]) < SIMILITUD_MINIMA_TOKEN) return false;
        }
        return true;
    }

    /** Similitud porcentual (0-100) basada en distancia de Levenshtein normalizada. */
    private static double similitudPorcentual(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 100;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 100;
        int distancia = distanciaLevenshtein(a, b);
        return (1.0 - ((double) distancia / maxLen)) * 100.0;
    }

    /** Distancia de Levenshtein clásica (mínimo de inserciones/borrados/sustituciones). */
    private static int distanciaLevenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    public void buscarTeCaliente() {
        buscarProducto("Té caliente");
    }

    public void buscarAmericano() {
        buscarProducto("Americano");
    }

    public void buscarMokaObscuro() {
        buscarProducto("Moka Obscuro");
    }

    public void buscarCapuccino() {
        buscarProducto("Capuccino");
    }

    public void buscarChocolate() {
        buscarProducto("Chocolate");
    }

    public void buscarPretzel() {
        buscarProducto("Pretzel");
    }

    public void buscarCheeseCake() {
        buscarProducto("Cheesecake");
    }
    public void TeMentaManzanilla() {
        this.clickCardByTextWithFallback("Té Menta Manzanilla", 10);
    }

    public void clickContinuar() {
        this.clickCardByTextWithFallback("Continuar", 10);
    }

    public void buscarCrepaSalada() {
        buscarProducto("Crepas Saladas Premium");
    }

    public void buscarCrepaDulce2() {
        buscarProducto("Crepas Dulces 2 ingredientes");
    }

    public void buscarCrepasDulces1() {
        buscarProducto("Crepas Dulces 1 ingrediente");
    }

    public void buscarCrepaSalada1() {
        buscarProducto("Crepas Saladas 1 Ingrediente");
    }

    public void buscarCornetto() {
        buscarProducto("Cornetto®");
    }

    public void buscarHersheys() {
        buscarProducto("Hershey's®");
    }

    public void buscarSnickers() {
        buscarProducto("Snickers®");
    }

    public void buscarQuesadilla() {
        buscarProducto("Quesadilla");
    }
    public void buscarPalomitasSkwinkles() {
        buscarProducto("Palomitas Skwinkles");
    }
    public void buscarMaxiComboFamiliar() {
        buscarProducto("Maxicombo Familiar Jumbo");
    }

    public void buscarSnackBoneless() {
        buscarProducto("Plato Snack Boneless");
    }
    public void buscarCrepaFrappeLeche() {
        buscarProducto("Plato Snack Boneless");
    }

    public void buscarMiniDogs() {
        buscarProducto("Mini Dogs VIP");
    }

    public void buscarPapasCrisscut() {
        buscarProducto("Papas Crisscut");
    }

    public void buscarNachosPremium() {
        buscarProducto("Nachos Premium");
    }

    public void buscarHotDog() {
        buscarProducto("Hot Dog");
    }

    public void buscarHotDogGuacamole() {
        buscarProducto("Hot Dog Guacamole");
    }

    public void buscarTexasDog() {
        buscarProducto("Texas Dog");
    }

    public void buscarPapasFritas() {
        buscarProducto("Papas Fritas");
    }

    public void cerrarPantalla() {
        long t0 = System.currentTimeMillis();
        log.info("[TRACE] Inicio SelectorPage.cerrarPantalla() | hilo={} plataforma={}",
                Thread.currentThread().getName(), isIOS() ? "iOS" : "Android");
        // clickIfPresent() no bloquea (findElementsFast usa implicitlyWait=0) — en iOS este
        // locator Android-only (android.view.ViewGroup/.../android.widget.Button) simplemente
        // no encuentra nada y retorna false de inmediato, sin tap y sin excepción.
        boolean clicked = this.clickIfPresent(By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.widget.Button"));
        log.info("[TRACE] Fin SelectorPage.cerrarPantalla() | clicked={} duracionMs={}",
                clicked, System.currentTimeMillis() - t0);
    }

    /**
     * Abre el carrito de compras usando múltiples estrategias para evitar fallos
     * por ausencia del badge "1" en la ventana de tiempo inicial.
     *
     * Estrategias (en orden):
     *   1. Badge numérico visible (cualquier número 1-9)
     *   2. Ícono de carrito por content-desc o resource-id
     *   3. Coordenada fija del ícono (esquina superior derecha del header)
     *
     * Cada estrategia verifica que la pantalla de carrito se abrió realmente.
     * Se realizan hasta 3 intentos antes de fallar.
     */
    public void abrirCarrito() {
        long t0 = System.currentTimeMillis();
        log.info("[PERF] Paso: Abrir carrito");
        log.info("[PERF] Inicio: {}", t0);
        log.info("[abrirCarrito] Intentando abrir carrito...");
        Exception lastError = null;

        for (int intento = 1; intento <= 3; intento++) {
            try {
                if (intentarAbrirCarritoInterno(intento)) {
                    log.info("[abrirCarrito] Carrito abierto correctamente (intento {})", intento);
                    log.info("[PERF] Fin: {} | Duración: {}ms", System.currentTimeMillis(), System.currentTimeMillis() - t0);
                    log.info("[PERF] Paso: Carrito abierto | Duración: {}ms", System.currentTimeMillis() - t0);
                    return;
                }
                log.warn("[abrirCarrito] Intento {} ejecutado pero pantalla no detectada.", intento);
            } catch (Exception e) {
                lastError = e;
                log.warn("[abrirCarrito] Intento {} fallido: {}", intento, e.getMessage());
            }
            sleep(800);
        }
        log.info("[PERF] Fin: {} | Duración: {}ms (FALLO)", System.currentTimeMillis(), System.currentTimeMillis() - t0);
        throw new RuntimeException("No se pudo abrir el carrito correctamente tras 3 intentos", lastError);
    }

    // Badge numérico (1-9) del carrito. Android: @text. iOS: @label/@name/@value.
    private static final By BADGE_CARRITO_ANDROID = By.xpath(
            "//android.widget.TextView[@text='1' or @text='2' or @text='3' " +
            "or @text='4' or @text='5' or @text='6' or @text='7' or @text='8' or @text='9']");
    // NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
    private static final By BADGE_CARRITO_IOS = AppiumBy.iOSNsPredicateString(
            "label == '1' OR label == '2' OR label == '3' OR label == '4' OR label == '5' " +
            "OR label == '6' OR label == '7' OR label == '8' OR label == '9' " +
            "OR name == '1' OR name == '2' OR name == '3' OR name == '4' OR name == '5' " +
            "OR name == '6' OR name == '7' OR name == '8' OR name == '9' " +
            "OR value == '1' OR value == '2' OR value == '3' OR value == '4' OR value == '5' " +
            "OR value == '6' OR value == '7' OR value == '8' OR value == '9'");

    private boolean intentarAbrirCarritoInterno(int intento) {
        // Estrategia 1: badge numérico en el header (cualquier número 1-9)
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            java.util.List<WebElement> badges = driver.findElements(
                    isIOS() ? BADGE_CARRITO_IOS : BADGE_CARRITO_ANDROID);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            for (WebElement badge : badges) {
                try {
                    Rectangle r = badge.getRect();
                    int cx = r.getX() + r.getWidth() / 2;
                    int cy = r.getY() + r.getHeight() / 2;
                    if (cy < driver.manage().window().getSize().getHeight() * 0.15) {
                        log.info("[abrirCarrito] Badge encontrado en ({},{})", cx, cy);
                        tapCarrito(cx, cy);
                        if (estaEnPantallaCarrito()) return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        // Estrategia 2: ícono de carrito por content-desc/resource-id (Android) o
        // name/label (iOS — XCUITest no tiene resource-id ni content-desc).
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            // NSPredicate en iOS — ver nota de rendimiento en PlatformLocator.byExactText().
            By iconLocator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "name == 'Carrito' OR name == 'Cart' OR name == 'carrito' OR name == 'basket' " +
                        "OR label == 'Carrito' OR label == 'Cart' OR label == 'carrito' OR label == 'basket'")
                    : By.xpath("//*[@content-desc='Carrito' or @content-desc='Cart' or " +
                        "contains(@resource-id,'cart') or contains(@resource-id,'carrito') or " +
                        "@content-desc='carrito' or @content-desc='basket']");
            WebElement icon = driver.findElement(iconLocator);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            Rectangle r = icon.getRect();
            log.info("[abrirCarrito] Ícono carrito encontrado por content-desc ({},{})",
                    r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
            tapCarrito(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
            if (estaEnPantallaCarrito()) return true;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        // Estrategia 3: coordenada fija del ícono (esquina superior derecha, ~94% × 6.7%)
        Dimension screen = driver.manage().window().getSize();
        int cartX = (int)(screen.getWidth()  * 0.945);
        int cartY = (int)(screen.getHeight() * 0.067);
        log.info("[abrirCarrito] Estrategia por coordenada fija: ({},{})", cartX, cartY);
        tapCarrito(cartX, cartY);
        sleep(600);
        return estaEnPantallaCarrito();
    }

    private void tapCarrito(int x, int y) {
        log.info("[abrirCarrito] Tap coordenadas -> X:{} Y:{}", x, y);
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(new Pause(finger, Duration.ofMillis(120)));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(tap));
        log.info("[abrirCarrito] Tap ejecutado correctamente");
    }

    private static final By SEÑALES_PANTALLA_CARRITO_ANDROID = By.xpath(
            "//*[contains(@text,'Carrito') or contains(@text,'carrito') " +
            "or contains(@text,'Continuar') or contains(@text,'Ir a pagar') " +
            "or contains(@text,'tu orden') or contains(@text,'Boletos')]");
    // NSPredicate — llamado tras cada estrategia de intentarAbrirCarritoInterno() (hasta
    // 3) × hasta 3 intentos en abrirCarrito() — ver nota de rendimiento en
    // PlatformLocator.byExactText().
    private static final By SEÑALES_PANTALLA_CARRITO_IOS = AppiumBy.iOSNsPredicateString(
            "label CONTAINS 'Carrito' OR label CONTAINS 'carrito' " +
            "OR label CONTAINS 'Continuar' OR label CONTAINS 'Ir a pagar' " +
            "OR label CONTAINS 'tu orden' OR label CONTAINS 'Boletos' " +
            "OR value CONTAINS 'Carrito' OR value CONTAINS 'carrito' " +
            "OR value CONTAINS 'Continuar' OR value CONTAINS 'Ir a pagar' " +
            "OR value CONTAINS 'tu orden' OR value CONTAINS 'Boletos' " +
            "OR name CONTAINS 'Carrito' OR name CONTAINS 'carrito' " +
            "OR name CONTAINS 'Continuar' OR name CONTAINS 'Ir a pagar' " +
            "OR name CONTAINS 'tu orden' OR name CONTAINS 'Boletos'");

    private boolean estaEnPantallaCarrito() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            boolean en = !driver.findElements(
                    isIOS() ? SEÑALES_PANTALLA_CARRITO_IOS : SEÑALES_PANTALLA_CARRITO_ANDROID).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            if (en) log.info("[abrirCarrito] Pantalla carrito detectada");
            return en;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return false;
        }
    }

    // NOTA-MIGRACION: locator Android original 100% posicional (sin ancla de texto ni
    // de accesibilidad) — se preserva EXACTO para Android. El equivalente iOS
    // ("último botón de la pantalla") es mejor-esfuerzo y debe verificarse contra un
    // dispositivo real antes de confiar en él (ver reporte de migración).
    public void personalizar() {
        this.click(PlatformLocator.of(
                By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button"),
                PlatformLocator.lastActionButton().ios()));
    }

    // NOTA-MIGRACION: mismo caso que personalizar() — locator posicional Android
    // preservado exacto; equivalente iOS es mejor-esfuerzo (ver reporte de migración).
    public void agregarCarrito() {
        this.click(PlatformLocator.of(
                By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button"),
                PlatformLocator.lastActionButton().ios()));
    }

    public void ManzanaCanela() {
        this.clickByExactText("Manzana Canela");
    }

    public void MermeladaZarzamora() {
        this.clickByExactText("Mermelada de zarzamora");
    }

    public void QuesoPhiladelphia() {
        this.clickByExactText("Queso Philadelphia®");
    }

    public void QuesoManchego() {
        this.clickByExactText("Queso machego");
    }

    public void Champiqueso() {
        this.clickByExactText("Champiqueso con queso Philadelphia®");
    }

    public void ChampiquesoManchego() {
        this.clickByExactText("Champiqueso con queso mancheco");
    }

    public void Nutella() {
        this.clickByExactText("Nutella®");
    }

    public void PLlevar() {
        this.clickByExactText("Para Llevar");
    }

    public void Jumbo() {
        this.clickByAccessibilityId("Jumbo");
    }

    public void TeMediano() {
        this.clickByExactText("Mediano Caliente");
    }

    public void CafeDescafeinado() {
        this.clickByExactText("Café Descafeinado");
    }

    public void Grandes() {
        this.clickByExactText("Grandes");
    }

    public void ChocolateMediano() {
        this.clickByExactText("Mediano");
    }

    public void Grande() {
        this.clickByExactText("Grande");
    }

    public void seismili() {
        this.clickByExactText("600 ML");
    }

    public void Mango() {
        this.clickByExactText("Mango", 1);
    }

    public void Adobadas() {
        this.clickByExactText("Adobadas");
    }

    public void Skittles() {
        this.clickByExactText("Skittles®");
    }

    public void NachosChicos() {
        this.clickByExactText("Chicos");
    }

    public void Cacahuate120g() {
        this.clickByExactText("Cacahuate 120 g.");
    }

    public void NachosNachos() {
        this.clickByExactText("Nachos", 2);
    }

    public void CookiesCream() {
        this.clickByExactText("Cookies & Cream");
    }

    public void NachosTajin() {
        this.clickByExactText("NACHOS TAJIN");
    }

    public void Medianas() {
        this.clickByExactText("Medianas");
    }

    public void Chicas() {
        this.clickByExactText("Chicas");
    }

    public void CarlosV() {
        this.clickByExactText("Carlos V®");
    }

    public void Chicas2() {
        this.clickByExactText("Chicos");
    }

    public void Res() {
        this.clickByExactText("Res");
    }

    public void Boneless() {
        this.clickByExactText("Boneless");
    }

    // NOTA-MIGRACION: posicional sin ancla de texto/accesibilidad — Android preservado
    // exacto; equivalente iOS mejor-esfuerzo, requiere verificación en dispositivo real
    // (riesgo: si "Regresar"/"Mas" coexisten visibles en la misma pantalla, "último
    // botón" puede ambigüarse entre los tres — ver reporte de migración).
    public void Siguiente() {
        this.click(PlatformLocator.of(
                By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]/android.view.View/android.widget.Button"),
                PlatformLocator.lastActionButton().ios()));
    }

    public void seisoz() {
        this.clickByExactText("6 Oz");
    }

    public void FresaCoco() {
        this.clickByExactText("Fresa-Coco");
    }

    public void nachosBoneless() {
        this.clickByExactText("Nachos Boneless");
    }

    public void nachosBrisquet() {
        this.clickByExactText("Nachos Brisket de Res");
    }


    public void Chico() {
        this.clickByExactText("Chico");
    }

    public void Mediano() {
        this.clickByExactText("Mediano");
    }

    public void Guacamole() {
        this.clickByExactText("Guacamole");
    }

    public void TexasDog() {
        this.clickByExactText("Texas Dog");
    }

    public void Amareto() {
        this.clickByExactText("Piña Colada Amareto");
    }

    public void CocaCola() {
        this.clickByExactText("Coca-Cola®");
    }

    public void PinaColada() {
        this.clickByExactText("Piña Colada Grande");
    }

    public void FrambuesaAzul() {
        this.clickByExactText("Frambuesa Azul", 1);
    }

    public void Midori() {
        this.clickByExactText("Piña Colada Midori");
    }

    public void kahlua() {
        this.clickByExactText("Piña Colada Kahlua");
    }

    public void Pepino() {
        this.clickByExactText("Pepino");
    }

    public void Manzana() {
        this.clickByExactText("Manzana Verde");
    }

    public void Cereza() {
        this.clickByExactText("Cereza");
    }

    // NOTA-MIGRACION: ver comentario en Siguiente() — mismo caso.
    public void Regresar() {
        this.click(PlatformLocator.of(
                By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.view.View/android.view.View[1]/android.widget.Button"),
                PlatformLocator.lastActionButton().ios()));
    }

    // NOTA-MIGRACION: ver comentario en Siguiente() — mismo caso.
    public void Mas() {
        this.click(PlatformLocator.of(
                By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]/android.widget.Button"),
                PlatformLocator.lastActionButton().ios()));
    }

    public void Algodon() {
        this.clickByExactText("Algodon de azucar");
    }


    // ── Búsqueda tolerante (fuzzy matching) ──────────────────────────────────

    /**
     * Normalización usada ÚNICAMENTE para comparar el texto buscado contra los
     * resultados que devuelve la app — nunca para el texto que se escribe en el
     * buscador (buscarProducto() sigue escribiendo exactamente lo solicitado).
     * Ignora símbolos comerciales (®™©), ampersand/apóstrofos/comillas (rectas y
     * tipográficas), acentos, mayúsculas y espacios múltiples, para que variantes
     * de catálogo como "Cornetto®", "M&M's®" o "Skwinkles®" se reconozcan como el
     * mismo producto que "Cornetto", "MM's" o "Skwinkles".
     */
    private static String normalizeForSearch(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        s = s.replaceAll("[®™©°&'’‘`´\"“”]", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String escapeXpathValue(String value) {
        if (value == null) return "";
        return value.replace("\"", "'").replace("\\", "");
    }

    private boolean tryClickByXpathContains(String xpath, String logLabel) {
        return tryClickByLocatorContains(By.xpath(xpath), logLabel);
    }

    /**
     * Variante By — permite pasar un locator NSPredicate (iOS) en vez de forzar XPath.
     * Ver nota de rendimiento en PlatformLocator.byExactText(). Llamado hasta 2 veces
     * por término candidato (hasta 4 términos) en tryFuzzyClick().
     */
    private boolean tryClickByLocatorContains(By locator, String logLabel) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            java.util.List<WebElement> candidates = driver.findElements(locator);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            for (WebElement candidate : candidates) {
                try {
                    if (!candidate.isDisplayed()) continue;
                    String actual = isIOS()
                            ? textoDeResultado(candidate)
                            : candidate.getAttribute("text");
                    if ((actual == null || actual.isBlank()) && !isIOS()) actual = candidate.getAttribute("content-desc");
                    if (actual == null || actual.isBlank()) continue;
                    log.info("[Fuzzy] Match: '{}' → búsqueda '{}'", actual, logLabel);
                    tapCenterW3C(candidate);
                    return true;
                } catch (Exception inner) {
                    rethrowIfAborted(inner);
                }
            }
        } catch (Exception e) {
            rethrowIfAborted(e);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        return false;
    }

    /**
     * Búsqueda tolerante a variaciones del catálogo:
     * mayúsculas/minúsculas, acentos, caracteres especiales (®, ™)
     * y diferencias ortográficas menores (ej: "Obscuro" / "Oscuro", "Sandía" / "Sandia").
     *
     * Telemetría: [BUSQUEDA] Producto=X | Estrategia=fuzzy | Resultado=ENCONTRADO/NO
     */
    private boolean tryFuzzyClick(String targetText) {
        if (targetText == null || targetText.isBlank()) return false;
        long t0 = System.currentTimeMillis();
        log.info("[Fuzzy] ENTER producto='{}' | implicitlyWait=0 via tryClickByXpathContains", targetText);
        String norm = normalizeForSearch(targetText);
        String[] words = norm.split("\\s+");

        java.util.List<String> searchTerms = new java.util.ArrayList<>();
        // Sin caracteres especiales (® → vacío)
        String withoutSpecial = targetText.replaceAll("[®™©°]", "").trim();
        if (!withoutSpecial.equals(targetText)) searchTerms.add(withoutSpecial);
        // Normalizado sin acentos
        if (!norm.equals(targetText.toLowerCase(Locale.ROOT))) searchTerms.add(norm);
        // Primera palabra + prefijo de la segunda (tolera "Obscuro"/"Oscuro", "Cocó"/"Coco")
        if (words.length >= 2) {
            int prefLen = Math.min(4, words[1].length());
            searchTerms.add(words[0] + " " + words[1].substring(0, prefLen));
        }
        // Primera palabra sola (más permisivo, solo si tiene >4 letras)
        if (words.length >= 1 && words[0].length() > 4) {
            searchTerms.add(words[0]);
        }

        // El descenso vertical+peek recorre el catálogo COMPLETO de forma exhaustiva y
        // nunca retrocede. Si ya se ejecutó una vez para un término y no encontró nada,
        // un segundo/tercer/cuarto intento con otro término no puede descubrir nada
        // nuevo (no queda catálogo sin recorrer) — repetirlo fue la causa confirmada de
        // los 180-220 s observados en Búsqueda-Fallback (hasta 4 recorridos completos
        // redundantes del mismo catálogo, uno por cada término candidato). Se ejecuta
        // como máximo UNA vez por llamada.
        boolean uia2ScrollExhausted = false;

        for (String term : searchTerms) {
            String safeT = escapeXpathValue(term);
            log.debug("[Fuzzy] Probando término: '{}'", term);
            // NSPredicate en iOS — literal con comillas dobles porque escapeXpathValue()
            // garantiza que safeT ya no contiene comillas dobles (pueden quedar simples,
            // p. ej. "M&M's®"), igual que asumía el XPath original con \"..\".
            By textLocator = isIOS()
                    ? AppiumBy.iOSNsPredicateString("label CONTAINS \"" + safeT + "\" OR value CONTAINS \"" + safeT + "\"")
                    : By.xpath("//*[contains(@text, \"" + safeT + "\")]");
            if (tryClickByLocatorContains(textLocator, targetText)) {
                log.info("[BUSQUEDA] Producto='{}' | Estrategia=fuzzy-text-contains | Tiempo={}ms | Resultado=ENCONTRADO",
                    targetText, System.currentTimeMillis() - t0);
                return true;
            }
            By descLocator = isIOS()
                    ? AppiumBy.iOSNsPredicateString("name CONTAINS \"" + safeT + "\"")
                    : By.xpath("//*[contains(@content-desc, \"" + safeT + "\")]");
            if (tryClickByLocatorContains(descLocator, targetText)) {
                log.info("[BUSQUEDA] Producto='{}' | Estrategia=fuzzy-desc-contains | Tiempo={}ms | Resultado=ENCONTRADO",
                    targetText, System.currentTimeMillis() - t0);
                return true;
            }
            // Scroll de catálogo con presupuesto propio (Android only) — reemplaza el
            // antiguo UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(...):
            // esa llamada NATIVA de UiAutomator2 delega en el driver tanto la elección
            // del contenedor scrollable como la dirección de scroll. En una pantalla
            // Compose con varios carruseles horizontales anidados dentro de la lista
            // vertical (Helados, Dulces y Chocolates, Promocionales, Destacados,
            // Combos...), UiAutomator2 puede enganchar un carrusel HORIZONTAL como "el"
            // contenedor scrollable — produciendo desplazamientos horizontales erráticos
            // que interrumpen el descenso vertical, vuelven varias veces al mismo
            // carrusel y nunca llegan a las categorías inferiores.
            //
            // oneShotVerticalWithRowPeek() usa exclusivamente nuestros propios swipes
            // W3C: desciende verticalmente en una única dirección y solo hace un "peek"
            // horizontal acotado en la fila que está actualmente en pantalla, abandonando
            // de inmediato si el producto no aparece ahí — nunca vuelve a un carrusel ya
            // procesado. Mismos presupuestos (20 vertical / 20 horizontal, igual que
            // Constantes.SWIPES_VERTICAL_MAX/SWIPES_HORIZONTAL_MAX) y mismos tiempos de
            // swipe que el resto del archivo — no se agrega ningún timeout nuevo.
            if (!isIOS() && !uia2ScrollExhausted) {
                try {
                    By combined = By.xpath("//*[contains(@text, \"" + safeT + "\") or contains(@content-desc, \"" + safeT + "\")]");
                    if (oneShotVerticalWithRowPeek(combined, 20, 5)) {
                        WebElement el = driver.findElement(combined);
                        log.info("[BUSQUEDA] Producto='{}' | Estrategia=fuzzy-vertical+peek('{}') | Tiempo={}ms | Resultado=ENCONTRADO",
                            targetText, term, System.currentTimeMillis() - t0);
                        tapCenterW3C(el);
                        return true;
                    }
                    // El descenso vertical+peek ya cubrió TODO el catálogo (incluyendo
                    // cada carrusel encontrado en el camino) sin éxito — repetirlo para
                    // los términos restantes de esta llamada sería igual de inútil.
                    uia2ScrollExhausted = true;
                    log.debug("[Fuzzy] vertical+peek agotó el catálogo sin éxito ({}ms) — se omite para términos restantes",
                        System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    rethrowIfAborted(e);
                    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                }
            }
        }
        log.warn("[BUSQUEDA] Producto='{}' | Estrategia=fuzzy | Tiempo={}ms | Resultado=NO_ENCONTRADO",
            targetText, System.currentTimeMillis() - t0);
        return false;
    }

    /**
     * Registra todos los textos visibles en pantalla para diagnóstico.
     * Útil para identificar cambios de nombre en el catálogo según cine/ciudad.
     * Adjunta la lista a Allure como evidencia.
     */
    private void logVisibleElements(String contexto) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            By locator = isIOS()
                    ? By.xpath("//*[string-length(@label)>0 or string-length(@value)>0 or string-length(@name)>0]")
                    : By.xpath("//*[string-length(@text)>0 or string-length(@content-desc)>0]");
            java.util.List<WebElement> textos = driver.findElements(locator);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            StringBuilder sb = new StringBuilder("[TELEMETRIA] ").append(contexto).append("\n");
            for (WebElement t : textos) {
                try {
                    String txt = textoDeResultado(t);
                    if ((txt == null || txt.isBlank()) && !isIOS()) txt = t.getAttribute("content-desc");
                    if (txt != null && !txt.isBlank()) sb.append("  VISIBLE: '").append(txt.trim()).append("'\n");
                } catch (Exception ignored) {}
            }
            log.info(sb.toString());
            try {
                io.qameta.allure.Allure.addAttachment("Visibles - " + contexto, "text/plain", sb.toString());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            rethrowIfAborted(e);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void clickCardByTextWithFallback(String visibleText, int longTimeoutSeconds) {
        long tEntry = System.currentTimeMillis();
        log.info("[ClickCard] ENTER producto='{}' longTimeout={}s", visibleText, longTimeoutSeconds);

        // ✅ 1) Construcción de locators (Texto exacto o Descripción de accesibilidad)
        // Esto cubre tanto el TextView como el contenedor de Compose.
        // Android expone el contenido en @text/@content-desc; iOS (XCUITest) lo expone
        // en @label/@name/@value.
        //
        // PERF (solo iOS): NSPredicate (AppiumBy.iOSNsPredicateString) en vez de XPath —
        // este método tiene 64 sitios de llamada en este archivo y alimenta directamente
        // findVisibleOrScrollToXpathAndClick/findVisibleOrScrollDownAndRightSlowToXpathAndClick
        // (Pasos 3 y 4), que re-evalúan byText/byParent en CADA iteración de scroll — el
        // mayor volumen de evaluaciones de locator de todo el archivo. Ver overloads By de
        // esos dos métodos en BasePage (aceptan un By directo, sin forzar XPath). Android
        // (@text/@content-desc, XPath) se deja exactamente igual.
        boolean ios = isIOS();
        By byText = ios
                ? AppiumBy.iOSNsPredicateString(
                        "label == \"" + visibleText + "\" OR name == \"" + visibleText + "\" OR value == \"" + visibleText + "\"")
                : By.xpath("//*[@text=\"" + visibleText + "\" or @content-desc=\"" + visibleText + "\"]");
        // "Padre del nodo" (XPath "..") no tiene equivalente NSPredicate (no expresa
        // relaciones de árbol) — se conserva como XPath para AMBAS plataformas en este
        // caso puntual, igual que antes, para no alterar qué elemento se termina tocando.
        String xpathTextForParent = ios
                ? "//*[@label=\"" + visibleText + "\" or @name=\"" + visibleText + "\" or @value=\"" + visibleText + "\"]"
                : "//*[@text=\"" + visibleText + "\" or @content-desc=\"" + visibleText + "\"]";
        By byParent = By.xpath(xpathTextForParent + "/..");

        // ✅ 2) Fast path MUY corto (tap por coordenadas si está visible)
        // OPTIMIZACIÓN: isVisibleFast() fuerza implicitlyWait=0 durante el chequeo y
        // restaura el wait ambiente al salir — evita que un "miss" pague el implicitlyWait
        // de 10s que queda activo en el driver tras cualquier búsqueda fuzzy/telemetría previa.
        long t2 = System.currentTimeMillis();
        log.info("[ClickCard] Paso-2 ENTER (visible check)");
        if (isVisibleFast(byText)) {
            try {
                WebElement el = driver.findElement(byText);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }
        if (isVisibleFast(byParent)) {
            try {
                WebElement el = driver.findElement(byParent);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }

        log.info("[ClickCard] Paso-2 EXIT no visible ({}ms)", System.currentTimeMillis() - t2);

        // ✅ 3) One-shot vertical (rápido)
        long t3 = System.currentTimeMillis();
        log.info("[ClickCard] Paso-3 ENTER (vertical ×2 | maxSwipes={})", Math.min(longTimeoutSeconds, 10));
        try {
            findVisibleOrScrollToXpathAndClick(byText, Math.min(longTimeoutSeconds, 10));
            log.info("[ClickCard] Paso-3 EXIT found byText ({}ms)", System.currentTimeMillis() - t3);
            return;
        } catch (Throwable ignored) {}

        try {
            findVisibleOrScrollToXpathAndClick(byParent, Math.min(longTimeoutSeconds, 10));
            log.info("[ClickCard] Paso-3 EXIT found byParent ({}ms)", System.currentTimeMillis() - t3);
            return;
        } catch (Throwable ignored) {}
        log.info("[ClickCard] Paso-3 EXIT no encontrado ({}ms)", System.currentTimeMillis() - t3);

        // ✅ 4) Si no salió con vertical, 1 sola pasada V/H y FIN (fast-fail)
        long t4 = System.currentTimeMillis();
        log.info("[ClickCard] Paso-4 ENTER (V/H ×2 | maxV={} maxH=5)", Math.min(longTimeoutSeconds, 10));
        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(byText, Math.min(longTimeoutSeconds, 10), 5);
            log.info("[ClickCard] Paso-4 EXIT found byText ({}ms)", System.currentTimeMillis() - t4);
            return;
        } catch (Throwable ignored) {}

        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(byParent, Math.min(longTimeoutSeconds, 10), 5);
            log.info("[ClickCard] Paso-4 EXIT found byParent ({}ms)", System.currentTimeMillis() - t4);
            return;
        } catch (Throwable ignored) {}
        log.info("[ClickCard] Paso-4 EXIT no encontrado ({}ms)", System.currentTimeMillis() - t4);

        // ✅ 5) Último intento "barato" con tap antes de lanzar error
        long t5 = System.currentTimeMillis();
        log.info("[ClickCard] Paso-5 ENTER (visible check final)");
        if (isVisibleFast(byText)) {
            try {
                WebElement el = driver.findElement(byText);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            log.info("[ClickCard] Paso-5 EXIT found byText ({}ms)", System.currentTimeMillis() - t5);
            return;
        }
        if (isVisibleFast(byParent)) {
            try {
                WebElement el = driver.findElement(byParent);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            log.info("[ClickCard] Paso-5 EXIT found byParent ({}ms)", System.currentTimeMillis() - t5);
            return;
        }
        log.info("[ClickCard] Paso-5 EXIT no visible ({}ms)", System.currentTimeMillis() - t5);

        // ✅ 6) Fallback REAL: swipes manuales (slowSwipeUp) + re-check
        // OPTIMIZACIÓN: implicitlyWait=0 UNA sola vez para todo el bucle (no por chequeo,
        // para no pagar el costo de alternar el wait extraSwipes×2 veces) — antes cada
        // "miss" de isVisibleQuick heredaba el implicitlyWait=10s ambiente, acumulando
        // hasta extraSwipes×2×10s (300+ s) antes de llegar al paso 7 (fuzzy).
        // Además: corte anticipado por fingerprint — si 2 swipes consecutivos no cambian
        // la pantalla (fin real del catálogo), seguir iterando solo repetiría chequeos
        // sobre los mismos elementos ya inspeccionados. El tope extraSwipes no se reduce;
        // solo se evita seguir cuando ya no hay nada nuevo que recorrer.
        long t6 = System.currentTimeMillis();
        try {
            int extraSwipes = Math.min(Math.max(longTimeoutSeconds, 6), 20); // 6..20
            log.info("[ClickCard] Paso-6 ENTER ({} swipes manuales)", extraSwipes);

            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            try {
                String lastFingerprint = null;
                int stalled = 0;

                for (int i = 0; i < extraSwipes; i++) {

                    if (isDisplayedNoWaitManaged(byText)) {
                        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                        try {
                            WebElement el = driver.findElement(byText);
                            tapCenterW3C(el);
                        } catch (Exception ignored) {}
                        return;
                    }
                    if (isDisplayedNoWaitManaged(byParent)) {
                        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                        try {
                            WebElement el = driver.findElement(byParent);
                            tapCenterW3C(el);
                        } catch (Exception ignored) {}
                        return;
                    }

                    // 👇 ESTE MÉTODO SÍ EXISTE EN TU BasePage
                    slowSwipeUp();

                    String fp = viewportFingerPrintPublic();
                    if (fp.equals(lastFingerprint)) {
                        if (++stalled >= 2) {
                            log.info("[ClickCard] Paso-6 fin de catálogo detectado en swipe {}/{} — corte anticipado",
                                    i + 1, extraSwipes);
                            break;
                        }
                    } else {
                        stalled = 0;
                    }
                    lastFingerprint = fp;
                }

                // Último re-check después de swipes
                if (isDisplayedNoWaitManaged(byText)) {
                    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                    try {
                        WebElement el = driver.findElement(byText);
                        tapCenterW3C(el);
                    } catch (Exception ignored) {}
                    return;
                }
                if (isDisplayedNoWaitManaged(byParent)) {
                    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                    try {
                        WebElement el = driver.findElement(byParent);
                        tapCenterW3C(el);
                    } catch (Exception ignored) {}
                    return;
                }
            } finally {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            }
        } catch (Throwable ignored) {}
        log.info("[ClickCard] Paso-6 EXIT no encontrado ({}ms)", System.currentTimeMillis() - t6);

        // ✅ 7) Búsqueda fuzzy: tolerante a variaciones de nombre en catálogo
        long t7 = System.currentTimeMillis();
        log.info("[ClickCard] Paso-7 ENTER (fuzzy) | acumulado={}ms", System.currentTimeMillis() - tEntry);
        try {
            if (tryFuzzyClick(visibleText)) {
                log.info("[ClickCard] Paso-7 EXIT fuzzy ok ({}ms) | total={}ms",
                        System.currentTimeMillis() - t7, System.currentTimeMillis() - tEntry);
                return;
            }
            // Scroll adicional + reintento fuzzy (el producto puede estar fuera de pantalla)
            for (int i = 0; i < 5; i++) {
                slowSwipeUp();
                if (tryFuzzyClick(visibleText)) {
                    log.info("[ClickCard] Paso-7 EXIT fuzzy+scroll({}) ok ({}ms) | total={}ms",
                            (i + 1), System.currentTimeMillis() - t7, System.currentTimeMillis() - tEntry);
                    return;
                }
            }
        } catch (Throwable ignored2) {}
        log.info("[ClickCard] Paso-7 EXIT fuzzy no encontrado ({}ms)", System.currentTimeMillis() - t7);

        // ✅ 8) Fail definitivo con telemetría completa
        log.warn("[ClickCard] EXIT FAIL | total={}ms | pasos: 2={}ms 3={}ms 4={}ms 5={}ms 6={}ms 7={}ms",
                System.currentTimeMillis() - tEntry,
                System.currentTimeMillis() - t2, System.currentTimeMillis() - t3,
                System.currentTimeMillis() - t4, System.currentTimeMillis() - t5,
                System.currentTimeMillis() - t6, System.currentTimeMillis() - t7);
        logVisibleElements("FAIL definitivo para '" + visibleText + "'");
        takeScreenshotOnFailure();
        throw new RuntimeException("[TELEMETRIA] No se encontró el elemento: '" + visibleText
            + "'. Ver log y evidencia Allure adjunta para diagnóstico de catálogo.");
    }
    public void forzarClic(By locator) {
        try {
            // 1. Localizar el elemento y obtener sus dimensiones
            WebElement el = driver.findElement(locator);
            Point location = el.getLocation();
            Dimension size = el.getSize();

            // 2. Calcular el centro exacto (X, Y)
            int centerX = location.getX() + (size.getWidth() / 2);
            int centerY = location.getY() + (size.getHeight() / 2);

            // 3. Configurar la acción del "dedo" (PointerInput)
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence tap = new Sequence(finger, 1);

            // Mover el dedo al centro
            tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, centerY));
            // Presionar
            tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            // Breve pausa para asegurar que el sistema detecte el toque
            tap.addAction(finger.createPointerMove(Duration.ofMillis(100), PointerInput.Origin.viewport(), centerX, centerY));
            // Levantar
            tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

            // 4. Ejecutar la acción
            driver.perform(Collections.singletonList(tap));

            log.debug("forzarClic executed successfully on: {}", locator);

        } catch (Exception e) {
            log.warn("forzarClic failed, falling back to standard click: {}", e.getMessage());
            // Fallback: Si el cálculo de coordenadas falla, intentamos el clic básico
            driver.findElement(locator).click();
        }
    }
    protected void swipeUpInMainContent(int durationMs) {
        ensureAppIsInForegroundOrRecover();

        try {
            WebElement content = driver.findElement(By.id("android:id/content"));
            org.openqa.selenium.Rectangle r = content.getRect();

            // Slightly left of center to avoid Compose gesture interceptors
            int x = r.getX() + (int) (r.getWidth() * 0.46);

            // Agressive full-range swipe: 90%→10% for Compose LazyColumn
            int startY = r.getY() + (int) (r.getHeight() * 0.90);
            int endY   = r.getY() + (int) (r.getHeight() * 0.10);

            // fallback de seguridad si el rect viene raro
            if (startY <= endY) {
                org.openqa.selenium.Dimension size = driver.manage().window().getSize();
                x = (int) (size.width * 0.46);
                startY = (int) (size.height * 0.88);
                endY   = (int) (size.height * 0.10);
            }

            swipeW3C(x, startY, x, endY, durationMs);
            try { Thread.sleep(350); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        } catch (Exception e) {
            rethrowIfAborted(e);

            // fallback: swipe por pantalla completa
            org.openqa.selenium.Dimension size = driver.manage().window().getSize();
            int x = (int) (size.width * 0.46);
            int startY = (int) (size.height * 0.88);
            int endY   = (int) (size.height * 0.10);

            swipeW3C(x, startY, x, endY, durationMs);
            try { Thread.sleep(350); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }
    protected void findVisibleOrScrollDownManySwipesAndClickProducto(
            String targetXpath,
            int timeoutSeconds,
            int maxSwipes,
            String nombreProducto
    ) {
        ensureAppIsInForegroundOrRecover();
        By target = By.xpath(targetXpath);

        try {
            // 0) Si ya está visible, clic directo (sin waitClickable para no caer en clickable=false)
            if (isVisibleQuick(target)) {
                WebElement el = driver.findElement(target);
                abortIfProductoNoDisponible(el, nombreProducto);
                try { el.click(); }
                catch (Exception e) { rethrowIfAborted(e); tapCenterW3C(el); }
                try { takeScreenshot(); } catch (Exception e) { rethrowIfAborted(e); }
                return;
            }

            // 1) Intento rápido con UiScrollable por texto (Android) / W3C swipe (iOS)
            if (!isIOS()) {
                try {
                    String uiScroll = "new UiScrollable(new UiSelector().scrollable(true))"
                            + ".scrollIntoView(new UiSelector().text(\"" + nombreProducto + "\"))";
                    driver.findElement(AppiumBy.androidUIAutomator(uiScroll));
                } catch (Exception ignore) {
                    rethrowIfAborted(ignore);
                }
            } else {
                oneShotVerticalSearch(target, 8);
            }

            // Check post UiScrollable
            if (isVisibleQuick(target)) {
                WebElement el = driver.findElement(target);
                abortIfProductoNoDisponible(el, nombreProducto);
                try { el.click(); }
                catch (Exception e) { rethrowIfAborted(e); tapCenterW3C(el); }
                try { takeScreenshot(); } catch (Exception e) { rethrowIfAborted(e); }
                return;
            }

            // 2) Swipes manuales (los que tú definas)
            for (int i = 1; i <= maxSwipes; i++) {
                if (isVisibleQuick(target)) {
                    WebElement el = driver.findElement(target);
                    abortIfProductoNoDisponible(el, nombreProducto);
                    try { el.click(); }
                    catch (Exception e) { rethrowIfAborted(e); tapCenterW3C(el); }
                    try { takeScreenshot(); } catch (Exception e) { rethrowIfAborted(e); }
                    return;
                }

                swipeUpInMainContent(950);
            }

            // Último check
            if (isVisibleQuick(target)) {
                WebElement el = driver.findElement(target);
                abortIfProductoNoDisponible(el, nombreProducto);
                try { el.click(); }
                catch (Exception e) { rethrowIfAborted(e); tapCenterW3C(el); }
                try { takeScreenshot(); } catch (Exception e) { rethrowIfAborted(e); }
                return;
            }

            // Si no aparece, respeta tu comportamiento: falla por no encontrado
            try { takeScreenshotOnFailure(); } catch (Exception e) { rethrowIfAborted(e); }
            throw new RuntimeException("No se encontró (vertical) el elemento: '" + nombreProducto
                    + "' con xpath: " + targetXpath);

        } catch (org.opentest4j.TestAbortedException aborted) {
            throw aborted; // ✅ deja pasar SKIPPED si el producto está agotado
        } catch (Exception e) {
            rethrowIfAborted(e);
            try { takeScreenshotOnFailure(); } catch (Exception ignore) { rethrowIfAborted(ignore); }
            throw e;
        }
    }
    /**
     * Mismo criterio de "visible" que isVisibleQuick() (BasePage) — requiere que el
     * primer elemento encontrado esté además isDisplayed() == true, pero SIN
     * gestionar implicitlyWait: asume que el llamador ya lo dejó en 0.
     *
     * Existe para poder envolver un bloque completo (p. ej. un bucle de swipes) en
     * un único implicitlyWait(0)/restore en el llamador, en vez de pagar el costo de
     * alternar el wait en cada chequeo individual dentro del bucle.
     */
    private boolean isDisplayedNoWaitManaged(By locator) {
        try {
            java.util.List<WebElement> els = driver.findElements(locator);
            if (els.isEmpty()) return false;
            try { return els.get(0).isDisplayed(); } catch (Exception ignore) { return false; }
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Chequeo de visibilidad puntual (fuera de un bucle) con implicitlyWait=0 propio.
     * Mismo criterio que isVisibleQuick() (BasePage), pero garantiza que el "miss" no
     * herede el implicitlyWait=10s ambiente que queda activo en el driver tras
     * cualquier búsqueda fuzzy/telemetría previa en la misma sesión.
     */
    private boolean isVisibleFast(By locator) {
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            return isDisplayedNoWaitManaged(locator);
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    protected boolean tryClickIfAlreadyVisible(By locator, int timeoutSeconds) {
        try {
            this.ensureAppIsInForegroundOrRecover();
            WebDriverWait wait = new WebDriverWait(this.driver, Duration.ofSeconds((long)timeoutSeconds));
            WebElement el = (WebElement)wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

            try {
                el.click();
                this.takeScreenshot();
                return true;
            } catch (Exception var6) {
                this.tapCenterW3C(el);
                return true;
            }
        } catch (Exception var7) {
            return false;
        }
    }

    public void buscarAguaEmbotellada() {
        buscarProducto("Agua Embotellada");
    }

    public void buscarMaxiComboMix() {
        buscarProducto("Maxicombo Mix");
    }

    public void buscarNachos() {
        buscarProducto("Nachos");
    }

    public void buscarMojito() {
        buscarProducto("Mojito");
    }

    public void buscarCarajillo() {
        buscarProducto("Carajillo");
    }

    public void buscarCerveza() {
        buscarProducto("Cerveza Clásica");
    }

    public void buscarNegraModelo() {
        buscarProducto("Negra Modelo");
    }

    public void seleccionarSaborPorContentDesc(String contentDesc, int index) {
        String xpath = isIOS()
                ? "(//*[@name=\"" + contentDesc + "\"])[" + index + "]"
                : "(//android.view.View[@content-desc=\"" + contentDesc + "\"])[" + index + "]";
        this.clickSmart(xpath, 10);
    }

    private WebElement findCardContainer(WebElement base) {
        // Mismo mapeo que BasePage.findBestCardContainer(): iOS no tiene "android.view.View" —
        // el contenedor genérico de Compose Multiplatform en XCUITest es XCUIElementTypeOther.
        String view = isIOS() ? "XCUIElementTypeOther" : "android.view.View";
        try {
            return base.findElement(By.xpath("./ancestor::*[@clickable='true'][1]"));
        } catch (Exception var5) {
            try {
                return base.findElement(By.xpath("./ancestor::" + view + "[2]"));
            } catch (Exception var4) {
                try {
                    return base.findElement(By.xpath("./ancestor::" + view + "[3]"));
                } catch (Exception var3) {
                    return base;
                }
            }
        }
    }

    private void fallbackTap(String xpath) {
        try {
            WebElement el = this.waits.waitClickable(By.xpath(xpath));
            int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
            int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;
            this.w3cTap(cx, cy, 150);
            this.takeScreenshot();
        } catch (Exception var5) {
            throw new RuntimeException("No se pudo interactuar con el elemento (click y tap fallaron). XPath: " + xpath + "\nPageSource:\n" + this.driver.getPageSource(), var5);
        }
    }

    private void fallbackTap(PlatformLocator locator) {
        try {
            WebElement el = this.waits.waitClickable(locator.resolve(isIOS()));
            int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
            int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;
            this.w3cTap(cx, cy, 150);
            this.takeScreenshot();
        } catch (Exception var5) {
            throw new RuntimeException("No se pudo interactuar con el elemento (click y tap fallaron). Locator: "
                    + locator + "\nPageSource:\n" + this.driver.getPageSource(), var5);
        }
    }

    private void w3cTap(int x, int y, int holdMs) {
        PointerInput finger = new PointerInput(Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO, Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerMove(Duration.ofMillis((long)Math.max(holdMs, 1)), Origin.viewport(), x, y));
        tap.addAction(finger.createPointerUp(MouseButton.LEFT.asArg()));
        this.driver.perform(Collections.singletonList(tap));
    }
}
