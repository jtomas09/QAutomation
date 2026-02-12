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
public class MenuVIP extends BaseTest{


    @Test
    @Order(1)
    @DisplayName("Menu VIP")
    @Epic("Alimentos-Palomitas/ParaLlevar-Mantequilla")
    @Story("Palomitas Clásicas - ParaLlevar-Mantequilla")
    void comprarPalomitasClasicasMantequilla() {
        new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("Espacio Las Américas");
        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Selección de Palomitas ", () -> page.clickPalomitas(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Selección de Tamaño", () -> page.PLlevar(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible (assert)", () -> {
            // Cambia esto a tu validación real
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);


        // TODO: agregar aserción
    }


@Test
@Order(2)
@DisplayName("Menu VIP")
@Epic("Alimentos-Palomitas/Jumbo-Caramelo")
@Story("Palomitas Clásicas - Jumbo-Caramelo")
void comprarPalomitasClasicas2() {

    SelectorPage page = new SelectorPage(driver);

    TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
    TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
    TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
    TestSteps.run("Selección de Palomitas ", () -> page.clickPalomitas(), driver);
    TestSteps.run("Personalizar", () -> page.personalizar(), driver);
    TestSteps.run("Selección de Tamaño", () -> page.Jumbo(), driver);
    TestSteps.run("Selección de Sabor", () -> page.Caramelo(), driver);
    TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
    TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
    TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
    TestSteps.run("Validar carrito visible (assert)", () -> {
        // Cambia esto a tu validación real
        Assertions.assertTrue(true, "No se pudo validar el carrito");
    }, driver);

    // TODO: agregar aserción
}
//    @Test
//    @Order(3)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas/Grandes-Takis Fuego")
//    @Story("Palomitas Clásicas - Grandes-Takis Fuego")
//    void comprarPalomitasClasicasGTakis() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas ", () -> page.clickPalomitas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Tamaño", () -> page.Grandes(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(4)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas/Medianas-Doritos Nachos")
//    @Story("Palomitas Clásicas - Medianas-Doritos Nachos")
//    void comprarPalomitasClasicasMDoritos() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas ", () -> page.clickPalomitas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Tamaño", () -> page.Medianas(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(5)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas/Chicas-CheetosMix")
//    @Story("Palomitas Clásicas - Chicas-CheetosMix")
//    void comprarPalomitasClasicasCCheetos() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas ", () -> page.clickPalomitas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Tamaño", () -> page.Chicas(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Cheetos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(6)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Nachos/Grandes-Clásicos-ExtraQueso")
//    @Story("Nachos - Grandes-Clásicos")
//    void comprarNachos() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Tamaño", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(7)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Nachos/Chicos-Doritos Nachos")
//    @Story("Nachos - Chicos-Doritos Nachos")
//    void comprarNachosChicos() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Tamaño", () -> page.Chicas2(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.SinQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(8)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Quesadilla-Guacamole")
//    @Story("Snacks - Quesadilla-Guacamole")
//    void comprarQuesadilla() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Quesadilla", () -> page.clickQuesadilla(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(9)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Quesadilla-Res")
//    @Story("Snacks - Quesadilla-Res")
//    void comprarQuesadillaR() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Quesadilla", () -> page.clickQuesadilla(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Res(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(10)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Quesadilla-Boneless")
//    @Story("Snacks - Quesadilla-Boneless")
//    void comprarQuesadillaB() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Quesadilla", () -> page.clickQuesadilla(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de Sabor", () -> page.Boneless(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(11)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/PlatoSnackBoneless-Papascrisscut")
//    @Story("Snacks - PlatoSnackBoneless-Papascrisscut")
//    void comprarBoneless() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de PlatoSnackBoneless", () -> page.clickSnackBoneless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(12)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/MiniDogsVIP-5piezas")
//    @Story("Snacks - MiniDogsVIP-5piezas")
//    void comprarMiniDogs() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de MiniDogsVIP", () -> page.clickMiniDogs(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(13)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Papas Crisscut-3oz")
//    @Story("Snacks - Papas Crisscut-3oz")
//    void comprarPapasCrisscut() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Crisscut", () -> page.clickPapasCrisscut(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(14)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Papas Crisscut-6oz")
//    @Story("Snacks - Papas Crisscut-6oz")
//    void comprarPapasCrisscut6z() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Crisscut", () -> page.clickPapasCrisscut(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.seisoz(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(15)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Nachos Premium-Nachos Premium")
//    @Story("Snacks - Nachos Premium-Nachos Premium")
//    void comprarNachosPremium() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachosPremium(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(16)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Nachos Premium-Nachos Boneless")
//    @Story("Snacks - Nachos Premium-Nachos Boneless")
//    void comprarNachosPremiumB() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachosPremium(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.nachosBoneless(), driver);
//        TestSteps.run("Seleccionar los nachos", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(17)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Nachos Premium-Nachos brisket de res")
//    @Story("Snacks - Nachos Premium-Nachos brisket de res")
//    void comprarNachosPremiumBr() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachosPremium(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.nachosBrisquet(), driver);
//        TestSteps.run("Seleccionar los nachos", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(18)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Hot Dog-Jumbo")
//    @Story("Snacks - Hot Dog-Jumbo")
//    void comprarHotdogJumbo() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog", () -> page.clickHotDog(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(19)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Hot Dog-chico")
//    @Story("Snacks - Hot Dog-chico")
//    void comprarHotdogChico() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog", () -> page.clickHotDog(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Chico(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(20)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Hot Dog Guacamole-Jumbo")
//    @Story("Snacks - Hot Dog Guacamole-Jumbo")
//    void comprarHotdogGuacamole() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog", () -> page.clickHotDogGuacamole(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(21)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Hot Dog Guacamole-Guacamole")
//    @Story("Snacks - Hot Dog Guacamole-Guacamole")
//    void comprarHotdogGuacamole2() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog", () -> page.clickHotDogGuacamole(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Guacamole(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(22)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/ParaLlevar-Mantequilla")
//    @Story("Snacks - Palomitas Skwinkles-ParaLlevar-Mantequilla")
//    void comprarPalomitasSkwinkless() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(23)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/ParaLlevar-TakisFuego")
//    @Story("Snacks - Palomitas Skwinkles-ParaLlevar-TakisFuego")
//    void comprarPalomitasSkwinklessT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(24)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/ParaLlevar-Cheetos Mix")
//    @Story("Snacks - Palomitas Skwinkles-ParaLlevar-Cheetos Mix")
//    void comprarPalomitasSkwinklessC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cheetos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(25)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/Jumbo-Caramelo")
//    @Story("Snacks - Palomitas Skwinkles-Jumbo-Caramelo")
//    void comprarPalomitasSkwinklessCa() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(26)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/Jumbo-DoritosNacho")
//    @Story("Snacks - Palomitas Skwinkles-Jumbo-DoritosNacho")
//    void comprarPalomitasSkwinklessD() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(27)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/Grandes-Mantequilla")
//    @Story("Snacks - Palomitas Skwinkles-Grandes-Mantequilla")
//    void comprarPalomitasSkwinklessG() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(28)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Palomitas Skwinkles/Grandes-Cheetos MIX")
//    @Story("Snacks - Palomitas Skwinkles-Grandes-Cheetos MIX")
//    void comprarPalomitasSkwinklessGC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Palomitas Skwinkles", () -> page.clickPalomitasSkinkless(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Grandes(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cheetos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(29)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Clásicos/Refresco - Jumbo - CocaCola")
//    @Story("Clásicos - Refresco - Jumbo - CocaCola")
//    void comprarRefresco() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(30)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Clásicos/Refresco - Grande-Sidral mundet")
//    @Story("Clásicos - Refresco - Grande-Sidral mundet")
//    void comprarRefrescoG() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Seleccionar el hirlo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(31)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Clásicos/Refresco - Mediano-FuzeTea")
//    @Story("Clásicos - Refresco - Mediano-FuzeTea")
//    void comprarRefrescoM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.FuzeTe(), driver);
//        TestSteps.run("Seleccionar el hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(32)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Clásicos/Chico-Fantanaranja")
//    @Story("Clásicos - Refresco - Chico-Fantanaranja")
//    void comprarRefrescoC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Chico(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Fanta(), driver);
//        TestSteps.run("Seleccionar el hirlo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(33)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Texas Dog-Jumbo")
//    @Story("Snacks - Texas Dog-Jumbo")
//    void comprarTexasDog() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de HotDog Texas Dog", () -> page.clickTexasDog(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar extras", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(34)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Snacks/Texas Dog-Texas Dog")
//    @Story("Snacks - Texas Dog-Texas Dog")
//    void comprarTexasDogJ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de HotDog Texas Dog", () -> page.clickTexasDog(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.TexasDog(), driver);
//        TestSteps.run("Seleccionar extras", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(35)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Piña Colada-Poña Colada")
//    @Story("Bar - Piña Colada-Poña Colada")
//    void comprarPinaColada() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(36)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Piña Colada-amareto")
//    @Story("Bar - Piña Colada-amareto")
//    void comprarPinaColadaA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Amareto(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(37)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Piña Colada-midori")
//    @Story("Bar - Piña Colada-midori")
//    void comprarPinaColadaM() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Midori(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//
//    @Test
//    @Order(38)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Piña Colada-kahlua")
//    @Story("Bar - Piña Colada-kahlua")
//    void comprarPinaColadaK() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.kahlua(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(39)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Mojito-Clásico")
//    @Story("Bar - Mojito-Clásico")
//    void comprarMojito() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickMojito(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(40)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar/Mojito-Pepino")
//    @Story("Bar - Mojito-Pepino")
//    void comprarMojitoP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Mojito", () -> page.clickMojito(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Pepino(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(41)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Mojito-Manzana Verde")
//    @Story("Bar - Mojito-Manzana Verde")
//    void comprarMojitoM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Mojito", () -> page.clickMojito(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Manzana(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(42)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Mojito-Mojito-Cereza")
//    @Story("Bar - Mojito-Mojito-Cereza")
//    void comprarMojitoC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Mojito", () -> page.clickMojito(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            // Cambia esto a tu validación real
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(43)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Mojito-Cereza + Snacks - Texas Dog")
//    @Story("Bar-Mojito-Cereza + Snacks - Texas Dog")
//    void comprarBarSnacks() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Bebida Mojito", () -> page.clickMojito(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de HotDog Texas Dog", () -> page.clickTexasDog(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.TexasDog(), driver);
//        TestSteps.run("Seleccionar extras", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible (assert)", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(44)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Piña Colada + Snacks - Hot Dog Guacamole")
//    @Story("Bar-Piña Colada + Snacks - Hot Dog Guacamole")
//    void comprarBarSnacksP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Midori(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de HotDog Guacamole", () -> page.clickHotDogGuacamole(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar extras", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(45)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Piña Colada + Snacks - Nachos Premium")
//    @Story("Bar-Piña Colada + Snacks - Nachos Premium")
//    void comprarBarSnacksPN() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Piña Colada", () -> page.clickPinaColada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos Premium", () -> page.clickNachosPremium(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Personalizar el tamaño", () -> page.Res(), driver);
//        TestSteps.run("Seleccionar extras", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(46)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Carajillo + Snacks - Papas Crisscut")
//    @Story("Bar-Carajillo + Snacks - Papas Crisscut")
//    void comprarCarajilloPapas() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Carajillo", () -> page.clickCarajillo(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de agregar más", () -> page.Mas(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Papas Crisscut", () -> page.clickPapasCrisscut(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(47)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Cerveza + Snacks - Quesadilla")
//    @Story("Bar-Cerveza + Snacks - Quesadilla")
//    void comprarCervezaQ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Cerveza", () -> page.clickCerveza(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de agregar más", () -> page.Mas(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Quesadilla", () -> page.clickQuesadilla(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(48)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Bar-Negra Modelo + Snacks - Quesadilla")
//    @Story("Bar-Negra Modelo + Snacks - Quesadilla")
//    void comprarNegraModeloQ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Negra Modelo", () -> page.clickNegraModelo(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de agregar más", () -> page.Mas(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Quesadilla", () -> page.clickQuesadilla(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(49)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Nuevos Lanzamientos/Dippin Dots - Vainilla")
//    @Story("Nuevos Lanzamientos/Dippin Dots - Vainilla")
//    void comprarDippinDots() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Dippin Dots", () -> page.clickDippinDots(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Selección de siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
//    @Test
//    @Order(50)
//    @DisplayName("Menu VIP")
//    @Epic("Alimentos-Nuevos Lanzamientos/Dippin Dots - Algodón de Azúcar")
//    @Story("Nuevos Lanzamientos/Dippin Dots - Algodón de Azúcar")
//    void comprarDippinDotsA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Dippin Dots", () -> page.clickDippinDots(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Algodon(), driver);
//        TestSteps.run("Selección de siguiente", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//        // TODO: agregar aserción
//    }
}
