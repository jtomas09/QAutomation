package tests.Chile;

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
@Epic("Pruebas de no afectación Chile")

public class NoAfectacionChile extends BaseTest {

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
        new SelectorsHome(driver).cambiarPaisChile();
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
    @DisplayName("Compra ticket - Cine Los Dominicos - Sin sesión")
    @Story("Flujos de compra en cine tradicional - Chile")
    void compraTicketDominicos() {
        TestSteps.run("Compra de boleto en cine Los Dominicos - Tradicional - Chile", () -> {
            cines.ensureCineDominicosSeleccionado();
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
    @DisplayName("Compra mix - Cine Los Dominicos - Sin sesión")
    @Story("Flujos de compra en cine tradicional - Chile\")")
    void compraMixDominicos() {
        TestSteps.run("Compra de boleto y alimento en cine Los Dominicos - Tradicional - Chile", () -> {
            cines.ensureCineDominicosSeleccionado();
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
    @DisplayName("Compra de alimento - Cine Los Dominicos - Sin sesión")
    @Story("Flujos de compra en cine tradicional - Chile")
    void compraAlimentoDominicos() {
        TestSteps.run("Compra de alimento en cine Los Dominicos - Tradicional - Chile", () -> {
            cines.ensureCineDominicosSeleccionado();
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
    @DisplayName("Compra ticket - Cine La Reina - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile")
    void compraTicketLaReina() {
        TestSteps.run("Compra de boleto en cine La Reina - Atmósfera - Chile", () -> {
            cines.ensureCineLaReinaSeleccionado();
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
    @DisplayName("Compra mix - Cine La Reina - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile\")")
    void compraMixLaReina() {
        TestSteps.run("Compra de boleto y alimento en cine La Reina - Atmósfera - Chile", () -> {
            cines.ensureCineLaReinaSeleccionado();
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
    @DisplayName("Compra de alimento - Cine La Reina - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile")
    void compraAlimentoLaReina() {
        TestSteps.run("Compra de alimento en cine La Reina - Atmósfera - Chile", () -> {
            cines.ensureCineLaReinaSeleccionado();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            alimentos.vincularOrdenChile();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(7)
    @DisplayName("Compra ticket - Cine Parque Arauco - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile")
    void compraTicketParqueArauco() {
        TestSteps.run("Compra de boleto en cine Parque Arauco - Atmósfera - Chile", () -> {
            cines.ensureCineParqueAraucoSeleccionado();
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
    @DisplayName("Compra mix - Cine Parque Arauco - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile\")")
    void compraMixParqueArauco() {
        TestSteps.run("Compra de boleto y alimento en cine Parque Arauco - Atmósfera - Chile", () -> {
            cines.ensureCineParqueAraucoSeleccionado();
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
    @DisplayName("Compra de alimento - Cine Parque Arauco - Sin sesión")
    @Story("Flujos de compra en cine atmósfera - Chile")
    void compraAlimentoParqueArauco() {
        TestSteps.run("Compra de alimento en cine Parque Arauco - Atmósfera - Chile", () -> {
            cines.ensureCineParqueAraucoSeleccionado();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            alimentos.vincularOrdenChile();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

    @Test
    @Order(10)
    @DisplayName("Compra ticket - Cine Parque Arauco Premium - Sin sesión")
    @Story("Flujos de compra en cine VIP - Chile")
    void compraTicketParqueAraucoPremium() {
        TestSteps.run("Compra de boleto en cine Parque Arauco Premium - VIP - Chile", () -> {
            cines.ensureCineAraucoPremiumSeleccionado();
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
    @Order(11)
    @DisplayName("Compra mix - Cine Parque Arauco Premium- Sin sesión")
    @Story("Flujos de compra en cine VIP - Chile\")")
    void compraMixParqueAraucoPremium() {
        TestSteps.run("Compra de boleto y alimento en cine Parque Arauco - VIP - Chile", () -> {
            cines.ensureCineAraucoPremiumSeleccionado();
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
    @Order(12)
    @DisplayName("Compra de alimento - Cine Parque Arauco Premium - Sin sesión")
    @Story("Flujos de compra en cine VIP - Chile")
    void compraAlimentoParqueAraucoPremium() {
        TestSteps.run("Compra de alimento en cine Parque Arauco Premium - VIP - Chile", () -> {
            cines.ensureCineAraucoPremiumSeleccionado();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            alimentos.vincularOrdenChile();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();

        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }





}
