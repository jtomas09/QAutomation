package pages.homeCartelera;

import org.openqa.selenium.By;

public class LocatorsHome {

//HOME
    public static final By TAB_CARTELERA = By.xpath("//android.widget.TextView[@text='Cartelera']");
    public static final By TAB_HORARIOS = By.xpath("//android.widget.TextView[@text='Horarios']");
    public static final By TAB_SECCCION_PELICULAS = By.xpath("//android.widget.TextView[@text=\"Películas\"]");
    public static final By TAB_SECCION_ALIMENTOS = By.xpath("//android.widget.TextView[@text=\"Alimentos\"]");
    public static final By TAB_SECCION_ALIMENTOS_ESPAÑA = By.xpath("//android.widget.TextView[@text=\"Comida\"]");
    public static final By TAB_SECCION_CLUB_CINEPOLIS = By.xpath("//android.widget.TextView[@text=\"Club\"]");
    public static final By TAB_SECCION_MIS_COMPRAS = By.xpath("//android.widget.TextView[@text=\"Mis Compras\"]");
    public static final By TAB_SECCION_MAS = By.xpath("//android.widget.TextView[@text=\"Más\"]");
    public static final By OPCION_PERFIL_HOME = By.xpath("//android.view.View[@content-desc=\"Perfil\"]");
    public static final By OPCION_NOTIFICACIONES_HOME = By.xpath("//android.view.View[@content-desc=\"Notificaciones\"]");
    public static final By SELECTOR_CINES = By.xpath("//android.view.View[@content-desc=\"Selecciona uno o más cines\"]");



//CARTELERA EN VISTA DE HORARIOS
    public static final By PRIMER_HORARIO = By.xpath("(//android.widget.TextView[contains(@text, 'PM') or contains(@text, 'AM')])[1]");
    public static final By ALERTA_ACEPTAR_CONTINUAR = By.xpath("//android.widget.TextView[@text=\"Aceptar y continuar\"]");
    public static final By PRIMER_ASIENTO_DISPONIBLE = By.xpath("(//android.widget.TextView[string(number(@text)) != 'NaN' and @enabled='true']/..)[1]");
    public static final By PRIMER_HORARIO_ES = By.xpath("(//android.widget.TextView[contains(@text, ':') and string-length(@text) >= 4 and string-length(@text) <= 5])[1]");

    public static final By HORARIO_PRUEBA = By.xpath("//android.widget.TextView[@text=\"22:20\"]");

}
