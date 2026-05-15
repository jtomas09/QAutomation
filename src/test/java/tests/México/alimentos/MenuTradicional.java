package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
import utils.TestSteps;

/**
 * Pruebas para el flujo de compra en la sección Tradicional.
 * ✅ Refactorizado para mantener los 50 casos de prueba con una estructura robusta y limpia.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Alimentos y Bebidas - Tradicional")
public class MenuTradicional extends BaseTest {

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
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Takis y Refrescos")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliar() {
        //new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("Escala La Huerta");
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Takis();
            page.Siguiente(); // Palomitas 2
            page.Sidral();
            page.HieloRegular();
            page.Siguiente(); // Refresco 1
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Refresco 2
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Chocolates
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(2)
    @DisplayName("Comprar Maxi Combo Familiar con Refrescos variados")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarJ() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.CocaColaZero();
            page.HieloRegular();
            page.Siguiente(); // Refresco 1
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Refresco 2
            page.DelValle();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(3)
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Caramelo")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarJU() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Caramelo();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.CocaColaZero();
            page.HieloRegular();
            page.Siguiente(); // Refresco 1
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Refresco 2
            page.DelValle();
            page.SinHielo();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4
//            page.Siguiente(); // Chocolates
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(4)
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Takis (Variante)")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarT() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Takis();
            page.Siguiente();
            page.Siguiente();
            page.CocaColaZero();
            page.HieloRegular();
            page.Siguiente();
            page.Sprite();
            page.PocoHielo();
            page.Siguiente();
            page.DelValle();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(5)
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Doritos")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarD() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Doritos();
            page.Siguiente();
            page.Siguiente();
            page.CocaColaZero();
            page.HieloRegular();
            page.Siguiente();
            page.Sprite();
            page.PocoHielo();
            page.Siguiente();
            page.DelValle();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(6)
    @DisplayName("Comprar Maxi Combo Familiar con Refrescos sin Hielo")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarM() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente();
            page.Siguiente();
            page.CocaColaZero();
            page.SinHielo();
            page.Siguiente();
            page.Sidral();
            page.SinHielo();
            page.Siguiente();
            page.DelValle();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(7)
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Mixtas")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarCT() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Caramelo();
            page.Siguiente();
            page.Takis();
            page.Siguiente();
            page.CocaColaZero();
            page.HieloRegular();
            page.Siguiente();
            page.Sprite();
            page.PocoHielo();
            page.Siguiente();
            page.DelValle();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(8)
    @DisplayName("Comprar Maxi Combo Familiar con Palomitas Takis y Refrescos Mixtos")
    @Story("Combos Familiares")
    void comprarMaxiComboFamiliarMT() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente();
            page.Takis();
            page.Siguiente();
            page.Sidral();
            page.HieloRegular();
            page.Siguiente();
            page.Sprite();
            page.PocoHielo();
            page.Siguiente();
            page.CocaColaZero();
            page.SinHielo();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(9)
    @DisplayName("Comprar Combo ICEE con Topping Sirena")
    @Story("Combos ICEE")
    void comprarComboICEE() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente();
            page.Cereza();
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(10)
    @DisplayName("Comprar Combo ICEE con Skittles")
    @Story("Combos ICEE")
    void comprarComboICEEM() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente();
            page.Mango();
            page.seleccionarSaborPorContentDesc2("Cereza");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Skittles();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(11)
    @DisplayName("Comprar Combo ICEE con Palomitas Takis y Skwinkles Rellenos")
    @Story("Combos ICEE")
    void comprarComboICEES() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente();
            page.seleccionarSaborPorContentDesc2("Mango");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.SkwinklessRellenos();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(12)
    @DisplayName("Comprar Combo ICEE con Palomitas Caramelo y Skwinkles Salsagheti")
    @Story("Combos ICEE")
    void comprarComboICEESA() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Caramelo();
            page.Siguiente();
            page.Cereza();
            page.seleccionarSaborPorContentDesc2("Mango");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.SkwinklessSpaguetti();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(13)
    @DisplayName("Comprar Combo ICEE con Palomitas Doritos y Pelon Pelonazo")
    @Story("Combos ICEE")
    void comprarComboICEEP() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Doritos();
            page.Siguiente();
            page.Mango();
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.PelonPelonazo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(14)
    @DisplayName("Comprar Combo ICEE Jumbo con Aritos Enchilados")
    @Story("Combos ICEE")
    void comprarComboICEEAR() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.seleccionarSaborPorContentDesc2("Cereza");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(15)
    @DisplayName("Comprar Combo ICEE Jumbo con doble Cereza y Skittles")
    @Story("Combos ICEE")
    void comprarComboICEECC() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.Cereza();
            page.seleccionarSaborPorContentDesc2("Cereza");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Skittles();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(16)
    @DisplayName("Comprar Combo ICEE Jumbo con doble Mango y Skwinkles Rellenos")
    @Story("Combos ICEE")
    void comprarComboICEEMM() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Takis();
            page.Siguiente();
            page.Mango();
            page.seleccionarSaborPorContentDesc2("Mango");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.SkwinklessRellenos();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(17)
    @DisplayName("Comprar Combo ICEE Jumbo con Palomitas Caramelo y Salsagheti")
    @Story("Combos ICEE")
    void comprarComboICEEFF() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Caramelo();
            page.Siguiente();
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.SkwinklessSpaguetti();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(18)
    @DisplayName("Comprar Combo ICEE Jumbo con Palomitas Doritos y Pelonazo")
    @Story("Combos ICEE")
    void comprarComboICEEPP() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Doritos();
            page.Siguiente();
            page.Cereza();
            page.seleccionarSaborPorContentDesc2("Mango");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.PelonPelonazo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(19)
    @DisplayName("Comprar Combo ICEE con Salsagheti")
    @Story("Combos ICEE")
    void comprarComboICEEPM() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente();
            page.seleccionarSaborPorContentDesc2("Cereza");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.SkwinklessSpaguetti();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(20)
    @DisplayName("Comprar Combo ICEE con Palomitas Takis y Pelonazo")
    @Story("Combos ICEE")
    void comprarComboICEECF() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Takis();
            page.Siguiente();
            page.Cereza();
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.PelonPelonazo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(21)
    @DisplayName("Comprar Combo ICEE Jumbo con Palomitas Caramelo y Aritos")
    @Story("Combos ICEE")
    void comprarComboICEEJC() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Caramelo();
            page.Siguiente();
            page.Mango();
            page.seleccionarSaborPorContentDesc2("Cereza");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(22)
    @DisplayName("Comprar Combo ICEE Jumbo con Palomitas Takis y Skittles")
    @Story("Combos ICEE")
    void comprarComboICEEJT() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE Jumbo", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Jumbo();
            page.Takis();
            page.Siguiente();
            page.seleccionarSaborPorContentDesc2("Mango");
            page.Siguiente();
            page.Toppin();
            page.Siguiente();
            page.Skittles();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(23)
    @DisplayName("Combo Hot Dog Takis y Refresco")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescos() {
        TestSteps.run("Añadir Hot Dog Takis", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco", () -> {
            page.clickRefresco();
            page.personalizar();
            page.HieloRegular();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(24)
    @DisplayName("Combo Hot Dog Takis y Sidral Grande")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosGS() {
        TestSteps.run("Añadir Hot Dog Takis", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Grande", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Grande();
            page.Sidral();
            page.PocoHielo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(25)
    @DisplayName("Combo Hot Dog Takis y Sprite Mediano")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosMS() {
        TestSteps.run("Añadir Hot Dog Takis", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Mediano", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Mediano();
            page.Sprite();
            page.SinHielo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(26)
    @DisplayName("Combo Hot Dog Takis y Coca-Cola Light Chica")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosCC() {
        TestSteps.run("Añadir Hot Dog Takis", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Chico", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Chico();
            page.CocaColaLigth();
            page.HieloRegular();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(27)
    @DisplayName("Combo Hot Dog Takis Jumbo y Refresco Jumbo")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosJJ() {
        TestSteps.run("Añadir Hot Dog Takis Jumbo", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Jumbo", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Jumbo();
            page.FuzeTe();
            page.PocoHielo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(28)
    @DisplayName("Combo Hot Dog Takis Jumbo y Refresco Grande")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosJG() {
        TestSteps.run("Añadir Hot Dog Takis Jumbo", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Grande", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Grande();
            page.Fanta();
            page.HieloRegular();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(29)
    @DisplayName("Combo Hot Dog Takis Jumbo y Refresco Mediano")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosJM() {
        TestSteps.run("Añadir Hot Dog Takis Jumbo", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Mediano", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Mediano();
            page.DelValle2();
            page.SinHielo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(30)
    @DisplayName("Combo Hot Dog Takis Jumbo y Refresco Chico")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosJC() {
        TestSteps.run("Añadir Hot Dog Takis Jumbo", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Chico", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Chico();
            page.SinHielo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(31)
    @DisplayName("Combo Hot Dog Takis Chico y Refresco Jumbo")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosCJ() {
        TestSteps.run("Añadir Hot Dog Takis Chico", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Chico();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Jumbo", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Jumbo();
            page.Sprite();
            page.HieloRegular();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(32)
    @DisplayName("Combo Hot Dog Takis Jumbo y Sidral Jumbo")
    @Story("Combos Hot Dogs")
    void comprarHotDogRefrescosJJS() {
        TestSteps.run("Añadir Hot Dog Takis Jumbo", () -> {
            page.clickHotDogTakis();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Refresco Jumbo", () -> {
            page.clickRefresco();
            page.personalizar();
            page.Jumbo();
            page.Sidral();
            page.HieloRegular();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(33)
    @DisplayName("Combo Papas, Agua y Nachos Clásicos")
    @Story("Combos Snacks")
    void comprarSnacksPapasAgua() {
        TestSteps.run("Añadir Papas Fritas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua Embotellada", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos", () -> {
            page.clickNachos();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(34)
    @DisplayName("Combo Papas, Agua y Nachos Doritos")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaN() {
        TestSteps.run("Añadir Papas Fritas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua Embotellada", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Doritos", () -> {
            page.clickNachos();
            page.personalizar();
            page.Doritos();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(35)
    @DisplayName("Combo Papas Adobadas, Agua y Nachos")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaA() {
        TestSteps.run("Añadir Papas Adobadas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Adobadas();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 600ml", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.seismili();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos", () -> {
            page.clickNachos();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(36)
    @DisplayName("Combo Papas Adobadas, Agua y Nachos Doritos")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaT() {
        TestSteps.run("Añadir Papas Adobadas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Adobadas();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 600ml", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.seismili();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Doritos", () -> {
            page.clickNachos();
            page.personalizar();
            page.Doritos();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(37)
    @DisplayName("Combo Papas Adobadas, Agua y Nachos Tajín")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaD() {
        TestSteps.run("Añadir Papas Adobadas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Adobadas();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 1L", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Tajín", () -> {
            page.clickNachos();
            page.personalizar();
            page.NachosTajin();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(38)
    @DisplayName("Combo Papas Adobadas, Agua 600ml y Nachos Tajín")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaML() {
        TestSteps.run("Añadir Papas Adobadas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Adobadas();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 600ml", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.seismili();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Tajín", () -> {
            page.clickNachos();
            page.personalizar();
            page.NachosTajin();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(39)
    @DisplayName("Combo Papas Naturales, Agua 600ml y Nachos Takis")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaNA() {
        TestSteps.run("Añadir Papas Naturales", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 600ml", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.seismili();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Mix Takis Fuego", () -> {
            page.clickNachos();
            page.personalizar();
            page.MixTakisFuego();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(40)
    @DisplayName("Combo Papas Naturales, Agua 1L y Nachos Chicos Takis")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaCM() {
        TestSteps.run("Añadir Papas Naturales", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 1L", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Chicos Mix Takis Fuego", () -> {
            page.clickNachos();
            page.personalizar();
            page.NachosChicos();
            page.MixTakisFuego();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(41)
    @DisplayName("Combo Papas Adobadas, Agua 1L y Nachos Chicos Doritos")
    @Story("Combos Snacks")
    void comprarSnacksPapasAguaAC() {
        TestSteps.run("Añadir Papas Adobadas", () -> {
            page.clickPapasFritas();
            page.personalizar();
            page.Adobadas();
            page.Siguiente();
            page.agregarCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_ALIMENTOS_ICON);
        }, driver);

        TestSteps.run("Regresar y añadir Agua 1L", () -> {
            page.clickAguaEmbotellada();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Nachos Chicos Mix Doritos", () -> {
            page.clickNachos();
            page.personalizar();
            page.NachosChicos();
            page.MixDoritos();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(42)
    @DisplayName("Comprar Maxicombo Mix con Nachos Clásicos")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMix() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.HieloRegular();
            page.Siguiente(); // Refresco 1
            page.Sidral();
            page.HieloRegular();
            page.Siguiente(); // Refresco 2
            page.ExtraQueso();
            page.Siguiente();
            page.Siguiente();// Hot Dog
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(43)
    @DisplayName("Comprar Maxicombo Mix con Palomitas Caramelo y Nachos Doritos")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixC() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Caramelo();
            page.Siguiente();
            page.PocoHielo();
            page.Siguiente();
            page.Sprite();
            page.HieloRegular();
            page.Siguiente();
            page.Doritos();
            page.ExtraQueso();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(44)
    @DisplayName("Comprar Maxicombo Mix con Palomitas Takis y Nachos Doritos sin queso")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixT() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Takis();
            page.Siguiente();
            page.Fanta();
            page.SinHielo();
            page.Siguiente();
            page.Sprite();
            page.HieloRegular();
            page.Siguiente();
            page.NachosNachos();
            page.Doritos();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(45)
    @DisplayName("Comprar Maxicombo Mix Jumbo con Hot Dog Jumbo")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixM() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix Jumbo", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Jumbo();
            page.Siguiente();
            page.Fanta();
            page.SinHielo();
            page.Siguiente();
            page.DelValle();
            page.HieloRegular();
            page.Siguiente();
            page.NachosNachos();
            page.Doritos();
            page.Siguiente();
            page.Jumbo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(46)
    @DisplayName("Comprar Maxicombo Mix Jumbo con Palomitas Caramelo y Hot Dog Jumbo")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixCJ() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix Jumbo", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Jumbo();
            page.Caramelo();
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente();
            page.NachosNachos();
            page.ExtraQueso();
            page.Siguiente();
            page.Jumbo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(47)
    @DisplayName("Comprar Maxicombo Mix Jumbo con Palomitas Doritos y Nachos Tajín")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixD() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix Jumbo", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Jumbo();
            page.Doritos();
            page.Siguiente();
            page.DelValle();
            page.HieloRegular();
            page.Siguiente();
            page.SinHielo();
            page.Siguiente();
            page.NachosNachos();
            page.NachosTajin();
            page.Siguiente();
            page.Jumbo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(48)
    @DisplayName("Comprar Maxicombo Mix Jumbo con Palomitas Cheetos y Nachos Tajín")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixDC() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix Jumbo", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Jumbo();
            page.CheetosMix();
            page.Siguiente();
            page.Sidral();
            page.HieloRegular();
            page.Siguiente();
            page.PocoHielo();
            page.Siguiente();
            page.NachosTajin();
            page.ExtraQueso();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(49)
    @DisplayName("Comprar Maxicombo Mix con Palomitas Cheetos y extra queso")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixPC() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.CheetosMix();
            page.Siguiente();
            page.CocaColaLigth();
            page.HieloRegular();
            page.Siguiente();
            page.PocoHielo();
            page.Siguiente();
            page.ExtraQueso();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(50)
    @DisplayName("Comprar Maxicombo Mix con Hot Dog Jumbo")
    @Story("Combos Maxicombo")
    void comprarMaxicomboMixPCJ() {
        TestSteps.run("Seleccionar y personalizar Maxicombo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.CheetosMix();
            page.Siguiente();
            page.CocaColaLigth();
            page.HieloRegular();
            page.Siguiente();
            page.PocoHielo();
            page.Siguiente();
            page.ExtraQueso();
            page.Siguiente();
            page.Jumbo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
}
