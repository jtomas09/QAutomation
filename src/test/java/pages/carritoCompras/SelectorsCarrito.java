package pages.carritoCompras;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import org.openqa.selenium.By;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.common.BasePage;

import static pages.carritoCompras.LocatorsCarrito.*;

public class SelectorsCarrito extends BasePage {

    // Totales capturados en el carrito; accesibles desde SelectorsCheckOut para comparar.
    private static final ThreadLocal<Map<String, String>> TOTALES_CAPTURADOS =
        ThreadLocal.withInitial(java.util.LinkedHashMap::new);

    public static Map<String, String> getTotalesCapturados() {
        return java.util.Collections.unmodifiableMap(TOTALES_CAPTURADOS.get());
    }

    public SelectorsCarrito(AndroidDriver driver) {
        super(driver);
    }

    public void validarAsientoEnCarrito() {
        for (int i = 0; i < 5; i++) {
            if (isVisibleQuick(BADGE_ASIENTOS_CARRITO)) break;
            slowSwipeUp();
        }
        validarElementoVisible(BADGE_ASIENTOS_CARRITO);

        List<Map<String, String>> boletos = recopilarBoletosCarrito();

        List<String> detalle = new ArrayList<>();
        for (Map<String, String> b : boletos)
            detalle.add(b.get("nombre") + " [" + b.get("asiento") + "] – " + b.get("personas"));

        String resumen = boletos.isEmpty()
            ? "⚠️ No se detectaron boletos en el carrito"
            : "✅ " + boletos.size() + " boleto(s) en carrito: " + detalle;

        Allure.step(resumen);
    }

    private List<Map<String, String>> recopilarBoletosCarrito() {
        final String MARCA_FIN = "Alimentos";
        final java.util.Set<String> UI_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Carrito de compras", "Asientos", "Editar", "Continuar", "Detalles de cine", "Eliminar"
        ));
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        List<Map<String, String>> boletos = new ArrayList<>();
        java.util.Set<String> asientosRegistrados = new java.util.HashSet<>();
        int screenH = driver.manage().window().getSize().getHeight();
        boolean finAlcanzado = false;

        for (int scroll = 0; scroll < 10 && !finAlcanzado; scroll++) {
            List<Object[]> entradas = new ArrayList<>();
            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                boolean seccionAsientosVista = false;
                int yInicioSeccion = 0;
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName("android.widget.TextView");
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                    String text   = el.getAttribute("text");
                    String bounds = el.getAttribute("bounds");
                    if (text == null || text.isBlank() || bounds == null) continue;

                    java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                    if (!m.matches()) continue;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    if (y < 0 || y > screenH) continue;

                    if (!seccionAsientosVista && "Asientos".equals(text)) {
                        seccionAsientosVista = true;
                        yInicioSeccion = y;
                        continue;
                    }
                    if (seccionAsientosVista && y > yInicioSeccion)
                        entradas.add(new Object[]{ y, text });
                }
            } catch (Exception ignored) {}

            entradas.sort((a, b) -> (Integer) a[0] - (Integer) b[0]);

            List<String> textos = new ArrayList<>();
            for (Object[] e : entradas) {
                String txt = (String) e[1];
                if (UI_EXCLUIR.contains(txt)) continue;
                if (MARCA_FIN.equals(txt)) { finAlcanzado = true; break; }
                textos.add(txt);
            }

            procesarCardsBoletos(textos, boletos, asientosRegistrados);

            if (!finAlcanzado) { slowSwipeUp(); sleep(200); }
        }

        System.out.println("\n=== Carrito de boletos (" + boletos.size() + " boleto(s)) ===");
        for (int i = 0; i < boletos.size(); i++) {
            Map<String, String> b = boletos.get(i);
            System.out.println("  " + (i + 1) + ". " + b.get("nombre")
                + " [" + b.get("asiento") + "] – " + b.get("personas"));
        }
        System.out.println("=================================================\n");

        return boletos;
    }

    private void procesarCardsBoletos(List<String> texts,
            List<Map<String, String>> boletos,
            java.util.Set<String> asientosRegistrados) {
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (!t.matches("[A-Z]+\\d+")) continue;
            String asiento = t;
            if (asientosRegistrados.contains(asiento)) continue;

            // nombre: último texto válido antes del asiento
            String nombre = null;
            for (int j = i - 1; j >= 0; j--) {
                String prev = texts.get(j);
                if (prev.startsWith("$") || "·".equals(prev)
                        || prev.matches("[A-Z]+\\d+") || prev.matches("\\d+ persona(s)?")) continue;
                nombre = prev;
                break;
            }

            // personas: primer "X persona(s)" después del asiento
            String personas = null;
            for (int j = i + 1; j < texts.size() && j <= i + 3; j++) {
                String next = texts.get(j);
                if (next.matches("\\d+ persona(s)?")) { personas = next; break; }
            }

            if (nombre == null) continue;
            asientosRegistrados.add(asiento);
            Map<String, String> b = new java.util.LinkedHashMap<>();
            b.put("nombre", nombre);
            b.put("asiento", asiento);
            b.put("personas", personas != null ? personas : "");
            boletos.add(b);
        }
    }

    public void validarAlimentoEnCarrito() {
        for (int i = 0; i < 5; i++) {
            if (isVisibleQuick(BADGE_ALIMENTOS_CARRITO)) break;
            slowSwipeUp();
        }
        validarElementoVisible(BADGE_ALIMENTOS_CARRITO);

        List<Map<String, String>> productos = recopilarProductosCarrito();

        List<String> nombres = new ArrayList<>();
        for (Map<String, String> p : productos) nombres.add(p.get("nombre"));

        String resumen = productos.isEmpty()
            ? "⚠️ No se detectaron productos en el carrito"
            : "✅ " + productos.size() + " alimento(s) en carrito: " + nombres;

        Allure.step(resumen);
    }

    /**
     * Recopila los productos del carrito de alimentos con nombre y personalización.
     * Usa getPageSource() (1 llamada HTTP por scroll) en lugar de leer atributos
     * por elemento (3 llamadas HTTP × N elementos = hasta 900 llamadas por ciclo).
     */
    private List<Map<String, String>> recopilarProductosCarrito() {
        final String MARCA_FIN    = "Añadir más alimentos";
        final String MARCA_EDITAR = "Editar";
        final java.util.Set<String> UI_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Carrito de compras", "Elegir una película", "Alimentos",
            "Añadir más alimentos", "Continuar", "Asientos", "Eliminar"
        ));
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        List<Map<String, String>> productos = new ArrayList<>();
        java.util.Set<String> nombresRegistrados = new java.util.HashSet<>();
        int screenH = driver.manage().window().getSize().getHeight();
        boolean finAlcanzado = false;

        for (int scroll = 0; scroll < 10 && !finAlcanzado; scroll++) {

            // ── Un solo dump XML reemplaza O(N×3) llamadas HTTP por elemento ──
            List<Object[]> entradas = new ArrayList<>();
            int yInicioSeccion = 0;
            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                boolean seccionAlimentosVista = false;
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName("android.widget.TextView");
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                    String text   = el.getAttribute("text");
                    String bounds = el.getAttribute("bounds");
                    if (text == null || text.isBlank() || bounds == null) continue;

                    java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                    if (!m.matches()) continue;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    if (y < 0 || y > screenH) continue;

                    if (!seccionAlimentosVista && "Alimentos".equals(text)) {
                        seccionAlimentosVista = true;
                        yInicioSeccion = y;
                        continue;
                    }
                    entradas.add(new Object[]{ y, x, text });
                }
            } catch (Exception ignored) {}

            entradas.sort((a, b) -> {
                int dy = (Integer) a[0] - (Integer) b[0];
                return dy != 0 ? dy : (Integer) a[1] - (Integer) b[1];
            });

            // ── Agrupar por card (entre marcas "Editar") ──────────────────────
            List<Object[]> cardEntradas = new ArrayList<>();
            for (Object[] e : entradas) {
                int    y   = (Integer) e[0];
                String txt = (String)  e[2];
                if (y <= yInicioSeccion) continue;
                if (UI_EXCLUIR.contains(txt)) continue;
                if (MARCA_FIN.equals(txt)) { finAlcanzado = true; break; }
                if (MARCA_EDITAR.equals(txt)) {
                    procesarCardCarrito(cardEntradas, productos, nombresRegistrados);
                    cardEntradas.clear();
                    continue;
                }
                cardEntradas.add(e);
            }

            if (!finAlcanzado) { slowSwipeUp(); sleep(200); }
        }

        System.out.println("\n=== Carrito de alimentos (" + productos.size() + " producto(s)) ===");
        for (int i = 0; i < productos.size(); i++) {
            Map<String, String> p = productos.get(i);
            String pers = p.get("personalizacion");
            System.out.println("  " + (i + 1) + ". " + p.get("nombre")
                + (pers != null && !pers.isBlank() ? " → " + pers : ""));
        }
        System.out.println("=================================================\n");

        return productos;
    }

    /**
     * Identifica nombre y personalización de una card a partir de sus TextViews.
     *
     * Estrategia: el contador de cantidad ("1", "2"…) siempre aparece en la misma
     * fila que el nombre del producto. Se usa su Y como ancla:
     *   - nombre       = primer texto no excluido cuya Y esté a ≤ 40 px del contador
     *   - personaliz.  = el resto de textos no excluidos de la card
     *
     * Si no hay contador visible, se toma el primer texto apto como nombre (fallback).
     */
    // Identifica textos de precio en cualquier formato: "$15.00", "4,95 €", "15,00€"
    private static boolean esPrecio(String txt) {
        return txt.matches("^\\d+$") || txt.startsWith("$") || txt.contains("€");
    }

    private void procesarCardCarrito(List<Object[]> tvs,
            List<Map<String, String>> productos,
            java.util.Set<String> nombresRegistrados) {

        if (tvs.isEmpty()) return;

        // 1. Localizar el contador de cantidad (número puro de 1-2 dígitos: 1-99)
        int yContador = -1;
        for (Object[] e : tvs) {
            String txt = (String) e[2];
            if (txt.matches("^[1-9]\\d?$")) { yContador = (Integer) e[0]; break; }
        }

        // 2. Nombre del producto: primer texto en la fila del contador (±40 px),
        //    que no sea número puro ni precio. Fallback: primer texto apto sin restricción de Y.
        String nombre = null;
        for (Object[] e : tvs) {
            String txt = (String) e[2];
            int y       = (Integer) e[0];
            if (esPrecio(txt)) continue;
            if (yContador >= 0 && Math.abs(y - yContador) > 40) continue;
            nombre = txt;
            break;
        }
        if (nombre == null) { // fallback sin restricción de fila
            for (Object[] e : tvs) {
                String txt = (String) e[2];
                if (!esPrecio(txt)) { nombre = txt; break; }
            }
        }
        if (nombre == null || nombresRegistrados.contains(nombre)) return;

        // 3. Personalización: todo lo demás excepto precio, cantidad y el nombre mismo
        List<String> partes = new ArrayList<>();
        for (Object[] e : tvs) {
            String txt = (String) e[2];
            if (txt.equals(nombre) || esPrecio(txt)) continue;
            partes.add(txt);
        }

        nombresRegistrados.add(nombre);
        Map<String, String> prod = new java.util.LinkedHashMap<>();
        prod.put("nombre", nombre);
        prod.put("personalizacion", String.join(", ", partes));
        productos.add(prod);
    }

// ── ESPAÑA: Butacas / Comida ──────────────────────────────────────────────────

    public void validarAsientoEnCarritoEspana() {
        for (int i = 0; i < 5; i++) {
            if (isVisibleQuick(BADGE_BUTACAS_CARRITO)) break;
            slowSwipeUp();
        }
        validarElementoVisible(BADGE_BUTACAS_CARRITO);

        List<Map<String, String>> boletos = recopilarBoletosCarritoEspana();

        List<String> detalle = new ArrayList<>();
        for (Map<String, String> b : boletos)
            detalle.add(b.get("nombre") + " [" + b.get("asiento") + "] – " + b.get("personas"));

        String resumen = boletos.isEmpty()
            ? "⚠️ No se detectaron butacas en el carrito"
            : "✅ " + boletos.size() + " butaca(s) en carrito: " + detalle;

        Allure.step(resumen);
    }

    private List<Map<String, String>> recopilarBoletosCarritoEspana() {
        final String MARCA_FIN = "Comida";
        final java.util.Set<String> UI_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Carrito de compras", "Butacas", "Editar", "Continuar", "Detalles de cine", "Eliminar"
        ));
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        List<Map<String, String>> boletos = new ArrayList<>();
        java.util.Set<String> asientosRegistrados = new java.util.HashSet<>();
        int screenH = driver.manage().window().getSize().getHeight();
        boolean finAlcanzado = false;

        for (int scroll = 0; scroll < 10 && !finAlcanzado; scroll++) {
            List<Object[]> entradas = new ArrayList<>();
            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                boolean seccionVista = false;
                int yInicioSeccion = 0;
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName("android.widget.TextView");
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                    String text   = el.getAttribute("text");
                    String bounds = el.getAttribute("bounds");
                    if (text == null || text.isBlank() || bounds == null) continue;

                    java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                    if (!m.matches()) continue;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    if (y < 0 || y > screenH) continue;

                    if (!seccionVista && "Butacas".equals(text)) {
                        seccionVista = true;
                        yInicioSeccion = y;
                        continue;
                    }
                    if (seccionVista && y > yInicioSeccion)
                        entradas.add(new Object[]{ y, text });
                }
            } catch (Exception ignored) {}

            entradas.sort((a, b) -> (Integer) a[0] - (Integer) b[0]);

            List<String> textos = new ArrayList<>();
            for (Object[] e : entradas) {
                String txt = (String) e[1];
                if (UI_EXCLUIR.contains(txt)) continue;
                if (MARCA_FIN.equals(txt)) { finAlcanzado = true; break; }
                textos.add(txt);
            }

            procesarCardsBoletoEspana(textos, boletos, asientosRegistrados);

            if (!finAlcanzado) { slowSwipeUp(); sleep(200); }
        }

        System.out.println("\n=== Carrito de butacas España (" + boletos.size() + " boleto(s)) ===");
        for (int i = 0; i < boletos.size(); i++) {
            Map<String, String> b = boletos.get(i);
            System.out.println("  " + (i + 1) + ". " + b.get("nombre")
                + " [" + b.get("asiento") + "] – " + b.get("personas"));
        }
        System.out.println("=================================================\n");

        return boletos;
    }

    // España: asientos con formato "Fila N • Butaca N" o "Fila A • Butaca N" (salas premium)
    private void procesarCardsBoletoEspana(List<String> texts,
            List<Map<String, String>> boletos,
            java.util.Set<String> asientosRegistrados) {
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (!t.matches("Fila [A-Z0-9]+ . Butaca \\d+")) continue;
            String asiento = t;
            if (asientosRegistrados.contains(asiento)) continue;

            String nombre = null;
            for (int j = i - 1; j >= 0; j--) {
                String prev = texts.get(j);
                if (prev.startsWith("$") || "·".equals(prev)
                        || prev.matches("Fila [A-Z0-9]+ . Butaca \\d+")
                        || prev.matches("\\d+ persona(s)?")) continue;
                nombre = prev;
                break;
            }

            String personas = null;
            for (int j = i + 1; j < texts.size() && j <= i + 3; j++) {
                String next = texts.get(j);
                if (next.matches("\\d+ persona(s)?")) { personas = next; break; }
            }

            if (nombre == null) continue;
            asientosRegistrados.add(asiento);
            Map<String, String> b = new java.util.LinkedHashMap<>();
            b.put("nombre", nombre);
            b.put("asiento", asiento);
            b.put("personas", personas != null ? personas : "");
            boletos.add(b);
        }
    }

    public void validarAlimentoEnCarritoEspana() {
        for (int i = 0; i < 5; i++) {
            if (isVisibleQuick(BADGE_COMIDA_CARRITO)) break;
            slowSwipeUp();
        }
        validarElementoVisible(BADGE_COMIDA_CARRITO);

        List<Map<String, String>> productos = recopilarProductosCarritoEspana();

        List<String> nombres = new ArrayList<>();
        for (Map<String, String> p : productos) nombres.add(p.get("nombre"));

        String resumen = productos.isEmpty()
            ? "⚠️ No se detectaron productos en el carrito"
            : "✅ " + productos.size() + " comida(s) en carrito: " + nombres;

        Allure.step(resumen);
    }

    private List<Map<String, String>> recopilarProductosCarritoEspana() {
        final String MARCA_FIN    = "Añadir comidas";
        final String MARCA_EDITAR = "Editar";
        final java.util.Set<String> UI_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Carrito de compras", "Elegir una película", "Comida",
            "Añadir comidas", "Continuar", "Butacas", "Eliminar"
        ));
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        List<Map<String, String>> productos = new ArrayList<>();
        java.util.Set<String> nombresRegistrados = new java.util.HashSet<>();
        int screenH = driver.manage().window().getSize().getHeight();
        boolean finAlcanzado = false;

        for (int scroll = 0; scroll < 10 && !finAlcanzado; scroll++) {
            List<Object[]> entradas = new ArrayList<>();
            int yInicioSeccion = 0;
            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                boolean seccionVista = false;
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName("android.widget.TextView");
                for (int i = 0; i < nodes.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                    String text   = el.getAttribute("text");
                    String bounds = el.getAttribute("bounds");
                    if (text == null || text.isBlank() || bounds == null) continue;

                    java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                    if (!m.matches()) continue;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    if (y < 0 || y > screenH) continue;

                    if (!seccionVista && "Comida".equals(text)) {
                        seccionVista = true;
                        yInicioSeccion = y;
                        continue;
                    }
                    entradas.add(new Object[]{ y, x, text });
                }
            } catch (Exception ignored) {}

            entradas.sort((a, b) -> {
                int dy = (Integer) a[0] - (Integer) b[0];
                return dy != 0 ? dy : (Integer) a[1] - (Integer) b[1];
            });

            List<Object[]> cardEntradas = new ArrayList<>();
            for (Object[] e : entradas) {
                int    y   = (Integer) e[0];
                String txt = (String)  e[2];
                if (y <= yInicioSeccion) continue;
                if (UI_EXCLUIR.contains(txt)) continue;
                if (MARCA_FIN.equals(txt)) { finAlcanzado = true; break; }
                if (MARCA_EDITAR.equals(txt)) {
                    procesarCardCarrito(cardEntradas, productos, nombresRegistrados);
                    cardEntradas.clear();
                    continue;
                }
                cardEntradas.add(e);
            }

            if (!finAlcanzado) { slowSwipeUp(); sleep(200); }
        }

        System.out.println("\n=== Carrito de comida España (" + productos.size() + " producto(s)) ===");
        for (int i = 0; i < productos.size(); i++) {
            Map<String, String> p = productos.get(i);
            String pers = p.get("personalizacion");
            System.out.println("  " + (i + 1) + ". " + p.get("nombre")
                + (pers != null && !pers.isBlank() ? " → " + pers : ""));
        }
        System.out.println("=================================================\n");

        return productos;
    }

// ─────────────────────────────────────────────────────────────────────────────

    public void validarFuncionEnCarrito() {
        waitForVisibility(By.xpath("//android.widget.TextView[@text='Carrito de compras']"));
        Map<String, String> detalles = recopilarDetallesFuncion();

        String pelicula = detalles.getOrDefault("pelicula", "—");
        String cine     = detalles.getOrDefault("Cine", "—");
        String sala     = detalles.get("Sala");
        String fecha    = detalles.getOrDefault("Fecha y hora", "—");

        StringBuilder sb = new StringBuilder("✅ Función en carrito –");
        sb.append(" 🎬 ").append(pelicula);
        sb.append(" | 🏢 ").append(cine);
        if (sala != null && !sala.isBlank()) sb.append(" | 🎭 Sala: ").append(sala);
        sb.append(" | 📅 ").append(fecha);

        Allure.step(sb.toString());
    }

    private Map<String, String> recopilarDetallesFuncion() {
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
        final java.util.Set<String> LABELS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Cine", "Sala", "Fecha y hora", "Duración", "Clasificación"
        ));
        final java.util.Set<String> UI_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Carrito de compras", "Detalles de cine", "Editar", "Continuar",
            "Asientos", "Alimentos", "Eliminar"
        ));

        Map<String, String> resultado = new java.util.LinkedHashMap<>();
        int screenH = driver.manage().window().getSize().getHeight();

        try {
            javax.xml.parsers.DocumentBuilder db =
                javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(
                new java.io.ByteArrayInputStream(
                    driver.getPageSource().getBytes("UTF-8")));

            List<Object[]> entradas = new ArrayList<>();
            org.w3c.dom.NodeList nodes = doc.getElementsByTagName("android.widget.TextView");
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                String text   = el.getAttribute("text");
                String bounds = el.getAttribute("bounds");
                if (text == null || text.isBlank() || bounds == null) continue;

                java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                if (!m.matches()) continue;
                int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                if (y < 0 || y > screenH) continue;
                if (UI_EXCLUIR.contains(text)) continue;

                entradas.add(new Object[]{ y, text });
            }
            entradas.sort((a, b) -> (Integer) a[0] - (Integer) b[0]);

            // Título de película: último texto sustantivo antes del label "Duración"
            int yDuracion = Integer.MAX_VALUE;
            for (Object[] e : entradas) {
                if ("Duración".equals(e[1])) { yDuracion = (Integer) e[0]; break; }
            }
            for (int i = entradas.size() - 1; i >= 0; i--) {
                int    y   = (Integer) entradas.get(i)[0];
                String txt = (String)  entradas.get(i)[1];
                if (y >= yDuracion) continue;
                if (!LABELS.contains(txt)) { resultado.put("pelicula", txt); break; }
            }

            // Labels con sus valores: el texto más largo en la misma fila del label
            // (cuando hay badge VIP + nombre completo en la misma fila, toma el más largo)
            for (int i = 0; i < entradas.size(); i++) {
                String label = (String) entradas.get(i)[1];
                if (!LABELS.contains(label) || "Duración".equals(label) || "Clasificación".equals(label)) continue;
                String valorEncontrado = null;
                int    yValorBase      = -1;
                for (int j = i + 1; j < entradas.size(); j++) {
                    String next  = (String)  entradas.get(j)[1];
                    int    yNext = (Integer) entradas.get(j)[0];
                    if (LABELS.contains(next) || UI_EXCLUIR.contains(next)) continue;
                    if (valorEncontrado == null) {
                        valorEncontrado = next;
                        yValorBase      = yNext;
                    } else if (Math.abs(yNext - yValorBase) <= 30) {
                        if (next.length() > valorEncontrado.length()) valorEncontrado = next;
                    } else {
                        break;
                    }
                }
                if (valorEncontrado != null) resultado.put(label, valorEncontrado);
            }
        } catch (Exception ignored) {}

        System.out.println("\n=== Detalles de función en carrito ===");
        resultado.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        System.out.println("======================================\n");

        return resultado;
    }

    public void capturarTotalesCarrito() {
        for (int i = 0; i < 8; i++) {
            if (isVisibleQuick(TOTAL_CARRITO)) break;
            slowSwipeUp();
        }
        Map<String, String> totales = TOTALES_CAPTURADOS.get();
        totales.clear();
        totales.put("Subtotal",           leerTexto(SUBTOTAL_CARRITO));
        totales.put("Cargo por servicio", leerTexto(CARGO_SERVICIO_CARRITO));
        totales.put("Total",              leerTexto(TOTAL_CARRITO));

        String linea = "Subtotal: " + totales.get("Subtotal")
            + " | Cargo por servicio: " + totales.get("Cargo por servicio")
            + " | Total: " + totales.get("Total");
        System.out.println("[TOTALES-CARRITO] " + linea);
        Allure.step("💰 Totales en carrito – " + linea);
    }

    private String leerTexto(By locator) {
        try { return driver.findElement(locator).getText(); } catch (Exception e) { return "—"; }
    }

    public void clickContinuarCarrito() {
        capturarTotalesCarrito();
        this.click(BOTON_CONTINUAR_CARRITO);
        verificarErrorProcesarOrden();
    }

    private void verificarErrorProcesarOrden() {
        By alertaError  = By.xpath("//android.widget.TextView[contains(@text,'Lo sentimos')]");
        By botonAceptar = By.xpath("//android.widget.TextView[@text='Aceptar']");

        // Polling hasta 3 s: el error aparece rápido si el servicio rechaza la orden
        long limite = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < limite) {
            if (isVisibleQuick(alertaError)) break;
            sleep(500);
        }
        if (!isVisibleQuick(alertaError)) return;

        String mensaje = "Error desconocido al procesar la orden";
        try { mensaje = driver.findElement(alertaError).getText(); } catch (Exception ignored) {}

        takeScreenshot("Error – procesar orden carrito");
        System.out.println("[ERROR-CARRITO] " + mensaje);
        Allure.step("❌ Error al procesar la orden: " + mensaje);

        try { driver.findElement(botonAceptar).click(); } catch (Exception ignored) {}

        org.junit.jupiter.api.Assertions.fail("❌ Error al procesar la orden: " + mensaje);
    }


}
