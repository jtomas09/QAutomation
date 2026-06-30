package qa.cinepolis.runner.accessibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * iOS implementation of {@link AccessibilityInspector}.
 *
 * Inspection  — calls WebDriverAgent (WDA) REST API:
 *                 POST /session          → create minimal inspection session
 *                 GET  /session/{id}/source → XCUITest hierarchy XML
 *                 DEL  /session/{id}     → cleanup on close()
 *
 * Actions     — W3C Actions API via WDA (pointer + pause sequences).
 *               This is the only way to inject touches into an iOS device
 *               without an active Appium test session; WDA itself is always
 *               running during recording because IosPreflightManager starts it.
 *
 * Hierarchy format — WDA returns XML like:
 *   <XCUIElementTypeButton type="XCUIElementTypeButton"
 *       name="Login" label="Login" value="" enabled="true" visible="true"
 *       accessible="true" x="50" y="100" width="290" height="44" index="0"/>
 * Some WDA builds use a rect JSON string instead of separate x/y/w/h attributes;
 * both formats are handled.
 *
 * Locator priority (iOS):
 *   accessibility-id (name) → label predicate → class chain → xpath
 */
public final class IOSAccessibilityInspector implements AccessibilityInspector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   HTTP   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String udid;
    private final String wdaBaseUrl;      // e.g. "http://localhost:8100"
    private final HierarchyCache cache    = new HierarchyCache();

    private volatile String wdaSessionId;  // created lazily on first use

    public IOSAccessibilityInspector(String udid, String wdaBaseUrl) {
        this.udid       = udid;
        this.wdaBaseUrl = (wdaBaseUrl != null && !wdaBaseUrl.isBlank())
                          ? wdaBaseUrl : "http://localhost:8100";
    }

    // ── Inspection ────────────────────────────────────────────────────────────

    @Override
    public UIElement findElementAt(int tapX, int tapY) {
        String xml = getXml();
        if (xml == null) return null;
        return parseElementAt(xml, tapX, tapY);
    }

    @Override
    public UIElement findFocusedInputElement() {
        String xml = getXml();
        if (xml == null) return null;
        return parseFocusedInput(xml);
    }

    @Override
    public boolean isKeyboardVisible() {
        String xml = getXml();
        if (xml == null) return false;
        return xml.contains("XCUIElementTypeKeyboard");
    }

    @Override
    public String getHierarchyXml() {
        return getXml();
    }

    @Override
    public void invalidateCache() {
        cache.invalidate();
    }

    @Override
    public int[] getScreenSize() {
        ensureSession();
        if (wdaSessionId == null) return new int[]{390, 844};
        try {
            String resp = httpGet("/session/" + wdaSessionId + "/window/size");
            if (resp == null) return new int[]{390, 844};
            JsonNode root = MAPPER.readTree(resp);
            JsonNode val  = root.path("value");
            int w = val.path("width").asInt(390);
            int h = val.path("height").asInt(844);
            return new int[]{w, h};
        } catch (Exception e) {
            return new int[]{390, 844};
        }
    }

    // ── Action injection ──────────────────────────────────────────────────────

    @Override
    public void performTap(int x, int y) {
        ensureSession();
        if (wdaSessionId == null) return;
        String body = buildW3CPointerAction(x, y, 100);
        httpPost("/session/" + wdaSessionId + "/actions", body);
        releaseActions();
    }

    @Override
    public void performSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        ensureSession();
        if (wdaSessionId == null) return;
        // W3C pointer move+down+move+up
        String body = String.format(
            "{\"actions\":[{\"type\":\"pointer\",\"id\":\"finger1\","
            + "\"parameters\":{\"pointerType\":\"touch\"},"
            + "\"actions\":["
            + "{\"type\":\"pointerMove\",\"duration\":0,\"x\":%d,\"y\":%d},"
            + "{\"type\":\"pointerDown\",\"button\":0},"
            + "{\"type\":\"pointerMove\",\"duration\":%d,\"x\":%d,\"y\":%d},"
            + "{\"type\":\"pointerUp\",\"button\":0}"
            + "]}]}", x1, y1, durationMs, x2, y2);
        httpPost("/session/" + wdaSessionId + "/actions", body);
        releaseActions();
    }

    @Override
    public void performLongPress(int x, int y, int durationMs) {
        ensureSession();
        if (wdaSessionId == null) return;
        String body = buildW3CPointerAction(x, y, durationMs);
        httpPost("/session/" + wdaSessionId + "/actions", body);
        releaseActions();
    }

    @Override
    public void performKey(String keyName) {
        ensureSession();
        if (wdaSessionId == null) return;
        String key = toW3CKey(keyName);
        String body = String.format(
            "{\"actions\":[{\"type\":\"key\",\"id\":\"keyboard\","
            + "\"actions\":["
            + "{\"type\":\"keyDown\",\"value\":\"%s\"},"
            + "{\"type\":\"keyUp\",\"value\":\"%s\"}"
            + "]}]}", key, key);
        httpPost("/session/" + wdaSessionId + "/actions", body);
    }

    @Override
    public void performText(String text) {
        ensureSession();
        if (wdaSessionId == null) return;
        // Type into the currently-active element via WDA /wda/keys
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        String body    = "{\"value\":[\"" + escaped + "\"]}";
        httpPost("/session/" + wdaSessionId + "/wda/keys", body);
    }

    @Override
    public void close() {
        cache.invalidate();
        String sid = wdaSessionId;
        if (sid != null) {
            wdaSessionId = null;
            try { httpDelete("/session/" + sid); }
            catch (Exception ignored) {}
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    private synchronized void ensureSession() {
        if (wdaSessionId != null) return;
        try {
            String body =
                "{\"capabilities\":{\"firstMatch\":[{\"platformName\":\"iOS\"}]}}";
            String resp = httpPost("/session", body);
            if (resp == null) return;
            JsonNode root = MAPPER.readTree(resp);
            // WDA may return sessionId at root level or nested in value
            JsonNode sid = root.path("sessionId");
            if (sid.isMissingNode()) sid = root.path("value").path("sessionId");
            if (!sid.isMissingNode() && !sid.isNull()) {
                wdaSessionId = sid.asText();
                System.out.println("[IOSInspector] WDA session created: " + wdaSessionId
                        + " @ " + wdaBaseUrl);
            }
        } catch (Exception e) {
            System.err.println("[IOSInspector] ensureSession error: " + e.getMessage());
        }
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    private String getXml() {
        String cached = cache.get(null); // iOS: screen key not used for now
        if (cached != null) return cached;

        ensureSession();
        if (wdaSessionId == null) return null;

        try {
            String resp = httpGet("/session/" + wdaSessionId + "/source");
            if (resp == null) return null;
            // WDA returns the XML as a string value in JSON
            JsonNode root = MAPPER.readTree(resp);
            JsonNode val  = root.path("value");
            String xml    = val.isTextual() ? val.asText() : resp;
            cache.put(xml, null);
            return xml;
        } catch (Exception e) {
            System.err.println("[IOSInspector] getXml error: " + e.getMessage());
            return null;
        }
    }

    // ── XML parsing ───────────────────────────────────────────────────────────

    private UIElement parseElementAt(String xml, int tapX, int tapY) {
        System.out.printf("[IOSInspector] Touch: (%d,%d)%n", tapX, tapY);
        try {
            Document doc   = parseDoc(xml);
            if (doc == null) {
                System.out.println("[IOSInspector] FAIL: Hierarchy not updated — WDA source returned null");
                return null;
            }
            NodeList nodes   = doc.getElementsByTagName("*");
            UIElement best   = null;
            int       bestDepth = -1;
            long      bestArea  = Long.MAX_VALUE;

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                Element el = (Element) node;

                int[] rect = parseRect(el);
                if (rect == null) continue;
                int ex = rect[0], ey = rect[1], ew = rect[2], eh = rect[3];
                if (tapX < ex || tapX > ex + ew || tapY < ey || tapY > ey + eh) continue;

                // Select deepest node (most specific leaf); ties broken by smaller area
                int  depth = getNodeDepth(node);
                long area  = (long) ew * eh;
                if (depth > bestDepth || (depth == bestDepth && area < bestArea)) {
                    bestDepth = depth;
                    bestArea  = area;
                    best      = buildElement(el, rect);
                }
            }
            if (best == null) {
                System.out.printf("[IOSInspector] No node contains (%d,%d)%n", tapX, tapY);
            } else {
                System.out.printf("[IOSInspector] Selected: type=%s  name=%s  locator=%s:%s%n",
                    best.className, best.accessId, best.locatorStrategy, best.locatorValue);
            }
            return best;
        } catch (Exception e) {
            System.err.println("[IOSInspector] parseElementAt error: " + e.getMessage());
            return null;
        }
    }

    private static int getNodeDepth(Node node) {
        int depth = 0;
        Node parent = node.getParentNode();
        while (parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
            depth++;
            parent = parent.getParentNode();
        }
        return depth;
    }

    private UIElement parseFocusedInput(String xml) {
        try {
            Document doc   = parseDoc(xml);
            if (doc == null) return null;
            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                Element el   = (Element) node;
                String  type = el.getAttribute("type");
                if (type == null) type = el.getTagName();
                if (!type.contains("TextField") && !type.contains("TextInput")
                        && !type.contains("SecureTextField")) continue;
                if (!"true".equals(el.getAttribute("focused"))) {
                    // On iOS, "focused" may not be set; return first text field found
                }
                int[] rect = parseRect(el);
                return buildElement(el, rect);
            }
        } catch (Exception e) {
            System.err.println("[IOSInspector] parseFocusedInput error: " + e.getMessage());
        }
        return null;
    }

    private UIElement buildElement(Element el, int[] rect) {
        String type  = el.getAttribute("type");
        if (type == null || type.isBlank()) type = el.getTagName();

        String name  = el.getAttribute("name");    // accessibility identifier
        String label = el.getAttribute("label");   // accessibility label
        String value = el.getAttribute("value");

        boolean enabled    = !"false".equals(el.getAttribute("enabled"));
        boolean visible    = !"false".equals(el.getAttribute("visible"));
        boolean accessible = "true".equals(el.getAttribute("accessible"));

        // Resolve best locator (iOS priority)
        String strategy, locValue;
        if (name != null && !name.isBlank()) {
            strategy = "accessibility_id";
            locValue = name;
        } else if (label != null && !label.isBlank()) {
            strategy = "predicate_string";
            locValue = "label == \"" + label.replace("\"", "\\\"") + "\"";
        } else {
            strategy = "class_chain";
            locValue = "**/" + type;
        }

        // shortId: name → label → type suffix
        String shortId = !isBlank(name) ? name
                       : !isBlank(label) ? toSlug(label)
                       : type.replace("XCUIElementType", "").toLowerCase();
        if (shortId.length() > 60) shortId = shortId.substring(0, 60);

        int bx = 0, by = 0, bw = 0, bh = 0;
        if (rect != null) { bx = rect[0]; by = rect[1]; bw = rect[2]; bh = rect[3]; }

        // text: prefer label, fall back to value
        String text = !isBlank(label) ? label : (!isBlank(value) ? value : "");

        return UIElement.builder()
                .platform("ios")
                .className(type)
                .locatorStrategy(strategy)
                .locatorValue(locValue)
                .text(text)
                .accessibilityLabel(label)
                .resourceId("")
                .packageName("")
                .bundleId("")
                .rect(bx, by, bw, bh)
                .enabled(enabled)
                .clickable(accessible || enabled)
                .visible(visible)
                // backward compat
                .shortId(shortId)
                .accessId(name != null ? name : "")      // name → accessId for code generators
                .elType(inferElType(type))
                .build();
    }

    private static int[] parseRect(Element el) {
        // Format 1: separate x, y, width, height attributes
        String xs = el.getAttribute("x");
        if (xs != null && !xs.isBlank()) {
            try {
                return new int[]{
                    Integer.parseInt(xs.trim()),
                    Integer.parseInt(el.getAttribute("y").trim()),
                    Integer.parseInt(el.getAttribute("width").trim()),
                    Integer.parseInt(el.getAttribute("height").trim())
                };
            } catch (Exception ignored) {}
        }
        // Format 2: rect="{\"x\":0,\"y\":0,\"width\":390,\"height\":844}"
        String rect = el.getAttribute("rect");
        if (rect != null && !rect.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(rect);
                return new int[]{
                    node.path("x").asInt(),
                    node.path("y").asInt(),
                    node.path("width").asInt(),
                    node.path("height").asInt()
                };
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String inferElType(String type) {
        if (type == null) return "btn";
        String t = type.toLowerCase();
        if (t.contains("textfield") || t.contains("textarea") || t.contains("securetextfield")) return "input";
        if (t.contains("button") || t.contains("cell"))  return "btn";
        if (t.contains("statictext"))                    return "text";
        if (t.contains("table") || t.contains("collection") || t.contains("scroll")) return "list";
        if (t.contains("image"))                         return "image";
        return "btn";
    }

    private static Document parseDoc(String xml) throws Exception {
        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
        fac.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fac.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fac.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fac.setExpandEntityReferences(false);
        DocumentBuilder builder = fac.newDocumentBuilder();
        // Suppress SAX error output
        builder.setErrorHandler(null);
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // ── W3C helpers ───────────────────────────────────────────────────────────

    private String buildW3CPointerAction(int x, int y, int pressDurationMs) {
        return String.format(
            "{\"actions\":[{\"type\":\"pointer\",\"id\":\"finger1\","
            + "\"parameters\":{\"pointerType\":\"touch\"},"
            + "\"actions\":["
            + "{\"type\":\"pointerMove\",\"duration\":0,\"x\":%d,\"y\":%d},"
            + "{\"type\":\"pointerDown\",\"button\":0},"
            + "{\"type\":\"pause\",\"duration\":%d},"
            + "{\"type\":\"pointerUp\",\"button\":0}"
            + "]}]}", x, y, pressDurationMs);
    }

    private void releaseActions() {
        if (wdaSessionId != null) {
            httpDelete("/session/" + wdaSessionId + "/actions");
        }
    }

    private static String toW3CKey(String keyName) {
        switch (keyName.toLowerCase()) {
            case "back":    return ""; // Escape / Back
            case "home":    return ""; // No direct W3C equivalent; use as best effort
            case "enter":
            case "done":    return ""; // Return
            case "delete":  return ""; // Delete
            default:        return keyName;
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String httpGet(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(wdaBaseUrl + path))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 300 ? resp.body() : null;
        } catch (Exception e) {
            System.err.println("[IOSInspector] GET " + path + " error: " + e.getMessage());
            return null;
        }
    }

    private String httpPost(String path, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(wdaBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 300 ? resp.body() : null;
        } catch (Exception e) {
            System.err.println("[IOSInspector] POST " + path + " error: " + e.getMessage());
            return null;
        }
    }

    private void httpDelete(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(wdaBaseUrl + path))
                    .DELETE()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String  toSlug(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
