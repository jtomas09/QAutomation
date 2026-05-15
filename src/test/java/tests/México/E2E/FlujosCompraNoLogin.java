package tests.México.E2E;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import base.BaseTest;
import config.DriverFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import pages.alimentos.SelectorsAlimentos;
import pages.carritoCompras.SelectorsCarrito;
import pages.checkOut.SelectorsCheckOut;
import pages.homeCartelera.SelectorsHome;
import pages.mapaAsientos.SelectorsMapaAsientos;
import pages.seleccionCines.SelectorsCines;
import utils.TestSteps;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Pruebas de no afectación México")

public class FlujosCompraNoLogin extends BaseTest {

    private SelectorsCines        cines;
    private SelectorsHome         home;
    private SelectorsMapaAsientos mapa;
    private SelectorsAlimentos    alimentos;
    private SelectorsCarrito      carrito;
    private SelectorsCheckOut     checkout;

    @BeforeAll
    void configurarPais() {
        driver = DriverFactory.getDriver();
        new SelectorsHome(driver).cambiarPaisMexico();
    }

    @BeforeEach
    void setUp() {
        cines     = new SelectorsCines(driver);
        home      = new SelectorsHome(driver);
        mapa      = new SelectorsMapaAsientos(driver);
        alimentos = new SelectorsAlimentos(driver);
        carrito   = new SelectorsCarrito(driver);
        checkout  = new SelectorsCheckOut(driver);
    }

    @Test
    @Order(1)
    @DisplayName("Compra ticket - Tradicional - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraTicketTradicional() {
        TestSteps.run("Compra de boleto en cine tradicional", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineTradicional();
            cines.aplicarSeleccionCine();
            cines.validarTabCineTradicional();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(2)
    @DisplayName("Compra mix - Tradicional - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraMixTradicional() {
        TestSteps.run("Compra de boleto y alimento en cine tradicional", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineTradicional();
            cines.aplicarSeleccionCine();
            cines.validarTabCineTradicional();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(3)
    @DisplayName("Compra de alimento - Tradicional - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraAlimentoTradicional() {
        TestSteps.run("Compra de alimento en cine tradicional", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineTradicional();
            cines.aplicarSeleccionCine();
            cines.validarTabCineTradicional();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(4)
    @DisplayName("Compra ticket - Atmósfera - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraTicketAtmosfera() {
        TestSteps.run("Compra de boleto en cine Atmósfera", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineAtmosfera();
            cines.seleccionarCineAtmosfera();
            cines.aplicarSeleccionCine();
            cines.validarTabCineAtmosfera();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(5)
    @DisplayName("Compra Mix - Atmósfera - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraMixAtmosfera() {
        TestSteps.run("Flujo de compra mix en cine Atmósfera", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineAtmosfera();
            cines.seleccionarCineAtmosfera();
            cines.aplicarSeleccionCine();
            cines.validarTabCineAtmosfera();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(6)
    @DisplayName("Compra alimento - Atmósfera - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraAlimentoAtmosfera() {
        TestSteps.run("Comprar alimento en cine Atmósfera", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineAtmosfera();
            cines.seleccionarCineAtmosfera();
            cines.aplicarSeleccionCine();
            cines.validarTabCineAtmosfera();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(7)
    @DisplayName("Compra ticket - VIP - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraTicketVIP() {
        TestSteps.run("Compra de boleto en cine VIP", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineVIP();
            cines.clickCineVIP();
            cines.aplicarSeleccionCine();
            cines.validarTabCineVIP();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(8)
    @DisplayName("Compra mix - VIP - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraMixVIP() {
        TestSteps.run("Compra mix en cine VIP", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineVIP();
            cines.clickCineVIP();
            cines.aplicarSeleccionCine();
            cines.validarTabCineVIP();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarrito();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(9)
    @DisplayName("Compra de alimento - VIP - Sin sesión")
    @Story("Flujos de compra sin sesión iniciada - México")
    void compraAlimentoVIP() {
        TestSteps.run("Compra de alimento en cine VIP", () -> {
            cines.abrirSelectorCines();
            cines.buscarCineVIP();
            cines.clickCineVIP();
            cines.aplicarSeleccionCine();
            cines.validarTabCineVIP();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            alimentos.vincularOrdenVIPSinSesion();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


}
