package pages.asientos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
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
import java.util.TreeSet;

/**
 * Diagnóstico exclusivo de investigación — activo SOLO con la propiedad de
 * sistema/variable de entorno {@code SEAT_SNAPSHOT_DEBUG=true} (por defecto
 * {@code false}: no-op inmediato, cero impacto en timeouts/reintentos/
 * resultado de cualquier flujo existente — mismo patrón ya establecido por
 * {@code IOSLocatorDebug}).
 *
 * Objetivo: capturar el árbol de accesibilidad COMPLETO (todos los atributos
 * que WDA exponga en ese momento, no una lista fija — si "hittable"/
 * "identifier"/"traits" existen en el page source real, aparecen; si no
 * existen, se reporta honestamente su ausencia en vez de simularlos) más una
 * captura de pantalla, inmediatamente antes y después de un tap de asiento.
 * Compara ambas capturas por identidad de POSICIÓN (no por índice en el
 * árbol, que puede reordenarse sin que nada visible cambie) y reporta, para
 * cada nodo que realmente cambió, qué atributo cambió y su valor antes/después
 * — para encontrar con evidencia real cuál es el indicador que la app usa
 * para reflejar que un asiento quedó seleccionado.
 */
final class SeatUiSnapshot {

    static final boolean ENABLED = "true".equalsIgnoreCase(
            System.getProperty("SEAT_SNAPSHOT_DEBUG",
                    System.getenv().getOrDefault("SEAT_SNAPSHOT_DEBUG", "false")));

    private static final Logger log = LoggerFactory.getLogger(SeatUiSnapshot.class);

    private static final Path DIAG_DIR = Paths.get("build", "seat-diagnostics");

    // Mismos límites verticales que esperarYObtenerAsientosDelMapa() usa para acotar
    // el mapa de asientos en pantalla (screenHeight*0.30 a *0.93) — Nivel 1 del diff
    // se restringe a esa misma región, por la misma razón: ahí es donde vive el mapa.
    private static final double MAP_TOP_RATIO = 0.30;
    private static final double MAP_BOTTOM_RATIO = 0.93;

    private static final Set<String> TIPOS_NIVEL_1 = Set.of(
            "XCUIElementTypeButton", "XCUIElementTypeStaticText", "XCUIElementTypeOther");

    private SeatUiSnapshot() {}

    /** Un nodo del árbol capturado, con TODOS sus atributos crudos (no un subconjunto fijo). */
    static final class Nodo {
        final String tag;
        final Map<String, String> attrs; // atributos crudos tal cual vienen del XML

        Nodo(String tag, Map<String, String> attrs) {
            this.tag = tag;
            this.attrs = attrs;
        }

        double num(String key) {
            try { return Double.parseDouble(attrs.getOrDefault(key, "")); }
            catch (Exception e) { return Double.NaN; }
        }

        /** Identidad estable entre capturas: tipo + posición/tamaño (NO cambia solo por reordenarse el árbol). */
        String identidad() {
            return tag + "@" + attrs.getOrDefault("x", "?") + "," + attrs.getOrDefault("y", "?")
                    + "," + attrs.getOrDefault("width", "?") + "x" + attrs.getOrDefault("height", "?");
        }

        boolean dentroDeRegion(double top, double bottom) {
            double y = num("y");
            return !Double.isNaN(y) && y >= top && y <= bottom;
        }
    }

    /** Resultado completo de una captura: page source crudo + todos los nodos parseados. */
    static final class Snapshot {
        final String pageSourceXml;
        final List<Nodo> nodos;

        Snapshot(String pageSourceXml, List<Nodo> nodos) {
            this.pageSourceXml = pageSourceXml;
            this.nodos = nodos;
        }
    }

    static Snapshot capturar(String pageSourceXml) {
        List<Nodo> nodos = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(pageSourceXml.getBytes(StandardCharsets.UTF_8)));
            recorrer(doc.getDocumentElement(), nodos);
        } catch (Exception e) {
            log.warn("[SeatUiSnapshot] No se pudo parsear el page source capturado: {}", e.getMessage());
        }
        return new Snapshot(pageSourceXml, nodos);
    }

    private static void recorrer(Node nodo, List<Nodo> out) {
        if (nodo == null) return;
        if (nodo.getNodeType() == Node.ELEMENT_NODE) {
            Map<String, String> attrs = new LinkedHashMap<>();
            NamedNodeMap nnm = nodo.getAttributes();
            if (nnm != null) {
                for (int i = 0; i < nnm.getLength(); i++) {
                    Node a = nnm.item(i);
                    attrs.put(a.getNodeName(), a.getNodeValue());
                }
            }
            out.add(new Nodo(nodo.getNodeName(), attrs));
        }
        Node hijo = nodo.getFirstChild();
        while (hijo != null) {
            recorrer(hijo, out);
            hijo = hijo.getNextSibling();
        }
    }

    /**
     * Captura antes/después + pantalla completa, guarda TODO en disco
     * (before/after .xml y .png) y registra en el log el diff en dos niveles
     * (Nivel 1: solo el mapa de asientos; Nivel 2: árbol completo, solo si
     * Nivel 1 no encontró ningún cambio) más un resumen legible.
     *
     * @return la ruta del directorio donde quedó todo guardado, para incluirla en el log.
     */
    static Path investigarTap(String contexto, String pageSourceAntes, byte[] screenshotAntes,
                               String pageSourceDespues, byte[] screenshotDespues, int screenHeight) {
        Path dir = DIAG_DIR.resolve(sanitize(contexto) + "_" + System.currentTimeMillis());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("before.xml"), pageSourceAntes == null ? "" : pageSourceAntes, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("after.xml"), pageSourceDespues == null ? "" : pageSourceDespues, StandardCharsets.UTF_8);
            if (screenshotAntes != null && screenshotAntes.length > 0) Files.write(dir.resolve("before.png"), screenshotAntes);
            if (screenshotDespues != null && screenshotDespues.length > 0) Files.write(dir.resolve("after.png"), screenshotDespues);
        } catch (Exception e) {
            log.warn("[SeatUiSnapshot] No se pudieron guardar los archivos de evidencia: {}", e.getMessage());
        }

        Snapshot antes = capturar(pageSourceAntes == null ? "" : pageSourceAntes);
        Snapshot despues = capturar(pageSourceDespues == null ? "" : pageSourceDespues);

        double top = screenHeight * MAP_TOP_RATIO;
        double bottom = screenHeight * MAP_BOTTOM_RATIO;
        List<Nodo> antesNivel1 = filtrarNivel1(antes.nodos, top, bottom);
        List<Nodo> despuesNivel1 = filtrarNivel1(despues.nodos, top, bottom);

        Diff diffNivel1 = comparar(antesNivel1, despuesNivel1);

        log.info("[SeatUiSnapshot] {} — evidencia guardada en {}", contexto, dir.toAbsolutePath());
        log.info("[SeatUiSnapshot] === NIVEL 1 (mapa de asientos: Button/StaticText/Other en la región del mapa) ===");
        reportar(diffNivel1);

        Diff diffFinal = diffNivel1;
        boolean seUsoNivel2 = false;
        if (diffNivel1.esVacio()) {
            log.info("[SeatUiSnapshot] Nivel 1 no detectó cambios — generando Nivel 2 (árbol completo).");
            diffFinal = comparar(antes.nodos, despues.nodos);
            seUsoNivel2 = true;
            log.info("[SeatUiSnapshot] === NIVEL 2 (árbol completo) ===");
            reportar(diffFinal);
        }

        log.info("[SeatUiSnapshot] RESUMEN ({}): {}", seUsoNivel2 ? "Nivel 2" : "Nivel 1", resumen(diffFinal));
        try {
            Files.writeString(dir.resolve("diff.txt"), textoCompleto(contexto, diffNivel1, seUsoNivel2 ? diffFinal : null),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SeatUiSnapshot] No se pudo guardar diff.txt: {}", e.getMessage());
        }
        return dir;
    }

    private static List<Nodo> filtrarNivel1(List<Nodo> nodos, double top, double bottom) {
        List<Nodo> out = new ArrayList<>();
        for (Nodo n : nodos) {
            if (TIPOS_NIVEL_1.contains(n.tag) && n.dentroDeRegion(top, bottom)) out.add(n);
        }
        return out;
    }

    // ── Diff por identidad estable (posición), comparación campo a campo ──────────

    static final class Modificado {
        final Nodo antes, despues;
        final List<String> atributosCambiados;
        Modificado(Nodo antes, Nodo despues, List<String> atributosCambiados) {
            this.antes = antes; this.despues = despues; this.atributosCambiados = atributosCambiados;
        }
    }

    static final class Diff {
        final List<Nodo> aparecieron = new ArrayList<>();
        final List<Nodo> desaparecieron = new ArrayList<>();
        final List<Modificado> modificados = new ArrayList<>();
        boolean esVacio() { return aparecieron.isEmpty() && desaparecieron.isEmpty() && modificados.isEmpty(); }
    }

    private static Diff comparar(List<Nodo> antes, List<Nodo> despues) {
        Map<String, Nodo> porIdAntes = new LinkedHashMap<>();
        for (Nodo n : antes) porIdAntes.putIfAbsent(n.identidad(), n);
        Map<String, Nodo> porIdDespues = new LinkedHashMap<>();
        for (Nodo n : despues) porIdDespues.putIfAbsent(n.identidad(), n);

        Diff diff = new Diff();
        for (Map.Entry<String, Nodo> e : porIdAntes.entrySet()) {
            Nodo nDespues = porIdDespues.get(e.getKey());
            if (nDespues == null) {
                diff.desaparecieron.add(e.getValue());
                continue;
            }
            List<String> cambiados = atributosCambiados(e.getValue(), nDespues);
            if (!cambiados.isEmpty()) diff.modificados.add(new Modificado(e.getValue(), nDespues, cambiados));
        }
        for (Map.Entry<String, Nodo> e : porIdDespues.entrySet()) {
            if (!porIdAntes.containsKey(e.getKey())) diff.aparecieron.add(e.getValue());
        }
        return diff;
    }

    private static List<String> atributosCambiados(Nodo antes, Nodo despues) {
        Set<String> claves = new TreeSet<>();
        claves.addAll(antes.attrs.keySet());
        claves.addAll(despues.attrs.keySet());
        // x/y/width/height ya definen la identidad — no se reportan como "cambio".
        claves.remove("x"); claves.remove("y"); claves.remove("width"); claves.remove("height");
        List<String> cambiados = new ArrayList<>();
        for (String k : claves) {
            String va = antes.attrs.get(k);
            String vd = despues.attrs.get(k);
            if (!java.util.Objects.equals(va, vd)) cambiados.add(k);
        }
        return cambiados;
    }

    private static void reportar(Diff diff) {
        for (Nodo n : diff.desaparecieron) log.info("[SeatUiSnapshot][-] {} {}", n.tag, n.attrs);
        for (Nodo n : diff.aparecieron) log.info("[SeatUiSnapshot][+] {} {}", n.tag, n.attrs);
        for (Modificado m : diff.modificados) {
            log.info("[SeatUiSnapshot][~] {} en {}", m.atributosCambiados, m.antes.identidad());
            for (String attr : m.atributosCambiados) {
                log.info("[SeatUiSnapshot][~]   {}: '{}' -> '{}'", attr,
                        m.antes.attrs.get(attr), m.despues.attrs.get(attr));
            }
        }
        if (diff.esVacio()) log.info("[SeatUiSnapshot] Sin diferencias en este nivel.");
    }

    /**
     * Resumen legible. Las etiquetas semánticas (Continuar/contador/precio) son
     * heurísticas sobre el texto de los nodos cambiados — solo para orientar la
     * lectura; el diff literal de arriba es la evidencia real, esto no la sustituye.
     */
    private static String resumen(Diff diff) {
        if (diff.esVacio()) return "No cambió absolutamente nada.";

        StringBuilder sb = new StringBuilder();
        sb.append("Cambiaron ").append(diff.modificados.size()).append(" nodo(s). ");
        sb.append("Se agregaron ").append(diff.aparecieron.size()).append(". ");
        sb.append("Se eliminaron ").append(diff.desaparecieron.size()).append(". ");

        Set<String> etiquetas = new LinkedHashSet<>();
        for (Modificado m : diff.modificados) etiquetarSiAplica(m.despues, etiquetas);
        for (Nodo n : diff.aparecieron) etiquetarSiAplica(n, etiquetas);
        for (Nodo n : diff.desaparecieron) etiquetarSiAplica(n, etiquetas);
        etiquetas.forEach(t -> sb.append(t).append(" "));
        return sb.toString().trim();
    }

    private static void etiquetarSiAplica(Nodo n, Set<String> etiquetas) {
        String texto = (safe(n.attrs.get("label")) + " " + safe(n.attrs.get("name")) + " " + safe(n.attrs.get("value"))).toLowerCase();
        if (texto.contains("continuar")) etiquetas.add("Cambió el botón Continuar.");
        if (texto.matches(".*\\$\\s?\\d.*")) etiquetas.add("Cambió el precio.");
        if (texto.matches(".*\\d+\\s*(asiento|butaca)s?.*")) etiquetas.add("Cambió el contador de asientos.");
        if (texto.contains("comprar")) etiquetas.add("Cambió el botón Comprar.");
        if (texto.contains("resumen") || texto.contains("total")) etiquetas.add("Cambió el resumen/total.");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String textoCompleto(String contexto, Diff nivel1, Diff nivel2) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SeatUiSnapshot — ").append(contexto).append(" ===\n\n");
        sb.append("NIVEL 1 (mapa de asientos):\n").append(resumen(nivel1)).append("\n");
        volcarDiff(sb, nivel1);
        if (nivel2 != null) {
            sb.append("\nNIVEL 2 (árbol completo):\n").append(resumen(nivel2)).append("\n");
            volcarDiff(sb, nivel2);
        }
        return sb.toString();
    }

    private static void volcarDiff(StringBuilder sb, Diff diff) {
        for (Nodo n : diff.desaparecieron) sb.append("[-] ").append(n.tag).append(' ').append(n.attrs).append('\n');
        for (Nodo n : diff.aparecieron) sb.append("[+] ").append(n.tag).append(' ').append(n.attrs).append('\n');
        for (Modificado m : diff.modificados) {
            sb.append("[~] ").append(m.antes.identidad()).append(" cambiaron: ").append(m.atributosCambiados).append('\n');
            for (String attr : m.atributosCambiados) {
                sb.append("    ").append(attr).append(": '").append(m.antes.attrs.get(attr))
                        .append("' -> '").append(m.despues.attrs.get(attr)).append("'\n");
            }
        }
    }

    private static String sanitize(String s) {
        return s == null ? "snapshot" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
