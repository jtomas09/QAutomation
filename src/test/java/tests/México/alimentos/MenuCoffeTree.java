package tests.México.alimentos;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.AlimentosPagina;
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

    // ── Bebidas Calientes ─────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Comprar Americano Grande con Coco")
    @Story("Bebidas Calientes")
    void comprarAmericano() {
        TestSteps.run("Buscar y seleccionar Americano", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar Americano", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(2)
    @DisplayName("Comprar Americano Descafeinado con Crema Irlandesa")
    @Story("Bebidas Calientes")
    void comprarAmericanoG() {
        TestSteps.run("Buscar y seleccionar Americano", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar Americano", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar café Descafeinado", () -> page.CafeDescafeinado(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(3)
    @DisplayName("Comprar Americano Mediano con Menta")
    @Story("Bebidas Calientes")
    void comprarAmericanoGM() {
        TestSteps.run("Buscar y seleccionar Americano", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar Americano", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.CafeMediano(), driver);
        TestSteps.run("Seleccionar Esencia Menta", () -> page.EsenciaMenta(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(4)
    @DisplayName("Comprar Americano Mediano con Vainilla")
    @Story("Bebidas Calientes")
    void comprarAmericanoGMV() {
        TestSteps.run("Buscar y seleccionar Americano", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar Americano", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.CafeMediano(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(5)
    @DisplayName("Comprar Moka Oscuro con Coco")
    @Story("Bebidas Calientes")
    void comprarMokaOscuro() {
        TestSteps.run("Buscar y seleccionar Moka Oscuro", () -> page.clickMokaOscuro(), driver);
        TestSteps.run("Personalizar Moka Oscuro", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(6)
    @DisplayName("Comprar Moka Oscuro Descafeinado con Leche Deslactosada")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroG() {
        TestSteps.run("Buscar y seleccionar Moka Oscuro", () -> page.buscarMokaObscuro(), driver);
        TestSteps.run("Personalizar Moka Oscuro", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar café Descafeinado", () -> page.CafeDescafeinado(), driver);
        TestSteps.run("Seleccionar Leche Deslactosada", () -> page.LecheDeslactosada(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(7)
    @DisplayName("Comprar Moka Oscuro Mediano con Leche de Almendra")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroM() {
        TestSteps.run("Buscar y seleccionar Moka Oscuro", () -> page.buscarMokaObscuro(), driver);
        TestSteps.run("Personalizar Moka Oscuro", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.CafeMediano(), driver);
        TestSteps.run("Seleccionar Leche de Almendra", () -> page.LecheAlmendra(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(8)
    @DisplayName("Comprar Moka Oscuro Mediano con Vainilla")
    @Story("Bebidas Calientes")
    void comprarMokaOscuroMD() {
        TestSteps.run("Buscar y seleccionar Moka Oscuro", () -> page.buscarMokaObscuro(), driver);
        TestSteps.run("Personalizar Moka Oscuro", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.CafeMediano(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(9)
    @DisplayName("Comprar Capuccino con Coco")
    @Story("Bebidas Calientes")
    void comprarCapuccino() {
        TestSteps.run("Buscar y seleccionar Capuccino", () -> page.buscarCapuccino(), driver);
        TestSteps.run("Personalizar Capuccino", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(10)
    @DisplayName("Comprar Té Caliente de Jamaica con Coco")
    @Story("Bebidas Calientes")
    void comprarTe() {
        TestSteps.run("Buscar y seleccionar Té Caliente", () -> page.buscarTeCaliente(), driver);
        TestSteps.run("Personalizar Té Caliente", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Jamaica", () -> page.TeJamaica(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(11)
    @DisplayName("Comprar Té Caliente Mediano con Menta")
    @Story("Bebidas Calientes")
    void comprarTeM() {
        TestSteps.run("Buscar y seleccionar Té Caliente", () -> page.buscarTeCaliente(), driver);
        TestSteps.run("Personalizar Té Caliente", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.TeMediano(), driver);
        TestSteps.run("Seleccionar Esencia Menta", () -> page.EsenciaMenta(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(12)
    @DisplayName("Comprar Chocolate con Coco")
    @Story("Bebidas Calientes")
    void comprarChocolate() {
        TestSteps.run("Buscar y seleccionar Chocolate", () -> page.buscarChocolate(), driver);
        TestSteps.run("Personalizar Chocolate", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(13)
    @DisplayName("Comprar Chocolate Mediano con Crema Irlandesa")
    @Story("Bebidas Calientes")
    void comprarChocolateM() {
        TestSteps.run("Buscar y seleccionar Chocolate", () -> page.buscarChocolate(), driver);
        TestSteps.run("Personalizar Chocolate", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.ChocolateMediano(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    // ── Postres y Helados ─────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("Comprar Pretzel")
    @Story("Postres y Helados")
    void comprarPretzel() {
        TestSteps.run("Buscar y seleccionar Pretzel", () -> page.buscarPretzel(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(15)
    @DisplayName("Comprar Cheese Cake")
    @Story("Postres y Helados")
    void comprarCheeseCake() {
        TestSteps.run("Buscar y seleccionar Cheese Cake", () -> page.buscarCheeseCake(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(16)
    @DisplayName("Comprar Cornetto")
    @Story("Postres y Helados")
    void comprarCornetto() {
        TestSteps.run("Buscar y seleccionar Cornetto", () -> page.clickCornetto(), driver);
        TestSteps.run("Personalizar Cornetto", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    // ── Dulces y Chocolates ───────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("Comprar Skwinkles Chunks")
    @Story("Dulces y Chocolates")
    void comprarSkwinkles() {
        TestSteps.run("Buscar y seleccionar Skwinkles Chunks", () -> page.clickSkwinkles(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(18)
    @DisplayName("Comprar M&M's")
    @Story("Dulces y Chocolates")
    void comprarMM() {
        TestSteps.run("Buscar y seleccionar M&M's", () -> page.clickMM(), driver);
        TestSteps.run("Personalizar M&M's", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(19)
    @DisplayName("Comprar Hershey's")
    @Story("Dulces y Chocolates")
    void comprarHersheys() {
        TestSteps.run("Buscar y seleccionar Hershey's", () -> page.clickHersheys(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(20)
    @DisplayName("Comprar Snickers")
    @Story("Dulces y Chocolates")
    void comprarSnickers() {
        TestSteps.run("Buscar y seleccionar Snickers", () -> page.clickSnickers(), driver);
        agregarAlCarritoYValidar();
    }

    // ── Crepas ────────────────────────────────────────────────────

    @Test
    @Order(21)
    @DisplayName("Comprar Crepa Dulce Premium")
    @Story("Crepas")
    void comprarCrepas() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce Premium", () -> page.clickCrepasDulces(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(22)
    @DisplayName("Comprar Crepa de Manzana Canela")
    @Story("Crepas")
    void comprarCrepasM() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce", () -> page.clickCrepasDulces(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Manzana Canela", () -> page.ManzanaCanela(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(23)
    @DisplayName("Comprar Crepa Salada Premium Hawaiana")
    @Story("Crepas")
    void comprarCrepasS() {
        TestSteps.run("Buscar y seleccionar Crepa Salada Hawaiana", () -> page.clickCrepaSalada(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(24)
    @DisplayName("Comprar Crepa Salada de Champiqueso")
    @Story("Crepas")
    void comprarCrepasSP() {
        TestSteps.run("Buscar y seleccionar Crepa Salada", () -> page.clickCrepaSalada(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Champiqueso", () -> page.Champiqueso(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    @Test
    @Order(25)
    @DisplayName("Comprar Crepa Salada Italiana con Queso Manchego")
    @Story("Crepas")
    void comprarCrepasSI() {
        TestSteps.run("Buscar y seleccionar Crepa Salada Italiana", () -> page.clickCrepaSalada(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Champiqueso con Manchego", () -> page.ChampiquesoManchego(), driver);
        TestSteps.run("Confirmar personalización", () -> page.Siguiente(), driver);
        agregarAlCarritoYValidar();
    }

    // ── Combos ────────────────────────────────────────────────────

    @Test
    @Order(26)
    @DisplayName("Combo Crepa Salada con Queso y Frappé con Coco")
    @Story("Combos")
    void comprarCrepasFrappe() {
        TestSteps.run("Buscar y seleccionar Crepa Salada", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(27)
    @DisplayName("Combo Crepa Salada de Pavo y Frappé de Sandía")
    @Story("Combos")
    void comprarCrepasFrappeM() {
        TestSteps.run("Buscar y seleccionar Crepa Salada de Jamón de Pavo", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Sandía Pelonada", () -> page.SandiaPelonada(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(28)
    @DisplayName("Combo Crepa con Tocino y Frappé con Crema Irlandesa")
    @Story("Combos")
    void comprarCrepasFrappeG() {
        TestSteps.run("Buscar y seleccionar Crepa Salada", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar extra Tocino", () -> page.Tocino(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua Grande", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(29)
    @DisplayName("Combo Crepa con Champiñón y Frappé de Mango Tajín")
    @Story("Combos")
    void comprarCrepasFrapeMA() {
        TestSteps.run("Buscar y seleccionar Crepa Salada", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Manchego", () -> page.QuesoManchego(), driver);
        TestSteps.run("Seleccionar extra Champiñón", () -> page.Champinon(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Mango Tajín", () -> page.MangoTajin(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(30)
    @DisplayName("Combo Crepa con extras y Frappé con Vainilla")
    @Story("Combos")
    void comprarCrepasFrappeS() {
        TestSteps.run("Buscar y seleccionar Crepa Salada", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Manchego", () -> page.QuesoManchego(), driver);
        TestSteps.run("Seleccionar extra Jamón de Pavo", () -> page.ExtraJamonPavo(), driver);
        TestSteps.run("Seleccionar extra Tocino", () -> page.Tocino(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Sandía Pelonada", () -> page.SandiaPelonada(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(31)
    @DisplayName("Combo Crepa con Jamón y Frappé con extras")
    @Story("Combos")
    void comprarCrepasFrappeJP() {
        TestSteps.run("Buscar y seleccionar Crepa Salada de Jamón de Pavo", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar extra Jamón de Pavo", () -> page.ExtraJamonPavo(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(32)
    @DisplayName("Combo Crepa con Tocino y Frappé de Leche con Chocolate")
    @Story("Combos")
    void comprarCrepasFrappeL() {
        TestSteps.run("Buscar y seleccionar Crepa Salada de Jamón de Pavo", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar extra Tocino", () -> page.Tocino(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Salsa de Chocolate", () -> page.SalsaChocolate(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(33)
    @DisplayName("Combo Crepa con Queso y Frappé de Leche Deslactosada")
    @Story("Combos")
    void comprarCrepasFrappeLD() {
        TestSteps.run("Buscar y seleccionar Crepa Salada con Queso Philadelphia", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.Mediano(), driver);
        TestSteps.run("Seleccionar sabor Moka Caramelo", () -> page.MokaCaramelo(), driver);
        TestSteps.run("Seleccionar Leche Deslactosada", () -> page.LecheDeslactosada(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(34)
    @DisplayName("Combo Crepa de Manchego y Frappé de Leche con Vainilla")
    @Story("Combos")
    void comprarCrepasFrapp() {
        TestSteps.run("Buscar y seleccionar Crepa Salada con Queso Manchego", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Manchego", () -> page.QuesoManchego(), driver);
        TestSteps.run("Seleccionar extra Queso Manchego", () -> page.ExtraQuesoManchego(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Grande", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar sabor Moka Caramelo", () -> page.MokaCaramelo(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(35)
    @DisplayName("Combo Crepa con Champiñón y Frappé de Leche con Coco")
    @Story("Combos")
    void comprarCrepasFrappess() {
        TestSteps.run("Buscar y seleccionar Crepa Salada con Queso Philadelphia", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar extra Champiñón", () -> page.Champinon(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Leche Deslactosada", () -> page.LecheDeslactosada(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(36)
    @DisplayName("Combo Crepa con Tocino/Queso y Frappé de Leche Mediano")
    @Story("Combos")
    void comprarCrepasFrappesC() {
        TestSteps.run("Buscar y seleccionar Crepa Salada de Jamón de Pavo", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar extra Tocino", () -> page.Tocino(), driver);
        TestSteps.run("Seleccionar extra Queso Philadelphia", () -> page.ExtraQuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Mediano", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.Mediano(), driver);
        TestSteps.run("Seleccionar sabor Chocolate Blanco", () -> page.ChocolateBlanco(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(37)
    @DisplayName("Combo Crepa de Manchego y Frappé de Leche Capuccino")
    @Story("Combos")
    void comprarCrepasFrappesOnly() {
        TestSteps.run("Buscar y seleccionar Crepa Salada con Queso Manchego", () -> page.clickCrepaSalada1(), driver);
        TestSteps.run("Personalizar Crepa Salada", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Manchego", () -> page.QuesoManchego(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Salada al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Capuccino", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(38)
    @DisplayName("Combo Crepa Dulce 2 Ing. y Frappé con Crema Irlandesa")
    @Story("Combos")
    void comprarCrepas2Frappes() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar extra Manzana", () -> page.ExtraManzana(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(39)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella y Frappé de Sandía")
    @Story("Combos")
    void comprarCrepas2Frappe() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.seleccionarSaborPorContentDesc2("Nutella®"), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua Mediano", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.Mediano(), driver);
        TestSteps.run("Seleccionar sabor Sandía Pelonada", () -> page.SandiaPelonada(), driver);
        TestSteps.run("Seleccionar Esencia Menta", () -> page.EsenciaMenta(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(40)
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Nutella y Frappé Grande")
    @Story("Combos")
    void comprarCrepas2FrappeFre() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.Nutella(), driver);
        TestSteps.run("Seleccionar sabor Mermelada de Fresa", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2), driver);
        TestSteps.run("Seleccionar extra Mermelada de Fresa", () -> page.ExtraMermeladaFresa(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua Grande", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(41)
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso y Frappé con Coco")
    @Story("Combos")
    void comprarCrepas2FrappePH() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar extra Queso Philadelphia", () -> page.ExtraQuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua con Mango Tajín", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Mango Tajín", () -> page.MangoTajin(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(42)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella/Fresa y Frappé con Vainilla")
    @Story("Combos")
    void comprarCrepas2FrappeZ() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.Nutella(), driver);
        TestSteps.run("Seleccionar extra Mermelada de Fresa", () -> page.ExtraMermeladaFresa(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua Grande", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar sabor Sandía Pelonada", () -> page.SandiaPelonada(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(43)
    @DisplayName("Combo Crepa Dulce 2 Ing. con múltiples extras y Frappé con Coco")
    @Story("Combos")
    void comprarCrepas2FrappeME() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.Nutella(), driver);
        TestSteps.run("Seleccionar sabor Mermelada de Fresa", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2), driver);
        TestSteps.run("Seleccionar extra Manzana", () -> page.ExtraManzana(), driver);
        TestSteps.run("Seleccionar extra Mermelada de Fresa", () -> page.ExtraMermeladaFresa(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(44)
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso/Nuez y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeNu() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar sabor Nuez", () -> page.seleccionarSaborPorContentDesc2("Nuez"), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche con Chocolate", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Salsa de Chocolate", () -> page.SalsaChocolate(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(45)
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Manzana y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeFr() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Mermelada de Fresa", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2), driver);
        TestSteps.run("Seleccionar extra Manzana", () -> page.ExtraManzana(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Grande con Chocolate Blanco", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar sabor Chocolate Blanco", () -> page.ChocolateBlanco(), driver);
        TestSteps.run("Seleccionar Leche Deslactosada", () -> page.LecheDeslactosada(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(46)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella/Queso y Frappé de Leche")
    @Story("Combos")
    void comprarCrepas2FrappeP() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.Nutella(), driver);
        TestSteps.run("Seleccionar sabor Queso Philadelphia", () -> page.seleccionarSaborPorContentDesc("Queso Philadelphia®", 2), driver);
        TestSteps.run("Seleccionar extra Queso Philadelphia", () -> page.ExtraQuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Mediano", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Mediano", () -> page.Mediano(), driver);
        TestSteps.run("Seleccionar sabor Moka Caramelo", () -> page.MokaCaramelo(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(47)
    @DisplayName("Combo Crepa Dulce 2 Ing. Nutella y Frappé de Leche con Coco")
    @Story("Combos")
    void comprarCrepas2FrappeNM() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Nutella", () -> page.Nutella(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Deslactosada", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Leche Deslactosada", () -> page.LecheDeslactosada(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(48)
    @DisplayName("Combo Crepa Dulce 2 Ing. Fresa/Manzana y Frappé de Leche con extras")
    @Story("Combos")
    void comprarCrepas2FrappeMC() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar sabor Mermelada de Fresa", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa", 2), driver);
        TestSteps.run("Seleccionar extra Manzana", () -> page.ExtraManzana(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Leche Grande con múltiples extras", () -> page.clickFrappeLeche(), driver);
        TestSteps.run("Personalizar Frappé de Leche", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar sabor Moka Caramelo", () -> page.MokaCaramelo(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Seleccionar Crema Irlandesa", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Seleccionar Esencia Vainilla", () -> page.EsenciaVainilla(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(49)
    @DisplayName("Combo Crepa Dulce 2 Ing. Queso y Frappé con extras")
    @Story("Combos")
    void comprarCrepas2FrappeQ() {
        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar relleno Queso Philadelphia", () -> page.QuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar extra Queso Philadelphia", () -> page.ExtraQuesoPhiladelphia(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Frappé de Agua con Menta y Coco", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Esencia Menta", () -> page.EsenciaMenta(), driver);
        TestSteps.run("Seleccionar sabor Coco", () -> page.Coco(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }

    @Test
    @Order(50)
    @DisplayName("Combo Frappé con Menta y Crepa Dulce 2 Ing. Zarzamora")
    @Story("Combos")
    void comprarCrepas2FrappeZA() {
        TestSteps.run("Buscar y seleccionar Frappé de Agua Grande", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar Frappé", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar tamaño Grande", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar sabor Sandía Pelonada", () -> page.SandiaPelonada(), driver);
        TestSteps.run("Seleccionar Esencia Menta", () -> page.EsenciaMenta(), driver);
        TestSteps.run("Confirmar personalización Frappé", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Frappé al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Buscar y seleccionar Crepa Dulce 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
        TestSteps.run("Personalizar Crepa Dulce", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar extra Mermelada de Zarzamora", () -> page.ExtraMermeladaZarzamora(), driver);
        TestSteps.run("Confirmar personalización Crepa", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar Crepa Dulce al carrito", () -> page.agregarCarrito(), driver);

        TestSteps.run("Abrir carrito y validar", () -> {
            page.abrirCarrito();
            page.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
        }, driver);
    }
}
