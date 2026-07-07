package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;
import qa.cinepolis.runner.model.TestCaseResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobExecutor {

    /**
     * Maps UI suite name (lowercase) to Gradle --tests filter.
     * The root project uses JUnit 5 + Gradle; tests live in packages like:
     *   tests.México.asientos.SeleccionAsientos
     *   tests.México.alimentos.*
     *   tests.México.E2E.FlujosCompraNoLogin
     *   tests.RunAllTests  (JUnit Platform Suite that selects @SelectPackages("tests"))
     */
    private static final Map<String, String> SUITE_MAP;

    /**
     * Pool de todos los métodos-de-test de México disponibles para el Smoke.
     * Se construye una sola vez desde SUITE_MAP filtrando los filtros con
     * 4+ puntos y prefijo "tests.México." (selector a nivel de método).
     */
    static final List<String> MEXICO_SMOKE_POOL = new ArrayList<>();

    /** Número de casos seleccionados para una ejecución Smoke. */
    static final int SMOKE_SIZE = 50;

    /**
     * Mapa de filtro Gradle → número de tests que ejecutará.
     * Calculado automáticamente desde SUITE_MAP:
     *  - Filtro de método (4+ puntos)   → 1
     *  - Filtro de clase  (3 puntos)    → número de métodos de esa clase en SUITE_MAP
     *  - Filtro wildcard  (.*)          → suma de métodos de todas las clases del paquete
     */
    static final Map<String, Integer> SUITE_FILTER_SIZE = new HashMap<>();

    static {
        SUITE_MAP = new HashMap<>();
        // Full suites — smoke apunta a lógica propia, NO a RunAllTests
        SUITE_MAP.put("smoke tests",          "§smoke§");   // manejado por buildSmokeCommand()
        SUITE_MAP.put("full suite",           "tests.RunAllTests");
        SUITE_MAP.put("regresión",            "tests.RunAllTests");
        SUITE_MAP.put("regresion",            "tests.RunAllTests");
        SUITE_MAP.put("sanity",               "tests.RunAllTests");
        SUITE_MAP.put("flujo completo",       "tests.México.E2E.FlujosCompraNoLogin");
        SUITE_MAP.put("flujo-completo",       "tests.México.E2E.FlujosCompraNoLogin");
        SUITE_MAP.put("pase anual",           "tests.México.E2E.CompraPaseAnual");
        SUITE_MAP.put("pase-anual",           "tests.México.E2E.CompraPaseAnual");
        SUITE_MAP.put("compra pase anual",    "tests.México.E2E.CompraPaseAnual");
        SUITE_MAP.put("compra-pase-anual",    "tests.México.E2E.CompraPaseAnual");
        SUITE_MAP.put("asientos",             "tests.México.asientos.SeleccionAsientos");
        SUITE_MAP.put("alimentos",            "tests.México.alimentos.*");
        SUITE_MAP.put("alimentos — todo",     "tests.México.alimentos.*");
        SUITE_MAP.put("alimentos-todo",       "tests.México.alimentos.*");
        SUITE_MAP.put("carrito de compras",   "tests.México.carrito.*");
        SUITE_MAP.put("carrito",              "tests.México.carrito.*");
        SUITE_MAP.put("checkout",             "tests.México.E2E.FlujosCompraNoLogin");
        // Individual alimentos tests (by suite id)
        SUITE_MAP.put("alimentos-atmosfera",  "tests.México.alimentos.MenuAtmosfera");
        SUITE_MAP.put("menú atmosphera",      "tests.México.alimentos.MenuAtmosfera");
        SUITE_MAP.put("alimentos-coffee",     "tests.México.alimentos.MenuCoffeTree");
        SUITE_MAP.put("menú coffee tree",     "tests.México.alimentos.MenuCoffeTree");
        SUITE_MAP.put("alimentos-micine",     "tests.México.alimentos.MenuMiCine");
        SUITE_MAP.put("menú mi cine",         "tests.México.alimentos.MenuMiCine");
        SUITE_MAP.put("alimentos-tradicional","tests.México.alimentos.MenuTradicional");
        SUITE_MAP.put("menú tradicional",     "tests.México.alimentos.MenuTradicional");
        SUITE_MAP.put("alimentos-vip",        "tests.México.alimentos.MenuVIP");
        SUITE_MAP.put("menú vip",             "tests.México.alimentos.MenuVIP");
        // Individual asientos test methods
        SUITE_MAP.put("asientos-seleccion1",   "tests.México.asientos.SeleccionAsientos.seleccion1Asiento");
        SUITE_MAP.put("asientos-multiples",    "tests.México.asientos.SeleccionAsientos.seleccionMultiplesAsientos");
        SUITE_MAP.put("asientos-consecutivos", "tests.México.asientos.SeleccionAsientos.seleccionAsientosConsecutivos");
        SUITE_MAP.put("asientos-deseleccion",  "tests.México.asientos.SeleccionAsientos.seleccionAsientosYDeseleccion");
        SUITE_MAP.put("asientos-10mas",        "tests.México.asientos.SeleccionAsientos.seleccion11Asientos");
        SUITE_MAP.put("asientos-horario",      "tests.México.asientos.SeleccionAsientos.cambioHorarioAsientos");
        SUITE_MAP.put("asientos-3d",           "tests.México.asientos.SeleccionAsientos.asientos3D");
        SUITE_MAP.put("asientos-especial",     "tests.México.asientos.SeleccionAsientos.alertaAsientoEspecial");
        SUITE_MAP.put("asientos-junior",       "tests.México.asientos.SeleccionAsientos.asientosSalaJunior");
        // Individual E2E test methods (used by both flujo-completo and checkout drill-downs)
        SUITE_MAP.put("e2e-ticket-trad",    "tests.México.E2E.FlujosCompraNoLogin.compraTicketTradicional");
        SUITE_MAP.put("e2e-mix-trad",       "tests.México.E2E.FlujosCompraNoLogin.compraMixTradicional");
        SUITE_MAP.put("e2e-alimento-trad",  "tests.México.E2E.FlujosCompraNoLogin.compraAlimentoTradicional");
        SUITE_MAP.put("e2e-ticket-atmos",   "tests.México.E2E.FlujosCompraNoLogin.compraTicketAtmosfera");
        SUITE_MAP.put("e2e-mix-atmos",      "tests.México.E2E.FlujosCompraNoLogin.compraMixAtmosfera");
        SUITE_MAP.put("e2e-alimento-atmos", "tests.México.E2E.FlujosCompraNoLogin.compraAlimentoAtmosfera");
        SUITE_MAP.put("e2e-ticket-vip",     "tests.México.E2E.FlujosCompraNoLogin.compraTicketVIP");
        SUITE_MAP.put("e2e-mix-vip",        "tests.México.E2E.FlujosCompraNoLogin.compraMixVIP");
        SUITE_MAP.put("e2e-alimento-vip",   "tests.México.E2E.FlujosCompraNoLogin.compraAlimentoVIP");
        // Individual Atmosphera test methods
        SUITE_MAP.put("atmos-t1", "tests.México.alimentos.MenuAtmosfera.comprarCrepaDulceFrappe");
        SUITE_MAP.put("atmos-t2", "tests.México.alimentos.MenuAtmosfera.comprarCrepaDulceFrappesG");
        SUITE_MAP.put("atmos-t3", "tests.México.alimentos.MenuAtmosfera.comprarCrepaDulceFrappes");
        // Individual VIP test methods
        SUITE_MAP.put("vip-t1", "tests.México.alimentos.MenuVIP.comprarPalomitasClasicasMantequilla");
        SUITE_MAP.put("vip-t2", "tests.México.alimentos.MenuVIP.comprarDippinDotsA");
        // Individual Coffee Tree test methods
        SUITE_MAP.put("coffee-t01", "tests.México.alimentos.MenuCoffeTree.comprarAmericano");
        SUITE_MAP.put("coffee-t02", "tests.México.alimentos.MenuCoffeTree.comprarAmericanoG");
        SUITE_MAP.put("coffee-t03", "tests.México.alimentos.MenuCoffeTree.comprarAmericanoGM");
        SUITE_MAP.put("coffee-t04", "tests.México.alimentos.MenuCoffeTree.comprarAmericanoGMV");
        SUITE_MAP.put("coffee-t05", "tests.México.alimentos.MenuCoffeTree.comprarMokaOscuro");
        SUITE_MAP.put("coffee-t06", "tests.México.alimentos.MenuCoffeTree.comprarMokaOscuroG");
        SUITE_MAP.put("coffee-t07", "tests.México.alimentos.MenuCoffeTree.comprarMokaOscuroM");
        SUITE_MAP.put("coffee-t08", "tests.México.alimentos.MenuCoffeTree.comprarMokaOscuroMD");
        SUITE_MAP.put("coffee-t09", "tests.México.alimentos.MenuCoffeTree.comprarCapuccino");
        SUITE_MAP.put("coffee-t10", "tests.México.alimentos.MenuCoffeTree.comprarTe");
        SUITE_MAP.put("coffee-t11", "tests.México.alimentos.MenuCoffeTree.comprarTeM");
        SUITE_MAP.put("coffee-t12", "tests.México.alimentos.MenuCoffeTree.comprarChocolate");
        SUITE_MAP.put("coffee-t13", "tests.México.alimentos.MenuCoffeTree.comprarChocolateM");
        SUITE_MAP.put("coffee-t14", "tests.México.alimentos.MenuCoffeTree.comprarPretzel");
        SUITE_MAP.put("coffee-t15", "tests.México.alimentos.MenuCoffeTree.comprarCheeseCake");
        SUITE_MAP.put("coffee-t16", "tests.México.alimentos.MenuCoffeTree.comprarCornetto");
        SUITE_MAP.put("coffee-t17", "tests.México.alimentos.MenuCoffeTree.comprarSkwinkles");
        SUITE_MAP.put("coffee-t18", "tests.México.alimentos.MenuCoffeTree.comprarMM");
        SUITE_MAP.put("coffee-t19", "tests.México.alimentos.MenuCoffeTree.comprarHersheys");
        SUITE_MAP.put("coffee-t20", "tests.México.alimentos.MenuCoffeTree.comprarSnickers");
        SUITE_MAP.put("coffee-t21", "tests.México.alimentos.MenuCoffeTree.comprarCrepas");
        SUITE_MAP.put("coffee-t22", "tests.México.alimentos.MenuCoffeTree.comprarCrepasM");
        SUITE_MAP.put("coffee-t23", "tests.México.alimentos.MenuCoffeTree.comprarCrepasS");
        SUITE_MAP.put("coffee-t24", "tests.México.alimentos.MenuCoffeTree.comprarCrepasSP");
        SUITE_MAP.put("coffee-t25", "tests.México.alimentos.MenuCoffeTree.comprarCrepasSI");
        SUITE_MAP.put("coffee-t26", "tests.México.alimentos.MenuCoffeTree.comprarCrepasFrappe");
        SUITE_MAP.put("coffee-t27", "tests.México.alimentos.MenuCoffeTree.comprarCrepasFrappeM");
        SUITE_MAP.put("coffee-t28", "tests.México.alimentos.MenuCoffeTree.comprarCrepasFrappeG");
        SUITE_MAP.put("coffee-t29", "tests.México.alimentos.MenuCoffeTree.comprarCrepasFrapeMA");
        SUITE_MAP.put("coffee-t30", "tests.México.alimentos.MenuCoffeTree.comprarCrepasFrappeS");
        // Individual Mi Cine test methods
        SUITE_MAP.put("micine-t01", "tests.México.alimentos.MenuMiCine.comprarMaxiComboMix");
        SUITE_MAP.put("micine-t02", "tests.México.alimentos.MenuMiCine.comprarMaxiComboMix2");
        SUITE_MAP.put("micine-t03", "tests.México.alimentos.MenuMiCine.comprarMaxiComboMix3");
        SUITE_MAP.put("micine-t04", "tests.México.alimentos.MenuMiCine.comprarMaxiComboMix4");
        SUITE_MAP.put("micine-t05", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar");
        SUITE_MAP.put("micine-t06", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar2");
        SUITE_MAP.put("micine-t07", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar3");
        SUITE_MAP.put("micine-t08", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar4");
        SUITE_MAP.put("micine-t09", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar5");
        SUITE_MAP.put("micine-t10", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar10");
        SUITE_MAP.put("micine-t11", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar6");
        SUITE_MAP.put("micine-t12", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar8");
        SUITE_MAP.put("micine-t13", "tests.México.alimentos.MenuMiCine.comprarMaxiComboFamiliar7");
        SUITE_MAP.put("micine-t14", "tests.México.alimentos.MenuMiCine.comprarComboICCE");
        SUITE_MAP.put("micine-t15", "tests.México.alimentos.MenuMiCine.comprarComboICCE2");
        SUITE_MAP.put("micine-t16", "tests.México.alimentos.MenuMiCine.comprarComboICCE3");
        SUITE_MAP.put("micine-t17", "tests.México.alimentos.MenuMiCine.comprarComboICCE4");
        SUITE_MAP.put("micine-t18", "tests.México.alimentos.MenuMiCine.comprarComboICCE5");
        SUITE_MAP.put("micine-t19", "tests.México.alimentos.MenuMiCine.comprarComboICCE6");
        SUITE_MAP.put("micine-t20", "tests.México.alimentos.MenuMiCine.comprarComboICCE7");
        SUITE_MAP.put("micine-t21", "tests.México.alimentos.MenuMiCine.comprarComboICCE8");
        SUITE_MAP.put("micine-t22", "tests.México.alimentos.MenuMiCine.comprarComboJunior");
        SUITE_MAP.put("micine-t23", "tests.México.alimentos.MenuMiCine.comprarComboJunior0");
        SUITE_MAP.put("micine-t24", "tests.México.alimentos.MenuMiCine.comprarComboJunior3");
        SUITE_MAP.put("micine-t25", "tests.México.alimentos.MenuMiCine.comprarComboJunior4");
        SUITE_MAP.put("micine-t26", "tests.México.alimentos.MenuMiCine.comprarComboJunior2");
        SUITE_MAP.put("micine-t27", "tests.México.alimentos.MenuMiCine.comprarComboClasico");
        SUITE_MAP.put("micine-t28", "tests.México.alimentos.MenuMiCine.comprarComboClasico2");
        SUITE_MAP.put("micine-t29", "tests.México.alimentos.MenuMiCine.comprarComboClasico3");
        SUITE_MAP.put("micine-t30", "tests.México.alimentos.MenuMiCine.comprarComboClasico4");
        SUITE_MAP.put("micine-t31", "tests.México.alimentos.MenuMiCine.comprarComboClasico5");
        SUITE_MAP.put("micine-t32", "tests.México.alimentos.MenuMiCine.comprarComboClasico6");
        SUITE_MAP.put("micine-t33", "tests.México.alimentos.MenuMiCine.comprarComboClasico7");
        SUITE_MAP.put("micine-t34", "tests.México.alimentos.MenuMiCine.comprarComboClasico8");
        SUITE_MAP.put("micine-t35", "tests.México.alimentos.MenuMiCine.comprarComboClasico10");
        SUITE_MAP.put("micine-t36", "tests.México.alimentos.MenuMiCine.comprarPalomitasSkwinkles");
        SUITE_MAP.put("micine-t37", "tests.México.alimentos.MenuMiCine.comprarPalomitasSkwinkles2");
        SUITE_MAP.put("micine-t38", "tests.México.alimentos.MenuMiCine.comprarPalomitasSkwinkles3");
        SUITE_MAP.put("micine-t39", "tests.México.alimentos.MenuMiCine.comprarPalomitasSkwinkles4");
        SUITE_MAP.put("micine-t40", "tests.México.alimentos.MenuMiCine.comprarPalomitasSkwinkles5");
        // Individual Tradicional test methods
        SUITE_MAP.put("trad-t01", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliar");
        SUITE_MAP.put("trad-t02", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarJ");
        SUITE_MAP.put("trad-t03", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarJU");
        SUITE_MAP.put("trad-t04", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarT");
        SUITE_MAP.put("trad-t05", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarD");
        SUITE_MAP.put("trad-t06", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarM");
        SUITE_MAP.put("trad-t07", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarCT");
        SUITE_MAP.put("trad-t08", "tests.México.alimentos.MenuTradicional.comprarMaxiComboFamiliarMT");
        SUITE_MAP.put("trad-t09", "tests.México.alimentos.MenuTradicional.comprarComboICEE");
        SUITE_MAP.put("trad-t10", "tests.México.alimentos.MenuTradicional.comprarComboICEEM");
        SUITE_MAP.put("trad-t11", "tests.México.alimentos.MenuTradicional.comprarComboICEES");
        SUITE_MAP.put("trad-t12", "tests.México.alimentos.MenuTradicional.comprarComboICEESA");
        SUITE_MAP.put("trad-t13", "tests.México.alimentos.MenuTradicional.comprarComboICEEP");
        SUITE_MAP.put("trad-t14", "tests.México.alimentos.MenuTradicional.comprarComboICEEAR");
        SUITE_MAP.put("trad-t15", "tests.México.alimentos.MenuTradicional.comprarComboICEECC");
        SUITE_MAP.put("trad-t16", "tests.México.alimentos.MenuTradicional.comprarComboICEEMM");
        SUITE_MAP.put("trad-t17", "tests.México.alimentos.MenuTradicional.comprarComboICEEFF");
        SUITE_MAP.put("trad-t18", "tests.México.alimentos.MenuTradicional.comprarComboICEEPP");
        SUITE_MAP.put("trad-t19", "tests.México.alimentos.MenuTradicional.comprarComboICEEPM");
        SUITE_MAP.put("trad-t20", "tests.México.alimentos.MenuTradicional.comprarComboICEECF");
        SUITE_MAP.put("trad-t21", "tests.México.alimentos.MenuTradicional.comprarComboICEEJC");
        SUITE_MAP.put("trad-t22", "tests.México.alimentos.MenuTradicional.comprarComboICEEJT");
        SUITE_MAP.put("trad-t23", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescos");
        SUITE_MAP.put("trad-t24", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosGS");
        SUITE_MAP.put("trad-t25", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosMS");
        SUITE_MAP.put("trad-t26", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosCC");
        SUITE_MAP.put("trad-t27", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosJJ");
        SUITE_MAP.put("trad-t28", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosJG");
        SUITE_MAP.put("trad-t29", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosJM");
        SUITE_MAP.put("trad-t30", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosJC");
        SUITE_MAP.put("trad-t31", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosCJ");
        SUITE_MAP.put("trad-t32", "tests.México.alimentos.MenuTradicional.comprarHotDogRefrescosJJS");
        SUITE_MAP.put("trad-t33", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAgua");
        SUITE_MAP.put("trad-t34", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaN");
        SUITE_MAP.put("trad-t35", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaA");
        SUITE_MAP.put("trad-t36", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaT");
        SUITE_MAP.put("trad-t37", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaD");
        SUITE_MAP.put("trad-t38", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaML");
        SUITE_MAP.put("trad-t39", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaNA");
        SUITE_MAP.put("trad-t40", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaCM");
        SUITE_MAP.put("trad-t41", "tests.México.alimentos.MenuTradicional.comprarSnacksPapasAguaAC");
        SUITE_MAP.put("trad-t42", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMix");
        SUITE_MAP.put("trad-t43", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixC");
        SUITE_MAP.put("trad-t44", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixT");
        SUITE_MAP.put("trad-t45", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixM");
        SUITE_MAP.put("trad-t46", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixCJ");
        SUITE_MAP.put("trad-t47", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixD");
        SUITE_MAP.put("trad-t48", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixDC");
        SUITE_MAP.put("trad-t49", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixPC");
        SUITE_MAP.put("trad-t50", "tests.México.alimentos.MenuTradicional.comprarMaxicomboMixPCJ");
        // Argentina suite and individual tests
        SUITE_MAP.put("noafectacion-argentina",   "tests.Argentina.NoAfectacionArgentina");
        SUITE_MAP.put("ar-ticket-avellaneda",     "tests.Argentina.NoAfectacionArgentina.compraTicketAvellaneda");
        SUITE_MAP.put("ar-mix-avellaneda",        "tests.Argentina.NoAfectacionArgentina.compraMixAvellaneda");
        SUITE_MAP.put("ar-food-avellaneda",       "tests.Argentina.NoAfectacionArgentina.compraFoodAvellaneda");
        SUITE_MAP.put("ar-ticket-lujan",          "tests.Argentina.NoAfectacionArgentina.compraTicketLujan");
        SUITE_MAP.put("ar-mix-lujan",             "tests.Argentina.NoAfectacionArgentina.compraMixLujan");
        SUITE_MAP.put("ar-food-lujan",            "tests.Argentina.NoAfectacionArgentina.compraFoodLujan");
        SUITE_MAP.put("ar-ticket-merlo",          "tests.Argentina.NoAfectacionArgentina.compraTicketMerlo");
        SUITE_MAP.put("ar-mix-merlo",             "tests.Argentina.NoAfectacionArgentina.compraMixMerlo");
        SUITE_MAP.put("ar-food-merlo",            "tests.Argentina.NoAfectacionArgentina.compraFoodMerlo");
        SUITE_MAP.put("ar-ticket-pilar",          "tests.Argentina.NoAfectacionArgentina.compraTicketPilar");
        SUITE_MAP.put("ar-mix-pilar",             "tests.Argentina.NoAfectacionArgentina.compraMixPilar");
        SUITE_MAP.put("ar-food-pilar",            "tests.Argentina.NoAfectacionArgentina.compraFoodPilar");
        SUITE_MAP.put("ar-ticket-houssay",        "tests.Argentina.NoAfectacionArgentina.compraTicketPlazaHoussay");
        SUITE_MAP.put("ar-mix-houssay",           "tests.Argentina.NoAfectacionArgentina.compraMixPlazaHoussay");
        SUITE_MAP.put("ar-food-houssay",          "tests.Argentina.NoAfectacionArgentina.compraFoodPlazaHoussay");
        SUITE_MAP.put("ar-ticket-recoleta",       "tests.Argentina.NoAfectacionArgentina.compraTicketRecoleta");
        SUITE_MAP.put("ar-mix-recoleta",          "tests.Argentina.NoAfectacionArgentina.compraMixRecoleta");
        SUITE_MAP.put("ar-food-recoleta",         "tests.Argentina.NoAfectacionArgentina.compraFoodRecoleta");
        SUITE_MAP.put("ar-ticket-arenamaipu",     "tests.Argentina.NoAfectacionArgentina.compraTicketArenaMaipu");
        SUITE_MAP.put("ar-mix-arenamaipu",        "tests.Argentina.NoAfectacionArgentina.compraMixArenaMaipu");
        SUITE_MAP.put("ar-food-arenamaipu",       "tests.Argentina.NoAfectacionArgentina.compraFoodArenaMaipu");
        SUITE_MAP.put("ar-ticket-mendozaplaza",   "tests.Argentina.NoAfectacionArgentina.compraTicketMendozaPlaza");
        SUITE_MAP.put("ar-mix-mendozaplaza",      "tests.Argentina.NoAfectacionArgentina.compraMixMendozaPlaza");
        SUITE_MAP.put("ar-food-mendozaplaza",     "tests.Argentina.NoAfectacionArgentina.compraFoodMendozaPlaza");
        SUITE_MAP.put("ar-ticket-neuquen",        "tests.Argentina.NoAfectacionArgentina.compraTicketNeuquen");
        SUITE_MAP.put("ar-mix-neuquen",           "tests.Argentina.NoAfectacionArgentina.compraMixNeuquen");
        SUITE_MAP.put("ar-food-neuquen",          "tests.Argentina.NoAfectacionArgentina.compraFoodNeuquen");
        SUITE_MAP.put("ar-ticket-rosario",        "tests.Argentina.NoAfectacionArgentina.compraTicketRosario");
        SUITE_MAP.put("ar-mix-rosario",           "tests.Argentina.NoAfectacionArgentina.compraMixRosario");
        SUITE_MAP.put("ar-food-rosario",          "tests.Argentina.NoAfectacionArgentina.compraFoodRosario");
        // Chile suite and individual tests
        SUITE_MAP.put("noafectacion-chile",          "tests.Chile.NoAfectacionChile");
        SUITE_MAP.put("cl-ticket-dominicos",         "tests.Chile.NoAfectacionChile.compraTicketDominicos");
        SUITE_MAP.put("cl-mix-dominicos",            "tests.Chile.NoAfectacionChile.compraMixDominicos");
        SUITE_MAP.put("cl-alimento-dominicos",       "tests.Chile.NoAfectacionChile.compraAlimentoDominicos");
        SUITE_MAP.put("cl-ticket-lareina",           "tests.Chile.NoAfectacionChile.compraTicketLaReina");
        SUITE_MAP.put("cl-mix-lareina",              "tests.Chile.NoAfectacionChile.compraMixLaReina");
        SUITE_MAP.put("cl-alimento-lareina",         "tests.Chile.NoAfectacionChile.compraAlimentoLaReina");
        SUITE_MAP.put("cl-ticket-parque",            "tests.Chile.NoAfectacionChile.compraTicketParqueArauco");
        SUITE_MAP.put("cl-mix-parque",               "tests.Chile.NoAfectacionChile.compraMixParqueArauco");
        SUITE_MAP.put("cl-alimento-parque",          "tests.Chile.NoAfectacionChile.compraAlimentoParqueArauco");
        SUITE_MAP.put("cl-ticket-parquepremium",     "tests.Chile.NoAfectacionChile.compraTicketParqueAraucoPremium");
        SUITE_MAP.put("cl-mix-parquepremium",        "tests.Chile.NoAfectacionChile.compraMixParqueAraucoPremium");
        SUITE_MAP.put("cl-alimento-parquepremium",   "tests.Chile.NoAfectacionChile.compraAlimentoParqueAraucoPremium");

        // Poblar pool de smoke: todos los métodos-de-test de México (4+ puntos en el filtro)
        for (String filter : SUITE_MAP.values()) {
            if (filter.startsWith("tests.México.")
                    && filter.chars().filter(c -> c == '.').count() >= 4
                    && !filter.endsWith(".*")
                    && !MEXICO_SMOKE_POOL.contains(filter)) {
                MEXICO_SMOKE_POOL.add(filter);
            }
        }

        // ── Calcular SUITE_FILTER_SIZE: cuántos tests ejecuta cada filtro ────────
        // Paso 1: contar métodos por clase (filtros de 4+ puntos → extraer la clase padre)
        Map<String, Integer> classMethodCount = new HashMap<>();
        for (String f : SUITE_MAP.values()) {
            if (f == null || f.startsWith("§") || !f.startsWith("tests.")) continue;
            long dots = f.chars().filter(c -> c == '.').count();
            if (dots >= 4 && !f.endsWith(".*")) {
                // Filtro de método → clase es todo menos el último segmento
                String cls = f.substring(0, f.lastIndexOf('.'));
                classMethodCount.merge(cls, 1, Integer::sum);
            }
        }
        // Paso 2: construir mapa filtro → count para los tres tipos de filtro
        for (String f : SUITE_MAP.values()) {
            if (f == null || f.startsWith("§") || f.equals("tests.RunAllTests")) continue;
            long dots = f.chars().filter(c -> c == '.').count();
            if (!f.endsWith(".*") && dots >= 4) {
                // Método individual
                SUITE_FILTER_SIZE.put(f, 1);
            } else if (!f.endsWith(".*") && dots == 3) {
                // Clase completa → buscar conteo de métodos
                Integer cnt = classMethodCount.get(f);
                if (cnt != null) SUITE_FILTER_SIZE.put(f, cnt);
            } else if (f.endsWith(".*")) {
                // Wildcard de paquete → sumar métodos de todas las clases del paquete
                String pkg = f.substring(0, f.length() - 2) + "."; // "tests.xxx.alimentos."
                int total = 0;
                for (Map.Entry<String, Integer> e : classMethodCount.entrySet()) {
                    if (e.getKey().startsWith(pkg)) total += e.getValue();
                }
                if (total > 0) SUITE_FILTER_SIZE.put(f, total);
            }
        }

        // ── Correcciones de conteo real vs entradas en SUITE_MAP ─────────────
        // SUITE_MAP solo registra los métodos usados para ejecución individual,
        // pero las clases pueden tener más @Test. Verificado con:
        //   grep -c "^    @Test$" ClassName.java
        //
        // MenuCoffeTree: 50 @Test reales, 30 en SUITE_MAP (coffee-t01..t30)
        SUITE_FILTER_SIZE.put("tests.México.alimentos.MenuCoffeTree",  50);
        // MenuMiCine:    50 @Test reales, 40 en SUITE_MAP (micine-t01..t40)
        SUITE_FILTER_SIZE.put("tests.México.alimentos.MenuMiCine",     50);
        // MenuTradicional: 50 reales = 50 en SUITE_MAP (trad-t01..t50) — ya correcto
        // MenuVIP / MenuAtmosfera: 2 reales ≈ entradas en SUITE_MAP — ya correcto
    }

    private final RunnerConfig   config;
    private final BackendClient  client;
    private final AppiumManager  appiumMgr;

    private volatile Process activeProcess;

    public JobExecutor(RunnerConfig config, BackendClient client) {
        this(config, client, null);
    }

    public JobExecutor(RunnerConfig config, BackendClient client, AppiumManager appiumMgr) {
        this.config    = config;
        this.client    = client;
        this.appiumMgr = appiumMgr;
    }

    /**
     * Calcula el total esperado DESDE EL COMANDO GRADLE ya construido, garantizando
     * que el valor enviado al dashboard coincide exactamente con lo que se ejecutará.
     *
     *  - Smoke / múltiples --tests individuales → número de flags --tests (1 por método)
     *  - Suite de clase (1 flag --tests ClassName) → lookup en SUITE_FILTER_SIZE
     *  - Wildcard (1 flag --tests pkg.*) → lookup en SUITE_FILTER_SIZE
     *  - Desconocido → -1 (sin barra de progreso)
     */
    private int resolveExpectedCountFromCommand(JobDto job, List<String> cmd) {
        String key = job.suite != null ? job.suite.toLowerCase().trim() : "";

        // Contar cuántos flags --tests tiene el comando
        long testsFlags = cmd.stream().filter("--tests"::equals).count();

        // Smoke y múltiples métodos individuales: cada flag = 1 test real
        if ("smoke tests".equals(key) || "smoke".equals(key) || testsFlags > 1) {
            return (int) testsFlags;
        }

        // 1 flag → clase o método
        if (testsFlags == 1) {
            int idx = cmd.indexOf("--tests");
            if (idx >= 0 && idx + 1 < cmd.size()) {
                String filter = cmd.get(idx + 1);
                Integer count = SUITE_FILTER_SIZE.get(filter);
                if (count != null) return count;
                // 4+ puntos sin .* = selector de método → 1 test
                if (filter.chars().filter(c -> c == '.').count() >= 4
                        && !filter.endsWith(".*")) return 1;
            }
        }

        return -1;
    }

    /**
     * Devuelve el número de tests esperados para la suite dada usando SUITE_FILTER_SIZE.
     * Cubre automáticamente todas las suites registradas en SUITE_MAP:
     *  - smoke               → SMOKE_SIZE (50, selección aleatoria)
     *  - clase completa      → número de métodos de esa clase en SUITE_MAP
     *  - wildcard de paquete → suma de todos los métodos del paquete
     *  - método individual   → 1
     *  - full suite / RunAllTests → -1 (desconocido, sin barra de progreso)
     */
    private int resolveExpectedTestCount(JobDto job) {
        String key = job.suite != null ? job.suite.toLowerCase().trim() : "";

        // Smoke: selección aleatoria de tamaño fijo
        if ("smoke tests".equals(key) || "smoke".equals(key)) {
            return Math.min(SMOKE_SIZE, MEXICO_SMOKE_POOL.size());
        }

        // Buscar el filtro Gradle para esta suite
        String filter = SUITE_MAP.getOrDefault(key, "");
        if (filter.isEmpty() || filter.startsWith("§") || filter.equals("tests.RunAllTests")) {
            return -1;
        }

        // Lookup en el mapa precalculado
        Integer count = SUITE_FILTER_SIZE.get(filter);
        return count != null ? count : -1;
    }

    /** Returns true if a Gradle test process is currently active. */
    public boolean hasActiveProcess() {
        Process p = activeProcess;
        return p != null && p.isAlive();
    }

    /** Kills the currently running Gradle process tree. Called by the shutdown hook. */
    public void killActiveProcess() {
        Process p = activeProcess;
        if (p != null && p.isAlive()) {
            System.out.println("\n[Runner] Shutdown detectado — terminando proceso Gradle...");
            forceKillProcessTree(p);
            System.out.println("[Runner] Proceso Gradle terminado.");
        }
    }

    /**
     * Mata el árbol de procesos de forma confiable en Windows y Linux/Mac.
     * Estrategia dual:
     *  1. Java ProcessHandle.descendants().destroyForcibly() (cross-platform)
     *  2. Windows: taskkill /F /T /PID para matar subárboles que Java puede perder
     *     (gradlew.bat → cmd.exe → java.exe → java.exe (test fork))
     */
    private void forceKillProcessTree(Process process) {
        long pid = process.pid();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // Paso 1: Java ProcessHandle (funciona en todos los SO)
        try {
            process.toHandle().descendants().forEach(h -> {
                try { h.destroyForcibly(); } catch (Exception ignored) {}
            });
            process.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[Executor] Error matando proceso (Java): " + e.getMessage());
        }

        // Paso 2: Windows taskkill como refuerzo (mata el árbol completo incluyendo cmd.exe wrappers)
        if (isWindows) {
            try {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[Executor] taskkill falló: " + e.getMessage());
            }
        }
    }

    public void execute(JobDto job) {
        System.out.printf("%n[Executor] ▶  %s  |  Suite: %s  |  Env: %s  |  País: %s%n",
                job.executionId, job.suite, job.env, job.country);

        AtomicInteger passed  = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        List<TestCaseResult> testCases = new ArrayList<>();
        boolean iosRecordingActive = false;
        boolean iosCleanupDone     = false; // prevents double-cleanup in finally safety net
        // Hoisted outside try so finally can access them for cleanup and finalization
        final boolean isPlatformIos = "ios".equalsIgnoreCase(nvl(job.platform, ""));
        final String  iosUdid       = nvl(job.udid, "");
        // Tracks whether the abort-watcher already called confirmAbort() (marks ABORTED in backend)
        final AtomicBoolean wasAborted = new AtomicBoolean(false);
        // Guards against duplicate sendResult() and enables the finally safety-net
        final AtomicBoolean resultSent = new AtomicBoolean(false);

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite
                    + "  |  Env: "    + job.env
                    + "  |  Device: " + job.device
                    + "  |  País: "   + job.country);
            client.sendLog(job.executionId, "INFO",
                    "📹 Grabación de video: " + (job.videoEnabled ? "ACTIVA" : "INACTIVA"));

            // ── Fetch central config from backend (single source of truth) ──────
            client.sendLog(job.executionId, "INFO", "📥 Obteniendo configuración desde Backend...");
            BackendClient.RunnerConfigResponse runnerCfg = client.getRunnerConfig();
            if (runnerCfg == null || !runnerCfg.isConfigured()) {
                client.sendLog(job.executionId, "ERROR",
                        "❌ No fue posible obtener la configuración del proyecto.");
                client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                return;
            }
            client.sendLog(job.executionId, "INFO", "✅ Configuración recibida.");

            // ── Device received from backend ───────────────────────────────────
            String receivedDevice   = nvl(job.deviceName, job.device);
            String receivedPlatform = nvl(job.platform, "");
            String receivedUdid     = nvl(job.udid, "");
            client.sendLog(job.executionId, "INFO",
                    "📱 DISPOSITIVO RECIBIDO DEL BACKEND: " + receivedDevice
                    + (receivedPlatform.isBlank() ? "" : " / " + receivedPlatform)
                    + (receivedUdid.isBlank()     ? "" : " / " + receivedUdid));
            if (receivedUdid.isBlank()) {
                client.sendLog(job.executionId, "ERROR",
                        "❌ La configuración almacenada no coincide con el dispositivo enviado. " +
                        "Verifica que el dispositivo esté conectado y registrado en el Runner.");
                client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                return;
            }

            // ── Sync workspace (clone/pull) + validate Gradle structure ──────────
            File   wsDir   = new File(config.workspaceDir, runnerCfg.projectName);
            WorkspaceManager wsMgr = new WorkspaceManager(
                    wsDir, runnerCfg.repositoryUrl, runnerCfg.branch, client);
            File projectDir = wsMgr.ensureWorkspace(job.executionId);
            if (projectDir == null) {
                client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                return;
            }
            String workDir = projectDir.getAbsolutePath();

            // ── Pre-flight ────────────────────────────────────────────────────
            boolean isAndroid = !"ios".equalsIgnoreCase(receivedPlatform);
            client.sendLog(job.executionId, "INFO",
                    "🖥  Plataforma detectada: " + (isAndroid ? "Android" : "iOS")
                    + " | UDID: " + receivedUdid);
            IosPreflightManager.IosPreflightResult iosResult = null;
            if (isAndroid) {
                checkAdbDevices(job.executionId);
                if (!receivedUdid.isBlank()) {
                    clearUiAutomator2SystemPort(receivedUdid, job.executionId);
                }
            } else {
                if (!checkIosXcuitestDriver(job.executionId)) {
                    client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                    return;
                }
                iosResult = IosPreflightManager.runPreflight(
                        client, job.executionId, receivedUdid);
            }
            checkAppiumServer(job.executionId);

            // ── Pre-clean locked test-results to avoid file-lock failures ────
            preCleanTestResults(job.executionId, workDir);

            // ── Build Gradle command ──────────────────────────────────────────
            List<String> cmd = buildCommand(job);

            // Per-device config takes precedence over global runner config
            String effectivePackage  = (job.appPackage != null && !job.appPackage.isBlank())
                    ? job.appPackage : runnerCfg.appPackage;
            String effectiveBundleId = (job.bundleId   != null && !job.bundleId.isBlank())
                    ? job.bundleId : "";

            // Android: inject package + auto-resolve launcher activity via ADB
            if (isAndroid && !effectivePackage.isBlank()) {
                cmd.add("-DappPackage=" + effectivePackage);
                client.sendLog(job.executionId, "INFO",
                        "[JobExecutor] 📱 Package: " + effectivePackage
                        + (job.appPackage != null && !job.appPackage.isBlank() ? " (config dispositivo)" : " (config global)"));

                String resolvedActivity = resolveLauncherActivity(receivedUdid, effectivePackage);
                if (resolvedActivity == null || resolvedActivity.isBlank()) {
                    client.sendLog(job.executionId, "ERROR",
                            "❌ Launcher Activity no encontrada para " + effectivePackage
                            + ". Verifica que la app esté instalada en el dispositivo.");
                    client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                    return;
                }
                cmd.add("-DappActivity=" + resolvedActivity);
                client.sendLog(job.executionId, "INFO",
                        "[JobExecutor] 🚀 Activity detectada: " + resolvedActivity);
            }

            // iOS: inject bundleId (per-device config first, then appPackage fallback)
            if (!isAndroid) {
                // Propagate Runner-confirmed xcuitest state to Gradle subprocess so DriverFactory
                // trusts this result instead of running its own redundant subprocess check.
                cmd.add("-DappiumXcuitestInstalled=true");
                // Signal physical iOS device: BaseTest must skip driver.startRecordingScreen()
                // (which depends on ffmpeg) and defer to xcrun devicectl device recordVideo.
                cmd.add("-DiosPhysicalDevice=true");
                String iosBundleId = !effectiveBundleId.isBlank() ? effectiveBundleId
                        : !effectivePackage.isBlank()             ? effectivePackage
                        : "";
                if (!iosBundleId.isBlank()) {
                    cmd.add("-DbundleId=" + iosBundleId);
                    cmd.add("-DappPackage=" + iosBundleId);
                    client.sendLog(job.executionId, "INFO",
                            "[JobExecutor] 🍎 Bundle ID: " + iosBundleId);
                    if (!runnerCfg.appActivity.isBlank()) {
                        cmd.add("-DappActivity=" + runnerCfg.appActivity);
                    }
                }

                // WDA signing + caching — results from IosPreflightManager
                if (iosResult != null) {
                    if (!iosResult.teamId.isBlank()) {
                        cmd.add("-DxcodeOrgId=" + iosResult.teamId);
                        cmd.add("-DxcodeSigningId=Apple Development");
                        client.sendLog(job.executionId, "INFO",
                                "[JobExecutor] 🔑 Team ID: " + iosResult.teamId);
                    }
                    if (!iosResult.iosVersion.isBlank()) {
                        // Override any backend-provided platformVersion with the real device value
                        cmd.add("-DplatformVersion=" + iosResult.iosVersion);
                    }
                    cmd.add("-DupdatedWDABundleId=" + iosResult.wdaBundleId);

                    // wdaPrebuilt=true when: cache existed OR WDA was confirmed running during preflight.
                    // This tells Appium XCUITest driver to reuse the existing WDA process
                    // instead of rebuilding from scratch.
                    boolean wdaPrebuilt = iosResult.wdaCached || iosResult.wdaReady;
                    cmd.add("-DwdaPrebuilt=" + wdaPrebuilt);

                    // webDriverAgentUrl: URL where WDA is already running (may be a CoreDevice IP
                    // rather than localhost when using Xcode 16+/26). When set, Appium connects
                    // directly to this URL instead of trying to start its own WDA instance.
                    String wdaUrl = WdaManager.getDetectedWdaUrl();
                    if (wdaUrl != null && !wdaUrl.isBlank()) {
                        cmd.add("-DwebDriverAgentUrl=" + wdaUrl);
                        client.sendLog(job.executionId, "INFO",
                                "[JobExecutor] 🌐 WebDriverAgent URL: " + wdaUrl);
                    }

                    client.sendLog(job.executionId, "INFO",
                            "[JobExecutor] 📦 WDA bundle: " + iosResult.wdaBundleId
                            + " | prebuilt: " + wdaPrebuilt
                            + (iosResult.wdaReady ? " | ✅ WDA activo en "
                                    + (wdaUrl != null ? wdaUrl : "localhost:8100") : ""));

                    // ── Pass confirmed device sync state to Gradle subprocess ──────────
                    // IOSDeviceStateService (test JVM) reads these properties and returns
                    // the Runner-confirmed state without running any new subprocess — which
                    // prevents IOSDeviceSynchronizationManager from querying xctrace again
                    // and getting a stale "not visible" result seconds after Runner confirmed ready.
                    cmd.add("-DiosState.xctraceVisible="     + iosResult.xctraceConfirmed);
                    cmd.add("-DiosState.coreDeviceVisible=true");
                    cmd.add("-DiosState.tunnelState="        + iosResult.tunnelState);
                    cmd.add("-DiosState.pairingState="       + iosResult.pairingState);
                    if (!iosResult.coreDeviceId.isBlank())
                        cmd.add("-DiosState.coreDeviceId="   + iosResult.coreDeviceId);
                    cmd.add("-DiosState.confirmedAtMs="      + iosResult.confirmedAtMs);
                    // Single-source-of-truth fields — Framework must not recalculate these
                    cmd.add("-DiosState.transportType="      + iosResult.transportType);
                    cmd.add("-DiosState.readyForExecution="  + iosResult.readyForExecution);
                    if (iosResult.notReadyReason != null && !iosResult.notReadyReason.isBlank())
                        cmd.add("-DiosState.notReadyReason=" + iosResult.notReadyReason);
                    // deviceUnlocked + confirmedUnlockedAtMs are injected in the pre-launch check below
                    // so the timestamp is as close to Gradle start as possible.
                    client.sendLog(job.executionId, "INFO",
                            "[JobExecutor] 🔗 Estado dispositivo → Gradle: xctrace="
                            + iosResult.xctraceConfirmed + " transport=" + iosResult.transportType
                            + " tunnel=" + iosResult.tunnelState + " pairing=" + iosResult.pairingState
                            + " readyForExecution=" + iosResult.readyForExecution
                            + (iosResult.notReadyReason != null ? " reason=" + iosResult.notReadyReason : ""));
                }
            }

            // Notificar TOTAL_ESPERADO DESPUÉS de construir el comando para usar
            // el conteo real: para smoke = número de --tests flags seleccionados;
            // para clases = lookup en SUITE_FILTER_SIZE. Esto garantiza que
            // la barra de progreso siempre coincide con lo que realmente se ejecuta.
            int expectedCount = resolveExpectedCountFromCommand(job, cmd);
            if (expectedCount > 0) {
                client.sendLog(job.executionId, "INFO", "⚡ TOTAL_ESPERADO:" + expectedCount);
                client.sendLog(job.executionId, "INFO", "📊 Casos seleccionados: " + expectedCount);
            }
            client.sendLog(job.executionId, "INFO",
                    "🔧 Comando: " + String.join(" ", cmd));
            System.out.println("[Executor] Comando: " + String.join(" ", cmd));

            // ── Pre-launch iOS unlock gate ─────────────────────────────────────
            // Last chance to abort before Gradle starts. This catches devices that
            // auto-locked during the preflight (team-id search, WDA warm-start, etc.).
            // Runs only for iOS; Android has no equivalent lock-state concept here.
            if (!isAndroid && iosResult != null) {
                if (!iosResult.readyForExecution) {
                    // Preflight already determined the device is not ready — abort immediately
                    // rather than letting Gradle start just to fail inside IOSDeviceSynchronizationManager.
                    client.sendLog(job.executionId, "ERROR",
                            "❌ Dispositivo no listo para ejecución — Gradle NO será lanzado.\n"
                            + "   Motivo: " + iosResult.notReadyReason + "\n"
                            + "   Corrige el problema y reintenta desde el Dashboard.");
                    client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                    return;
                }

                // Re-check lock state right before Gradle start — the confirmedUnlockedAtMs
                // timestamp injected here is what DriverFactory uses to compute elapsed time.
                DeviceScreenLockChecker.LockState preLaunch =
                        DeviceScreenLockChecker.check(receivedUdid);
                if (!preLaunch.unlocked) {
                    client.sendLog(job.executionId, "ERROR",
                            "🔒 Dispositivo bloqueado justo antes de lanzar Gradle — ejecución cancelada.\n"
                            + "   El dispositivo se bloqueó entre el Pre-flight y el inicio de la ejecución.\n"
                            + "   Desbloquea el iPhone y reintenta.");
                    client.sendResult(job.executionId, 0, 0, 0, null, List.of());
                    return;
                }
                client.sendLog(job.executionId, "INFO",
                        "🔓 Device unlocked: YES ✅ — confirmado antes de lanzar Gradle.");
                cmd.add("-DiosState.deviceUnlocked=true");
                cmd.add("-DiosState.confirmedUnlockedAtMs=" + preLaunch.checkedAtMs);
            } else if (!isAndroid) {
                // No iosResult (shouldn't happen for iOS) — pass safe defaults
                cmd.add("-DiosState.deviceUnlocked=true");
                cmd.add("-DiosState.confirmedUnlockedAtMs=" + System.currentTimeMillis());
            }

            // ── iOS video recording (physical device only) ─────────────────────
            // Uses xcrun devicectl device recordVideo — independent of WDA status.
            // Recording starts before the Appium session and stops after test execution.
            if (!isAndroid && job.videoEnabled && iosResult != null) {
                File videosDir = Paths.get(workDir, "build", "videos").toFile();
                iosRecordingActive = IOSVideoRecordingManager.start(
                        client, job.executionId, receivedUdid, videosDir) != null;
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);

            // ── Android SDK — Plug & Play (no manual ANDROID_HOME required) ──────
            AndroidEnvironmentBootstrap androidEnv = AndroidEnvironmentBootstrap.get();
            androidEnv.logStatus(job.executionId, client);
            if (androidEnv.isValid()) {
                pb.environment().putAll(androidEnv.buildEnv());
            }

            // ── Test environment variables (read via System.getenv() in tests) ───
            pb.environment().put("SUITE_ID",      nvl(job.suite));
            pb.environment().put("ENV",            nvl(job.env));
            pb.environment().put("DEVICE_NAME",    nvl(job.device));
            pb.environment().put("COUNTRY",        nvl(job.country));
            pb.environment().put("APPIUM_HUB",     config.appiumHub);
            pb.environment().put("EXECUTION_NAME", nvl(job.suite));
            pb.environment().put("REUSE_DRIVER",   "true");
            pb.environment().put("VIDEO_ENABLED",  String.valueOf(job.videoEnabled));
            if (job.testClass != null && !job.testClass.isBlank()) {
                pb.environment().put("TEST_CLASS", job.testClass);
            }
            if (job.sendMail && job.reportEmails != null && !job.reportEmails.isBlank()) {
                pb.environment().put("MAIL_TO", job.reportEmails);
            }

            // ── STARTING → RUNNING: Gradle arrancó, tests en ejecución activa ──
            try { client.sendRunning(job.executionId); } catch (Exception ignored) {}

            Process process = pb.start();
            activeProcess = process;

            // Abort watcher — polls backend every 1s; kills Gradle tree si ABORTING/ABORTED
            Thread abortWatcher = new Thread(() -> {
                while (process.isAlive()) {
                    try { Thread.sleep(1_000); } catch (InterruptedException e) { return; }
                    if (client.isJobAborted(job.executionId)) {
                        wasAborted.set(true);
                        client.sendLog(job.executionId, "WARN",
                            "🛑 Aborto recibido — deteniendo proceso Gradle...");
                        System.out.println("\n[Executor] Aborto detectado — terminando árbol de procesos Gradle");
                        forceKillProcessTree(process);
                        // Confirmar al backend que el proceso fue terminado (marca ABORTED + libera device)
                        client.confirmAbort(job.executionId);
                        resultSent.set(true); // confirmAbort finalizes the execution — do NOT call sendResult()
                        client.sendLog(job.executionId, "WARN", "⛔ Ejecución abortada correctamente.");
                        return;
                    }
                }
            }, "abort-watcher-" + job.executionId);
            abortWatcher.setDaemon(true);
            abortWatcher.start();

            AtomicInteger streamIncidents = new AtomicInteger(0);
            consumeProcessOutputResilient(job, process, passed, failed, skipped, testCases,
                    expectedCount, streamIncidents);

            // ── Resilient waitFor ──────────────────────────────────────────────────
            // process.waitFor() can receive an InterruptedException when the Runner
            // lifecycle calls jobPollThread.interrupt() (e.g. STOP/RESTART command).
            // An interrupt does NOT mean Gradle finished — if process.isAlive() is
            // still true we MUST keep waiting; otherwise we finalize a live execution.
            //
            // Defence-in-depth: RunnerAgent.stopAllServices() now kills the process
            // BEFORE interrupting the thread, so the process should already be dead
            // when the interrupt arrives.  This loop handles any case where the
            // interrupt reaches waitFor() before the kill (race, JVM shutdown hook,
            // future refactors, etc.).
            int exitCode;
            {
                boolean wasInterrupted = false;
                exitCode = 0;
                while (true) {
                    try {
                        exitCode = process.waitFor();
                        break; // process finished normally
                    } catch (InterruptedException ie) {
                        wasInterrupted = true;
                        boolean alive = process.isAlive();
                        // ── Diagnostic log (always printed) ─────────────────────
                        System.out.printf("[Runner] ⚠ waitFor() fue interrumpido%n");
                        System.out.printf("[Runner]   Hilo actual        : %s%n",
                                Thread.currentThread().getName());
                        System.out.printf("[Runner]   Estado process.isAlive(): %s%n", alive);
                        System.out.printf("[Runner]   Stack completa:%n%s%n", getStackTrace(ie));
                        try {
                            client.sendLog(job.executionId, "WARN",
                                    "[Runner] waitFor() interrumpido | process.isAlive(): " + alive
                                    + " | Hilo: " + Thread.currentThread().getName());
                        } catch (Exception ignored) {}

                        if (!alive) {
                            // Proceso ya terminó — leer exitValue() y continuar
                            System.out.println("[Runner] Proceso ya terminado — leyendo exitValue()");
                            try { exitCode = process.exitValue(); } catch (Exception ev) { exitCode = -1; }
                            break;
                        }

                        // Proceso sigue vivo: NO finalizar.  Limpiar el flag de
                        // interrupción para poder re-entrar en waitFor().
                        Thread.interrupted(); // clear interrupt flag
                        System.out.println("[Runner] Proceso Gradle sigue vivo — ignorando interrupción, re-esperando terminación real");
                        try {
                            client.sendLog(job.executionId, "WARN",
                                    "[Runner] Proceso Gradle sigue vivo — continuando monitoreo (no se finalizará hasta que el proceso termine)");
                        } catch (Exception ignored) {}
                    }
                }
                activeProcess = null;
                abortWatcher.interrupt();
                // Restaurar el flag para que el bucle job-poll detecte la señal
                // de parada y salga limpiamente tras el retorno de execute().
                if (wasInterrupted) Thread.currentThread().interrupt();
            }

            // Stop recording before any other work (must precede uploadVideos to finalize MP4)
            if (iosRecordingActive) IOSVideoRecordingManager.stop(client, job.executionId);

            if (wasAborted.get()) {
                // On abort: still clean up the device so banner disappears
                if (isPlatformIos && !iosUdid.isBlank()) {
                    IOSExecutionCleanupManager.cleanup(client, job.executionId, iosUdid,
                            config.appiumHub.replaceAll("/wd/hub$", ""));
                    iosCleanupDone = true;
                }
                client.sendLog(job.executionId, "WARN", "Ejecución abortada por el usuario");
                System.out.println("[Executor] Job abortado: " + job.executionId);
                resultSent.set(true); // confirmAbort() was already called by the abort-watcher thread
                return;
            }

            System.out.printf("[Executor] Gradle terminó | exit=%d | %s%n", exitCode, job.executionId);
            // Diagnóstico completo del proceso antes de transicionar a FINALIZING —
            // deja constancia de que la finalización se basa en una terminación real
            // del proceso (isAlive=false) y no en un efecto colateral como Stream closed.
            logProcessDiagnostics("pre-finalize", job, process, exitCode);

            // If Gradle crashed with no test output (e.g. compilation error),
            // record at least one failure so the execution doesn't finish as PASSED.
            if (passed.get() == 0 && failed.get() == 0 && skipped.get() == 0 && exitCode != 0) {
                failed.incrementAndGet();
            }

            String summary = passed.get() + " PASSED · "
                           + failed.get() + " FAILED · "
                           + skipped.get() + " SKIPPED";

            // ── Validación obligatoria antes de FINALIZING ────────────────────────
            // Condiciones requeridas:
            //   1. remainingTests == 0  (no hay tests pendientes)
            //   2. runningWorkers == 0  (proceso Gradle terminó — waitFor() ya retornó)
            //   3. activeDrivers == 0   (Appium sessions cerradas por el framework de tests)
            //   4. activeTestMethods == 0 (proceso Gradle terminó)
            // Las condiciones 2, 3 y 4 se garantizan porque process.waitFor() ya retornó.
            int actualTotal = passed.get() + failed.get() + skipped.get();
            int incidents   = streamIncidents.get();

            // ── Resumen obligatorio de fin de suite ───────────────────────────────
            // TOTAL_INICIADOS se reporta igual a TOTAL_SELECCIONADOS: Gradle valida y
            // acepta los N filtros --tests ANTES de ejecutar cualquiera (confirmado:
            // "Gradle recibe correctamente los 50 --tests"), y al no haber paralelismo
            // configurado (sin maxParallelForks/forkEvery) solo un test está en curso
            // a la vez — no hay una señal de Gradle distinta de PASSED/FAILED/SKIPPED
            // para "iniciado pero no terminado" sin habilitar testLogging{events
            // "started"} (fuera de alcance de este fix). NO_FINALIZADOS es la métrica
            // que realmente importa: cuántos de los seleccionados nunca produjeron un
            // resultado terminal.
            int totalSeleccionados = expectedCount > 0 ? expectedCount : actualTotal;
            int totalIniciados     = totalSeleccionados;
            int noFinalizados      = Math.max(0, totalSeleccionados - actualTotal);

            System.out.println("[Runner] TOTAL_SELECCIONADOS: " + totalSeleccionados);
            System.out.println("[Runner] TOTAL_INICIADOS: " + totalIniciados);
            System.out.println("[Runner] TOTAL_FINALIZADOS: " + actualTotal);
            System.out.println("[Runner] PASSED: " + passed.get());
            System.out.println("[Runner] FAILED: " + failed.get());
            System.out.println("[Runner] SKIPPED: " + skipped.get());
            System.out.println("[Runner] NO_INICIADOS: " + noFinalizados);
            System.out.println("[Runner] NO_FINALIZADOS: " + noFinalizados);
            try {
                client.sendLog(job.executionId, "INFO", String.format(
                        "[Runner] Resumen final | SELECCIONADOS=%d INICIADOS=%d FINALIZADOS=%d "
                        + "PASSED=%d FAILED=%d SKIPPED=%d NO_FINALIZADOS=%d",
                        totalSeleccionados, totalIniciados, actualTotal,
                        passed.get(), failed.get(), skipped.get(), noFinalizados));
            } catch (Exception ignored) {}

            if (expectedCount > 0 && actualTotal != expectedCount) {
                // Discrepancia real entre lo planificado y lo ejecutado/contado. NUNCA se
                // ajusta esto en silencio: se registra la causa probable de forma explícita
                // ANTES de sincronizar TOTAL_ESPERADO (necesario para que el Dashboard no
                // muestre tests pendientes eternamente — su lógica no se modifica, solo se
                // antepone este diagnóstico).
                String causaProbable = incidents > 0
                        ? "posible pérdida de conteo durante " + incidents + " incidente(s) de stream "
                          + "(reposo/USB/Appium) — ver logs '[Executor] Stream de salida interrumpido' de esta ejecución"
                        : "Gradle/JVM terminó (exit=" + exitCode + ") antes de completar todos los casos "
                          + "planificados — sin incidentes de stream registrados; revisar causa en el log de Gradle "
                          + "(fallo de compilación, crash de la JVM de test, etc.)";
                String errMsg = String.format(
                        "[Runner] ⚠ VALIDACIÓN FALLIDA: ejecutados (%d) != planificados (%d) | incidentesStream=%d | causa probable: %s",
                        actualTotal, expectedCount, incidents, causaProbable);
                System.err.println(errMsg);
                try { client.sendLog(job.executionId, "ERROR", errMsg); } catch (Exception ignored) {}

                // Sincroniza TOTAL_ESPERADO con la realidad (comportamiento preexistente,
                // requerido por el Dashboard) — pero ya quedó registrada la causa explícita
                // arriba, en vez de ajustar el conteo en silencio.
                client.sendLog(job.executionId, "INFO", "⚡ TOTAL_ESPERADO:" + actualTotal);
                System.out.printf("[Executor] [FINALIZING] Conteo actualizado: %d ejecutados de %d esperados%n",
                        actualTotal, expectedCount);
            } else if (expectedCount > 0) {
                System.out.println("[Runner] Validación OK: Total ejecutado coincide con el total planificado.");
            }
            System.out.printf("[Executor] [FINALIZING] Validación: tests=%d workers=0 drivers=0 methods=0 → OK%n",
                    actualTotal);
            System.out.println("[Executor] [FINALIZING] Transicionando RUNNING → FINALIZING…");
            try { client.sendFinalizing(job.executionId); } catch (Exception ignored) {}

            // ── Post-processing: each step is isolated so a failure in one never skips the rest ──

            // [POST-1] Resumen de ejecución
            System.out.println("[Executor] [POST-1] Enviando resumen de ejecución…");
            try {
                client.sendLog(job.executionId, "INFO",
                        exitCode == 0
                            ? "✅ Suite completada — " + summary
                            : "❌ Suite terminó con errores (exit " + exitCode + ") — " + summary);
            } catch (Exception ex) {
                System.err.println("[Executor] [POST-1] Error al enviar resumen: " + ex.getMessage());
            }

            // [POST-2] Cleanup de dispositivo iOS (si aplica)
            if (isPlatformIos && !iosUdid.isBlank()) {
                System.out.println("[Executor] [POST-2] Iniciando cleanup de dispositivo iOS…");
                try {
                    IOSExecutionCleanupManager.cleanup(client, job.executionId, iosUdid,
                            config.appiumHub.replaceAll("/wd/hub$", ""));
                    iosCleanupDone = true;
                    System.out.println("[Executor] [POST-2] Cleanup iOS completado.");
                } catch (Exception ex) {
                    System.err.println("[Executor] [POST-2] Error en cleanup iOS:\n" + getStackTrace(ex));
                    try { client.sendLog(job.executionId, "WARN",
                            "[POST-2] Error en cleanup iOS — " + describeException(ex)); } catch (Exception ignored) {}
                }
            }

            // [POST-3] Upload de videos
            System.out.println("[Executor] [POST-3] Procesando videos…");
            try {
                if (iosRecordingActive || !isPlatformIos) {
                    uploadVideos(job.executionId, job.suite, workDir);
                }
                System.out.println("[Executor] [POST-3] Videos procesados.");
            } catch (Exception ex) {
                System.err.println("[Executor] [POST-3] Error al subir videos:\n" + getStackTrace(ex));
                try { client.sendLog(job.executionId, "WARN",
                        "[POST-3] Error al subir videos — " + describeException(ex)); } catch (Exception ignored) {}
            }

            // [POST-4] Generación de reporte Allure
            System.out.println("[Executor] [POST-4] Generando reporte Allure…");
            String allureUrl = null;
            try {
                allureUrl = generateAllureReport(job.executionId, workDir);
                System.out.println("[Executor] [POST-4] Allure: "
                        + (allureUrl != null ? allureUrl : "no disponible (allure-cli ausente o fallo)"));
            } catch (Exception ex) {
                System.err.println("[Executor] [POST-4] Error en Allure:\n" + getStackTrace(ex));
            }

            // [POST-5] Envío de resultado final al Backend (obligatorio)
            System.out.println("[Executor] [POST-5] Enviando resultado final al Backend…");
            client.sendResult(job.executionId,
                    passed.get(), failed.get(), skipped.get(), allureUrl, testCases);
            resultSent.set(true);
            System.out.println("[Executor] ✓ Finalizado correctamente: " + job.executionId
                    + " — " + summary);

        } catch (Exception e) {
            // Each step is independently protected — a failure here must never prevent sendResult()
            try { if (iosRecordingActive) IOSVideoRecordingManager.stop(client, job.executionId); } catch (Exception ignored) {}
            // iOS cleanup in the catch path — runs before sendResult so messages are visible
            if (isPlatformIos && !iosUdid.isBlank() && !iosCleanupDone) {
                try {
                    IOSExecutionCleanupManager.cleanup(client, job.executionId, iosUdid,
                            config.appiumHub.replaceAll("/wd/hub$", ""));
                    iosCleanupDone = true;
                } catch (Exception ignored) {}
            }
            String stackTrace = getStackTrace(e);
            System.err.println("[Executor] Error fatal en ejecución " + job.executionId + ":\n" + stackTrace);
            try { client.sendLog(job.executionId, "ERROR",
                    "❌ Error interno del runner: " + describeException(e)
                    + "\n" + stackTrace.lines().limit(10).reduce("", (a, b) -> a + b + "\n")); } catch (Exception ignored) {}
            try {
                client.sendResult(job.executionId,
                        passed.get(), Math.max(failed.get(), 1), skipped.get(), null, testCases);
                resultSent.set(true);
            } catch (Exception ignored) {}
        } finally {
            // Safety net: guarantees cleanup and execution finalization regardless of what failed above.
            // IOSVideoRecordingManager.stop() is idempotent — no-op if already called.
            try { if (iosRecordingActive) IOSVideoRecordingManager.stop(client, job.executionId); } catch (Exception ignored) {}
            if (!iosCleanupDone && isPlatformIos && !iosUdid.isBlank()) {
                try {
                    IOSExecutionCleanupManager.cleanup(client, job.executionId, iosUdid,
                            config.appiumHub.replaceAll("/wd/hub$", ""));
                } catch (Exception ignored) {}
            }
            // If neither the happy path nor the catch block sent a result, finalize here.
            // This handles the case where the catch block itself throws before reaching sendResult().
            if (!resultSent.get() && !wasAborted.get()) {
                System.err.println("[Executor] ⚠ [finally] Ejecución " + job.executionId
                        + " sin resultado previo — ejecutando sendResult de emergencia");
                try {
                    client.sendLog(job.executionId, "ERROR",
                            "⚠ Ejecución finalizada por safety-net del runner. Revise los logs para más detalles.");
                    client.sendResult(job.executionId,
                            passed.get(), Math.max(failed.get(), 1), skipped.get(), null, testCases);
                } catch (Exception ignored) {}
            }
        }
    }

    // ── Resilient process-output consumption ────────────────────────────────────
    //
    // Cuando el equipo host entra en reposo, la pantalla se apaga o el dispositivo
    // pierde actividad temporalmente, el pipe stdout/stderr del proceso Gradle puede
    // arrojar "java.io.IOException: Stream closed" en BufferedReader.readLine() aun
    // cuando el proceso sigue vivo y la suite continúa ejecutándose.  Ese IOException
    // NUNCA debe propagarse hacia el catch(Exception) de execute() — allí se
    // interpretaría como un fallo fatal y se finalizaría la ejecución con FAILED,
    // aunque Gradle siga corriendo.  Este método aísla esa lectura y decide, en cada
    // interrupción, si el proceso realmente terminó (dejar de leer y avanzar a
    // waitFor()/exitValue()) o si sigue vivo (recrear el lector y continuar, con
    // reintento de reconexión a Appium de por medio).
    //
    // ── CAUSA RAÍZ REAL del corte a ~9, 10 o 19 casos de 50 (encontrada al auditar
    // el fix anterior) ──────────────────────────────────────────────────────────
    // `try (BufferedReader br = new BufferedReader(new InputStreamReader(
    //      process.getInputStream())))` es un try-with-resources: la especificación
    // de Java (JLS §14.20.3) garantiza que `br.close()` se invoca SIEMPRE al salir
    // del bloque try, tanto en el camino normal como cuando una excepción es
    // atrapada por el catch adjunto. `close()` en un BufferedReader se propaga al
    // InputStreamReader y de ahí al InputStream subyacente — en este caso, el MISMO
    // stream que devuelve process.getInputStream() durante TODO el ciclo de vida del
    // proceso (Process no abre un stream nuevo en cada llamada; siempre retorna la
    // misma referencia). Es decir: la primera vez que se lanzaba una IOException
    // (p. ej. por reposo del equipo, USB o Appium) el propio try-with-resources
    // cerraba PARA SIEMPRE el stream de stdout del proceso Gradle — aunque el
    // proceso seguía vivo ejecutando el resto de la suite. El "reintento sin
    // límite" que se agregó antes seguía envolviendo ese MISMO stream ya muerto en
    // un BufferedReader nuevo en cada vuelta del bucle, así que TODOS los
    // reintentos fallaban de inmediato con el mismo "Stream closed" — la lectura
    // jamás se recuperaba y passed/failed/skipped quedaban congelados en el punto
    // exacto del primer glitch (caso 9, 10, 19… lo que sea que haya coincidido con
    // el reposo/desconexión), mientras Gradle seguía corriendo en segundo plano
    // hasta agotar los 50 casos sin que ninguno más se contara.
    //
    // FIX: eliminar el try-with-resources. El BufferedReader NUNCA se cierra
    // explícitamente mientras el proceso esté vivo — solo se descarta la
    // referencia (se recolecta por GC normalmente; no retiene ningún descriptor
    // de archivo propio, el pipe pertenece al Process) y se envuelve de nuevo el
    // MISMO InputStream subyacente, que sigue intacto porque nunca lo cerramos.

    private void consumeProcessOutputResilient(JobDto job, Process process,
            AtomicInteger passed, AtomicInteger failed, AtomicInteger skipped,
            List<TestCaseResult> testCases, int expectedCount, AtomicInteger streamIncidents) {

        int consecutiveFailures = 0;
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Sin límite de reintentos: mientras Gradle siga vivo, se sigue intentando
        // leer — de lo contrario, cualquier corte temporal dejaría de contar
        // PASS/FAIL/SKIP para el resto de la suite aunque Gradle siguiera
        // ejecutando decenas de tests más.
        while (process.isAlive()) {

            try {
                String line;
                while ((line = br.readLine()) != null) {
                    consecutiveFailures = 0; // el stream volvió a estar sano
                    String level = detectLevel(line);
                    client.sendLog(job.executionId, level, line);
                    System.out.println("[" + level + "] " + line);
                    boolean isCaseResult = false;
                    if      ("PASS".equals(level)) { passed.incrementAndGet();  testCases.add(new TestCaseResult(extractTestName(line), "PASS")); isCaseResult = true; }
                    else if ("FAIL".equals(level)) { failed.incrementAndGet();  testCases.add(new TestCaseResult(extractTestName(line), "FAIL")); isCaseResult = true; }
                    else if ("SKIP".equals(level)) { skipped.incrementAndGet(); testCases.add(new TestCaseResult(extractTestName(line), "SKIP")); isCaseResult = true; }
                    if (isCaseResult) {
                        logCaseProgress(job, process, passed.get() + failed.get() + skipped.get(),
                                expectedCount, streamIncidents.get());
                    }
                }

                // readLine() devolvió null → EOF.
                if (!process.isAlive()) {
                    System.out.println("[Executor] EOF en stdout/stderr tras finalización real del proceso.");
                    return;
                }
                // EOF con el proceso AÚN VIVO: un pipe normal no debería cerrarse así
                // salvo que el hijo cerrara su propio stdout sin salir (glitch tras
                // reposo del equipo / desconexión USB). Esto NUNCA se interpreta como
                // fin de la suite: se recrea el lector — SIN cerrar el anterior, para
                // no matar el stream subyacente — y se sigue leyendo mientras el
                // proceso exista.
                consecutiveFailures++;
                streamIncidents.incrementAndGet();
                logStreamDisruption(job, "EOF-con-proceso-vivo", null, consecutiveFailures);
                attemptStreamRecovery(job, consecutiveFailures);
                br = new BufferedReader(new InputStreamReader(process.getInputStream()));

            } catch (IOException ioe) {
                if (!process.isAlive()) {
                    // El cierre del stream es consecuencia de que el proceso terminó,
                    // no la causa de la finalización — no es un error a reportar.
                    System.out.println("[Executor] Stream cerrado tras finalización real del proceso (isAlive=false).");
                    return;
                }
                consecutiveFailures++;
                streamIncidents.incrementAndGet();
                logStreamDisruption(job, diagnoseStreamDisruption(job), ioe.getMessage(), consecutiveFailures);
                attemptStreamRecovery(job, consecutiveFailures);
                // IMPORTANTE: NO se llama a br.close() en ningún punto de este método.
                // Solo se reemplaza la referencia — el InputStream subyacente de
                // process.getInputStream() nunca se cierra mientras el proceso viva,
                // así que envolverlo de nuevo aquí SÍ puede seguir leyendo datos reales.
                br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            }
        }
        System.out.println("[Executor] Proceso ya no está vivo — fin de la lectura de stdout/stderr.");
    }

    /**
     * Instrumentación solicitada: progreso caso-por-caso mientras la suite corre.
     * streamsActivos=false solo mientras el bucle de lectura está en medio de un
     * reintento tras un incidente — no implica que el proceso ni la suite hayan
     * terminado.
     */
    private void logCaseProgress(JobDto job, Process process, int executed, int expectedCount, int streamIncidents) {
        int planned   = expectedCount > 0 ? expectedCount : executed; // -1/0 = desconocido, se usa el propio conteo
        int remaining = Math.max(0, planned - executed);
        String msg = String.format(
                "[Runner] Caso actual: %d/%d | Tests restantes: %d | Gradle alive: %s | Streams activos: %s | Workers activos: %d | Estado ejecución: RUNNING%s",
                executed, planned, remaining, process.isAlive(), streamIncidents == 0,
                1, streamIncidents > 0 ? " | incidentesStream=" + streamIncidents : "");
        System.out.println(msg);
        try { client.sendLog(job.executionId, "DEBUG", msg); } catch (Exception ignored) {}
    }

    /**
     * Log local SIEMPRE (para diagnóstico completo); log al backend acotado
     * (intento 1, luego cada 10) para no saturar el canal de logs durante un
     * corte largo — la suite puede seguir corriendo horas sin que esto se pierda
     * ni se convierta en spam.
     */
    private void logStreamDisruption(JobDto job, String reason, String detail, int attempt) {
        System.err.printf(
                "[Executor] ⚠ Stream de salida interrumpido (%s%s) | intento=%d | proceso vivo — reintentando sin límite%n",
                reason, detail != null ? (": " + detail) : "", attempt);
        if (attempt == 1 || attempt % 10 == 0) {
            try {
                client.sendLog(job.executionId, "WARN",
                        "[Runner] Lectura de logs interrumpida (" + reason + ") — intento " + attempt
                        + ". El proceso sigue vivo; la suite continúa y NO se finaliza por este motivo.");
            } catch (Exception ignored) {}
        }
    }

    /** Backoff acotado + intento de reconexión a Appium cuando el stream se corta con el proceso vivo. */
    private void attemptStreamRecovery(JobDto job, int attempt) {
        long backoffMs = Math.min(2_000L * attempt, 10_000L);
        try { Thread.sleep(backoffMs); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }

        if (appiumMgr != null && !appiumMgr.isAlive()) {
            try {
                client.sendLog(job.executionId, "WARN",
                        "[Runner] Appium no responde tras interrupción de stream — intentando reconectar...");
                appiumMgr.ensureRunning();
                client.sendLog(job.executionId, "INFO",
                        "[Runner] Appium " + (appiumMgr.isAlive() ? "reconectado ✅" : "sigue sin responder ⚠"));
            } catch (Exception e) {
                System.err.println("[Executor] No fue posible reconectar Appium: " + e.getMessage());
            }
        }
    }

    /**
     * Determina la causa más probable de una interrupción de stream con el proceso
     * aún vivo, en lugar de reportar genéricamente "Stream closed":
     *   - DeviceDisconnected  → el dispositivo (Android/iOS) ya no responde
     *   - AppiumDisconnected  → el dispositivo responde pero Appium no
     *   - ComputerSleep       → dispositivo y Appium responden; el patrón típico
     *                           de una suspensión momentánea del equipo host
     */
    private String diagnoseStreamDisruption(JobDto job) {
        boolean isAndroid = !"ios".equalsIgnoreCase(nvl(job.platform, ""));
        String  udid      = nvl(job.udid, "");
        Boolean deviceConnected = isAndroid ? isAndroidDeviceConnected(udid) : isIosDeviceReachable(udid);
        boolean appiumOk        = appiumMgr != null && appiumMgr.isAlive();

        if (Boolean.FALSE.equals(deviceConnected)) return "DeviceDisconnected";
        if (!appiumOk)                             return "AppiumDisconnected";
        return "ComputerSleep";
    }

    /** True/false si se pudo determinar el estado del dispositivo Android via ADB; null si no se pudo determinar. */
    private Boolean isAndroidDeviceConnected(String udid) {
        if (udid == null || udid.isBlank()) return null;
        try {
            Process p = new ProcessBuilder(embeddedAdbPath(), "devices")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor(5, TimeUnit.SECONDS);
            return Arrays.stream(output.split("\n"))
                    .anyMatch(l -> l.startsWith(udid) && l.contains("\tdevice"));
        } catch (Exception e) {
            return null;
        }
    }

    /** True/false si el dispositivo iOS respondió a la consulta devicectl; null si no se pudo determinar. */
    private Boolean isIosDeviceReachable(String udid) {
        if (udid == null || udid.isBlank()) return null;
        try {
            DeviceScreenLockChecker.LockState state = DeviceScreenLockChecker.check(udid);
            return "devicectl".equals(state.method);
        } catch (Exception e) {
            return null;
        }
    }

    /** Log detallado del estado del proceso/Appium/dispositivo antes de finalizar una ejecución. */
    private void logProcessDiagnostics(String stage, JobDto job, Process process, int exitCode) {
        boolean isAndroid = !"ios".equalsIgnoreCase(nvl(job.platform, ""));
        String  udid      = nvl(job.udid, "");
        Boolean deviceConnected = isAndroid ? isAndroidDeviceConnected(udid) : isIosDeviceReachable(udid);
        String  deviceState = deviceConnected == null ? "DESCONOCIDO"
                : (deviceConnected ? "CONECTADO" : "DESCONECTADO");
        boolean appiumOk = appiumMgr != null && appiumMgr.isAlive();
        String  pid;
        try { pid = String.valueOf(process.pid()); } catch (Exception e) { pid = "unknown"; }

        String msg = String.format(
                "[Runner] Diagnóstico de proceso [%s] | isAlive=%s | PID=%s | exitCode=%d | Appium=%s | Dispositivo=%s",
                stage, process.isAlive(), pid, exitCode, appiumOk ? "OK" : "NO_DISPONIBLE", deviceState);
        System.out.println(msg);
        try { client.sendLog(job.executionId, "INFO", msg); } catch (Exception ignored) {}
    }

    // ── Gradle command builder ─────────────────────────────────────────────────

    private List<String> buildCommand(JobDto job) {
        String key = job.suite != null ? job.suite.toLowerCase().trim() : "";

        // Smoke usa su propio comando con selección aleatoria de SMOKE_SIZE tests
        if ("smoke tests".equals(key) || "smoke".equals(key)) {
            return buildSmokeCommand(job);
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String  testFilter = resolveTestFilter(job.suite, job.testClass);

        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            cmd.add("cmd"); cmd.add("/c"); cmd.add("gradlew.bat");
        } else {
            cmd.add("./gradlew");
        }

        cmd.add("test");
        cmd.add("--tests");
        cmd.add(testFilter);
        cmd.add("--rerun-tasks");
        cmd.add("--no-daemon");

        addCommonDFlags(cmd, job);
        if (job.testClass != null && !job.testClass.isBlank())
            cmd.add("-DtestClass=" + job.testClass);

        return cmd;
    }

    /**
     * Construye el comando Gradle para la suite Smoke:
     *  1. Toma el pool de tests México (143 métodos aprox.)
     *  2. Baraja aleatoriamente y selecciona SMOKE_SIZE (50)
     *  3. Genera un --tests por cada método seleccionado
     *
     * Garantías:
     *  - PASSED + FAILED + SKIPPED == SMOKE_SIZE (Gradle solo ejecuta lo seleccionado)
     *  - La selección varía en cada ejecución (diversidad de cobertura)
     *  - Nunca más de SMOKE_SIZE tests, aunque el pool crezca
     */
    private List<String> buildSmokeCommand(JobDto job) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // Selección aleatoria
        List<String> pool = new ArrayList<>(MEXICO_SMOKE_POOL);
        java.util.Collections.shuffle(pool, new java.util.Random());
        int count = Math.min(SMOKE_SIZE, pool.size());
        List<String> selected = pool.subList(0, count);

        // Log de selección
        client.sendLog(job.executionId, "INFO",
            "🔀 Smoke seleccionado: " + count + " casos aleatorios de " + pool.size() + " disponibles");
        StringBuilder sb = new StringBuilder("Casos elegidos:");
        for (int i = 0; i < selected.size(); i++) {
            String s = selected.get(i);
            sb.append("\n  ").append(i + 1).append(". ")
              .append(s.substring(s.lastIndexOf('.') + 1));
        }
        client.sendLog(job.executionId, "INFO", sb.toString());

        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            cmd.add("cmd"); cmd.add("/c"); cmd.add("gradlew.bat");
        } else {
            cmd.add("./gradlew");
        }

        cmd.add("test");
        for (String test : selected) {
            cmd.add("--tests");
            cmd.add(test);
        }
        cmd.add("--rerun-tasks");
        cmd.add("--no-daemon");

        addCommonDFlags(cmd, job);
        return cmd;
    }

    /** Añade los -D flags comunes a cualquier comando Gradle de test. */
    private void addCommonDFlags(List<String> cmd, JobDto job) {
        // Prefer discovered deviceName, fall back to user selection
        String deviceName = (job.deviceName != null && !job.deviceName.isBlank())
                ? job.deviceName : nvl(job.device, "Galaxy A56 5G");
        cmd.add("-DdeviceName="    + deviceName);
        cmd.add("-Denv="           + nvl(job.env,     "QA"));
        cmd.add("-Dcountry="       + nvl(job.country, "mexico"));

        // Platform — DriverFactory.createDriverWithRetries() reads prop("platformName","Android").
        // Without this flag it always defaults to Android even when the device is an iPhone.
        String platformName = "ios".equalsIgnoreCase(job.platform) ? "iOS" : "Android";
        cmd.add("-DplatformName=" + platformName);
        System.out.println("[JobExecutor] 🖥  Driver platform: " + platformName
                + " | raw platform field: " + nvl(job.platform, "<null>"));
        // Normalize: strip legacy /wd/hub suffix — Appium 2.x/3.x uses bare base URL
        String cleanAppiumHub = config.appiumHub.replaceAll("/wd/hub$", "");
        cmd.add("-Dappium.hub=" + cleanAppiumHub);
        cmd.add("-DexecutionName=" + nvl(job.suite,   "Suite"));
        cmd.add("-DREUSE_DRIVER=true");

        // Dynamic device capabilities from Device Farm discovery
        if (job.udid != null && !job.udid.isBlank()) {
            cmd.add("-Dudid=" + job.udid);
        }
        // Guard: never propagate "unknown" — Appium rejects it with SessionNotCreatedException.
        // IosPreflightManager.detectIosVersion() overrides this with the real device version when available.
        if (job.platformVersion != null && !job.platformVersion.isBlank()
                && !"unknown".equalsIgnoreCase(job.platformVersion)) {
            cmd.add("-DplatformVersion=" + job.platformVersion);
        }

        if (job.videoEnabled) cmd.add("-Dvideo.enabled=true");
        if (job.sendMail)     cmd.add("-DsendMail=true");

        // Pass AGENT_DATA_DIR so DriverFactory can find the runtime Appium binary
        // (AGENT_DATA_DIR/runtime/appium/node_modules/appium/index.js) without relying
        // on the system PATH — which may not include the enterprise-installed Appium.
        if (config.agentDataDir != null && !config.agentDataDir.isBlank()) {
            cmd.add("-DAGENT_DATA_DIR=" + config.agentDataDir);
        }
    }

    private static String resolveTestFilter(String suiteName, String testClass) {
        if (suiteName == null || suiteName.isBlank()) return "tests.RunAllTests";

        // Cuando se pide una clase específica dentro de una suite
        if (testClass != null && !testClass.isBlank()) {
            String key = suiteName.toLowerCase().trim();
            String base = SUITE_MAP.getOrDefault(key, null);
            if (base != null && !base.startsWith("§")) {
                String pkg = base.endsWith(".*") ? base.substring(0, base.length() - 2) : base;
                return pkg + "." + testClass;
            }
            return "tests.México.alimentos." + testClass;
        }

        String key = suiteName.toLowerCase().trim();
        String filter = SUITE_MAP.getOrDefault(key, "tests.RunAllTests");
        // §smoke§ no debería llegar aquí (manejado en buildCommand), pero por seguridad:
        return filter.startsWith("§") ? "tests.RunAllTests" : filter;
    }

    // ── Pre-flight: ADB ────────────────────────────────────────────────────────

    private String embeddedAdbPath() {
        String path = System.getProperty("ADB_PATH");
        return (path != null && !path.isBlank()) ? path : "adb";
    }

    /**
     * Removes stale ADB port forwarding for UiAutomator2's systemPort (default 8200).
     * A previous execution may have left tcp:8200 forwarded; if not removed, Appium cannot
     * re-establish it cleanly and the session creation fails with "systemPort 8200 is busy".
     */
    private void clearUiAutomator2SystemPort(String udid, String executionId) {
        try {
            Process p = new ProcessBuilder(embeddedAdbPath(), "-s", udid, "forward", "--remove", "tcp:8200")
                    .redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
            client.sendLog(executionId, "INFO",
                    "[Android] Limpieza previa: ADB forward tcp:8200 removido (previene conflicto systemPort UiAutomator2)");
        } catch (Exception ignored) {
            // Silently ignore — if there was no forward, "adb forward --remove" exits with error (that's expected)
        }
    }

    private void checkAdbDevices(String executionId) {
        try {
            Process p = new ProcessBuilder(embeddedAdbPath(), "devices")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();

            long count = Arrays.stream(output.split("\n"))
                    .filter(l -> l.contains("\tdevice"))
                    .count();

            client.sendLog(executionId, count > 0 ? "INFO" : "WARN",
                    count > 0
                        ? "📱 " + count + " dispositivo(s) Android detectado(s) via ADB"
                        : "⚠️  No se detectaron dispositivos ADB. Conecta el dispositivo y habilita depuración USB.");
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  ADB no disponible: " + e.getMessage());
        }
    }

    // checkIosDevices() replaced by IosPreflightManager.runPreflight() — see execute()

    /**
     * Resolves the real launcher Activity for an Android package via ADB.
     * Uses "cmd package resolve-activity --brief" which returns the component
     * the OS would launch for ACTION_MAIN / CATEGORY_LAUNCHER intents.
     *
     * Returns the fully-qualified activity name (e.g. com.example.app.SplashActivity)
     * or null when the package is not installed / command fails.
     */
    private String resolveLauncherActivity(String udid, String appPackage) {
        try {
            List<String> adbCmd = new ArrayList<>();
            adbCmd.add(embeddedAdbPath());
            if (!udid.isBlank()) { adbCmd.add("-s"); adbCmd.add(udid); }
            adbCmd.addAll(List.of("shell", "cmd", "package",
                    "resolve-activity", "--brief", appPackage));

            Process p = new ProcessBuilder(adbCmd)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);
            p.destroyForcibly();

            // Find the line in "package/activity" format
            for (String line : out.split("\\n")) {
                String t = line.trim();
                if (!t.contains("/")) continue;
                String[] parts = t.split("/", 2);
                if (parts.length == 2 && parts[0].trim().equals(appPackage)) {
                    String activity = parts[1].trim();
                    // Normalize relative form ".SplashActivity" → "com.x.SplashActivity"
                    return activity.startsWith(".") ? appPackage + activity : activity;
                }
            }
            System.err.printf("[JobExecutor] resolve-activity output for %s: %s%n", appPackage, out);
        } catch (Exception e) {
            System.err.println("[JobExecutor] resolveLauncherActivity error: " + e.getMessage());
        }
        return null;
    }

    // ── Pre-flight: XCUITest driver ────────────────────────────────────────────

    private boolean checkIosXcuitestDriver(String executionId) {
        client.sendLog(executionId, "INFO", "Verificando drivers Appium");
        if (appiumMgr == null) {
            client.sendLog(executionId, "WARN",
                    "Error de instalación: AppiumManager no disponible para verificar drivers.");
            return false;
        }
        // Use strict detection: require xcuitest@X.Y.Z (version present) to avoid false
        // positives from Appium versions that list all available drivers in --installed output.
        String installed = appiumMgr.getInstalledDriverList();
        if (AppiumManager.xcuitestIsInstalled(installed)) {
            client.sendLog(executionId, "INFO", "Driver XCUITest encontrado");
            return true;
        }
        client.sendLog(executionId, "INFO", "Instalando XCUITest");
        boolean ok = appiumMgr.ensureXcuitestInstalled();
        if (ok) {
            client.sendLog(executionId, "INFO", "Instalación completada");
            return true;
        }
        client.sendLog(executionId, "ERROR",
                "Error de instalación: no se pudo instalar el driver XCUITest. "
                + "Ejecuta manualmente: appium driver install xcuitest");
        return false;
    }

    // ── Pre-flight: Appium ─────────────────────────────────────────────────────

    private void checkAppiumServer(String executionId) {
        // If AppiumManager is wired, ask it to ensure Appium is running
        if (appiumMgr != null) {
            String hubBase = config.appiumHub.replaceAll("/wd/hub$", "");
            client.sendLog(executionId, "INFO", "📡 Appium endpoint: " + hubBase);
            if (!appiumMgr.isAlive()) {
                client.sendLog(executionId, "INFO", "Appium no responde — intentando iniciar...");
                try {
                    appiumMgr.ensureRunning();
                    client.sendLog(executionId, "INFO", "✅ Appium disponible");
                } catch (Exception e) {
                    client.sendLog(executionId, "WARN",
                            "❌ Appium no pudo iniciarse: " + e.getMessage());
                }
            } else {
                client.sendLog(executionId, "INFO", "✅ Appium disponible");
            }
            return;
        }

        // Fallback: passive check only (legacy path — no AppiumManager)
        String hubBase = config.appiumHub.replaceAll("/wd/hub$", "");
        client.sendLog(executionId, "INFO", "📡 Appium endpoint: " + hubBase);
        HttpClient http = HttpClient.newHttpClient();
        // Try Appium 2.x/3.x /status first; fall back to legacy /wd/hub/status
        for (String path : new String[]{"/status", "/wd/hub/status"}) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(hubBase + path))
                        .timeout(Duration.ofSeconds(5))
                        .GET().build();
                if (http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    client.sendLog(executionId, "INFO", "✅ Appium disponible");
                    return;
                }
            } catch (Exception ignored) {}
        }
        client.sendLog(executionId, "WARN",
                "❌ Appium no disponible en " + hubBase + " — inicia Appium con: appium --port 4723");
    }

    // ── Video upload ───────────────────────────────────────────────────────────

    private void uploadVideos(String executionId, String suite, String workDir) {
        try {
            Path videosDir = Paths.get(workDir, "build", "videos");
            if (!Files.exists(videosDir)) {
                client.sendLog(executionId, "INFO",
                        "📹 Sin videos: directorio build/videos no existe");
                return;
            }

            long[] count = {0};
            Files.walk(videosDir)
                    .filter(p -> p.toString().endsWith(".mp4"))
                    .filter(p -> p.toFile().length() > 0)
                    .forEach(p -> {
                        count[0]++;
                        String className = p.getParent().getFileName().toString();
                        String testName  = p.getFileName().toString()
                                .replace(".mp4", "")
                                .replace("_", " ")
                                .trim();
                        client.sendLog(executionId, "INFO",
                                "📹 Subiendo video: " + p.getFileName()
                                + " (" + p.toFile().length() / 1024 + " KB)");
                        client.uploadVideo(executionId, nvl(suite, className), testName, p);
                    });

            if (count[0] == 0) {
                client.sendLog(executionId, "WARN",
                        "📹 No se encontraron videos en build/videos (Appium no grabó)");
            } else {
                client.sendLog(executionId, "INFO",
                        "📹 " + count[0] + " video(s) subido(s) — disponibles en sección Videos");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "📹 Error al subir videos: " + e.getMessage());
            System.out.println("[Executor] No se pudieron subir videos: " + e.getMessage());
        }
    }

    // ── Pre-clean: kill stuck Gradle JVMs, then delete locked binary dir ─────
    // Gradle --rerun-tasks fails with IOException when a prior test JVM still
    // has build/test-results/test/binary/output.bin open. We first kill any
    // java.exe whose command line references "gradle" (the stuck build daemon
    // or test JVM), wait for it to die, then delete the directory ourselves.

    private void preCleanTestResults(String executionId, String workDir) {
        killStuckGradleProcesses(executionId);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // ── Clean binary test-results dir (locked by previous Gradle run) ────
        Path binaryDir = Paths.get(workDir, "build", "test-results", "test", "binary");
        if (Files.exists(binaryDir)) {
            if (isWindows) {
                try {
                    new ProcessBuilder("cmd", "/c", "rd", "/S", "/Q",
                            binaryDir.toAbsolutePath().toString())
                            .redirectErrorStream(true).start().waitFor(8, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            try {
                if (Files.exists(binaryDir)) {
                    Files.walk(binaryDir).sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            } catch (Exception ignored) {}

            if (!Files.exists(binaryDir)) {
                client.sendLog(executionId, "INFO", "🧹 Resultados de test anteriores eliminados");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️ No se pudo eliminar build/test-results/test/binary — Gradle lo intentará");
            }
        }

        // ── Clean stale videos so only this run's recordings get uploaded ────
        Path videosDir = Paths.get(workDir, "build", "videos");
        if (Files.exists(videosDir)) {
            if (isWindows) {
                try {
                    new ProcessBuilder("cmd", "/c", "rd", "/S", "/Q",
                            videosDir.toAbsolutePath().toString())
                            .redirectErrorStream(true).start().waitFor(8, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            try {
                if (Files.exists(videosDir)) {
                    Files.walk(videosDir).sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            } catch (Exception ignored) {}
            client.sendLog(executionId, "INFO", "🎬 Videos de ejecuciones anteriores eliminados");
        }
    }

    private void killStuckGradleProcesses(String executionId) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) return;

        long currentPid = ProcessHandle.current().pid();
        List<String> pidsToKill = new ArrayList<>();

        try {
            // Get-CimInstance replaces wmic (removed in Windows 11 22H2+).
            // Uses Where-Object pipeline with single-quoted literals to avoid
            // the double-quote escaping issues of -Filter string.
            String script =
                "$myPid = " + currentPid + "; " +
                "Get-CimInstance Win32_Process | " +
                "Where-Object { $_.Name -eq 'java.exe' -and " +
                "  $_.ProcessId -ne $myPid -and " +
                "  $_.CommandLine -like '*gradle*' -and " +
                "  $_.CommandLine -notlike '*appium*' } | " +
                "Select-Object -ExpandProperty ProcessId";

            Process ps = new ProcessBuilder(
                    "powershell", "-NonInteractive", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true)
                    .start();

            String output = new String(ps.getInputStream().readAllBytes()).trim();
            ps.waitFor(10, TimeUnit.SECONDS);

            for (String line : output.split("\\r?\\n")) {
                String pid = line.trim();
                if (!pid.isEmpty() && pid.matches("\\d+")) {
                    pidsToKill.add(pid);
                }
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ No se pudo consultar procesos Java: " + e.getMessage());
        }

        for (String pid : pidsToKill) {
            try {
                client.sendLog(executionId, "WARN",
                        "🔪 Terminando proceso Java/Gradle bloqueante PID: " + pid);
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", pid)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                client.sendLog(executionId, "WARN",
                        "No se pudo terminar PID " + pid + ": " + e.getMessage());
            }
        }

        if (!pidsToKill.isEmpty()) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        }
    }

    // ── Allure report (optional — requires allure CLI installed) ───────────────

    private String generateAllureReport(String executionId, String workDir) {
        try {
            // Results dir is build/allure-results (Gradle allure plugin default)
            ProcessBuilder pb = new ProcessBuilder(
                    "allure", "generate", "build/allure-results",
                    "-o", "build/reports/allure-report/" + executionId, "--clean");
            pb.directory(new File(workDir));
            int exit = pb.start().waitFor();
            if (exit == 0 && !config.allureBaseUrl.isBlank()) {
                String url = config.allureBaseUrl + "/" + executionId;
                System.out.println("[Executor] Allure report generado: " + url);
                return url;
            }
        } catch (Exception e) {
            System.out.println("[Executor] Allure CLI no disponible (opcional): " + e.getMessage());
        }
        return null;
    }

    // ── Exception helpers ─────────────────────────────────────────────────────

    /** Full stacktrace as a String — avoids null when getMessage() is null (e.g. NPE). */
    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** Short human-readable description: ClassName: message, or just ClassName if message is null. */
    private static String describeException(Throwable t) {
        String msg = t.getMessage();
        return msg != null ? t.getClass().getSimpleName() + ": " + msg : t.getClass().getName();
    }

    // ── Log level detection — Gradle / JUnit5 output ──────────────────────────
    // JUnit5 format: "ClassName > methodName() PASSED/FAILED/SKIPPED"
    // Gradle build:  "BUILD SUCCESSFUL in Xs" / "BUILD FAILED"

    private String detectLevel(String line) {
        String trim  = line.trim();
        String upper = trim.toUpperCase();

        // JUnit5 individual test result (only these increment the counters)
        if (trim.contains(" > ")) {
            if (upper.endsWith(" PASSED"))  return "PASS";
            if (upper.endsWith(" FAILED"))  return "FAIL";
            if (upper.endsWith(" SKIPPED")) return "SKIP";
        }

        // Build-level status (shown in functional view — not counted as tests)
        if (upper.contains("BUILD SUCCESSFUL") || upper.contains("BUILD FAILED")) return "INFO";

        // Errors and warnings in Gradle output
        if (upper.contains("[ERROR]") || upper.startsWith("E: ")) return "FAIL";
        if (upper.contains("[WARNING]") || upper.startsWith("W: ")) return "WARN";

        // Technical lines go to DEBUG so they appear only in the Logs Técnicos tab
        if (isTechnicalLine(trim)) return "DEBUG";

        return "INFO";
    }

    // Returns true for lines that belong only in the technical log, not the functional activity view.
    // Covers: DriverFactory internals, Appium HTTP traffic, Gradle task headers, stack frames,
    // Allure/JUnit platform/PDF/Mail listener output, and classpath dumps.
    private static boolean isTechnicalLine(String trim) {
        if (trim.startsWith("[DriverFactory]"))        return true;
        if (trim.startsWith("[HTTP]"))                 return true;
        if (trim.startsWith("[DeviceSync]"))           return true;
        if (trim.startsWith("[WDA]"))                  return true;
        if (trim.startsWith("[Video]"))                return true;
        if (trim.startsWith("[Cleanup]"))              return true;
        if (trim.startsWith("[Preflight]"))            return true;
        if (trim.startsWith("[AllureReportSender]"))   return true;
        if (trim.startsWith("> Task :"))               return true;
        if (trim.startsWith("at "))                    return true; // Java stack frame
        if (trim.startsWith("\t"))                     return true; // tab-indented technical detail
        if (trim.startsWith("org.openqa.selenium"))    return true;
        if (trim.startsWith("io.appium.java_client"))  return true;
        if (trim.startsWith("io.netty"))               return true;
        if (trim.startsWith("com.google.guava"))       return true;

        String upper = trim.toUpperCase();
        if (upper.contains("ALLURE"))                  return true;
        if (upper.contains("JUNIT PLATFORM"))          return true;
        if (upper.contains("JUNIT JUPITER"))           return true;
        if (upper.contains("JUNIT VINTAGE"))           return true;
        if (upper.contains("BASETEST"))                return true;
        if (upper.contains("CLASSPATH"))               return true;
        if (upper.contains("SECURITY FIND-IDENTITY"))  return true;
        if (upper.contains("PDFGENERATOR"))            return true;
        if (upper.contains("MAILSENDER"))              return true;
        if (upper.contains("CODESIGN"))                return true;
        if (upper.contains("CAPABILITIES"))            return true;
        if (upper.contains("APPIUMDRIVER"))            return true;
        if (upper.contains("DESIRED CAPABILITIES"))    return true;
        if (upper.contains("PLATFORMNAME"))            return true;
        if (upper.contains("DEVICENAME"))              return true;
        if (upper.contains("AUTOMATIONNAME"))          return true;
        if (upper.contains("DEVICESYNC"))              return true;
        if (upper.contains("COREDEVICE"))              return true;
        if (upper.contains("CHROMEDRIVER"))            return true;
        if (upper.contains("WEBDRIVERMANAGER"))        return true;
        if (upper.contains("SELENIUM"))                return true;
        if (upper.contains("XCUITEST"))                return true;
        if (upper.contains("BUNDLEID"))                return true;
        if (upper.contains("WDABUNDLEID"))             return true;

        return false;
    }

    // Parses "MenuCoffeTree > comprarAmericano() PASSED" → "comprarAmericano"
    static String extractTestName(String line) {
        if (line == null) return "unknown";
        int sep = line.indexOf(" > ");
        if (sep < 0) return line.trim();
        String after = line.substring(sep + 3).trim();
        for (String suffix : new String[]{" PASSED", " FAILED", " SKIPPED"}) {
            if (after.toUpperCase().endsWith(suffix)) {
                after = after.substring(0, after.length() - suffix.length()).trim();
                break;
            }
        }
        if (after.endsWith("()")) after = after.substring(0, after.length() - 2);
        return after.isEmpty() ? "unknown" : after;
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
}
