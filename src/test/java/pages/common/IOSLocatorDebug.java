package pages.common;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modo de diagnóstico para el endurecimiento de locators iOS.
 *
 * Se activa con la propiedad de sistema o variable de entorno IOS_LOCATOR_DEBUG=true
 * (por defecto: false — cuando está apagado, cada método de esta clase es un no-op
 * inmediato; no cambia timeouts, waits, reintentos ni el resultado de ninguna
 * interacción existente en BasePage/CinemasHelper/SelectorPage).
 *
 * Este modo NO reemplaza locators por sí solo. Genera evidencia real (page source
 * de la sesión Appium activa) y, a partir de ESA evidencia — nunca de una suposición —
 * calcula una recomendación de locator ordenada por prioridad:
 *   1. Accessibility Identifier / accessibility id (@name)
 *   2. label (@label)
 *   3. value (@value)
 *   4. iOSNsPredicateString (type + visible/enabled, cuando no hay texto propio)
 *   5. iOSClassChain (mismo criterio que NsPredicate, sintaxis alternativa)
 *   6. Si nada de lo anterior es posible: mantener el locator posicional y documentar
 *      por qué (ningún atributo estable existe en el árbol capturado).
 *
 * Uso previsto: correr la suite de Alimentos contra un iPhone real con
 * -DIOS_LOCATOR_DEBUG=true. Cada intento de click() en iOS queda registrado; si el
 * locator falla, se captura el page source completo a
 * build/ios-diagnostics/{paso}_{timestamp}.xml junto con un reporte legible
 * ({paso}_{timestamp}_reporte.txt) con la sugerencia calculada sobre ESE XML.
 */
public final class IOSLocatorDebug {

    private static final Logger log = LoggerFactory.getLogger(IOSLocatorDebug.class);

    private static final boolean ENABLED = "true".equalsIgnoreCase(
            System.getProperty("IOS_LOCATOR_DEBUG",
                    System.getenv().getOrDefault("IOS_LOCATOR_DEBUG", "false")));

    // Tipos de elemento XCUITest considerados "candidatos" al volcar el árbol cercano.
    // No se vuelca el árbol completo (podría ser enorme) — se acota a los tipos
    // interactivos/de texto más relevantes para depurar locators de UI.
    private static final String CANDIDATE_XPATH =
            "//XCUIElementTypeButton | //XCUIElementTypeStaticText | //XCUIElementTypeTextField" +
            " | //XCUIElementTypeSearchField | //XCUIElementTypeSwitch | //XCUIElementTypeCell" +
            " | //XCUIElementTypeOther[@visible='true']";

    private static final int MAX_CANDIDATES_DUMPED = 60;

    private static final Path DIAG_DIR = Paths.get("build", "ios-diagnostics");

    private IOSLocatorDebug() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Debe llamarse SIEMPRE antes de intentar interactuar con un elemento en iOS
     * (BasePage.click/clickIfPresent ya lo hacen, guardado detrás de isEnabled()).
     * No-op inmediato si el modo está apagado.
     */
    public static void beforeInteraction(AppiumDriver driver, String stepLabel, By locator) {
        if (!ENABLED) return;
        try {
            log.info("[IOS_LOCATOR_DEBUG] Intentando '{}' | locator={}", stepLabel, locator);
            List<WebElement> candidatos = safeFindCandidates(driver);
            log.info("[IOS_LOCATOR_DEBUG] Árbol cercano ({} candidatos, máx {}):",
                    candidatos.size(), MAX_CANDIDATES_DUMPED);
            int i = 0;
            for (WebElement el : candidatos) {
                if (i >= MAX_CANDIDATES_DUMPED) {
                    log.info("[IOS_LOCATOR_DEBUG]   ... ({} candidatos adicionales omitidos)",
                            candidatos.size() - MAX_CANDIDATES_DUMPED);
                    break;
                }
                log.info("[IOS_LOCATOR_DEBUG]   [{}] {}", i, describe(el, i));
                i++;
            }
        } catch (Exception e) {
            log.warn("[IOS_LOCATOR_DEBUG] No se pudo volcar el árbol cercano: {}", e.getMessage());
        }
    }

    /**
     * Debe llamarse cuando un locator falla (elemento no encontrado / interacción
     * lanzó excepción). Captura driver.getPageSource() completo a un XML con
     * timestamp y genera un reporte legible con la sugerencia calculada sobre esa
     * captura. No-op inmediato si el modo está apagado. Nunca lanza — cualquier
     * fallo interno del propio diagnóstico se registra como warning y se ignora,
     * para no alterar la excepción original que el llamador va a relanzar.
     */
    public static void onFailure(AppiumDriver driver, String stepLabel, By locator, Throwable cause) {
        if (!ENABLED) return;
        try {
            Files.createDirectories(DIAG_DIR);
            String ts = String.valueOf(System.currentTimeMillis());
            String safeLabel = sanitize(stepLabel);

            String pageSource = driver.getPageSource();
            Path xmlFile = DIAG_DIR.resolve(safeLabel + "_" + ts + ".xml");
            Files.writeString(xmlFile, pageSource == null ? "" : pageSource, StandardCharsets.UTF_8);

            String reporte = buildFailureReport(stepLabel, locator, cause, pageSource);
            Path reportFile = DIAG_DIR.resolve(safeLabel + "_" + ts + "_reporte.txt");
            Files.writeString(reportFile, reporte, StandardCharsets.UTF_8);

            log.error("[IOS_LOCATOR_DEBUG] FALLO capturado — paso='{}' locator={} " +
                            "pageSource={} reporte={}",
                    stepLabel, locator, xmlFile, reportFile);
            log.error(reporte);
        } catch (Exception e) {
            log.warn("[IOS_LOCATOR_DEBUG] No se pudo capturar diagnóstico de fallo: {}", e.getMessage());
        }
    }

    // =========================================================
    // Reporte legible: esperado -> encontrados -> por qué no coincide -> sugerencia
    // =========================================================

    private static String buildFailureReport(String stepLabel, By locator, Throwable cause, String pageSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== [IOS_LOCATOR_DEBUG] Reporte de fallo — ").append(stepLabel).append(" ===\n");
        sb.append("Elemento esperado (locator intentado):\n  ").append(locator).append("\n");
        if (cause != null) sb.append("Excepción: ").append(cause.getClass().getSimpleName())
                .append(" - ").append(cause.getMessage()).append("\n");

        List<Map<String, String>> candidatos = parseCandidates(pageSource);
        sb.append("\nElementos encontrados en el árbol capturado (").append(candidatos.size()).append("):\n");
        if (candidatos.isEmpty()) {
            sb.append("  (ninguno — el page source no contenía elementos de los tipos auditados,\n")
              .append("   o no pudo parsearse; revisar el XML crudo adjunto)\n");
        } else {
            int i = 0;
            for (Map<String, String> attrs : candidatos) {
                if (i >= MAX_CANDIDATES_DUMPED) {
                    sb.append("  ... (").append(candidatos.size() - MAX_CANDIDATES_DUMPED)
                      .append(" candidatos adicionales omitidos)\n");
                    break;
                }
                sb.append("  [").append(i).append("] ").append(formatAttrs(attrs)).append("\n");
                i++;
            }
        }

        sb.append("\nPor qué ninguno coincide:\n");
        String locatorStr = String.valueOf(locator);
        boolean algunaCoincidenciaParcial = false;
        for (Map<String, String> attrs : candidatos) {
            for (String key : new String[]{"name", "label", "value"}) {
                String v = attrs.getOrDefault(key, "");
                if (!v.isBlank() && locatorStr.contains(v)) {
                    algunaCoincidenciaParcial = true;
                }
            }
        }
        if (algunaCoincidenciaParcial) {
            sb.append("  Hay al menos un elemento cuyo atributo aparece mencionado en el locator —\n")
              .append("  revisar manualmente si el predicado (contains/exact, mayúsculas, espacios) es el problema.\n");
        } else {
            sb.append("  Ningún elemento capturado tiene name/label/value que coincida con lo que el\n")
              .append("  locator busca — el control esperado no está en el árbol en este momento, o\n")
              .append("  expone su contenido bajo un atributo distinto a los tres consultados.\n");
        }

        sb.append("\nAtributos únicos realmente presentes en el árbol (name/label/value no vacíos):\n");
        Set<String> unicos = new LinkedHashSet<>();
        for (Map<String, String> attrs : candidatos) {
            for (String key : new String[]{"name", "label", "value"}) {
                String v = attrs.getOrDefault(key, "");
                if (!v.isBlank()) unicos.add(key + "='" + v + "'");
            }
        }
        if (unicos.isEmpty()) {
            sb.append("  (ninguno — ningún candidato expone name/label/value; solo queda type+posición)\n");
        } else {
            unicos.forEach(u -> sb.append("  ").append(u).append("\n"));
        }

        sb.append("\nSugerencia de locator (calculada sobre esta captura, prioridad " +
                "AccessibilityId > name > label > value > iOSNsPredicateString > iOSClassChain):\n");
        sb.append(suggestLocator(candidatos)).append("\n");

        return sb.toString();
    }

    /**
     * Motor de sugerencia — función pura, no requiere driver. Se expone público para
     * poder alimentarla con un page source capturado por otra vía (p. ej. Appium
     * Inspector) sin tener que reproducir el fallo en vivo.
     */
    public static String suggestLocator(List<Map<String, String>> candidatos) {
        for (Map<String, String> attrs : candidatos) {
            String name = attrs.getOrDefault("name", "");
            if (!name.isBlank()) {
                return "AppiumBy.accessibilityId(\"" + name + "\")  — equivalente: @name='" + name + "'";
            }
        }
        for (Map<String, String> attrs : candidatos) {
            String label = attrs.getOrDefault("label", "");
            if (!label.isBlank()) {
                return "By.xpath(\"//*[@label='" + label + "']\")";
            }
        }
        for (Map<String, String> attrs : candidatos) {
            String value = attrs.getOrDefault("value", "");
            if (!value.isBlank()) {
                return "By.xpath(\"//*[@value='" + value + "']\")";
            }
        }
        // Ningún candidato tiene name/label/value — el único atributo estable
        // disponible es el type (y opcionalmente visible/enabled). Se sugiere
        // iOSNsPredicateString como siguiente prioridad antes de caer a posición.
        if (!candidatos.isEmpty()) {
            String type = candidatos.get(0).getOrDefault("type", "XCUIElementTypeButton");
            return "AppiumBy.iOSNsPredicateString(\"type == '" + type + "' AND visible == 1\")" +
                    "  — ADVERTENCIA: sin name/label/value no distingue entre varios elementos del" +
                    " mismo type; si hay más de uno visible, sigue siendo ambiguo. Considerar" +
                    " iOSClassChain con índice explícito solo si esto se confirma con el equipo de app," +
                    " o pedir un accessibilityIdentifier estable en el código de la app.";
        }
        return "Sin candidatos capturados — no se puede calcular ninguna sugerencia." +
                " Mantener el locator posicional existente y documentar la ausencia de evidencia.";
    }

    // =========================================================
    // Utilidades de captura / parseo (sin dependencias nuevas — XML del JDK)
    // =========================================================

    private static List<WebElement> safeFindCandidates(AppiumDriver driver) {
        try {
            List<WebElement> els = driver.findElements(By.xpath(CANDIDATE_XPATH));
            return els == null ? List.of() : els;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String describe(WebElement el, int index) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("name", safeAttr(el, "name"));
        attrs.put("label", safeAttr(el, "label"));
        attrs.put("value", safeAttr(el, "value"));
        attrs.put("type", safeAttr(el, "type"));
        attrs.put("enabled", safeAttr(el, "enabled"));
        attrs.put("visible", safeAttr(el, "visible"));
        attrs.put("accessible", safeAttr(el, "accessible"));
        String rect;
        try { rect = String.valueOf(el.getRect()); } catch (Exception e) { rect = "N/A"; }
        attrs.put("rect", rect);
        attrs.put("index", String.valueOf(index));
        return formatAttrs(attrs);
    }

    private static String safeAttr(WebElement el, String name) {
        try {
            String v = el.getAttribute(name);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatAttrs(Map<String, String> attrs) {
        StringBuilder sb = new StringBuilder();
        attrs.forEach((k, v) -> sb.append(k).append("=\"").append(v).append("\" "));
        return sb.toString().trim();
    }

    /**
     * Parsea un page source de XCUITest (XML) y devuelve, para cada nodo de los
     * tipos candidatos, un mapa de sus atributos (name/label/value/type/enabled/
     * visible/accessible/rect aproximado/index de aparición). Tolerante a XML
     * inválido/vacío — devuelve lista vacía en ese caso, nunca lanza.
     */
    public static List<Map<String, String>> parseCandidates(String pageSourceXml) {
        List<Map<String, String>> result = new ArrayList<>();
        if (pageSourceXml == null || pageSourceXml.isBlank()) return result;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    pageSourceXml.getBytes(StandardCharsets.UTF_8)));

            NodeList all = doc.getElementsByTagName("*");
            int index = 0;
            for (int i = 0; i < all.getLength(); i++) {
                Node node = all.item(i);
                if (!(node instanceof Element)) continue;
                Element el = (Element) node;
                String tag = el.getTagName();
                if (!isCandidateTag(tag)) continue;

                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("type", tag);
                NamedNodeMap nnm = el.getAttributes();
                for (String key : new String[]{"name", "label", "value", "enabled", "visible", "accessible",
                        "x", "y", "width", "height"}) {
                    Node a = nnm.getNamedItem(key);
                    attrs.put(key, a == null ? "" : a.getNodeValue());
                }
                attrs.put("index", String.valueOf(index++));
                result.add(attrs);
            }
        } catch (Exception e) {
            log.warn("[IOS_LOCATOR_DEBUG] No se pudo parsear el page source capturado: {}", e.getMessage());
        }
        return result;
    }

    private static boolean isCandidateTag(String tag) {
        return tag.equals("XCUIElementTypeButton")
                || tag.equals("XCUIElementTypeStaticText")
                || tag.equals("XCUIElementTypeTextField")
                || tag.equals("XCUIElementTypeSearchField")
                || tag.equals("XCUIElementTypeSwitch")
                || tag.equals("XCUIElementTypeCell")
                || tag.equals("XCUIElementTypeOther");
    }

    private static String sanitize(String s) {
        return s == null ? "step" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
