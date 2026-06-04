package pages.mapaAsientos;
import static pages.mapaAsientos.LocatorsMapaAsientos.*;

import org.openqa.selenium.By;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.common.BasePage;

public class SelectorsMapaAsientos extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelectorsMapaAsientos.class);
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
        log.info("[Mapa] Mapa cargado correctamente, iniciando selección del primer asiento disponible");
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
        log.info("[Mapa] Mapa cargado (España), buscando primer asiento disponible...");
        tapAsientoEspana();
    }

    public void seleccionarDosAsientosEspana() {
        esperarMapaOFallar();
        tapAsientoEspana();
        tapAsientoEspana();
    }

    // Busca el primer TextView numérico+enabled que NO esté en la columna izquierda
    // de etiquetas de fila (x < 90) y toca sus coordenadas directamente.
    // Evita navegar al padre via XPath (..) que falla en elementos Compose de España.
    private void tapAsientoEspana() {
        By tvNumericos = By.xpath(
            "//android.widget.TextView[contains(@text,'Pantalla')]" +
            "/following::android.widget.TextView[" +
                "(string(number(@text)) != 'NaN' or @text='PR') and @enabled='true']");

        java.util.List<org.openqa.selenium.WebElement> candidatos = driver.findElements(tvNumericos);
        System.out.println("[DEBUG-MAPA] Candidatos (numérico o PR) enabled: " + candidatos.size());

        for (org.openqa.selenium.WebElement tv : candidatos) {
            org.openqa.selenium.Point loc = tv.getLocation();
            if (loc.x < 90) {
                System.out.println("[DEBUG-MAPA] Saltando etiqueta de fila en x=" + loc.x + " texto='" + tv.getText() + "'");
                continue;
            }
            org.openqa.selenium.Dimension sz = tv.getSize();
            int cx = loc.x + sz.width  / 2;
            int cy = loc.y + sz.height / 2;
            String texto = "";
            try { texto = tv.getText(); } catch (Exception ignored) {}
            System.out.println("[DEBUG-MAPA] Tapping asiento: texto='" + texto + "' coords=(" + cx + "," + cy + ")");
            try {
                java.util.Map<String, Object> args = new java.util.HashMap<>();
                args.put("x", cx);
                args.put("y", cy);
                driver.executeScript("mobile: clickGesture", args);
            } catch (Exception ignored) {
                tapW3C(cx, cy);
            }
            takeScreenshot();
            return;
        }

        org.junit.jupiter.api.Assertions.fail("No se encontró ningún asiento disponible en España");
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
        if (!isVisibleQuick(AUMENTAR_BOLETO_ESTANDAR)) {
            scrollSlowDownThenUpUntilVisible(AUMENTAR_BOLETO_ESTANDAR, 4);
        }
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

    // ─────────────────────────────────────────────────────────────────────
    // PASE ANUAL / FOLIO en pantalla "Selecciona tus boletos"
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Activa la pestaña de Pase Anual / Folio en la pantalla de boletos.
     * Prueba múltiples locators porque el ícono puede variar por versión de app.
     */
    public void seleccionarTabPaseAnual() {
        log.info("[PaseAnual] Activando pestaña de Pase Anual / Folio");

        // Intento 1: content-desc explícito
        String[] descCandidatos = {
            "Pase Anual", "Folio", "folio", "pase", "Pase", "Pass", "Cinépolis Pass"
        };
        for (String desc : descCandidatos) {
            try {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(0));
                java.util.List<org.openqa.selenium.WebElement> els =
                    driver.findElements(org.openqa.selenium.By.xpath(
                        "//*[@content-desc='" + desc + "' or @text='" + desc + "']"));
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
                if (!els.isEmpty()) {
                    els.get(0).click();
                    sleep(500);
                    log.info("[PaseAnual] Pestaña activada con: {}", desc);
                    return;
                }
            } catch (Exception e) {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            }
        }

        // Intento 2: última pestaña en la fila de promociones (Mastercard | Folios | Pase)
        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(0));
            java.util.List<org.openqa.selenium.WebElement> tabs =
                driver.findElements(org.openqa.selenium.By.xpath(
                    "//android.widget.TextView[@text='Mastercard 2x1']/../../.." +
                    "//android.view.View[@clickable='true' or ancestor-or-self::*[@clickable='true']]"));
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            if (!tabs.isEmpty()) {
                tabs.get(tabs.size() - 1).click();
                sleep(500);
                log.info("[PaseAnual] Pestaña activada por posición (última)");
                return;
            }
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        }

        // Intento 3: si el input de folio ya es visible, la pestaña ya está activa
        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));
            driver.findElement(org.openqa.selenium.By.xpath("//android.widget.EditText"));
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            log.info("[PaseAnual] Input folio ya visible — pestaña activa");
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            log.warn("[PaseAnual] No se pudo activar pestaña Pase Anual — continuando");
        }
    }

    /**
     * Ingresa el folio del Pase Anual en el campo de texto correspondiente.
     * Prueba distintos locators para mayor robustez.
     */
    public void ingresarFolioPaseAnual(String folio) {
        log.info("[PaseAnual] Ingresando folio: {}", folio);

        org.openqa.selenium.By[] candidatos = {
            org.openqa.selenium.By.xpath("//android.widget.EditText"),
            org.openqa.selenium.By.xpath(
                "//*[contains(@text,'0000') or @hint='Ingresa tu folio'" +
                " or contains(@hint,'folio') or contains(@hint,'Folio')]"),
            org.openqa.selenium.By.xpath(
                "//*[@class='android.widget.EditText']")
        };

        org.openqa.selenium.WebElement input = null;
        for (org.openqa.selenium.By loc : candidatos) {
            try {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(3));
                input = driver.findElement(loc);
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
                break;
            } catch (Exception e) {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            }
        }

        if (input == null) {
            throw new AssertionError(
                "No se encontró el campo de folio en la pantalla de boletos");
        }

        input.clear();
        input.sendKeys(folio);
        sleep(300);
        log.info("[PaseAnual] Folio ingresado correctamente");
    }

    /** Presiona el botón "Aplicar" del folio. */
    public void clickAplicarFolio() {
        log.info("[PaseAnual] Presionando Aplicar");
        org.openqa.selenium.By[] candidatos = {
            org.openqa.selenium.By.xpath(
                "//android.widget.TextView[@text='Aplicar']" +
                "/following-sibling::android.widget.Button"),
            org.openqa.selenium.By.xpath(
                "//android.widget.TextView[@text='Aplicar']" +
                "/../android.widget.Button"),
            org.openqa.selenium.By.xpath(
                "//android.widget.Button[@text='Aplicar']"),
            org.openqa.selenium.By.xpath(
                "//android.widget.TextView[@text='Aplicar']")
        };
        for (org.openqa.selenium.By loc : candidatos) {
            try {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(3));
                driver.findElement(loc).click();
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
                sleep(800);
                log.info("[PaseAnual] Aplicar presionado correctamente");
                return;
            } catch (Exception e) {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            }
        }
        throw new AssertionError("No se encontró el botón Aplicar del folio");
    }

    /**
     * Valida que el folio se aplicó sin errores.
     * Busca mensajes de error; si no hay ninguno, asume éxito (smoke behavior).
     */
    public void validarFolioAplicado() {
        log.info("[PaseAnual] Validando resultado del folio");
        sleep(1000);
        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(3));
            java.util.List<org.openqa.selenium.WebElement> errores =
                driver.findElements(org.openqa.selenium.By.xpath(
                    "//*[contains(@text,'inválido') or contains(@text,'Inválido') " +
                    "or contains(@text,'incorrecto') or contains(@text,'error') " +
                    "or contains(@text,'Error') or contains(@text,'no válido')]"));
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));

            if (!errores.isEmpty()) {
                String msg = errores.get(0).getText();
                log.error("[PaseAnual] Error al aplicar folio: {}", msg);
                throw new AssertionError(
                    "El folio fue rechazado por la aplicación: " + msg);
            }
            log.info("[PaseAnual] Folio aplicado sin errores detectados");
        } catch (AssertionError ae) {
            throw ae;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
            log.info("[PaseAnual] Validación completada (sin errores detectados)");
        }
    }

}
