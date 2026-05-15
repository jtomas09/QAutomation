package pages.carritoCompras;
import org.openqa.selenium.By;

public class LocatorsCarrito {

//CARRITO DE COMPRAS
    public static final By BOTON_CONTINUAR_CARRITO    = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button");
    public static final By NOMBRE_CINE_CARRITO_CUMBRES = By.xpath("//android.widget.TextView[@text=\"Cumbres Monterrey\"]");
    public static final By BADGE_ASIENTOS_CARRITO      = By.xpath("//android.widget.TextView[@text='Asientos']/following-sibling::android.widget.TextView[string(number(@text)) != 'NaN']");
    public static final By BADGE_ALIMENTOS_CARRITO     = By.xpath("//android.widget.TextView[@text='Alimentos']/following-sibling::android.widget.TextView[string(number(@text)) != 'NaN']");
    // España: "Asientos" → "Butacas", "Alimentos" → "Comida"
    public static final By BADGE_BUTACAS_CARRITO       = By.xpath("//android.widget.TextView[@text='Butacas']/following-sibling::android.widget.TextView[string(number(@text)) != 'NaN']");
    public static final By BADGE_COMIDA_CARRITO        = By.xpath("//android.widget.TextView[@text='Comida']/following-sibling::android.widget.TextView[string(number(@text)) != 'NaN']");

//TOTALES CARRITO
    public static final By SUBTOTAL_CARRITO          = By.xpath("//android.widget.TextView[@text='Subtotal']/following-sibling::android.widget.TextView[1]");
    public static final By CARGO_SERVICIO_CARRITO    = By.xpath("//android.widget.TextView[@text='Cargo por servicio']/following-sibling::android.widget.TextView[1]");
    public static final By TOTAL_CARRITO             = By.xpath("//android.widget.TextView[@text='Total']/following-sibling::android.widget.TextView[1]");

}
