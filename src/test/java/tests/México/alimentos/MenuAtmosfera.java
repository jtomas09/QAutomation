package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
import pages.common.CinemasHelper;
import utils.Cinema;
import utils.TestSteps;
import java.time.Duration;

/**
 * Pruebas para el flujo de compra en la sección Atmósfera.
 * ✅ Refactorizado para incluir TODOS los casos de prueba originales con una estructura robusta y limpia.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
@Epic("Alimentos y Bebidas - Atmósfera")
public class MenuAtmosfera extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(MenuAtmosfera.class);
    private static final String APP_PACKAGE = "com.cinepolis.go";

    private static boolean firstTest = true;

    private AlimentosPagina page;

    @BeforeEach
    void setUp() {
        if (firstTest) {
            log.info("Primer test detectado → NO reiniciar app");
            firstTest = false;
            waitForHomeReady();
        } else {
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("Reiniciando app entre pruebas");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            try {
                driver.terminateApp(APP_PACKAGE);
                log.info("App terminada correctamente");

                Thread.sleep(2000);

                driver.activateApp(APP_PACKAGE);
                log.info("App activada nuevamente");

                waitForHomeReady();
                log.info("App reiniciada y estable");
            } catch (Exception e) {
                log.error("Error reiniciando app", e);
                throw new RuntimeException("No se pudo reiniciar la app", e);
            }
        }

        page = new AlimentosPagina(driver);
        TestSteps.run("Abrir menú de alimentos", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar notificaciones", () -> page.cerrarPantalla(), driver);
    }

    private void waitForHomeReady() {
        log.info("Esperando carga de la app...");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));

        try {
            // Esperar a que la app renderice algo — home OR pantalla Club Cinépolis
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@text,'Cines')]")
                ),
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@text,'Inicia sesi')]")
                ),
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@content-desc,'CLUB')]")
                )
            ));

            log.info("App visible — descartando pantallas transitorias (Club Cinépolis, promos, etc.)");

            // dismissTransientPromosGuard maneja: Club Cinépolis login, Mario promo, location popup
            new CinemasHelper(driver).dismissTransientPromosGuard("waitForHomeReady");

            log.info("Esperando Home principal...");

            // Ahora sí esperar el home real
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(@text,'Cines')]")
            ));

            // Esperar que desaparezcan indicadores de carga
            wait.until(d -> {
                List<WebElement> loaders = d.findElements(
                    By.xpath("//*[contains(@text,'Cargando')]")
                );
                return loaders.isEmpty();
            });

            Thread.sleep(2500);

            log.info("Home cargado correctamente");
            log.info("Jetpack Compose estabilizado");

        } catch (Exception e) {
            log.error("Home no cargó correctamente", e);
            throw new RuntimeException("La app no terminó de cargar correctamente", e);
        }
    }

    /**
     * Helper para agregar un producto al carrito y validar que la pantalla del carrito sea visible.
     */
    private void agregarAlCarritoYValidar() {
        TestSteps.run("Agregar al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

   /*  @Test
    @Order(1)
    @DisplayName("Té de Manzanilla")
    @Story("Combos")
    void comprarCrepaDulceFrappe() {
       new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("La Perla");
        TestSteps.run("Seleccionar Crepa Dulce", () -> page.clickCrepasDulces1(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> {
            page.personalizar();
            page.MermeladaZarzamora();
            page.ExtraMermeladaFresa();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé de Agua Grande", () -> {
            page.personalizar();
            page.Grande();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    } */

    /* @Test
    @Order(2)
    @DisplayName("Combo Frappé de Coco y Crepa Dulce con Queso")
    @Story("Combos")
    void comprarCrepaDulceFrappesG() {
        TestSteps.run("Seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé de Agua Grande con Coco", () -> {
            page.personalizar();
            page.Grande();
            page.Coco();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Seleccionar Crepa Dulce", () -> page.clickCrepasDulces1(), driver);
        TestSteps.run("Personalizar Crepa Dulce con Queso Philadelphia", () -> {
            page.personalizar();
            page.MermeladaZarzamora();
            page.ExtraQuesoPhiladelphia();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    } */

  /*   @Test
    @Order(3)
    @DisplayName("Combo Frappé Sandía Pelonada y Crepa de Fresa")
    @Story("Combos")
    void comprarCrepaDulceFrappes() {
        TestSteps.run("Seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé de Agua Grande con Sandía Pelonada", () -> {
            page.personalizar();
            page.Grande();
            page.SandiaPelonada();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Seleccionar Crepa Dulce", () -> page.clickCrepasDulces1(), driver);
        TestSteps.run("Personalizar Crepa Dulce con Mermelada de Fresa", () -> {
            page.personalizar();
            page.MermeladaZarzamora();
            page.ExtraMermeladaFresa();
            page.Siguiente();
        }, driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    } */

    @Test
    @Order(1)
    @DisplayName("Té Caliente Menta Manzanilla - Patio Santa Fe")
    @Story("Bebidas Calientes")
    @Cinema("Patio Santa Fe")
    void comprarTeCalienteManzanillaPatioSantaFe() {
        TestSteps.run("Buscar y seleccionar Té Caliente", () -> page.buscarTeCaliente(), driver);
        TestSteps.run("Personalizar Té Caliente", () -> page.personalizar(), driver);
        TestSteps.run("Validar tamaño Grande Caliente por defecto", () ->
            page.validarElementoVisible(AlimentosLocators.BTN_GRANDE_CALIENTE), driver);
        TestSteps.run("Seleccionar sabor Té Menta Manzanilla", () -> page.TeMentaManzanilla(), driver);
        TestSteps.run("Confirmar personalización", () -> page.clickContinuar(), driver);
        TestSteps.run("Agregar al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.TXT_TE_CALIENTE_CARRITO);
            page.validarElementoVisible(AlimentosLocators.TXT_TE_VARIANTE_CARRITO);
        }, driver);
    }

    @Test
    @Order(2)
    @DisplayName("Té Caliente Menta Manzanilla - Arcos Bosques")
    @Story("Bebidas Calientes")
    @Cinema("Arcos Bosques")
    void comprarTeCalienteManzanillaArcosBosques() {
        TestSteps.run("Buscar y seleccionar Té Caliente", () -> page.buscarTeCaliente(), driver);
        TestSteps.run("Personalizar Té Caliente", () -> page.personalizar(), driver);
        TestSteps.run("Validar tamaño Grande Caliente por defecto", () ->
            page.validarElementoVisible(AlimentosLocators.BTN_GRANDE_CALIENTE), driver);
        TestSteps.run("Seleccionar sabor Té Menta Manzanilla", () -> page.TeMentaManzanilla(), driver);
        TestSteps.run("Confirmar personalización", () -> page.clickContinuar(), driver);
        TestSteps.run("Agregar al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.TXT_TE_CALIENTE_CARRITO);
            page.validarElementoVisible(AlimentosLocators.TXT_TE_VARIANTE_CARRITO);
        }, driver);
    }

//    @Test
//    @Order(4)
//    @DisplayName("Combo Crepa de Nutella y Frappé Fresa-Coco")
//    @Story("Combos Destacados")
//    void comprarPromociones() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.FresaCoco();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nutella®");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(5)
//    @DisplayName("Combo Crepa de Nuez y Frappé Piña Colada")
//    @Story("Combos Destacados")
//    void comprarPromocionesN() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.PinaColada();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nuez");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(6)
//    @DisplayName("Combo Crepa de Cajeta y Frappé Mango-Chamoy")
//    @Story("Combos Destacados")
//    void comprarPromocionesC() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.MangoChamoy();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Cajeta");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(7)
//    @DisplayName("Combo Crepa de Fresa y Frappé Jolly Rancher")
//    @Story("Combos Destacados")
//    void comprarPromocionesM() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.JollyRancherRaspberry();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Mermelada de fresa");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(8)
//    @DisplayName("Combo Crepa de Queso y Frappé Jolly Rancher")
//    @Story("Combos Destacados")
//    void comprarPromocionesQ() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.JollyRancherRaspberry();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Queso Philadelphia®");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(9)
//    @DisplayName("Combo Crepa de Nutella y Frappé Manzana Verde")
//    @Story("Combos Destacados")
//    void comprarPromocionesMa() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.ManzanaVerde();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nutella®");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(10)
//    @DisplayName("Combo Crepa de Nuez y Frappé Chicle-Plátano")
//    @Story("Combos Destacados")
//    void comprarPromocionesCP() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.ChiclePlatano();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nuez");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(11)
//    @DisplayName("Combo Crepa de Cajeta y Frappé Frutos Pelon")
//    @Story("Combos Destacados")
//    void comprarPromocionesPR() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.FrutosPelonPeloRico();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Cajeta");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(12)
//    @DisplayName("Combo Crepa de Fresa y Frappé Frutos Pelon")
//    @Story("Combos Destacados")
//    void comprarPromocionesPR2() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.FrutosPelonPeloRico();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Mermelada de fresa");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(13)
//    @DisplayName("Combo Crepa de Queso y Frappé Moras")
//    @Story("Combos Destacados")
//    void comprarPromocionesFP() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.MorasMaracuya();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Queso Philadelphia®");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(14)
//    @DisplayName("Combo Crepa de Nutella y Frappé Sandía Pelonada")
//    @Story("Combos Destacados")
//    void comprarPromocionesNT() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.SandiaPelonada();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nutella®");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(15)
//    @DisplayName("Combo Crepa de Nuez y Frappé Fresa Pelonada")
//    @Story("Combos Destacados")
//    void comprarPromocionesFPL() {
//        TestSteps.run("Seleccionar y personalizar Combo Crepa + Frappé", () -> {
//            page.clickCrepaFrappe();
//            page.personalizar();
//            page.FresaPelonada();
//            page.Siguiente();
//            page.seleccionarSaborPorContentDesc2("Nuez");
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(16)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 1")
//    @Story("Combos Nachos")
//    void comprarPromocionesCO() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Jumbo();
//            page.Caramelo();
//            page.Siguiente();
//            page.DelValle();
//            page.Siguiente();
//            page.CocaCola();
//            page.SinHielo();
//            page.Siguiente();
//            page.Grandes();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(17)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 2")
//    @Story("Combos Nachos")
//    void comprarPromocionesCOA() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Siguiente(); // Avanza en palomitas
//            page.FuzeTe();
//            page.Siguiente(); // Avanza en refresco 1
//            page.PocoHielo();
//            page.Siguiente(); // Avanza en refresco 2
//            page.NachosChicos();
//            page.MixDoritos();
//            page.Siguiente(); // Avanza en nachos
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(18)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 3")
//    @Story("Combos Nachos")
//    void comprarPromocionesNC() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Jumbo();
//            page.CheetosMix();
//            page.Siguiente();
//            page.Fanta();
//            page.Siguiente();
//            page.Sidral();
//            page.HieloRegular();
//            page.Siguiente();
//            page.Grandes();
//            page.MixTakisFuego();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(19)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 4")
//    @Story("Combos Nachos")
//    void comprarPromocionesNC2() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Takis();
//            page.Siguiente();
//            page.Sprite();
//            page.Siguiente();
//            page.Manzana();
//            page.SinHielo();
//            page.Siguiente();
//            page.MixDoritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(20)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 5")
//    @Story("Combos Nachos")
//    void comprarPromocionesNC3() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Jumbo();
//            page.Caramelo();
//            page.Siguiente();
//            page.CocaColaLigth();
//            page.Siguiente();
//            page.CocaColaZero();
//            page.PocoHielo();
//            page.Siguiente();
//            page.Grandes();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(21)
//    @DisplayName("Comprar Combo Nachos en Pareja Completo 6")
//    @Story("Combos Nachos")
//    void comprarPromocionesFPE2() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos en Pareja", () -> {
//            page.clickComboNachosPareja();
//            page.personalizar();
//            page.Siguiente(); // Palomitas
//            page.Siguiente(); // Refresco 1
//            page.CocaCola();
//            page.HieloRegular();
//            page.Siguiente(); // Refresco 2
//            page.Grandes();
//            page.NachosTajin();
//            page.Siguiente(); // Nachos
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(22)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Cajeta")
//    @Story("Combos Pretzel")
//    void comprarPromocionesFPE3() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.Siguiente(); // Frappe 1
//            page.Siguiente(); // Frappe 2
//            page.Siguiente(); // Palomitas
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(23)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Carlos V")
//    @Story("Combos Pretzel")
//    void comprarPromocionesFPE4() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.CarlosV();
//            page.Siguiente();
//            page.CarlosV();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(24)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Cookies & Cream")
//    @Story("Combos Pretzel")
//    void comprarPromocionesFPV() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.CookiesCream();
//            page.Siguiente();
//            page.CookiesCream();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(25)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé M&M's")
//    @Story("Combos Pretzel")
//    void comprarPromocionesFPE() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.MMs();
//            page.Siguiente();
//            page.MMs();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(26)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Capuccino")
//    @Story("Combos Pretzel")
//    void comprarPromocionesCap() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.Capuccino();
//            page.Siguiente();
//            page.Capuccino();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(27)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Chocolate Blanco")
//    @Story("Combos Pretzel")
//    void comprarPromocionesChoco() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.ChocolateBlanco();
//            page.Siguiente();
//            page.ChocolateBlanco();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(28)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Moka Caramelo")
//    @Story("Combos Pretzel")
//    void comprarPromocionesMoka() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.MokaCaramelo();
//            page.Siguiente();
//            page.MokaCaramelo();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(29)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Macchiato Coco")
//    @Story("Combos Pretzel")
//    void comprarPromocionesMacchiatoCoco() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.MacchiatoCoco();
//            page.Siguiente();
//            page.MacchiatoCoco();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(30)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Macchiato Menta")
//    @Story("Combos Pretzel")
//    void comprarPromocionesMacchiatoMenta() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.MacchiatoMenta();
//            page.Siguiente();
//            page.MacchiatoMenta();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(31)
//    @DisplayName("Comprar Combo Pretzel en Pareja con Frappé Crema Irlandesa")
//    @Story("Combos Pretzel")
//    void comprarPromocionesMacchiato() {
//        TestSteps.run("Seleccionar y personalizar Combo Pretzel", () -> {
//            page.clickComboPretzelPareja();
//            page.personalizar();
//            page.MacchiatoCremaIrlandesa();
//            page.Siguiente();
//            page.MacchiatoCremaIrlandesa();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(32)
//    @DisplayName("Comprar Combo Nachos simple 1")
//    @Story("Combos Nachos")
//    void comprarComboNachos1() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Siguiente(); // Palomitas
//            page.HieloRegular();
//            page.Siguiente(); // Refresco
//            page.NachosTajin();
//            page.Siguiente(); // Nachos
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(33)
//    @DisplayName("Comprar Combo Nachos simple 2")
//    @Story("Combos Nachos")
//    void comprarComboNachos2() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Caramelo();
//            page.Siguiente();
//            page.Sidral();
//            page.SinHielo();
//            page.Siguiente();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(34)
//    @DisplayName("Comprar Combo Nachos simple 3")
//    @Story("Combos Nachos")
//    void comprarComboNachos3() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Jumbo();
//            page.Siguiente();
//            page.CocaColaLigth();
//            page.PocoHielo();
//            page.Siguiente();
//            page.Grandes();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(35)
//    @DisplayName("Comprar Combo Nachos simple 4")
//    @Story("Combos Nachos")
//    void comprarComboNachos() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Jumbo();
//            page.Caramelo();
//            page.Siguiente();
//            page.Fanta();
//            page.HieloRegular();
//            page.Siguiente();
//            page.MixTakisFuego();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(36)
//    @DisplayName("Comprar Combo Nachos simple 5")
//    @Story("Combos Nachos")
//    void comprarComboNachos6() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Takis();
//            page.Siguiente();
//            page.Sprite();
//            page.SinHielo();
//            page.Siguiente();
//            page.Grandes();
//            page.MixDoritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(37)
//    @DisplayName("Comprar Combo Nachos: Palomitas:Jumbo - Doritos Nachos - Refresco: Delvalle Frut - Hielo Regular - Nachos:Chicos - Doritos Nachos")
//    @Story("Combos Nachos")
//    void comprarComboNachos7() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.Jumbo();
//            page.Doritos();
//            page.Siguiente();
//            page.DelValle();
//            page.HieloRegular();
//            page.Siguiente();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(38)
//    @DisplayName("Comprar Combo Nachos: Palomitas:Para Llevar - Cheetos Mix - Refresco: Fuze tea sin Azúcar - Poco Hielo - Nachos:Grandes - Nachos Tajín")
//    @Story("Combos Nachos")
//    void comprarComboNachos8() {
//        TestSteps.run("Seleccionar y personalizar Combo Nachos", () -> {
//            page.clickComboNachos();
//            page.personalizar();
//            page.CheetosMix();
//            page.Siguiente();
//            page.FuzeTe();
//            page.PocoHielo();
//            page.Siguiente();
//            page.Grandes();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(39)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Cajeta - Leche Entera - Crepa: Nutella")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.Siguiente();
//            page.Nutella();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(40)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Carlos V - Leche Deslactosada - Crepa: Queso Philadelphia")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche2() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.CarlosV();
//            page.LecheDeslactosada();
//            page.Siguiente();
//            page.QuesoPhiladelphia();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(41)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Cookies & Cream - Leche de Almendra - Crepa: Nuez")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche3() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.CookiesCream();
//            page.LecheAlmendra();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(42)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Moka Caramelo - Leche Entera - Crepa: Cajeta")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche4() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.MokaCaramelo();
//            page.Siguiente();
//            //page.Cajeta();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(43)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Chocolate Blanco - Leche Deslactosada - Crepa: Nutella")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche5() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.ChocolateBlanco();
//            page.LecheDeslactosada();
//            page.Siguiente();
//            page.Nutella();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(44)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Cappuccino - Leche de Almendra - Crepa: Queso Philadelphia")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche6() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.Capuccino();
//            page.LecheAlmendra();
//            page.Siguiente();
//            page.QuesoPhiladelphia();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(45)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Macchiato Menta - Leche Entera - Crepa: Queso Philadelphia")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche7() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.MacchiatoMenta();
//            page.Siguiente();
//            page.QuesoPhiladelphia();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(46)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Macchiato Coco - Leche Deslactosada - Crepa: Nutella")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche8() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.MacchiatoCoco();
//            page.LecheDeslactosada();
//            page.Siguiente();
//            page.Nutella();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//    @Test
//    @Order(47)
//    @DisplayName("Comprar Crepa + Frappé Leche: Sabor Macchiato Crema Irlandesa - Leche de Almendra - Crepa: Nuez")
//    @Story("Crepa + Frappé Leche")
//    void comprarCrepaFrappeLeche9() {
//        TestSteps.run("Seleccionar y personalizar Crepa + Frappé Leche", () -> {
//            page.clickCrepaFrappeLeche();
//            page.personalizar();
//            page.MacchiatoCremaIrlandesa();
//            page.LecheAlmendra();
//            page.Siguiente();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
}
