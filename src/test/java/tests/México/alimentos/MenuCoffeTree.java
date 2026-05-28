package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
import pages.common.CinemasHelper;
import utils.TestSteps;

/**
 * Pruebas para el flujo de compra en CoffeTree, refactorizadas para máxima robustez y legibilidad.
 * ✅ Mantiene los 50 casos de prueba originales.
 * ✅ Centralizada la configuración inicial y la navegación.
 * ✅ Reutiliza la lógica de validación para tests más limpios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Alimentos y Bebidas - Coffe Tree")
public class MenuCoffeTree extends BaseTest {

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
    @DisplayName("Comprar Americano Grande con Coco")
    @Story("Bebidas Calientes")
    void comprarAmericano() {
      new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("Escala Morelia");
        TestSteps.run("Seleccionar y personalizar Americano", () -> {
            page.clickAmericano();
            page.personalizar();
            page.Coco();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(2)
    @DisplayName("Comprar Americano Descafeinado con Crema Irlandesa")
    @Story("Bebidas Calientes")
    void comprarAmericanoG() {
        TestSteps.run("Seleccionar y personalizar Americano Descafeinado", () -> {
            page.clickAmericano();
            page.personalizar();
            page.CafeDescafeinado();
            page.CremaIrlandesa();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(3)
    @DisplayName("Comprar Americano Mediano con Menta")
    @Story("Bebidas Calientes")
    void comprarAmericanoGM() {
        TestSteps.run("Seleccionar y personalizar Americano Mediano", () -> {
            page.clickAmericano();
            page.personalizar();
            page.CafeMediano();
            page.EsenciaMenta();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(4)
    @DisplayName("Comprar Americano Mediano con Vainilla")
    @Story("Bebidas Calientes")
    void comprarAmericanoGMV() {
        TestSteps.run("Seleccionar y personalizar Americano Mediano", () -> {
            page.clickAmericano();
            page.personalizar();
            page.CafeMediano();
            page.EsenciaVainilla();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(5)
    @DisplayName("Comprar Moka Oscuro con Coco")
    @Story("Bebidas Calientes")
    void comprarMokaOscuro() {
        TestSteps.run("Seleccionar y personalizar Moka Oscuro", () -> {
            page.clickMokaOscuro();
            page.personalizar();
            page.Coco();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(6)
    @DisplayName("Comprar Moka Oscuro Descafeinado con Leche Deslactosada")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroG() {
        TestSteps.run("Seleccionar y personalizar Moka Oscuro Descafeinado", () -> {
            page.clickMokaOscuro();
            page.personalizar();
            page.CafeDescafeinado();
            page.LecheDeslactosada();
            page.CremaIrlandesa();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(7)
    @DisplayName("Comprar Moka Oscuro Mediano con Leche de Almendra")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroM() {
        TestSteps.run("Seleccionar y personalizar Moka Oscuro Mediano", () -> {
            page.clickMokaOscuro();
            page.personalizar();
            page.CafeMediano();
            page.LecheAlmendra();
            page.CremaIrlandesa();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(8)
    @DisplayName("Comprar Moka Oscuro Mediano con Vainilla")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroMD() {
        TestSteps.run("Seleccionar y personalizar Moka Oscuro Mediano", () -> {
            page.clickMokaOscuro();
            page.personalizar();
            page.CafeMediano();
            page.EsenciaVainilla();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(9)
    @DisplayName("Comprar Capuccino con Coco")
    @Story("Bebidas Calientes")
    void comprarCapuccino() {
        TestSteps.run("Seleccionar y personalizar Capuccino", () -> {
            page.clickCapuccino();
            page.personalizar();
            page.Coco();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(10)
    @DisplayName("Comprar Té Caliente de Jamaica con Coco")
    @Story("Bebidas Calientes")
    void comprarTe() {
        TestSteps.run("Seleccionar y personalizar Té Caliente", () -> {
            page.clickTeCaliente();
            page.personalizar();
            page.TeJamaica();
            page.Coco();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(11)
    @DisplayName("Comprar Té Caliente Mediano con Menta")
    @Story("Bebidas Calientes")
    void comprarTeM() {
        TestSteps.run("Seleccionar y personalizar Té Caliente Mediano", () -> {
            page.clickTeCaliente();
            page.personalizar();
            page.TeMediano();
            page.EsenciaMenta();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(12)
    @DisplayName("Comprar Chocolate con Coco")
    @Story("Bebidas Calientes")
    void comprarChocolate() {
        TestSteps.run("Seleccionar y personalizar Chocolate", () -> {
            page.clickChocolate();
            page.personalizar();
            page.Coco();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(13)
    @DisplayName("Comprar Chocolate Mediano con Crema Irlandesa")
    @Story("Bebidas Calientes")
    void comprarChocolateM() {
        TestSteps.run("Seleccionar y personalizar Chocolate Mediano", () -> {
            page.clickChocolate();
            page.personalizar();
            page.ChocolateMediano();
            page.CremaIrlandesa();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(14)
    @DisplayName("Comprar Pretzel")
    @Story("Postres y Helados")
    void comprarPretzel() {
        TestSteps.run("Seleccionar y personalizar Chocolate Mediano", () -> {
            page.clickPretzel();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(15)
    @DisplayName("Comprar Cheese Cake")
    @Story("Postres y Helados")
    void comprarCheeseCake() {
        TestSteps.run("Seleccionar Cheese Cake", page::clickCheeseCake, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(16)
    @DisplayName("Comprar Cornetto")
    @Story("Postres y Helados")
    void comprarCornetto() {
        TestSteps.run("Seleccionar y personalizar Cornetto", () -> {
            page.clickCornetto();
            page.personalizar();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(17)
    @DisplayName("Comprar Skwinkles Chunks")
    @Story("Dulces y Chocolates")
    void comprarSkwinkles() {
        TestSteps.run("Seleccionar Skwinkles", page::clickSkwinkles, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(18)
    @DisplayName("Comprar M&M's")
    @Story("Dulces y Chocolates")
    void comprarMM() {
        TestSteps.run("Seleccionar y personalizar M&M's", () -> {
            page.clickMM();
            page.personalizar();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(19)
    @DisplayName("Comprar Hershey's")
    @Story("Dulces y Chocolates")
    void comprarHersheys() {
        TestSteps.run("Seleccionar Hershey's", page::clickHersheys, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(20)
    @DisplayName("Comprar Snickers")
    @Story("Dulces y Chocolates")
    void comprarSnickers() {
        TestSteps.run("Seleccionar Snickers", page::clickSnickers, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(21)
    @DisplayName("Comprar Crepa Dulce Premium")
    @Story("Crepas")
    void comprarCrepas() {
        TestSteps.run("Seleccionar y personalizar Crepa Dulce", () -> {
            page.clickCrepasDulces();
            page.personalizar();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(22)
    @DisplayName("Comprar Crepa de Manzana Canela")
    @Story("Crepas")
    void comprarCrepasM() {
        TestSteps.run("Seleccionar y personalizar Crepa de Manzana Canela", () -> {
            page.clickCrepasDulces();
            page.personalizar();
            page.ManzanaCanela();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(23)
    @DisplayName("Comprar Crepa Salada Premium Hawaiana")
    @Story("Crepas")
    void comprarCrepasS() {
        TestSteps.run("Seleccionar y personalizar Crepa Salada Hawaiana", () -> {
            page.clickCrepaSalada();
            page.personalizar();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(24)
    @DisplayName("Comprar Crepa Salada de Champiqueso")
    @Story("Crepas")
    void comprarCrepasSP() {
        TestSteps.run("Seleccionar y personalizar Crepa de Champiqueso", () -> {
            page.clickCrepaSalada();
            page.personalizar();
            page.Champiqueso();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(25)
    @DisplayName("Comprar Crepa Salada Italiana con Queso Manchego")
    @Story("Crepas")
    void comprarCrepasSI() {
        TestSteps.run("Seleccionar y personalizar Crepa Italiana", () -> {
            page.clickCrepaSalada();
            page.personalizar();
            page.ChampiquesoManchego();
            page.Siguiente();
        }, driver);
        agregarAlCarritoYValidar();
    }

    // --- Tests de Combos Refactorizados ---

    @Test
    @Order(26)
    @DisplayName("Combo Crepa Salada con Queso y Frappé con Coco")
    @Story("Combos")
    void comprarCrepasFrappe() {
        TestSteps.run("Añadir Crepa Salada con Queso Philadelphia", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé con Coco", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Coco();
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
    @DisplayName("Combo Crepa Salada de Pavo y Frappé de Sandía")
    @Story("Combos")
    void comprarCrepasFrappeM() {
        TestSteps.run("Añadir Crepa Salada de Jamón de Pavo", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Sandía Pelonada", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.SandiaPelonada();
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
    @DisplayName("Combo Crepa con Tocino y Frappé con Crema Irlandesa")
    @Story("Combos")
    void comprarCrepasFrappeG() {
        TestSteps.run("Añadir Crepa de Queso Philadelphia con Tocino", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.Tocino();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé Grande con Crema Irlandesa", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Grande();
            page.CremaIrlandesa();
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
    @DisplayName("Combo Crepa con Champiñón y Frappé de Mango Tajín")
    @Story("Combos")
    void comprarCrepasFrapeMA() {
        TestSteps.run("Añadir Crepa de Queso Manchego con Champiñón", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoManchego();
            page.Champinon();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Mango Tajín", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.MangoTajin();
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
    @DisplayName("Combo Crepa con extras y Frappé con Vainilla")
    @Story("Combos")
    void comprarCrepasFrappeS() {
        TestSteps.run("Añadir Crepa de Queso Manchego con Jamón y Tocino", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoManchego();
            page.ExtraJamonPavo();
            page.Tocino();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Sandía con Vainilla", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.SandiaPelonada();
            page.EsenciaVainilla();
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
    @DisplayName("Combo Crepa con Jamón y Frappé con extras")
    @Story("Combos")
    void comprarCrepasFrappeJP() {
        TestSteps.run("Añadir Crepa de Jamón de Pavo con extra", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.ExtraJamonPavo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé con Coco y Vainilla", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Coco();
            page.EsenciaVainilla();
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
    @DisplayName("Combo Crepa con Tocino y Frappé de Leche con Chocolate")
    @Story("Combos")
    void comprarCrepasFrappeL() {
        TestSteps.run("Añadir Crepa de Jamón de Pavo con Tocino", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.Tocino();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche con Salsa de Chocolate", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.SalsaChocolate();
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
    @DisplayName("Combo Crepa con Queso y Frappé de Leche Deslactosada")
    @Story("Combos")
    void comprarCrepasFrappeLD() {
        TestSteps.run("Añadir Crepa de Queso Philadelphia", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Moka Caramelo Mediano", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Mediano();
            page.MokaCaramelo();
            page.LecheDeslactosada();
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
    @DisplayName("Combo Crepa de Manchego y Frappé de Leche con Vainilla")
    @Story("Combos")
    void comprarCrepasFrapp() {
        TestSteps.run("Añadir Crepa de Queso Manchego con extra", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoManchego();
            page.ExtraQuesoManchego();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé Grande con Esencia de Vainilla", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Grande();
            page.MokaCaramelo();
            page.EsenciaVainilla();
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
    @DisplayName("Combo Crepa con Champiñón y Frappé de Leche con Coco")
    @Story("Combos")
    void comprarCrepasFrappess() {
        TestSteps.run("Añadir Crepa de Queso Philadelphia con Champiñón", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.Champinon();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche Deslactosada con Coco", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.LecheDeslactosada();
            page.Coco();
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
    @DisplayName("Combo Crepa con Tocino/Queso y Frappé de Leche Mediano")
    @Story("Combos")
    void comprarCrepasFrappesC() {
        TestSteps.run("Añadir Crepa de Jamón de Pavo con extras", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.Tocino();
            page.ExtraQuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Chocolate Blanco Mediano", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Mediano();
            page.ChocolateBlanco();
            page.CremaIrlandesa();
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
    @DisplayName("Combo Crepa de Manchego y Frappé de Leche Capuccino")
    @Story("Combos")
    void comprarCrepasFrappesOnly() {
        TestSteps.run("Añadir Crepa de Queso Manchego", () -> {
            page.clickCrepaSalada1();
            page.personalizar();
            page.QuesoManchego();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche Capuccino", () -> {
            page.clickFrappeLeche();
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
    @Order(38)
    @DisplayName("Combo Crepa Dulce 2 Ing. y Frappé con Crema Irlandesa")
    @Story("Combos")
    void comprarCrepas2Frappes() {
        TestSteps.run("Añadir Crepa Dulce 2 Ingredientes con extra Manzana", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.ExtraManzana();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Agua con Crema Irlandesa", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.CremaIrlandesa();
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
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella y Frappé con Menta")
    @Story("Combos")
    void comprarCrepas2Frappe() {
        TestSteps.run("Añadir Crepa Dulce 2 Ingredientes con Nutella", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.seleccionarSaborPorContentDesc2("Nutella®");
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé Mediano de Sandía con Menta", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Mediano();
            page.SandiaPelonada();
            page.EsenciaMenta();
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
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Nutella y Frappé Grande")
    @Story("Combos")
    void comprarCrepas2FrappeFre() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con extras", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.Nutella();
            page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2);
            page.ExtraMermeladaFresa();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Agua Grande", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Grande();
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
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso y Frappé con Coco")
    @Story("Combos")
    void comprarCrepas2FrappePH() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Queso Philadelphia", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.ExtraQuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Mango Tajín con Coco", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.MangoTajin();
            page.Coco();
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
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella/Fresa y Frappé con Vainilla")
    @Story("Combos")
    void comprarCrepas2FrappeZ() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Nutella y extra Fresa", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.Nutella();
            page.ExtraMermeladaFresa();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé Grande de Sandía con Vainilla", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Grande();
            page.SandiaPelonada();
            page.EsenciaVainilla();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(43)
    @DisplayName("Combo Crepa Dulce 2 Ing. con múltiples extras y Frappé con Coco")
    @Story("Combos")
    void comprarCrepas2FrappeME() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Nutella, Fresa y Manzana", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.Nutella();
            page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2);
            page.ExtraManzana();
            page.ExtraMermeladaFresa();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Agua con Coco", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Coco();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(44)
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso/Nuez y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeNu() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Queso y Nuez", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.seleccionarSaborPorContentDesc2("Nuez");
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche con Salsa de Chocolate", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.SalsaChocolate();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(45)
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Manzana y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeFr() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Fresa y extra Manzana", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2);
            page.ExtraManzana();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé Grande de Chocolate Blanco", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Grande();
            page.ChocolateBlanco();
            page.LecheDeslactosada();
            page.EsenciaVainilla();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(46)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella/Queso y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeP() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Nutella y Queso", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.Nutella();
            page.seleccionarSaborPorContentDesc("Queso Philadelphia®", 2);
            page.ExtraQuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche Mediano", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Mediano();
            page.MokaCaramelo();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(47)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella y Frappé de Leche con Coco")
    @Story("Combos")
    void comprarCrepas2FrappeNM() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Nutella", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.Nutella();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche Deslactosada con Coco", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.LecheDeslactosada();
            page.Coco();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(48)
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Manzana y Frappé de Leche con extras")
    @Story("Combos")
    void comprarCrepas2FrappeMC() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Fresa y extra Manzana", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2);
            page.ExtraManzana();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Leche Grande con múltiples extras", () -> {
            page.clickFrappeLeche();
            page.personalizar();
            page.Grande();
            page.MokaCaramelo();
            page.Coco();
            page.CremaIrlandesa();
            page.EsenciaVainilla();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(49)
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso y Frappé con extras")
    @Story("Combos")
    void comprarCrepas2FrappeQ() {
        TestSteps.run("Añadir Crepa Dulce 2 Ing. con Queso Philadelphia", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.QuesoPhiladelphia();
            page.ExtraQuesoPhiladelphia();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Frappé de Agua con Menta y Coco", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.EsenciaMenta();
            page.Coco();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(50)
    @DisplayName("Combo Frappé con Menta y Crepa Dulce 2 Ing. Zarzamora")
    @Story("Combos")
    void comprarCrepas2FrappeZA() {
        TestSteps.run("Añadir Frappé Grande de Sandía con Menta", () -> {
            page.clickFrappeAgua();
            page.personalizar();
            page.Grande();
            page.SandiaPelonada();
            page.EsenciaMenta();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Regresar y añadir Crepa Dulce 2 Ing. con extra Zarzamora", () -> {
            page.clickCrepaDulce2();
            page.personalizar();
            page.ExtraMermeladaZarzamora();
            page.Siguiente();
            page.agregarCarrito();
        }, driver);

        TestSteps.run("Validar Carrito Final", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }
}
