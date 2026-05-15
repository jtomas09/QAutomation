package pages.perfil;
import org.openqa.selenium.By;


public class LocatorsPerfil {

//PANTALLA CLUB CINEPOLIS SIN SESIÓN INICIADA

    public static final By OPCION_REGRESAR = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.widget.Button");
    public static final By BOTON_LOGIN = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.widget.Button");
    public static final By BOTON_CREAR_CUENTA = By.xpath("//android.widget.TextView[@text=\"Crea tu cuenta\"]");
    public static final By BOTON_ACTUALIZAR_CUENTA = By.xpath("//android.widget.TextView[@text=\"Actualizar cuenta\"]");
    public static final By BOTON_CAMBIAR_PAIS = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]");
    public static final By BOTON_CAMBIAR_PAIS_DESDE_ARGENTINA = By.xpath("//android.widget.TextView[@text=\"Cambiar\"]");


//LISTADO DE PAÍSES
    public static final By CERRAR_LISTADO_PAISES    = By.xpath("//android.widget.ImageView[@content-desc=\"Close icon\"]");
    // Botón de retroceso en pantalla "Ajustes" (Argentina). En otros países el retroceso
    // es un android.widget.Button sin content-desc, cubierto por OPCION_REGRESAR.
    public static final By BOTON_IR_ATRAS_AJUSTES  = By.xpath("//android.view.View[@content-desc=\"Ir atrás\"]");
    public static final By OPCION_MEXICO = By.xpath("//android.widget.TextView[@text=\"México\"]");
    public static final By OPCION_INDIA = By.xpath("//android.widget.TextView[@text=\"India\"]");
    public static final By OPCION_ESPAÑA = By.xpath("//android.widget.TextView[@text=\"España\"]");
    public static final By OPCION_CHILE = By.xpath("//android.widget.TextView[@text=\"Chile\"]");
    public static final By OPCION_ARGENTINA = By.xpath("//android.widget.TextView[@text=\"Argentina\"]");  
    public static final By BOTON_APLICAR = By.xpath("//android.widget.TextView[@text=\"Aplicar\"]");

}
