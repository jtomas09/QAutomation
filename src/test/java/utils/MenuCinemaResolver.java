package utils;

import java.util.Map;

public final class MenuCinemaResolver {

    private static final Map<String, String> MENU_CINEMA_MAP = Map.of(
        "MenuCoffeTree",   "Escala Morelia",
        "MenuMiCine",      "El Prado Morelia",
        "MenuVIP",         "Espacio Las Américas",
        "MenuTradicional", "Escala La Huerta"
        // MenuAtmosfera uses per-test @Cinema annotation — not in this map
    );

    private MenuCinemaResolver() {}

    /** Returns the required cinema for the given menu class, or null if not an alimentos menu. */
    public static String resolve(String menuClassName) {
        return MENU_CINEMA_MAP.get(menuClassName);
    }

    /** True for all alimentos menu classes, including MenuAtmosfera (handled via @Cinema). */
    public static boolean isAlimentosMenu(String className) {
        return MENU_CINEMA_MAP.containsKey(className) || "MenuAtmosfera".equals(className);
    }
}
