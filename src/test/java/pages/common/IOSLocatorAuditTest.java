package pages.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auditoría estática de la migración multiplataforma (sin driver, sin dispositivo).
 *
 * Para cada campo {@link PlatformLocator} declarado en las clases del flujo de
 * Alimentos, resuelve la rama iOS ({@code resolve(true)}) y falla si el resultado
 * contiene cualquier firma exclusiva de Android (tipo de elemento UiAutomator2,
 * atributo content-desc, API androidUIAutomator/UiSelector/UiScrollable).
 *
 * PlatformLocator.resolve(boolean) es una función pura — no requiere AppiumDriver
 * ni sesión activa, por lo que esta verificación es 100% determinística y corre
 * en cualquier entorno, sin dispositivo Android ni iOS conectado.
 */
public class IOSLocatorAuditTest {

    private static final Pattern ANDROID_SIGNATURE = Pattern.compile(
            "android\\.widget\\.|android\\.view\\.|ComposeView|content-desc|UiSelector|UiScrollable|androidUIAutomator"
    );

    private static final Class<?>[] AUDITED_CLASSES = {
            CinemasHelper.class,
            pages.alimentos.AlimentosLocators.class,
            pages.alimentos.SelectorPage.class,
    };

    @Test
    @DisplayName("Ningun PlatformLocator resuelve a un locator Android cuando isIOS()=true")
    void ningunLocatorIOSContieneFirmaAndroid() throws IllegalAccessException {
        List<String> violaciones = new ArrayList<>();
        List<String> reporte = new ArrayList<>();

        for (Class<?> clazz : AUDITED_CLASSES) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() != PlatformLocator.class) continue;
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                PlatformLocator pl = (PlatformLocator) f.get(null);
                if (pl == null) continue;

                By iosLocator = pl.resolve(true);
                By androidLocator = pl.resolve(false);
                String iosStr = String.valueOf(iosLocator);

                reporte.add(clazz.getSimpleName() + "." + f.getName() + " -> iOS: " + iosStr);

                if (ANDROID_SIGNATURE.matcher(iosStr).find()) {
                    violaciones.add(clazz.getSimpleName() + "." + f.getName()
                            + " | iOS resuelve a: " + iosStr
                            + " | Android original: " + androidLocator);
                }
            }
        }

        System.out.println("=== REPORTE: locator iOS resuelto por cada campo PlatformLocator ===");
        reporte.forEach(System.out::println);
        System.out.println("=== Total campos PlatformLocator auditados: " + reporte.size() + " ===");

        if (!violaciones.isEmpty()) {
            System.out.println("=== VIOLACIONES: firma Android detectada en rama iOS ===");
            violaciones.forEach(System.out::println);
        }

        assertTrue(violaciones.isEmpty(),
                "Se encontraron " + violaciones.size()
                        + " PlatformLocator cuya rama iOS contiene una firma Android:\n"
                        + String.join("\n", violaciones));
    }

    @Test
    @DisplayName("Los locators iOS-only sueltos (no PlatformLocator) tampoco contienen firma Android")
    void locatorsIOSSueltosSinFirmaAndroid() throws NoSuchFieldException, IllegalAccessException {
        // CLUB_BACK_BUTTON_XPATH_IOS es un By crudo (no PlatformLocator) porque su
        // hermano Android-only (CLUB_BACK_BUTTON_UIAUTO, API UiAutomator2) no tiene
        // contraparte iOS posible — ver CinemasHelper.tapBackFromClubUI().
        Field f = CinemasHelper.class.getDeclaredField("CLUB_BACK_BUTTON_XPATH_IOS");
        f.setAccessible(true);
        By iosOnly = (By) f.get(null);
        String s = String.valueOf(iosOnly);
        assertTrue(!ANDROID_SIGNATURE.matcher(s).find(),
                "CLUB_BACK_BUTTON_XPATH_IOS contiene firma Android: " + s);
    }

    @Test
    @DisplayName("Ningun PlatformLocator tiene el lado Android o iOS en null")
    void ningunLadoEsNull() throws IllegalAccessException {
        // Defensa en profundidad: PlatformLocator.of()/same() ya rechazan null en
        // construcción (Objects.requireNonNull) — este test verifica además, por
        // reflection, que ningún campo YA CONSTRUIDO expone un lado null (cubre
        // cualquier vía de construcción futura que no pase por of()/same()).
        List<String> violaciones = new ArrayList<>();

        for (Class<?> clazz : AUDITED_CLASSES) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() != PlatformLocator.class) continue;
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                PlatformLocator pl = (PlatformLocator) f.get(null);
                if (pl == null) continue;

                if (pl.android() == null) {
                    violaciones.add(clazz.getSimpleName() + "." + f.getName() + " -> android() es null");
                }
                if (pl.ios() == null) {
                    violaciones.add(clazz.getSimpleName() + "." + f.getName() + " -> ios() es null");
                }
            }
        }

        assertTrue(violaciones.isEmpty(),
                "Se encontraron " + violaciones.size() + " PlatformLocator con un lado null:\n"
                        + String.join("\n", violaciones));
    }

    @Test
    @DisplayName("Reporte de PlatformLocator distintos que resuelven al mismo locator iOS (riesgo de colisión)")
    void reportarLocatorsIOSDuplicados() throws IllegalAccessException {
        // No falla el build (colisionar no es necesariamente un bug — puede ser un
        // mismo botón reutilizado a propósito) pero deja evidencia explícita en el
        // log de qué campos comparten exactamente el mismo locator iOS, para que la
        // decisión de si es riesgo real se tome con el reporte de endurecimiento en mano.
        Map<String, List<String>> porLocatorIOS = new LinkedHashMap<>();

        for (Class<?> clazz : AUDITED_CLASSES) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() != PlatformLocator.class) continue;
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                PlatformLocator pl = (PlatformLocator) f.get(null);
                if (pl == null) continue;

                String iosStr = String.valueOf(pl.resolve(true));
                porLocatorIOS.computeIfAbsent(iosStr, k -> new ArrayList<>())
                        .add(clazz.getSimpleName() + "." + f.getName());
            }
        }

        System.out.println("=== REPORTE: locators iOS compartidos por más de un campo PlatformLocator ===");
        porLocatorIOS.forEach((locator, campos) -> {
            if (campos.size() > 1) {
                System.out.println(locator + "  <-  " + String.join(", ", campos));
            }
        });
    }
}
