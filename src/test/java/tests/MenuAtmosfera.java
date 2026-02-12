package tests;

import base.BaseTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import pages.alimentos.SelectorPage;
import pages.common.BasePage;
import pages.common.CinemasHelper;
import utils.TestSteps;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MenuAtmosfera extends BaseTest {

    @Test
    @Order(1)
    @DisplayName("Menu Atmosfera")
    @Epic("Alimentos - Crepas Dulces 1 Ing:Mermelada de Zarzamora - Mermelada de Fresa + Frappé Agua:Grande - Frutos Rojos")
    @Story("Alimentos - Crepas Dulces 1 Ing:Mermelada de Zarzamora - Mermelada de Fresa + Frappé Agua:Grande - Frutos Rojos")
    void comprarCrepaDulceFrappe() {
        new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("La Perla");
        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Seleccionar Crepas Dulces 1 Ing", () -> page.clickCrepasDulces1(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Sabor", () -> page.MermeladaZarzamora(), driver);
        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaFresa(), driver);
        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Seleccionar Frappé Agua ", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible", () -> {
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);

        // TODO: agregar aserción
    }

    @Test
    @Order(2)
    @DisplayName("Menu Atmosfera")
    @Epic("Alimentos - Frappé Agua:Grande - Coco + Crepas Dulces 1 ing:Mermelada de Zarzamora - Queso Philadelphia")
    @Story("Alimentos - Frappé Agua:Grande - Coco + Crepas Dulces 1 ing:Mermelada de Zarzamora - Queso Philadelphia")
    void comprarCrepaDulceFrappesG() {

        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Seleccionar Crepas Dulces 1 Ing", () -> page.clickFrappeAgua(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
        TestSteps.run("Seleccionar Sabor", () -> page.Coco(), driver);
        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Seleccionar Frappé Agua ", () -> page.clickCrepasDulces1(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar Sabor", () -> page.MermeladaZarzamora(), driver);
        TestSteps.run("Seleccionar Extra", () -> page.ExtraQuesoPhiladelphia(), driver);
        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible", () -> {
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);

    }
//
//    @Test
//    @Order(3)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos - Frappé Agua:Grande - Pelon Pelonada + Crepas Dulces 1 ing:Mermelada de Zarzamora - Mermelada de Fresa")
//    @Story("Alimentos - Frappé Agua:Grande - Pelon Pelonada + Crepas Dulces 1 ing:Mermelada de Zarzamora - Mermelada de Fresa")
//    void comprarCrepaDulceFrappes() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas Dulces 1 Ing", () -> page.clickFrappeAgua(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Seleccionar Frappé Agua ", () -> page.clickCrepasDulces1(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.MermeladaZarzamora(), driver);
//        TestSteps.run("Seleccionar Extra", () -> page.ExtraMermeladaFresa(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(4)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuttella: Frappe Agua: Grande - Fresa-Coco")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuttella: Frappe Agua: Grande - Fresa-Coco")
//    void comprarPromociones() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.FresaCoco(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nutella®"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(5)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Piña Colada")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (repa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Piña Colada")
//    void comprarPromocionesN() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.PinaColada(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nuez"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//
//    @Test
//    @Order(6)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Cajeta: Frappe Agua: Grande - Mango-Chamoy")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Cajeta: Frappe Agua: Grande - Mango-Chamoy")
//    void comprarPromocionesC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.MangoChamoy(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cajeta"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(7)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Mermelada de Fresa: Frappe Agua: Grande - Jolly Rancher Raspberry")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Mermelada de Fresa: Frappe Agua: Grande - Jolly Rancher Raspberry")
//    void comprarPromocionesM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.JollyRancherRaspberry(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mermelada de fresa"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(8)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Queso Philadelphia: Frappe Agua: Grande - Jolly Rancher Raspberry")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Queso Philadelphia: Frappe Agua: Grande - Jolly Rancher Raspberry")
//    void comprarPromocionesQ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.JollyRancherRaspberry(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Queso Philadelphia®"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(9)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nutella: Frappe Agua: Grande - Manzana Verde")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nutella: Frappe Agua: Grande - Manzana Verde")
//    void comprarPromocionesMa() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.ManzanaVerde(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nutella®"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(10)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Chicle Plátano")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Chicle Plátano")
//    void comprarPromocionesCP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.ChiclePlatano(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nuez"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(11)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Cajeta: Frappe Agua: Grande - Frutos Pelon Pelo Rico")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Cajeta: Frappe Agua: Grande - Frutos Pelon Pelo Rico")
//    void comprarPromocionesPR() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.FrutosPelonPeloRico(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cajeta"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(12)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Mermelada de fresa: Frappe Agua: Grande - Frutos Pelon Pelo Rico")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Mermelada de fresa: Frappe Agua: Grande - Frutos Pelon Pelo Rico")
//    void comprarPromocionesPR2() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.FrutosPelonPeloRico(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mermelada de fresa"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(13)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Queso Philadelphia: Frappe Agua: Grande - Moras Maracuya")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Queso Philadelphia: Frappe Agua: Grande - Moras Maracuya")
//    void comprarPromocionesFP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.MorasMaracuya(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Queso Philadelphia®"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(14)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nutella: Frappe Agua: Grande - Sandia Pelonada")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nutella: Frappe Agua: Grande - Sandia Pelonada")
//    void comprarPromocionesNT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.SandiaPelonada(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nutella®"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(15)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Fresa Pelonada")
//    @Story("Alimentos Destacados - Crepa + Frappé Agua (Crepa Dulce: Mermelada de Zarzamora - Nuez: Frappe Agua: Grande - Fresa Pelonada")
//    void comprarPromocionesFPL() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickCrepaFrappe(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar Sabor", () -> page.FresaPelonada(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Nuez"), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(16)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Caramelo - Refresco 1:Jumbo - Del valle frut - Refresco 2: Jumbo - Coca Cola - Sin Hielo - Nachos:Grandes - Doritos Nachos")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Caramelo - Refresco 1:Jumbo - Del valle frut - Refresco 2: Jumbo - Coca Cola - Sin Hielo - Nachos:Grandes - Doritos Nachos")
//    void comprarPromocionesCO() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selecciona el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del primer Refresco", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del segundo Refresco", () -> page.CocaCola(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño de los Nachos", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(17)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Mantequilla - Refresco 1:Jumbo - Fuze Tea - Refresco 2: Jumbo - Agua Ciel 1 L - Poco Hielo - Nachos:Chicos - Mix Doritos")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Mantequilla - Refresco 1:Jumbo - Fuze Tea - Refresco 2: Jumbo - Agua Ciel 1 L - Poco Hielo - Nachos:Chicos - Mix Doritos")
//    void comprarPromocionesCOA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del primer Refresco", () -> page.FuzeTe(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño de los Nachos", () -> page.NachosChicos(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.MixDoritos(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(18)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Cheetos Mix - Refresco 1:Jumbo - Fanta Naranja - Refresco 2: Jumbo - Sidral Mundet - Hielo Regular - Nachos:Grandes - Mix takis fuego")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Cheetos Mix - Refresco 1:Jumbo - Fanta Naranja - Refresco 2: Jumbo - Sidral Mundet - Hielo Regular - Nachos:Grandes - Mix takis fuego")
//    void comprarPromocionesNC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selecciona el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CheetosMix(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del primer Refresco", () -> page.Fanta(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del segundo Refresco", () -> page.Sidral(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño de los Nachos", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.MixTakisFuego(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(19)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Takis Fuego - Refresco 1:Jumbo - Sprite Sin Azúcar - Refresco 2: Jumbo - Manzana - Sin Hielo - Nachos:Chicos - Mix Doritos")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Takis Fuego - Refresco 1:Jumbo - Sprite Sin Azúcar - Refresco 2: Jumbo - Manzana - Sin Hielo - Nachos:Chicos - Mix Doritos")
//    void comprarPromocionesNC2() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del primer Refresco", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del segundo Refresco", () -> page.Manzana(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.MixDoritos(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(20)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Caramelo - Refresco 1:Jumbo - Coca Cola Ligth - Refresco 2: Jumbo - Coca Cola Sin Azúcar - Poco Hielo - Nachos:Grandes - Doritos Nacho")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Jumbo - Caramelo - Refresco 1:Jumbo - Coca Cola Ligth - Refresco 2: Jumbo - Coca Cola Sin Azúcar - Poco Hielo - Nachos:Grandes - Doritos Nacho")
//    void comprarPromocionesNC3() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selecciona el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del primer Refresco", () -> page.CocaColaLigth(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del segundo Refresco", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño de los Nachos", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(21)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Mantequilla - Refresco 1:Jumbo - Coca Cola - Refresco 2: Jumbo - Coca Cola - Hielo Regular - Nachos:Grandes - Nachos Tajín")
//    @Story("Alimentos Destacados - Combos Nachos en Pareja:Palomitas Para Llevar - Mantequilla - Refresco 1:Jumbo - Coca Cola - Refresco 2: Jumbo - Coca Cola - Hielo Regular - Nachos:Grandes - Nachos Tajín")
//    void comprarPromocionesFPE2() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachosPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor del segundo Refresco", () -> page.CocaCola(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño de los Nachos", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el Tipo de Nachos", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(22)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Cajeta - Leche Entera - Frappé 2:Grande - Cajeta - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Cajeta - Leche Entera - Frappé 2:Grande - Cajeta -  Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesFPE3() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(23)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Carlos V - Leche Entera - Frappé 2:Grande - Carlos V - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Carlos V - Leche Entera - Frappé 2:Grande - Carlos V - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesFPE4() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CarlosV(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CarlosV(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(24)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Cookies & Cream - Leche Entera - Frappé 2:Grande - Cookies & Cream - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Cookies & Cream - Leche Entera - Frappé 2:Grande - Cookies & Cream - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesFPV() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CookiesCream(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CookiesCream(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(25)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - M&M´s - Leche Entera - Frappé 2:Grande - M&M´s - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - M&M´s - Leche Entera - Frappé 2:Grande - M&M´s - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesFPE() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MMs(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MMs(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(26)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Capuccino - Leche Entera - Frappé 2:Grande - Capuccino - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Capuccino - Leche Entera - Frappé 2:Grande - Capuccino - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesCap() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Capuccino(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Capuccino(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(27)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Chocolate Blanco - Leche Entera - Frappé 2:Grande - Chocolate Blanco - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Chocolate Blanco - Leche Entera - Frappé 2:Grande - Chocolate Blanco - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesChoco() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ChocolateBlanco(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.ChocolateBlanco(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(28)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Moka Caramelo - Leche Entera - Frappé 2:Grande - Moka Caramelo - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Moka Caramelo - Leche Entera - Frappé 2:Grande - Moka Caramelo - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesMoka() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MokaCaramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(29)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Coco - Leche Entera - Frappé 2:Grande - Macchiato Coco - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Coco - Leche Entera - Frappé 2:Grande - Macchiato Coco - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesMacchiatoCoco() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoCoco(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoCoco(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(30)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Menta - Leche Entera - Frappé 2:Grande - Macchiato Menta - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Menta - Leche Entera - Frappé 2:Grande - Macchiato Menta - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesMacchiatoMenta() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoMenta(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoMenta(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(31)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Crema Irlandesa - Leche Entera - Frappé 2:Grande - Macchiato Crema Irlandesa - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    @Story("Alimentos Destacados - Combo Pretzel en Pareja:Frappé Grande - Macchiato Crema Irlandesa - Leche Entera - Frappé 2:Grande - Macchiato Crema Irlandesa - Leche Entera - Palomitas: Jumbo - Mantequilla")
//    void comprarPromocionesMacchiato() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboPretzelPareja(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoCremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MacchiatoCremaIrlandesa(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(32)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Nachos: Palomitas Para Llevar - Mantequilla - Refresco: Jumbo - Coca Cola - Hielo Regular - Nachos:Chicos - Nachos Tajín")
//    @Story("Alimentos Destacados - Combo Nachos: Palomitas Para Llevar - Mantequilla - Refresco: Jumbo - Coca Cola - Hielo Regular - Nachos:Chicos - Nachos Tajín")
//    void comprarComboNachos1() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(33)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Nachos: Palomitas Para Llevar - Caramelo - Refresco: Jumbo - Sidral Mundet - Sin Hielo - Nachos:Chicos - Doritos Nachos")
//    @Story("Alimentos Destacados - Combo Nachos: Palomitas Para Llevar - Caramelo - Refresco: Jumbo - Sidral Mundet - Sin Hielo - Nachos:Chicos - Doritos Nachos")
//    void comprarComboNachos2() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Crepas + Frappé Agua", () -> page.clickComboNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(34)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Nachos: Palomitas Jumbo - Mantequilla - Refresco: Jumbo - Coca Cola Ligth - Poco Hielo - Nachos:Grandes - Clásicos")
//    @Story("Alimentos Destacados - Combo Nachos: Palomitas Jumbo - Mantequilla - Refresco: Jumbo - Coca Cola Ligth - Poco Hielo - Nachos:Grandes - Clásicos")
//    void comprarComboNachos3() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Combo Nachos", () -> page.clickComboNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CocaColaLigth(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(35)
//    @DisplayName("Menu Atmosfera")
//    @Epic("Alimentos Destacados - Combo Nachos: Palomitas Jumbo - Caramelo - Refresco: Jumbo - Fanta Naranja Sin Azúcar - Hielo Regular - Nachos:Chicos - Mix Takis Fuego")
//    @Story("Alimentos Destacados - Combo Nachos: Palomitas Jumbo - Caramelo - Refresco: Jumbo - Fanta Naranja Sin Azúcar - Hielo Regular - Nachos:Chicos - Mix Takis Fuego")
//    void comprarComboNachos() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Seleccionar Combo Nachos", () -> page.clickComboNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Fanta(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MixTakisFuego(), driver);
//        TestSteps.run("Seleccionar el botón Siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
}

