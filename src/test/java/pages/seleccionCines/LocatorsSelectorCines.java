package pages.seleccionCines;

import org.openqa.selenium.By;

public class LocatorsSelectorCines {

//SELECTOR DE CINES
    public static final By TAB_CARTELERA = By.xpath("//android.widget.TextView[@text='Cartelera']");
    public static final By SELECTOR_CINES = By.xpath("//android.view.View[@content-desc=\"Selecciona uno o más cines\"]");
    public static final By TAB_HORARIOS = By.xpath("//android.widget.TextView[@text='Horarios']");
    public static final By TAB_HORARIOS_SELECCIONADO = By.xpath("//android.view.View[@checked='true' and android.widget.TextView[@text='Horarios']]");
    public static final By BUSCADOR_CIUDAD = By.xpath("//android.widget.EditText/android.view.View[3]");
    public static final By BUSCADOR_CIUDAD_INPUT = By.xpath("//android.widget.EditText");
    public static final By CIUDAD_MORELIA = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[2]");
    public static final By CINE_ESCALA_MORELIA = By.xpath("//android.widget.TextView[@text='Escala Morelia']");
    public static final By CINE_MORELIA_VIP = By.xpath("//android.view.View[android.view.View/android.widget.TextView[@text='Espacio Las Américas'] and count(android.view.View) = 2]");
    public static final By CINE_ATMOSFERA_CUMBRES = By.xpath("//android.widget.TextView[@text=\"Cumbres Monterrey\"]");
    public static final By ACEPTAR_CAMBIAR_CIUDAD = By.xpath("//android.widget.TextView[@text='Aceptar']");
    public static final By RECHAZAR_CAMBIAR_CIUDAD = By.xpath("//android.widget.TextView[@text=\"Cancelar\"]");
    public static final By TAB_CINE_ESCALA_MORELIA = By.xpath("//android.widget.TextView[@text='Escala Morelia']");
    public static final By TAB_CINE_CUMBRES = By.xpath("//android.widget.TextView[@text='Cumbres Monterrey']");
    public static final By TAB_CINE_VIP_MORELIA = By.xpath("//android.view.View[.//android.widget.TextView[@text='Espacio Las Américas'] and .//android.widget.TextView[@text='VIP']]");
    public static final By BOTON_CERRAR_TAB_CINE = By.xpath("(//android.view.View[child::android.widget.Button and child::android.widget.CheckBox]/android.widget.Button)[1]");
    public static final By BOTON_APLICAR_SELECCION = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.widget.Button");
    public static final By RECHAZAR_PERMISOS_UBICACIÓN = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.widget.Button");
    public static final By ACEPTAR_PERMISOS_UBICACION = By.xpath("//android.view.ViewGroup/android.view.View/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.widget.Button");
    public static final By CONCEDER_PERMISOS_UBICACION_NATIVO = By.xpath("//android.widget.Button[@resource-id=\"com.android.permissioncontroller:id/permission_allow_foreground_only_button\"]");
    public static final By DENEGAR_PERMISOS_UBICACION_NATIVO = By.xpath("//android.widget.Button[@resource-id=\"com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button\"]");
    public static final By CERRAR_ALERTA_LOCALIZACIÓN = By.xpath("//android.widget.TextView[@text=\"No cambiar\"]");
    public static final By SELECCIONAR_TODOS_LOS_CINES = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[1]");
    public static final By CINE_VIP_UNIVERSAL = By.xpath("//android.widget.TextView[@text=\"VIP Diana Acapulco\"]");
    public static final By TAB_CINE_VIP = By.xpath("//android.view.View[.//android.widget.TextView[@text='VIP Diana Acapulco'] and .//android.widget.TextView[@text='VIP']]");
    public static final By OPCION_NO_CAMBIAR_ZONA = By.xpath("//android.widget.TextView[@text=\"No cambiar\"]");



//LOCALIZADORES ARGENTINA

    public static final By CIUDAD_BUENOS_AIRES = By.xpath("//android.widget.TextView[@text=\"Buenos Aires\"]");
    public static final By CIUDAD_MENDOZA = By.xpath("//android.widget.TextView[@text=\"Mendoza\"]");
    public static final By CIUDAD_NEUQUEN = By.xpath("//android.widget.TextView[@text=\"Neuquen\"]");
    public static final By CIUDAD_SANTA_FE = By.xpath("//android.widget.TextView[@text=\"Santa Fe\"]");
   
    public static final By CINE_AVELLANEDA = By.xpath("//android.widget.TextView[@text=\"Cinépolis Avellaneda\"]");
    public static final By CINE_LUJAN = By.xpath("//android.widget.TextView[@text=\"Cinépolis Lujan\"]");
    public static final By CINE_MERLO = By.xpath("//android.widget.TextView[@text=\"Cinépolis Merlo\"]");
    public static final By CINE_PILAR = By.xpath("//android.widget.TextView[@text=\"Cinépolis Pilar\"]");
    public static final By CINE_PLAZA_HOUSSAY = By.xpath("//android.widget.TextView[@text=\"Cinépolis Plaza Houssay\"]");
    public static final By CINE_RECOLETA = By.xpath("//android.widget.TextView[@text=\"Recoleta\"]");
    public static final By CINE_ARENA_MAIPU = By.xpath("//android.widget.TextView[@text=\"Cinépolis Arena Maipu\"]");
    public static final By CINE_MENDOZA_PLAZA = By.xpath("//android.widget.TextView[@text=\"Cinépolis Mendoza Plaza\"]");
    public static final By CINE_NEUQUEN = By.xpath("//android.widget.TextView[@text=\"Cinépolis Neuquen\"]");
    public static final By CINE_ROSARIO = By.xpath("//android.widget.TextView[@text=\"Cinépolis Rosario\"]");

    public static final By TAB_CINE_AVELLANEDA = By.xpath("//android.widget.TextView[@text=\"Cinépolis Avellaneda\"]");
    public static final By TAB_CINE_LUJAN = By.xpath("//android.widget.TextView[@text=\"Cinépolis Lujan\"]");
    public static final By TAB_CINE_MERLO = By.xpath("//android.widget.TextView[@text=\"Cinépolis Merlo\"]");
    public static final By TAB_CINE_PILAR = By.xpath("//android.widget.TextView[@text=\"Cinépolis Pilar\"]");
    public static final By TAB_CINE_PLAZA_HOUSSAY = By.xpath("//android.widget.TextView[@text=\"Cinépolis Plaza Houssay\"]");
    public static final By TAB_CINE_RECOLETA = By.xpath("//android.widget.TextView[@text=\"Recoleta\"]");
    public static final By TAB_CINE_ARENA_MAIPU = By.xpath("//android.widget.TextView[@text=\"Cinépolis Arena Maipu\"]");
    public static final By TAB_CINE_MENDOZA_PLAZA = By.xpath("//android.widget.TextView[@text=\"Cinépolis Mendoza Plaza\"]");
    public static final By TAB_CINE_NEUQUEN = By.xpath("//android.widget.TextView[@text=\"Cinépolis Neuquen\"]");
    public static final By TAB_CINE_ROSARIO = By.xpath("//android.widget.TextView[@text=\"Cinépolis Rosario\"]");



//LOCALIZADORES CHILE

    public static final By CIUDAD_SANTIAGO_ORIENTE = By.xpath("//android.widget.TextView[@text=\"Santiago Oriente\"]");

    public static final By CINE_LA_REINA = By.xpath("//android.widget.TextView[@text=\"La Reina\"]");
    public static final By CINE_PARQUE_ARAUCO_PREMIUM = By.xpath("//android.widget.TextView[@text=\"Parque Arauco Premium\"]");
    public static final By CINE_LOS_DOMINICOS = By.xpath("//android.widget.TextView[@text=\"Los Dominicos\"]");
    public static final By CINE_ARAUCO = By.xpath("//android.widget.TextView[@text=\"Parque Arauco\"]");
    public static final By CINE_MALLPLAZA_ANTOFAGASTA = By.xpath("//android.widget.TextView[@text=\"Mallplaza Antofagasta\"]");
    public static final By CINE_DOMINICOS_PREMIUM = By.xpath("//android.widget.TextView[@text=\"Mall Plaza Los Dominicos Premium\"]");

    public static final By TAB_CINE_LOS_DOMINICOS = By.xpath("//android.widget.TextView[@text=\"Los Dominicos\"]");
    public static final By TAB_CINE_LA_REINA = By.xpath("//android.widget.TextView[@text=\"La Reina\"]");
    public static final By TAB_CINE_ANTOFAGASTA = By.xpath("//android.widget.TextView[@text=\"Mallplaza Antofagasta\"]");
    public static final By TAB_CINE_PARQUE_ARAUCO = By.xpath("//android.widget.TextView[@text=\"Parque Arauco\"]");
    public static final By TAB_CINE_PARQUE_ARAUCO_PREMIUM = By.xpath("//android.widget.TextView[@text=\"Parque Arauco Premium\"]");


//LOCALIZADORES ESPAÑA

    public static final By CINE_PLENILUNIO = By.xpath("//android.widget.TextView[@text=\"Plenilunio\"]");
    public static final By CINE_PARQUE_CORREDOR = By.xpath("//android.widget.TextView[@text=\"Premium Parque Corredor\"]");
    public static final By CINE_TRES_AGUAS = By.xpath("//android.widget.TextView[@text=\"TresAguas\"]");
    public static final By CINE_PLAZA_NORTE = By.xpath("//android.widget.TextView[@text=\"Plaza Norte 2\"]");
    public static final By CINE_PALAFOX_LUXURY = By.xpath("//android.widget.TextView[@text=\"Palafox Luxury\"]");

    public static final By TAB_CINE_PLENILUNIO = By.xpath("//android.widget.TextView[@text=\"Plenilunio\"]");
    public static final By TAB_CINE_PARQUE_CORREDOR = By.xpath("//android.widget.TextView[@text=\"Premium Parque Corredor\"]");
    public static final By TAB_CINE_TRES_AGUAS = By.xpath("//android.widget.TextView[@text=\"TresAguas\"]");
    public static final By TAB_CINE_PLAZA_NORTE = By.xpath("//android.widget.TextView[@text=\"Plaza Norte 2\"]");
    public static final By TAB_CINE_PALAFOX_LUXURY = By.xpath("//android.widget.TextView[@text=\"Palafox Luxury\"]");

}
