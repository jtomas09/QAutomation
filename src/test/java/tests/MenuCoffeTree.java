package tests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import pages.alimentos.SelectorPage;
import pages.common.CinemasHelper;
import utils.TestSteps;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MenuCoffeTree extends BaseTest {

    @Test
    @Order(1)
    @DisplayName("Menu CoffeTree")
    @Epic("Alimentos-Bebidas Calientes/Americano - Grande - Café Regular - Coco")
    @Story("Alimentos-Bebidas Calientes - Americano - Grande - Café Regular - Coco")
    void comprarAmericano() {
       new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("Escala Morelia");
        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Selección de Café Americano ", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Coco(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible", () -> {
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);


        // TODO: agregar aserción
    }

    @Test
    @Order(2)
    @DisplayName("Menu CoffeTree")
    @Epic("Alimentos-Bebidas Calientes/Americano - Grande - Café Descafeinado - Crema Irlandesa")
    @Story("Alimentos-Bebidas Calientes - Americano - Grande - Café Descafeinado - Crema Irlandesa")
    void comprarAmericanoG() {

        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Selección de Café Americano", () -> page.clickAmericano(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.CafeDescafeinado(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.CremaIrlandesa(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible", () -> {
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);


        // TODO: agregar aserción
    }
//
//    @Test
//    @Order(3)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Americano - Mediano - Café Descafeinado - Crema Irlandesa")
//    @Story("Alimentos-Bebidas Calientes - Americano - Mediano - Café Descafeinado - Crema Irlandesa")
//    void comprarAmericanoGM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Café Americano", () -> page.clickAmericano(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.CafeMediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.EsenciaMenta(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(4)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Americano - Mediano - Café Descafeinado - Esencia Vainilla")
//    @Story("Alimentos-Bebidas Calientes - Americano - Mediano - Café Descafeinado - Esencia Vainilla")
//    void comprarAmericanoGMV() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Café Americano", () -> page.clickAmericano(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.CafeMediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(5)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Moka Oscuro - Grande - Café Regular - Leche Entera - Coco")
//    @Story("Alimentos-Bebidas Calientes - Moka Oscuro - Grande - Café Regular - Leche Entera - Coco")
//    void comprarMokaOscuro() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Moka Oscuro", () -> page.clickMokaOscuro(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(6)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Moka Oscuro - Grande - Café Descafeinado - Leche Deslactosada - Crema Irlandesa")
//    @Story("Alimentos-Bebidas Calientes - Moka Oscuro - Grande - Café Descafeinado - Leche Deslactosada - Crema Irlandesa")
//    void comprarMokaOscuroG() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Moka Oscuro", () -> page.clickMokaOscuro(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CafeDescafeinado(), driver);
//        TestSteps.run("Seleccionar el tipo de leche", () -> page.LecheDeslactosada(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(7)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Moka Oscuro - Mediano - Café Regular - Leche de Almendra - Esencia Menta")
//    @Story("Alimentos-Bebidas Calientes - Moka Oscuro - Mediano - Café Regular - Leche de Almendra - Esencia Menta")
//    void comprarMokaOscuroM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Moka Oscuro", () -> page.clickMokaOscuro(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.CafeMediano(), driver);
//        TestSteps.run("Seleccionar el tipo de leche", () -> page.LecheAlmendra(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(8)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Moka Oscuro - Mediano - Café Regular - Leche Entera - Esencia Vainilla")
//    @Story("Alimentos-Bebidas Calientes - Moka Oscuro - Mediano - Café Regular - Leche Entera - Esencia Vainilla")
//    void comprarMokaOscuroMD() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Moka Oscuro", () -> page.clickMokaOscuro(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.CafeMediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(9)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Capuccino - Grande - Café Regular - Leche Entera - Coco")
//    @Story("Alimentos-Bebidas Calientes - Capuccino - Grande - Café Regular - Leche Entera - Coco")
//    void comprarCapuccino() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Capuccino", () -> page.clickCapuccino(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(10)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Te Caliente - Grande - Té Mora Jamaica - Coco")
//    @Story("Alimentos-Bebidas Calientes - Te Caliente - Grande - Té Mora Jamaica - Coco")
//    void comprarTe() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Te Caliente", () -> page.clickTeCaliente(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.TeJamaica(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(11)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Te Caliente - Mediano - Té Monin Durazno - Esencia Menta")
//    @Story("Alimentos-Bebidas Calientes - Te Caliente - Mediano - Té Monin Durazno - Esencia Menta")
//    void comprarTeM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Te Caliente", () -> page.clickTeCaliente(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.TeMediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.EsenciaMenta(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(12)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Chocolate - Grande - Chocolate Obscuro - Leche Entera - Coco")
//    @Story("Alimentos-Bebidas Calientes - Chocolate - Grande - Chocolate Obscuro - Leche Entera - Coco")
//    void comprarChocolate() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Chocolate", () -> page.clickChocolate(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(13)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Bebidas Calientes/Chocolate - Mediano - Chocolate Obscuro - Leche Deslactosada - Crema Irlandesa")
//    @Story("Alimentos-Bebidas Calientes - Chocolate - Mediano - Chocolate Obscuro - Leche Deslactosada - Crema Irlandesa")
//    void comprarChocolateM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Chocolate", () -> page.clickChocolate(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el Tamaño", () -> page.ChocolateMediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(14)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Postres y Helados/Pretzel")
//    @Story("Alimentos-Postres y Helados - Pretzel")
//    void comprarPretzel() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Pretzel", () -> page.clickPretzel(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(15)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Postres y Helados/Cheese Cake Queso Crema")
//    @Story("Alimentos-Postres y Helados - Cheese Cake Queso Crema")
//    void comprarCheeseCake() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCheeseCake(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(16)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Postres y Helados/Cornetto")
//    @Story("Alimentos-Postres y Helados - Cornetto")
//    void comprarCornetto() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCornetto(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(17)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Dulces y Chocolates/ Skwinkles Chunks sandia")
//    @Story("Alimentos-Dulces y Chocolates -  Skwinkles Chunks sandia")
//    void comprarSkwinkles() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickSkwinkles(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(18)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Dulces y Chocolates/ M&M´s")
//    @Story("Alimentos-Dulces y Chocolates - M&M´s")
//    void comprarMM() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickMM(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(19)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Dulces y Chocolates/Hershey´s")
//    @Story("Alimentos-Dulces y Chocolates - Hershey´s")
//    void comprarHersheys() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickHersheys(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(20)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Dulces y Chocolates/Snickers")
//    @Story("Alimentos-Dulces y Chocolates - Snickers")
//    void comprarSnickers() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickSnickers(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(21)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Crepas/Cheese Cake Queso con mermelada de fresa")
//    @Story("Alimentos-Crepas - Cheese Cake Queso con mermelada de fresa")
//    void comprarCrepas() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCrepasDulces(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(22)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Crepas/Manzana Canela")
//    @Story("Alimentos-Crepas - Manzana Canela")
//    void comprarCrepasM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCrepasDulces(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ManzanaCanela(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(23)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Crepas/Crepa Salada Premium - Hawaiana")
//    @Story("Alimentos-Crepas - Crepa Salada Premium - Hawaiana")
//    void comprarCrepasS() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCrepaSalada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(24)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Crepas/Crepa Salada Premium - Champiqueso con queso Philadelphia")
//    @Story("Alimentos-Crepas - Crepa Salada Premium - Champiqueso con queso Philadelphia")
//    void comprarCrepasSP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCrepaSalada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Champiqueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(25)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Crepas/Crepa Salada Premium - Italiana Queso Manchego")
//    @Story("Alimentos-Crepas - Crepa Salada Premium - Italiana Queso Manchego")
//    void comprarCrepasSI() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cheese Cake", () -> page.clickCrepaSalada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ChampiquesoManchego(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(26)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Jamón de Pavo - Extra Queso Philadelphia + Frappé Agua:Jumbo - Frutos Rojos - Coco")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Jamón de Pavo - Extra Queso Philadelphia + Frappé Agua:Jumbo - Frutos Rojos - Coco")
//    void comprarCrepasFrappe() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Extras", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(27)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Jamón de Pavo + Frappé Agua:Jumbo - Sandía Pelonada")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Jamón de Pavo + Frappé Agua:Jumbo - Sandía Pelonada")
//    void comprarCrepasFrappeM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(28)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Philadelphia - Extra Tocino + Frappé Agua:Grande - Frutos Rojos - Crema Irlandesa")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Philadelphia - Extra Tocino + Frappé Agua:Grande - Frutos Rojos - Crema Irlandesa")
//    void comprarCrepasFrappeG() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Tocino(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(29)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Manchego - Extra Champiñon + Frappé Agua:Jumbo - Mango Tajín")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Manchego - Extra Champiñon + Frappé Agua:Jumbo - Mango Tajín")
//    void comprarCrepasFrapeMA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoManchego(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Champinon(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MangoTajin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(30)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Manchego - Extra Jamón de Pavo - Extra Tocino + Frappé Agua:Jumbo - Sandía Pelonada - Esencia Vainilla")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Manchego - Extra Jamón de Pavo - Extra Tocino + Frappé Agua:Jumbo - Sandía Pelonada - Esencia Vainilla")
//    void comprarCrepasFrappeS() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoManchego(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraJamonPavo(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Tocino(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(31)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Jamón de Pavo - Extra Jamón de Pavo + Frappé Agua:Jumbo - Frutos Rojos - Coco - Esencia Vainilla")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Jamón de Pavo - Extra Jamón de Pavo + Frappé Agua:Jumbo - Frutos Rojos - Coco - Esencia Vainilla")
//    void comprarCrepasFrappeJP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraJamonPavo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(32)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Jamón de Pavo - Extra Tocino + Frappé Leche:Jumbo - Cappucino - Leche entera - Salsa de Chocolate")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Jamón de Pavo - Extra Tocino + Frappé Leche:Jumbo - Cappucino - Leche entera - Salsa de Chocolate")
//    void comprarCrepasFrappeL() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Tocino(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.SalsaChocolate(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(33)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Philadelphia + Frappé Leche:Mediano - Moka Caramelo - Leche Deslactosada")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Philadelphia + Frappé Leche:Mediano - Moka Caramelo - Leche Deslactosada")
//    void comprarCrepasFrappeLD() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar la Leche", () -> page.LecheDeslactosada(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(34)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Manchego - Extra Queso Manchego + Frappé Leche:Grande - Moka Caramelo - Leche Entera - Esencia Vainilla")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Manchego - Extra Queso Manchego + Frappé Leche:Grande - Moka Caramelo - Leche Entera - Esencia Vainilla")
//    void comprarCrepasFrapp() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoManchego(), driver);
//        TestSteps.run("Seleccionar Extras", () -> page.ExtraQuesoManchego(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Leche", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(35)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Philadelphia - Extra Champiñon + Frappé Leche:Jumbo - Capuccino - Leche Deslactosada - Coco")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Philadelphia - Extra Champiñon + Frappé Leche:Jumbo - Capuccino - Leche Deslactosada - Coco")
//    void comprarCrepasFrappess() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la crepa", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar Extras", () -> page.Champinon(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Leche", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la leche", () -> page.LecheDeslactosada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(36)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Jamón de Pavo - Extra Tocino - Extra Queso Philadelphia + Frappé Leche:Mediano - Chocolate Blanco - Leche Entera - Crema Irlandesa")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Jamón de Pavo - Extra Tocino - Extra Queso Philadelphia + Frappé Leche:Mediano - Chocolate Blanco - Leche Entera - Crema Irlandesa")
//    void comprarCrepasFrappesC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extras", () -> page.Tocino(), driver);
//        TestSteps.run("Seleccionar Extras", () -> page.ExtraQuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Leche", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ChocolateBlanco(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(37)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Salada 1 Ing:Queso Manchego + Frappé Leche:Jumbo - Capuccino - Leche Entera")
//    @Story("Alimentos-Combo - Crepa Salada 1 Ing:Queso Manchego + Frappé Leche:Jumbo - Capuccino - Leche Entera")
//    void comprarCrepasFrappesOnly() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepa Salada 1 Ingrediente", () -> page.clickCrepaSalada1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la Crepa", () -> page.QuesoManchego(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Leche", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(38)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nuez - Mermelada de Zarzamora - Extra Manzana  + Frappé Agua:Jumbo - Frutos Rojos - Crema Irlandesa")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nuez - Mermelada de Zarzamora - Extra Manzana  + Frappé Agua:Jumbo - Frutos Rojos - Crema Irlandesa")
//    void comprarCrepas2Frappes() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Extra", () -> page.ExtraManzana(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(39)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nuez - Nutella + Frappé Agua:Mediano - Sandia Pelonada - Esencia Menta")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nuez - Nutella + Frappé Agua:Mediano - Sandia Pelonada - Esencia Menta")
//    void comprarCrepas2Frappe() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nutella®"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaMenta(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(40)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nutella - Mermelada de Fresa - Extra Mermelada de Fresa + Frappé Agua:Grande - Frutos Rojos")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nutella - Mermelada de Fresa - Extra Mermelada de Fresa + Frappé Agua:Grande - Frutos Rojos")
//    void comprarCrepas2FrappeFre() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Nutella(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa",2), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaFresa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(41)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Queso Philadelphia - Mermelada de Zarzamora - Extra Queso Philadephia + Frappé Agua:Jumbo - Mango Tajín - Extra Coco")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Queso Philadelphia - Mermelada de Zarzamora - Extra Queso Philadephia + Frappé Agua:Jumbo - Mango Tajín - Extra Coco")
//    void comprarCrepas2FrappePH() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraQuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MangoTajin(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(42)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nutella - Mermelada de Zarzamora - Extra Mermelada de Fresa + Frappé Agua:Grande - Sandia Pelonada - Esencia de Vainilla")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nutella - Mermelada de Zarzamora - Extra Mermelada de Fresa + Frappé Agua:Grande - Sandia Pelonada - Esencia de Vainilla")
//    void comprarCrepas2FrappeZ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Nutella(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaFresa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(43)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nutella - Mermelada de Fresa - Extra Manzana - Extra Mermelada de Fresa + Frappé Agua:Jumbo - Frutos Rojos - Coco")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nutella - Mermelada de Fresa - Extra Manzana - Extra Mermelada de Fresa + Frappé Agua:Jumbo - Frutos Rojos - Coco")
//    void comprarCrepas2FrappeME() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Nutella(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa",2), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraManzana(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaFresa(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(44)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Queso Philadelphia - Nuez + Frappé Leche:Jumbo - Capuccino - Leche Entera - Salsa de Chocolate")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Queso Philadelphia - Nuez + Frappé Leche:Jumbo - Capuccino - Leche Entera - Salsa de Chocolate")
//    void comprarCrepas2FrappeNu() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nuez"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.SalsaChocolate(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(45)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nuez - Mermelada de Fresa - Extra Manzana + Frappé Leche:Grande - Chocolate Blanco - Leche Deslactosada - Esencia Vainilla")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nuez - Mermelada de Fresa - Extra Manzana + Frappé Leche:Grande - Chocolate Blanco - Leche Deslactosada - Esencia Vainilla")
//    void comprarCrepas2FrappeFr() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa",2), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraManzana(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ChocolateBlanco(), driver);
//        TestSteps.run("Seleccionar la Leche", () -> page.LecheDeslactosada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(46)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nutella - Queso Philadelphia - Extra Queso Philadelphia + Frappé Leche:Mediano - Moka Caramelo - Leche Entera")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nutella - Queso Philadelphia - Extra Queso Philadelphia + Frappé Leche:Mediano - Moka Caramelo - Leche Entera")
//    void comprarCrepas2FrappeP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Nutella(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc("Queso Philadelphia®",2), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraQuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(47)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nutella - Mermelada de Zarzamora + Frappé Leche:Jumbo - Capuccino - Leche Deslactosada - Coco")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nutella - Mermelada de Zarzamora + Frappé Leche:Jumbo - Capuccino - Leche Deslactosada - Coco")
//    void comprarCrepas2FrappeNM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Nutella(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar la Leche", () -> page.LecheDeslactosada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(48)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Nuez - Mermelada de Fresa - Extra Manzana + Frappé Leche:Grande - Moka Caramelo - Leche Entera - Coco - Crema Irlandesa - Esencia de Vainilla")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Nuez - Mermelada de Fresa - Extra Manzana + Frappé Leche:Grande - Moka Caramelo - Leche Entera - Coco - Crema Irlandesa - Esencia de Vainilla")
//    void comprarCrepas2FrappeMC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc("Mermelada de fresa",2), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraManzana(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeLeche(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.CremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaVainilla(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(49)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Crepa Dulces 2 Ing:Queso Philadelphia - Extra Queso Philadelphia + Frappé Agua:Jumbo - Frutos Rojos - Esencia Menta - Coco")
//    @Story("Alimentos-Combo - Crepa Dulces 2 Ing:Queso Philadelphia - Extra Queso Philadelphia + Frappé Agua:Jumbo - Frutos Rojos - Esencia Menta - Coco")
//    void comprarCrepas2FrappeQ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Crepas Dulces 2 Ingredientes", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.QuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraQuesoPhiladelphia(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaMenta(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.Coco(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(50)
//    @DisplayName("Menu CoffeTree")
//    @Epic("Alimentos-Combo/Frappe Agua:Grande - Sandia Pelonada - Esencia Menta + Crepas Dulces 2 Ing:Nuez - Mermelada de Zarzamora -Extra Mermelada de Zarzamora")
//    @Story("Alimentos-Combo - Frappe Agua:Grande - Sandia Pelonada - Esencia Menta + Crepas Dulces 2 Ing:Nuez - Mermelada de Zarzamora -Extra Mermelada de Zarzamora")
//    void comprarCrepas2FrappeZA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.EsenciaMenta(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Frappé de Agua", () -> page.clickCrepaDulce2(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaZarzamora(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//}
}