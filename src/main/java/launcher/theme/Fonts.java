package launcher.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

public final class Fonts {

    private Fonts() {}

    private static final Set<String> AVAILABLE;
    static {
        AVAILABLE = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    }

    private static String pick(String... families) {
        for (String f : families) if (AVAILABLE.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    private static String pickMono(String... families) {
        for (String f : families) if (AVAILABLE.contains(f)) return f;
        return Font.MONOSPACED;
    }

    // ── Family names ─────────────────────────────────────────────────
    public static final String UI_FAMILY   = pick("Segoe UI", "Inter", "SF Pro Display", "Helvetica Neue", "Arial");
    public static final String MONO_FAMILY = pickMono("Cascadia Code", "Cascadia Mono", "Consolas", "Menlo", "Courier New");

    // ── UI fonts ──────────────────────────────────────────────────────
    public static final Font UI_SMALL      = new Font(UI_FAMILY, Font.PLAIN,  10);
    public static final Font UI_SMALL_BOLD = new Font(UI_FAMILY, Font.BOLD,   10);
    public static final Font UI_BODY       = new Font(UI_FAMILY, Font.PLAIN,  12);
    public static final Font UI_BODY_BOLD  = new Font(UI_FAMILY, Font.BOLD,   12);
    public static final Font UI_LABEL      = new Font(UI_FAMILY, Font.PLAIN,  11);
    public static final Font UI_LABEL_BOLD = new Font(UI_FAMILY, Font.BOLD,   11);
    public static final Font UI_SUBTITLE   = new Font(UI_FAMILY, Font.PLAIN,  13);
    public static final Font UI_TITLE      = new Font(UI_FAMILY, Font.BOLD,   14);
    public static final Font UI_H2         = new Font(UI_FAMILY, Font.BOLD,   18);
    public static final Font UI_H1         = new Font(UI_FAMILY, Font.BOLD,   22);
    public static final Font NAV_ITEM      = new Font(UI_FAMILY, Font.PLAIN,  13);
    public static final Font NAV_ITEM_BOLD = new Font(UI_FAMILY, Font.BOLD,   13);
    public static final Font NAV_GROUP     = new Font(UI_FAMILY, Font.BOLD,   9);
    public static final Font KPI_NUMBER    = new Font(UI_FAMILY, Font.BOLD,   28);
    public static final Font KPI_LABEL     = new Font(UI_FAMILY, Font.BOLD,   9);
    public static final Font KPI_SUB       = new Font(UI_FAMILY, Font.PLAIN,  11);
    public static final Font BTN_PRIMARY   = new Font(UI_FAMILY, Font.BOLD,   13);
    public static final Font BTN_SMALL     = new Font(UI_FAMILY, Font.BOLD,   11);
    public static final Font BADGE         = new Font(UI_FAMILY, Font.BOLD,   9);
    public static final Font PILL          = new Font(UI_FAMILY, Font.BOLD,   10);

    // ── Monospace fonts ───────────────────────────────────────────────
    public static final Font MONO          = new Font(MONO_FAMILY, Font.PLAIN, 12);
    public static final Font MONO_SMALL    = new Font(MONO_FAMILY, Font.PLAIN, 11);
    public static final Font MONO_CONFIG   = new Font(MONO_FAMILY, Font.PLAIN, 12);

    // ── Emoji / symbol ────────────────────────────────────────────────
    public static final Font EMOJI_SM      = new Font("Dialog", Font.PLAIN,   13);
    public static final Font EMOJI_MD      = new Font("Dialog", Font.PLAIN,   16);
    public static final Font EMOJI_LG      = new Font("Dialog", Font.PLAIN,   20);
    public static final Font SYMBOL        = new Font("Dialog", Font.BOLD,    14);
}
