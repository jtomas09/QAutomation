package tests.Argentina;

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
@Epic("Pruebas de no afectación Argentina")

public class NoAfectacionArgentina extends BaseTest {

    private SelectorsCines        cines;
    private SelectorsHome         home;
    private SelectorsMapaAsientos mapa;
    private SelectorsAlimentos    alimentos;
    private SelectorsCarrito      carrito;
    private SelectorsCheckOut     checkout;

   
    @BeforeAll
    void configurarPais() {
        driver = DriverFactory.getDriver();
        new SelectorsHome(driver).cambiarPaisArgentina();
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
    @DisplayName("Compra ticket - Cine Avellaneda - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketAvellaneda() {
        TestSteps.run("Compra de boleto en cine Avellaneda - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineAvellaneda();
            cines.validarTabCineAvellaneda();
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
    @DisplayName("Compra mix - Cine Avellaneda - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixAvellaneda() {
        TestSteps.run("Compra de boleto y alimento en cine Avellaneda - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineAvellaneda();
            cines.validarTabCineAvellaneda();
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
    @DisplayName("Compra de alimento - Cine Avellaneda - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodAvellaneda() {
        TestSteps.run("Compra de alimento en cine Avellaneda - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineAvellaneda();
            cines.validarTabCineAvellaneda();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @DisplayName("Compra ticket - Cine Lujan - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketLujan() {
        TestSteps.run("Compra de boleto en cine Lujan - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineLujan();
            cines.validarTabCineLujan();
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
    @DisplayName("Compra mix - Cine Lujan - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixLujan() {
        TestSteps.run("Compra de boleto y alimento en cine Lujan - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineLujan();
            cines.validarTabCineLujan();
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
    @DisplayName("Compra de alimento - Cine Lujan - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodLujan() {
        TestSteps.run("Compra de alimento en cine Lujan - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineLujan();
            cines.validarTabCineLujan();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @DisplayName("Compra ticket - Cine Merlo - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketMerlo() {
        TestSteps.run("Compra de boleto en cine Merlo - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMerlo();
            cines.validarTabCineMerlo();
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
    @DisplayName("Compra mix - Cine Merlo - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixMerlo() {
        TestSteps.run("Compra de boleto y alimento en cine Merlo - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMerlo();
            cines.validarTabCineMerlo();
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
    @DisplayName("Compra de alimento - Cine Merlo - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodMerlo() {
        TestSteps.run("Compra de alimento en cine Merlo - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMerlo();
            cines.validarTabCineMerlo();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @Order(10)
    @DisplayName("Compra ticket - Cine Pilar - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketPilar() {
        TestSteps.run("Compra de boleto en cine Pilar - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePilar();
            cines.validarTabCinePilar();
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
    @DisplayName("Compra mix - Cine Pilar - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixPilar() {
        TestSteps.run("Compra de boleto y alimento en cine Pilar - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePilar();
            cines.validarTabCinePilar();
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
    @DisplayName("Compra de alimento - Cine Pilar - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodPilar() {
        TestSteps.run("Compra de alimento en cine Pilar - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePilar();
            cines.validarTabCinePilar();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @Order(13)
    @DisplayName("Compra ticket - Cine Plaza Houssay - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketPlazaHoussay() {
        TestSteps.run("Compra de boleto en cine Plaza Houssay - Argentina - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePlazaHoussay();
            cines.validarTabCinePlazaHoussay();
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
    @Order(14)
    @DisplayName("Compra mix - Cine Plaza Houssay - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixPlazaHoussay() {
        TestSteps.run("Compra de boleto y alimento en cine Plaza Houssay - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePlazaHoussay();
            cines.validarTabCinePlazaHoussay();
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
    @Order(15)
    @DisplayName("Compra de alimento - Cine Plaza Houssay - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodPlazaHoussay() {
        TestSteps.run("Compra de alimento en cine Plaza Houssay - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCinePlazaHoussay();
            cines.validarTabCinePlazaHoussay();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @Order(16)
    @DisplayName("Compra ticket - Cine Recoleta - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraTicketRecoleta() {
        TestSteps.run("Compra de boleto en cine Recoleta - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRecoleta();
            cines.validarTabCineRecoleta();
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
    @Order(17)
    @DisplayName("Compra mix - Cine Recoleta - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraMixRecoleta() {
        TestSteps.run("Compra de boleto y alimento en cine Recoleta - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRecoleta();
            cines.validarTabCineRecoleta();
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
    @Order(18)
    @DisplayName("Compra de alimento - Cine Recoleta - Sin sesión")
    @Story("Flujos de compra Buenos Aires - Argentina")
    void compraFoodRecoleta() {
        TestSteps.run("Compra de alimento en cine Recoleta - Buenos Aires - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRecoleta();
            cines.validarTabCineRecoleta();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();
        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }

//TESTS DE CIUDAD MENDOZA    
    @Test
    @Order(19)
    @DisplayName("Compra ticket - Cine Arena Maipu - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraTicketArenaMaipu() {
        TestSteps.run("Compra de boleto en cine Arena Maipu - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineArenaMaipu();
            cines.validarTabCineArenaMaipu();
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
    @Order(20)
    @DisplayName("Compra mix - Cine Arena Maipu - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraMixArenaMaipu() {
        TestSteps.run("Compra de boleto y alimento en cine Arena Maipu - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineArenaMaipu();
            cines.validarTabCineArenaMaipu();
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
    @Order(21)
    @DisplayName("Compra de alimento - Cine Arena Maipu - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraFoodArenaMaipu() {
        TestSteps.run("Compra de alimento en cine Arena Maipu - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineArenaMaipu();
            cines.validarTabCineArenaMaipu();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
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
    @Order(22)
    @DisplayName("Compra ticket - Cine Mendoza Plaza - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraTicketMendozaPlaza() {
        TestSteps.run("Compra de boleto en cine Mendoza Plaza - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMendozaPlaza();
            cines.validarTabCineMendozaPlaza();
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
    @Order(23)
    @DisplayName("Compra mix - Cine Mendoza Plaza - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraMixMendozaPlaza() {
        TestSteps.run("Compra de boleto y alimento en cine Mendoza Plaza - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMendozaPlaza();
            cines.validarTabCineMendozaPlaza();
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
    @Order(24)
    @DisplayName("Compra de alimento - Cine Mendoza Plaza - Sin sesión")
    @Story("Flujos de compra Mendoza - Argentina")
    void compraFoodMendozaPlaza() {
        TestSteps.run("Compra de alimento en cine Mendoza Plaza - Mendoza - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineMendozaPlaza();
            cines.validarTabCineMendozaPlaza();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();
        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }    

//TESTS DE CIUDAD NEUQUEN    

    @Test
    @Order(25)
    @DisplayName("Compra ticket - Cine Neuquen - Sin sesión")
    @Story("Flujos de compra Neuquen - Argentina")
    void compraTicketNeuquen() {
        TestSteps.run("Compra de boleto en cine Neuquen - Neuquen - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineNeuquen();
            cines.validarTabCineNeuquen();
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
    @Order(26)
    @DisplayName("Compra mix - Cine Neuquen - Sin sesión")
    @Story("Flujos de compra Neuquen - Argentina")
    void compraMixNeuquen() {
        TestSteps.run("Compra de boleto y alimento en cine Neuquen - Neuquen - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineNeuquen();
            cines.validarTabCineNeuquen();
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
    @Order(27)
    @DisplayName("Compra de alimento - Cine Neuquen - Sin sesión")
    @Story("Flujos de compra Neuquen - Argentina")
    void compraFoodNeuquen() {
        TestSteps.run("Compra de alimento en cine Neuquen - Neuquen - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineNeuquen();
            cines.validarTabCineNeuquen();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();
        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }    

//TESTS DE CIUDAD SANTA FE    

    @Test
    @Order(28)
    @DisplayName("Compra ticket - Cine Rosario - Sin sesión")
    @Story("Flujos de compra Santa Fe - Argentina")
    void compraTicketRosario() {
        TestSteps.run("Compra de boleto en cine Rosario - Santa Fe - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRosario();
            cines.validarTabCineRosario();
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
    @Order(29)
    @DisplayName("Compra mix - Cine Rosario - Sin sesión")
    @Story("Flujos de compra Santa Fe - Argentina")
    void compraMixRosario() {
        TestSteps.run("Compra de boleto y alimento en cine Rosario - Santa Fe - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRosario();
            cines.validarTabCineRosario();
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
    @Order(30)
    @DisplayName("Compra de alimento - Cine Rosario - Sin sesión")
    @Story("Flujos de compra Santa Fe - Argentina")
    void compraFoodRosario() {
        TestSteps.run("Compra de alimento en cine Rosario - Santa Fe - Argentina", () -> {
            cines.abrirSelectorCines();
            cines.seleccionarCineRosario();
            cines.validarTabCineRosario();
            home.clickSeccionAlimentos();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.seleccionarAlimentoAleatorio();
            alimentos.clikIrAPagar();
            carrito.validarAlimentoEnCarrito();
            carrito.clickContinuarCarrito();
            checkout.llenarDatosPersonales();
            //checkout.pagarConTarjetaBancaria();
        }, driver);
        TestSteps.run("Validar orden generada", () -> checkout.validarOrdenGeneradaCorrectamente(), driver);
    }    


}

