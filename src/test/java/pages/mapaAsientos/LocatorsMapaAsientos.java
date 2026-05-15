package pages.mapaAsientos;
import org.openqa.selenium.By;

public class LocatorsMapaAsientos {

//MAPA DE ASIENTOS
    public static final By OVERLAY_MAPA_ASIENTOS = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View");
    public static final By PRIMER_ASIENTO_DISPONIBLE = By.xpath("(//android.widget.TextView[contains(@text,'Pantalla')]/following::android.widget.TextView[string(number(@text)) != 'NaN' and @enabled='true']/..)[1]");
    // España — cubre dos tipos de sala:
    //   · Regular : filas numéricas (1,2,3…), asientos con número
    //   · Premium : filas con letras (A,B,C…), asientos con etiqueta "PR"
    // Busca el View[@enabled='true'] que contiene directamente un TextView
    // numérico O con texto "PR", evitando contenedores de etiqueta de fila.
    public static final By PRIMER_ASIENTO_DISPONIBLE_ESPANA = By.xpath(
        "(//android.widget.TextView[contains(@text,'Pantalla')]" +
        "/following::android.view.View[@enabled='true' and (" +
            "android.widget.TextView[string(number(@text)) != 'NaN'] or " +
            "android.widget.TextView[@text='PR']" +
        ")])[1]");
    public static final By PANTALLA_MAPA = By.xpath("//android.widget.TextView[contains(@text, 'Pantalla')]");
    public static final By BOTON_CONTINUAR_MAPA = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.view.View[2]/android.widget.Button");
    public static final By TAB_SIGUIENTE_HORARIO = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.view.View[2]");
    public static final By PRIMERA_FILA = By.xpath("//android.widget.TextView[@text=\"A\"]");


//SELECTOR DE BOLETOS
    public static final By AUMENTAR_BOLETO_ESTANDAR = By.xpath("(//android.view.View[@content-desc=\"Añadir\"])[1]");
    public static final By DISMINUIR_BOLETO_ESTANDAR = By.xpath("(//android.view.View[@content-desc=\"Menos\"])[1]");
    public static final By AUMENTAR_BOLETO_NIÑO = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[5]/android.view.View[2]/android.widget.Button");
    public static final By DISMINUIR_BOLETO_NIÑO = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[5]/android.view.View[1]/android.widget.Button");
    public static final By AUMENTAR_BOLETO_3AEDAD = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[6]/android.view.View[2]/android.widget.Button");
    public static final By DISMINUIR_BOLETO_3AEDAD = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[6]/android.view.View[1]/android.widget.Button");
    public static final By BOTON_CONTINUAR_BOLETOS = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[3]/android.widget.Button");




}
