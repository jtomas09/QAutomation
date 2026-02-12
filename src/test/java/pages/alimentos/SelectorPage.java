package pages.alimentos;

import static io.appium.java_client.touch.WaitOptions.waitOptions;
import static io.appium.java_client.touch.offset.PointOption.point;

import java.time.Duration;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import pages.common.BasePage;

public class SelectorPage extends BasePage {

    public static final int FAST_VISIBLE_SECONDS = 2;
    private static final String HEADER_SEGUNDO_SABOR = "Selecciona el segundo sabor";
    private static final String HEADER_EXTRA = "Selecciona tu extra";

    public SelectorPage(AndroidDriver driver) {
        super(driver);
    }

    public void abrirMenu() {
        try {
            click(By.xpath(AlimentosLocators.BTN_ALIMENTOS_ICON));
        } catch (Exception e) {
            // fallback: intentar tap por coordenadas si el elemento no responde
            fallbackTap(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }
    }

    // =================================================================================================================
    // ✅ Clicks directos por texto (sin cambios)
    // =================================================================================================================
    public void clickAmericano() { clickCardByTextWithFallback("Americano", 20); }
    public void clickDippinDots() { clickCardByTextWithFallback("Dippin Dots", 10); }
    public void clickPalomitasSkinkless() { clickCardByTextWithFallback("Palomitas Skwinkles", 10); }
    public void clickComboNachosPareja() { clickCardByTextWithFallback("Combo Nachos en Pareja ", 10); }
    public void clickPinaColada() { clickCardByTextWithFallback("Piña Colada", 10); }
    public void clickPalomitas() { clickCardByTextWithFallback("Palomitas", 10); }
    public void SinQueso() { clickCardByTextWithFallback("Sin Extra Queso", 10); }
    public void FrutosPelonPeloRico() { clickCardByTextWithFallback("Frutos Pelon pelon rico", 10); }
    public void Cheetos() { clickCardByTextWithFallback("Cheetos® Mix", 10); }
    public void MorasMaracuya() { clickCardByTextWithFallback("Moras Maracuya", 10); }
    public void ManzanaVerde() { clickCardByTextWithFallback("Manzana Verde", 10); }
    public void FresaPelonada() { clickCardByTextWithFallback("Fresa Pelonada", 10); }
    public void MokaCaramelo() { clickCardByTextWithFallback("Moka Caramelo", 10); }
    public void ChocolateBlanco() { clickCardByTextWithFallback("Chocolate blanco", 10); }
    public void ChiclePlatano() { clickCardByTextWithFallback("Chicle Platano", 10); }
    public void MMs() { clickCardByTextWithFallback("M&M's®", 10); }
    public void JollyRancherRaspberry() { clickCardByTextWithFallback("Jolly Rancher Raspberry", 10); }
    public void MangoChamoy() { clickCardByTextWithFallback("Mango Chamoy", 10); }
    public void clickPretzel() { clickCardByTextWithFallback("Pretzel", 20); }
    public void SandiaPelonada() { clickCardByTextWithFallback("Sandía Pelonada", 10); }
    public void clickSkwinkles() { clickCardByTextWithFallback("Skwinkles® Chunks sandia", 20); }
    public void EsenciaMenta() { clickCardByTextWithFallback("Esencia Menta", 10); }
    public void Capuccino() { clickCardByTextWithFallback("Cappuccino", 10); }
    public void MacchiatoCoco() { clickCardByTextWithFallback("Macchiato cocó", 10); }
    public void MacchiatoMenta() { clickCardByTextWithFallback("Macchiato menta", 10); }
    public void MacchiatoCremaIrlandesa() { clickCardByTextWithFallback("Macchiato crema irlandesa", 10); }
    public void MangoTajin() { clickCardByTextWithFallback("Mango Tajin", 10); }
    public void CafeMediano() { clickCardByTextWithFallback("Mediano", 10); }
    public void LecheDeslactosada() { clickCardByTextWithFallback("Leche Deslactosada", 10); }
    public void SalsaChocolate() { clickCardByTextWithFallback("Salsa de Chocolate Oscuro", 20); }
    public void ExtraManzana() { clickCardByTextWithFallback("Extra Manzana Canela", 20); }
    public void ExtraQuesoPhiladelphia() { clickCardByTextWithFallback("Extra Queso Philadelphia®", 20); }
    public void ExtraMermeladaFresa() { clickCardByTextWithFallback("Extra Mermelada de Fresa", 20); }
    public void ExtraMermeladaZarzamora() { clickCardByTextWithFallback("Extra Mermelada de Zarzamora", 20); }
    public void TeJamaica() { clickCardByTextWithFallback("Té Mora Jamaica", 10); }
    public void Tocino() { clickCardByTextWithFallback("Extra Tocino", 20); }
    public void ExtraQuesoManchego() { clickCardByTextWithFallback("Extra Queso Manchego", 20); }
    public void Champinon() { clickCardByTextWithFallback("Extra Champiñon", 20); }
    public void ExtraJamonPavo() { clickCardByTextWithFallback("Extra Jamón Pavo", 20); }
    public void LecheAlmendra() { clickCardByTextWithFallback("Leche Almendra", 10); }
    public void EsenciaVainilla() { clickCardByTextWithFallback("Esencia Vainilla", 10); }
    public void CremaIrlandesa() { clickCardByTextWithFallback("Crema Irlandesa", 10); }
    public void clickMM() { clickCardByTextWithFallback("M&M's®", 20); }
    public void clickCrepasDulces() { clickCardByTextWithFallback("Crepas Dulces Premium", 20); }
    public void clickMaxiComboFamiliar() { clickCardByTextWithFallback("Maxicombo Familiar Jumbo", 10); }
    public void clickComboICEE() { clickCardByTextWithFallback("Combo ICEE® con Skwinkles®", 20); }
    public void clickHotDogTakis() { clickCardByTextWithFallback("Hot Dog Takis ", 10); }
    public void clickRefresco() { clickCardByTextWithFallback("Refresco", 20); }
    public void Takis() { clickCardByTextWithFallback("Takis® Fuego", 20); }
    public void Sidral() { clickCardByTextWithFallback("Sidral Mundet® Sin Azúcar", 20); }
    public void FuzeTe() { clickCardByTextWithFallback("Fuze Tea® Sin Azúcar", 20); }
    public void Fanta() { clickCardByTextWithFallback("Fanta® Naranja Sin Azúcar", 20); }
    public void Sprite() { clickCardByTextWithFallback("Sprite® Sin Azúcar", 20); }
    public void CocaColaZero() { clickCardByTextWithFallback("Coca-Cola® Zero Azúcar", 20); }
    public void CocaColaLigth() { clickCardByTextWithFallback("Coca-Cola® Light", 20); }
    public void Coco() { clickCardByTextWithFallback("Coco", 10); }
    public void DelValle() { clickCardByTextWithFallback("Del Valle Frut® ", 20); }
    public void HieloRegular() { clickCardByTextWithFallback("Hielo Regular", 20); }
    public void PocoHielo() { clickCardByTextWithFallback("Poco Hielo", 20); }
    public void SinHielo() { clickCardByTextWithFallback("Sin Hielo", 20); }
    public void Caramelo() { clickCardByTextWithFallback("Caramelo", 20); }
    public void CheetosMix() { clickCardByTextWithFallback("Cheetos® Mix", 20); }
    public void Doritos() { clickCardByTextWithFallback("Doritos® Nacho", 20); }
    public void MixTakisFuego() { clickCardByTextWithFallback("Mix Takis Fuego", 20); }
    public void MixDoritos() { clickCardByTextWithFallback("Mix Doritos® Nacho", 20); }
    public void Toppin() { clickCardByTextWithFallback("Topping Pelon pelo rico", 20); }
    public void SkwinklessRellenos() { clickCardByTextWithFallback("Skwinkles® Rellenos", 20); }
    public void PelonPelonazo() { clickCardByTextWithFallback("Pelón Pelonazo®", 20); }
    public void SkwinklessSpaguetti() { clickCardByTextWithFallback("Skwinkles® Salsagheti", 20); }

    // =================================================================================================================
    // ✅ Métodos públicos que usan anchors (sin cambios)
    // =================================================================================================================
    public void clickMokaOscuro() { clickRightFromAmericanoAnchor("Moka Obscuro"); }
    public void clickCrepaFrappe() { clickRightFromComboNachosParejaAnchor("Crepa dulce + Frappé agua"); }
    public void clickComboPretzelPareja() { clickRightFromComboNachosParejaAnchor("Combo Pretzel en Pareja"); }
    public void clickComboNachos() { clickRightFromComboNachosParejaAnchor("Combo Nachos "); }
    public void clickFrappeAgua() { clickRightFromFrappeAnchor("Frappé Agua"); }
    public void clickFrappeLeche() { clickRightFromFrappeAnchor("Frappé Leche"); }
    public void clickCapuccino() { clickRightFromAmericanoAnchor("Capuccino"); }
    public void clickTeCaliente() { clickRightFromAmericanoAnchor("Té caliente"); }
    public void clickChocolate() { clickRightFromAmericanoAnchor("Chocolate"); }
    public void clickCheeseCake() { clickRightFromPretzelAnchor("Cheesecake"); }
    public void clickCrepaSalada() { clickRightFromCrepasDulcesAnchor("Crepas Saladas Premium"); }
    public void clickCrepaDulce2() { clickRightFromCrepasDulcesAnchor("Crepas Dulces 2 ingredientes"); }
    public void clickCrepasDulces1() { clickRightFromCrepasDulcesAnchor("Crepas Dulces 1 ingrediente"); }
    public void clickCrepaSalada1() { clickRightFromCrepasDulcesAnchor("Crepas Saladas 1 Ingrediente"); }
    public void clickCornetto() { clickRightFromPretzelAnchor("Cornetto®"); }
    public void clickHersheys() { clickRightFromSkwinklesAnchor("Hershey's®"); }
    public void clickSnickers() { clickRightFromSkwinklesAnchor("Snickers®"); }
    public void clickQuesadilla() { clickRightFromBurgerAnchor("Quesadilla"); }
    public void clickSnackBoneless() { clickRightFromBurgerAnchor("Plato Snack Boneless"); }
    public void clickMiniDogs() { clickRightFromBurgerAnchor("Mini Dogs VIP"); }
    public void clickPapasCrisscut() { clickRightFromBurgerAnchor("Papas Crisscut"); }
    public void clickNachosPremium() { clickRightFromBurgerAnchor("Nachos Premium"); }
    public void clickHotDog() { clickRightFromBurgerAnchor("Hot Dog"); }
    public void clickHotDogGuacamole() { clickRightFromBurgerAnchor("Hot Dog Guacamole"); }
    public void clickTexasDog() { clickRightFromBurgerAnchor("Texas Dog"); }
    public void clickPapasFritas() { clickRightFromBurgerAnchor("Papas Fritas"); }

    // =================================================================================================================
    // ✅ Acciones directas por locators (sin cambios)
    // =================================================================================================================
    public void cerrarPantalla() { clickIfPresent(By.xpath(AlimentosLocators.BTN_REGRESO)); }
    public void abrirCarrito() { click(By.xpath(AlimentosLocators.BTN_CARRITO)); }
    public void personalizar() { click(By.xpath(AlimentosLocators.BTN_PERSONALIZAR)); }
    public void agregarCarrito() { click(By.xpath(AlimentosLocators.BTN_AGREGAR_CARRITO)); }
    public void ManzanaCanela() { click(By.xpath(AlimentosLocators.BTN_MANZANACANELA)); }
    public void MermeladaZarzamora() { click(By.xpath(AlimentosLocators.BTN_MERMELADAZARZAMORA)); }
    public void QuesoPhiladelphia() { click(By.xpath(AlimentosLocators.BTN_QUESOPHILADELPHIA)); }
    public void QuesoManchego() { click(By.xpath(AlimentosLocators.BTN_QUESOMANCHEGO)); }
    public void Champiqueso() { click(By.xpath(AlimentosLocators.BTN_CHAMPIQUESO)); }
    public void ChampiquesoManchego() { click(By.xpath(AlimentosLocators.BTN_CHAMPIQUESOMANCHEGO)); }
    public void Nutella() { click(By.xpath(AlimentosLocators.BTN_NUTELLA)); }
    public void PLlevar() { click(By.xpath(AlimentosLocators.BTN_PARALLEVAR)); }
    public void Jumbo() { click(By.xpath(AlimentosLocators.BTN_JUMBO)); }
    public void TeMediano() { click(By.xpath(AlimentosLocators.BTN_TEMEDIANO)); }
    public void CafeDescafeinado() { click(By.xpath(AlimentosLocators.BTN_CAFEDESCAFEINADO)); }
    public void Grandes() { click(By.xpath(AlimentosLocators.BTN_GRANDES)); }
    public void ChocolateMediano() { click(By.xpath(AlimentosLocators.BTN_CHOCOLATEMEDIANO)); }
    public void Grande() { click(By.xpath(AlimentosLocators.BTN_REFRESCOGRANDE)); }
    public void seismili() { click(By.xpath(AlimentosLocators.BTN_660ML)); }
    public void Mango() { click(By.xpath(AlimentosLocators.BTN_MANGO)); }
    public void Adobadas() { click(By.xpath(AlimentosLocators.BTN_ADOBADAS)); }
    public void Skittles() { click(By.xpath(AlimentosLocators.BTN_SKITTLES)); }
    public void NachosChicos() { click(By.xpath(AlimentosLocators.BTN_NACHOSCHICOS)); }
    public void NachosNachos() { click(By.xpath(AlimentosLocators.BTN_NACHOSNACHOS)); }
    public void CookiesCream() { click(By.xpath(AlimentosLocators.BTN_COOKISCREAM)); }
    public void NachosTajin() { click(By.xpath(AlimentosLocators.BTN_NACHOSTAJIN)); }
    public void Medianas() { click(By.xpath(AlimentosLocators.BTN_MEDIANAS)); }
    public void Chicas() { click(By.xpath(AlimentosLocators.BTN_CHICAS)); }
    public void CarlosV() { click(By.xpath(AlimentosLocators.BTN_CARLOSV)); }
    public void Chicas2() { click(By.xpath(AlimentosLocators.BTN_CHICAS2)); }
    public void Res() { click(By.xpath(AlimentosLocators.BTN_RES)); }
    public void Boneless() { click(By.xpath(AlimentosLocators.BTN_BONELESS)); }
    public void Siguiente() { click(By.xpath(AlimentosLocators.BTN_SIGUIENTE)); }
    public void seisoz() { click(By.xpath(AlimentosLocators.BTN_6OZ)); }
    public void FresaCoco() { click(By.xpath(AlimentosLocators.BTN_FRESACOCO)); }
    public void nachosBoneless() { click(By.xpath(AlimentosLocators.BTN_NACHOSBONELESS)); }
    public void nachosBrisquet() { click(By.xpath(AlimentosLocators.BTN_NACHOSBRISKET)); }
    public void ExtraQueso() { click(By.xpath(AlimentosLocators.BTN_EXTRAQUESO)); }
    public void Chico() { click(By.xpath(AlimentosLocators.BTN_CHICO)); }
    public void Mediano() { click(By.xpath(AlimentosLocators.BTN_REFRESCOMEDIANO)); }
    public void Guacamole() { click(By.xpath(AlimentosLocators.BTN_GUACAMOLE)); }
    public void TexasDog() { click(By.xpath(AlimentosLocators.BTN_TEXASDOG)); }
    public void Amareto() { click(By.xpath(AlimentosLocators.BTN_AMARETO)); }
    public void CocaCola() { click(By.xpath(AlimentosLocators.BTN_COCACOLA)); }
    public void PinaColada() { click(By.xpath(AlimentosLocators.BTN_PINACOLADA)); }
    public void Nuez() { click(By.xpath(AlimentosLocators.BTN_NUEZ)); }
    public void Midori() { click(By.xpath(AlimentosLocators.BTN_MIDORI)); }
    public void kahlua() { click(By.xpath(AlimentosLocators.BTN_KAHLUA)); }
    public void Pepino() { click(By.xpath(AlimentosLocators.BTN_PEPINO)); }
    public void Manzana() { click(By.xpath(AlimentosLocators.BTN_MANZANA)); }
    public void Cereza() { click(By.xpath(AlimentosLocators.BTN_CEREZA)); }
    public void Regresar() { click(By.xpath(AlimentosLocators.BTN_REGRESARMENU)); }
    public void Mas() { click(By.xpath(AlimentosLocators.BTN_MAS)); }
    public void Algodon() { click(By.xpath(AlimentosLocators.BTN_ALGODON)); }

    // =================================================================================================================
    // ⚠️ Tu método original (NO fue modificado aquí porque pediste solo "parecidos" a anchor)
    // =================================================================================================================
    private void clickCardByTextWithFallback(String visibleText, int longTimeoutSeconds) {

        // ✅ 1) Construcción de XPaths (igual)
        String xpathText   = "//android.widget.TextView[@text=\"" + visibleText + "\"]";
        String xpathParent = xpathText + "/..";

        By byText   = By.xpath(xpathText);
        By byParent = By.xpath(xpathParent);

        // ✅ 2) Fast path MUY corto (reduce esperas acumuladas)
        // Si ya existe en pantalla, no hacemos scroll.
        if (tryClickIfAlreadyVisible(byText, 1)) return;
        if (tryClickIfAlreadyVisible(byParent, 1)) return;

        // ✅ 3) One-shot vertical (rápido): 1 sola pasada hacia abajo
        // (sin bajar + subir, porque eso es lo que te deja a mitad y cuesta mucho)
        try {
            findVisibleOrScrollToXpathAndClick(xpathText, Math.min(longTimeoutSeconds, 10));
            return;
        } catch (Exception ignored) {}

        try {
            findVisibleOrScrollToXpathAndClick(xpathParent, Math.min(longTimeoutSeconds, 10));
            return;
        } catch (Exception ignored) {}

        // ✅ 4) Si no salió con vertical, 1 sola pasada V/H y FIN (fast-fail)
        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(xpathText, Math.min(longTimeoutSeconds, 10), 5);
            return;
        } catch (Exception ignored) {}

        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(xpathParent, Math.min(longTimeoutSeconds, 10), 5);
            return;
        } catch (Exception ignored) {}

        // ✅ 5) último intento “barato” (sin waits largos) y lanzar error
        if (isVisibleQuick(byText)) {
            click(byText);
            return;
        }
        if (isVisibleQuick(byParent)) {
            click(byParent);
            return;
        }

        throw new RuntimeException("No se encontró el elemento con texto: '" + visibleText + "' tras 1 intento (fast).");
    }


    // =================================================================================================================
    // ✅ HELPER ÚNICO (1 intento) para TODOS los métodos tipo "ancla + reset + sweep"
    // - Respeta: resetCarouselFromAnchorY(anchorY, 3) y sweep(...,6)
    // - Anclas "originales (18,6)" -> ahora solo ensureVisibleByXpathNoClick(...,18)
    // - Sin up-if-end, sin fallback lento, sin reintentos
    // =================================================================================================================
    private void clickRightFromAnchorOneTry(String anchorXpath, String targetXpath) {

        // 1) Ancla visible (18 swipes hacia abajo, SIN subir)
        boolean anchorVisible = ensureVisibleByXpathNoClick(anchorXpath, 18);
        if (!anchorVisible) {
            throw new RuntimeException("No se encontró ancla en 1 intento: " + anchorXpath);
        }

        // 2) anchorY
        WebElement anchorEl = driver.findElement(By.xpath(anchorXpath));
        int anchorY = anchorEl.getLocation().getY() + (anchorEl.getSize().getHeight() / 2);

        // ✅ 3) Resetea carrusel (RESPETAR)
        resetCarouselFromAnchorY(anchorY, 3);

        // 4) Buscar target (6 swipes horizontales)
        By target = By.xpath(targetXpath);
        if (sweepCatalogRightFromAnchorY(target, anchorY, 6)) {
            click(target);
            return;
        }

        // 5) Fallback SOLO al padre (6 swipes horizontales)
        By targetParent = By.xpath(targetXpath + "/..");
        if (sweepCatalogRightFromAnchorY(targetParent, anchorY, 6)) {
            click(targetParent);
            return;
        }

        // 6) No buscar más
        throw new RuntimeException("No se encontró target en 1 intento: " + targetXpath);
    }
    /**
     * ✅ Intento rápido: si el elemento ya está visible, lo intenta clickear (o tap) y regresa true.
     * NO hace scroll.
     */
    protected boolean tryClickIfAlreadyVisible(By locator, int timeoutSeconds) {
        try {
            // Guard ligero para evitar waits cuando la app se fue a background o salió
            ensureAppIsInForegroundOrRecover();
            //if (guardWarmupElapsed()) dismissClubLoginIfPresent();

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

            try {
                el.click();
                takeScreenshot();
                return true;
            } catch (Exception ignored) {
                // fallback a tap W3C
                tapCenterW3C(el);
                return true;
            }

        } catch (Exception e) {
            return false;
        }
    }

    protected boolean ensureVisibleByXpathNoClick(String xpath, int maxVerticalSwipes) {

        By locator = By.xpath(xpath);

        // 1) Si ya está visible, salir
        if (isVisibleQuick(locator)) return true;

        for (int i = 0; i < maxVerticalSwipes; i++) {

            // intento antes de swipe
            if (isVisibleQuick(locator)) return true;

            // swipe hacia abajo (contenido sube)
            slowSwipeUp();

            // micro-pausa para estabilizar render
            sleep(120);

            // intento después de swipe
            if (isVisibleQuick(locator)) return true;
        }

        return isVisibleQuick(locator);
    }

    // Variante para los que usaban ensureVisibleByXpathNoClick(...,4)
    private void clickRightFromAnchorOneTry(String anchorXpath, String targetXpath, int verticalSwipes) {

        boolean anchorVisible = ensureVisibleByXpathNoClick(anchorXpath, verticalSwipes);
        if (!anchorVisible) {
            throw new RuntimeException("No se encontró ancla en 1 intento: " + anchorXpath);
        }

        WebElement anchorEl = driver.findElement(By.xpath(anchorXpath));
        int anchorY = anchorEl.getLocation().getY() + (anchorEl.getSize().getHeight() / 2);

        // ✅ Respeta reset
        resetCarouselFromAnchorY(anchorY, 3);

        By target = By.xpath(targetXpath);
        if (sweepCatalogRightFromAnchorY(target, anchorY, 6)) {
            click(target);
            return;
        }

        By targetParent = By.xpath(targetXpath + "/..");
        if (sweepCatalogRightFromAnchorY(targetParent, anchorY, 6)) {
            click(targetParent);
            return;
        }

        throw new RuntimeException("No se encontró target en 1 intento: " + targetXpath);
    }

    // =================================================================================================================
    // ✅ Métodos privados "parecidos" adaptados (1 intento)
    // =================================================================================================================
    private void clickRightFromAmericanoAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Americano\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromComboNachosParejaAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromFrappeAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Malteadas Sencillas\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightCreppaDulceFrappeAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromPretzelAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Pretzel\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromCrepasDulcesAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Crepas Dulces Premium\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromSkwinklesAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Skwinkles® Chunks sandia\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromBurgerAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Pulled Pork Burger\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    // =================================================================================================================
    // ✅ Métodos públicos "parecidos" adaptados (1 intento) - los que usaban vertical 4
    // =================================================================================================================
    public void clickAguaEmbotellada() {
        String anchorXpath = "//android.widget.TextView[@text='Palomitas']";
        String targetXpath = "//android.widget.TextView[@text=\"Agua Embotellada\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickMaxiComboMix() {
        String anchorXpath = "//android.widget.TextView[@text=\"Maxicombo Familiar Jumbo\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Maxicombo Mix\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickNachos() {
        String anchorXpath = "//android.widget.TextView[@text='Palomitas']";
        String targetXpath = "//android.widget.TextView[@text=\"Nachos\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickMojito() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Mojito\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickCarajillo() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Carajillo\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickCerveza() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Cerveza Clásica\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    public void clickNegraModelo() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Negra Modelo\"]";
        clickRightFromAnchorOneTry(anchorXpath, targetXpath, 4);
    }

    // =================================================================================================================
    // Otros métodos que ya estaban (sin cambios)
    // =================================================================================================================
    public void seleccionarSaborPorContentDesc(String contentDesc, int index) {
        String xpath = "(//android.view.View[@content-desc=\"" + contentDesc + "\"])[" + index + "]";
        clickSmart(xpath, 10);
    }

    private WebElement findCardContainer(WebElement base) {
        try { return base.findElement(By.xpath("./ancestor::*[@clickable='true'][1]")); }
        catch (Exception ignored) {}
        try { return base.findElement(By.xpath("./ancestor::android.view.View[2]")); }
        catch (Exception ignored) {}
        try { return base.findElement(By.xpath("./ancestor::android.view.View[3]")); }
        catch (Exception ignored) {}
        return base;
    }

    private void fallbackTap(String xpath) {
        try {
            WebElement el = waits.waitClickable(By.xpath(xpath));
            int cx = el.getLocation().getX() + el.getSize().getWidth() / 2;
            int cy = el.getLocation().getY() + el.getSize().getHeight() / 2;

            new TouchAction<>(driver)
                    .press(point(cx, cy))
                    .waitAction(waitOptions(Duration.ofMillis(150)))
                    .release()
                    .perform();

            takeScreenshot();

        } catch (Exception ex) {
            throw new RuntimeException(
                    "No se pudo interactuar con el elemento (click y tap fallaron). XPath: " + xpath
                            + "\nPageSource:\n" + driver.getPageSource(),
                    ex
            );
        }
    }
}
