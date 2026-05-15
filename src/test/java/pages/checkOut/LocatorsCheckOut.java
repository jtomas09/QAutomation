package pages.checkOut;

import org.openqa.selenium.By;

public class LocatorsCheckOut {

//CHECKOUT
    public static final By ENCABEZADO_CHECKOUT       = By.xpath("//android.widget.TextView[@text=\"Pago\"]");
    public static final By SUBTOTAL_CHECKOUT         = By.xpath("//android.widget.TextView[@text='Subtotal']/following-sibling::android.widget.TextView[1]");
    public static final By CARGO_SERVICIO_CHECKOUT   = By.xpath("//android.widget.TextView[@text='Cargo por servicio']/following-sibling::android.widget.TextView[1]");
    public static final By TOTAL_CHECKOUT            = By.xpath("//android.widget.TextView[@text='Total']/following-sibling::android.widget.TextView[1]");
    public static final By COPY_DATOS_PERSONALES = By.xpath("//android.widget.TextView[@text=\"Datos personales\"]");
    public static final By INPUT_NOMBRE_CHECKOUT = By.xpath("//android.widget.ScrollView/android.widget.EditText[1]");
    public static final By INPUT_APELLIDO_CHECKOUT = By.xpath("//android.widget.ScrollView/android.widget.EditText[2]");
    public static final By INPUT_CORREO_CHECKOUT = By.xpath("//android.widget.ScrollView/android.widget.EditText[3]");
    public static final By INPUT_TELEFONO_CHECKOUT = By.xpath("//android.widget.ScrollView/android.widget.EditText[4]");

    // España: disclaimer de política de cines (ej. Cine Yelmo "Atención")
    public static final By ALERTA_ATENCION_ESPANA   = By.xpath("//android.widget.TextView[@text='Atención']");
    public static final By BOTON_ACEPTAR_DISCLAIMER = By.xpath("//android.widget.TextView[@text='Aceptar']");

    public static final By TAB_CLUB_CINEPOLIS_CHECKOUT = By.xpath("//android.widget.ScrollView/android.view.View[2]/android.widget.Button");
    public static final By TARJETA_BANCARIA_CHECKOUT = By.xpath("//android.widget.TextView[@text=\"Tarjeta de crédito o débito\"]");
    public static final By C2P_CHECKOUT = By.xpath("//android.widget.TextView[@text=\"Click to Pay\"]");
    public static final By APLAZO_CHECKOUT = By.xpath("//android.widget.TextView[@text=\"Aplazo\"]");
    public static final By PAYPAL_CHECKOUT = By.xpath("//android.widget.TextView[@text=\"PayPal\"]");





}
