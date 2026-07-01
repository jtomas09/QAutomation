package qa.cinepolis.runner.accessibility;

/**
 * Platform-independent representation of a UI element found during recording.
 *
 * Backward-compatible with the previous UiHierarchyParser.ElementInfo:
 * shortId, resourceId, accessId, text, elType, className, bounds are all
 * preserved so existing JSON consumers (code generators) keep working without
 * change. New fields (platform, locatorStrategy, locatorValue, …) are additive.
 *
 * For Android  — resourceId is the primary locator; accessId is content-desc.
 * For iOS      — accessId carries the XCUITest accessibility identifier (name
 *                attribute); resourceId is always empty; bundleId identifies
 *                the app.
 */
public final class UIElement {

    // ── Platform ──────────────────────────────────────────────────────────────
    public final String  platform;           // "android" | "ios"

    // ── Element identity ──────────────────────────────────────────────────────
    public final String  className;          // e.g. "android.widget.Button" | "XCUIElementTypeButton"
    public final String  locatorStrategy;    // "id" | "accessibility_id" | "text" | "xpath"
    public final String  locatorValue;       // ready-to-use locator value

    public final String  text;               // visible text or value
    public final String  accessibilityLabel; // content-desc (Android) | label (iOS)
    public final String  resourceId;         // Android only;  "" for iOS
    public final String  packageName;        // Android package; "" for iOS
    public final String  bundleId;           // iOS bundle id;   "" for Android

    // ── Geometry ──────────────────────────────────────────────────────────────
    public final int     x;
    public final int     y;
    public final int     width;
    public final int     height;

    // ── State ─────────────────────────────────────────────────────────────────
    public final boolean enabled;
    public final boolean clickable;
    public final boolean visible;

    // ── Resolved by ElementResolver + SemanticAnalyzer ───────────────────────
    public final String  varName;              // camelCase variable name (Spanish), e.g. "btnContinuar"
    public final String  semanticName;         // Spanish semantic name set by SemanticAnalyzer
    public final String  pageObjectAnnotation; // full @FindBy annotation block, e.g. "@AndroidFindBy(id=\"...\")\nprivate WebElement btnContinuar;"

    // ── Backward-compat fields (match UiHierarchyParser.ElementInfo) ──────────
    public final String  shortId;   // short name derived from resource-id or accessibility label
    public final String  accessId;  // content-desc (Android) | name (iOS)  ← used by code generators
    public final String  elType;    // btn | input | text | list | image

    // ─────────────────────────────────────────────────────────────────────────

    private UIElement(Builder b) {
        platform              = b.platform;
        className             = b.className;
        locatorStrategy       = b.locatorStrategy;
        locatorValue          = b.locatorValue;
        text                  = b.text;
        accessibilityLabel    = b.accessibilityLabel;
        resourceId            = b.resourceId;
        packageName           = b.packageName;
        bundleId              = b.bundleId;
        x                     = b.x;
        y                     = b.y;
        width                 = b.width;
        height                = b.height;
        enabled               = b.enabled;
        clickable             = b.clickable;
        visible               = b.visible;
        varName               = b.varName;
        semanticName          = b.semanticName;
        pageObjectAnnotation  = b.pageObjectAnnotation;
        shortId               = b.shortId;
        accessId              = b.accessId;
        elType                = b.elType;
    }

    /** Returns "[x,y][x+w,y+h]" — same format as UiHierarchyParser.ElementInfo.bounds */
    public String getBoundsString() {
        return "[" + x + "," + y + "][" + (x + width) + "," + (y + height) + "]";
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        String  platform           = "";
        String  className          = "";
        String  locatorStrategy    = "";
        String  locatorValue       = "";
        String  text               = "";
        String  accessibilityLabel = "";
        String  resourceId         = "";
        String  packageName        = "";
        String  bundleId           = "";
        int     x, y, width, height;
        boolean enabled   = true;
        boolean clickable = true;
        boolean visible   = true;
        String  varName              = "";
        String  semanticName         = "";
        String  pageObjectAnnotation = "";
        String  shortId              = "";
        String  accessId             = "";
        String  elType               = "btn";

        private Builder() {}

        public Builder platform(String v)              { platform              = nvl(v); return this; }
        public Builder className(String v)             { className             = nvl(v); return this; }
        public Builder locatorStrategy(String v)       { locatorStrategy       = nvl(v); return this; }
        public Builder locatorValue(String v)          { locatorValue          = nvl(v); return this; }
        public Builder text(String v)                  { text                  = nvl(v); return this; }
        public Builder accessibilityLabel(String v)    { accessibilityLabel    = nvl(v); return this; }
        public Builder resourceId(String v)            { resourceId            = nvl(v); return this; }
        public Builder packageName(String v)           { packageName           = nvl(v); return this; }
        public Builder bundleId(String v)              { bundleId              = nvl(v); return this; }
        public Builder rect(int x, int y, int w, int h){ this.x=x; this.y=y; this.width=w; this.height=h; return this; }
        public Builder enabled(boolean v)              { enabled               = v; return this; }
        public Builder clickable(boolean v)            { clickable             = v; return this; }
        public Builder visible(boolean v)              { visible               = v; return this; }
        public Builder varName(String v)               { varName               = nvl(v); return this; }
        public Builder semanticName(String v)          { semanticName          = nvl(v); return this; }
        public Builder pageObjectAnnotation(String v)  { pageObjectAnnotation  = nvl(v); return this; }
        public Builder shortId(String v)               { shortId               = nvl(v); return this; }
        public Builder accessId(String v)              { accessId              = nvl(v); return this; }
        public Builder elType(String v)                { elType                = nvl(v); return this; }

        public UIElement build() { return new UIElement(this); }

        private static String nvl(String v) { return v != null ? v : ""; }
    }
}
