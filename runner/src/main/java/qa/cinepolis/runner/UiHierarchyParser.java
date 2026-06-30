package qa.cinepolis.runner;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Parses Android UI hierarchy XML from uiautomator dump and finds elements at coordinates.
 *
 * Locator priority per spec: resource-id > content-desc > text > class name.
 * Never returns an xpath locator when a resource-id is available.
 */
public final class UiHierarchyParser {

    private static final int DUMP_TIMEOUT_S = 6;

    private UiHierarchyParser() {}

    // ── Element info ──────────────────────────────────────────────────────────

    public static final class ElementInfo {
        public final String shortId;
        public final String resourceId;
        public final String accessId;
        public final String text;
        public final String elType;     // btn | input | text | list | image
        public final String className;
        public final String bounds;     // "[x1,y1][x2,y2]"

        ElementInfo(String shortId, String resourceId, String accessId,
                    String text, String elType, String className, String bounds) {
            this.shortId    = shortId;
            this.resourceId = resourceId;
            this.accessId   = accessId;
            this.text       = text;
            this.elType     = elType;
            this.className  = className;
            this.bounds     = bounds;
        }
    }

    // ── Screen size ───────────────────────────────────────────────────────────

    /** Returns [width, height] from `adb shell wm size`. Falls back to [1080, 1920]. */
    public static int[] getScreenSize(String adbPath, String udid) {
        try {
            Process p = new ProcessBuilder(adbPath, "-s", udid, "shell", "wm", "size")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(4, TimeUnit.SECONDS);
            p.destroyForcibly();
            // "Physical size: 1080x2400" or "Override size: 1080x2400"
            for (String line : out.split("\\n")) {
                if (!line.contains("size:")) continue;
                String[] parts = line.trim().split(":\\s*");
                if (parts.length < 2) continue;
                String[] wh = parts[1].trim().split("x");
                if (wh.length == 2) {
                    return new int[]{
                        Integer.parseInt(wh[0].trim()),
                        Integer.parseInt(wh[1].trim())
                    };
                }
            }
        } catch (Exception e) {
            System.err.println("[UiHierarchyParser] getScreenSize error: " + e.getMessage());
        }
        return new int[]{1080, 1920};
    }

    // ── Hierarchy dump ────────────────────────────────────────────────────────

    /**
     * Dumps the UI hierarchy XML using three strategies in order:
     *   1. uiautomator dump --compressed /dev/stdout   (fast, most devices)
     *   2. uiautomator dump /dev/stdout                (--compressed not supported on all ROMs)
     *   3. uiautomator dump /data/local/tmp/uidump_rec.xml  then  adb pull via cat
     *      (fallback for devices that cannot write to /dev/stdout at all)
     *
     * Each strategy has a hard timeout. Leading garbage before {@code <?xml} or
     * {@code <hierarchy} is stripped, and a UTF-8 BOM is removed when present.
     *
     * @return valid XML string, or null if all strategies fail
     */
    public static String dumpHierarchy(String adbPath, String udid) {
        // Strategy 1 — compressed stdout
        String xml = dumpViaStdout(adbPath, udid, true);
        if (xml != null) return xml;

        System.err.println("[UiHierarchyParser] --compressed stdout failed, retrying without flag");

        // Strategy 2 — plain stdout (no --compressed)
        xml = dumpViaStdout(adbPath, udid, false);
        if (xml != null) return xml;

        System.err.println("[UiHierarchyParser] stdout strategies failed, trying file-based fallback");

        // Strategy 3 — write to on-device file, then cat it back
        return dumpViaFile(adbPath, udid);
    }

    /** Runs "uiautomator dump [--compressed] /dev/stdout" and extracts XML from stdout. */
    private static String dumpViaStdout(String adbPath, String udid, boolean compressed) {
        try {
            ProcessBuilder pb;
            if (compressed) {
                pb = new ProcessBuilder(adbPath, "-s", udid, "shell",
                        "uiautomator", "dump", "--compressed", "/dev/stdout");
            } else {
                pb = new ProcessBuilder(adbPath, "-s", udid, "shell",
                        "uiautomator", "dump", "/dev/stdout");
            }
            pb.redirectErrorStream(false);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            boolean done = p.waitFor(DUMP_TIMEOUT_S, TimeUnit.SECONDS);
            p.destroyForcibly();
            if (!done) return null;
            return extractXml(new String(out, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[UiHierarchyParser] dumpViaStdout(" + compressed + ") error: " + e.getMessage());
            return null;
        }
    }

    /** Dumps hierarchy to /data/local/tmp/uidump_rec.xml on-device, then cats it back. */
    private static String dumpViaFile(String adbPath, String udid) {
        final String REMOTE = "/data/local/tmp/uidump_rec.xml";
        try {
            // Write the dump file on device
            Process dump = new ProcessBuilder(adbPath, "-s", udid, "shell",
                    "uiautomator", "dump", REMOTE)
                    .redirectErrorStream(true).start();
            dump.getInputStream().readAllBytes(); // drain
            boolean done = dump.waitFor(DUMP_TIMEOUT_S, TimeUnit.SECONDS);
            dump.destroyForcibly();
            if (!done) return null;

            // Cat the file back over adb
            Process cat = new ProcessBuilder(adbPath, "-s", udid, "shell", "cat", REMOTE)
                    .redirectErrorStream(false).start();
            byte[] out = cat.getInputStream().readAllBytes();
            cat.waitFor(DUMP_TIMEOUT_S, TimeUnit.SECONDS);
            cat.destroyForcibly();
            return extractXml(new String(out, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[UiHierarchyParser] dumpViaFile error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Strips leading non-XML garbage (status messages, BOM) from a raw dump string
     * and trims anything after {@code </hierarchy>}.
     * Returns null when no valid XML root element is found.
     */
    private static String extractXml(String raw) {
        if (raw == null) return null;

        // Remove UTF-8 BOM if present
        if (raw.startsWith("﻿")) raw = raw.substring(1);

        // Find the start of the XML — prefer <?xml, fall back to <hierarchy
        int xmlStart = raw.indexOf("<?xml");
        if (xmlStart < 0) xmlStart = raw.indexOf("<hierarchy");
        if (xmlStart < 0) return null;

        String xml = raw.substring(xmlStart).trim();

        // Trim anything after the closing root element
        int end = xml.lastIndexOf("</hierarchy>");
        if (end >= 0) xml = xml.substring(0, end + "</hierarchy>".length());

        return xml.isBlank() ? null : xml;
    }

    // ── Keyboard detection ────────────────────────────────────────────────────

    /**
     * Returns true when the XML hierarchy contains elements belonging to an IME
     * (on-screen keyboard) window. Checks package names for common keyboards.
     */
    public static boolean isKeyboardVisible(String xml) {
        if (xml == null || xml.isBlank()) return false;
        return xml.contains("com.android.inputmethod")
            || xml.contains("com.google.android.inputmethod")
            || xml.contains("com.samsung.android.honeyboard")
            || xml.contains("com.huawei.keyboard")
            || xml.contains("com.xiaomi.keyboard")
            || xml.contains("com.miui.ime")
            || xml.contains("InputMethod")
            || xml.contains("SoftInputWindow");
    }

    /**
     * Returns the text attribute of the node whose bounds attribute matches
     * {@code targetBounds} exactly, or null when no match is found.
     */
    public static String getElementTextAtBounds(String xml, String targetBounds) {
        if (xml == null || targetBounds == null || targetBounds.isBlank()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName("node");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                Element el = (Element) node;
                if (targetBounds.equals(el.getAttribute("bounds"))) {
                    return el.getAttribute("text");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Finds the focused, editable node in the hierarchy (class contains EditText
     * and focused="true"), or the first EditText if none is focused.
     * Returns null when no EditText is present.
     */
    public static ElementInfo findFocusedEditText(String xml) {
        if (xml == null || xml.isBlank()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName("node");

            Element firstEditText = null;
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                Element el = (Element) node;
                String cls = el.getAttribute("class").toLowerCase();
                if (!cls.contains("edittext") && !cls.contains("textfield")) continue;
                if (firstEditText == null) firstEditText = el;
                if ("true".equals(el.getAttribute("focused"))) {
                    return buildElementInfo(el);
                }
            }
            return (firstEditText != null) ? buildElementInfo(firstEditText) : null;
        } catch (Exception ignored) {}
        return null;
    }

    // ── Element lookup ────────────────────────────────────────────────────────

    /**
     * Finds the deepest node in the tree that contains (tapX, tapY).
     *
     * "Deepest" means the node with the greatest ancestor-count in the DOM tree,
     * which corresponds to the most specific/leaf element — exactly what Appium
     * selectors should target.  When two nodes are at the same depth, the one
     * with the smaller bounding area is preferred (handles overlapping siblings).
     *
     * Returns null when no element is found or the XML is invalid.
     */
    public static ElementInfo findElementAt(String xml, int tapX, int tapY) {
        if (xml == null || xml.isBlank()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE hardening
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList nodes = doc.getElementsByTagName("node");
            ElementInfo best     = null;
            int         bestDepth = -1;
            long        bestArea  = Long.MAX_VALUE;

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                Element el = (Element) node;

                int[] rect = parseBounds(el.getAttribute("bounds"));
                if (rect == null) continue;
                if (tapX < rect[0] || tapX > rect[2] || tapY < rect[1] || tapY > rect[3]) continue;

                int  depth = getNodeDepth(node);
                long area  = (long)(rect[2] - rect[0]) * (rect[3] - rect[1]);

                // Prefer deeper nodes; break ties by smaller area (overlapping siblings)
                if (depth > bestDepth || (depth == bestDepth && area < bestArea)) {
                    bestDepth = depth;
                    bestArea  = area;
                    best      = buildElementInfo(el);
                }
            }
            return best;
        } catch (Exception e) {
            System.err.println("[UiHierarchyParser] findElementAt error: " + e.getMessage());
            return null;
        }
    }

    /** Returns the number of ancestor nodes (depth in DOM tree, 0 = document root). */
    private static int getNodeDepth(Node node) {
        int depth = 0;
        Node parent = node.getParentNode();
        while (parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
            depth++;
            parent = parent.getParentNode();
        }
        return depth;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static int[] parseBounds(String bounds) {
        if (bounds == null || bounds.isBlank()) return null;
        try {
            // "[x1,y1][x2,y2]" → "x1,y1,x2,y2"
            String s = bounds.replace("][", ",").replace("[", "").replace("]", "");
            String[] p = s.split(",");
            return new int[]{
                Integer.parseInt(p[0].trim()),
                Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()),
                Integer.parseInt(p[3].trim())
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static ElementInfo buildElementInfo(Element el) {
        String resourceId  = el.getAttribute("resource-id");
        String contentDesc = el.getAttribute("content-desc");
        String text        = el.getAttribute("text");
        String className   = el.getAttribute("class");
        String bounds      = el.getAttribute("bounds");

        // shortId: last segment of resource-id after '/', else content-desc, else text
        String shortId;
        if (!resourceId.isBlank()) {
            int slash = resourceId.lastIndexOf('/');
            shortId = slash >= 0 ? resourceId.substring(slash + 1) : resourceId;
        } else if (!contentDesc.isBlank()) {
            shortId = contentDesc.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        } else if (!text.isBlank()) {
            shortId = text.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        } else {
            String cls = className.isEmpty() ? "element" : className;
            shortId = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase();
        }
        // Trim excessively long shortIds
        if (shortId.length() > 60) shortId = shortId.substring(0, 60);

        return new ElementInfo(
            shortId, resourceId, contentDesc, text,
            inferElType(className), className, bounds
        );
    }

    private static String inferElType(String cls) {
        if (cls == null || cls.isBlank()) return "btn";
        String c = cls.toLowerCase();
        if (c.contains("edittext") || c.contains("textfield") || c.contains("input")) return "input";
        if (c.contains("button"))                          return "btn";
        if (c.contains("imagebutton"))                     return "btn";
        if (c.contains("textview"))                        return "text";
        if (c.contains("listview") || c.contains("recyclerview") || c.contains("scrollview")) return "list";
        if (c.contains("imageview") || c.contains("image")) return "image";
        return "btn";
    }
}
