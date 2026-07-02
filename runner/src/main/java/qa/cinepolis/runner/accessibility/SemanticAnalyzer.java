package qa.cinepolis.runner.accessibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Semantic Analyzer — translates English variable names to Spanish and improves
 * Page Object annotation quality.
 *
 * Pipeline position (called from ElementResolver.enrich):
 *   AccessibilityInspector → UIElement (raw)
 *   ElementResolver         → UIElement (+ English varName + annotation)
 *   SemanticAnalyzer        → UIElement (+ Spanish semanticName + improved annotation)
 *   RecordingEngine         → JSON step → frontend
 *
 * Name translation examples:
 *   btnAdd      → btnAgregar
 *   txtEmail    → txtCorreo
 *   btnContinue → btnContinuar
 *   rvMovies    → rvPeliculas
 *   txtPassword → txtContrasena
 *
 * Locator priority (never falls back to XPath when a stable locator exists):
 *   Android: resource-id → accessibility-id → text/UiSelector → xpath (last resort)
 *   iOS:     accessibility-id → predicate-string → class-chain → xpath (last resort)
 */
public final class SemanticAnalyzer {

    private SemanticAnalyzer() {}

    // ── English → Spanish dictionary ──────────────────────────────────────────
    // Values are PascalCase so they can be directly concatenated after a prefix.

    private static final Map<String, String> EN_ES = new LinkedHashMap<>();
    static {
        // ── Actions / verbs ──
        EN_ES.put("login",       "IniciarSesion");
        EN_ES.put("logout",      "CerrarSesion");
        EN_ES.put("register",    "Registrar");
        EN_ES.put("signin",      "IniciarSesion");
        EN_ES.put("signup",      "Registrarse");
        EN_ES.put("add",         "Agregar");
        EN_ES.put("remove",      "Eliminar");
        EN_ES.put("delete",      "Eliminar");
        EN_ES.put("search",      "Buscar");
        EN_ES.put("continue",    "Continuar");
        EN_ES.put("cancel",      "Cancelar");
        EN_ES.put("confirm",     "Confirmar");
        EN_ES.put("save",        "Guardar");
        EN_ES.put("submit",      "Enviar");
        EN_ES.put("back",        "Regresar");
        EN_ES.put("next",        "Siguiente");
        EN_ES.put("prev",        "Anterior");
        EN_ES.put("previous",    "Anterior");
        EN_ES.put("select",      "Seleccionar");
        EN_ES.put("accept",      "Aceptar");
        EN_ES.put("close",       "Cerrar");
        EN_ES.put("open",        "Abrir");
        EN_ES.put("apply",       "Aplicar");
        EN_ES.put("pay",         "Pagar");
        EN_ES.put("checkout",    "Pagar");
        EN_ES.put("share",       "Compartir");
        EN_ES.put("skip",        "Omitir");
        EN_ES.put("finish",      "Finalizar");
        EN_ES.put("start",       "Iniciar");
        EN_ES.put("edit",        "Editar");
        EN_ES.put("update",      "Actualizar");
        EN_ES.put("refresh",     "Actualizar");
        EN_ES.put("retry",       "Reintentar");
        EN_ES.put("send",        "Enviar");
        EN_ES.put("filter",      "Filtrar");
        EN_ES.put("sort",        "Ordenar");
        EN_ES.put("buy",         "Comprar");
        EN_ES.put("purchase",    "Comprar");
        EN_ES.put("change",      "Cambiar");
        EN_ES.put("swap",        "Intercambiar");
        EN_ES.put("go",          "Ir");
        EN_ES.put("show",        "Mostrar");
        EN_ES.put("hide",        "Ocultar");
        EN_ES.put("expand",      "Expandir");
        EN_ES.put("collapse",    "Colapsar");
        EN_ES.put("toggle",      "Alternar");
        EN_ES.put("reset",       "Restablecer");

        // ── Form fields ──
        EN_ES.put("email",       "Correo");
        EN_ES.put("password",    "Contrasena");
        EN_ES.put("pwd",         "Contrasena");
        EN_ES.put("pass",        "Contrasena");
        EN_ES.put("username",    "Usuario");
        EN_ES.put("user",        "Usuario");
        EN_ES.put("phone",       "Telefono");
        EN_ES.put("mobile",      "Celular");
        EN_ES.put("name",        "Nombre");
        EN_ES.put("lastname",    "Apellido");
        EN_ES.put("address",     "Direccion");
        EN_ES.put("city",        "Ciudad");
        EN_ES.put("country",     "Pais");
        EN_ES.put("date",        "Fecha");
        EN_ES.put("time",        "Hora");
        EN_ES.put("price",       "Precio");
        EN_ES.put("total",       "Total");
        EN_ES.put("code",        "Codigo");
        EN_ES.put("folio",       "Folio");
        EN_ES.put("points",      "Puntos");
        EN_ES.put("balance",     "Saldo");
        EN_ES.put("number",      "Numero");
        EN_ES.put("num",         "Numero");
        EN_ES.put("amount",      "Monto");
        EN_ES.put("quantity",    "Cantidad");

        // ── Cinépolis domain ──
        EN_ES.put("movie",       "Pelicula");
        EN_ES.put("movies",      "Peliculas");
        EN_ES.put("film",        "Pelicula");
        EN_ES.put("seat",        "Asiento");
        EN_ES.put("seats",       "Asientos");
        EN_ES.put("ticket",      "Boleto");
        EN_ES.put("tickets",     "Boletos");
        EN_ES.put("card",        "Tarjeta");
        EN_ES.put("menu",        "Menu");
        EN_ES.put("home",        "Inicio");
        EN_ES.put("profile",     "Perfil");
        EN_ES.put("settings",    "Configuracion");
        EN_ES.put("food",        "Alimentos");
        EN_ES.put("snack",       "Antojito");
        EN_ES.put("combo",       "Combo");
        EN_ES.put("drink",       "Bebida");
        EN_ES.put("poster",      "Poster");
        EN_ES.put("cinema",      "Cine");
        EN_ES.put("theater",     "Sala");
        EN_ES.put("hall",        "Sala");
        EN_ES.put("room",        "Sala");
        EN_ES.put("session",     "Sesion");
        EN_ES.put("list",        "Lista");
        EN_ES.put("detail",      "Detalle");
        EN_ES.put("info",        "Informacion");
        EN_ES.put("notification","Notificacion");
        EN_ES.put("title",       "Titulo");
        EN_ES.put("description", "Descripcion");
        EN_ES.put("category",    "Categoria");
        EN_ES.put("rating",      "Calificacion");
        EN_ES.put("showtime",    "Funcion");
        EN_ES.put("showtimes",   "Funciones");
        EN_ES.put("format",      "Formato");
        EN_ES.put("offer",       "Oferta");
        EN_ES.put("offers",      "Ofertas");
        EN_ES.put("promo",       "Promocion");
        EN_ES.put("promotion",   "Promocion");
        EN_ES.put("discount",    "Descuento");
        EN_ES.put("coupon",      "Cupon");
        EN_ES.put("reward",      "Recompensa");
        EN_ES.put("account",     "Cuenta");
        EN_ES.put("wallet",      "Monedero");
        EN_ES.put("favorite",    "Favorito");
        EN_ES.put("favorites",   "Favoritos");
        EN_ES.put("order",       "Pedido");
        EN_ES.put("booking",     "Reservacion");
        EN_ES.put("reservation", "Reservacion");
        EN_ES.put("schedule",    "Horario");
        EN_ES.put("row",         "Fila");
        EN_ES.put("location",    "Ubicacion");
        EN_ES.put("map",         "Mapa");
        EN_ES.put("image",       "Imagen");
        EN_ES.put("photo",       "Foto");
        EN_ES.put("gallery",     "Galeria");
        EN_ES.put("terms",       "Terminos");
        EN_ES.put("privacy",     "Privacidad");
        EN_ES.put("help",        "Ayuda");
        EN_ES.put("support",     "Soporte");
        EN_ES.put("contact",     "Contacto");
        EN_ES.put("member",      "Socio");
        EN_ES.put("membership",  "Membresia");
        EN_ES.put("loyalty",     "Lealtad");
        EN_ES.put("genre",       "Genero");
        EN_ES.put("trailer",     "Trailer");
        EN_ES.put("synopsis",    "Sinopsis");
        EN_ES.put("billboard",   "Cartelera");
        EN_ES.put("store",       "Tienda");
        EN_ES.put("billing",     "Facturacion");
        EN_ES.put("invoice",     "Factura");
        EN_ES.put("language",    "Idioma");
        EN_ES.put("version",     "Version");
        EN_ES.put("club",        "Club");
    }

    // Names that produce noise — force fallback to content-based name or generic
    private static final Set<String> FORBIDDEN = Set.of(
        "view", "button", "text", "a", "b", "c", "d", "e",
        "btnview", "btnbutton", "txttext", "lblelement",
        "elelement", "el", "elemento",
        "btnelemento", "txtelemento", "lblelemento", "imgelemento",
        "btnvista",    "txtvista",    "lblvista",
        "btnlayout",   "btnframe",    "btncontainer",
        "eldesconocido", "elunknown"
    );

    // Words to skip when building a name from visible content
    private static final Set<String> STOP_WORDS = Set.of(
        // Spanish
        "a", "al", "de", "del", "el", "la", "los", "las",
        "y", "o", "en", "con", "para", "por", "un", "una", "su", "mi",
        // English
        "the", "to", "of", "an", "and", "or", "in", "with",
        "for", "on", "at", "by", "is", "it", "this", "that"
    );

    private static final String[] PREFIXES = {
        "btn", "txt", "lbl", "img", "rv", "lst", "sw", "chk", "spn", "cell", "el"
    };

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a new UIElement with the English varName translated to Spanish
     * and with the pageObjectAnnotation regenerated using the best available
     * locator (resource-id > accessibility > text > xpath).
     */
    public static UIElement analyze(UIElement el) {
        if (el == null) return null;

        String semantic   = toSemantic(el.varName, el);
        String annotation = regenerateAnnotation(el, semantic);

        return UIElement.builder()
                .platform(el.platform)
                .className(el.className)
                .locatorStrategy(el.locatorStrategy)
                .locatorValue(el.locatorValue)
                .text(el.text)
                .accessibilityLabel(el.accessibilityLabel)
                .resourceId(el.resourceId)
                .packageName(el.packageName)
                .bundleId(el.bundleId)
                .rect(el.x, el.y, el.width, el.height)
                .enabled(el.enabled)
                .clickable(el.clickable)
                .visible(el.visible)
                .varName(semantic)
                .semanticName(semantic)
                .pageObjectAnnotation(annotation)
                .shortId(el.shortId)
                .accessId(el.accessId)
                .elType(el.elType)
                .build();
    }

    // ── Name translation ──────────────────────────────────────────────────────

    /**
     * Translates an English camelCase varName to a Spanish semantic name.
     * "btnContinue" → "btnContinuar",  "txtEmail" → "txtCorreo"
     */
    static String toSemantic(String varName, UIElement el) {
        if (blank(varName)) return "elDesconocido";

        String prefix = extractPrefix(varName);
        String stem   = varName.length() > prefix.length()
                        ? varName.substring(prefix.length()) : varName;
        if (stem.isEmpty()) return prefix.isEmpty() ? "elDesconocido" : prefix;

        String translated = translateCamelStem(stem);

        String result = prefix + Character.toUpperCase(translated.charAt(0))
                      + translated.substring(1);

        if (FORBIDDEN.contains(result.toLowerCase())) {
            result = prefix.isEmpty() ? "elElemento" : prefix + "Elemento";
        }

        // When no EN_ES dictionary word matched, the stem is untranslated — try to
        // derive a more descriptive name from the element's visible content instead.
        if (!anyWordTranslated(stem) || FORBIDDEN.contains(result.toLowerCase())) {
            String fromContent = nameFromContent(el, prefix);
            if (!fromContent.isEmpty()) {
                result = fromContent;
            }
        }

        if (!varName.equals(result)) {
            System.out.printf("[SemanticAnalyzer] %s → %s%n", varName, result);
        }
        return result;
    }

    /**
     * Splits camelCase stem and translates each word using the dictionary.
     * "ClubCard" → ["Club","Card"] → "Club"+"Tarjeta" → "ClubTarjeta"
     */
    static String translateCamelStem(String stem) {
        if (blank(stem)) return "Elemento";
        String[] words = stem.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String lower      = word.toLowerCase();
            String translated = EN_ES.get(lower);
            if (translated != null) {
                sb.append(translated);
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.length() > 1 ? word.substring(1) : "");
            }
        }
        return sb.length() > 0 ? sb.toString() : stem;
    }

    // ── Content-based naming ──────────────────────────────────────────────────

    /** Returns true if at least one camelCase word in {@code stem} has a dictionary entry. */
    private static boolean anyWordTranslated(String stem) {
        if (blank(stem)) return false;
        String[] words = stem.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        for (String w : words) {
            if (!w.isEmpty() && EN_ES.containsKey(w.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Builds a variable name by inspecting the element's visible text,
     * accessibilityLabel, and accessId — in that priority order.
     * Returns an empty string when no usable content is found.
     */
    private static String nameFromContent(UIElement el, String prefix) {
        String[] sources = { el.text, el.accessibilityLabel, el.accessId };
        for (String source : sources) {
            if (blank(source)) continue;
            String t = source.trim();
            // Skip: too short/long, resource-ID-like strings, pure numbers
            if (t.length() < 2 || t.length() > 60) continue;
            if (t.contains("/") || t.contains(":"))  continue;
            if (t.matches("\\d+"))                   continue;

            // Normalize accents and keep only alphanumeric + spaces
            String norm = t
                .replace("á","a").replace("é","e").replace("í","i")
                .replace("ó","o").replace("ú","u")
                .replace("Á","A").replace("É","E").replace("Í","I")
                .replace("Ó","O").replace("Ú","U")
                .replace("ñ","n").replace("Ñ","N")
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
            if (norm.isEmpty()) continue;

            StringBuilder sb = new StringBuilder();
            int wordCount = 0;
            for (String w : norm.split(" ")) {
                if (w.isEmpty() || STOP_WORDS.contains(w.toLowerCase())) continue;
                if (wordCount >= 4) break;
                String tr = EN_ES.get(w.toLowerCase());
                sb.append(tr != null ? tr
                        : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase());
                wordCount++;
            }
            if (sb.length() == 0) continue;

            String candidate = (prefix.isEmpty() ? "el" : prefix) + sb;
            if (!FORBIDDEN.contains(candidate.toLowerCase())) return candidate;
        }
        return "";
    }

    // ── Rename helper (used by RecordingEngine for deduplication) ─────────────

    /**
     * Returns a copy of {@code el} with varName/semanticName set to
     * {@code newName} and a freshly regenerated pageObjectAnnotation.
     */
    public static UIElement renameElement(UIElement el, String newName) {
        String ann = regenerateAnnotation(el, newName);
        return UIElement.builder()
                .platform(el.platform)
                .className(el.className)
                .locatorStrategy(el.locatorStrategy)
                .locatorValue(el.locatorValue)
                .text(el.text)
                .accessibilityLabel(el.accessibilityLabel)
                .resourceId(el.resourceId)
                .packageName(el.packageName)
                .bundleId(el.bundleId)
                .rect(el.x, el.y, el.width, el.height)
                .enabled(el.enabled)
                .clickable(el.clickable)
                .visible(el.visible)
                .varName(newName)
                .semanticName(newName)
                .pageObjectAnnotation(ann)
                .shortId(el.shortId)
                .accessId(el.accessId)
                .elType(el.elType)
                .build();
    }

    // ── Annotation improvement ─────────────────────────────────────────────────

    /**
     * Regenerates @FindBy annotation using the semantic varName and the best
     * available locator.  Never emits XPath when id/accessibility/text exists.
     */
    public static String regenerateAnnotation(UIElement el, String semanticVarName) {
        if (blank(semanticVarName)) return "";

        String ann;
        if ("android".equalsIgnoreCase(el.platform)) {
            ann = bestAndroidAnnotation(el);
        } else if ("ios".equalsIgnoreCase(el.platform)) {
            ann = bestIosAnnotation(el);
        } else {
            return "";
        }

        if (blank(ann)) return "";
        return ann + "\nprivate WebElement " + semanticVarName + ";";
    }

    /** Android priority: resource-id → accessibility-id → UiSelector(text) → xpath */
    private static String bestAndroidAnnotation(UIElement el) {
        if (!blank(el.resourceId)) {
            return "@AndroidFindBy(id = \"" + esc(el.resourceId) + "\")";
        }
        if (!blank(el.accessId)) {
            return "@AndroidFindBy(accessibility = \"" + esc(el.accessId) + "\")";
        }
        if (!blank(el.accessibilityLabel)) {
            return "@AndroidFindBy(accessibility = \"" + esc(el.accessibilityLabel) + "\")";
        }
        if (!blank(el.text)) {
            return "@AndroidFindBy(uiAutomator = \"new UiSelector().text(\\\""
                    + esc(el.text) + "\\\")\")";
        }
        if (!blank(el.locatorValue)) {
            switch (el.locatorStrategy) {
                case "id":               return "@AndroidFindBy(id = \"" + esc(el.locatorValue) + "\")";
                case "accessibility_id": return "@AndroidFindBy(accessibility = \"" + esc(el.locatorValue) + "\")";
                case "text":             return "@AndroidFindBy(uiAutomator = \"new UiSelector().text(\\\""
                                                 + esc(el.locatorValue) + "\\\")\")";
                default:                 return "@AndroidFindBy(xpath = \"" + esc(el.locatorValue) + "\")";
            }
        }
        return "";
    }

    /** iOS priority: accessibility-id → predicate-string → class-chain → xpath */
    private static String bestIosAnnotation(UIElement el) {
        if (!blank(el.accessId)) {
            return "@iOSXCUITFindBy(accessibility = \"" + esc(el.accessId) + "\")";
        }
        if (!blank(el.accessibilityLabel)) {
            return "@iOSXCUITFindBy(accessibility = \"" + esc(el.accessibilityLabel) + "\")";
        }
        if (!blank(el.locatorValue)) {
            switch (el.locatorStrategy) {
                case "accessibility_id": return "@iOSXCUITFindBy(accessibility = \"" + esc(el.locatorValue) + "\")";
                case "predicate_string": return "@iOSXCUITFindBy(iOSNsPredicate = \"" + esc(el.locatorValue) + "\")";
                case "class_chain":      return "@iOSXCUITFindBy(iOSClassChain = \"" + esc(el.locatorValue) + "\")";
                default:                 return "@iOSXCUITFindBy(xpath = \"" + esc(el.locatorValue) + "\")";
            }
        }
        return "";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String extractPrefix(String varName) {
        for (String pfx : PREFIXES) {
            if (varName.length() > pfx.length()
                    && varName.toLowerCase().startsWith(pfx)
                    && Character.isUpperCase(varName.charAt(pfx.length()))) {
                return pfx;
            }
        }
        return "";
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
