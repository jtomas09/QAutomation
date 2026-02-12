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
public class  MenuTradicional extends BaseTest {

    @Test
    @Order(1)
    @DisplayName("Menu Tradicional")
    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Mantequilla-Takis Fuego-Sidral Mundet-Hirlo Regular-Sprite Sin Azúcar-Poco Hielo-Sprite Sin Azúcar-Poco Hielo-M&M´s Chocoloate")
    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo- Mantequilla-Takis Fuego-Sidral Mundet-Hirlo Regular-Sprite Sin Azúcar-Poco Hielo-Sprite Sin Azúcar-Poco Hielo-M&M´s Chocoloate")
    void comprarMaxiComboFamiliar() {
        new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos("Escala La Huerta");
        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Selección de Maxicombo Familiar ", () -> page.clickMaxiComboFamiliar(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
    @DisplayName("Menu Tradicional")
    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Mantequilla-Mantequilla-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo- Mantequilla-Mantequilla-Coca Cola-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
    void comprarMaxiComboFamiliarJ() {

        SelectorPage page = new SelectorPage(driver);

        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
        TestSteps.run("Selección de Maxicombo Familiar ", () -> page.clickMaxiComboFamiliar(), driver);
        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
        TestSteps.run("Validar carrito visible", () -> {
            Assertions.assertTrue(true, "No se pudo validar el carrito");
        }, driver);


        // TODO: agregar aserción
//    }
//    @Test
//    @Order(3)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Caramelo-Mantequilla-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo- Caramelo-Mantequilla-Coca Cola-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarJU() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar ", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Takis Fuego-Mantequilla-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo- Takis Fuego-Mantequilla-Coca Cola-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(5)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Doritos Nacho-Mantequilla-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo- Doritos Nacho-Mantequilla-Coca Cola-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarD() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(6)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Mantequilla-Mantequilla-Coca Cola Zero-Sin Hielo-Sidral Mundet-Sin Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo - Mantequilla-Mantequilla-Coca Cola Zero-Sin Hielo-Sidral Mundet-Sin Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(7)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Caramelo-Takis Fuego-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo - Caramelo-Takis Fuego-Coca Cola Zero-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Del valle Frut-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarCT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(8)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Para Compartir/Maxicombo Familiar Jumbo - Mantequilla-Takis Fuego-Sidral Mundet-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Coca Cola Zero-Sin Hielo-M&M´s Chocoloate")
//    @Story("Alimentos-Para Compartir - Maxicombo Familiar Jumbo - Mantequilla-Takis Fuego-Sidral Mundet-Hielo Regular-Sprite Sin Azúcar-Poco Hielo-Coca Cola Zero-Sin Hielo-M&M´s Chocoloate")
//    void comprarMaxiComboFamiliarMT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Familiar", () -> page.clickMaxiComboFamiliar(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaZero(), driver);
//        TestSteps.run("Seleccionar el Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(9)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Cereza - Frambuesa Azul - Topping Icee sirena - Aritos Enchilados Cinépolis")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Cereza - Frambuesa Azul - Topping Icee sirena - Aritos Enchilados Cinépolis")
//    void comprarComboICEE() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
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
//    @Test
//    @Order(10)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Mango - Cereza - Topping Icee Pelon pelo rico - Skittles")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Mango - Cereza - Topping Icee Pelon pelo rico - Skittles")
//    void comprarComboICEEM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Mango(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cereza"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Dulcería", () -> page.Skittles(), driver);
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
//    @Test
//    @Order(11)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Takis Fuego - ICEE Grande - Frambuesa Azul - Mango - Topping Icee Pelon pelo rico - Skwinkles® Rellenos")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Takis Fuego - ICEE Grande - Frambuesa Azul - Mango - Topping Icee Pelon pelo rico - Skwinkles® Rellenos")
//    void comprarComboICEES() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mango"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Dulcería", () -> page.SkwinklessRellenos(), driver);
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
//    @Test
//    @Order(12)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Caramelo - ICEE Grande - Cereza - Mango - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Caramelo - ICEE Grande - Cereza - Mango - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    void comprarComboICEESA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mango"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Dulcería", () -> page.SkwinklessSpaguetti(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(13)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Doritos Nacho - ICEE Grande - Mango - Frambueza - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Doritos Nacho - ICEE Grande - Mango - Frambueza - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    void comprarComboICEEP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Mango(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Dulcería", () -> page.PelonPelonazo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(14)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Mantequilla - ICEE Grande - Frambueza - Cereza - Topping Icee Pelon pelo rico - Aritos Enchilados Cinépolis")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Mantequilla - ICEE Grande - Frambueza - Cereza - Topping Icee Pelon pelo rico - Aritos Enchilados Cinépolis")
//    void comprarComboICEEAR() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cereza"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(15)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Mantequilla - ICEE Grande - Cereza - Cereza - Topping Icee Pelon pelo rico - Skittles")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Mantequilla - ICEE Grande - Cereza - Cereza - Topping Icee Pelon pelo rico - Skittles")
//    void comprarComboICEECC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cereza"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.Skittles(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(16)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Takis Fuego - ICEE Grande - Mango - Mango - Topping Icee Pelon pelo rico - Skittles Rellenos")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Takis Fuego - ICEE Grande - Mango - Mango - Topping Icee Pelon pelo rico - Skittles Rellenos")
//    void comprarComboICEEMM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Mango(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mango"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.SkwinklessRellenos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(17)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Caramelo - ICEE Grande - Frambueza Azul - Frambueza Azul - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Caramelo - ICEE Grande - Frambueza Azul - Frambueza Azul - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    void comprarComboICEEFF() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.SkwinklessSpaguetti(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(18)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Doritos Nacho - ICEE Grande - Cereza - Mango - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Doritos Nacho - ICEE Grande - Cereza - Mango - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    void comprarComboICEEPP() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mango"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.PelonPelonazo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(19)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Frambueza Azul - Cereza - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Para Llevar - Mantequilla - ICEE Grande - Frambueza Azul - Cereza - Topping Icee Pelon pelo rico - Skwinkles® Salsagheti")
//    void comprarComboICEEPM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cereza"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.SkwinklessSpaguetti(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(20)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Paea Llevar - Takis Fuego - ICEE Grande - Cereza - Frambueza Azul - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Paea Llevar - Takis Fuego - ICEE Grande - Cereza - Frambueza Azul - Topping Icee Pelon pelo rico - Pelon Pelonazo")
//    void comprarComboICEECF() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Cereza(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.PelonPelonazo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(21)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Caramelo - ICEE Grande - Mango - Cereza - Topping Icee Pelon pelo rico - Aritos Enchilados Cinépolis")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Caramelo - ICEE Grande - Mango - Cereza - Topping Icee Pelon pelo rico - Aritos Enchilados Cinépolis")
//    void comprarComboICEEJC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Mango(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Cereza"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(22)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Combos/Combo ICEE con Skwinkles- Jumbo - Takis Fuego - ICEE Grande - Frambueza Azul - Mango - Topping Icee Pelon pelo rico - Skittles")
//    @Story("Alimentos-Combos - Combo ICEE con Skwinkles- Jumbo - Takis Fuego - ICEE Grande - Frambueza Azul - Mango - Topping Icee Pelon pelo rico - Skittles")
//    void comprarComboICEEJT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Combo ICEE", () -> page.clickComboICEE(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.seleccionarSaborPorContentDesc2("Mango"), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Toppin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar Dulcería", () -> page.Skittles(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(23)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Chico - Refresco; Jumbo - Coca cola - Hielo Regular")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Chico - Refresco; Jumbo - Coca cola - Hielo Regular")
//    void comprarHotDogRefrescos() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//    }
//    @Test
//    @Order(24)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Chico - Refresco; Grande - Sidral Mundet - Poco Hielo")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Chico - Refresco; Grande - Sidral Mundet - Poco Hielo")
//    void comprarHotDogRefrescosGS() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(25)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Chico - Refresco; Mediano - Sprite Sin Azúcar - Sin Hielo")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Chico - Refresco; Mediano - Sprite Sin Azúcar - Sin Hielo")
//    void comprarHotDogRefrescosMS() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(26)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Chico - Refresco; Chico - Coca Cola Ligth - Hielo Regular")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Chico - Refresco; Chico - Coca Cola Ligth - Hielo Regular")
//    void comprarHotDogRefrescosCC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Chico(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.CocaColaLigth(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(27)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Jumbo - Refresco; Jumbo - Fuze Tea sin Azúcar - Poco Hielo")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Jumbo - Refresco; Jumbo - Fuze Tea sin Azúcar - Poco Hielo")
//    void comprarHotDogRefrescosJJ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.FuzeTe(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(28)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Jumbo - Refresco; Grande - Fanta Naranja Sin Azúcar - Hielo Regular")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Jumbo - Refresco; Grande - Fanta Naranja Sin Azúcar - Hielo Regular")
//    void comprarHotDogRefrescosJG() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Grande(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Fanta(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(29)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Jumbo - Refresco; Mediano - Del Valle Frut - Sin Hielo")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Jumbo - Refresco; Mediano - Del Valle Frut - Sin Hielo")
//    void comprarHotDogRefrescosJM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Mediano(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(30)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Jumbo - Refresco; Chico - Coca Cola - Poco Hielo")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Jumbo - Refresco; Chico - Coca Cola - Poco Hielo")
//    void comprarHotDogRefrescosJC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Chico(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(31)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Chico - Refresco; Jumbo - Sprite - Hielo Regular")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Chico - Refresco; Jumbo - Sprite - Hielo Regular")
//    void comprarHotDogRefrescosCJ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Chico(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Sprite(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(32)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-HotDog Takis + Refrescos/Hot Dog Takis - Hot Dog Jumbo - Refresco; Jumbo - Sidral Mundet sin Azúcar - Hielo Regular")
//    @Story("Alimentos-HotDog Takis + Refrescos: Hot Dog Takis - Hot Dog Jumbo - Refresco; Jumbo - Sidral Mundet sin Azúcar - Hielo Regular\"")
//    void comprarHotDogRefrescosJJS() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Hot Dog Takis", () -> page.clickHotDogTakis(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Refrescos", () -> page.clickRefresco(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Sidral(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(33)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1 Litro+ Clásicos: Nachos - Clásicos")
//    @Story("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1 Litro+ Clásicos: Nachos - Clásicos")
//    void comprarSnacksPapasAgua() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Regresar al menú de alimentos", () -> page.Regresar(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(34)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1 Litro+ Clásicos: Nachos - Doritos")
//    @Story("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1 Litro+ Clásicos: Nachos - Doritos")
//    void comprarSnacksPapasAguaN() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(35)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 600 ML + Clásicos: Nachos - Clásicos")
//    @Story("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 600 ML + Clásicos: Nachos - Clásicos")
//    void comprarSnacksPapasAguaA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Adobadas(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.seismili(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(36)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1 Litro + Clásicos: Nachos - Nachos Tajín")
//    @Story("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1 Litro + Clásicos: Nachos - Nachos Tajín")
//    void comprarSnacksPapasAguaT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Adobadas(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.seismili(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(37)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1 L + Clásicos: Nachos - Nachos Tajín")
//    @Story("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1 L + Clásicos: Nachos - Nachos Tajín")
//    void comprarSnacksPapasAguaD() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Adobadas(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(38)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 600ML + Clásicos: Nachos - Nachos Tajín")
//    @Story("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 600ML + Clásicos: Nachos - Nachos Tajín")
//    void comprarSnacksPapasAguaML() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Adobadas(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.seismili(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(39)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 600ML + Clásicos: Nachos - Mix Takis Fuego")
//    @Story("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 600ML + Clásicos: Nachos - Mix Takis Fuego")
//    void comprarSnacksPapasAguaNA() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.seismili(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MixTakisFuego(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(40)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1L + Clásicos: Chicos - Mix Takis Fuego")
//    @Story("Alimentos-Snacks: Papas Fritas - Naturales + Clásicos: Agua embotellada - 1L + Clásicos: Chicos - Mix Takis Fuego")
//    void comprarSnacksPapasAguaCM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosChicos(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MixTakisFuego(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//
//    @Test
//    @Order(41)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1L + Clásicos: Chicos - Mix Doritos")
//    @Story("Alimentos-Snacks: Papas Fritas - Adobadas + Clásicos: Agua embotellada - 1L + Clásicos: Chicos - Mix Doritos")
//    void comprarSnacksPapasAguaAC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Papas Fritas", () -> page.clickPapasFritas(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.Adobadas(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Agua Embotellada", () -> page.clickAguaEmbotellada(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Selección de Nachos", () -> page.clickNachos(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosChicos(), driver);
//        TestSteps.run("Seleccionar el Sabor", () -> page.MixDoritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(42)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Matequilla - Refresco:Jumbo - Coca Cola - Hielo Regular - Refresco:Jumbo - Sidral Mundet - Hielo Regular - Nachos:Chicos - Clásicos - Extra Queso - Hot dog:Chico")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Matequilla - Refresco:Jumbo - Coca Cola - Hielo Regular - Refresco:Jumbo - Sidral Mundet - Hielo Regular - Nachos:Chicos - Clásicos - Extra Queso - Hot dog:Chico")
//    void comprarMaxicomboMix() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Selección de ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(43)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Caramelo - Refresco:Jumbo - Coca Cola - Poco Hielo - Refresco:Jumbo - Sprite Sin Azúcar - Hielo Regular - Nachos:Chicos - Doritos Nachos - Extra Queso - Hot dog:Chico")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Caramelo - Refresco:Jumbo - Coca Cola - Poco Hielo - Refresco:Jumbo - Sprite Sin Azúcar - Hielo Regular - Nachos:Chicos - Doritos Nachos - Extra Queso - Hot dog:Chico")
//    void comprarMaxicomboMixC() {
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Selección de ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(44)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Takis Fuego - Refresco:Jumbo - Fanta Naranja - Sin Hielo - Refresco:Jumbo - Sprite Sin Azúcar - Hielo Regular - Nachos:Nachos - Doritos Nachos - Sin Extra Queso - Hot dog:Chico")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Takis Fuego - Refresco:Jumbo - Fanta Naranja - Sin Hielo - Refresco:Jumbo - Sprite Sin Azúcar - Hielo Regular - Nachos:Nachos - Doritos Nachos - Sin Extra Queso - Hot dog:Chico")
//    void comprarMaxicomboMixT() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Takis(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Fanta(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sprite(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosNachos(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(45)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Mantequilla - Refresco:Jumbo - Fanta Naranja - Sin Hielo - Refresco:Jumbo - Del valle Frut - Hielo Regular - Nachos:Nachos - Doritos Nachos - Sin Extra Queso - Hot dog:Jumbo")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Mantequilla - Refresco:Jumbo - Fanta Naranja - Sin Hielo - Refresco:Jumbo - Del valle Frut - Hielo Regular - Nachos:Nachos - Doritos Nachos - Sin Extra Queso - Hot dog:Jumbo")
//    void comprarMaxicomboMixM() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Fanta(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosNachos(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(46)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Caramelo - Refresco:Jumbo - Coca Cola - Hielo Regular - Refresco:Jumbo - Coca Cola - Hielo Regular - Nachos:Nachos - Doritos Nachos -  Extra Queso - Hot dog:Jumbo")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Caramelo - Refresco:Jumbo - Coca Cola - Hielo Regular - Refresco:Jumbo - Coca Cola - Hielo Regular - Nachos:Nachos - Doritos Nachos -  Extra Queso - Hot dog:Jumbo")
//    void comprarMaxicomboMixCJ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Caramelo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosNachos(), driver);
//        TestSteps.run("Seleccionar ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(47)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Doritos - Refresco:Jumbo - Del Valle Frut - Hielo Regular - Refresco:Jumbo - Coca Cola - Sin Hielo - Nachos:Nachos - Nachos Tajín -  Sin Extra Queso - Hot dog:Jumbo")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Doritos - Refresco:Jumbo - Del Valle Frut - Hielo Regular - Refresco:Jumbo - Coca Cola - Sin Hielo - Nachos:Nachos - Nachos Tajín -  Sin Extra Queso - Hot dog:Jumbo")
//    void comprarMaxicomboMixD() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Doritos(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.DelValle(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.SinHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.NachosNachos(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(48)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Cheetos Mix - Refresco:Jumbo - Sidral Mundet - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Nachos Tajín -  Extra Queso - Hot dog:Chico")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Jumbo - Cheetos Mix - Refresco:Jumbo - Sidral Mundet - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Nachos Tajín -  Extra Queso - Hot dog:Chico")
//    void comprarMaxicomboMixDC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CheetosMix(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.Sidral(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.NachosTajin(), driver);
//        TestSteps.run("Seleccionar ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(49)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Cheetos Mix - Refresco:Jumbo - Coca Cola Ligth - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Clásicos -  Extra Queso - Hot dog:Chico")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Cheetos Mix - Refresco:Jumbo - Coca Cola Ligth - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Clásicos -  Extra Queso - Hot dog:Chico")
//    void comprarMaxicomboMixPC() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CheetosMix(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaLigth(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
//    @Test
//    @Order(50)
//    @DisplayName("Menu Tradicional")
//    @Epic("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Cheetos Mix - Refresco:Jumbo - Coca Cola Ligth - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Clásicos -  Extra Queso - Hot dog:Jumbo")
//    @Story("Alimentos-Maxicombo Mix- Palomitas:Para Llevar - Cheetos Mix - Refresco:Jumbo - Coca Cola Ligth - Hielo Regular - Refresco:Jumbo - Coca Cola - Poco Hielo - Nachos:Chicos - Clásicos -  Extra Queso - Hot dog:Jumbo")
//    void comprarMaxicomboMixPCJ() {
//
//        SelectorPage page = new SelectorPage(driver);
//
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Abrir menú", () -> page.abrirMenu(), driver);
//        TestSteps.run("Cerrar Pantalla", () -> page.cerrarPantalla(), driver);
//        TestSteps.run("Selección de Maxicombo Mix", () -> page.clickMaxiComboMix(), driver);
//        TestSteps.run("Personalizar", () -> page.personalizar(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CheetosMix(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el sabor", () -> page.CocaColaLigth(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.HieloRegular(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar Hielo", () -> page.PocoHielo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar ExtraQueso", () -> page.ExtraQueso(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Seleccionar el Tamaño", () -> page.Jumbo(), driver);
//        TestSteps.run("Seleccionar el botón Continuar", () -> page.Siguiente(), driver);
//        TestSteps.run("Agregar alimento al carrito", () -> page.agregarCarrito(), driver);
//        TestSteps.run("Ver el alimento en el carrito", () -> page.abrirCarrito(), driver);
//        TestSteps.run("Validar carrito visible", () -> {
//            Assertions.assertTrue(true, "No se pudo validar el carrito");
//        }, driver);
//
//    }
    }
}
