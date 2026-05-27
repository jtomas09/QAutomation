package pages.alimentos;
import org.openqa.selenium.By;

public class LocatorsAlimentos {

//MENÚ DE ALIMENTOS
    public static final By INPUT_FOLIO_ALIMENTOS = By.xpath("//android.widget.TextView[@text='Ingresa tu folio']");
    public static final By BUSCADOR_ALIMENTOS = By.xpath("//android.widget.TextView[@text='Buscar']");
    public static final By INPUT_BUSCADOR_ALIMENTOS = By.xpath("//android.widget.EditText/android.view.View[3]");
    public static final By RESULTADO_EXTRA_QUESO = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.view.View/android.view.View");
    public static final By PRIMER_RESULTADO_BUSQUEDA = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[2]/android.view.View[1]/android.view.View");
    public static final By CARDS_PRODUCTOS_DISPONIBLES = By.xpath("//android.view.View[android.widget.TextView[starts-with(@text,'$') or contains(@text,'€')] and not(android.widget.TextView[@text='Agotado'])]");
    public static final By BOTON_CONTINUAR_Y_PAGAR = By.xpath("//android.widget.TextView[@text=\"Continuar e ir a pagar\"]");
    public static final By BARRA_CATEGORIAS_ALIMENTOS = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[1]/android.view.View/android.view.View[1]/android.view.View[1]/android.view.View[4]/android.view.View[2]//android.view.View[android.view.View/android.widget.TextView[@text='Destacados']]");
    public static final By BARRA_CATEGORIAS_ALIMENTOS_PROMO = By.xpath("(//android.widget.TextView[@text=\"Promociones\"])[2]");
    public static final By TAB_SNACKS = By.xpath("//android.widget.TextView[@text='Snacks']");
    public static final By PRODUCTOS_SNACKS = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[1]/android.view.View/android.view.View[1]/android.view.View[1]/android.view.View[2]");
    public static final By SECCION_SNACKS = By.xpath("(//android.widget.TextView[@text='Snacks'])[1]");
    public static final By EXTRA_QUESO = By.xpath("//android.view.View[@content-desc='Extra Queso']");
    public static final By BOTON_AGREGAR_EXTRAQUESO = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[3]/android.view.View/android.widget.Button");
    public static final By BOTON_IR_A_PAGAR = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[3]/android.view.View/android.view.View/android.widget.Button");

//ERRORES Y NAVEGACIÓN ALIMENTOS
    public static final By MENU_NO_DISPONIBLE   = By.xpath("//android.widget.TextView[@text='Menú de alimentos no disponible']");
    public static final By ALERTA_ERROR_CARRITO = By.xpath("//android.widget.TextView[contains(@text,'Lo sentimos')]");
    public static final By BOTON_ACEPTAR_ALERTA = By.xpath("//android.widget.TextView[@text='Aceptar']/../android.widget.Button");
    public static final By BOTON_IR_ATRAS_ALIMENTOS = By.xpath("//android.view.View[@content-desc='Ir atrás']");
    public static final By BOTON_CONFIRMAR_SALIR_ALIMENTOS = By.xpath("//android.widget.TextView[@text='Confirmar']/../android.widget.Button");

//DETALLE DEL PRODUCTO - PERTSONALIZACIÓN
    public static final By TEXTO_AGREGAR_CARRITO = By.xpath("//android.widget.TextView[@text='Agregar al carrito']");
    public static final By BOTON_ACCION_PRINCIPAL = By.xpath("(//android.widget.Button)[last()]");
    public static final By OPCIONES_PERSONALIZACION = By.xpath("//android.view.View[@content-desc!='' and @clickable='true']");


//VINCULACION DE ORDEN DE ALIMENTOS EN CINE VIP
    public static final By BOTON_BUSCAR_FUNCION = By.xpath("//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View/android.view.View[2]/android.widget.Button");
    public static final By BOTON_ELEGIR_MANUALMENTE = By.xpath("//android.widget.TextView[@text='Elegir función manualmente']");
    public static final By ELEGIR_PELICULA = By.xpath("//android.widget.TextView[@text='Película']");
    public static final By ELEGIR_HORA = By.xpath("//android.widget.TextView[@text='Hora']");
    public static final By ELEGIR_FILA = By.xpath("//android.widget.TextView[@text='Fila']");
    public static final By ELEGIR_NUMERO = By.xpath("//android.widget.TextView[@text='Número']");
    public static final By PRIMERA_OPCION_DESPLEGABLE = By.xpath("//android.widget.ScrollView/android.view.View[1]");
    public static final By BOTON_VINCULAR_ORDEN = By.xpath("//android.widget.TextView[@text='Buscar']");
    public static final By BOTON_CONFIRMAR_VINCULACION = By.xpath("//android.widget.TextView[@text='Confirmar']");
    public static final By ELEGIR_NUMERO_BUTACA = By.xpath("//android.widget.TextView[@text=\"Número de butaca\"]");
    public static final By BOTON_BUSCAR = By.xpath("//android.widget.TextView[@text=\"Buscar\"]");


//VINCULACION DE ORDEN DE ALIMENTOS EN CINES PREMIUM DE ESPAÑA
    public static final By TÍTULO_MODAL_VINCULACION_ESPAÑA = By.xpath("//android.widget.TextView[@text=\"¿Tienes una butaca premium?\"]");
    public static final By BOTON_LLEVAR_A_BUTACA = By.xpath("//android.widget.TextView[@text=\"Llevar a mi butaca\"]");
    public static final By BOTON_SALTAR_VINCULACION_ESPAÑA = By.xpath("//android.widget.TextView[@text=\"Saltar y pagar\"]");
    public static final By ELEGIR_NUMERO_DE_BUTACA = By.xpath("//android.widget.TextView[@text=\"Número de butaca\"]");
    public static final By PRIMERA_FILA_HABILITADA_PREMIUM = By.xpath("(//android.widget.ScrollView/android.view.View[@enabled='true' and .//android.widget.TextView])[1]");


    public static final By ALERTA_ERROR_VINCULACION = By.xpath("//android.widget.TextView[@text=\"Hubo un error al vincular tu función\"]");
    public static final By BOTON_ALERTA_ERROR_VINCULACION = By.xpath("//android.widget.Button");



}
