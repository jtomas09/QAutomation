package pages.alimentos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import pages.common.BasePage;

import static pages.alimentos.LocatorsAlimentos.*;

public class SelectorsAlimentos extends BasePage {
    public static final int FAST_VISIBLE_SECONDS = 2;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelectorsAlimentos.class);

    public SelectorsAlimentos(AndroidDriver driver) {
        super(driver);
    }

    public void clickSaltarAlimentos() {
        By carrito = By.xpath("//android.widget.TextView[@text='Carrito de compras']");
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            if (isVisibleQuick(BOTON_CONTINUAR_Y_PAGAR)) { this.click(BOTON_CONTINUAR_Y_PAGAR); return; }
            if (isVisibleQuick(carrito)) return;
            sleep(500);
        }
    }

    public void clickTabSnacks() {
        waitForVisibility(INPUT_FOLIO_ALIMENTOS);
        int cy = obtenerBarraCategoriasY();
        for (int i = 0; i < 5; i++) {
            fastSwipeLeftAtY(cy);
            sleep(400);
            if (clickIfPresent(TAB_SNACKS)) return;
        }
        throw new AssertionError("FAST-FAIL: Tab 'Snacks' NO encontrado tras desplazamiento horizontal en la barra de pestañas.");
    }

    public void buscarExtraQueso() {
        this.click(BUSCADOR_ALIMENTOS);
        this.click(INPUT_BUSCADOR_ALIMENTOS);
        this.driver.executeScript("mobile: type", Map.of("text", "extra queso"));
        this.click(RESULTADO_EXTRA_QUESO);
    }

    public void buscarProducto() {
        this.click(BUSCADOR_ALIMENTOS);
        this.click(INPUT_BUSCADOR_ALIMENTOS);
        this.driver.executeScript("mobile: type", Map.of("text", "combo pancho mix extreme grande"));
        sleep(1500);
        this.click(PRIMER_RESULTADO_BUSQUEDA); // primer resultado de búsqueda
    }

// VINCULACIÓN ALIMENTOS PREMIUM ESPAÑA

    public void buscarAlimentoPremium() {
        this.click(BUSCADOR_ALIMENTOS);
        this.click(INPUT_BUSCADOR_ALIMENTOS);
        this.driver.executeScript("mobile: type", Map.of("text", "burguer doble"));
        sleep(1500);
        this.click(PRIMER_RESULTADO_BUSQUEDA); // primer resultado de búsqueda
    }   

    public void buscarAlimentoEstandar() {
        this.click(BUSCADOR_ALIMENTOS);
        this.click(INPUT_BUSCADOR_ALIMENTOS);
        this.driver.executeScript("mobile: type", Map.of("text", "refresco"));
        sleep(1500);
        this.click(PRIMER_RESULTADO_BUSQUEDA); // primer resultado de búsqueda
    }  

    public void seleccionarAlimentoPremium() {
        buscarAlimentoPremium();
        completarPersonalizacion();
    }

    public void seleccionarAlimentoEstandarEspaña() {
        buscarAlimentoEstandar();
        completarPersonalizacion();
    }

    public void validarModalOrderLinkingEspaña() {
        validarElementoVisible(TÍTULO_MODAL_VINCULACION_ESPAÑA);
        validarElementoVisible(BOTON_LLEVAR_A_BUTACA);
        Allure.step("✅ Modal de vinculación de pedidos España mostrado correctamente");
    }

    public void clickExtraQueso() {
        if (clickIfPresent(EXTRA_QUESO)) return;

        // Desplazamiento lateral dentro del contenedor de productos Snacks
        for (int i = 0; i < 8; i++) {
            try {
                WebElement container = driver.findElement(PRODUCTOS_SNACKS);
                int left  = container.getLocation().getX() + 20;
                int right = container.getLocation().getX() + container.getSize().getWidth() - 20;
                int cy    = container.getLocation().getY() + container.getSize().getHeight() / 2;
                swipeW3C(right, cy, left, cy, 600L);
                sleep(400);
            } catch (Exception ignored) {}

            if (clickIfPresent(EXTRA_QUESO)) return;
        }

        throw new AssertionError("FAST-FAIL: Tarjeta 'Extra Queso' NO encontrada tras desplazamiento horizontal en la sección Snacks.");
    }

    public void agregarExtraQueso() {
        this.click(BOTON_AGREGAR_EXTRAQUESO);
    }

    public void vincularOrdenVIPSinSesion() {
        scrollYClick(BOTON_BUSCAR_FUNCION);
        scrollYClick(BOTON_ELEGIR_MANUALMENTE);
        scrollYClick(ELEGIR_PELICULA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_HORA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_FILA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_NUMERO);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(BOTON_VINCULAR_ORDEN);
        scrollYClick(BOTON_CONFIRMAR_VINCULACION);
        sleep(1500);

        if (isVisibleQuick(ALERTA_ERROR_VINCULACION)) {
            String msg = "Hubo un error al vincular tu función";
            takeScreenshot("Error de vinculación - VIP");
            log.error("[VincularVIP] Error de vinculación detectado: {}", msg);
            Allure.step("❌ Error de vinculación detectado: " + msg);
            org.junit.jupiter.api.Assertions.fail("Error de vinculación VIP: " + msg);
        }

        By errorInesperado = By.xpath(
            "//android.widget.TextView[contains(@text,'error') or contains(@text,'Error')" +
            " or contains(@text,'falló') or contains(@text,'no se pudo')]");
        if (isVisibleQuick(errorInesperado)) {
            String textoError = "";
            try { textoError = driver.findElement(errorInesperado).getText(); } catch (Exception ignored) {}
            takeScreenshot("Error inesperado de vinculación - VIP");
            log.error("[VincularVIP] Error inesperado detectado: {}", textoError);
            Allure.step("❌ Error inesperado en vinculación VIP: " + textoError);
            org.junit.jupiter.api.Assertions.fail("Error inesperado al vincular la orden VIP: " + textoError);
        }
    }

public void vincularOrdenEspaña() {
        scrollYClick(BOTON_LLEVAR_A_BUTACA);
        scrollYClick(ELEGIR_PELICULA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_HORA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_FILA);
        this.click(PRIMERA_FILA_HABILITADA_PREMIUM);
        scrollYClick(ELEGIR_NUMERO_DE_BUTACA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(BOTON_VINCULAR_ORDEN);
        scrollYClick(BOTON_CONFIRMAR_VINCULACION);
        sleep(1500);

        if (isVisibleQuick(ALERTA_ERROR_VINCULACION)) {
            String msg = "Hubo un error al vincular tu función";
            takeScreenshot("Error de vinculación - España");
            log.error("[VincularEspaña] Error de vinculación detectado: {}", msg);
            Allure.step("❌ Error de vinculación detectado: " + msg);
            org.junit.jupiter.api.Assertions.fail("Error de vinculación: " + msg);
        }

        By errorInesperado = By.xpath(
            "//android.widget.TextView[contains(@text,'error') or contains(@text,'Error')" +
            " or contains(@text,'falló') or contains(@text,'no se pudo')]");
        if (isVisibleQuick(errorInesperado)) {
            String textoError = "";
            try { textoError = driver.findElement(errorInesperado).getText(); } catch (Exception ignored) {}
            takeScreenshot("Error inesperado de vinculación - España");
            log.error("[VincularEspaña] Error inesperado detectado: {}", textoError);
            Allure.step("❌ Error inesperado en vinculación: " + textoError);
            org.junit.jupiter.api.Assertions.fail("Error inesperado al vincular la orden: " + textoError);
        }
    }

    public void saltarVinculacionEspaña() {
        sleep(1500);
        if (isVisibleQuick(TÍTULO_MODAL_VINCULACION_ESPAÑA)) {
            Allure.step("ℹ️ Modal de vinculación detectado – saltando vinculación para continuar al checkout");
            click(BOTON_SALTAR_VINCULACION_ESPAÑA);
        }
    }

//VINCULACION DE ORDEN DE ALIMENTOS EN CHILE

    public void vincularOrdenChile() {
        By tituloPantalla = By.xpath("//android.widget.TextView[@text='Vinculación de pedidos']");
        boolean pantallaVisible = isVisibleQuick(tituloPantalla); 

        if (!pantallaVisible) {
            takeScreenshot("Pantalla de vinculación no encontrada");
            Allure.step("❌ La pantalla de vinculación de orden no se muestra");
            org.junit.jupiter.api.Assertions.fail(
                "La pantalla 'Vinculación de pedidos' no se muestra en este cine");
            return;
        }

        Allure.step("✅ Pantalla de vinculación de orden mostrada correctamente");
        scrollYClick(ELEGIR_PELICULA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_HORA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_FILA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(ELEGIR_NUMERO_BUTACA);
        this.click(PRIMERA_OPCION_DESPLEGABLE);
        scrollYClick(BOTON_BUSCAR);
        scrollYClick(BOTON_CONFIRMAR_VINCULACION);
    }


    private void scrollYClick(By locator) {
        ensureVisibleNoClick(locator, 5);
        this.click(locator);
    }


    public void clikIrAPagar() {
        List<String> errores = ERRORES_PRODUCTOS_ACUMULADOS.get();
        if (!errores.isEmpty()) {
            String resumen = "• " + String.join("\n• ", errores);
            System.out.println("[ERRORES-ALIMENTOS] " + errores.size()
                + " producto(s) no se pudieron agregar al carrito:\n" + resumen);
            Allure.addAttachment(
                "⚠️ Productos con error al agregar (" + errores.size() + ")",
                "text/plain",
                errores.size() + " producto(s) no pudieron agregarse al carrito:\n\n" + resumen);
            String stepUuid = java.util.UUID.randomUUID().toString();
            Allure.getLifecycle().startStep(stepUuid,
                new io.qameta.allure.model.StepResult()
                    .setName("⚠️ " + errores.size() + " producto(s) con error – ver adjunto para detalle")
                    .setStatus(io.qameta.allure.model.Status.BROKEN));
            Allure.getLifecycle().stopStep(stepUuid);
            errores.clear();
        }
        this.click(BOTON_IR_A_PAGAR);
    }

    // ── Selección aleatoria de alimento ──────────────────────────────────────

    private String categoriaActual; // categoría elegida, compartida entre pasos

    // Acumula los productos que fallaron al agregar al carrito durante la sesión de alimentos.
    // Se reporta como resumen en clikIrAPagar() y se limpia ahí mismo.
    private static final ThreadLocal<List<String>> ERRORES_PRODUCTOS_ACUMULADOS =
        ThreadLocal.withInitial(ArrayList::new);

    // Tabs de la barra que no son categorías de productos y deben ignorarse
    private static final java.util.Set<String> TABS_EXCLUIDOS = new java.util.HashSet<>(
        java.util.Arrays.asList("Club Cinépolis", "Promocionales", "Buscar", "Mis promociones Movie Yelmo", "Gafas 3D"));

    /**
     * Paso 1: recorre la barra de categorías de izquierda a derecha recopilando
     * todos los tabs, elige uno al azar (excluyendo "Club Cinépolis" y "Promocionales")
     * y desde ahí regresa (swipe right) hasta encontrarlo y hacer tap — sin resetear
     * al inicio entre recopilación y click.
     */
    public void seleccionarTabAleatorio() {
        // Calcular Y central de la barra con retry: Compose puede recomponer el nodo
        // justo entre findElement() y getLocation(), dejando la referencia stale.
        // Usar el mínimo Y entre todas las instancias de textos de tab conocidos.
        // Compose renderiza el contenido (sección "Destacados") antes que la barra de tabs,
        // por lo que el primer resultado en DOM puede ser el título de sección (Y alto)
        // en lugar del tab real (Y bajo). El mínimo Y siempre corresponde al tab.
        int barraY    = encontrarTabBarY();
        int barraHalf = 30;

        // Resetear al inicio y esperar a que la barra se estabilice
        for (int i = 0; i < 4; i++) fastSwipeRightAtY(barraY);
        sleep(600);

        // Barrer hacia la izquierda recopilando todos los tabs.
        // Usa búsqueda global + filtro Y en lugar de buscar dentro de BARRA_CATEGORIAS_ALIMENTOS:
        // cuando la barra se desplaza, "Destacados" puede salir del viewport (Compose lazy-loading)
        // y findElement(BARRA_CATEGORIAS_ALIMENTOS) lanza excepción, silenciándola y terminando
        // la recolección antes de capturar todos los tabs.
        // sinCambios >= 3: requiere TRES lecturas consecutivas sin cambio para confirmar el final
        // (con >= 2 un render lento de Compose podía provocar un break prematuro en el recorrido).
        // sleep(600): 270ms gesto + 30ms BasePage + 600ms = 900ms total por swipe,
        // suficiente para que Compose lazy-loading termine de renderizar el tab recién visible.
        List<String> tabs = new ArrayList<>();
        int sinCambios = 0;
        for (int intento = 0; intento < 10; intento++) {
            int antes = tabs.size();
            try {
                List<WebElement> allTvs = safeFindElements(By.className("android.widget.TextView"));
                for (WebElement tv : allTvs) {
                    try {
                        int tvY = tv.getLocation().getY();
                        int distY = Math.abs(tvY - barraY);
                        String texto = tv.getText();
                        boolean filtradoPorY = distY > barraHalf + 10;
                        boolean excluido = TABS_EXCLUIDOS.contains(texto);
                        boolean yaEnLista = tabs.contains(texto);
                        if (filtradoPorY) continue;
                        if (texto != null && !texto.isBlank() && !excluido && !yaEnLista)
                            tabs.add(texto);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            if (tabs.size() == antes) { if (++sinCambios >= 3) break; } else sinCambios = 0;
            fastSwipeLeftAtY(barraY);
            sleep(600);
        }

        org.junit.jupiter.api.Assertions.assertFalse(tabs.isEmpty(),
            "No se encontraron categorías de productos en la barra de alimentos");

        // Loguear la lista completa para diagnóstico futuro
        Allure.step("📋 Categorías encontradas (" + tabs.size() + "): " + tabs);

        // Elegir categoría al azar — estamos al final de la barra
        categoriaActual = tabs.get(new Random().nextInt(tabs.size()));
        Allure.step("🎲 Categoría seleccionada al azar: " + categoriaActual);

        // Regresar (swipe right) hasta encontrar el tab elegido y hacer tap.
        // Usa búsqueda global + filtro Y (igual que clickTabCategoria) para no depender
        // de que BARRA_CATEGORIAS_ALIMENTOS contenga exactamente el tab en su subárbol.
        // barraHalf ya calculado al inicio del método (antes de cualquier swipe).
        for (int intento = 0; intento < 8; intento++) {
            try {
                for (WebElement tv : safeFindElements(
                        By.xpath("//android.widget.TextView[@text='" + categoriaActual + "']"))) {
                    if (Math.abs(tv.getLocation().getY() - barraY) <= barraHalf + 10) {
                        tv.click();
                        sleep(1500);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            fastSwipeRightAtY(barraY);
            sleep(400); // esperar a que Compose renderice los tabs recién visibles
        }
        org.junit.jupiter.api.Assertions.fail(
            "No se encontró la categoría en la barra: " + categoriaActual);
    }

    /** Paso 2: elige un producto al azar del carrusel de la categoría actual y lo abre. */
    public void seleccionarProductoEnSeccionActual() {
        org.junit.jupiter.api.Assertions.assertNotNull(categoriaActual,
            "Llama primero a seleccionarTabAleatorio()");
        seleccionarProductoEnSeccion(categoriaActual);
    }

    /**
     * Selecciona una categoría al azar de la barra de categorías (con scroll
     * lateral para cubrir todas), luego elige un producto disponible al azar
     * dentro de esa categoría y completa su flujo de personalización.
     */
    public void seleccionarAlimentoAleatorio() {
        seleccionarAlimentoAleatorio(1);
    }

    private void seleccionarAlimentoAleatorio(int reintentosRestantes) {
        sleep(2000);

        // Esperar hasta 10 s a que aparezca la barra de categorías,
        // distinguiendo dos escenarios de fallo distintos.
        long limite = System.currentTimeMillis() + 10_000;
        int ciclo = 0;
        while (System.currentTimeMillis() < limite) {
            ciclo++;
            // Escenario 1: el cine no tiene alimentos activos → SKIP
            if (isVisibleQuick(MENU_NO_DISPONIBLE)) {
                takeScreenshot("Menú de alimentos no disponible");
                Allure.step("⚠️ El menú de alimentos no está disponible en el cine seleccionado");
                org.junit.jupiter.api.Assumptions.abort(
                    "El menú de alimentos no está disponible en este cine");
                return;
            }
            By barraBy = resolverBarraCategorias();
            if (isVisibleQuick(barraBy)) break;
            sleep(500);
        }

        // Escenario 2: el menú no cargó (error de servicio o intermitencia) → FAIL
        By barraFinal = resolverBarraCategorias();
        if (!isVisibleQuick(barraFinal)) {
            takeScreenshot("Error – menú de alimentos no cargó");
            Allure.step("❌ El menú de alimentos no cargó tras 10 s – posible error de servicio o intermitencia");
            org.junit.jupiter.api.Assertions.fail(
                "El menú de alimentos no cargó – posible error de servicio o intermitencia");
            return;
        }

        log.info("[Alimentos] Barra de categorías detectada, iniciando recolección de categorías para seleccionar una al azar");
        seleccionarTabAleatorio();
        seleccionarProductoEnSeccionActual();
        boolean exito = completarPersonalizacion();
        if (!exito) {
            if (reintentosRestantes > 0) {
                Allure.step("🔄 Reintentando selección de alimento...");
                seleccionarAlimentoAleatorio(reintentosRestantes - 1);
            } else {
                org.junit.jupiter.api.Assertions.fail("No se pudo agregar alimento al carrito tras 2 intentos");
            }
        }
    }

    /**
     * Recorre el carrusel horizontal de la sección de categoría indicada,
     * recopila todos los productos disponibles y hace click en uno al azar.
     * Usa el título de sección como ancla para determinar la Y del carrusel.
     */
    public void seleccionarProductoEnSeccion(String nombreCategoria) {
        By tituloLocator = By.xpath(
            "//android.widget.TextView[@text='" + nombreCategoria + "']");
        waitForVisibility(tituloLocator);

        WebElement titulo  = driver.findElement(tituloLocator);
        int altTitulo      = titulo.getSize().getHeight();
        int tituloBottomY  = titulo.getLocation().getY() + altTitulo;
        int carouselY      = tituloBottomY + altTitulo * 2; // centro aprox. del carrusel

        // Resetear al inicio del carrusel: swipe right hasta que el contenido
        // visible deje de cambiar, lo que indica que llegamos al primer producto.
        String hashAntes = "";
        for (int i = 0; i < 15; i++) {
            fastSwipeRightAtY(carouselY);
            sleep(400);
            StringBuilder sb = new StringBuilder();
            try {
                for (WebElement card : safeFindElements(CARDS_PRODUCTOS_DISPONIBLES)) {
                    try {
                        int cardCenterY = card.getLocation().getY() + card.getSize().getHeight() / 2;
                        if (Math.abs(cardCenterY - carouselY) > altTitulo * 3) continue;
                        List<WebElement> inner = card.findElements(
                            By.xpath(".//android.view.View[@content-desc!='']"));
                        for (WebElement v : inner) {
                            String d = v.getAttribute("content-desc");
                            if (d != null && !d.isBlank() && !d.equals("null")) { sb.append(d); break; }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            String hashDespues = sb.toString();
            if (!hashDespues.isEmpty() && hashDespues.equals(hashAntes)) break;
            hashAntes = hashDespues;
        }
        sleep(300); // estabilizar antes de empezar a leer

        // Recopilar nombres únicos de productos disponibles recorriendo el carrusel.
        // sinCambios >= 3 y sleep(600): mismos ajustes que seleccionarTabAleatorio para
        // tolerar el lazy-loading de Compose sin romper la recolección antes de llegar al final.
        List<String> nombres = new ArrayList<>();
        int sinCambios = 0;
        for (int intento = 0; intento < 20; intento++) {
            List<WebElement> cards = safeFindElements(CARDS_PRODUCTOS_DISPONIBLES);
            int antes = nombres.size();
            for (WebElement card : cards) {
                try {
                    int cardCenterY = card.getLocation().getY() + card.getSize().getHeight() / 2;
                    if (Math.abs(cardCenterY - carouselY) > altTitulo * 3) continue;
                    // El nombre del producto está en el content-desc del View interno de
                    // la card (imagen del producto). Los badges NO tienen content-desc en
                    // su View contenedor, por lo que no interfieren.
                    String nombre = null;
                    List<WebElement> innerViews = card.findElements(
                        By.xpath(".//android.view.View[@content-desc!='']"));
                    for (WebElement inner : innerViews) {
                        String desc = inner.getAttribute("content-desc");
                        if (desc != null && !desc.isBlank() && !desc.equals("null")) {
                            nombre = desc;
                            break;
                        }
                    }
                    if (nombre == null || nombres.contains(nombre)) continue;
                    nombres.add(nombre);
                } catch (Exception ignored) {}
            }
            if (nombres.size() == antes) { if (++sinCambios >= 3) break; } else sinCambios = 0;
            fastSwipeLeftAtY(carouselY);
            sleep(600); // 900 ms totales por swipe — suficiente para lazy-loading de Compose
        }

        org.junit.jupiter.api.Assertions.assertFalse(nombres.isEmpty(),
            "No hay productos disponibles en la categoría: " + nombreCategoria);

        String elegido = nombres.get(new Random().nextInt(nombres.size()));
        Allure.step("🎲 Producto seleccionado al azar: " + elegido);

        // El carrusel ya está al final tras la recolección; buscamos de regreso (derecha)
        boolean encontrado = clickProductoEnCarrusel(elegido, carouselY, altTitulo);
        org.junit.jupiter.api.Assertions.assertTrue(encontrado,
            "No se pudo localizar el producto en el carrusel: " + elegido);
    }

    /**
     * Busca un producto en el carrusel horizontal por su content-desc y lo toca.
     * Usa getPageSource() + comparación Java (sin XPath) para evitar problemas con
     * caracteres especiales (®, ñ, tildes) que no se escapan correctamente en XPath 1.0.
     * La tolerancia Y es al menos 150 px para cubrir la variación entre el centro
     * del carrusel y el elemento imagen (parte superior de la card).
     */
    private boolean clickProductoEnCarrusel(String nombre, int carouselY, int altTitulo) {
        final int toleranciaY = Math.max(altTitulo * 4, 150);
        final int screenW = driver.manage().window().getSize().getWidth();
        final int screenH = driver.manage().window().getSize().getHeight();
        final java.util.regex.Pattern BOUNDS = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        for (int intento = 0; intento < 15; intento++) {
            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                org.w3c.dom.NodeList views = doc.getElementsByTagName("android.view.View");
                for (int i = 0; i < views.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) views.item(i);
                    if (!nombre.equals(el.getAttribute("content-desc"))) continue;

                    String bounds = el.getAttribute("bounds");
                    if (bounds == null) continue;
                    java.util.regex.Matcher m = BOUNDS.matcher(bounds);
                    if (!m.matches()) continue;

                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    if (Math.abs(y - carouselY) <= toleranciaY && x > 0 && x < screenW) {
                        sleep(300); // dejar que el carrusel termine de asentarse antes del tap
                        tapW3C(x, y);
                        sleep(500);
                        return true;
                    }
                }
            } catch (Exception ignored) {}

            fastSwipeRightAtY(carouselY);
            sleep(600);
        }
        return false;
    }

    /**
     * Avanza por todas las pantallas de personalización de un producto
     * eligiendo siempre la primera opción disponible hasta llegar a
     * "Agregar al carrito". Funciona para productos simples, complejos y combos.
     */
    public boolean completarPersonalizacion() {
        final By BTN_AGREGAR_CARRITO = By.xpath(
            "//android.widget.TextView[@text='Agregar al carrito']/../android.widget.Button");
        final By BTN_PERSONALIZAR = By.xpath(
            "//android.widget.TextView[@text='Personalizar']/../android.widget.Button");
        final By BTN_CONTINUAR = By.xpath(
            "//android.widget.TextView[@text='Continuar']/../android.widget.Button");
        final By BTN_SIGUIENTE = By.xpath(
            "//android.widget.TextView[@text='Siguiente']/../android.widget.Button");
        // 30 pasos: margen para productos con muchas pantallas o transiciones lentas
        final int MAX_PASOS = 30;

        for (int paso = 0; paso < MAX_PASOS; paso++) {
            sleep(1000); // esperar a que la pantalla termine de renderizarse
            if (gestionarErrorSiPresente()) return false;

            // Estado 1: pantalla de confirmación final — "Agregar al carrito"
            if (isVisibleQuick(TEXTO_AGREGAR_CARRITO)) {
                click(BTN_AGREGAR_CARRITO);
                boolean errorManejado = manejarErrorCarritoSiPresente();
                if (!errorManejado) Allure.step("✅ Producto agregado al carrito");
                return !errorManejado;
            }

            // Estado 2: pantalla de detalle inicial — solo vista previa, sin opciones
            if (isVisibleQuick(BTN_PERSONALIZAR)) {
                click(BTN_PERSONALIZAR);
                sleep(800);
                continue;
            }

            // Estado 3: pantalla de personalización con botón "Continuar"
            if (isVisibleQuick(BTN_CONTINUAR)) {
                seleccionarOpcionesAlAzar();
                // Re-verificar que el botón sigue visible tras el scroll de selección
                if (isVisibleQuick(BTN_CONTINUAR)) {
                    click(BTN_CONTINUAR);
                    sleep(1200); // dar tiempo a la transición entre pantallas
                }
                continue;
            }

            // Estado 4: pantalla de personalización de combo con botón "Siguiente"
            if (isVisibleQuick(BTN_SIGUIENTE)) {
                seleccionarOpcionesAlAzar();
                if (isVisibleQuick(BTN_SIGUIENTE)) {
                    click(BTN_SIGUIENTE);
                    sleep(1200);
                }
                continue;
            }
        }
        org.junit.jupiter.api.Assertions.fail(
            "❌ No se llegó a 'Agregar al carrito' tras " + MAX_PASOS + " pasos");
        return false;
    }

    private boolean gestionarErrorSiPresente() {
        List<WebElement> errores;
        try { errores = driver.findElements(ALERTA_ERROR_CARRITO); }
        catch (Exception e) { return false; }
        if (errores.isEmpty()) return false;

        String mensaje = "—";
        try { mensaje = errores.get(0).getText(); } catch (Exception ignored) {}

        String nombreProducto = categoriaActual != null ? categoriaActual : "producto";
        String detalleError   = "[" + nombreProducto + "] " + mensaje;

        // Acumular para el resumen final en clikIrAPagar()
        ERRORES_PRODUCTOS_ACUMULADOS.get().add(detalleError);

        // Step con status BROKEN: aparece en naranja/rojo en Allure sin fallar el test
        String stepUuid = java.util.UUID.randomUUID().toString();
        Allure.getLifecycle().startStep(stepUuid,
            new io.qameta.allure.model.StepResult()
                .setName("❌ No se pudo agregar al carrito: " + detalleError)
                .setStatus(io.qameta.allure.model.Status.BROKEN));
        Allure.getLifecycle().stopStep(stepUuid);

        try {
            byte[] shot = driver.getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            Allure.addAttachment(
                "Error – " + nombreProducto,
                "image/png", new java.io.ByteArrayInputStream(shot), "png");
        } catch (Exception ignored) {}
        clickIfPresent(BOTON_ACEPTAR_ALERTA);
        sleep(600);
        click(BOTON_IR_ATRAS_ALIMENTOS);
        sleep(800);
        clickIfPresent(BOTON_CONFIRMAR_SALIR_ALIMENTOS);
        sleep(600);
        return true;
    }

    private boolean manejarErrorCarritoSiPresente() {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            if (gestionarErrorSiPresente()) return true;
            sleep(500);
        }
        return false;
    }

    /**
     * Devuelve el locator más estable disponible para detectar la barra de categorías.
     * El orden refleja qué tab aparece primero según el país/cadena de cines.
     */
    private By resolverBarraCategorias() {
        // Estrategia 1: "Club Cinépolis" — primer tab en la mayoría de mercados
        By byClub = By.xpath("//android.widget.TextView[@text='Club Cinépolis']");
        try {
            if (!driver.findElements(byClub).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 1: 'Club Cinépolis' encontrado");
                return byClub;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 1: 'Club Cinépolis' NO encontrado"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 1 excepción: " + e.getMessage()); }

        // Estrategia 2: "Destacados"
        By byDestacados = By.xpath("//android.widget.TextView[@text='Destacados']");
        try {
            if (!driver.findElements(byDestacados).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 2: 'Destacados' encontrado");
                return byDestacados;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 2: 'Destacados' NO encontrado"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 2 excepción: " + e.getMessage()); }

        // Estrategia 3: "Promociones"
        try {
            if (!driver.findElements(BARRA_CATEGORIAS_ALIMENTOS_PROMO).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 3: 'Promociones' encontrado");
                return BARRA_CATEGORIAS_ALIMENTOS_PROMO;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 3: 'Promociones' NO encontrado"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 3 excepción: " + e.getMessage()); }

        // Estrategia 4: "Coleccionables"
        By byColeccionables = By.xpath("//android.widget.TextView[@text='Coleccionables']");
        try {
            if (!driver.findElements(byColeccionables).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 4: 'Coleccionables' encontrado");
                return byColeccionables;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 4: 'Coleccionables' NO encontrado"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 4 excepción: " + e.getMessage()); }

        // Estrategia 5: "Mis promociones Movie Yelmo"
        By byYelmo = By.xpath("//android.widget.TextView[@text='Mis promociones Movie Yelmo']");
        try {
            if (!driver.findElements(byYelmo).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 5: 'Mis promociones Movie Yelmo' encontrado");
                return byYelmo;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 5: 'Mis promociones Movie Yelmo' NO encontrado"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 5 excepción: " + e.getMessage()); }

        // Estrategia 6: otros tabs de categoría genéricos
        for (String tab : new String[]{"Snacks", "Combos", "Bebidas", "Palomitas", "Postres"}) {
            By by = By.xpath("//android.widget.TextView[@text='" + tab + "']");
            try {
                if (!driver.findElements(by).isEmpty()) {
                    System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 6: tab '" + tab + "' encontrado");
                    return by;
                }
            } catch (Exception ignored) {}
        }
        System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 6: ningún tab genérico encontrado");

        // Último recurso: localizador estructural original
        try {
            if (!driver.findElements(BARRA_CATEGORIAS_ALIMENTOS).isEmpty()) {
                System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 7: localizador estructural OK");
                return BARRA_CATEGORIAS_ALIMENTOS;
            } else { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 7: localizador estructural VACÍO"); }
        } catch (Exception e) { System.out.println("[DEBUG-ALIMENTOS] resolverBarra → estrategia 7 excepción: " + e.getMessage()); }

        System.out.println("[DEBUG-ALIMENTOS] resolverBarra → todas las estrategias fallaron, devolviendo PROMO como último recurso");
        return BARRA_CATEGORIAS_ALIMENTOS_PROMO;
    }

    /**
     * Obtiene la coordenada Y central de la barra de categorías de alimentos
     * delegando a encontrarTabBarY().
     */
    private int obtenerBarraCategoriasY() {
        return encontrarTabBarY();
    }

    /**
     * Calcula la Y central de la barra de tabs buscando el mínimo Y entre todas
     * las instancias de textos de tab conocidos.
     *
     * Compose renderiza el contenido (ej. sección "Destacados") antes que la barra
     * de navegación de tabs en el árbol de accesibilidad, por lo que driver.findElement
     * devuelve el título de sección (Y alto) en lugar del tab real (Y bajo).
     * Tomando el mínimo Y siempre se obtiene el tab, no el heading de sección.
     */
    private int encontrarTabBarY() {
        int minY = Integer.MAX_VALUE;
        String[] tabTexts = {"Club Cinépolis", "Destacados", "Coleccionables",
                             "Mis promociones Movie Yelmo", "Snacks", "Combos",
                             "Bebidas", "Palomitas", "Postres", "Promociones"};
        for (String text : tabTexts) {
            try {
                for (WebElement el : driver.findElements(
                        By.xpath("//android.widget.TextView[@text='" + text + "']"))) {
                    int y = el.getLocation().getY();
                    if (y > 0 && y < minY) minY = y;
                }
            } catch (Exception ignored) {}
        }
        if (minY < Integer.MAX_VALUE) return minY + 30; // +30 px ≈ mitad de la altura de un tab
        return (int) (driver.manage().window().getSize().getHeight() * 0.20);
    }

    private void seleccionarOpcionesAlAzar() {
        final java.util.regex.Pattern BOUNDS_P = java.util.regex.Pattern
            .compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
        final java.util.Set<String> NAV_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Ir atrás", "Atrás", "Back", "Regresar", "Volver", "Cerrar", "Close", "Salir"));
        final java.util.Set<String> TEXTO_EXCLUIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "Continuar", "Siguiente", "Agregar al carrito", "Personalizar",
            "Requerido", "Opcional", "Eliminar", "Editar", "Ir atrás", "Atrás"));

        int screenH = driver.manage().window().getSize().getHeight();
        int screenW = driver.manage().window().getSize().getWidth();
        Random rnd  = new Random();
        java.util.Set<String> seccionesProcesadas = new java.util.HashSet<>();

        // Resetear al inicio de la pantalla
        slowSwipeDown(); sleep(200);
        slowSwipeDown(); sleep(200);

        int scrollsVacios = 0;

        for (int scroll = 0; scroll < 12 && scrollsVacios < 3; scroll++) {
            // [y, cx, type(0=título|1=opción), texto/desc]
            List<Object[]> elementos = new ArrayList<>();

            try {
                javax.xml.parsers.DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(
                    new java.io.ByteArrayInputStream(
                        driver.getPageSource().getBytes("UTF-8")));

                // Paso 1: recopilar todos los content-descs de opciones para distinguirlos de títulos
                java.util.Set<String> descsOpciones = new java.util.HashSet<>();
                org.w3c.dom.NodeList allViews = doc.getElementsByTagName("android.view.View");
                for (int i = 0; i < allViews.getLength(); i++) {
                    String desc = ((org.w3c.dom.Element) allViews.item(i)).getAttribute("content-desc");
                    if (desc != null && !desc.isBlank() && !desc.equals("null"))
                        descsOpciones.add(desc);
                }

                // Paso 2: TextViews que NO son etiquetas de opciones → candidatos a título de sección
                // No depende de estructura de hermanos en el DOM, por lo que funciona
                // aunque la sección sea la primera hija de su contenedor.
                org.w3c.dom.NodeList tvs = doc.getElementsByTagName("android.widget.TextView");
                for (int i = 0; i < tvs.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) tvs.item(i);
                    String text   = el.getAttribute("text");
                    String bounds = el.getAttribute("bounds");
                    if (text == null || text.isBlank()) continue;
                    if (descsOpciones.contains(text)) continue;  // etiqueta de opción, no título
                    if (TEXTO_EXCLUIR.contains(text)) continue;
                    if (text.startsWith("$") || text.matches("\\d.*")) continue;
                    if (bounds == null) continue;
                    java.util.regex.Matcher m = BOUNDS_P.matcher(bounds);
                    if (!m.matches()) continue;
                    int y  = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    int cx = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    if (y < 0 || y > screenH) continue;
                    elementos.add(new Object[]{ y, cx, 0, text });
                }

                // Paso 3: Views con content-desc = opciones de personalización
                for (int i = 0; i < allViews.getLength(); i++) {
                    org.w3c.dom.Element el = (org.w3c.dom.Element) allViews.item(i);
                    String desc   = el.getAttribute("content-desc");
                    String bounds = el.getAttribute("bounds");
                    if (desc == null || desc.isBlank() || desc.equals("null")) continue;
                    if (NAV_EXCLUIR.contains(desc)) continue;
                    if (bounds == null) continue;
                    java.util.regex.Matcher m = BOUNDS_P.matcher(bounds);
                    if (!m.matches()) continue;
                    int y  = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    int cx = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    if (y < 0 || y > screenH) continue;
                    if (cx <= screenW * 0.12) continue;
                    elementos.add(new Object[]{ y, cx, 1, desc });
                }
            } catch (Exception ignored) {}

            // Ordenar por Y; en empate, títulos (type=0) antes que opciones (type=1)
            elementos.sort((a, b) -> {
                int dy = (Integer) a[0] - (Integer) b[0];
                return dy != 0 ? dy : (Integer) a[2] - (Integer) b[2];
            });

            // Agrupar opciones bajo su título de sección (top→bottom)
            java.util.LinkedHashMap<String, List<Object[]>> secciones = new java.util.LinkedHashMap<>();
            String tituloActual = null;
            for (Object[] e : elementos) {
                if ((Integer) e[2] == 0) {
                    tituloActual = (String) e[3];
                    secciones.computeIfAbsent(tituloActual, k -> new ArrayList<>());
                } else if (tituloActual != null) {
                    secciones.get(tituloActual).add(e);
                }
            }

            // Tap a una opción al azar por cada sección no procesada en este viewport
            boolean tappeado = false;
            for (java.util.Map.Entry<String, List<Object[]>> entry : secciones.entrySet()) {
                String titulo = entry.getKey();
                if (seccionesProcesadas.contains(titulo)) continue;
                List<Object[]> opciones = entry.getValue();
                if (opciones.isEmpty()) continue;

                Object[] elegida = opciones.get(rnd.nextInt(opciones.size()));
                try {
                    tapW3C((Integer) elegida[1], (Integer) elegida[0]);
                    seccionesProcesadas.add(titulo);
                    sleep(300);
                    Allure.step("🎲 [" + titulo + "] → " + elegida[3]);
                    tappeado = true;
                } catch (Exception ignored) {}
            }

            if (!tappeado) scrollsVacios++; else scrollsVacios = 0;

            slowSwipeUp(); sleep(300);
        }
    }





}
