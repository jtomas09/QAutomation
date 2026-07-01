package qa.cinepolis.runner.accessibility;

/**
 * Platform-independent contract for UI inspection and action injection.
 *
 * Responsibilities (single per method):
 *  - findElementAt:          locate the UI element beneath the tap coordinates
 *  - findFocusedInputElement: find the currently-focused text field
 *  - isKeyboardVisible:      detect whether a software keyboard is on screen
 *  - getHierarchyXml:        raw accessibility tree (XML inspector / page objects)
 *  - invalidateCache:        force the next inspection call to re-dump the hierarchy
 *  - getScreenSize:          physical screen dimensions [width, height]
 *  - perform*:               inject an action into the device without the caller
 *                            needing to know whether it is Android or iOS
 *  - close:                  release resources (WDA session, threads, …)
 *
 * The Recorder (RecordingEngine) calls ONLY these methods. It never directly
 * invokes ADB, WDA, or any platform-specific API.
 */
public interface AccessibilityInspector {

    // ── Inspection ────────────────────────────────────────────────────────────

    /**
     * Returns the element at (tapX, tapY) in device coordinates, or null when
     * no element can be found (hierarchy dump failed, coordinates out of bounds).
     */
    UIElement findElementAt(int tapX, int tapY);

    /**
     * Returns the currently-focused editable element (EditText on Android,
     * XCUIElementTypeTextField on iOS), or null when none is focused.
     */
    UIElement findFocusedInputElement();

    /** Returns true when a software keyboard occupies part of the screen. */
    boolean isKeyboardVisible();

    /**
     * Returns the raw accessibility hierarchy as XML.
     * Used by XML Inspector and Page Object generator.
     * Returns null when the dump fails.
     */
    String getHierarchyXml();

    /**
     * Invalidates the cached hierarchy so the next inspection call re-dumps.
     * Must be called after every action that changes the UI.
     */
    void invalidateCache();

    /** Returns [width, height] of the physical screen, falling back to [1080, 1920]. */
    int[] getScreenSize();

    // ── Action injection ──────────────────────────────────────────────────────

    /** Injects a single tap at (x, y) in device coordinates. */
    void performTap(int x, int y);

    /** Injects a swipe from (x1,y1) to (x2,y2) with the given duration in ms. */
    void performSwipe(int x1, int y1, int x2, int y2, int durationMs);

    /** Injects a long-press at (x, y) for {@code durationMs} milliseconds. */
    void performLongPress(int x, int y, int durationMs);

    /**
     * Sends a key event (back, home, enter, delete, …).
     * The key name is the same string the RecordingEngine already uses.
     */
    void performKey(String keyName);

    /** Types the given text into the currently-focused element. */
    void performText(String text);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Releases all resources held by this inspector (WDA sessions, threads). */
    void close();

    /**
     * Returns the human-readable name of the current screen or activity.
     * Examples: "Login", "Home", "ClubCinepolis".
     * Default implementation returns an empty string; platform inspectors override it.
     */
    default String getCurrentScreenName() { return ""; }
}
