package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
import utils.TestSteps;

/**
 * Pruebas para el flujo de compra en la sección Mi Cine.
 * ✅ Refactorizado para mantener los 20 casos de prueba con una estructura robusta y limpia.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Alimentos y Bebidas - Mi Cine")
public class MenuMiCine extends BaseTest {

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
    @DisplayName("Comprar Maxi Combo Mix con Palomitas de Caramelo")
    @Story("Combos Mi Cine")
    void comprarMaxiComboMix() {
        TestSteps.run("Buscar y seleccionar Maxi Combo Mix", () -> page.buscarTeCaliente(), driver);
        TestSteps.run("personalizar Maxi Combo Mix", () -> {
            page.personalizar();
            page.Caramelo();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(2)
    @DisplayName("Comprar Maxi Combo Mix con Palomitas de Doritos")
    @Story("Combos Mi Cine")
    void comprarMaxiComboMix2() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Doritos();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(3)
    @DisplayName("Comprar Maxi Combo Mix con Palomitas Takis")
    @Story("Combos Mi Cine")
    void comprarMaxiComboMix3() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.Takis();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(4)
    @DisplayName("Comprar Maxi Combo Mix con Palomitas Cheetos Mix")
    @Story("Combos Mi Cine")
    void comprarMaxiComboMix4() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Mix", () -> {
            page.clickMaxiComboMix();
            page.personalizar();
            page.CheetosMix();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(5)
    @DisplayName("Comprar Maxi Combo Familiar con Refrescos Light")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.CocaColaLigth();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(6)
    @DisplayName("Comprar Maxi Combo Familiar con Refrescos Zero")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar2() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.CocaColaZero();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(7)
    @DisplayName("Comprar Maxi Combo Familiar con Sprite")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar3() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.Sprite();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4

        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(8)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con M&Ms")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar4() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.Siguiente(); // Refresco 3
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(9)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con Cheetos y M&Ms")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar5() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Cheetos();
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(10)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con Takis y M&Ms")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar10() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Takis();
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.Siguiente(); // Refresco 3
            page.Cacahuate120g();
            page.Siguiente(); // Chocolates
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(11)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con Doritos y M&Ms")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar6() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente();
            page.Doritos();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.MMsCacahuate();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(12)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con Sprite")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar8() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Siguiente(); // Palomitas 1
            page.Siguiente(); // Palomitas 2
            page.Siguiente(); // Refresco 1
            page.Siguiente(); // Refresco 2
            page.Sprite();
            page.HieloRegular();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Refresco 4
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(13)
    @DisplayName("Comprar Maxi Combo Familiar Jumbo con Cheetos y Fanta")
    @Story("Combos Mi Cine")
    void comprarMaxiComboFamiliar7() {
        TestSteps.run("Seleccionar y personalizar Maxi Combo Familiar Jumbo", () -> {
            page.clickMaxiComboFamiliar();
            page.personalizar();
            page.Cheetos();
            page.Siguiente(); // Palomitas 1
            page.Cheetos();
            page.Siguiente(); // Palomitas 2
            page.CocaColaZero();
            page.Siguiente(); // Refresco 1
            page.CocaColaZero();
            page.Siguiente(); // Refresco 2
            page.Fanta();
            page.SinHielo();
            page.Siguiente(); // Refresco 3
            page.Siguiente(); // Chocolates
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(14)
    @DisplayName("Comprar Combo ICEE con Skwinkles y Topping")
    @Story("Combos ICEE")
    void comprarComboICCE() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            // page.seleccionarSaborPorContentDesc2("Cereza"); // Comentado en original
            page.Siguiente(); // Sabor ICEE 2
            page.Siguiente(); // Extras
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(15)
    @DisplayName("Comprar Combo ICEE con Frambuesa Azul y Topping")
    @Story("Combos ICEE")
    void comprarComboICCE2() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.FrambuesaAzul();
            page.Siguiente(); // Sabor ICEE 2
            page.Siguiente(); // Extras
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(16)
    @DisplayName("Comprar Combo ICEE con Mango y Topping")
    @Story("Combos ICEE")
    void comprarComboICCE3() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.Mango();
            page.Siguiente(); // Sabor ICEE 2
            page.Siguiente(); // Extras

        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(17)
    @DisplayName("Comprar Combo ICEE con Mango, Frambuesa y Topping")
    @Story("Combos ICEE")
    void comprarComboICCE4() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.FrambuesaAzul();
            page.Siguiente(); // Sabor ICEE 1
            page.Mango();
            page.Siguiente(); // Sabor ICEE 2
            page.Siguiente(); // Extras

        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(18)
    @DisplayName("Comprar Combo ICEE con Topping y Skwinkles Rellenos")
    @Story("Combos ICEE")
    void comprarComboICCE5() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.Siguiente(); // Extras
            page.SkwinklessRellenos();
            page.Siguiente(); // Dulces
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(19)
    @DisplayName("Comprar Combo ICEE con Topping y Skwinkles Salsaguetti")
    @Story("Combos ICEE")
    void comprarComboICCE6() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.Siguiente(); // Extras
            page.SkwinklessSpaguetti();
            page.Siguiente(); // Dulces
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(20)
    @DisplayName("Comprar Combo ICEE con Topping y M&M's Cacahuate")
    @Story("Combos ICEE")
    void comprarComboICCE7() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.Siguiente(); // Extras
            page.MMsCacahuate2();
            page.Siguiente(); // Dulces
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(21)
    @DisplayName("Comprar Combo ICEE con Topping y M&M's Chocolate")
    @Story("Combos ICEE con Skwinkles")
    void comprarComboICCE8() {
        TestSteps.run("Seleccionar y personalizar Combo ICEE", () -> {
            page.clickComboICEE();
            page.personalizar();
            page.Siguiente(); // Palomitas
            page.Siguiente(); // Sabor ICEE 1
            page.Siguiente(); // Sabor ICEE 2
            page.Siguiente(); // Dulces
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(22)
    @DisplayName("Comprar Combo Junior con Refrescos: Mediano - Coca cola - Hielo Regular y Dulces combo Junior:Skittles")
    @Story("Combo Junior")
    void comprarComboJunior() {
        TestSteps.run("Seleccionar y personalizar Combo Junior", () -> {
            page.clickComboJunior();
            page.personalizar();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(23)
    @DisplayName("Comprar Combo Junior con Palomitas Mantequilla - Refrescos: Mediano - Sprite sin Azúcar - Poco Hielo y Dulces combo Junior:Cacahuate")
    @Story("Combo Junior")
    void comprarComboJunior0() {
        TestSteps.run("Seleccionar y personalizar Combo Junior", () -> {
            page.clickComboJunior();
            page.personalizar();
            page.Siguiente(); // Sabor Refresco 1
            page.Sprite();
            page.Siguiente(); // Sabor Refresco 2
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Dulcería
            page.MMsCacahuate();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(24)
    @DisplayName("Comprar Combo Junior con Palomitas Caramelo - Refrescos: Mediano - Sidral Mundet sin Azúcar - Coca Cola Ligth - Sin Hielo y Dulces combo Junior:Chocolate")
    @Story("Combo Junior")
    void comprarComboJunior3() {
        TestSteps.run("Seleccionar y personalizar Combo Junior", () -> {
            page.clickComboJunior();
            page.personalizar();// Palomitas
            page.Caramelo();
            page.Siguiente(); // Sabor Refresco 1
            page.Sidral();
            page.Siguiente(); // Sabor Refresco 2
            page.CocaColaLigth();
            page.SinHielo();
            page.Siguiente(); // Dulcería
            page.MMsChocolate();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(25)
    @DisplayName("Comprar Combo Junior con Palomitas Doritos Nachos - Refrescos: Mediano - Fanta Naranja - Del Valle Frut - Hielo Regular y Dulces combo Junior:Skwinkles Salsaguetti")
    @Story("Combo Junior")
    void comprarComboJunior4() {
        TestSteps.run("Seleccionar y personalizar Combo Junior", () -> {
            page.clickComboJunior();
            page.personalizar();// Palomitas
            page.Doritos();
            page.Siguiente(); // Sabor Refresco 1
            page.Fanta();
            page.Siguiente(); // Sabor Refresco 2
            page.DelValle2();
            page.HieloRegular();
            page.Siguiente(); // Dulcería
            page.SkwinklessSpaguetti();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(26)
    @DisplayName("Comprar Combo Junior con Palomitas Takis - Refrescos: Mediano - Coca Cola Ligth - Sprite sin Azúcar - Poco Hielo y Dulces combo Junior:Pelon Pelonazo")
    @Story("Combo Junior")
    void comprarComboJunior2() {
        TestSteps.run("Seleccionar y personalizar Combo Junior", () -> {
            page.clickComboJunior();
            page.personalizar();// Palomitas
            page.TakisJr();
            page.Siguiente(); // Sabor Refresco 1
            page.CocaColaLigth();
            page.Siguiente(); // Sabor Refresco 2
            page.Sprite();
            page.PocoHielo();
            page.Siguiente(); // Dulcería
            page.PelonPelonazo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(27)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Coca Cola - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(28)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Coca Cola - Poco Hielo - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico2() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.PocoHielo();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(29)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Coca Cola - Sin Hielo - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico3() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.SinHielo();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(30)
    @DisplayName("Comprar Combo Clásico con Palomitas Caramelo - Refresco:Jumbo - Coca Cola - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico4() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Caramelo();
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(31)
    @DisplayName("Comprar Combo Clásico con Palomitas Takis Fuego - Refresco:Jumbo - Coca Cola - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico5() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Takis();
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(32)
    @DisplayName("Comprar Combo Clásico con Palomitas Doritos Nachos - Refresco:Jumbo - Coca Cola - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico6() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Doritos();
            page.Siguiente();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(33)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Sprite sin Azúcar - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico7() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.Sprite();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(34)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Sidral Mundet - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico8() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.Sidral();
            page.PocoHielo();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(35)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Sidral Mundet - Hielo Regular - HotDog:Chico")
    @Story("Combo Clásico")
    void comprarComboClasico9() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.Sidral();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(36)
    @DisplayName("Comprar Combo Clásico con Palomitas Mantequilla - Refresco:Jumbo - Coca Cola - Hielo Regular - HotDog:Jumbo")
    @Story("Combo Clásico")
    void comprarComboClasico10() {
        TestSteps.run("Seleccionar y personalizar Combo Clásico", () -> {
            page.clickComboClasico();
            page.personalizar();// Palomitas
            page.Siguiente();
            page.CocaCola();
            page.HieloRegular();
            page.Siguiente(); // HotDog
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(37)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas: Para Llevar - Mantequilla")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(38)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas: Jumbo - Mantequilla")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles2() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Jumbo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(39)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas: Grandes - Mantequilla")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles3() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Grandes();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(40)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas: Medianas - Mantequilla")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles4() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Medianas();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(41)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas: Chicas - Mantequilla")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles5() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Chicas();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(42)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Para Llevar - Caramelo")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles6() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Caramelo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(43)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Jumbo - Caramelo")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles7() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Jumbo();
            page.Caramelo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(44)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Grandes - Caramelo")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles8() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Grandes();
            page.Caramelo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(45)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Medianas - Caramelo")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles9() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Medianas();
            page.Caramelo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(46)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Chicas - Caramelo")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles10() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Chicas();
            page.Caramelo();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(47)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Para Llevar - Takis Fuego")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles11() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Takis();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(48)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Jumbo - Takis Fuego")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles12() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Jumbo();
            page.Takis();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(49)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Grandes - Takis Fuego")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles13() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Grandes();
            page.Takis();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
    @Test
    @Order(50)
    @DisplayName("Comprar Palomitas Skwinkles - Palomitas:Medianas - Takis Fuego")
    @Story("Palomitas Skwinkles")
    void comprarPalomitasSkwinkles14() {
        TestSteps.run("Seleccionar y personalizar Palomitas Skwinkles", () -> {
            page.clickPalomitasSkwinkles();
            page.personalizar();// Palomitas
            page.Medianas();
            page.Takis();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }
}
