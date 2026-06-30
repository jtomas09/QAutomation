package qa.cinepolis.runner.accessibility;

/**
 * Smart two-level cache for accessibility hierarchy XML dumps.
 *
 * Invalidation triggers:
 *   1. Max age exceeded   (configurable, default 2 s)
 *   2. Activity / screen changed  (caller provides current screen label)
 *   3. Explicit invalidate()      (called after every injected action)
 *
 * Thread-safe: all state is guarded by the instance monitor.
 */
public final class HierarchyCache {

    private final long maxAgeMs;

    private volatile String cachedXml;
    private volatile long   cachedAtMs;
    private volatile String lastScreen;  // Activity (Android) or ViewController name (iOS)

    public HierarchyCache() {
        this(2_000);
    }

    public HierarchyCache(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * Returns the cached XML when it is still fresh and the screen has not
     * changed; otherwise returns null (the caller should re-dump).
     *
     * @param currentScreen nullable; passing null skips the screen-change check
     */
    public synchronized String get(String currentScreen) {
        if (cachedXml == null) return null;
        if (System.currentTimeMillis() - cachedAtMs > maxAgeMs) {
            cachedXml = null;
            return null;
        }
        if (currentScreen != null && lastScreen != null
                && !currentScreen.equals(lastScreen)) {
            cachedXml = null;
            return null;
        }
        return cachedXml;
    }

    /**
     * Stores a fresh hierarchy dump.
     *
     * @param xml           the XML string to cache
     * @param currentScreen nullable; identifies the screen this dump belongs to
     */
    public synchronized void put(String xml, String currentScreen) {
        cachedXml   = xml;
        cachedAtMs  = System.currentTimeMillis();
        lastScreen  = currentScreen;
    }

    /** Forces the next {@link #get} call to return null. */
    public synchronized void invalidate() {
        cachedXml = null;
    }
}
