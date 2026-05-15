package pages.mapaAsientos;
import static pages.mapaAsientos.LocatorsMapaAsientos.*;

import org.openqa.selenium.By;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.common.BasePage;

public class SelectorsMapaAsientos extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    private static final int PANTALLA_TIMEOUT_MS = 25_000;

    public SelectorsMapaAsientos(AndroidDriver driver) {
        super(driver);
    }


// MAPA DE ASIENTOS
    public void cerrarOverlayMapa() {
        this.clickIfPresent(OVERLAY_MAPA_ASIENTOS);
    }

    public void seleccionarAsiento() {
        esperarMapaOFallar();
        tapAsientoExacto(waitForVisibility(locatorAsiento()));
    }

    public void seleccionarDosAsientos() {
        esperarMapaOFallar();
        By locator = locatorAsiento();
        tapAsientoExacto(waitForVisibility(locator));
        tapAsientoExacto(waitForVisibility(locator));
    }

    // Devuelve el localizador correcto según el tipo de fila de la sala:
    // · Filas con letra (A, B, C…) → localizador estándar (busca TextView numérico habilitado)
    // · Filas con número (1, 2, 3…) → localizador de España (busca View habilitado que
    //   contiene un TextView numérico, evitando confundir la etiqueta de fila con un asiento)
    private By locatorAsiento() {
        return isVisibleQuick(PRIMERA_FILA) ? PRIMER_ASIENTO_DISPONIBLE : PRIMER_ASIENTO_DISPONIBLE_ESPANA;
    }

    // España: filas numeradas (1,2,3…) en lugar de letras (A,B,C…).
    // Usa PRIMER_ASIENTO_DISPONIBLE_ESPANA que busca el View[@enabled='true']
    // directamente, evitando que el locator encuentre primero el contenedor
    // numérico de etiqueta de fila (falso positivo del DOM de Compose).
    public void seleccionarAsientoEspana() {
        esperarMapaOFallar();
        tapAsientoExacto(waitForVisibility(PRIMER_ASIENTO_DISPONIBLE_ESPANA));
    }

    public void seleccionarDosAsientosEspana() {
        esperarMapaOFallar();
        tapAsientoExacto(waitForVisibility(PRIMER_ASIENTO_DISPONIBLE_ESPANA));
        tapAsientoExacto(waitForVisibility(PRIMER_ASIENTO_DISPONIBLE_ESPANA));
    }

    /**
     * Toca el centro exacto de un asiento sin offset hardcodeado.
     * Usa mobile:clickGesture (óptimo para Compose) con fallbacks a W3C y el.click().
     * Las coordenadas se derivan de los bounds del elemento, por lo que escalan
     * automáticamente a cualquier resolución o densidad de pantalla.
     */
    private void tapAsientoExacto(org.openqa.selenium.WebElement asiento) {
        int cx = asiento.getLocation().getX() + asiento.getSize().getWidth() / 2;
        int cy = asiento.getLocation().getY() + asiento.getSize().getHeight() / 2;

        try {
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("x", cx);
            args.put("y", cy);
            driver.executeScript("mobile: clickGesture", args);
            takeScreenshot();
            return;
        } catch (Exception ignored) {}

        try {
            tapW3C(cx, cy);
            takeScreenshot();
            return;
        } catch (Exception ignored) {}

        asiento.click();
        takeScreenshot();
    }

    private void esperarMapaOFallar() {
        if (!esperarPantalla()) {
            String info = obtenerInfoFuncionMapa();
            Allure.step("⚠️ Mapa de asientos no cargó – " + info);
            throw new AssertionError("El mapa de asientos no cargó – " + info);
        }
    }

    private boolean esperarPantalla() {
        // Filas con letra (A, B, C…) o con número (1, 2, 3…)
        java.util.regex.Pattern patronFila = java.util.regex.Pattern.compile("text=\"([A-Z]|[1-9])\"");
        long limite = System.currentTimeMillis() + PANTALLA_TIMEOUT_MS;
        while (System.currentTimeMillis() < limite) {
            try {
                // getPageSource() usa un endpoint HTTP distinto a findElements():
                // bypasa el motor XPath de UIAutomator2 que falla silenciosamente en Compose
                String xml = driver.getPageSource();
                if (xml != null && (xml.contains("Pantalla Sala") || patronFila.matcher(xml).find())) {
                    return true;
                }
            } catch (Exception ignore) {}
            sleep(1000);
        }
        return false;
    }

    private String obtenerInfoFuncionMapa() {
        try {
            String xml = driver.getPageSource();

            // Título: texto inmediatamente después de "Paso N de M"
            String pelicula = "—";
            java.util.regex.Matcher mPaso = java.util.regex.Pattern
                .compile("text=\"Paso \\d+ de \\d+\"").matcher(xml);
            if (mPaso.find()) {
                java.util.regex.Matcher mTitulo = java.util.regex.Pattern
                    .compile("text=\"([^\"]+)\"").matcher(xml.substring(mPaso.end()));
                if (mTitulo.find()) pelicula = mTitulo.group(1);
            }

            // Fecha: "DD Mes"
            java.util.regex.Matcher mFecha = java.util.regex.Pattern
                .compile("text=\"(\\d{1,2} [A-ZÁÉÍÓÚ][a-záéíóú]+)\"").matcher(xml);
            String fecha = mFecha.find() ? mFecha.group(1) : "—";

            // Hora: "H:MM AM/PM"
            java.util.regex.Matcher mHora = java.util.regex.Pattern
                .compile("text=\"(\\d{1,2}:\\d{2} [AP]M)\"").matcher(xml);
            String hora = mHora.find() ? mHora.group(1) : "—";

            return "🎬 " + pelicula + " | 📅 " + fecha + " " + hora;
        } catch (Exception e) {
            return "función no identificada";
        }
    }

    public void clickContinuarMapaAsientos() {
        this.click(BOTON_CONTINUAR_MAPA);
        verificarSinErrorApp();

        // Verificar que cargó el selector de boletos
        boolean boletosCargados = false;
        long limite = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < limite) {
            if (isVisibleQuick(BOTON_CONTINUAR_BOLETOS)) { boletosCargados = true; break; }
            sleep(400);
        }

        if (!boletosCargados) {
            String info = obtenerInfoFuncionMapa();
            Allure.step("⚠️ Selector de boletos no cargó tras continuar – " + info);
            throw new AssertionError("El selector de boletos no cargó tras continuar – " + info);
        }
    }

    public void agregarBoletoAdulto() {
        this.click(AUMENTAR_BOLETO_ESTANDAR);
    }

    public void agregarDosBoletosStandar() {
        this.click(AUMENTAR_BOLETO_ESTANDAR);
        this.click(AUMENTAR_BOLETO_ESTANDAR);
    }

    public void clickContinuarSelectorBoletos() {
        this.click(BOTON_CONTINUAR_BOLETOS);
        verificarSinErrorApp();
    }



}
