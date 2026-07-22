package pages.alimentos;

import org.openqa.selenium.By;
import pages.common.PlatformLocator;

/**
 * Locators centralizados para todas las pantallas de Alimentos.
 * Fusiona el contenido de LocatorsAlimentos (deprecada) en esta clase canónica.
 * Usa By directamente para evitar envolver con By.xpath() en los tests.
 *
 * NOTA-MIGRACION (auditoría multiplataforma): de todas las constantes de este
 * archivo, SOLO BTN_REGRESARMENU, BTN_ALIMENTOS_ICON, BTN_GRANDE_CALIENTE,
 * TXT_TE_CALIENTE_CARRITO y TXT_TE_VARIANTE_CARRITO se usan realmente en el
 * proyecto (vía page.validarElementoVisible(...) en los tests Menu*.java y en
 * flujos/AlimentosFlujo.java) — se migraron a PlatformLocator. El resto de las
 * constantes de este archivo (BTN_PERSONALIZAR, BTN_SIGUIENTE, BTN_JUMBO, todas
 * las de "Bebidas y sabores"/"Snacks y nachos"/"Postres"/"Vinculación VIP", etc.)
 * NO tiene ninguna referencia en el código — quedaron como By de Android sin
 * tocar. Se recomienda eliminarlas en una limpieza aparte (ver reporte).
 */
public final class AlimentosLocators {

    private AlimentosLocators() {}

    // ─── Navegación general ───────────────────────────────────────────────────

    public static final By BTN_REGRESO = By.xpath(
        "//android.view.ViewGroup/android.view.View/android.view.View/android.view.View" +
        "/android.view.View/android.view.View/android.widget.Button");

    // NOTA-MIGRACION: locator Android 100% posicional, sin ancla de texto — el
    // equivalente iOS ("Alimentos" por texto visible, misma ancla que
    // SelectorPage.TAB_ALIMENTOS_BOTTOMNAV) es mejor-esfuerzo sin verificar en
    // dispositivo real.
    public static final PlatformLocator BTN_ALIMENTOS_ICON = PlatformLocator.of(
        By.xpath(
            "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[2]" +
            "/android.view.View/android.view.View[2]/android.view.View[3]"),
        PlatformLocator.byExactText("Alimentos").ios());

    // NOTA-MIGRACION: locator Android 100% posicional, sin ancla de texto — el
    // equivalente iOS ("último botón de la pantalla") es mejor-esfuerzo, requiere
    // verificación en dispositivo real (ver PlatformLocator.lastActionButton()).
    public static final PlatformLocator BTN_REGRESARMENU = PlatformLocator.of(
        By.xpath(
            "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
            "/android.view.View/android.view.View[2]/android.view.View/android.view.View[1]/android.widget.Button"),
        PlatformLocator.lastActionButton().ios());

    // ─── Acciones de producto ─────────────────────────────────────────────────

    public static final By BTN_PERSONALIZAR = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button");

    public static final By BTN_SIGUIENTE = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View[2]/android.view.View/android.widget.Button");

    public static final By BTN_AGREGAR_CARRITO = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View/android.view.View/android.widget.Button");

    public static final By BTN_CARRITO = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[1]" +
        "/android.view.View/android.view.View[2]/android.view.View/android.view.View[5]");

    public static final By BTN_MAS = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View[2]/android.widget.Button");

    // ─── Menú / Búsqueda ──────────────────────────────────────────────────────

    public static final By INPUT_FOLIO_ALIMENTOS   = By.xpath("//android.widget.TextView[@text='Ingresa tu folio']");
    public static final By BUSCADOR_ALIMENTOS      = By.xpath("//android.widget.TextView[@text='Buscar']");
    public static final By INPUT_BUSCADOR_ALIMENTOS= By.xpath("//android.widget.EditText/android.view.View[3]");
    public static final By RESULTADO_EXTRA_QUESO   = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[2]/android.view.View/android.view.View");
    public static final By PRIMER_RESULTADO_BUSQUEDA = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[2]/android.view.View[1]/android.view.View");

    public static final By CARDS_PRODUCTOS_DISPONIBLES = By.xpath(
        "//android.view.View[android.widget.TextView[starts-with(@text,'$')] " +
        "and not(android.widget.TextView[@text='Agotado'])]");

    public static final By BARRA_CATEGORIAS_ALIMENTOS = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[1]" +
        "/android.view.View/android.view.View[1]/android.view.View[1]/android.view.View[4]" +
        "/android.view.View[2]//android.view.View[android.view.View/android.widget.TextView[@text='Destacados']]");
    public static final By BARRA_CATEGORIAS_ALIMENTOS_PROMO = By.xpath(
        "(//android.widget.TextView[@text=\"Promociones\"])[2]");

    public static final By TAB_SNACKS       = By.xpath("//android.widget.TextView[@text='Snacks']");
    public static final By PRODUCTOS_SNACKS = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[1]" +
        "/android.view.View/android.view.View[1]/android.view.View[1]/android.view.View[2]");
    public static final By SECCION_SNACKS   = By.xpath("(//android.widget.TextView[@text='Snacks'])[1]");
    public static final By EXTRA_QUESO      = By.xpath("//android.view.View[@content-desc='Extra Queso']");

    // ─── Carrito ──────────────────────────────────────────────────────────────

    public static final By BOTON_CONTINUAR_Y_PAGAR = By.xpath(
        "//android.widget.TextView[@text=\"Continuar e ir a pagar\"]");
    public static final By BOTON_AGREGAR_EXTRAQUESO = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View[3]/android.view.View/android.widget.Button");
    public static final By BOTON_IR_A_PAGAR = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View[3]/android.view.View/android.view.View/android.widget.Button");

    // ─── Errores y navegación ─────────────────────────────────────────────────

    public static final By MENU_NO_DISPONIBLE        = By.xpath("//android.widget.TextView[@text='Menú de alimentos no disponible']");
    public static final By ALERTA_ERROR_CARRITO      = By.xpath("//android.widget.TextView[contains(@text,'Lo sentimos')]");
    public static final By BOTON_ACEPTAR_ALERTA      = By.xpath("//android.widget.TextView[@text='Aceptar']/../android.widget.Button");
    public static final By BOTON_IR_ATRAS_ALIMENTOS  = By.xpath("//android.view.View[@content-desc='Ir atrás']");
    public static final By BOTON_CONFIRMAR_SALIR     = By.xpath("//android.widget.TextView[@text='Confirmar']/../android.widget.Button");
    public static final By TEXTO_AGREGAR_CARRITO     = By.xpath("//android.widget.TextView[@text='Agregar al carrito']");
    public static final By OPCIONES_PERSONALIZACION  = By.xpath("//android.view.View[@content-desc!='' and @clickable='true']");
    public static final By BOTON_ACCION_PRINCIPAL    = By.xpath("(//android.widget.Button)[last()]");

    // ─── Menú VIP ─────────────────────────────────────────────────────────────

    public static final By BTN_PARALLEVAR    = By.xpath("//android.widget.TextView[@text=\"Para Llevar\"]");
    public static final By BTN_GRANDES       = By.xpath("//android.widget.TextView[@text=\"Grandes\"]");
    public static final By BTN_MEDIANAS      = By.xpath("//android.widget.TextView[@text=\"Medianas\"]");
    public static final By BTN_CHICAS        = By.xpath("//android.widget.TextView[@text=\"Chicas\"]");
    public static final By BTN_CHICAS2       = By.xpath("//android.widget.TextView[@text=\"Chicos\"]");
    public static final By BTN_RES           = By.xpath("//android.widget.TextView[@text=\"Res\"]");
    public static final By BTN_BONELESS      = By.xpath("//android.widget.TextView[@text=\"Boneless\"]");
    public static final By BTN_6OZ           = By.xpath("//android.widget.TextView[@text=\"6 Oz\"]");
    public static final By BTN_NACHOSBONELESS= By.xpath("//android.widget.TextView[@text=\"Nachos Boneless\"]");
    public static final By BTN_NACHOSBRISKET = By.xpath("//android.widget.TextView[@text=\"Nachos Brisket de Res\"]");
    public static final By BTN_EXTRAQUESO    = By.xpath("//android.widget.TextView[@text=\"Extra Queso\"]");
    public static final By BTN_CHICO         = By.xpath("//android.widget.TextView[@text=\"Chico\"]");
    public static final By BTN_GUACAMOLE     = By.xpath("//android.widget.TextView[@text=\"Guacamole\"]");
    public static final By BTN_REFRESCOGRANDE= By.xpath("//android.widget.TextView[@text=\"Grande\"]");
    public static final By BTN_REFRESCOMEDIANO=By.xpath("//android.widget.TextView[@text=\"Mediano\"]");
    public static final By BTN_TEXASDOG      = By.xpath("//android.widget.TextView[@text=\"Texas Dog\"]");

    // ─── Bebidas y sabores ────────────────────────────────────────────────────

    public static final By BTN_JUMBO         = By.xpath("//android.view.View[@content-desc=\"Jumbo\"]");
    public static final By BTN_FRAPPE        = By.xpath("//android.view.View[@content-desc=\"Frappé Premium\"]");
    public static final By BTN_FRAPPEGRANDE  = By.xpath("//android.view.View[@content-desc=\"Grande\"]");
    public static final By BTN_FRAPPECOOKIES = By.xpath("//android.widget.TextView[@text=\"Cookies & Cream\"]");
    public static final By BTN_CARAMELO      = By.xpath("//android.widget.TextView[@text=\"Caramelo\"]");
    public static final By BTN_LIGTH         = By.xpath("//android.widget.TextView[@text=\"Coca-Cola® Light\"]");
    public static final By BTN_COCACOLA      = By.xpath("//android.widget.TextView[@text=\"Coca-Cola®\"]");
    public static final By BTN_CEREZA        = By.xpath("//android.widget.TextView[@text=\"Cereza\"]");
    public static final By BTN_CEREZA1       = By.xpath("(//android.widget.TextView[@text=\"Cereza\"])[1]");
    public static final By BTN_CEREZA2       = By.xpath("(//android.widget.TextView[@text=\"Cereza\"])[2]");
    public static final By BTN_MANGO         = By.xpath("(//android.widget.TextView[@text=\"Mango\"])[1]");
    public static final By BTN_FRAMBUEZA     = By.xpath("(//android.widget.TextView[@text=\"Frambuesa Azul\"])[1]");
    public static final By BTN_FRAMBUESA     = By.xpath("//android.widget.TextView[@text=\"Frambuesa Azul\"]");
    public static final By BTN_AMARETO       = By.xpath("//android.widget.TextView[@text=\"Piña Colada Amareto\"]");
    public static final By BTN_MIDORI        = By.xpath("//android.widget.TextView[@text=\"Piña Colada Midori\"]");
    public static final By BTN_KAHLUA        = By.xpath("//android.widget.TextView[@text=\"Piña Colada Kahlua\"]");
    public static final By BTN_PEPINO        = By.xpath("//android.widget.TextView[@text=\"Pepino\"]");
    public static final By BTN_MANZANA       = By.xpath("//android.widget.TextView[@text=\"Manzana Verde\"]");
    public static final By BTN_SKITTLES      = By.xpath("//android.widget.TextView[@text=\"Skittles®\"]");
    public static final By BTN_PINACOLADA    = By.xpath("//android.widget.TextView[@text=\"Piña Colada Grande\"]");
    public static final By BTN_CARLOSV       = By.xpath("//android.widget.TextView[@text=\"Carlos V®\"]");
    public static final By BTN_COOKISCREAM   = By.xpath("//android.widget.TextView[@text=\"Cookies & Cream\"]");
    public static final By BTN_MANZANACANELA = By.xpath("//android.widget.TextView[@text=\"Manzana Canela\"]");
    public static final By BTN_MOKACARAMELO  = By.xpath("//android.widget.TextView[@text=\"Moka Caramelo\"]");
    public static final By BTN_CHOCOLATEBLANCO= By.xpath("//android.view.View[@content-desc=\"Chocolate Blanco\"]");
    public static final By BTN_TEMEDIANO     = By.xpath("//android.widget.TextView[@text=\"Mediano Caliente\"]");
    public static final By BTN_CHOCOLATEMEDIANO= By.xpath("//android.widget.TextView[@text=\"Mediano\"]");
    public static final By BTN_CAFEDESCAFEINADO= By.xpath("//android.widget.TextView[@text=\"Café Descafeinado\"]");
    public static final By BTN_660ML         = By.xpath("//android.widget.TextView[@text=\"600 ML\"]");

    // ─── Snacks y nachos ─────────────────────────────────────────────────────

    public static final By BTN_PALOMITAS     = By.xpath("(//android.view.View[@content-desc=\"Combo Nachos en Pareja \"])[2]");
    public static final By BTN_COMBOCLASICO  = By.xpath("//android.widget.TextView[@text=\"Combo Clásico\"]");
    public static final By BTN_NACHOSGRANDES = By.xpath("//android.widget.TextView[@text=\"Grandes\"]");
    public static final By BTN_DORITOSNACHOS = By.xpath("//android.widget.TextView[@text=\"Doritos® Nacho\"]");
    public static final By BTN_ADOBADAS      = By.xpath("//android.widget.TextView[@text=\"Adobadas\"]");
    public static final By BTN_NACHOSTAJIN   = By.xpath("//android.widget.TextView[@text=\"NACHOS TAJIN\"]");
    public static final By BTN_NACHOSCHICOS  = By.xpath("//android.widget.TextView[@text=\"Chicos\"]");
    public static final By BTN_NACHOSNACHOS  = By.xpath("(//android.widget.TextView[@text=\"Nachos\"])[2]");
    public static final By BTN_EXTRAQUESO2   = By.xpath("//android.widget.TextView[@text=\"Extra Queso\"]");
    public static final By BTN_ALGODON       = By.xpath("//android.widget.TextView[@text=\"Algodon de azucar\"]");

    // ─── Postres / Toppings ───────────────────────────────────────────────────

    public static final By BTN_QUESOPHILADELPHIA  = By.xpath("//android.widget.TextView[@text=\"Queso Philadelphia®\"]");
    public static final By BTN_NUTELLA            = By.xpath("//android.widget.TextView[@text=\"Nutella®\"]");
    public static final By BTN_QUESOMANCHEGO      = By.xpath("//android.widget.TextView[@text=\"Queso machego\"]");
    public static final By BTN_MERMELADAZARZAMORA = By.xpath("//android.widget.TextView[@text=\"Mermelada de zarzamora\"]");
    public static final By BTN_NUEZ               = By.xpath("//android.widget.TextView[@text=\"Nuez\"]");
    public static final By BTN_FRESACOCO          = By.xpath("//android.widget.TextView[@text=\"Fresa-Coco\"]");
    public static final By BTN_CACAHUATE          = By.xpath("//android.widget.TextView[@text=\"Cacahuate 120 g.\"]");
    public static final By BTN_CHAMPIQUESO        = By.xpath("//android.widget.TextView[@text=\"Champiqueso con queso Philadelphia®\"]");
    public static final By BTN_CHAMPIQUESOMANCHEGO= By.xpath("//android.widget.TextView[@text=\"Champiqueso con queso mancheco\"]");

    // ─── Vinculación VIP ──────────────────────────────────────────────────────

    public static final By BOTON_BUSCAR_FUNCION     = By.xpath(
        "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View" +
        "/android.view.View/android.view.View[1]/android.view.View/android.view.View[2]/android.widget.Button");
    public static final By BOTON_ELEGIR_MANUALMENTE = By.xpath("//android.widget.TextView[@text='Elegir función manualmente']");
    public static final By ELEGIR_PELICULA          = By.xpath("//android.widget.TextView[@text='Película']");
    public static final By ELEGIR_HORA              = By.xpath("//android.widget.TextView[@text='Hora']");
    public static final By ELEGIR_FILA              = By.xpath("//android.widget.TextView[@text='Fila']");
    public static final By ELEGIR_NUMERO            = By.xpath("//android.widget.TextView[@text='Número']");
    public static final By PRIMERA_OPCION_DESPLEGABLE= By.xpath("//android.widget.ScrollView/android.view.View[1]");
    public static final By BOTON_VINCULAR_ORDEN     = By.xpath("//android.widget.TextView[@text='Buscar']");
    public static final By BOTON_CONFIRMAR_VINCULACION= By.xpath("//android.widget.TextView[@text='Confirmar']");

    // ─── Té Caliente ──────────────────────────────────────────────────────────────

    public static final PlatformLocator BTN_GRANDE_CALIENTE     = PlatformLocator.byExactText("Grande Caliente");
    public static final PlatformLocator TXT_TE_CALIENTE_CARRITO = PlatformLocator.byExactText("Té caliente");
    public static final PlatformLocator TXT_TE_VARIANTE_CARRITO = PlatformLocator.byExactText("Grande Caliente. Té Menta Manzanilla.");
}
