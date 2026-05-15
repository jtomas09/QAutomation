package pages.seleccionCines;
import java.util.Map;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.common.BasePage;
import static pages.seleccionCines.LocatorsSelectorCines.*;

public class SelectorsCines extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    public SelectorsCines(AndroidDriver driver) {
        super(driver);
    }

    public void abrirSelectorCines() {
        waitForVisibility(TAB_CARTELERA);
        if (!isVisibleQuick(TAB_HORARIOS_SELECCIONADO)) {
            click(TAB_HORARIOS);
        }
        while (isVisibleQuick(BOTON_CERRAR_TAB_CINE)) {
            click(BOTON_CERRAR_TAB_CINE);
        }
        waitAndClick(SELECTOR_CINES, 15);
        clickAceptarUbicacion();
    }

    public void cerrarAlertaLocalizacionSiPresente() {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            if (clickIfPresent(CERRAR_ALERTA_LOCALIZACIÓN)) return;
            sleep(200);
        }
    }

    public void clickAceptarUbicacion() {
        this.clickIfPresent(ACEPTAR_PERMISOS_UBICACION);
        this.clickIfPresent(CONCEDER_PERMISOS_UBICACION_NATIVO);
    }

    public void clickDenegarUbicacion() {
        this.clickIfPresent(DENEGAR_PERMISOS_UBICACION_NATIVO);
    }

    public void clickBuscadorCiudad() {
        this.click(BUSCADOR_CIUDAD_INPUT);
    }

    public void buscarCineAtmosfera() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "Cumbres Monterrey"));
    }

    public void buscarCiudadMorelia() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "Morelia"));
    }

    public void clickCiudadMorelia() {
        this.click(CIUDAD_MORELIA);
    }

    public void clickCineVIPMorelia() {
        findVisibleOrScrollDownAndRightSlowToXpathAndClick(
            "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]", 1, 5);
    }

    public void buscarCineVIP() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "vip"));
    }

    public void clickCineVIP() {
        this.click(CINE_VIP_UNIVERSAL);
    }

    public void seleccionarCineTradicional() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "escala morelia"));
        this.click(CINE_ESCALA_MORELIA);
    } 

    public void seleccionarCineAtmosfera() {
        this.click(CINE_ATMOSFERA_CUMBRES);
    }

    public void aplicarSeleccionCine() {
        this.click(BOTON_APLICAR_SELECCION);
        this.clickIfPresent(ACEPTAR_CAMBIAR_CIUDAD);
    }

//SELECCIÓN DE CINES - ARGENTINA

    public void seleccionarCinesBuenosAires() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(SELECCIONAR_TODOS_LOS_CINES);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineAvellaneda() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_AVELLANEDA);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineLujan() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_LUJAN);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineMerlo() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_MERLO);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCinePilar() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_PILAR);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCinePlazaHoussay() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_PLAZA_HOUSSAY);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineRecoleta() {
        this.click(CIUDAD_BUENOS_AIRES);
        this.click(CINE_RECOLETA);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineArenaMaipu() {
        this.click(CIUDAD_MENDOZA);
        this.click(CINE_ARENA_MAIPU);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineMendozaPlaza() {
        this.click(CIUDAD_MENDOZA);
        this.click(CINE_MENDOZA_PLAZA);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineNeuquen() {
        this.click(CIUDAD_NEUQUEN);
        this.click(CINE_NEUQUEN);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineRosario() {
        this.click(CIUDAD_SANTA_FE);
        this.click(CINE_ROSARIO);
        this.aplicarSeleccionCine();
    }

//SELECCIÓN DE CINES - CHILE

    public void seleccionarCineLaReina() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "la reina"));
        validarElementoVisible(CINE_LA_REINA);
        this.click(CINE_LA_REINA);
        this.aplicarSeleccionCine();
    } 

    public void seleccionarCineDominicos() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "los dominicos"));
        validarElementoVisible(CINE_LOS_DOMINICOS);
        this.click(CINE_LOS_DOMINICOS);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineMallplazaAntofagasta() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "mallplaza antofagasta"));
        validarElementoVisible(CINE_MALLPLAZA_ANTOFAGASTA);
        this.click(CINE_MALLPLAZA_ANTOFAGASTA);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineParqueArauco() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "parque arauco"));
        validarElementoVisible(CINE_ARAUCO);
        this.click(CINE_ARAUCO);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineAraucoPremium() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "parque arauco premium"));
        validarElementoVisible(CINE_PARQUE_ARAUCO_PREMIUM);
        this.click(CINE_PARQUE_ARAUCO_PREMIUM);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCineLosDominicosPremium() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "dominicos premium"));
        validarElementoVisible(CINE_DOMINICOS_PREMIUM);
        this.click(CINE_DOMINICOS_PREMIUM);
        this.aplicarSeleccionCine();
    }

//SELECCIÓN DE CINES - ESPAÑA

    public void seleccionarCinePlenilunio() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "plenilunio"));
        validarElementoVisible(CINE_PLENILUNIO);
        this.click(CINE_PLENILUNIO);
        this.aplicarSeleccionCine();
    } 

    public void seleccionarCineParqueCorredor() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "parque corredor"));
        validarElementoVisible(CINE_PARQUE_CORREDOR);
        this.click(CINE_PARQUE_CORREDOR);
        this.aplicarSeleccionCine();
    } 

    public void seleccionarCineTresAguas() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "tres"));
        validarElementoVisible(CINE_TRES_AGUAS);
        this.click(CINE_TRES_AGUAS);
        this.aplicarSeleccionCine();
    } 

    public void seleccionarCinePlazaNorte() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "plaza norte 2"));
        validarElementoVisible(CINE_PLAZA_NORTE);
        this.click(CINE_PLAZA_NORTE);
        this.aplicarSeleccionCine();
    }

    public void seleccionarCinePalafoxLuxury() {
        this.click(BUSCADOR_CIUDAD_INPUT);
        this.driver.executeScript("mobile: type", Map.of("text", "palafox luxury"));
        validarElementoVisible(CINE_PALAFOX_LUXURY);
        this.click(CINE_PALAFOX_LUXURY);
        this.aplicarSeleccionCine();
    }


//VALIDACIONES DURANTE EL FLUJO
    public void validarTabCineTradicional() {
        validarElementoVisible(TAB_CINE_ESCALA_MORELIA);
        Allure.step("✅ Cine Escala Morelia seleccionado correctamente");
    }

    public void validarTabCineAtmosfera() {
        validarElementoVisible(TAB_CINE_CUMBRES);
        Allure.step("✅ Cine Atmósfera Cumbres Monterrey seleccionado correctamente");
    }

    public void validarTabCineVIPMorelia() {
        validarElementoVisible(TAB_CINE_VIP_MORELIA);
        Allure.step("✅ Cine VIP Espacio Las Américas seleccionado correctamente");
    }

    public void validarTabCineVIP() {
        validarElementoVisible(TAB_CINE_VIP);
        Allure.step("✅ Cine VIP Diana Acapulco seleccionado correctamente");
    }

//VALIDACIONES ARGENTINA

    public void validarTabCineAvellaneda() {
        validarElementoVisible(TAB_CINE_AVELLANEDA);
        Allure.step("✅ Cine Avellaneda (Buenos Aires) seleccionado correctamente");
    }

    public void validarTabCineLujan() {
        validarElementoVisible(TAB_CINE_LUJAN);
        Allure.step("✅ Cine Lujan (Buenos Aires) seleccionado correctamente");
    }

    public void validarTabCineMerlo() {
        validarElementoVisible(TAB_CINE_MERLO);
        Allure.step("✅ Cine Merlo (Buenos Aires) seleccionado correctamente");
    }

    public void validarTabCinePilar() {
        validarElementoVisible(TAB_CINE_PILAR);
        Allure.step("✅ Cine Pilar (Buenos Aires) seleccionado correctamente");
    }

    public void validarTabCinePlazaHoussay() {
        validarElementoVisible(TAB_CINE_PLAZA_HOUSSAY);
        Allure.step("✅ Cine Plaza Houssay (Buenos Aires) seleccionado correctamente");
    }
    
    public void validarTabCineRecoleta() {
        validarElementoVisible(TAB_CINE_RECOLETA);
        Allure.step("✅ Cine Recoleta (Buenos Aires) seleccionado correctamente");
    }

    public void validarTabCineArenaMaipu() {
        validarElementoVisible(TAB_CINE_ARENA_MAIPU);
        Allure.step("✅ Cine Arena Maipu (Mendoza) seleccionado correctamente");
    }

    public void validarTabCineMendozaPlaza() {
        validarElementoVisible(TAB_CINE_MENDOZA_PLAZA);
        Allure.step("✅ Cine Mendoza Plaza (Mendoza) seleccionado correctamente");
    }

    public void validarTabCineNeuquen() {
        validarElementoVisible(TAB_CINE_NEUQUEN);
        Allure.step("✅ Cine Neuquen (Neuquen) seleccionado correctamente");
    }

    public void validarTabCineRosario() {
        validarElementoVisible(TAB_CINE_ROSARIO);
        Allure.step("✅ Cine Rosario (Santa Fe) seleccionado correctamente");
    }

// VALIDACIONES CHILE

    public void validarTabCineLaReina() {
        validarElementoVisible(TAB_CINE_LA_REINA);
        Allure.step("✅ Cine La Reina (Santiago Oriente) seleccionado correctamente");
    }

    public void validarTabCineLosDominicos() {
        validarElementoVisible(TAB_CINE_LOS_DOMINICOS);
        Allure.step("✅ Cine Los Dominicos (Santiago Oriente) seleccionado correctamente");
    }

    public void validarTabCineAntofagasta() {
        validarElementoVisible(TAB_CINE_ANTOFAGASTA);
        Allure.step("✅ Cine Antofagasta (Norte y Centro de Chile) seleccionado correctamente");
    }

    public void validarTabCineParqueArauco() {
        validarElementoVisible(TAB_CINE_PARQUE_ARAUCO);
        Allure.step("✅ Cine Parque Arauco (Santiago Oriente) seleccionado correctamente");
    }

    public void validarTabCineParqueAraucoPremium() {
        validarElementoVisible(TAB_CINE_PARQUE_ARAUCO_PREMIUM);
        Allure.step("✅ Cine Parque Arauco Premium (Santiago Oriente) seleccionado correctamente");
    }

// VALIDACIONES ESPAÑA

    public void validarTabCinePlenilunio() {
        validarElementoVisible(TAB_CINE_PLENILUNIO);
        Allure.step("✅ Cine Plenilunio (Madrid) seleccionado correctamente");
    }

    public void validarTabCineParqueCorredor() {
        validarElementoVisible(TAB_CINE_PARQUE_CORREDOR);
        Allure.step("✅ Cine Premium Parque Corredor (Madrid) seleccionado correctamente");
    }

    public void validarTabCineTresAguas() {
        validarElementoVisible(TAB_CINE_TRES_AGUAS);
        Allure.step("✅ Cine TresAguas (Madrid) seleccionado correctamente");
    }

    public void validarTabCinePlazaNorte() {
        validarElementoVisible(TAB_CINE_PLAZA_NORTE);
        Allure.step("✅ Cine Plaza Norte 2 (Madrid) seleccionado correctamente");
    }

    public void validarTabCinePalafoxLuxury() {
        validarElementoVisible(TAB_CINE_PALAFOX_LUXURY);
        Allure.step("✅ Cine Palafox Luxury (Madrid) seleccionado correctamente");
    }    

}
