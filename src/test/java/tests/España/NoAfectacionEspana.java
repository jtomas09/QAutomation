package tests.España;

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
import pages.common.CinemasHelper;
import pages.seleccionCines.SelectorsCines;
import utils.TestSteps;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Pruebas de no afectación España")

public class NoAfectacionEspana extends BaseTest {

    private SelectorsCines        cines;
    private SelectorsHome         home;
    private SelectorsMapaAsientos mapa;
    private SelectorsAlimentos    alimentos;
    private SelectorsCarrito      carrito;
    private SelectorsCheckOut     checkout;


    @BeforeAll
    void configurarPais() {
        driver = DriverFactory.getDriver();
        new CinemasHelper(driver).dismissLocationPopupIfPresent();
        new SelectorsHome(driver).cambiarPaisEspaña();
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
    @DisplayName("Compra ticket - Cine Plenilunio - Sin sesión")
    @Story("Flujos de compra en cine tradicional - España")
    void compraTicketPlenilunio() {
        TestSteps.run("Compra de boleto en cine Plenilunio - Tradicional - España", () -> {
            cines.ensureCinePleniulunioSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(2)
    @DisplayName("Compra mix - Cine Plenilunio - Sin sesión")
    @Story("Flujos de compra en cine tradicional - España")
    void compraMixPlenilunio() {
        TestSteps.run("Compra de boleto y alimento en cine Plenilunio - Tradicional - España", () -> {
            cines.ensureCinePleniulunioSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(3)
    @DisplayName("Compra de alimento - Cine Plenilunio - Sin sesión")
    @Story("Flujos de compra en cine tradicional - España")
    void compraAlimentoPlenilunio() {
        TestSteps.run("Compra de alimento en cine Plenilunio - Tradicional - España", () -> {
            cines.ensureCinePleniulunioSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(4)
    @DisplayName("Compra ticket - Cine Parque Corredor - Sin sesión")
    @Story("Flujos de compra en cine premium - España")
    void compraTicketParqueCorredor() {
        TestSteps.run("Compra de boleto en cine Parque Corredor - Premium - España", () -> {
            cines.ensureCineParqueCorredorSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(5)
    @DisplayName("Compra mix - Cine Parque Corredor - Sin sesión")
    @Story("Flujos de compra en cine premium - España")
    void compraMixParqueCorredor() {
        TestSteps.run("Compra de boleto y alimento en cine Parque Corredor - Premium - España", () -> {
            cines.ensureCineParqueCorredorSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(6)
    @DisplayName("Compra de alimento - Cine Parque Corredor - Sin sesión")
    @Story("Flujos de compra en cine premium - España")
    void compraAlimentoParqueCorredor() {
        TestSteps.run("Compra de alimento en cine Parque Corredor - Premium - España", () -> {
            cines.ensureCineParqueCorredorSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(7)
    @DisplayName("Compra ticket - Cine TresAguas - Sin sesión")
    @Story("Flujos de compra en cine Premiux - España")
    void compraTicketTresAguas() {
        TestSteps.run("Compra de boleto en cine TresAguas - Premium - España", () -> {
            cines.ensureCineTresAguasSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(8)
    @DisplayName("Compra mix - Cine TresAguas - Sin sesión")
    @Story("Flujos de compra en cine Premiux - España")
    void compraMixTresAguas() {
        TestSteps.run("Compra de boleto y alimento en cine TresAguas - Premium - España", () -> {
            cines.ensureCineTresAguasSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.saltarVinculacionEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(9)
    @DisplayName("Compra de alimento (con order linking) - Cine TresAguas - Sin sesión")
    @Story("Flujos de compra en cine Premiux - España")
    void compraAlimentoPremiumTresAguas() {
        TestSteps.run("Compra de alimento con order linking en cine TresAguas - Premiux - España", () -> {
            cines.ensureCineTresAguasSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoPremium();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.validarModalOrderLinkingEspaña();
            alimentos.vincularOrdenEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(10)
    @DisplayName("Compra de alimento (sin order linking) - Cine TresAguas - Sin sesión")
    @Story("Flujos de compra en cine Premiux - España")
    void compraAlimentoTresAguas() {
        TestSteps.run("Compra de alimento sin order linking en cine TresAguas - Premiux - España", () -> {
            cines.ensureCineTresAguasSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoEstandarEspaña();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.validarSinModalOrderLinkingEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(11)
    @DisplayName("Compra ticket - Cine Plaza Norte 2 - Sin sesión")
    @Story("Flujos de compra en cine Premix - España")
    void compraTicketPlazaNorte() {
        TestSteps.run("Compra de boleto en cine Plaza Norte 2 - Premix - España", () -> {
            cines.ensureCinePlazaNorteSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(12)
    @DisplayName("Compra mix - Cine Plaza Norte 2 - Sin sesión")
    @Story("Flujos de compra en cine Premix - España")
    void compraMixPlazaNorte() {
        TestSteps.run("Compra de boleto y alimento en cine Plaza Norte 2 - Premix - España", () -> {
            cines.ensureCinePlazaNorteSeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.saltarVinculacionEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(13)
    @DisplayName("Compra de alimento (con order linking) - Cine Plaza Norte 2 - Sin sesión")
    @Story("Flujos de compra en cine Premix - España")
    void compraAlimentoPremiumPlazaNorte() {
        TestSteps.run("Compra de alimento con order linking en cine Plaza Norte 2 - Premix - España", () -> {
            cines.ensureCinePlazaNorteSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoPremium();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.validarModalOrderLinkingEspaña();
            alimentos.vincularOrdenEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(14)
    @DisplayName("Compra de alimento (sin order linking) - Cine Plaza Norte 2 - Sin sesión")
    @Story("Flujos de compra en cine Premix - España")
    void compraAlimentoPlazaNorte() {
        TestSteps.run("Compra de alimento sin order linking en cine Plaza Norte 2 - Premix - España", () -> {
            cines.ensureCinePlazaNorteSeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoEstandarEspaña();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.validarSinModalOrderLinkingEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(15)
    @DisplayName("Compra ticket - Cine Palafox Luxury - Sin sesión")
    @Story("Flujos de compra en cine Luxury - España")
    void compraTicketPalafoxLuxury() {
        TestSteps.run("Compra de boleto en cine Palafox Luxury - Luxury - España", () -> {
            cines.ensureCinePalafoxLuxurySeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.clickSaltarAlimentos();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }


    @Test
    @Order(16)
    @DisplayName("Compra mix - Cine Palafox Luxury - Sin sesión")
    @Story("Flujos de compra en cine Luxury - España")
    void compraMixPalafoxLuxury() {
        TestSteps.run("Compra de boleto y alimento en cine Palafox Luxury - Luxury - España", () -> {
            cines.ensureCinePalafoxLuxurySeleccionado();
            home.seleccionarPrimerHorarioEspaña();
            mapa.seleccionarAsientoEspana();
            mapa.clickContinuarMapaAsientos();
            mapa.agregarBoletoAdulto();
            mapa.clickContinuarSelectorBoletos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarFuncionEnCarrito();
            carrito.validarAsientoEnCarritoEspana();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.saltarVinculacionEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(17)
    @DisplayName("Compra de alimento (con order linking) - Cine Palafox Luxury- Sin sesión")
    @Story("Flujos de compra en cine Luxury - España")
    void compraAlimentoPremiumPalafoxLuxury() {
        TestSteps.run("Compra de alimento con order linking en cine Palafox Luxury - Luxury - España", () -> {
            cines.ensureCinePalafoxLuxurySeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoPremium();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            alimentos.validarModalOrderLinkingEspaña();
            alimentos.vincularOrdenEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(18)
    @DisplayName("Compra de alimento (sin order linking) - Cine Palafox Luxury - Sin sesión")
    @Story("Flujos de compra en cine Luxury - España")
    void compraAlimentoPalafoxLuxury() {
        TestSteps.run("Compra de alimento sin order linking en cine Palafox Luxury - Luxury - España", () -> {
            cines.ensureCinePalafoxLuxurySeleccionado();
            home.clickSeccionAlimentosEspaña();
            alimentos.seleccionarAlimentoEstandarEspaña();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarritoEspana();
            carrito.clickContinuarCarrito();
            checkout.validarSinModalOrderLinkingEspaña();
            checkout.llenarDatosPersonalesEspana();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }



}
