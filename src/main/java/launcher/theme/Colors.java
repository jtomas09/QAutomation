package launcher.theme;

import java.awt.Color;

public final class Colors {

    private Colors() {}

    // ── Base backgrounds ──────────────────────────────────────────────
    public static final Color BG_MAIN    = new Color(4,  8,  22);
    public static final Color BG_PANEL   = new Color(7,  12, 28);
    public static final Color BG_CARD    = new Color(12, 18, 38);
    public static final Color BG_ROW     = new Color(10, 16, 34);
    public static final Color BG_ROW_SEL = new Color(59, 130, 246);
    public static final Color BG_HOVER   = new Color(28, 45, 90);
    public static final Color BG_NAVBAR  = new Color(6,  10, 26);
    public static final Color BG_SIDEBAR = new Color(7,  11, 27);

    // ── Accent palette ────────────────────────────────────────────────
    public static final Color ACCENT     = new Color(59,  130, 246);
    public static final Color ACCENT_HOV = new Color(96,  165, 250);
    public static final Color PURPLE     = new Color(147, 51,  234);
    public static final Color PURPLE_L   = new Color(167, 139, 250);
    public static final Color ORANGE     = new Color(249, 115, 22);
    public static final Color GREEN      = new Color(34,  197, 94);
    public static final Color TEAL       = new Color(20,  184, 166);
    public static final Color GOLD       = new Color(234, 179, 8);
    public static final Color PINK       = new Color(236, 72,  153);

    // ── Semantic ──────────────────────────────────────────────────────
    public static final Color OK         = new Color(34,  197, 94);
    public static final Color FAIL       = new Color(239, 68,  68);
    public static final Color WARN       = new Color(234, 179, 8);
    public static final Color INFO       = new Color(59,  130, 246);

    // ── Text ──────────────────────────────────────────────────────────
    public static final Color TEXT_PRI   = new Color(245, 247, 255);
    public static final Color TEXT_SEC   = new Color(200, 210, 235);
    public static final Color TEXT_DIM   = new Color(120, 136, 180);
    public static final Color TEXT_LBL   = new Color(160, 175, 215);
    public static final Color BLUE_TITLE = new Color(96,  165, 250);

    // ── Borders / dividers ────────────────────────────────────────────
    public static final Color BORDER     = new Color(25,  35, 65);
    public static final Color BORDER_L   = new Color(30,  45, 85);
    public static final Color BORDER_HL  = new Color(59,  130, 246, 80);

    // ── Status pills ──────────────────────────────────────────────────
    public static final Color STATUS_OK_BG    = new Color(34,  197, 94,  25);
    public static final Color STATUS_OK_FG    = new Color(34,  197, 94);
    public static final Color STATUS_FAIL_BG  = new Color(239, 68,  68,  25);
    public static final Color STATUS_FAIL_FG  = new Color(239, 68,  68);
    public static final Color STATUS_WARN_BG  = new Color(234, 179, 8,   25);
    public static final Color STATUS_WARN_FG  = new Color(234, 179, 8);
    public static final Color STATUS_INFO_BG  = new Color(59,  130, 246, 25);
    public static final Color STATUS_INFO_FG  = new Color(59,  130, 246);
    public static final Color STATUS_IDLE_BG  = new Color(30,  35,  60);
    public static final Color STATUS_IDLE_FG  = new Color(120, 136, 180);

    // ── Transparent ───────────────────────────────────────────────────
    public static final Color TRANSPARENT     = new Color(0, 0, 0, 0);
}
