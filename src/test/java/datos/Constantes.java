package datos;

/**
 * Constantes globales del proyecto.
 * Centraliza valores que antes estaban hardcodeados en múltiples clases.
 */
public final class Constantes {

    private Constantes() {}

    // ─── Aplicación ───────────────────────────────────────────────────────────

    public static final String APP_PACKAGE    = "com.cinepolis.go";   // Android
    public static final String APP_BUNDLE_ID  = "com.cinepolis.ios";  // iOS — actualizar con el bundle ID real
    public static final int    TIMEOUT_SESION = 180; // segundos (newCommandTimeout)

    // ─── Timeouts de espera (segundos) ───────────────────────────────────────

    public static final int ESPERA_CORTA     = 5;
    public static final int ESPERA_NORMAL    = 15;
    public static final int ESPERA_LARGA     = 30;
    public static final int ESPERA_CARGA     = 10; // para menus y transiciones

    // ─── Reintentos ───────────────────────────────────────────────────────────

    public static final int REINTENTOS_CLICK     = 3;
    public static final int REINTENTOS_SCROLL    = 2;
    public static final int REINTENTOS_POPUP     = 4;
    public static final long PAUSA_ENTRE_REINTENTOS_MS = 300;

    // ─── Swipes ───────────────────────────────────────────────────────────────

    public static final int SWIPES_VERTICAL_MAX   = 20;
    public static final int SWIPES_HORIZONTAL_MAX = 20;
    public static final int SWIPE_LENTO_MS        = 950;
    public static final int SWIPE_RAPIDO_MS       = 270;
    public static final int PAUSA_POST_SWIPE_MS   = 320;

    // ─── Límites de negocio ───────────────────────────────────────────────────

    public static final int MAX_ASIENTOS_POR_COMPRA = 10;
    public static final int PRESUPUESTO_SWIPES_2D   = 20;
}
