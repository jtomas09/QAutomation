package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

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
    static {
        SUITE_MAP = new HashMap<>();
        // Full suites
        SUITE_MAP.put("smoke tests",          "tests.RunAllTests");
        SUITE_MAP.put("full suite",           "tests.RunAllTests");
        SUITE_MAP.put("regresión",            "tests.RunAllTests");
        SUITE_MAP.put("regresion",            "tests.RunAllTests");
        SUITE_MAP.put("sanity",               "tests.RunAllTests");
        SUITE_MAP.put("flujo completo",       "tests.México.E2E.FlujosCompraNoLogin");
        SUITE_MAP.put("flujo-completo",       "tests.México.E2E.FlujosCompraNoLogin");
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
    }

    private final RunnerConfig  config;
    private final BackendClient client;

    public JobExecutor(RunnerConfig config, BackendClient client) {
        this.config = config;
        this.client = client;
    }

    public void execute(JobDto job) {
        System.out.printf("%n[Executor] ▶  %s  |  Suite: %s  |  Env: %s  |  País: %s%n",
                job.executionId, job.suite, job.env, job.country);

        AtomicInteger passed  = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite
                    + "  |  Env: "    + job.env
                    + "  |  Device: " + job.device
                    + "  |  País: "   + job.country);

            // ── Pre-flight ────────────────────────────────────────────────────
            checkAdbDevices(job.executionId);
            checkAppiumServer(job.executionId);

            // ── Build Gradle command ──────────────────────────────────────────
            List<String> cmd = buildCommand(job);
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

            Process process = pb.start();

            // Abort watcher — polls backend every 3 s; kills Gradle if ABORTED
            AtomicBoolean wasAborted = new AtomicBoolean(false);
            Thread abortWatcher = new Thread(() -> {
                while (process.isAlive()) {
                    try { Thread.sleep(3_000); } catch (InterruptedException e) { return; }
                    if (client.isJobAborted(job.executionId)) {
                        wasAborted.set(true);
                        System.out.println("\n[Executor] Aborto detectado — terminando árbol de procesos Gradle");
                        // Kill all child processes first (JVM spawned by gradlew.bat on Windows)
                        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
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
                    if      ("PASS".equals(level)) passed.incrementAndGet();
                    else if ("FAIL".equals(level)) failed.incrementAndGet();
                    else if ("SKIP".equals(level)) skipped.incrementAndGet();
                }
            }

            int exitCode = process.waitFor();
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
                    passed.get(), failed.get(), skipped.get(), allureUrl);
            System.out.println("[Executor] ✓ Finalizado: " + job.executionId);

        } catch (Exception e) {
            System.err.println("[Executor] Error fatal: " + e.getMessage());
            e.printStackTrace();
            client.sendLog(job.executionId, "ERROR",
                    "❌ Error interno del runner: " + e.getMessage());
            try {
                client.sendResult(job.executionId,
                        passed.get(), Math.max(failed.get(), 1), skipped.get(), null);
            } catch (Exception ignored) {}
        }
    }

    // ── Gradle command builder ─────────────────────────────────────────────────

    private List<String> buildCommand(JobDto job) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String  testFilter = resolveTestFilter(job.suite);

        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("gradlew.bat");
        } else {
            cmd.add("./gradlew");
        }

        cmd.add("test");
        cmd.add("--tests");
        cmd.add(testFilter);
        cmd.add("--rerun-tasks");

        // build.gradle contains: systemProperties System.getProperties()
        // so -D flags on the Gradle JVM are visible to tests as System.getProperty()
        cmd.add("-DdeviceName="    + nvl(job.device,  "Galaxy A56 5G"));
        cmd.add("-Denv="           + nvl(job.env,     "QA"));
        cmd.add("-Dcountry="       + nvl(job.country, "mexico"));
        cmd.add("-Dappium.hub="    + config.appiumHub + "/wd/hub");
        cmd.add("-DexecutionName=" + nvl(job.suite,   "Suite"));
        cmd.add("-DREUSE_DRIVER=true");

        return cmd;
    }

    private static String resolveTestFilter(String suiteName) {
        if (suiteName == null || suiteName.isBlank()) return "tests.RunAllTests";
        String key = suiteName.toLowerCase().trim();
        return SUITE_MAP.getOrDefault(key, "tests.RunAllTests");
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
        String hubBase = config.appiumHub.replaceAll("/wd/hub$", "");
        HttpClient http = HttpClient.newHttpClient();

        // Try /status (Appium 2.x default) and /wd/hub/status (Appium 1.x / base-path)
        for (String path : new String[]{"/status", "/wd/hub/status"}) {
            String statusUrl = hubBase + path;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(statusUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET().build();
                int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 200) {
                    client.sendLog(executionId, "INFO",
                            "✅ Appium server online: " + hubBase);
                    return;
                }
            } catch (Exception ignored) {
                // try next path
            }
        }

        client.sendLog(executionId, "WARN",
                "⚠️  Appium no disponible en " + hubBase
                + " — inícialo con: appium --port 4723");
    }

    // ── Video upload ───────────────────────────────────────────────────────────

    private void uploadVideos(String executionId, String suite) {
        try {
            Path videosDir = Paths.get(config.workDir, "build", "videos");
            if (!Files.exists(videosDir)) return;
            Files.walk(videosDir)
                    .filter(p -> p.toString().endsWith(".mp4"))
                    .forEach(p -> {
                        String className = p.getParent().getFileName().toString();
                        String testName  = p.getFileName().toString()
                                .replace(".mp4", "")
                                .replace("_", " ")
                                .trim();
                        client.uploadVideo(executionId, nvl(suite, className), testName, p);
                    });
        } catch (Exception e) {
            System.out.println("[Executor] No se pudieron subir videos: " + e.getMessage());
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

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
}
