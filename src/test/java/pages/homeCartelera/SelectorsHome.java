package pages.homeCartelera;

import org.openqa.selenium.By;
import io.appium.java_client.AppiumDriver;
import pages.common.BasePage;
import static pages.homeCartelera.LocatorsHome.*;
import static pages.perfil.LocatorsPerfil.*;


public class SelectorsHome extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelectorsHome.class);

    public SelectorsHome(AppiumDriver driver) {
        super(driver);
    }


    public void seleccionarPrimerHorario() {
        By botonAceptar     = By.xpath("//android.widget.TextView[@text='Aceptar']");
        By cualquierHorario = By.xpath("(//android.widget.TextView[contains(@text,'PM') or contains(@text,'AM')])[1]");

        for (int intento = 1; intento <= 5; intento++) {
            By horario = By.xpath(
                "(//android.widget.TextView[contains(@text,'PM') or contains(@text,'AM')])[" + intento + "]"
            );
            this.click(horario);
            sleep(800);
            this.clickIfPresent(ALERTA_ACEPTAR_CONTINUAR);

            // Polling: esperar hasta 8s por error ("Aceptar") o mapa listo (asiento disponible)
            boolean hayError = false;
            long limite = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < limite) {
                if (isVisibleQuick(botonAceptar))           { hayError = true; break; }
                if (isVisibleQuick(PRIMER_ASIENTO_DISPONIBLE)) { break; } // mapa cargado ✅
                sleep(400);
            }

            if (hayError) {
                try { driver.findElement(botonAceptar).click(); } catch (Exception ignored) {}
                waitForVisibility(cualquierHorario);
                sleep(500);
                continue;
            }

            return; // mapa de asientos cargado sin errores
        }

        org.junit.jupiter.api.Assertions.fail("❌ No se encontró ningún horario disponible después de 5 intentos.");
    }

    public void seleccionarPrimerHorarioEspaña() {
        // España usa formato 24 h (ej. "21:00") sin indicadores AM/PM
        By cualquierHorario = By.xpath(
            "(//android.widget.TextView[contains(@text,':') " +
            "and not(contains(@text,'AM')) and not(contains(@text,'PM')) " +
            "and string(number(substring-before(@text,':'))) != 'NaN'])[1]");
        By botonAceptar = By.xpath("//android.widget.TextView[@text='Aceptar']");

        for (int intento = 1; intento <= 5; intento++) {
            By horario = By.xpath(
                "(//android.widget.TextView[contains(@text,':') " +
                "and not(contains(@text,'AM')) and not(contains(@text,'PM')) " +
                "and string(number(substring-before(@text,':'))) != 'NaN'])[" + intento + "]");
            this.click(horario);
            sleep(800);
            this.clickIfPresent(ALERTA_ACEPTAR_CONTINUAR);

            boolean hayError = false;
            long limite = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < limite) {
                if (isVisibleQuick(botonAceptar))              { hayError = true; break; }
                if (isVisibleQuick(PRIMER_ASIENTO_DISPONIBLE)) { break; }
                sleep(400);
            }

            if (hayError) {
                try { driver.findElement(botonAceptar).click(); } catch (Exception ignored) {}
                waitForVisibility(cualquierHorario);
                sleep(500);
                continue;
            }

            return;
        }

        org.junit.jupiter.api.Assertions.fail("❌ No se encontró ningún horario disponible después de 5 intentos.");
    }

    public void clickSeccionAlimentos() {
        this.click(TAB_SECCION_ALIMENTOS);
    }

    public void clickSeccionAlimentosEspaña() {
        this.click(TAB_SECCION_ALIMENTOS_ESPAÑA);
    }

//CAMBIAR DE PAÍS DESDE MÉXICO

    public void abrirSelectorPaises() {
        // Espera a que cargue: home (Argentina o cambio previo) o Club Cinépolis (resto de países)
        By indicadorIdioma = By.xpath("//android.widget.TextView[@text='ES']");
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            if (isVisibleQuick(TAB_CARTELERA) || isVisibleQuick(indicadorIdioma)) break;
            sleep(400);
        }
        if (isVisibleQuick(OPCION_PERFIL_HOME)) {
            this.click(OPCION_PERFIL_HOME);
        }
        if (isVisibleQuick(BOTON_CAMBIAR_PAIS_DESDE_ARGENTINA)) {
            this.click(BOTON_CAMBIAR_PAIS_DESDE_ARGENTINA);
        } else {
            this.click(BOTON_CAMBIAR_PAIS);
        }
    }

    public void abrirSelectorPaisesDesdeArgentina() {
        abrirSelectorPaises();
    }

//MÉTODOS PARA CAMBIAR DE PAÍS DESDE MX, ES, CL E INDIA

    public void cambiarPaisMexico() {
        abrirSelectorPaises();
        seleccionarPais(OPCION_MEXICO, "México");
    }

    public void cambiarPaisArgentina() {
        abrirSelectorPaises();
        seleccionarPais(OPCION_ARGENTINA, "Argentina");
    }

    public void cambiarPaisIndia() {
        abrirSelectorPaises();
        seleccionarPais(OPCION_INDIA, "India");
    }

    public void cambiarPaisEspaña() {
        abrirSelectorPaises();
        seleccionarPais(OPCION_ESPAÑA, "España");
    }

    public void cambiarPaisChile() {
        abrirSelectorPaises();
        seleccionarPais(OPCION_CHILE, "Chile");
    }

//MÉTODOS PARA CAMBIAR DE PAÍS DESDE ARGENTINA

    public void cambiarPaisMexicoDesdeArgentina() {
        abrirSelectorPaisesDesdeArgentina();
        seleccionarPais(OPCION_MEXICO, "México");
    }

    public void cambiarPaisIndiaDesdeArgentina() {
        abrirSelectorPaisesDesdeArgentina();
        seleccionarPais(OPCION_INDIA, "India");
    }

    public void cambiarPaisEspañaDesdeArgentina() {
        abrirSelectorPaisesDesdeArgentina();
        seleccionarPais(OPCION_ESPAÑA, "España");
    }

    public void cambiarPaisChileDesdeArgentina() {
        abrirSelectorPaisesDesdeArgentina();
        seleccionarPais(OPCION_CHILE, "Chile");
    }

    // Si el país ya está seleccionado (RadioButton checked=true en su fila), cierra el listado.
    // Si no lo está, hace click en la opción, click en Aplicar y espera 5s para que el servidor
    // persista el cambio. En ambos casos termina la app sin relanzarla; @BeforeEach se encarga
    // del relaunch, evitando dos ciclos terminate+activate en cascada.
    private void seleccionarPais(By opcion, String nombrePais) {
        log.info("[País] Iniciando verificación del país seleccionado...");
        By yaSeleccionado = By.xpath(
            "//android.view.View[android.widget.RadioButton[@checked='true']" +
            " and android.widget.TextView[@text='" + nombrePais + "']]");
        if (isVisibleQuick(yaSeleccionado)) {
            log.info("[País] {} ya está seleccionado. Iniciando ejecución de los tests.", nombrePais);
            click(CERRAR_LISTADO_PAISES);
            // Argentina no regresa al home al cerrar el listado: queda en Ajustes.
            // Terminar la app desde ahí provoca que al relanzarla aparezca la pantalla de login
            // y bloquee el flujo. Navegamos al home antes de terminar la app.
            if ("Argentina".equals(nombrePais)) {
                navegarAlHomeDesdeAjustesArgentina();
            }
        } else {
            click(opcion);
            click(BOTON_APLICAR);
            System.out.println("[PAÍS] Espera 5s para que el servidor persista el cambio de país...");
            try { Thread.sleep(5_000); } catch (InterruptedException ignored) {}
        }
        terminarAppSinRelanzar();
    }

    private void navegarAlHomeDesdeAjustesArgentina() {
        clickIfPresent(BOTON_IR_ATRAS_AJUSTES);
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            if (isVisibleQuick(TAB_CARTELERA)) {
                System.out.println("[PAÍS][ARG] Tab Cartelera visible - home alcanzado antes de terminar app.");
                return;
            }
            sleep(300);
        }
        System.out.println("[PAÍS][ARG] WARN: Tab Cartelera no visible tras 5s, se continúa de todas formas.");
    }

    private void terminarAppSinRelanzar() {
        try {
            if (isIOS()) {
                ((io.appium.java_client.ios.IOSDriver) driver).runAppInBackground(java.time.Duration.ofSeconds(-1));
            } else {
                ((io.appium.java_client.android.AndroidDriver) driver).runAppInBackground(java.time.Duration.ofSeconds(-1));
            }
            System.out.println("[PAÍS] App enviada a background. @BeforeEach la relanzará con el país actualizado.");
        } catch (Exception e) {
            System.out.println("[PAÍS] runAppInBackground falló, intentando terminateApp: " + e.getMessage());
            try {
                String key = isIOS() ? "bundleId" : "appPackage";
                Object pkg = driver.getCapabilities().getCapability(key);
                if (pkg != null) terminateApp(pkg.toString());
            } catch (Exception ignored) {}
        }
    }

//MÉTODO DE PRUEBA PARA ELEGIR UN HORARIO ESPECÍFICO
    public void pruebaHorario() {
        By botonAceptar = By.xpath("//android.widget.TextView[@text='Aceptar']");

        for (int intento = 1; intento <= 5; intento++) {
            this.click(HORARIO_PRUEBA);
            sleep(800);
            this.clickIfPresent(ALERTA_ACEPTAR_CONTINUAR);

            boolean hayError = false;
            long limite = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < limite) {
                if (isVisibleQuick(botonAceptar))              { hayError = true; break; }
                if (isVisibleQuick(PRIMER_ASIENTO_DISPONIBLE)) { break; }
                sleep(400);
            }

            if (hayError) {
                try { driver.findElement(botonAceptar).click(); } catch (Exception ignored) {}
                waitForVisibility(HORARIO_PRUEBA);
                sleep(500);
                continue;
            }

            return;
        }

        org.junit.jupiter.api.Assertions.fail("❌ No se pudo seleccionar el horario de prueba después de 5 intentos.");
    }
    

}
