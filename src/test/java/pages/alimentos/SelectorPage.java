package pages.alimentos;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
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

public class SelectorPage extends BasePage {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelectorPage.class);
    public static final int FAST_VISIBLE_SECONDS = 2;
    private static final String HEADER_SEGUNDO_SABOR = "Selecciona el segundo sabor";
    private static final String HEADER_EXTRA = "Selecciona tu extra";

    public SelectorPage(AndroidDriver driver) {
        super(driver);
    }

    public void abrirMenu() {
        // UiSelector works from any screen (home or already on alimentos tab)
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                "new UiSelector().text(\"Alimentos\")"
            )).click();
            sleep(800);
            return;
        } catch (Exception ignored) {}
        // Fallback: Compose xpath for bottom-nav Alimentos tab from home/cartelera screen
        try {
            this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[2]/android.view.View/android.view.View[2]/android.view.View[3]"));
        } catch (Exception e) {
            this.fallbackTap("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[2]/android.view.View/android.view.View[2]/android.view.View[3]");
        }
    }

    public void clickAmericano() {
        this.clickCardByTextWithFallback("Americano",10);
    }

    public void clickDippinDots() {
        this.clickCardByTextWithFallback("Dippin Dots",10);
    }

    public void clickComboNachosPareja2() {
        this.clickCardByTextWithFallback("Combo Nachos en Pareja ",10);
    }

    public void clickPalomitasSkinkless() {
        this.clickCardByTextWithFallback("Palomitas Skwinkles",10);
    }

    public void clickComboNachosPareja() {
        this.clickCardByTextWithFallback("Combo Nachos en Pareja ",10);
    }

    public void clickPinaColada() {
        this.clickCardByTextWithFallback("Piña Colada",10);
    }

    public void clickPalomitas() {
        this.clickCardByTextWithFallback("Palomitas",10);
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

    public void clickPretzel() {
        this.clickCardByTextWithFallback("Pretzel",10);
    }

    public void SandiaPelonada() {
        this.clickCardByTextWithFallback("Sandía Pelonada",10);
    }

    public void clickSkwinkles() {
        this.clickCardByTextWithFallback("Skwinkles® Chunks sandia",10);
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

    public void clickMM() {
        this.clickCardByTextWithFallback("M&M's®",10);
    }

    public void clickCrepasDulces() {
        this.clickCardByTextWithFallback("Crepas Dulces Premium",10);
    }

    public void clickComboICEE() {
        this.clickCardByTextWithFallback("Combo ICEE® con Skwinkles®",10);
    }

    public void clickHotDogTakis() {
        this.clickCardByTextWithFallback("Hot Dog Takis ",10);
    }

    public void clickRefresco() {
        this.clickCardByTextWithFallback("Refresco",10);
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

    public void clickMokaOscuro() {
        this.clickRightFromAmericanoAnchor("Moka Obscuro");
    }

    public void clickCrepaFrappe() {
        this.clickRightFromComboNachosParejaAnchor("Crepa dulce + Frappé agua");
    }

    public void clickComboPretzelPareja() {
        this.clickRightFromComboNachosParejaAnchor("Combo Pretzel en Pareja");
    }

    public void clickComboNachos() {
        this.clickRightFromComboNachosParejaAnchor("Combo Nachos ");
    }

    public void clickFrappeAgua() {
        this.clickRightFromFrappeAnchor("Frappé Agua");
    }

    public void clickMaxiComboFamiliarJumbo() {
        this.clickRightFromPalomitasLiloAnchor("Maxicombo Familiar Jumbo");
    }

    public void clickFrappeLeche() {
        this.clickRightFromFrappeAnchor("Frappé Leche");
    }

    public void clickCapuccino() {
        this.clickRightFromAmericanoAnchor("Capuccino");
    }
    public void clickComboClasico() {
        this.clickRightFromComboClasicoAnchor("Combo Clásico");
    }
    public void clickComboJunior() {
        this.clickRightFromComboClasicoAnchor("Combo Junior");
    }

    public void clickTeCaliente() {
        this.clickRightFromAmericanoAnchor("Té caliente");
    }

    public void buscarTeCaliente() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "té caliente"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Té caliente']"));
    }

     public void buscarAmericano() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Americano"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Americano']"));
    }

     public void buscarMokaObscuro() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Moka Obscuro"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Moka Obscuro']"));
    }

       public void buscarCapuccino() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Capuccino"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Capuccino']"));
    }
    
       public void buscarChocolate() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Chocolate"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Chocolate']"));
    }
     public void buscarPretzel() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Pretzel"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Pretzel']"));
    }
       public void buscarCheeseCake() {
        // El ícono de búsqueda del menú de alimentos tiene content-desc="Buscar" (parent View, no el TextView hijo)
        this.click(By.xpath("//android.view.View[@content-desc='Buscar']"));
        this.sleep(600);
        // Foco en el campo de texto y escritura
        this.click(By.className("android.widget.EditText"));
        this.driver.executeScript("mobile: type", Map.of("text", "Cheesecake"));
        this.sleep(1500);
        this.click(By.xpath("//android.widget.TextView[@text='Cheesecake']"));
    }
    public void TeMentaManzanilla() {
        this.clickCardByTextWithFallback("Té Menta Manzanilla", 10);
    }

    public void clickContinuar() {
        this.clickCardByTextWithFallback("Continuar", 10);
    }

    public void clickChocolate() {
        this.clickRightFromAmericanoAnchor("Chocolate");
    }

    public void clickCheeseCake() {
        this.clickRightFromPretzelAnchor("Cheesecake");
    }

    public void clickCrepaSalada() {
        this.clickRightFromCrepasDulcesAnchor("Crepas Saladas Premium");
    }

    public void clickCrepaDulce2() {
        this.clickRightFromCrepasDulcesAnchor("Crepas Dulces 2 ingredientes");
    }

    public void clickCrepasDulces1() {
        this.clickRightFromCrepasDulcesAnchor("Crepas Dulces 1 ingrediente");
    }

    public void clickCrepaSalada1() {
        this.clickRightFromCrepasDulcesAnchor("Crepas Saladas 1 Ingrediente");
    }

    public void clickCornetto() {
        this.clickRightFromPretzelAnchor("Cornetto®");
    }

    public void clickHersheys() {
        this.clickRightFromSkwinklesAnchor("Hershey's®");
    }

    public void clickSnickers() {
        this.clickRightFromSkwinklesAnchor("Snickers®");
    }

    public void clickQuesadilla() {
        this.clickRightFromBurgerAnchor("Quesadilla");
    }
    public void clickPalomitasSkwinkles() {
        this.clickRightFromPalomasSkwinklesAnchor("Palomitas Skwinkles");
    }
    public void clickMaxiComboFamiliar() {
        this.clickRightFromMaxiComboFamiliar("Maxicombo Familiar Jumbo");
    }

    public void clickSnackBoneless() {
        this.clickRightFromBurgerAnchor("Plato Snack Boneless");
    }
    public void clickCrepaFrappeLeche() {
        this.clickRightFromBurgerAnchor("Plato Snack Boneless");
    }

    public void clickMiniDogs() {
        this.clickRightFromBurgerAnchor("Mini Dogs VIP");
    }

    public void clickPapasCrisscut() {
        this.clickRightFromBurgerAnchor("Papas Crisscut");
    }

    public void clickNachosPremium() {
        this.clickRightFromBurgerAnchor("Nachos Premium");
    }

    public void clickHotDog() {
        this.clickRightFromBurgerAnchor("Hot Dog");
    }

    public void clickHotDogGuacamole() {
        this.clickRightFromBurgerAnchor("Hot Dog Guacamole");
    }

    public void clickTexasDog() {
        this.clickRightFromBurgerAnchor("Texas Dog");
    }

    public void clickPapasFritas() {
        this.clickRightFromBurgerAnchor("Papas Fritas");
    }

    public void cerrarPantalla() {
        this.clickIfPresent(By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.widget.Button"));
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
        log.info("[abrirCarrito] Intentando abrir carrito...");
        Exception lastError = null;

        for (int intento = 1; intento <= 3; intento++) {
            try {
                if (intentarAbrirCarritoInterno(intento)) {
                    log.info("[abrirCarrito] Carrito abierto correctamente (intento {})", intento);
                    return;
                }
                log.warn("[abrirCarrito] Intento {} ejecutado pero pantalla no detectada.", intento);
            } catch (Exception e) {
                lastError = e;
                log.warn("[abrirCarrito] Intento {} fallido: {}", intento, e.getMessage());
            }
            sleep(800);
        }
        throw new RuntimeException("No se pudo abrir el carrito correctamente tras 3 intentos", lastError);
    }

    private boolean intentarAbrirCarritoInterno(int intento) {
        // Estrategia 1: badge numérico en el header (cualquier número 1-9)
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            java.util.List<WebElement> badges = driver.findElements(By.xpath(
                "//android.widget.TextView[@text='1' or @text='2' or @text='3' " +
                "or @text='4' or @text='5' or @text='6' or @text='7' or @text='8' or @text='9']"));
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

        // Estrategia 2: ícono de carrito por content-desc / resource-id
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            WebElement icon = driver.findElement(By.xpath(
                "//*[@content-desc='Carrito' or @content-desc='Cart' or " +
                "contains(@resource-id,'cart') or contains(@resource-id,'carrito') or " +
                "@content-desc='carrito' or @content-desc='basket']"));
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

    private boolean estaEnPantallaCarrito() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            boolean en = !driver.findElements(By.xpath(
                "//*[contains(@text,'Carrito') or contains(@text,'carrito') " +
                "or contains(@text,'Continuar') or contains(@text,'Ir a pagar') " +
                "or contains(@text,'tu orden') or contains(@text,'Boletos')]")).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            if (en) log.info("[abrirCarrito] Pantalla carrito detectada");
            return en;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return false;
        }
    }

    public void personalizar() {
        this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button"));
    }

    public void agregarCarrito() {
        this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button"));
    }

    public void ManzanaCanela() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Manzana Canela\"]"));
    }

    public void MermeladaZarzamora() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Mermelada de zarzamora\"]"));
    }

    public void QuesoPhiladelphia() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Queso Philadelphia®\"]"));
    }

    public void QuesoManchego() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Queso machego\"]"));
    }

    public void Champiqueso() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Champiqueso con queso Philadelphia®\"]"));
    }

    public void ChampiquesoManchego() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Champiqueso con queso mancheco\"]"));
    }

    public void Nutella() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Nutella®\"]"));
    }

    public void PLlevar() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Para Llevar\"]"));
    }

    public void Jumbo() {
        this.click(By.xpath("//android.view.View[@content-desc=\"Jumbo\"]"));
    }

    public void TeMediano() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Mediano Caliente\"]"));
    }

    public void CafeDescafeinado() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Café Descafeinado\"]"));
    }

    public void Grandes() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Grandes\"]"));
    }

    public void ChocolateMediano() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Mediano\"]"));
    }

    public void Grande() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Grande\"]"));
    }

    public void seismili() {
        this.click(By.xpath("//android.widget.TextView[@text=\"600 ML\"]"));
    }

    public void Mango() {
        this.click(By.xpath("(//android.widget.TextView[@text=\"Mango\"])[1]"));
    }

    public void Adobadas() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Adobadas\"]"));
    }

    public void Skittles() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Skittles®\"]"));
    }

    public void NachosChicos() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Chicos\"]"));
    }

    public void Cacahuate120g() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Cacahuate 120 g.\"]"));
    }

    public void NachosNachos() {
        this.click(By.xpath("(//android.widget.TextView[@text=\"Nachos\"])[2]"));
    }

    public void CookiesCream() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Cookies & Cream\"]"));
    }

    public void NachosTajin() {
        this.click(By.xpath("//android.widget.TextView[@text=\"NACHOS TAJIN\"]"));
    }

    public void Medianas() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Medianas\"]"));
    }

    public void Chicas() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Chicas\"]"));
    }

    public void CarlosV() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Carlos V®\"]"));
    }

    public void Chicas2() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Chicos\"]"));
    }

    public void Res() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Res\"]"));
    }

    public void Boneless() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Boneless\"]"));
    }

    public void Siguiente() {
        this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]/android.view.View/android.widget.Button"));
    }

    public void seisoz() {
        this.click(By.xpath("//android.widget.TextView[@text=\"6 Oz\"]"));
    }

    public void FresaCoco() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Fresa-Coco\"]"));
    }

    public void nachosBoneless() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Nachos Boneless\"]"));
    }

    public void nachosBrisquet() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Nachos Brisket de Res\"]"));
    }


    public void Chico() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Chico\"]"));
    }

    public void Mediano() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Mediano\"]"));
    }

    public void Guacamole() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Guacamole\"]"));
    }

    public void TexasDog() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Texas Dog\"]"));
    }

    public void Amareto() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Piña Colada Amareto\"]"));
    }

    public void CocaCola() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Coca-Cola®\"]"));
    }

    public void PinaColada() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Piña Colada Grande\"]"));
    }

    public void FrambuesaAzul() {
        this.click(By.xpath("(//android.widget.TextView[@text=\"Frambuesa Azul\"])[1]"));
    }

    public void Midori() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Piña Colada Midori\"]"));
    }

    public void kahlua() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Piña Colada Kahlua\"]"));
    }

    public void Pepino() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Pepino\"]"));
    }

    public void Manzana() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Manzana Verde\"]"));
    }

    public void Cereza() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Cereza\"]"));
    }

    public void Regresar() {
        this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.view.View/android.view.View[1]/android.widget.Button"));
    }

    public void Mas() {
        this.click(By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]/android.widget.Button"));
    }

    public void Algodon() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Algodon de azucar\"]"));
    }


    private void clickCardByTextWithFallback(String visibleText, int longTimeoutSeconds) {

        // ✅ 1) Construcción de XPaths Mejorada (Texto exacto o Descripción de accesibilidad)
        // Esto cubre tanto el TextView como el contenedor de Compose
        String xpathText   = "//*[@text=\"" + visibleText + "\" or @content-desc=\"" + visibleText + "\"]";
        String xpathParent = xpathText + "/..";

        By byText   = By.xpath(xpathText);
        By byParent = By.xpath(xpathParent);

        // ✅ 2) Fast path MUY corto (tap por coordenadas si está visible)
        if (isVisibleQuick(byText)) {
            try {
                WebElement el = driver.findElement(byText);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }
        if (isVisibleQuick(byParent)) {
            try {
                WebElement el = driver.findElement(byParent);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }

        // ✅ 3) One-shot vertical (rápido)
        try {
            findVisibleOrScrollToXpathAndClick(xpathText, Math.min(longTimeoutSeconds, 10));
            return;
        } catch (Throwable ignored) {}

        try {
            findVisibleOrScrollToXpathAndClick(xpathParent, Math.min(longTimeoutSeconds, 10));
            return;
        } catch (Throwable ignored) {}

        // ✅ 4) Si no salió con vertical, 1 sola pasada V/H y FIN (fast-fail)
        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(xpathText, Math.min(longTimeoutSeconds, 10), 5);
            return;
        } catch (Throwable ignored) {}

        try {
            findVisibleOrScrollDownAndRightSlowToXpathAndClick(xpathParent, Math.min(longTimeoutSeconds, 10), 5);
            return;
        } catch (Throwable ignored) {}

        // ✅ 5) Último intento “barato” con tap antes de lanzar error
        if (isVisibleQuick(byText)) {
            try {
                WebElement el = driver.findElement(byText);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }
        if (isVisibleQuick(byParent)) {
            try {
                WebElement el = driver.findElement(byParent);
                tapCenterW3C(el);
            } catch (Exception ignored) {}
            return;
        }

        // ✅ 6) Fallback REAL: swipes manuales (slowSwipeUp) + re-check
        // Esto asegura scroll real aunque los helpers no alcancen el elemento.
        try {
            int extraSwipes = Math.min(Math.max(longTimeoutSeconds, 6), 20); // 6..20
            for (int i = 0; i < extraSwipes; i++) {

                if (isVisibleQuick(byText)) {
                    try {
                        WebElement el = driver.findElement(byText);
                        tapCenterW3C(el);
                    } catch (Exception ignored) {}
                    return;
                }
                if (isVisibleQuick(byParent)) {
                    try {
                        WebElement el = driver.findElement(byParent);
                        tapCenterW3C(el);
                    } catch (Exception ignored) {}
                    return;
                }

                // 👇 ESTE MÉTODO SÍ EXISTE EN TU BasePage
                slowSwipeUp();
            }

            // Último re-check después de swipes
            if (isVisibleQuick(byText)) {
                try {
                    WebElement el = driver.findElement(byText);
                    tapCenterW3C(el);
                } catch (Exception ignored) {}
                return;
            }
            if (isVisibleQuick(byParent)) {
                try {
                    WebElement el = driver.findElement(byParent);
                    tapCenterW3C(el);
                } catch (Exception ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

        throw new RuntimeException("No se encontró el elemento (Texto/Desc): '" + visibleText + "' tras búsqueda rápida.");
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

            // 1) Intento rápido con UiScrollable por texto (si aplica)
            try {
                String uiScroll = "new UiScrollable(new UiSelector().scrollable(true))"
                        + ".scrollIntoView(new UiSelector().text(\"" + nombreProducto + "\"))";
                driver.findElement(AppiumBy.androidUIAutomator(uiScroll));
            } catch (Exception ignore) {
                rethrowIfAborted(ignore);
                // fallback manual
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
    // ─── Estado del carrusel: permite omitir el reset cuando la sección no cambia ───
    private String  lastAnchorXpath  = null;

    /**
     * Busca y clica un producto dentro del carrusel horizontal de una sección.
     *
     * OPTIMIZACIONES v2:
     *  1. implicitlyWait=0 en el bucle  → elimina ~10 s de espera por swipe infructuoso
     *     (el comportamiento anterior: 20 swipes × 10 s = hasta 200 s solo en waits)
     *  2. swipeRightInAnchorY reducido   → 350 ms + 80 ms = 430 ms (era 1100 ms, −61 %)
     *  3. Reset carrusel opcional        → se omite si la sección ya es la misma
     *  4. Métricas completas en log      → swipe N/total + tiempo total + producto
     *
     * La lógica funcional (forzarClic, verificarYAbortarSiAgotado, SKIP) se mantiene intacta.
     */
    private void clickRightFromAnchorOneTry(String anchorXpath, String targetXpath) {
        // 1. Extraer nombre del producto (sin cambios)
        String extractedText = targetXpath.replaceAll(".*@text=['\"]", "").replaceAll("['\"].*", "");

        final int ANCHOR_SCROLL_MAX  = 18;
        final int RESET_SWIPES       = 3;
        final int MAX_CAROUSEL_SWIPES = 20;

        long t0 = System.currentTimeMillis();
        log.info("[Búsqueda] Producto: \"{}\"", extractedText);

        // 2. Asegurar sección visible (con métricas de scroll vertical)
        if (!this.ensureVisibleByXpathNoClick(anchorXpath, ANCHOR_SCROLL_MAX)) {
            throw new RuntimeException("Sección no encontrada: " + anchorXpath);
        }

        WebElement anchorEl = this.driver.findElement(By.xpath(anchorXpath));
        int anchorY = anchorEl.getLocation().getY() + (anchorEl.getSize().getHeight() / 2);

        // 3. Reset del carrusel — se omite si estamos en la misma sección que la búsqueda anterior
        //    Esto evita 3 swipes de retroceso (~900 ms) cuando los productos son consecutivos.
        if (!anchorXpath.equals(lastAnchorXpath)) {
            this.resetCarouselFromAnchorY(anchorY, RESET_SWIPES);
            lastAnchorXpath = anchorXpath;
        } else {
            log.debug("[Búsqueda] Misma sección — reset de carrusel omitido");
        }

        // Ajuste de XPath para compatibilidad con Compose (texto o content-desc)
        String targetComposeXpath = "//*[@text='" + extractedText
                + "' or @content-desc='" + extractedText + "']";
        By target = By.xpath(targetComposeXpath);

        // 4. BUCLE DE BÚSQUEDA OPTIMIZADO
        //    KEY FIX: implicitlyWait=0 → driver.findElements() retorna inmediatamente
        //    si el elemento no está en pantalla, en lugar de esperar hasta 10 s.
        //    El implicit wait se restaura antes de cualquier acción que lo requiera.
        long tBucle = System.currentTimeMillis();
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

            for (int i = 0; i < MAX_CAROUSEL_SWIPES; i++) {

                // isVisibleInstantaneamente usa findElements con wait=0 ya activo
                if (isVisibleInstantaneamente(target)) {
                    log.info("[Búsqueda] \"{}\" encontrado en swipe {} | Tiempo búsqueda: {} ms",
                            extractedText, i, (System.currentTimeMillis() - tBucle));

                    // Restaurar timeout antes de acciones que dependen de él
                    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

                    // verificarYAbortarSiAgotado y forzarClic permanecen sin cambios
                    this.verificarYAbortarSiAgotado(extractedText);
                    try {
                        this.forzarClic(target);
                        log.info("[Búsqueda] Clic exitoso | Tiempo total: {} ms",
                                (System.currentTimeMillis() - t0));
                        return;
                    } catch (Exception e) {
                        this.verificarYAbortarSiAgotado(extractedText);
                        throw e;
                    }
                }

                log.debug("[Búsqueda] Swipe {}/{} → \"{}\"",
                        (i + 1), MAX_CAROUSEL_SWIPES, extractedText);
                this.swipeRightInAnchorY(anchorY);
            }

        } finally {
            // Garantiza restauración del timeout aunque el bucle lance excepción
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        // 5. No encontrado tras todos los swipes → SKIP (mantiene comportamiento original)
        long elapsed = System.currentTimeMillis() - tBucle;
        log.warn("[Búsqueda] \"{}\" NO localizado | Swipes: {} | Tiempo: {} ms",
                extractedText, MAX_CAROUSEL_SWIPES, elapsed);
        throw new org.opentest4j.TestAbortedException(
                "El alimento \"" + extractedText + "\" no fue localizado o está agotado.");
    }

    /**
     * Verifica si un elemento está presente en pantalla SIN esperar.
     * Requiere que implicitlyWait esté en 0 (lo establece el caller).
     * Método privado — solo usar dentro de clickRightFromAnchorOneTry.
     * No reemplaza isVisibleQuick; coexisten para contextos distintos.
     *
     * Usa !isEmpty() en lugar de safeDisplayed() (private en BasePage)
     * porque con wait=0 un elemento encontrado está necesariamente en el DOM
     * actual — la verificación de isDisplayed() es redundante aquí.
     */
    private boolean isVisibleInstantaneamente(By locator) {
        try {
            return !driver.findElements(locator).isEmpty();
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Swipe horizontal optimizado: 350 ms gesto + 80 ms pausa = 430 ms total.
     * Reducción respecto a versión anterior (600 ms + 500 ms = 1100 ms): −61 %.
     * El gesto de 350 ms es suficiente para que Compose detecte el scroll de carrusel
     * manteniendo estabilidad en Galaxy A56 5G / Android 15.
     */
    protected void swipeRightInAnchorY(int anchorY) {
        int screenWidth = driver.manage().window().getSize().width;
        int startX = (int) (screenWidth * 0.8);
        int endX   = (int) (screenWidth * 0.2);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), startX, anchorY));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(350),
                PointerInput.Origin.viewport(), endX, anchorY));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(swipe));

        // 80 ms: suficiente para que el carrusel de Compose complete la animación
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}
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

    protected boolean ensureVisibleByXpathNoClick(String xpath, int maxVerticalSwipes) {
        By locator = By.xpath(xpath);
        if (this.isVisibleQuick(locator)) {
            log.debug("[Scroll-V] Sección ya visible (sin scrolls necesarios)");
            return true;
        }
        for (int i = 0; i < maxVerticalSwipes; ++i) {
            if (this.isVisibleQuick(locator)) {
                log.info("[Scroll-V] Sección encontrada en scroll vertical {}/{}", (i + 1), maxVerticalSwipes);
                return true;
            }
            log.debug("[Scroll-V] Scroll vertical {}/{} — sección no visible aún", (i + 1), maxVerticalSwipes);
            this.slowSwipeUp();
            this.sleep(120L);
            if (this.isVisibleQuick(locator)) {
                log.info("[Scroll-V] Sección encontrada tras scroll vertical {}/{}", (i + 1), maxVerticalSwipes);
                return true;
            }
        }
        log.warn("[Scroll-V] Sección NO encontrada tras {} scrolls verticales", maxVerticalSwipes);
        return this.isVisibleQuick(locator);
    }

//    private void clickRightFromAnchorOneTry(String anchorXpath, String targetXpath, int verticalSwipes) {
//        boolean anchorVisible = this.ensureVisibleByXpathNoClick(anchorXpath, verticalSwipes);
//        if (!anchorVisible) {
//            throw new RuntimeException("No se encontró ancla en 1 intento: " + anchorXpath);
//        } else {
//            WebElement anchorEl = this.driver.findElement(By.xpath(anchorXpath));
//            int anchorY = anchorEl.getLocation().getY() + anchorEl.getSize().getHeight() / 2;
//            this.resetCarouselFromAnchorY(anchorY, 3);
//            By target = By.xpath(targetXpath);
//            if (this.sweepCatalogRightFromAnchorY(target, anchorY, 6)) {
//                this.click(target);
//            } else {
//                By targetParent = By.xpath(targetXpath + "/..");
//                if (this.sweepCatalogRightFromAnchorY(targetParent, anchorY, 6)) {
//                    this.click(targetParent);
//                } else {
//                    throw new RuntimeException("No se encontró target en 1 intento: " + targetXpath);
//                }
//            }
//        }
//    }

    private void clickRightFromAmericanoAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Americano\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }
    private void clickRightFromPalomasSkwinklesAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromPalomitasLiloAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Palomitas Lilo & Stitch\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }
    private void clickRightFromMaxiComboFamiliar(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }
    private void clickRightFromComboClasicoAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos Refresco Jumbo Pal Jumbo\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromComboNachosParejaAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromFrappeAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Malteadas Sencillas\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightCreppaDulceFrappeAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Combo Nachos en Pareja \"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromPretzelAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Pretzel\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromCrepasDulcesAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Crepas Dulces Premium\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromSkwinklesAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Skwinkles® Chunks sandia\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    private void clickRightFromBurgerAnchor(String targetText) {
        String anchorXpath = "//android.widget.TextView[@text=\"Boneless Mix\"]";
        String targetXpath = "//android.widget.TextView[@text=\"" + targetText + "\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickAguaEmbotellada() {
        String anchorXpath = "//android.widget.TextView[@text='Palomitas']";
        String targetXpath = "//android.widget.TextView[@text=\"Agua Embotellada\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickMaxiComboMix() {
        String anchorXpath = "//android.widget.TextView[@text=\"Maxicombo Familiar Jumbo\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Maxicombo Mix\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickNachos() {
        String anchorXpath = "//android.widget.TextView[@text='Palomitas']";
        String targetXpath = "//android.widget.TextView[@text=\"Nachos\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickMojito() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Mojito\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickCarajillo() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Carajillo\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickCerveza() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Cerveza Clásica\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void clickNegraModelo() {
        String anchorXpath = "//android.widget.TextView[@text=\"Mezcalada\"]";
        String targetXpath = "//android.widget.TextView[@text=\"Negra Modelo\"]";
        this.clickRightFromAnchorOneTry(anchorXpath, targetXpath);
    }

    public void seleccionarSaborPorContentDesc(String contentDesc, int index) {
        String xpath = "(//android.view.View[@content-desc=\"" + contentDesc + "\"])[" + index + "]";
        this.clickSmart(xpath, 10);
    }

    private WebElement findCardContainer(WebElement base) {
        try {
            return base.findElement(By.xpath("./ancestor::*[@clickable='true'][1]"));
        } catch (Exception var5) {
            try {
                return base.findElement(By.xpath("./ancestor::android.view.View[2]"));
            } catch (Exception var4) {
                try {
                    return base.findElement(By.xpath("./ancestor::android.view.View[3]"));
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
