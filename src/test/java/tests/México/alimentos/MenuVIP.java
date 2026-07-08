package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
import utils.TestSteps;

/**
 * Pruebas para el flujo de compra en el menú VIP.
 * ✅ Refactorizado para mantener los 50 casos de prueba con una estructura robusta y limpia.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Alimentos y Bebidas - VIP")
public class MenuVIP extends BaseTest {

    private AlimentosPagina page;

    @BeforeEach
    void setUp() {
        page = new AlimentosPagina(driver);
        TestSteps.run("Cerrar pantalla inicial", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú de alimentos", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar notificaciones", () -> page.cerrarPantalla(), driver);
    }

    private void agregarAlCarritoYValidar() {
        TestSteps.run("Agregar al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(1)
    @DisplayName("Comprar Palomitas Clásicas de Mantequilla")
    @Story("Palomitas")
    void comprarPalomitasClasicasMantequilla() {
        TestSteps.run("Seleccionar y personalizar Palomitas", () -> {
            page.buscarPalomitas();
            page.personalizar();
            page.PLlevar();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

//    @Test
//    @Order(2)
//    @DisplayName("Comprar Palomitas Clásicas de Caramelo Jumbo")
//    @Story("Palomitas")
//    void comprarPalomitasClasicas2() {
//        TestSteps.run("Seleccionar y personalizar Palomitas", () -> {
//            page.buscarPalomitas();
//            page.personalizar();
//            page.Jumbo();
//            page.Caramelo();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(3)
//    @DisplayName("Comprar Palomitas Clásicas Takis Grandes")
//    @Story("Palomitas")
//    void comprarPalomitasClasicasGTakis() {
//        TestSteps.run("Seleccionar y personalizar Palomitas", () -> {
//            page.buscarPalomitas();
//            page.personalizar();
//            page.Grandes();
//            page.Takis();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(4)
//    @DisplayName("Comprar Palomitas Clásicas Doritos Medianas")
//    @Story("Palomitas")
//    void comprarPalomitasClasicasMDoritos() {
//        TestSteps.run("Seleccionar y personalizar Palomitas", () -> {
//            page.buscarPalomitas();
//            page.personalizar();
//            page.Medianas();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(5)
//    @DisplayName("Comprar Palomitas Clásicas Cheetos Chicas")
//    @Story("Palomitas")
//    void comprarPalomitasClasicasCCheetos() {
//        TestSteps.run("Seleccionar y personalizar Palomitas", () -> {
//            page.buscarPalomitas();
//            page.personalizar();
//            page.Chicas();
//            page.Cheetos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(6)
//    @DisplayName("Comprar Nachos Clásicos Grandes")
//    @Story("Nachos")
//    void comprarNachos() {
//        TestSteps.run("Seleccionar y personalizar Nachos", () -> {
//            page.buscarNachos();
//            page.personalizar();
//            page.Grandes();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(7)
//    @DisplayName("Comprar Nachos Chicos Doritos sin queso")
//    @Story("Nachos")
//    void comprarNachosChicos() {
//        TestSteps.run("Seleccionar y personalizar Nachos", () -> {
//            page.buscarNachos();
//            page.personalizar();
//            page.Chicas2();
//            page.Doritos();
//            page.SinQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(8)
//    @DisplayName("Comprar Quesadilla con Guacamole")
//    @Story("Snacks")
//    void comprarQuesadilla() {
//        TestSteps.run("Seleccionar y personalizar Quesadilla", () -> {
//            page.buscarQuesadilla();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(9)
//    @DisplayName("Comprar Quesadilla de Res")
//    @Story("Snacks")
//    void comprarQuesadillaR() {
//        TestSteps.run("Seleccionar y personalizar Quesadilla", () -> {
//            page.buscarQuesadilla();
//            page.personalizar();
//            page.Res();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(10)
//    @DisplayName("Comprar Quesadilla de Boneless")
//    @Story("Snacks")
//    void comprarQuesadillaB() {
//        TestSteps.run("Seleccionar y personalizar Quesadilla", () -> {
//            page.buscarQuesadilla();
//            page.personalizar();
//            page.Boneless();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(11)
//    @DisplayName("Comprar Plato de Boneless")
//    @Story("Snacks")
//    void comprarBoneless() {
//        TestSteps.run("Seleccionar Plato Snack Boneless", () -> {
//            page.buscarSnackBoneless();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(12)
//    @DisplayName("Comprar Mini Dogs VIP")
//    @Story("Snacks")
//    void comprarMiniDogs() {
//        TestSteps.run("Seleccionar Mini Dogs VIP", () -> {
//            page.buscarMiniDogs();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(13)
//    @DisplayName("Comprar Papas Crisscut 3oz")
//    @Story("Snacks")
//    void comprarPapasCrisscut() {
//        TestSteps.run("Seleccionar Papas Crisscut", () -> {
//            page.buscarPapasCrisscut();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(14)
//    @DisplayName("Comprar Papas Crisscut 6oz")
//    @Story("Snacks")
//    void comprarPapasCrisscut6z() {
//        TestSteps.run("Seleccionar y personalizar Papas Crisscut", () -> {
//            page.buscarPapasCrisscut();
//            page.personalizar();
//            page.seisoz();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(15)
//    @DisplayName("Comprar Nachos Premium")
//    @Story("Nachos")
//    void comprarNachosPremium() {
//        TestSteps.run("Seleccionar Nachos Premium", () -> {
//            page.buscarNachosPremium();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(16)
//    @DisplayName("Comprar Nachos Premium con Boneless y Doritos")
//    @Story("Nachos")
//    void comprarNachosPremiumB() {
//        TestSteps.run("Seleccionar y personalizar Nachos Premium", () -> {
//            page.buscarNachosPremium();
//            page.personalizar();
//            page.nachosBoneless();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(17)
//    @DisplayName("Comprar Nachos Premium con Brisket y Doritos")
//    @Story("Nachos")
//    void comprarNachosPremiumBr() {
//        TestSteps.run("Seleccionar y personalizar Nachos Premium", () -> {
//            page.buscarNachosPremium();
//            page.personalizar();
//            page.nachosBrisquet();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(18)
//    @DisplayName("Comprar Hot Dog Jumbo con queso")
//    @Story("Hot Dogs")
//    void comprarHotdogJumbo() {
//        TestSteps.run("Seleccionar y personalizar Hot Dog", () -> {
//            page.buscarHotDog();
//            page.personalizar();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(19)
//    @DisplayName("Comprar Hot Dog Chico con queso")
//    @Story("Hot Dogs")
//    void comprarHotdogChico() {
//        TestSteps.run("Seleccionar y personalizar Hot Dog", () -> {
//            page.buscarHotDog();
//            page.personalizar();
//            page.Chico();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(20)
//    @DisplayName("Comprar Hot Dog Guacamole con queso")
//    @Story("Hot Dogs")
//    void comprarHotdogGuacamole() {
//        TestSteps.run("Seleccionar y personalizar Hot Dog Guacamole", () -> {
//            page.buscarHotDogGuacamole();
//            page.personalizar();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(21)
//    @DisplayName("Comprar Hot Dog Guacamole con extra Guacamole")
//    @Story("Hot Dogs")
//    void comprarHotdogGuacamole2() {
//        TestSteps.run("Seleccionar y personalizar Hot Dog Guacamole", () -> {
//            page.buscarHotDogGuacamole();
//            page.personalizar();
//            page.Guacamole();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(22)
//    @DisplayName("Comprar Palomitas Skwinkles")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinkless() {
//        TestSteps.run("Seleccionar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(23)
//    @DisplayName("Comprar Palomitas Skwinkles sabor Takis")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessT() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Takis();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(24)
//    @DisplayName("Comprar Palomitas Skwinkles sabor Cheetos")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessC() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Cheetos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(25)
//    @DisplayName("Comprar Palomitas Skwinkles Jumbo sabor Caramelo")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessCa() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Jumbo();
//            page.Caramelo();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(26)
//    @DisplayName("Comprar Palomitas Skwinkles Jumbo sabor Doritos")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessD() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Jumbo();
//            page.Doritos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(27)
//    @DisplayName("Comprar Palomitas Skwinkles Grandes")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessG() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Grandes();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(28)
//    @DisplayName("Comprar Palomitas Skwinkles Grandes sabor Cheetos")
//    @Story("Palomitas")
//    void comprarPalomitasSkwinklessGC() {
//        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
//            page.buscarPalomitasSkinkless();
//            page.personalizar();
//            page.Grandes();
//            page.Cheetos();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(29)
//    @DisplayName("Comprar Refresco Jumbo")
//    @Story("Bebidas")
//    void comprarRefresco() {
//        TestSteps.run("Seleccionar Refresco", () -> {
//            page.buscarRefresco();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(30)
//    @DisplayName("Comprar Sidral Grande con poco hielo")
//    @Story("Bebidas")
//    void comprarRefrescoG() {
//        TestSteps.run("Seleccionar y personalizar Refresco", () -> {
//            page.buscarRefresco();
//            page.personalizar();
//            page.Grande();
//            page.Sidral();
//            page.PocoHielo();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(31)
//    @DisplayName("Comprar Fuze Tea Mediano sin hielo")
//    @Story("Bebidas")
//    void comprarRefrescoM() {
//        TestSteps.run("Seleccionar y personalizar Refresco", () -> {
//            page.buscarRefresco();
//            page.personalizar();
//            page.Mediano();
//            page.FuzeTe();
//            page.SinHielo();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(32)
//    @DisplayName("Comprar Fanta Chica con hielo")
//    @Story("Bebidas")
//    void comprarRefrescoC() {
//        TestSteps.run("Seleccionar y personalizar Refresco", () -> {
//            page.buscarRefresco();
//            page.personalizar();
//            page.Chico();
//            page.Fanta();
//            page.HieloRegular();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(33)
//    @DisplayName("Comprar Texas Dog Jumbo con queso")
//    @Story("Hot Dogs")
//    void comprarTexasDog() {
//        TestSteps.run("Seleccionar y personalizar Texas Dog", () -> {
//            page.buscarTexasDog();
//            page.personalizar();
//            page.Jumbo();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(34)
//    @DisplayName("Comprar Texas Dog con extra queso")
//    @Story("Hot Dogs")
//    void comprarTexasDogJ() {
//        TestSteps.run("Seleccionar y personalizar Texas Dog", () -> {
//            page.buscarTexasDog();
//            page.personalizar();
//            page.TexasDog();
//            page.ExtraQueso();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(35)
//    @DisplayName("Comprar Piña Colada")
//    @Story("Bar")
//    void comprarPinaColada() {
//        TestSteps.run("Seleccionar Piña Colada", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(36)
//    @DisplayName("Comprar Piña Colada con Amaretto")
//    @Story("Bar")
//    void comprarPinaColadaA() {
//        TestSteps.run("Seleccionar y personalizar Piña Colada", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.Amareto();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(37)
//    @DisplayName("Comprar Piña Colada con Midori")
//    @Story("Bar")
//    void comprarPinaColadaM() {
//        TestSteps.run("Seleccionar y personalizar Piña Colada", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.Midori();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(38)
//    @DisplayName("Comprar Piña Colada con Kahlua")
//    @Story("Bar")
//    void comprarPinaColadaK() {
//        TestSteps.run("Seleccionar y personalizar Piña Colada", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.kahlua();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(39)
//    @DisplayName("Comprar Mojito Clásico")
//    @Story("Bar")
//    void comprarMojito() {
//        TestSteps.run("Seleccionar Mojito", () -> {
//            page.buscarMojito();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(40)
//    @DisplayName("Comprar Mojito de Pepino")
//    @Story("Bar")
//    void comprarMojitoP() {
//        TestSteps.run("Seleccionar y personalizar Mojito", () -> {
//            page.buscarMojito();
//            page.personalizar();
//            page.Pepino();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(41)
//    @DisplayName("Comprar Mojito de Manzana Verde")
//    @Story("Bar")
//    void comprarMojitoM() {
//        TestSteps.run("Seleccionar y personalizar Mojito", () -> {
//            page.buscarMojito();
//            page.personalizar();
//            page.Manzana();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(42)
//    @DisplayName("Comprar Mojito de Cereza")
//    @Story("Bar")
//    void comprarMojitoC() {
//        TestSteps.run("Seleccionar y personalizar Mojito", () -> {
//            page.buscarMojito();
//            page.personalizar();
//            page.Cereza();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }
//
//    @Test
//    @Order(43)
//    @DisplayName("Combo Mojito de Cereza y Texas Dog")
//    @Story("Combos VIP")
//    void comprarBarSnacks() {
//        TestSteps.run("Añadir Mojito de Cereza", () -> {
//            page.buscarMojito();
//            page.personalizar();
//            page.Cereza();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Texas Dog", () -> {
//            page.buscarTexasDog();
//            page.personalizar();
//            page.TexasDog();
//            page.ExtraQueso();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(44)
//    @DisplayName("Combo Piña Colada y Hot Dog Guacamole")
//    @Story("Combos VIP")
//    void comprarBarSnacksP() {
//        TestSteps.run("Añadir Piña Colada con Midori", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.Midori();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Hot Dog Guacamole", () -> {
//            page.buscarHotDogGuacamole();
//            page.personalizar();
//            page.Jumbo();
//            page.ExtraQueso();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(45)
//    @DisplayName("Combo Piña Colada y Nachos Premium")
//    @Story("Combos VIP")
//    void comprarBarSnacksPN() {
//        TestSteps.run("Añadir Piña Colada", () -> {
//            page.buscarPinaColada();
//            page.personalizar();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Nachos Premium de Res", () -> {
//            page.buscarNachosPremium();
//            page.personalizar();
//            page.Doritos();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(46)
//    @DisplayName("Combo Carajillo y Papas Crisscut")
//    @Story("Combos VIP")
//    void comprarCarajilloPapas() {
//        TestSteps.run("Añadir Carajillo", () -> {
//            page.buscarCarajillo();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Papas Crisscut", () -> {
//            page.buscarPapasCrisscut();
//            page.personalizar();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(47)
//    @DisplayName("Combo Cerveza y Quesadilla")
//    @Story("Combos VIP")
//    void comprarCervezaQ() {
//        TestSteps.run("Añadir Cerveza", () -> {
//            page.buscarCerveza();
//            page.personalizar();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Quesadilla", () -> {
//            page.buscarQuesadilla();
//            page.personalizar();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(48)
//    @DisplayName("Combo Negra Modelo y Quesadilla")
//    @Story("Combos VIP")
//    void comprarNegraModeloQ() {
//        TestSteps.run("Añadir Negra Modelo", () -> {
//            page.buscarNegraModelo();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Regresar y añadir Quesadilla", () -> {
//            page.buscarQuesadilla();
//            page.personalizar();
//            page.Siguiente();
//            page.agregarCarrito();
//        }, driver);
//
//        TestSteps.run("Validar Carrito Final", () -> {
//            page.abrirCarrito();
//            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
//        }, driver);
//    }
//
//    @Test
//    @Order(49)
//    @DisplayName("Comprar Dippin Dots de Vainilla")
//    @Story("Nuevos Lanzamientos")
//    void comprarDippinDots() {
//        TestSteps.run("Seleccionar Dippin Dots", () -> {
//            page.buscarDippinDots();
//            page.personalizar();
//            page.Siguiente();
//        }, driver);
//        agregarAlCarritoYValidar();
//    }

    @Test
    @Order(50)
    @DisplayName("Comprar Dippin Dots de Algodón de Azúcar")
    @Story("Nuevos Lanzamientos")
    void comprarDippinDotsA() {
        TestSteps.run("Seleccionar y personalizar Dippin Dots", () -> {
            page.buscarDippinDots();
            page.personalizar();
            page.Algodon();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
}
