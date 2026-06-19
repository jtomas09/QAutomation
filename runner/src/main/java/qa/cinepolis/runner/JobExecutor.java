package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;
import qa.cinepolis.runner.model.TestCaseResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
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

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite
                    + "  |  Env: "    + job.env
                    + "  |  Device: " + job.device
                    + "  |  País: "   + job.country);
            client.sendLog(job.executionId, "INFO",
                    "📹 Grabación de video: " + (job.videoEnabled ? "ACTIVA" : "INACTIVA"));

            // ── Pre-flight ────────────────────────────────────────────────────
            checkAdbDevices(job.executionId);
            checkAppiumServer(job.executionId);

            // ── Pre-clean locked test-results to avoid file-lock failures ────
            preCleanTestResults(job.executionId);

            // ── Build Gradle command ──────────────────────────────────────────
            List<String> cmd = buildCommand(job);

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

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(config.workDir));
            pb.redirectErrorStream(true);

            // Environment variables — tests read these via System.getenv()
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

            Process process = pb.start();
            activeProcess = process;

            // Abort watcher — polls backend every 1s; kills Gradle tree si ABORTING/ABORTED
            AtomicBoolean wasAborted = new AtomicBoolean(false);
            Thread abortWatcher = new Thread(() -> {
                while (process.isAlive()) {
                    try { Thread.sleep(1_000); } catch (InterruptedException e) { return; }
                    if (client.isJobAborted(job.executionId)) {
                        wasAborted.set(true);
                        client.sendLog(job.executionId, "WARN",
                            "🛑 Aborto recibido — deteniendo proceso Gradle...");
                        System.out.println("\n[Executor] Aborto detectado — terminando árbol de procesos Gradle");
                        forceKillProcessTree(process);
                        // Confirmar al backend que el proceso fue terminado
                        client.confirmAbort(job.executionId);
                        client.sendLog(job.executionId, "WARN", "⛔ Ejecución abortada correctamente.");
                        return;
                    }
                }
            }, "abort-watcher-" + job.executionId);
            abortWatcher.setDaemon(true);
            abortWatcher.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String level = detectLevel(line);
                    client.sendLog(job.executionId, level, line);
                    System.out.println("[" + level + "] " + line);
                    if      ("PASS".equals(level)) { passed.incrementAndGet();  testCases.add(new TestCaseResult(extractTestName(line), "PASS")); }
                    else if ("FAIL".equals(level)) { failed.incrementAndGet();  testCases.add(new TestCaseResult(extractTestName(line), "FAIL")); }
                    else if ("SKIP".equals(level)) { skipped.incrementAndGet(); testCases.add(new TestCaseResult(extractTestName(line), "SKIP")); }
                }
            }

            int exitCode = process.waitFor();
            activeProcess = null;
            abortWatcher.interrupt();

            if (wasAborted.get()) {
                client.sendLog(job.executionId, "WARN", "Ejecución abortada por el usuario");
                System.out.println("[Executor] Job abortado: " + job.executionId);
                return;
            }

            // If Gradle crashed with no test output (e.g. compilation error),
            // record at least one failure so the execution doesn't finish as PASSED.
            if (passed.get() == 0 && failed.get() == 0 && skipped.get() == 0 && exitCode != 0) {
                failed.incrementAndGet();
            }

            String summary = passed.get() + " PASSED · "
                           + failed.get() + " FAILED · "
                           + skipped.get() + " SKIPPED";

            client.sendLog(job.executionId,
                    exitCode == 0 ? "PASS" : "FAIL",
                    exitCode == 0
                        ? "✅ Suite completada — " + summary
                        : "❌ Suite terminó con errores (exit " + exitCode + ") — " + summary);

            uploadVideos(job.executionId, job.suite);
            String allureUrl = generateAllureReport(job.executionId);
            client.sendResult(job.executionId,
                    passed.get(), failed.get(), skipped.get(), allureUrl, testCases);
            System.out.println("[Executor] ✓ Finalizado: " + job.executionId);

        } catch (Exception e) {
            System.err.println("[Executor] Error fatal: " + e.getMessage());
            e.printStackTrace();
            client.sendLog(job.executionId, "ERROR",
                    "❌ Error interno del runner: " + e.getMessage());
            try {
                client.sendResult(job.executionId,
                        passed.get(), Math.max(failed.get(), 1), skipped.get(), null, testCases);
            } catch (Exception ignored) {}
        }
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
        cmd.add("-Dappium.hub="    + config.appiumHub + "/wd/hub");
        cmd.add("-DexecutionName=" + nvl(job.suite,   "Suite"));
        cmd.add("-DREUSE_DRIVER=true");

        // Dynamic device capabilities from Device Farm discovery
        if (job.udid != null && !job.udid.isBlank()) {
            cmd.add("-Dudid=" + job.udid);
        }
        if (job.platformVersion != null && !job.platformVersion.isBlank()) {
            cmd.add("-DplatformVersion=" + job.platformVersion);
        }

        if (job.videoEnabled) cmd.add("-Dvideo.enabled=true");
        if (job.sendMail)     cmd.add("-DsendMail=true");
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

    private void checkAdbDevices(String executionId) {
        try {
            Process p = new ProcessBuilder("adb", "devices")
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

    // ── Pre-flight: Appium ─────────────────────────────────────────────────────

    private void checkAppiumServer(String executionId) {
        // If AppiumManager is wired, ask it to ensure Appium is running
        if (appiumMgr != null) {
            if (!appiumMgr.isAlive()) {
                client.sendLog(executionId, "INFO", "Appium no responde — intentando iniciar...");
                try {
                    appiumMgr.ensureRunning();
                    client.sendLog(executionId, "INFO", "Appium iniciado correctamente.");
                } catch (Exception e) {
                    client.sendLog(executionId, "WARN",
                            "Appium no pudo iniciarse: " + e.getMessage());
                }
            } else {
                client.sendLog(executionId, "INFO", "Appium server online.");
            }
            return;
        }

        // Fallback: passive check only (legacy path — no AppiumManager)
        String hubBase = config.appiumHub.replaceAll("/wd/hub$", "");
        HttpClient http = HttpClient.newHttpClient();
        for (String path : new String[]{"/status", "/wd/hub/status"}) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(hubBase + path))
                        .timeout(Duration.ofSeconds(5))
                        .GET().build();
                if (http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    client.sendLog(executionId, "INFO", "Appium server online: " + hubBase);
                    return;
                }
            } catch (Exception ignored) {}
        }
        client.sendLog(executionId, "WARN",
                "Appium no disponible en " + hubBase + " — inicialo con: appium --port 4723");
    }

    // ── Video upload ───────────────────────────────────────────────────────────

    private void uploadVideos(String executionId, String suite) {
        try {
            Path videosDir = Paths.get(config.workDir, "build", "videos");
            if (!Files.exists(videosDir)) {
                client.sendLog(executionId, "INFO",
                        "📹 Sin videos: directorio build/videos no existe");
                return;
            }

            long[] count = {0};
            Files.walk(videosDir)
                    .filter(p -> p.toString().endsWith(".mp4"))
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

    private void preCleanTestResults(String executionId) {
        killStuckGradleProcesses(executionId);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // ── Clean binary test-results dir (locked by previous Gradle run) ────
        Path binaryDir = Paths.get(config.workDir, "build", "test-results", "test", "binary");
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
        Path videosDir = Paths.get(config.workDir, "build", "videos");
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

    private String generateAllureReport(String executionId) {
        try {
            // Results dir is build/allure-results (Gradle allure plugin default)
            ProcessBuilder pb = new ProcessBuilder(
                    "allure", "generate", "build/allure-results",
                    "-o", "build/reports/allure-report/" + executionId, "--clean");
            pb.directory(new File(config.workDir));
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

        // Build-level status (shown in logs but NOT counted as tests)
        if (upper.contains("BUILD SUCCESSFUL") || upper.contains("BUILD FAILED")) return "INFO";

        // Errors and warnings in Gradle output
        if (upper.contains("[ERROR]") || upper.startsWith("E: ")) return "FAIL";
        if (upper.contains("[WARNING]") || upper.startsWith("W: "))  return "WARN";

        return "INFO";
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
