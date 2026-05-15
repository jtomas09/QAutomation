package pages.checkOut;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.carritoCompras.SelectorsCarrito;
import pages.common.BasePage;
import static pages.checkOut.LocatorsCheckOut.*;

public class SelectorsCheckOut extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    // ── Coordenadas de la pasarela de pago (% del alto de pantalla) ──────────
    // Calibradas para Pixel 3a XL (1080x2160). Ajustar si se cambia dispositivo.
    private static final double Y_CAMPO_NOMBRE_TITULAR    = 0.32;
    private static final double Y_CAMPO_NUMERO_TARJETA    = 0.40;
    private static final double Y_CAMPO_FECHA_VENCIMIENTO = 0.50;
    private static final double Y_CAMPO_CVV               = 0.59;
    private static final double Y_BOTON_PAGAR             = 0.75;

    // ── Datos de tarjeta para pruebas ─────────────────────────────────────────
    private static final String TARJETA_NOMBRE_TITULAR    = "Pruebas IA";
    private static final String TARJETA_NUMERO            = "4111111111111111";
    private static final String TARJETA_FECHA_VENCIMIENTO = "12/26";
    private static final String TARJETA_CVV               = "123";

    public SelectorsCheckOut(AndroidDriver driver) {
        super(driver);
    }


    public void validarPantallaCheckout() {
        validarElementoVisible(ENCABEZADO_CHECKOUT);
    }


    public void llenarDatosPersonales() {
        this.click(INPUT_NOMBRE_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "Pruebas"));
        this.click(INPUT_APELLIDO_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "IA Interactive"));
        driver.hideKeyboard();
        this.click(INPUT_CORREO_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "cinepolispayments@ia.com.mx"));
        driver.hideKeyboard();
    }

    public void llenarDatosPersonalesEspana() {
        aceptarDisclaimerSiPresente();
        waitForVisibility(COPY_DATOS_PERSONALES);
        this.click(INPUT_NOMBRE_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "Pruebas"));
        this.click(INPUT_APELLIDO_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "IA Interactive"));
        driver.hideKeyboard();
        this.click(INPUT_CORREO_CHECKOUT);
        driver.executeScript("mobile: type", Map.of("text", "cinepolispayments@ia.com.mx"));
        driver.hideKeyboard();
        if (isVisibleQuick(INPUT_TELEFONO_CHECKOUT)) {
            this.click(INPUT_TELEFONO_CHECKOUT);
            driver.executeScript("mobile: type", Map.of("text", "600000000"));
            driver.hideKeyboard();
        }
    }

    // Acepta el disclaimer de política de cine (ej. aviso de Cine Yelmo) si aparece
    // antes de la pantalla de datos personales tras continuar desde el carrito.
    private void aceptarDisclaimerSiPresente() {
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite) {
            if (isVisibleQuick(ALERTA_ATENCION_ESPANA)) {
                Allure.step("ℹ️ Disclaimer de cine detectado – aceptando para continuar");
                clickIfPresent(BOTON_ACEPTAR_DISCLAIMER);
                return;
            }
            if (isVisibleQuick(COPY_DATOS_PERSONALES)) return;
            sleep(400);
        }
    }

    public void validarTotalNoVacio() {
        for (int i = 0; i < 8; i++) {
            if (isVisibleQuick(TOTAL_CHECKOUT)) break;
            slowSwipeUp();
        }
        validarElementoVisible(TOTAL_CHECKOUT);
        String total = driver.findElement(TOTAL_CHECKOUT).getText();
        org.junit.jupiter.api.Assertions.assertTrue(
            total != null && total.length() > 1 && (total.contains("$") || total.contains("€")),
            "El total de la orden está vacío o no tiene formato válido: " + total
        );
    }

    public void validarTotalesCheckout() {
        for (int i = 0; i < 8; i++) {
            if (isVisibleQuick(TOTAL_CHECKOUT)) break;
            slowSwipeUp();
        }
        validarElementoVisible(TOTAL_CHECKOUT);

        String subtotal = leerTexto(SUBTOTAL_CHECKOUT);
        String cargo    = leerTexto(CARGO_SERVICIO_CHECKOUT);
        String total    = leerTexto(TOTAL_CHECKOUT);

        org.junit.jupiter.api.Assertions.assertTrue(
            total != null && total.length() > 1 && (total.contains("$") || total.contains("€")),
            "El total en checkout está vacío o no tiene formato válido: " + total
        );

        String linea = "Subtotal: " + subtotal + " | Cargo por servicio: " + cargo + " | Total: " + total;
        System.out.println("[TOTALES-CHECKOUT] " + linea);
        Allure.step("💰 Totales en checkout – " + linea);

        Map<String, String> carrito = SelectorsCarrito.getTotalesCapturados();
        if (carrito.isEmpty()) return;

        List<String> discrepancias = new ArrayList<>();
        verificarCampo("Subtotal",           subtotal, carrito, discrepancias);
        verificarCampo("Cargo por servicio", cargo,    carrito, discrepancias);
        verificarCampo("Total",              total,    carrito, discrepancias);

        if (discrepancias.isEmpty()) {
            Allure.step("✅ Totales coinciden entre carrito y checkout");
        } else {
            String detalle = String.join(" | ", discrepancias);
            System.out.println("[DISCREPANCIA-TOTALES] " + detalle);
            Allure.step("❌ Discrepancias en totales carrito vs checkout: " + detalle);
            org.junit.jupiter.api.Assertions.fail(
                "Los totales no coinciden entre carrito y checkout: " + detalle);
        }
    }

    private void verificarCampo(String campo, String valorCheckout,
                                 Map<String, String> totalesCarrito,
                                 List<String> discrepancias) {
        String valorCarrito = totalesCarrito.getOrDefault(campo, "—");
        if (!"—".equals(valorCarrito) && !valorCheckout.equals(valorCarrito)) {
            discrepancias.add(campo + ": carrito='" + valorCarrito
                + "' vs checkout='" + valorCheckout + "'");
        }
    }

    private String leerTexto(By locator) {
        try { return driver.findElement(locator).getText(); } catch (Exception e) { return "—"; }
    }

    public void validarOrdenGeneradaCorrectamente() {
        validarPantallaCheckout();
        validarTotalesCheckout();
        Allure.step("✅ La orden se generó correctamente y permite llegar hasta el checkout");
    }

    public void pagarConTarjetaBancaria() {
        driver.hideKeyboard();
        ensureVisibleNoClick(TARJETA_BANCARIA_CHECKOUT, FAST_VISIBLE_SECONDS);
        this.click(TARJETA_BANCARIA_CHECKOUT);
        sleep(3000);

        org.openqa.selenium.Dimension screen = driver.manage().window().getSize();
        int x = screen.getWidth() / 2;
        int h = screen.getHeight();

        tapW3C(x, (int)(h * Y_CAMPO_NOMBRE_TITULAR));
        driver.executeScript("mobile: type", Map.of("text", TARJETA_NOMBRE_TITULAR));
        driver.hideKeyboard();

        tapW3C(x, (int)(h * Y_CAMPO_NUMERO_TARJETA));
        typeCaracterPorCaracter(TARJETA_NUMERO);
        driver.hideKeyboard();

        tapW3C(x, (int)(h * Y_CAMPO_FECHA_VENCIMIENTO));
        typeCaracterPorCaracter(TARJETA_FECHA_VENCIMIENTO);
        driver.hideKeyboard();

        tapW3C(x, (int)(h * Y_CAMPO_CVV));
        typeCaracterPorCaracter(TARJETA_CVV);
        driver.hideKeyboard();

        tapW3C(x, (int)(h * Y_BOTON_PAGAR));
    }

    public void pagarConC2P() {
        driver.hideKeyboard();
        ensureVisibleNoClick(C2P_CHECKOUT, FAST_VISIBLE_SECONDS);
        this.click(C2P_CHECKOUT);
        sleep(3000);

    }

    public void pagarConAplazo() {
        driver.hideKeyboard();
        ensureVisibleNoClick(APLAZO_CHECKOUT, FAST_VISIBLE_SECONDS);
        this.click(APLAZO_CHECKOUT);
        sleep(3000);

    }

    public void pagarConPaypal() {
        driver.hideKeyboard();
        ensureVisibleNoClick(PAYPAL_CHECKOUT, FAST_VISIBLE_SECONDS);
        this.click(PAYPAL_CHECKOUT);
        sleep(3000);

    }

    private void typeCaracterPorCaracter(String texto) {
        for (char c : texto.toCharArray()) {
            driver.executeScript("mobile: type", Map.of("text", String.valueOf(c)));
            sleep(120);
        }
    }


}
