package qa.cinepolis.runner.accessibility;

/**
 * Element Resolver — enriches a raw {@link UIElement} with a smart variable name
 * and a ready-to-paste Page Object annotation block.
 *
 * This is a platform-agnostic, stateless layer between the AccessibilityInspector
 * and the Step Builder.  It knows nothing about ADB or WDA; it only reads the
 * fields already present on UIElement and derives human-readable identifiers.
 *
 * Architecture:
 *   AccessibilityInspector (Android | iOS)
 *       ↓ UIElement (raw)
 *   ElementResolver.enrich()
 *       ↓ UIElement (+ varName + pageObjectAnnotation)
 *   RecordingEngine.appendElement() → JSON → frontend
 *
 * Variable-name priority:
 *   1. resourceId last segment  ("com.pkg:id/btn_continuar" → "btn_continuar")
 *   2. accessId                 (iOS name / Android content-desc)
 *   3. text                     (truncated to 28 chars)
 *   4. accessibilityLabel
 *   5. className suffix         ("android.widget.Button" → "Button")
 *
 * Prefix is derived from className:
 *   Button / ImageButton                → btn
 *   EditText / TextField / SecureTextField → txt
 *   TextView / StaticText               → lbl
 *   ImageView / Image                   → img
 *   RecyclerView / Table / Collection   → rv / lst
 *   Switch                              → sw
 *   CheckBox                            → chk
 *   Spinner                             → spn
 *   Cell                                → cell
 *   default                             → el
 */
public final class ElementResolver {

    private ElementResolver() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a copy of {@code el} enriched with {@code varName} and
     * {@code pageObjectAnnotation}.  The original UIElement is not mutated.
     * Returns null when el is null.
     */
    public static UIElement enrich(UIElement el) {
        if (el == null) return null;

        String varName   = generateVarName(el);
        String annotation = generatePageObjectAnnotation(el, varName);

        debugLog(el, varName, annotation);

        // Rebuild using existing Builder — all other fields are copied
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
                .varName(varName)
                .pageObjectAnnotation(annotation)
                .shortId(el.shortId)
                .accessId(el.accessId)
                .elType(el.elType)
                .build();
    }

    // ── Variable name ─────────────────────────────────────────────────────────

    static String generateVarName(UIElement el) {
        String prefix = inferPrefix(el.className, el.elType);

        // Choose the most descriptive source string
        String source;
        if (!isBlank(el.resourceId)) {
            source = lastSegment(el.resourceId);         // "com.pkg:id/btn_continuar" → "btn_continuar"
        } else if (!isBlank(el.accessId)) {
            source = el.accessId;                        // iOS name / Android content-desc
        } else if (!isBlank(el.text)) {
            String t = el.text.trim();
            source = t.length() > 28 ? t.substring(0, 28) : t;
        } else if (!isBlank(el.accessibilityLabel)) {
            String a = el.accessibilityLabel.trim();
            source = a.length() > 28 ? a.substring(0, 28) : a;
        } else {
            // className suffix without package or XCUIElementType prefix
            String cls = el.className;
            int dot = cls.lastIndexOf('.');
            String suffix = dot >= 0 ? cls.substring(dot + 1) : cls;
            source = suffix.replace("XCUIElementType", "")
                           .replace("android.widget.", "");
        }

        String camel = toCamelCase(source);
        if (camel.isEmpty()) camel = "element";

        // Avoid double-prefix ("btnBtnContinuar" → "btnContinuar")
        if (camel.toLowerCase().startsWith(prefix.toLowerCase())) {
            return camel.isEmpty() ? prefix : lc1(camel);
        }
        return prefix + uc1(camel);
    }

    // ── Page Object annotation ─────────────────────────────────────────────────

    static String generatePageObjectAnnotation(UIElement el, String varName) {
        if (isBlank(el.locatorValue)) return "";

        String ann;
        if ("android".equalsIgnoreCase(el.platform)) {
            ann = androidAnnotation(el);
        } else if ("ios".equalsIgnoreCase(el.platform)) {
            ann = iosAnnotation(el);
        } else {
            return "";
        }

        return ann + "\nprivate WebElement " + varName + ";";
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static String androidAnnotation(UIElement el) {
        String v = escape(el.locatorValue);
        switch (el.locatorStrategy) {
            case "id":               return "@AndroidFindBy(id = \"" + v + "\")";
            case "accessibility_id": return "@AndroidFindBy(accessibility = \"" + v + "\")";
            case "text":             return "@AndroidFindBy(uiAutomator = \"new UiSelector().text(\\\"" + v + "\\\")\")";
            case "xpath":            return "@AndroidFindBy(xpath = \"" + v + "\")";
            default:                 return "@AndroidFindBy(xpath = \"" + v + "\")";
        }
    }

    private static String iosAnnotation(UIElement el) {
        String v = escape(el.locatorValue);
        switch (el.locatorStrategy) {
            case "accessibility_id": return "@iOSXCUITFindBy(accessibility = \"" + v + "\")";
            case "predicate_string": return "@iOSXCUITFindBy(iOSNsPredicate = \"" + v + "\")";
            case "class_chain":      return "@iOSXCUITFindBy(iOSClassChain = \"" + v + "\")";
            case "xpath":            return "@iOSXCUITFindBy(xpath = \"" + v + "\")";
            default:                 return "@iOSXCUITFindBy(xpath = \"" + v + "\")";
        }
    }

    /** Infers the camelCase prefix from the element's class name and elType. */
    static String inferPrefix(String className, String elType) {
        if (className != null) {
            String c = className.toLowerCase();
            if (c.contains("imagebutton"))       return "btn";
            if (c.contains("button"))            return "btn";
            if (c.contains("edittext"))          return "txt";
            if (c.contains("textfield"))         return "txt";
            if (c.contains("securetextfield"))   return "txt";
            if (c.contains("textarea"))          return "txt";
            if (c.contains("textview"))          return "lbl";
            if (c.contains("statictext"))        return "lbl";
            if (c.contains("imageview"))         return "img";
            if (c.contains("image"))             return "img";
            if (c.contains("recyclerview"))      return "rv";
            if (c.contains("collectionview"))    return "rv";
            if (c.contains("tableview"))         return "lst";
            if (c.contains("listview"))          return "lst";
            if (c.contains("scrollview"))        return "lst";
            if (c.contains("switch"))            return "sw";
            if (c.contains("checkbox"))          return "chk";
            if (c.contains("spinner"))           return "spn";
            if (c.contains("cell"))              return "cell";
        }
        // Fallback: use elType
        if (elType != null) {
            switch (elType) {
                case "btn":   return "btn";
                case "input": return "txt";
                case "text":  return "lbl";
                case "image": return "img";
                case "list":  return "lst";
            }
        }
        return "el";
    }

    /** Returns the last path segment of a resource-id string. */
    private static String lastSegment(String resourceId) {
        int slash = resourceId.lastIndexOf('/');
        return slash >= 0 ? resourceId.substring(slash + 1) : resourceId;
    }

    /**
     * Converts an arbitrary string to camelCase.
     * Splits on _, -, space, and non-alphanumeric characters.
     * Trims non-identifier characters.
     */
    static String toCamelCase(String s) {
        if (s == null || s.isBlank()) return "";
        // Normalize: replace non-alphanumeric (except _) with _
        String norm = s.trim().replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
        String[] parts = norm.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (sb.length() == 0) {
                sb.append(part.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        // Strip leading digits
        int start = 0;
        while (start < sb.length() && Character.isDigit(sb.charAt(start))) start++;
        return sb.substring(start);
    }

    private static String lc1(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String uc1(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Debug logging ──────────────────────────────────────────────────────────

    private static void debugLog(UIElement el, String varName, String annotation) {
        System.out.printf(
            "[ElementResolver] platform=%-7s  class=%-40s%n" +
            "  locator=%-20s  value=%s%n" +
            "  varName=%s%n",
            el.platform, el.className,
            el.locatorStrategy, el.locatorValue,
            varName
        );
        if (isBlank(el.locatorValue)) {
            System.out.println("[ElementResolver] WARN: locatorValue is empty — no annotation generated");
        }
    }
}
