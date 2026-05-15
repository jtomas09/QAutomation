package launcher;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.platform.engine.discovery.DiscoverySelectors.*;

public class Main {

    // ─── Premium Dark Palette ─────────────────────────────────────
private static final Color BG_MAIN    = new Color(4, 8, 22);
private static final Color BG_PANEL   = new Color(7, 12, 28);
private static final Color BG_CARD    = new Color(12, 18, 38);
private static final Color BG_ROW     = new Color(10, 16, 34);
private static final Color BG_ROW_SEL = new Color(59, 130, 246);
private static final Color BG_HOVER   = new Color(28, 45, 90);

private static final Color ACCENT     = new Color(59, 130, 246);
private static final Color PURPLE     = new Color(147, 51, 234);
private static final Color ORANGE     = new Color(249, 115, 22);
private static final Color GREEN      = new Color(34, 197, 94);
private static final Color TEAL       = new Color(20, 184, 166);
private static final Color GOLD       = new Color(234, 179, 8);

private static final Color BLUE_TITLE  = new Color(96, 165, 250);

private static final Color TEXT_PRI   = new Color(245, 247, 255);
private static final Color TEXT_DIM   = new Color(120, 136, 180);
private static final Color TEXT_LBL   = new Color(160, 175, 215);

private static final Color COLOR_OK   = new Color(34, 197, 94);
private static final Color COLOR_FAIL = new Color(239, 68, 68);
private static final Color COLOR_SKIP = new Color(234, 179, 8);

private static final Color BORDER     = new Color(25, 35, 65);

    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss");

    // ─── Countries [flag, code, name] ─────────────────────────────
    private static final String[][] COUNTRIES = {
        {"🇲🇽", "MX", "México"},
        {"🇦🇷", "AR", "Argentina"},
        {"🇨🇱", "CL", "Chile"},
        {"🇨🇴", "CO", "Colombia"},
        {"🇵🇪", "PE", "Perú"},
        {"🇪🇸", "ES", "España"},
    };

    // ─── Argentina ciudades ───────────────────────────────────────
    private static final Map<String, String[]> ARGENTINA_CITIES = new LinkedHashMap<>();
    static {
        ARGENTINA_CITIES.put("Buenos Aires", new String[]{
            "Cinépolis Avellaneda", "Cinépolis Luján",
            "Cinépolis Merlo",      "Cinépolis Pilar",
            "Cinépolis Plaza Houssay", "Cinépolis Recoleta",
        });
        ARGENTINA_CITIES.put("Mendoza",          new String[]{"Cinépolis Arena Maipu", "Cinépolis Mendoza Plaza"});
        ARGENTINA_CITIES.put("Neuquén", new String[]{"Cinépolis Neuquén"});
        ARGENTINA_CITIES.put("Santa Fe",         new String[]{"Cinépolis Rosario"});
    }

    // ─── Argentina cine → sufijo de método ───────────────────────
    private static final Map<String, String> ARGENTINA_CINEMA_SUFFIX = new LinkedHashMap<>();
    static {
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Avellaneda",    "Avellaneda");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Luján",         "Lujan");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Merlo",         "Merlo");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Pilar",         "Pilar");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Plaza Houssay", "PlazaHoussay");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Recoleta",      "Recoleta");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Arena Maipu",   "ArenaMaipu");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Mendoza Plaza", "MendozaPlaza");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Neuquén",       "Neuquen");
        ARGENTINA_CINEMA_SUFFIX.put("Cinépolis Rosario",       "Rosario");
    }

    // ─── Chile cines [nombre, tipo, sufijo] ───────────────────────
    private static final String[][] CHILE_CINES = {
        {"Los Dominicos",         "Tradicional", "Dominicos"},
        {"La Reina",              "Atmósfera",   "LaReina"},
        {"Parque Arauco",         "Atmósfera",   "ParqueArauco"},
        {"Parque Arauco Premium", "VIP",         "ParqueAraucoPremium"},
    };

    // ─── UI state ─────────────────────────────────────────────────
    private static JTextPane  logPane;
    private static JLabel     statusDot, statusText;
    private static RoundedButton mainBtn;
    private static RoundedButton abortBtn;
    private static JPanel     rightCardPanel;
    private static CardLayout rightCardLayout;
    private static JLabel     passedVal, failedVal, skippedVal, totalVal, timeVal;
    private static JComboBox<String> deviceCombo;
    private static JTextPane  chatPane;
    private static JTextField chatInput;
    private static final java.util.List<org.json.JSONObject> chatHistory = new ArrayList<>();
    private static File   pendingAttachment = null;
    private static JLabel attachLabel       = null;

    private static final Map<String, RoundedPanel>   countryRows          = new LinkedHashMap<>();
    private static final Map<String, JPanel>         moduleCards          = new LinkedHashMap<>();
    private static final Map<String, String>         moduleDisplayNames   = new LinkedHashMap<>(); // normalizedKey → display name
    private static final Map<String, String>         genymotionRecipeMap  = new LinkedHashMap<>(); // display → uuid
    private static final java.util.List<JButton>     testButtons          = new ArrayList<>();
    private static volatile boolean                   running        = false;
    private static String                             selectedCountry = "";
    private static volatile Thread                    testThread     = null;
    private static volatile java.util.List<JButton>  runningSnapshot = null;
    private static JPanel mexicoGrid;

    private static int statPassed = 0, statFailed = 0, statSkipped = 0, statTotal = 0;

    // ─── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) runConsole();
        else SwingUtilities.invokeLater(Main::buildGui);
    }

    // ══════════════════════════════════════════════════════════════
    //  GUI
    // ══════════════════════════════════════════════════════════════

    private static void buildGui() {
        if (!FlatDarkLaf.setup()) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }
        UIManager.put("defaultFont",                  new Font("Segoe UI", Font.PLAIN, 13));
        UIManager.put("Component.arc",                8);
        UIManager.put("Button.arc",                   8);
        UIManager.put("TextComponent.arc",            6);
        UIManager.put("ScrollBar.width",              8);
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("ScrollBar.thumbInsets",        new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track",              BG_PANEL);
        UIManager.put("ScrollBar.thumb",              new Color(59, 130, 246, 90));
        UIManager.put("ScrollBar.hoverThumbColor",    ACCENT);
        UIManager.put("Panel.background",             BG_MAIN);
        UIManager.put("ComboBox.background",          BG_CARD);
        UIManager.put("ComboBox.foreground",          TEXT_PRI);
        UIManager.put("ComboBox.buttonBackground",    BG_CARD);
        UIManager.put("ComboBox.selectionBackground", new Color(59, 130, 246, 90));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("TextField.background",         BG_CARD);
        UIManager.put("TextField.foreground",         TEXT_PRI);
        UIManager.put("PasswordField.background",     BG_CARD);
        UIManager.put("SplitPane.background",         BG_MAIN);
        UIManager.put("SplitPaneDivider.background",  BG_MAIN);
        JFrame frame = new JFrame("Cinépolis · Automation QA");
        java.util.List<java.awt.Image> frameIcons = new ArrayList<>();
        for (int sz : new int[]{16, 32, 48, 64}) {
            ImageIcon ic = loadScaledIcon("/logos/Cinépolis.png", sz);
            if (ic == null) ic = loadScaledIcon("/logos/Cinepolis.png", sz);
            if (ic != null) frameIcons.add(ic.getImage());
        }
        if (!frameIcons.isEmpty()) frame.setIconImages(frameIcons);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1450, 900);
        frame.setMinimumSize(new Dimension(1000, 660));
        frame.setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // Base gradient
                g2.setPaint(new GradientPaint(0, 0, new Color(6, 10, 30), 0, h, BG_MAIN));
                g2.fillRect(0, 0, w, h);
                // Top-center blue atmospheric halo
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, 0f), w * 0.65f,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(59, 130, 246, 28), new Color(59, 130, 246, 9), new Color(59, 130, 246, 0)}));
                g2.fillRect(0, 0, w, h / 2);
                // Top-left accent radial (sidebar side)
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.13f, h * 0.06f), w * 0.38f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(59, 130, 246, 35), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, w / 2, h / 3);
                // Bottom-right purple ambient
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.88f, h * 0.94f), w * 0.42f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(147, 51, 234, 22), new Color(0, 0, 0, 0)}));
                g2.fillRect(w / 2, h * 2 / 3, w, h);
                // Vignette (darker edges for depth)
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, h * 0.5f),
                    (float) Math.max(w, h) * 0.75f,
                    new float[]{0.35f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 65)}));
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        root.setOpaque(true);
        root.add(buildHeader(),     BorderLayout.NORTH);
        root.add(buildCenter(),     BorderLayout.CENTER);
        root.add(buildFooter(),     BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setVisible(true);
        redirectOutput();
    }

    // ── Header ────────────────────────────────────────────────────

    private static JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), ht = getHeight();
                // Glass base: dark blue-tinted fill
                g2.setColor(new Color(7, 12, 32));
                g2.fillRect(0, 0, w, ht);
                // Top-to-bottom white sheen (glass refraction)
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 9), 0, ht, new Color(255, 255, 255, 0)));
                g2.fillRect(0, 0, w, ht);
                // Right-center blue atmospheric glow
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.72f, ht * 0.5f), w * 0.28f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(59, 130, 246, 22), new Color(0, 0, 0, 0)}));
                g2.fillRect(w / 2, 0, w / 2, ht);
                // Bottom edge accent glow
                g2.setPaint(new GradientPaint(0, ht - 3, new Color(59, 130, 246, 50), 0, ht, new Color(59, 130, 246, 0)));
                g2.fillRect(0, ht - 3, w, 3);
                g2.dispose();
            }
        };
        h.setOpaque(true);
        h.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(30, 45, 85)),
            new EmptyBorder(14, 28, 14, 28)));

        // Left: config dropdowns (Ambiente, Dispositivo, Suite)
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        left.setOpaque(false);
        left.add(configItem("🔍", "Ambiente:", new String[]{"QA", "Staging", "Producción"}));
        left.add(buildDeviceItem());
        left.add(configItem(null, "Suite:", new String[]{"Smoke Tests", "Completa", "Regresión"}));

        // Right: status + button + dots menu
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);

        statusDot  = new JLabel("●");
        statusDot.setFont(new Font("Dialog", Font.PLAIN, 14));
        statusDot.setForeground(COLOR_OK);
        statusText = new JLabel("Ready");
        statusText.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusText.setForeground(COLOR_OK);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.setOpaque(false);
        statusPanel.add(statusDot); statusPanel.add(statusText);

        mainBtn = new RoundedButton("▶  EJECUTAR PRUEBAS", ACCENT, 14);
        mainBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        mainBtn.setForeground(Color.WHITE);
        mainBtn.setPreferredSize(new Dimension(200, 38));
        mainBtn.addActionListener(e -> onMainBtnClick());

        RoundedButton dotsBtn = new RoundedButton("···", BG_CARD, 8);
        dotsBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        dotsBtn.setForeground(TEXT_DIM);
        dotsBtn.setPreferredSize(new Dimension(40, 38));
        dotsBtn.addActionListener(e -> showConfigDialog(dotsBtn));

        right.add(statusPanel); right.add(mainBtn); right.add(dotsBtn);

        h.add(left,  BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private static void onMainBtnClick() {
        if (running) { abortExecution(); return; }
        Properties props = readAppiumProps();
        boolean dfEnabled = "SI".equalsIgnoreCase(System.getProperty("devicefarm.enabled",
                props.getProperty("devicefarm.enabled", "NO")));
        boolean gmEnabled = "SI".equalsIgnoreCase(System.getProperty("genymotion.enabled",
                props.getProperty("genymotion.enabled", "NO")));
        if (dfEnabled) { execDeviceFarm(); return; }
        if (gmEnabled)  { execGenymotion(); return; }
        if (!selectedCountry.isEmpty()) execAllForCountry();
    }

    private static void execAllForCountry() {
        switch (selectedCountry) {
            case "México"    -> exec("Suite Completa México",    selectPackage("tests.México"));
            case "Argentina" -> exec("Suite Completa Argentina", selectClass("tests.Argentina.NoAfectacionArgentina"));
            case "Chile"     -> exec("Suite Completa Chile",     selectClass("tests.Chile.NoAfectacionChile"));
            default          -> logLine("ℹ", "Sin suite configurada para " + selectedCountry, TEXT_DIM);
        }
    }

    // ── Center ────────────────────────────────────────────────────

    private static JPanel buildCenter() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_MAIN);
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(BG_MAIN);
        body.add(buildSidebar(),    BorderLayout.WEST);
        body.add(buildRightArea(),  BorderLayout.CENTER);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }

    // ── Config bar ────────────────────────────────────────────────

    private static JPanel buildConfigBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 0));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(10, 18, 10, 18)));

        bar.add(configItem("🔍", "Ambiente:", new String[]{"QA", "Staging", "Producción"}));
        bar.add(buildDeviceItem());
        bar.add(configItem(null, "Suite:", new String[]{"Smoke Tests", "Completa", "Regresión"}));
        return bar;
    }

    private static JPanel buildDeviceItem() {
        Properties props = readAppiumProps();
        String name = System.getProperty("deviceName", props.getProperty("deviceName", "Sin dispositivo"));
        boolean dfEnabled = "SI".equalsIgnoreCase(System.getProperty("devicefarm.enabled",
                props.getProperty("devicefarm.enabled", "NO")));
        boolean gmEnabled = "SI".equalsIgnoreCase(System.getProperty("genymotion.enabled",
                props.getProperty("genymotion.enabled", "NO")));

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);

        JLabel ico = new JLabel("📱");
        ico.setFont(new Font("Dialog", Font.PLAIN, 13));
        ico.setForeground(TEXT_DIM);

        JLabel lbl = new JLabel("Dispositivo:");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(TEXT_DIM);

        deviceCombo = new JComboBox<>(new String[]{name});
        deviceCombo.setFont(new Font("SansSerif", Font.BOLD, 12));
        deviceCombo.setBackground(BG_CARD);
        deviceCombo.setForeground(TEXT_PRI);
        deviceCombo.setPreferredSize(new Dimension(200, 28));
        deviceCombo.addActionListener(e -> {
            String sel = (String) deviceCombo.getSelectedItem();
            if (sel != null && !sel.isBlank()) {
                String gmUuid = genymotionRecipeMap.get(sel);
                if (gmUuid != null) {
                    System.setProperty("genymotion.recipe.uuid", gmUuid);
                } else {
                    String devName = sel.contains(" (") ? sel.substring(0, sel.indexOf(" (")).trim() : sel;
                    System.setProperty("deviceName", devName);
                }
            }
        });

        p.add(ico); p.add(lbl); p.add(deviceCombo);

        if (dfEnabled) {
            JLabel dfBadge = new JLabel("☁ AWS");
            dfBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
            dfBadge.setForeground(new Color(255, 153, 0));
            dfBadge.setOpaque(true);
            dfBadge.setBackground(new Color(35, 25, 5));
            dfBadge.setBorder(new EmptyBorder(2, 5, 2, 5));
            p.add(dfBadge);
        }

        if (gmEnabled) {
            JLabel gmBadge = new JLabel("📱 GENY");
            gmBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
            gmBadge.setForeground(new Color(80, 220, 120));
            gmBadge.setOpaque(true);
            gmBadge.setBackground(new Color(5, 30, 10));
            gmBadge.setBorder(new EmptyBorder(2, 5, 2, 5));
            p.add(gmBadge);
        }

        return p;
    }

    private static JPanel configItem(String icon, String label, String[] opts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        if (icon != null) {
            JLabel ico = new JLabel(icon);
            ico.setFont(new Font("Dialog", Font.PLAIN, 13));
            ico.setForeground(TEXT_DIM);
            p.add(ico);
        }
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(TEXT_DIM);
        JComboBox<String> box = new JComboBox<>(opts);
        box.setFont(new Font("SansSerif", Font.BOLD, 12));
        box.setBackground(BG_CARD);
        box.setForeground(TEXT_PRI);
        box.setPreferredSize(new Dimension(145, 28));
        p.add(lbl); p.add(box);
        return p;
    }

    // ── Sidebar ───────────────────────────────────────────────────

    private static JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(8, 14, 34), 0, h, new Color(5, 9, 22)));
                g2.fillRect(0, 0, w, h);
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, 0), w,
                    new float[]{0f, 1f},
                    new Color[]{new Color(59, 130, 246, 42), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, w, h / 2);
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(0, (float) h),
                    w * 0.85f, new float[]{0f, 1f},
                    new Color[]{new Color(147, 51, 234, 28), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, h * 2 / 3, w, h / 3 + 1);
                g2.dispose();
            }
        };
        sidebar.setOpaque(true);
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, new Color(30, 45, 85)));

        // ── Brand section at top ──────────────────────────────────
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 14));
        brand.setOpaque(false);
        brand.setBorder(new MatteBorder(0, 0, 1, 0, new Color(30, 45, 85)));
        ImageIcon cineLogo = loadScaledIcon("/logos/Cinépolis.png", 36);
        if (cineLogo == null) cineLogo = loadScaledIcon("/logos/Cinepolis.png", 36);
        if (cineLogo != null) {
            brand.add(new JLabel(cineLogo));
        } else {
            JLabel star = new JLabel("★");
            star.setFont(new Font("Dialog", Font.BOLD, 22));
            star.setForeground(GOLD);
            brand.add(star);
        }
        JPanel brandTitles = new JPanel();
        brandTitles.setLayout(new BoxLayout(brandTitles, BoxLayout.Y_AXIS));
        brandTitles.setOpaque(false);
        JLabel aqLbl = new JLabel("AUTOMATION QA");
        aqLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        aqLbl.setForeground(Color.WHITE);
        JLabel tlLbl = new JLabel("Test Launcher");
        tlLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tlLbl.setForeground(TEXT_DIM);
        brandTitles.add(aqLbl);
        brandTitles.add(tlLbl);
        brand.add(brandTitles);

        // ── "SELECCIONA UN PAÍS" header ───────────────────────────
        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        hdr.setOpaque(false);
        JLabel globe = new JLabel("🌐");
        globe.setFont(new Font("Dialog", Font.PLAIN, 14));
        JLabel title = new JLabel("SELECCIONA UN PAÍS");
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        title.setForeground(TEXT_DIM);
        hdr.add(globe); hdr.add(title);

        // ── Country list ──────────────────────────────────────────
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(4, 10, 10, 10));
        for (String[] c : COUNTRIES) {
            RoundedPanel row = buildCountryRow(c[0], c[1], c[2]);
            JPanel wrapper = (JPanel) row.getClientProperty("wrapper");
            list.add(wrapper != null ? wrapper : row);
        }
        list.add(Box.createVerticalGlue());

        JPanel countrySection = new JPanel(new BorderLayout());
        countrySection.setOpaque(false);
        countrySection.add(hdr,  BorderLayout.NORTH);
        countrySection.add(list, BorderLayout.CENTER);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(brand,         BorderLayout.NORTH);
        topSection.add(countrySection, BorderLayout.CENTER);

        // ── Bottom area: Ejecución inteligente + info + version ───
        JPanel bottomSection = new JPanel();
        bottomSection.setLayout(new BoxLayout(bottomSection, BoxLayout.Y_AXIS));
        bottomSection.setOpaque(false);

        // Ejecución inteligente row
        JPanel ejecRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        ejecRow.setOpaque(false);
        ejecRow.setBorder(new MatteBorder(1, 0, 0, 0, new Color(30, 45, 85)));
        JLabel purpleCircle = new JLabel("✦");
        purpleCircle.setFont(new Font("Dialog", Font.PLAIN, 18));
        purpleCircle.setForeground(new Color(167, 139, 250));
        purpleCircle.setOpaque(true);
        purpleCircle.setBackground(new Color(59, 7, 100));
        purpleCircle.setBorder(new EmptyBorder(6, 8, 6, 8));
        JPanel ejecTxt = new JPanel();
        ejecTxt.setLayout(new BoxLayout(ejecTxt, BoxLayout.Y_AXIS));
        ejecTxt.setOpaque(false);
        JLabel ejecTitle = new JLabel("Ejecución inteligente");
        ejecTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        ejecTitle.setForeground(TEXT_PRI);
        JLabel ejecSub = new JLabel("<html><div style='width:130px;color:#647290;font-size:10px'>"
            + "Automatiza, valida y entrega mejores experiencias.</div></html>");
        ejecTxt.add(ejecTitle);
        ejecTxt.add(ejecSub);
        ejecRow.add(purpleCircle);
        ejecRow.add(ejecTxt);
        bottomSection.add(ejecRow);

        // Info row
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        infoRow.setOpaque(false);
        infoRow.setBorder(new MatteBorder(1, 0, 0, 0, new Color(30, 45, 85)));
        JLabel infoIco = new JLabel("ⓘ");
        infoIco.setFont(new Font("Dialog", Font.BOLD, 14));
        infoIco.setForeground(ACCENT);
        JLabel infoTxt = new JLabel("INFORMACIÓN DEL ENTORNO");
        infoTxt.setFont(new Font("SansSerif", Font.BOLD, 10));
        infoTxt.setForeground(TEXT_DIM);
        infoRow.add(infoIco); infoRow.add(infoTxt);
        infoRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bottomSection.add(infoRow);

        // Version label
        JPanel verRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 7));
        verRow.setOpaque(false);
        verRow.setBorder(new MatteBorder(1, 0, 0, 0, new Color(30, 45, 85)));
        JLabel verLbl = new JLabel("Versión: 1.0.0");
        verLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        verLbl.setForeground(TEXT_DIM);
        verRow.add(verLbl);
        bottomSection.add(verRow);

        sidebar.add(topSection,    BorderLayout.CENTER);
        sidebar.add(bottomSection, BorderLayout.SOUTH);
        return sidebar;
    }

    private static RoundedPanel buildCountryRow(String flag, String code, String name) {
        RoundedPanel row = new RoundedPanel(10, BG_ROW);
        row.setLayout(new BorderLayout(0, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel left2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left2.setOpaque(false);
        ImageIcon countryLogo = loadCountryIcon(name, 32);
        if (countryLogo != null) {
            left2.add(new JLabel(countryLogo));
        } else {
            JLabel flagLbl = new JLabel(flag);
            flagLbl.setFont(new Font("Dialog", Font.PLAIN, 20));
            JLabel codeLbl = new JLabel(code);
            codeLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            codeLbl.setForeground(new Color(160, 175, 215));
            left2.add(flagLbl); left2.add(codeLbl);
        }
        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        nameLbl.setForeground(TEXT_PRI);
        left2.add(nameLbl);

        JLabel arrow = new JLabel(">");
        arrow.setFont(new Font("SansSerif", Font.BOLD, 14));
        arrow.setForeground(TEXT_DIM);

        row.add(left2, BorderLayout.CENTER);
        row.add(arrow, BorderLayout.EAST);

        countryRows.put(name, row);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (!running) selectCountry(name); }
            @Override public void mouseEntered(MouseEvent e) { if (!name.equals(selectedCountry)) row.setBg(BG_HOVER); }
            @Override public void mouseExited(MouseEvent e)  { if (!name.equals(selectedCountry)) row.setBg(BG_ROW); }
        });

        // Wrap row + gap in a transparent panel
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        wrap.add(row);
        wrap.add(Box.createRigidArea(new Dimension(0, 6)));

        // We add wrap to sidebar list, but return row for the map
        // To allow adding to the list properly, store wrap on row as client property
        row.putClientProperty("wrapper", wrap);
        return row;
    }

    private static void selectCountry(String name) {
        if (!selectedCountry.isEmpty()) {
            RoundedPanel prev = countryRows.get(selectedCountry);
            if (prev != null) prev.setBg(BG_ROW);
        }
        selectedCountry = name;
        System.setProperty("country", name);
        RoundedPanel sel = countryRows.get(name);
        if (sel != null) sel.setBg(BG_ROW_SEL);
        rightCardLayout.show(rightCardPanel, name);
    }

    // ── Right area ────────────────────────────────────────────────

    private static JPanel buildRightArea() {
        rightCardLayout = new CardLayout();
        rightCardPanel  = new JPanel(rightCardLayout);
        rightCardPanel.setBackground(BG_MAIN);

        rightCardPanel.add(buildWelcomePanel(),                                       "none");
        rightCardPanel.add(buildCountryPanel("México", mexicoCards()),               "México");
        rightCardPanel.add(buildAsientosDetailPanel(),                               "México-Asientos");
        rightCardPanel.add(buildFlujosDetailPanel(),                                 "México-E2E");
        rightCardPanel.add(buildAlimentosDetailPanel(),                              "México-Alimentos");
        rightCardPanel.add(buildCarritoDetailPanel(),                                "México-Carrito");
        rightCardPanel.add(buildCheckoutDetailPanel(),                               "México-Checkout");
        rightCardPanel.add(buildArgentinaPanel(),                                    "Argentina");
        rightCardPanel.add(buildChilePanel(),                                        "Chile");
        for (String[] c : COUNTRIES) {
            String n = c[2];
            if (!n.equals("México") && !n.equals("Argentina") && !n.equals("Chile"))
                rightCardPanel.add(buildComingSoonPanel(c[0], n), n);
        }
        rightCardLayout.show(rightCardPanel, "none");
        loadCustomSuites();

        JScrollPane topScroll = new JScrollPane(rightCardPanel);
        topScroll.setBorder(null);
        topScroll.setBackground(BG_MAIN);
        topScroll.getViewport().setBackground(BG_MAIN);
        topScroll.getVerticalScrollBar().setUnitIncrement(12);

        JSplitPane vSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topScroll, buildBottomArea());
        vSplit.setResizeWeight(0.58);
        vSplit.setDividerSize(4);
        vSplit.setBorder(null);
        vSplit.setUI(new BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setColor(BG_MAIN);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(BORDER);
                        g2.fillRect(0, getHeight() / 2, getWidth(), 1);
                        g2.dispose();
                    }
                };
            }
        });

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_MAIN);
        wrap.add(vSplit, BorderLayout.CENTER);
        return wrap;
    }

    private static JPanel buildWelcomePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_MAIN);
        JLabel lbl = new JLabel(
            "<html><div style='text-align:center;color:#6875a8'>"
            + "<span style='font-size:28px'>★</span><br/><br/>"
            + "<b style='font-size:15px;color:#dce1f0'>Cinépolis Automation QA</b><br/><br/>"
            + "Selecciona un país para ver las suites disponibles.</div></html>");
        p.add(lbl);
        return p;
    }

    private static JPanel buildComingSoonPanel(String flag, String name) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_MAIN);
        JLabel lbl = new JLabel(
            "<html><div style='text-align:center;color:#6875a8'>"
            + "<span style='font-size:36px'>" + flag + "</span><br/><br/>"
            + "<b style='font-size:14px;color:#dce1f0'>" + name + "</b><br/><br/>"
            + "Próximamente disponibles</div></html>");
        p.add(lbl);
        return p;
    }

    // ── México card definitions ────────────────────────────────────

    private static java.util.List<CardData> mexicoCards() {
        final String ASI = "tests.México.asientos.SeleccionAsientos";
        final String E2E = "tests.México.E2E.FlujosCompraNoLogin";
        return java.util.List.of(
            new CardData("🎬", new Color(37, 99, 235),
                "Flujo Completo",
                "Ejecuta el flujo completo de compra de boletos, alimentos y checkout.",
                new Color(37, 99, 235),
                () -> rightCardLayout.show(rightCardPanel, "México-E2E")),

            new CardData("💺", new Color(109, 40, 217),
                "Asientos",
                "Valida selección de asientos, disponibilidad y cambios de horario.",
                new Color(109, 40, 217),
                () -> rightCardLayout.show(rightCardPanel, "México-Asientos")),

            new CardData("🍿", new Color(194, 85, 17),
                "Alimentos",
                "Pruebas de combos, agregado al carrito y selección de productos.",
                new Color(194, 85, 17),
                () -> rightCardLayout.show(rightCardPanel, "México-Alimentos")),

            new CardData("🛒", new Color(22, 163, 74),
                "Carrito de Compras",
                "Valida productos en el carrito, códigos promocionales y resumen de compra.",
                new Color(22, 163, 74),
                () -> rightCardLayout.show(rightCardPanel, "México-Carrito")),

            new CardData("💳", new Color(13, 148, 136),
                "Checkout",
                "Valida el proceso de pago, métodos disponibles y confirmación de compra.",
                new Color(13, 148, 136),
                () -> rightCardLayout.show(rightCardPanel, "México-Checkout")),

            new CardData("🔍", new Color(202, 138, 4),
                "Smoke Tests",
                "Suite rápida para validar funcionalidades críticas de la aplicación.",
                new Color(202, 138, 4),
                () -> exec("Smoke Tests",
                    selectMethod(ASI, "seleccion1Asiento"),
                    selectMethod(E2E, "compraTicketTradicional")))
        );
    }

    private static JPanel buildCountryPanel(String name, java.util.List<CardData> cards) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_MAIN);
        outer.setBorder(new EmptyBorder(20, 20, 12, 20));

        // Section title
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setBorder(new EmptyBorder(0, 0, 14, 0));
        JLabel gridIco = new JLabel("☷");
        gridIco.setFont(new Font("Dialog", Font.PLAIN, 14));
        gridIco.setForeground(TEXT_DIM);
        JLabel titleLbl = new JLabel("SELECCIONA LA PRUEBA QUE DESEAS EJECUTAR");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_DIM);
        titleRow.add(gridIco); titleRow.add(titleLbl);

        JPanel grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 18, 18));
        grid.setOpaque(false);
        for (CardData cd : cards) grid.add(buildCard(cd));
        if ("México".equals(name)) mexicoGrid = grid;

        outer.add(titleRow, BorderLayout.NORTH);
        outer.add(grid,     BorderLayout.CENTER);
        return outer;
    }

    // ── Generic detail panel builder ──────────────────────────────

    private static JPanel buildDetailPanel(String title, String icon, Color accent,
                                           Runnable runAll, java.util.List<TestRow> rows) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_MAIN);
        outer.setBorder(new EmptyBorder(16, 20, 10, 20));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel leftHdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftHdr.setOpaque(false);

        RoundedButton backBtn = new RoundedButton("← Volver", BG_CARD, 6);
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        backBtn.setForeground(TEXT_PRI);
        backBtn.setPreferredSize(new Dimension(88, 26));
        backBtn.addActionListener(e -> rightCardLayout.show(rightCardPanel, "México"));

        JLabel titleLbl = new JLabel(icon + "  " + title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_DIM);
        leftHdr.add(backBtn); leftHdr.add(titleLbl);

        RoundedButton runAllBtn = new RoundedButton("▶  Ejecutar Todos", accent, 6);
        runAllBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        runAllBtn.setForeground(Color.WHITE);
        runAllBtn.setPreferredSize(new Dimension(150, 26));
        runAllBtn.addActionListener(e -> { if (!running) runAll.run(); });
        testButtons.add(runAllBtn);

        header.add(leftHdr,   BorderLayout.WEST);
        header.add(runAllBtn, BorderLayout.EAST);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(BG_MAIN);

        for (int i = 0; i < rows.size(); i++) {
            TestRow  r      = rows.get(i);
            int      num    = i + 1;
            Runnable action = r.action();

            RoundedPanel row = new RoundedPanel(8, BG_CARD);
            row.setLayout(new BorderLayout(10, 0));
            row.setBorder(new EmptyBorder(8, 12, 8, 12));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            CircleIcon numIco = new CircleIcon(String.valueOf(num), accent, 28);
            numIco.setOpaque(false);

            JPanel textBox = new JPanel();
            textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
            textBox.setOpaque(false);
            JLabel nameLbl = new JLabel(r.label());
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            nameLbl.setForeground(TEXT_PRI);
            textBox.add(nameLbl);
            if (r.description() != null && !r.description().isBlank()) {
                JLabel descLbl = new JLabel(r.description());
                descLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
                descLbl.setForeground(TEXT_DIM);
                textBox.add(descLbl);
            }

            RoundedButton execBtn = new RoundedButton("▶  Ejecutar", accent, 5);
            execBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
            execBtn.setForeground(Color.WHITE);
            execBtn.setPreferredSize(new Dimension(105, 26));
            execBtn.addActionListener(e -> { if (!running) action.run(); });
            testButtons.add(execBtn);

            row.add(numIco,  BorderLayout.WEST);
            row.add(textBox, BorderLayout.CENTER);
            row.add(execBtn, BorderLayout.EAST);
            list.add(row);
            list.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setBackground(BG_MAIN);
        scroll.getViewport().setBackground(BG_MAIN);
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        outer.add(header, BorderLayout.NORTH);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // ── Asientos detail panel ─────────────────────────────────────

    private static JPanel buildAsientosDetailPanel() {
        final String ASI    = "tests.México.asientos.SeleccionAsientos";
        final Color  PURPLE = new Color(109, 40, 217);
        return buildDetailPanel("ASIENTOS – TESTS INDIVIDUALES", "💺", PURPLE,
            () -> exec("Asientos – Todos", selectClass(ASI)),
            java.util.List.of(
                new TestRow("Selección de 1 Asiento",       "Selecciona un asiento disponible y continúa",     () -> exec("1 Asiento",          selectMethod(ASI, "seleccion1Asiento"))),
                new TestRow("Múltiples Asientos",           "Selecciona 3 asientos disponibles y continúa",   () -> exec("Múltiples",           selectMethod(ASI, "seleccionMultiplesAsientos"))),
                new TestRow("Asientos Consecutivos",        "Selecciona 3 asientos consecutivos",              () -> exec("Consecutivos",        selectMethod(ASI, "seleccionAsientosConsecutivos"))),
                new TestRow("Selección y Deselección",      "Selecciona y deselecciona 3 asientos",            () -> exec("Sel/Desel",           selectMethod(ASI, "seleccionAsientosYDeseleccion"))),
                new TestRow("Más de 10 Asientos",           "Selecciona más de 10 y valida alerta",            () -> exec("Más de 10",           selectMethod(ASI, "seleccion11Asientos"))),
                new TestRow("Cambio de Horario",            "Cambia el horario en el mapa de asientos",        () -> exec("Cambio Horario",      selectMethod(ASI, "cambioHorarioAsientos"))),
                new TestRow("Asientos 3D",                  "Verifica el banner en sala 3D",                   () -> exec("3D",                  selectMethod(ASI, "asientos3D"))),
                new TestRow("Alerta Asiento Especial",      "Valida la alerta al seleccionar asiento especial",() -> exec("Asiento Especial",    selectMethod(ASI, "alertaAsientoEspecial"))),
                new TestRow("Sala Junior",                  "Verifica el banner en Sala Junior",               () -> exec("Sala Junior",         selectMethod(ASI, "asientosSalaJunior")))
            ));
    }

    // ── Flujo Completo detail panel ───────────────────────────────

    private static JPanel buildFlujosDetailPanel() {
        final String E2E  = "tests.México.E2E.FlujosCompraNoLogin";
        final Color  BLUE = new Color(37, 99, 235);
        return buildDetailPanel("FLUJO COMPLETO – E2E", "🎬", BLUE,
            () -> exec("Flujo Completo – Todos", selectClass(E2E)),
            java.util.List.of(
                new TestRow("Compra ticket – Tradicional",  "Sin sesión", () -> exec("Ticket Tradicional",    selectMethod(E2E, "compraTicketTradicional"))),
                new TestRow("Compra mix – Tradicional",     "Sin sesión", () -> exec("Mix Tradicional",       selectMethod(E2E, "compraMixTradicional"))),
                new TestRow("Compra alimento – Tradicional","Sin sesión", () -> exec("Alimento Tradicional",  selectMethod(E2E, "compraAlimentoTradicional"))),
                new TestRow("Compra ticket – Atmósfera",    "Sin sesión", () -> exec("Ticket Atmósfera",      selectMethod(E2E, "compraTicketAtmosfera"))),
                new TestRow("Compra mix – Atmósfera",       "Sin sesión", () -> exec("Mix Atmósfera",         selectMethod(E2E, "compraMixAtmosfera"))),
                new TestRow("Compra alimento – Atmósfera",  "Sin sesión", () -> exec("Alimento Atmósfera",    selectMethod(E2E, "compraAlimentoAtmosfera"))),
                new TestRow("Compra ticket – VIP",          "Sin sesión", () -> exec("Ticket VIP",            selectMethod(E2E, "compraTicketVIP"))),
                new TestRow("Compra mix – VIP",             "Sin sesión", () -> exec("Mix VIP",               selectMethod(E2E, "compraMixVIP"))),
                new TestRow("Compra alimento – VIP",        "Sin sesión", () -> exec("Alimento VIP",          selectMethod(E2E, "compraAlimentoVIP")))
            ));
    }

    // ── Alimentos detail panel ────────────────────────────────────

    private static JPanel buildAlimentosDetailPanel() {
        final Color ORANGE = new Color(194, 85, 17);
        return buildDetailPanel("ALIMENTOS – MENÚS", "🍿", ORANGE,
            () -> exec("Alimentos – Todos", selectPackage("tests.México.alimentos")),
            java.util.List.of(
                new TestRow("Menú Tradicional", "50 tests · combos y palomitas", () -> exec("Menú Tradicional", selectClass("tests.México.alimentos.MenuTradicional"))),
                new TestRow("Menú Atmósfera",   "3 tests · creps y frappés",     () -> exec("Menú Atmósfera",   selectClass("tests.México.alimentos.MenuAtmosfera"))),
                new TestRow("Menú VIP",         "2 tests · palomitas VIP",        () -> exec("Menú VIP",         selectClass("tests.México.alimentos.MenuVIP"))),
                new TestRow("Coffee Tree",      "50 tests · bebidas calientes",   () -> exec("Coffee Tree",      selectClass("tests.México.alimentos.MenuCoffeTree"))),
                new TestRow("Mi Cine",          "50 tests · combos Mi Cine",      () -> exec("Mi Cine",          selectClass("tests.México.alimentos.MenuMiCine")))
            ));
    }

    // ── Carrito detail panel ──────────────────────────────────────

    private static JPanel buildCarritoDetailPanel() {
        final String E2E   = "tests.México.E2E.FlujosCompraNoLogin";
        final Color  GREEN = new Color(22, 163, 74);
        return buildDetailPanel("CARRITO DE COMPRAS", "🛒", GREEN,
            () -> exec("Carrito – Todos",
                selectMethod(E2E, "compraMixTradicional"),
                selectMethod(E2E, "compraMixAtmosfera"),
                selectMethod(E2E, "compraMixVIP")),
            java.util.List.of(
                new TestRow("Compra mix – Tradicional", "Sin sesión", () -> exec("Mix Tradicional", selectMethod(E2E, "compraMixTradicional"))),
                new TestRow("Compra mix – Atmósfera",   "Sin sesión", () -> exec("Mix Atmósfera",   selectMethod(E2E, "compraMixAtmosfera"))),
                new TestRow("Compra mix – VIP",         "Sin sesión", () -> exec("Mix VIP",          selectMethod(E2E, "compraMixVIP")))
            ));
    }

    // ── Checkout detail panel ─────────────────────────────────────

    private static JPanel buildCheckoutDetailPanel() {
        final String E2E  = "tests.México.E2E.FlujosCompraNoLogin";
        final Color  TEAL = new Color(13, 148, 136);
        return buildDetailPanel("CHECKOUT – PAGO", "💳", TEAL,
            () -> exec("Checkout – Todos",
                selectMethod(E2E, "compraTicketTradicional"),
                selectMethod(E2E, "compraTicketAtmosfera"),
                selectMethod(E2E, "compraTicketVIP")),
            java.util.List.of(
                new TestRow("Compra ticket – Tradicional", "Sin sesión", () -> exec("Ticket Tradicional", selectMethod(E2E, "compraTicketTradicional"))),
                new TestRow("Compra ticket – Atmósfera",   "Sin sesión", () -> exec("Ticket Atmósfera",   selectMethod(E2E, "compraTicketAtmosfera"))),
                new TestRow("Compra ticket – VIP",         "Sin sesión", () -> exec("Ticket VIP",          selectMethod(E2E, "compraTicketVIP")))
            ));
    }

    private static JPanel buildArgentinaPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_MAIN);

        CardLayout innerLayout = new CardLayout();
        JPanel innerPanel = new JPanel(innerLayout);
        innerPanel.setBackground(BG_MAIN);

        // ── Vista de ciudades ─────────────────────────────────────
        JPanel citiesOuter = new JPanel(new BorderLayout());
        citiesOuter.setBackground(BG_MAIN);
        citiesOuter.setBorder(new EmptyBorder(20, 20, 12, 20));

        JPanel citiesTitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        citiesTitleRow.setOpaque(false);
        citiesTitleRow.setBorder(new EmptyBorder(0, 0, 14, 0));
        JLabel citiesTitleLbl = new JLabel("☷  SELECCIONA UNA CIUDAD");
        citiesTitleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        citiesTitleLbl.setForeground(TEXT_DIM);
        citiesTitleRow.add(citiesTitleLbl);

        JPanel citiesGrid = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
        citiesGrid.setOpaque(false);
        ARGENTINA_CITIES.forEach((city, cinemas) -> {
            String abbr = city.substring(0, Math.min(2, city.length())).toUpperCase();
            int n = cinemas.length;
            citiesGrid.add(buildCard(new CardData(abbr, ACCENT, city,
                n + " cine" + (n > 1 ? "s" : "") + " disponible" + (n > 1 ? "s" : ""), ACCENT,
                () -> innerLayout.show(innerPanel, "city_" + city))));
        });
        citiesGrid.add(buildCard(new CardData("▶▶", new Color(22, 163, 74),
            "Suite Completa", "Todos los cines de Argentina", new Color(22, 163, 74),
            () -> exec("Suite Completa Argentina", selectClass("tests.Argentina.NoAfectacionArgentina")))));

        citiesOuter.add(citiesTitleRow, BorderLayout.NORTH);
        citiesOuter.add(citiesGrid,     BorderLayout.CENTER);
        innerPanel.add(citiesOuter, "cities");

        // ── Vista de cines por ciudad ─────────────────────────────
        ARGENTINA_CITIES.forEach((city, cinemas) -> {
            JPanel cityOuter = new JPanel(new BorderLayout());
            cityOuter.setBackground(BG_MAIN);
            cityOuter.setBorder(new EmptyBorder(20, 20, 12, 20));

            // Header: ← Volver + título de ciudad
            JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            headerRow.setOpaque(false);
            headerRow.setBorder(new EmptyBorder(0, 0, 14, 0));

            RoundedButton backBtn = new RoundedButton("← Volver", BG_CARD, 6);
            backBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            backBtn.setForeground(TEXT_DIM);
            backBtn.setPreferredSize(new Dimension(88, 26));
            backBtn.addActionListener(e -> innerLayout.show(innerPanel, "cities"));

            JLabel cityTitleLbl = new JLabel("☷  " + city.toUpperCase() + " — SELECCIONA UN CINE");
            cityTitleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            cityTitleLbl.setForeground(TEXT_DIM);
            headerRow.add(backBtn);
            headerRow.add(cityTitleLbl);

            // Grid de cines individuales
            JPanel cinesGrid = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
            cinesGrid.setOpaque(false);

            for (String cinema : cinemas) {
                String displayName = cinema.replace("Cinépolis ", "").replace("Cinepolis ", "");
                String abbr = displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
                String sfx = ARGENTINA_CINEMA_SUFFIX.getOrDefault(cinema, "");
                cinesGrid.add(buildCard(new CardData(abbr, ACCENT, displayName, city, ACCENT,
                    () -> exec(cinema,
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraTicket" + sfx),
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraMix"    + sfx),
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraFood"   + sfx)))));
            }

            // Card "Ejecutar Todos" para esta ciudad
            cinesGrid.add(buildCard(new CardData("▶▶", new Color(22, 163, 74),
                "Ejecutar Todos", "Todos los cines de " + city, new Color(22, 163, 74),
                () -> exec("Todos - " + city, argCitySelectors(city)))));

            JScrollPane cinesScroll = new JScrollPane(cinesGrid);
            cinesScroll.setBorder(null);
            cinesScroll.setBackground(BG_MAIN);
            cinesScroll.getViewport().setBackground(BG_MAIN);
            cinesScroll.getVerticalScrollBar().setUnitIncrement(14);

            cityOuter.add(headerRow,  BorderLayout.NORTH);
            cityOuter.add(cinesScroll, BorderLayout.CENTER);
            innerPanel.add(cityOuter, "city_" + city);
        });

        outer.add(innerPanel, BorderLayout.CENTER);
        return outer;
    }

    // ── Chile panel ───────────────────────────────────────────────

    private static JPanel buildChilePanel() {
        final String CHILE_CLS = "tests.Chile.NoAfectacionChile";

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_MAIN);
        outer.setBorder(new EmptyBorder(20, 20, 12, 20));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setBorder(new EmptyBorder(0, 0, 14, 0));
        JLabel titleLbl = new JLabel("☷  SELECCIONA UN CINE – CHILE");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_DIM);
        titleRow.add(titleLbl);

        JPanel grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 18, 18));
        grid.setOpaque(false);

        for (String[] cine : CHILE_CINES) {
            String name = cine[0], type = cine[1], sfx = cine[2];
            String abbr = name.substring(0, Math.min(2, name.length())).toUpperCase();
            grid.add(buildCard(new CardData(abbr, ACCENT, name, type, ACCENT,
                () -> exec(name,
                    selectMethod(CHILE_CLS, "compraTicket"   + sfx),
                    selectMethod(CHILE_CLS, "compraMix"      + sfx),
                    selectMethod(CHILE_CLS, "compraAlimento" + sfx)))));
        }

        grid.add(buildCard(new CardData("▶▶", new Color(22, 163, 74),
            "Suite Completa", "Todos los cines de Chile", new Color(22, 163, 74),
            () -> exec("Suite Completa Chile", selectClass(CHILE_CLS)))));

        outer.add(titleRow, BorderLayout.NORTH);
        outer.add(grid,     BorderLayout.CENTER);
        return outer;
    }

    // ── Helper: selectors para todos los cines de una ciudad (Argentina) ──

    private static DiscoverySelector[] argCitySelectors(String city) {
        final String cls = "tests.Argentina.NoAfectacionArgentina";
        java.util.List<DiscoverySelector> list = new ArrayList<>();
        String[] cinemas = ARGENTINA_CITIES.get(city);
        if (cinemas != null) {
            for (String cinema : cinemas) {
                String sfx = ARGENTINA_CINEMA_SUFFIX.getOrDefault(cinema, "");
                if (!sfx.isEmpty()) {
                    list.add(selectMethod(cls, "compraTicket" + sfx));
                    list.add(selectMethod(cls, "compraMix"    + sfx));
                    list.add(selectMethod(cls, "compraFood"   + sfx));
                }
            }
        }
        return list.toArray(new DiscoverySelector[0]);
    }

    // ── Card ──────────────────────────────────────────────────────

    private static JPanel buildCard(CardData data) {
        Color accent = data.iconColor();
        boolean[] hov = {false};

        RoundedPanel card = new RoundedPanel(22, BG_CARD) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int r = accent.getRed(), gr = accent.getGreen(), b = accent.getBlue();

                // Lower-left radial glow (brighter on hover)
                float rad = w * (hov[0] ? 0.70f : 0.55f);
                RadialGradientPaint glow = new RadialGradientPaint(
                    new Point2D.Float(w * 0.28f, h * 0.80f), rad,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(r, gr, b, hov[0] ? 85 : 55),
                                new Color(r, gr, b, hov[0] ? 32 : 18),
                                new Color(r, gr, b, 0)});
                g2.setPaint(glow);
                g2.fillRect(0, h / 2, w, h / 2 + 2);

                // Top-left illumination on hover
                if (hov[0]) {
                    RadialGradientPaint topGlow = new RadialGradientPaint(
                        new Point2D.Float(w * 0.08f, h * 0.08f), w * 0.55f,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(r, gr, b, 50), new Color(r, gr, b, 18), new Color(r, gr, b, 0)});
                    g2.setPaint(topGlow);
                    g2.fillRect(0, 0, w, h / 2);
                }

                // Filled wave decoration at bottom
                float ws = h * 0.58f;
                float wa = h - ws;
                // Layer 1 — broadest, most transparent
                Path2D.Float wv1 = new Path2D.Float();
                wv1.moveTo(0, ws + wa * 0.30f);
                wv1.curveTo(w * 0.25f, ws,
                            w * 0.65f, ws + wa * 0.42f,
                            w,         ws + wa * 0.14f);
                wv1.lineTo(w, h); wv1.lineTo(0, h); wv1.closePath();
                g2.setColor(new Color(r, gr, b, hov[0] ? 38 : 26));
                g2.fill(wv1);
                // Layer 2 — middle
                Path2D.Float wv2 = new Path2D.Float();
                wv2.moveTo(0, ws + wa * 0.52f);
                wv2.curveTo(w * 0.30f, ws + wa * 0.20f,
                            w * 0.60f, ws + wa * 0.56f,
                            w,         ws + wa * 0.38f);
                wv2.lineTo(w, h); wv2.lineTo(0, h); wv2.closePath();
                g2.setColor(new Color(r, gr, b, hov[0] ? 58 : 44));
                g2.fill(wv2);
                // Layer 3 — highest, most opaque
                Path2D.Float wv3 = new Path2D.Float();
                wv3.moveTo(0, ws + wa * 0.70f);
                wv3.curveTo(w * 0.22f, ws + wa * 0.44f,
                            w * 0.68f, ws + wa * 0.74f,
                            w,         ws + wa * 0.60f);
                wv3.lineTo(w, h); wv3.lineTo(0, h); wv3.closePath();
                g2.setColor(new Color(r, gr, b, hov[0] ? 80 : 64));
                g2.fill(wv3);

                // Glowing border on hover
                if (hov[0]) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(new Color(r, gr, b, 130));
                    g2.drawRoundRect(1, 1, w - 2, h - 2, 44, 44);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(new Color(r, gr, b, 55));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 44, 44);
                }

                g2.dispose();
            }
        };
        card.setLayout(new BorderLayout(0, 0));
        card.setPreferredSize(new Dimension(365, 215));
        card.setBorder(new EmptyBorder(16, 20, 14, 20));

        // MAIN: horizontal — icon LEFT, text RIGHT
        JPanel mainSection = new JPanel(new BorderLayout(14, 0));
        mainSection.setOpaque(false);

        CircleIcon ico = new CircleIcon(data.icon(), accent, 64);
        ico.setOpaque(false);

        JPanel textBox = new JPanel();
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
        textBox.setOpaque(false);
        JLabel titleLbl = new JLabel(data.title());
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(TEXT_PRI);
        JLabel descLbl = new JLabel("<html><div style='width:215px'>" + data.description() + "</div></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLbl.setForeground(TEXT_DIM);
        descLbl.setBorder(new EmptyBorder(5, 0, 0, 0));
        textBox.add(titleLbl);
        textBox.add(descLbl);

        mainSection.add(ico,     BorderLayout.WEST);
        mainSection.add(textBox, BorderLayout.CENTER);

        // BOTTOM: full-width Ejecutar button
        RoundedButton execBtn = new RoundedButton("▶  Ejecutar", data.btnColor(), 12);
        execBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        execBtn.setForeground(Color.WHITE);
        execBtn.addActionListener(e -> { if (!running) data.action().run(); });
        testButtons.add(execBtn);

        execBtn.setPreferredSize(new Dimension(138, 32));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(10, 0, 0, 0));
        btnRow.add(execBtn);

        card.add(mainSection, BorderLayout.CENTER);
        card.add(btnRow,      BorderLayout.SOUTH);

        // Hover listener propagated to all non-interactive children to avoid dead zones
        MouseAdapter cardHover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hov[0] = true; card.repaint(); }
            @Override public void mouseExited(MouseEvent e) {
                Point pt = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), card);
                if (!card.contains(pt)) { hov[0] = false; card.repaint(); }
            }
        };
        card.addMouseListener(cardHover);
        mainSection.addMouseListener(cardHover);
        titleLbl.addMouseListener(cardHover);
        descLbl.addMouseListener(cardHover);
        textBox.addMouseListener(cardHover);
        ico.addMouseListener(cardHover);
        btnRow.addMouseListener(cardHover);

        moduleCards.put(normalize(data.title()), card);
        moduleDisplayNames.put(normalize(data.title()), data.title());
        return card;
    }

    // ── Bottom area ───────────────────────────────────────────────

    private static JPanel buildBottomArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(BG_MAIN);
        area.add(buildSummaryBar(),   BorderLayout.NORTH);
        area.add(buildTabbedBottom(), BorderLayout.CENTER);
        return area;
    }

    private static JPanel buildTabbedBottom() {
        CardLayout cards = new CardLayout();
        JPanel cardPanel = new JPanel(cards);
        cardPanel.setBackground(BG_MAIN);
        cardPanel.add(buildLogPanel(),  "log");
        cardPanel.add(buildChatPanel(), "ia");

        // ── Tab bar ───────────────────────────────────────────────
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(new Color(7, 11, 26));
        tabBar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(28, 42, 75)));

        JPanel logTab = makeTab(">_  LOG DE EJECUCIÓN");
        JPanel iaTab  = makeTab("🤖  ASISTENTE IA");
        tabBar.add(logTab);
        tabBar.add(iaTab);

        // initial state: LOG activo
        setTabActive(logTab, true);
        setTabActive(iaTab,  false);

        logTab.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                cards.show(cardPanel, "log");
                setTabActive(logTab, true); setTabActive(iaTab, false);
            }
        });
        iaTab.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                cards.show(cardPanel, "ia");
                setTabActive(iaTab, true); setTabActive(logTab, false);
            }
        });

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_MAIN);
        wrap.add(tabBar,    BorderLayout.NORTH);
        wrap.add(cardPanel, BorderLayout.CENTER);
        return wrap;
    }

    private static JPanel makeTab(String text) {
        JPanel tab = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        tab.setOpaque(true);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        tab.add(lbl);
        tab.putClientProperty("lbl", lbl);
        return tab;
    }

    private static void setTabActive(JPanel tab, boolean active) {
        JLabel lbl = (JLabel) tab.getClientProperty("lbl");
        tab.setBackground(active ? new Color(4, 8, 22) : new Color(7, 11, 26));
        if (lbl != null) lbl.setForeground(active ? TEXT_PRI : TEXT_DIM);
        tab.setBorder(active
            ? new MatteBorder(0, 0, 2, 0, ACCENT)
            : new EmptyBorder(0, 0, 2, 0));
    }

    // ── Chat panel ────────────────────────────────────────────────

    private static JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_MAIN);
        panel.setBorder(new EmptyBorder(6, 14, 8, 14));

        // ── conversation area ─────────────────────────────────────
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(6, 8, 18));
        chatPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatPane.setForeground(TEXT_PRI);
        chatPane.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        // ── input row ─────────────────────────────────────────────
        chatInput = new JTextField();
        chatInput.setBackground(BG_CARD);
        chatInput.setForeground(TEXT_PRI);
        chatInput.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatInput.setCaretColor(TEXT_PRI);
        chatInput.setToolTipText("Escribe tu pregunta y presiona Enter...");
        chatInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 75, 130)),
            new EmptyBorder(6, 10, 6, 10)));
        chatInput.addActionListener(e -> sendChatMessage());

        RoundedButton sendBtn = new RoundedButton("Enviar ▶", ACCENT, 6);
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setPreferredSize(new Dimension(105, 34));
        sendBtn.addActionListener(e -> sendChatMessage());

        RoundedButton attachBtn = new RoundedButton("📎", BG_CARD, 6);
        attachBtn.setFont(new Font("Dialog", Font.PLAIN, 16));
        attachBtn.setForeground(TEXT_DIM);
        attachBtn.setPreferredSize(new Dimension(38, 34));
        attachBtn.setToolTipText("Adjuntar imagen o archivo (png, jpg, gif, webp, txt, json, log)");
        attachBtn.addActionListener(e -> chooseAttachment());

        attachLabel = new JLabel();
        attachLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        attachLabel.setForeground(COLOR_SKIP);
        attachLabel.setBorder(new EmptyBorder(2, 2, 0, 0));
        attachLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        attachLabel.setToolTipText("Clic para quitar el archivo adjunto");
        attachLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                pendingAttachment = null;
                attachLabel.setText("");
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.add(attachBtn, BorderLayout.WEST);
        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(sendBtn,   BorderLayout.EAST);

        JPanel bottomArea = new JPanel(new BorderLayout());
        bottomArea.setOpaque(false);
        bottomArea.setBorder(new EmptyBorder(4, 0, 0, 0));
        bottomArea.add(attachLabel, BorderLayout.NORTH);
        bottomArea.add(inputRow,    BorderLayout.CENTER);

        // ── model info bar ────────────────────────────────────────
        JLabel modelLbl = new JLabel("Modelo: claude-haiku-4-5 · Configura tu API key en ⚙");
        modelLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        modelLbl.setForeground(TEXT_DIM);
        modelLbl.setBorder(new EmptyBorder(0, 2, 4, 0));

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(modelLbl, BorderLayout.NORTH);
        content.add(scroll,      BorderLayout.CENTER);
        content.add(bottomArea,  BorderLayout.SOUTH);

        panel.add(content, BorderLayout.CENTER);

        // welcome message
        SwingUtilities.invokeLater(() -> appendChat("🤖 Asistente",
            "Hola! Soy tu asistente de QA para Cinépolis.\n"
            + "Puedo analizar logs de Appium, diagnosticar fallos y responder preguntas sobre la automatización.\n"
            + "Configura tu API key de Anthropic en ⚙ para comenzar.",
            BLUE_TITLE));
        return panel;
    }

    // ── Send / API ────────────────────────────────────────────────

    private static void sendChatMessage() {
        String msg = chatInput.getText().trim();
        if (msg.isBlank()) return;

        Properties props = readAppiumProps();
        String apiKey = System.getProperty("claude.api.key", props.getProperty("claude.api.key", ""));
        if (apiKey.isBlank()) {
            appendChat("⚙ Sistema",
                "No hay API key configurada. Abre ⚙ → sección ASISTENTE IA y pega tu Anthropic API key.",
                COLOR_SKIP);
            return;
        }

        File attachment = pendingAttachment;
        pendingAttachment = null;
        if (attachLabel != null) attachLabel.setText("");

        chatInput.setText("");
        chatInput.setEnabled(false);
        String displayMsg = (attachment != null)
            ? msg + "\n[📎 " + attachment.getName() + "]"
            : msg;
        appendChat("Tú", displayMsg, new Color(147, 197, 253));

        // Context: log + stats + device
        String logText = (logPane != null) ? logPane.getText() : "";
        if (logText.length() > 3000)
            logText = "[...truncado...]\n" + logText.substring(logText.length() - 3000);

        String context = String.format(
            "Dispositivo: %s | Hub: %s\nResultados: ✓ %d PASSED  ✗ %d FAILED  ▶▶ %d SKIPPED  Total: %d\n\nLog:\n%s",
            System.getProperty("deviceName",      props.getProperty("deviceName", "N/A")),
            System.getProperty("appium.hub",      props.getProperty("appium.hub", "N/A")),
            statPassed, statFailed, statSkipped, statTotal, logText);

        chatHistory.add(new org.json.JSONObject().put("role", "user").put("content", msg));
        if (chatHistory.size() > 20) chatHistory.remove(0);

        String finalKey = apiKey, finalCtx = context;
        File finalAttachment = attachment;
        new Thread(() -> {
            try {
                String reply = callClaudeApiWithTools(finalKey, finalCtx, finalAttachment);
                chatHistory.add(new org.json.JSONObject().put("role", "assistant").put("content", reply));
                if (chatHistory.size() > 20) chatHistory.remove(0);
                SwingUtilities.invokeLater(() -> {
                    appendChat("🤖 Asistente", reply, COLOR_OK);
                    chatInput.setEnabled(true);
                    chatInput.requestFocus();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendChat("✗ Error", ex.getMessage(), COLOR_FAIL);
                    chatInput.setEnabled(true);
                });
            }
        }, "claude-api").start();
    }

    private static String callClaudeApi(String apiKey, String context) throws Exception {
        org.json.JSONArray messages = new org.json.JSONArray();
        for (org.json.JSONObject m : chatHistory) messages.put(m);

        String body = new org.json.JSONObject()
            .put("model",      "claude-haiku-4-5-20251001")
            .put("max_tokens", 1024)
            .put("system",
                "Eres un asistente de QA especializado en automatización con Appium para la app móvil de Cinépolis. "
                + "Ayudas a analizar logs, diagnosticar fallos de tests y sugerir soluciones. "
                + "Responde siempre en español, de forma concisa y técnica.\n\nContexto del sistema:\n" + context)
            .put("messages", messages)
            .toString();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key",          apiKey)
            .header("anthropic-version",  "2023-06-01")
            .header("content-type",       "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new RuntimeException("HTTP " + response.statusCode() + " — " + response.body());

        return new org.json.JSONObject(response.body())
            .getJSONArray("content").getJSONObject(0).getString("text");
    }

    private static void appendChat(String sender, String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = chatPane.getStyledDocument();
                SimpleAttributeSet senderStyle = new SimpleAttributeSet();
                StyleConstants.setBold(senderStyle, true);
                StyleConstants.setForeground(senderStyle, color);
                SimpleAttributeSet msgStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(msgStyle, TEXT_PRI);
                doc.insertString(doc.getLength(), sender + "\n", senderStyle);
                doc.insertString(doc.getLength(), message + "\n\n", msgStyle);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {}
        });
    }

    // ── Tool Use: crear suite dinámica ────────────────────────────

    private static String callClaudeApiWithTools(String apiKey, String context, File attachment) throws Exception {
        // ── Tool: crear_suite ─────────────────────────────────────
        org.json.JSONObject crearSuiteTool = new org.json.JSONObject()
            .put("name", "crear_suite")
            .put("description",
                "Crea una nueva suite de pruebas y la agrega al menú del launcher para México. "
                + "Úsalo cuando el usuario pida crear, agregar o registrar una nueva suite o flujo de pruebas.")
            .put("input_schema", new org.json.JSONObject()
                .put("type", "object")
                .put("properties", new org.json.JSONObject()
                    .put("nombre",       new org.json.JSONObject().put("type","string").put("description","Nombre de la suite, ej: 'Login'"))
                    .put("descripcion",  new org.json.JSONObject().put("type","string").put("description","Descripción breve de la suite"))
                    .put("icono",        new org.json.JSONObject().put("type","string").put("description","Emoji representativo, ej: '🔑'"))
                    .put("color",        new org.json.JSONObject().put("type","string").put("description","Color hex, ej: '#7C3AED'"))
                    .put("clase_prueba", new org.json.JSONObject().put("type","string").put("description","Clase Java completa, ej: 'tests.México.login.LoginTests'"))
                    .put("pruebas", new org.json.JSONObject()
                        .put("type","array")
                        .put("description","Lista de tests individuales")
                        .put("items", new org.json.JSONObject()
                            .put("type","object")
                            .put("properties", new org.json.JSONObject()
                                .put("etiqueta",    new org.json.JSONObject().put("type","string").put("description","Nombre del test"))
                                .put("descripcion", new org.json.JSONObject().put("type","string").put("description","Descripción corta"))
                                .put("metodo",      new org.json.JSONObject().put("type","string").put("description","Nombre del método Java, camelCase")))
                            .put("required", new org.json.JSONArray().put("etiqueta").put("metodo")))))
                .put("required", new org.json.JSONArray().put("nombre").put("descripcion").put("clase_prueba").put("pruebas")));

        // ── Tool: ocultar_modulo ──────────────────────────────────
        org.json.JSONObject ocultarModuloTool = new org.json.JSONObject()
            .put("name", "ocultar_modulo")
            .put("description",
                "Oculta visualmente un módulo o suite del menú del launcher de México de forma inmediata. "
                + "El código NO se elimina; solo desaparece de la UI. Para restaurarlo hay que reiniciar el launcher. "
                + "Úsalo cuando el usuario pida eliminar, ocultar, quitar o remover visualmente un módulo del menú.")
            .put("input_schema", new org.json.JSONObject()
                .put("type", "object")
                .put("properties", new org.json.JSONObject()
                    .put("nombre_modulo", new org.json.JSONObject()
                        .put("type", "string")
                        .put("description",
                            "Nombre del módulo a ocultar. Módulos disponibles en México: "
                            + "Flujo Completo, Asientos, Alimentos, Carrito de Compras, Checkout, Smoke Tests. "
                            + "También puede ser el nombre de una suite personalizada creada con crear_suite.")))
                .put("required", new org.json.JSONArray().put("nombre_modulo")));

        org.json.JSONArray toolsArray = new org.json.JSONArray()
            .put(crearSuiteTool)
            .put(ocultarModuloTool);

        String systemPrompt =
            "Eres un asistente de QA especializado en automatización con Appium para la app móvil de Cinépolis. "
            + "Tienes dos herramientas disponibles:\n"
            + "  1. 'crear_suite': crea y agrega nuevas suites de prueba al menú del launcher.\n"
            + "  2. 'ocultar_modulo': oculta visualmente un módulo del menú de forma inmediata (sin borrar código).\n"
            + "Módulos ACTUALMENTE visibles en el menú: "
            + (moduleDisplayNames.isEmpty() ? "(ninguno)" : String.join(", ", moduleDisplayNames.values())) + ".\n"
            + "IMPORTANTE: si el usuario pide ocultar un módulo que aparece en esa lista, SIEMPRE llama la herramienta 'ocultar_modulo' — nunca respondas que ya fue eliminado sin llamarla.\n"
            + "Cuando el usuario pida crear un flujo/suite, usa 'crear_suite'. "
            + "Cuando pida eliminar, ocultar, quitar o remover un módulo del menú, usa 'ocultar_modulo'. "
            + "Ayudas también a analizar logs, diagnosticar fallos y sugerir soluciones. "
            + "Responde siempre en español, de forma concisa y técnica.\n\nContexto del sistema:\n" + context;

        org.json.JSONArray messages = new org.json.JSONArray();
        int histSize = chatHistory.size();
        for (int i = 0; i < histSize - 1; i++) messages.put(chatHistory.get(i));

        // Last user message: inject image or file content if present
        org.json.JSONObject lastMsg = chatHistory.get(histSize - 1);
        if (attachment != null && attachment.exists()) {
            String mediaType = detectMediaType(attachment.getName());
            if (mediaType != null) {
                // Vision: encode image as base64
                byte[] imgBytes;
                try (FileInputStream fis = new FileInputStream(attachment)) { imgBytes = fis.readAllBytes(); }
                String b64 = java.util.Base64.getEncoder().encodeToString(imgBytes);
                org.json.JSONArray contentArr = new org.json.JSONArray()
                    .put(new org.json.JSONObject()
                        .put("type", "image")
                        .put("source", new org.json.JSONObject()
                            .put("type",       "base64")
                            .put("media_type", mediaType)
                            .put("data",       b64)))
                    .put(new org.json.JSONObject()
                        .put("type", "text")
                        .put("text", lastMsg.getString("content")));
                messages.put(new org.json.JSONObject().put("role","user").put("content", contentArr));
            } else {
                // Text file: append raw content to message
                String fileText;
                try (FileInputStream fis = new FileInputStream(attachment)) {
                    fileText = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                }
                String combined = lastMsg.getString("content")
                    + "\n\n[Archivo adjunto: " + attachment.getName() + "]\n" + fileText;
                messages.put(new org.json.JSONObject().put("role","user").put("content", combined));
            }
        } else {
            messages.put(lastMsg);
        }

        String body = new org.json.JSONObject()
            .put("model",      "claude-haiku-4-5-20251001")
            .put("max_tokens", 2048)
            .put("system",     systemPrompt)
            .put("tools",      toolsArray)
            .put("messages",   messages)
            .toString();

        HttpResponse<String> resp = sendHttpRequest(apiKey, body);
        org.json.JSONObject  json = new org.json.JSONObject(resp.body());

        if ("tool_use".equals(json.optString("stop_reason"))) {
            org.json.JSONArray content = json.getJSONArray("content");

            // Mostrar texto previo si Claude lo incluyó antes de la tool call
            for (int i = 0; i < content.length(); i++) {
                org.json.JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type")) && !block.optString("text","").isBlank()) {
                    final String txt = block.getString("text");
                    SwingUtilities.invokeLater(() -> appendChat("🤖 Asistente", txt, COLOR_OK));
                }
            }

            // Buscar y ejecutar el bloque tool_use
            org.json.JSONObject toolUse = null;
            for (int i = 0; i < content.length(); i++) {
                org.json.JSONObject block = content.getJSONObject(i);
                if ("tool_use".equals(block.optString("type"))) { toolUse = block; break; }
            }
            String toolResult = (toolUse != null) ? executeTool(toolUse) : "No se encontró ninguna tool.";

            // Segunda llamada con el resultado de la tool
            org.json.JSONArray msgs2 = new org.json.JSONArray();
            for (org.json.JSONObject m : chatHistory) msgs2.put(m);
            msgs2.put(new org.json.JSONObject().put("role","assistant").put("content", content));
            msgs2.put(new org.json.JSONObject().put("role","user").put("content",
                new org.json.JSONArray().put(new org.json.JSONObject()
                    .put("type","tool_result")
                    .put("tool_use_id", toolUse != null ? toolUse.getString("id") : "")
                    .put("content", toolResult))));

            String body2 = new org.json.JSONObject()
                .put("model",      "claude-haiku-4-5-20251001")
                .put("max_tokens", 1024)
                .put("system",     systemPrompt)
                .put("tools",      toolsArray)
                .put("messages",   msgs2)
                .toString();

            HttpResponse<String> resp2 = sendHttpRequest(apiKey, body2);
            org.json.JSONObject  json2 = new org.json.JSONObject(resp2.body());
            return json2.getJSONArray("content").getJSONObject(0).getString("text");
        }

        return json.getJSONArray("content").getJSONObject(0).getString("text");
    }

    private static HttpResponse<String> sendHttpRequest(String apiKey, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key",         apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type",      "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
            .send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " — " + resp.body());
        return resp;
    }

    private static void chooseAttachment() {
        javax.swing.filechooser.FileNameExtensionFilter filter =
            new javax.swing.filechooser.FileNameExtensionFilter(
                "Imágenes y archivos (png, jpg, gif, webp, txt, json, log, xml)",
                "png", "jpg", "jpeg", "gif", "webp", "txt", "json", "log", "xml");
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Adjuntar imagen o archivo");
        fc.setFileFilter(filter);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            pendingAttachment = fc.getSelectedFile();
            if (attachLabel != null)
                attachLabel.setText("📎 " + pendingAttachment.getName() + "  [✕ clic para quitar]");
        }
    }

    private static String detectMediaType(String fileName) {
        String l = fileName.toLowerCase();
        if (l.endsWith(".png"))                    return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif"))                    return "image/gif";
        if (l.endsWith(".webp"))                   return "image/webp";
        return null;
    }

    private static String executeTool(org.json.JSONObject toolUse) {
        try {
            String toolName = toolUse.optString("name", "");
            if ("ocultar_modulo".equals(toolName))
                return executeHideModuleTool(toolUse.getJSONObject("input"));
            if (!"crear_suite".equals(toolName)) return "Tool desconocida: " + toolName;
            org.json.JSONObject inp = toolUse.getJSONObject("input");

            String nombre      = inp.getString("nombre");
            String descripcion = inp.getString("descripcion");
            String icono       = inp.optString("icono",  "🔬");
            String colorHex    = inp.optString("color",  "#7C3AED");
            String clase       = inp.getString("clase_prueba");
            org.json.JSONArray pruebasJson = inp.optJSONArray("pruebas");

            Color color = parseColor(colorHex);

            // clHolder permite que las lambdas capturen el classloader que se
            // resuelve después de compilar (antes de que el usuario pulse Ejecutar)
            final ClassLoader[] clHolder = {null};

            java.util.List<TestRow> rows = new ArrayList<>();
            if (pruebasJson != null) {
                for (int i = 0; i < pruebasJson.length(); i++) {
                    org.json.JSONObject p = pruebasJson.getJSONObject(i);
                    final String lbl  = p.getString("etiqueta");
                    final String desc = p.optString("descripcion", "");
                    final String mth  = p.getString("metodo");
                    final String cls  = clase;
                    rows.add(new TestRow(lbl, desc.isBlank() ? null : desc,
                        () -> exec(lbl, clHolder[0], selectMethod(cls, mth))));
                }
            }

            String id = normalize(nombre);
            ClassLoader cl = generateAndCompileTestFile(clase, nombre, pruebasJson);
            clHolder[0] = cl;

            addSuiteToMenu(id, icono, color, nombre, descripcion, clase, rows, cl);
            saveCustomSuite(id, icono, colorHex, nombre, descripcion, clase, pruebasJson);
            return "Suite '" + nombre + "' creada y agregada al menú. "
                + (cl != null
                    ? "Archivo Java generado y compilado — lista para ejecutar de inmediato."
                    : "Archivo Java generado en src/test/java — reconstruye el proyecto para ejecutarla.");
        } catch (Exception e) {
            return "Error al crear suite: " + e.getMessage();
        }
    }

    private static String executeHideModuleTool(org.json.JSONObject inp) {
        String nombre = inp.optString("nombre_modulo", "").trim();
        if (nombre.isBlank()) return "Falta el parámetro 'nombre_modulo'.";

        String key = normalize(nombre);

        // Búsqueda exacta
        JPanel card = moduleCards.get(key);
        String matchedKey = key;

        // Búsqueda parcial si no hay exacta
        if (card == null) {
            for (Map.Entry<String, JPanel> e : moduleCards.entrySet()) {
                if (e.getKey().contains(key) || key.contains(e.getKey())) {
                    card = e.getValue();
                    matchedKey = e.getKey();
                    break;
                }
            }
        }

        if (card == null) {
            String disponibles = String.join(", ", moduleCards.keySet());
            return "No se encontró el módulo '" + nombre + "'. "
                + "Módulos disponibles: " + (disponibles.isBlank() ? "(ninguno registrado)" : disponibles);
        }

        final JPanel target   = card;
        final String finalKey = matchedKey;
        moduleCards.remove(finalKey);
        moduleDisplayNames.remove(finalKey);

        SwingUtilities.invokeLater(() -> {
            Container parent = target.getParent();
            if (parent != null) {
                parent.remove(target);
                parent.revalidate();
                parent.repaint();
            }
        });

        logLine("✓", "Módulo '" + nombre + "' ocultado del launcher.", COLOR_SKIP);
        return "✓ Módulo '" + nombre + "' ocultado visualmente. "
            + "El código permanece intacto. Reinicia el launcher para restaurarlo.";
    }

    private static void addSuiteToMenu(String id, String icon, Color color, String title,
                                        String description, String testClass,
                                        java.util.List<TestRow> tests, ClassLoader classLoader) {
        JPanel detailPanel = buildDetailPanel(
            title.toUpperCase() + " – TESTS INDIVIDUALES", icon, color,
            () -> exec(title + " – Todos", classLoader, selectClass(testClass)),
            tests);

        CardData card = new CardData(icon, color, title, description, color,
            () -> rightCardLayout.show(rightCardPanel, "México-" + id));

        SwingUtilities.invokeLater(() -> {
            rightCardPanel.add(detailPanel, "México-" + id);
            if (mexicoGrid != null) {
                mexicoGrid.add(buildCard(card));
                mexicoGrid.revalidate();
                mexicoGrid.repaint();
            }
        });
        logLine("✓", "Suite '" + title + "' agregada al menú.", COLOR_OK);
    }

    private static void loadCustomSuites() {
        File f = new File("suites.json");
        if (!f.exists()) f = new File("src/test/resources/suites.json");
        if (!f.exists()) return;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            org.json.JSONObject root   = new org.json.JSONObject(sb.toString());
            org.json.JSONArray  suites = root.optJSONArray("suites");
            if (suites == null) return;

            // Un único URLClassLoader cubre todas las clases compiladas dinámicamente
            ClassLoader customCl = null;
            File customClassesDir = new File("custom-classes");
            if (customClassesDir.exists()) {
                try {
                    customCl = new java.net.URLClassLoader(
                        new java.net.URL[]{customClassesDir.toURI().toURL()},
                        Main.class.getClassLoader());
                } catch (Exception ignored) {}
            }
            final ClassLoader clForSuites = customCl;

            for (int i = 0; i < suites.length(); i++) {
                org.json.JSONObject s = suites.getJSONObject(i);
                String id       = s.getString("id");
                String icon     = s.optString("icon",        "🔬");
                String colorHex = s.optString("color",       "#7C3AED");
                String title    = s.getString("title");
                String desc     = s.optString("description", "");
                String cls      = s.getString("testClass");
                Color  color    = parseColor(colorHex);

                java.util.List<TestRow> rows = new ArrayList<>();
                org.json.JSONArray tests = s.optJSONArray("tests");
                if (tests != null) {
                    for (int j = 0; j < tests.length(); j++) {
                        org.json.JSONObject t  = tests.getJSONObject(j);
                        final String lbl  = t.optString("etiqueta", t.optString("label",  "Test " + (j + 1)));
                        final String tdsc = t.optString("descripcion", t.optString("description", ""));
                        final String mth  = t.optString("metodo",  t.optString("method", ""));
                        final String fcls = cls;
                        rows.add(new TestRow(lbl, tdsc.isBlank() ? null : tdsc,
                            () -> exec(lbl, clForSuites, selectMethod(fcls, mth))));
                    }
                }
                addSuiteToMenu(id, icon, color, title, desc, cls, rows, clForSuites);
            }
        } catch (Exception e) {
            logLine("⚠", "Error al cargar suites.json: " + e.getMessage(), COLOR_SKIP);
        }
    }

    private static void saveCustomSuite(String id, String icon, String colorHex, String title,
                                         String description, String testClass,
                                         org.json.JSONArray pruebas) {
        File f = new File("suites.json");
        try {
            org.json.JSONObject root;
            if (f.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                }
                root = new org.json.JSONObject(sb.toString());
            } else {
                root = new org.json.JSONObject().put("suites", new org.json.JSONArray());
            }
            org.json.JSONArray arr = root.getJSONArray("suites");
            for (int i = arr.length() - 1; i >= 0; i--) {
                if (id.equals(arr.getJSONObject(i).optString("id"))) arr.remove(i);
            }
            org.json.JSONObject entry = new org.json.JSONObject()
                .put("id",          id)
                .put("icon",        icon)
                .put("color",       colorHex)
                .put("title",       title)
                .put("description", description)
                .put("testClass",   testClass);
            if (pruebas != null) entry.put("tests", pruebas);
            arr.put(entry);
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
                bw.write(root.toString(2));
            }
        } catch (Exception e) {
            logLine("⚠", "No se pudo guardar suites.json: " + e.getMessage(), COLOR_SKIP);
        }
    }

    private static Color parseColor(String hex) {
        try   { return Color.decode(hex); }
        catch (Exception e) { return new Color(124, 58, 237); }
    }

    // ── Generación y compilación dinámica de clases de prueba ─────

    private static ClassLoader generateAndCompileTestFile(String clase, String nombre,
                                                           org.json.JSONArray pruebas) {
        try {
            int    lastDot   = clase.lastIndexOf('.');
            String pkg       = clase.substring(0, lastDot);
            String className = clase.substring(lastDot + 1);
            String source    = generateTestSource(pkg, className, nombre, pruebas);

            // Escribir archivo .java
            String pkgPath = pkg.replace('.', File.separatorChar);
            File   srcDir  = new File("src/test/java/" + pkgPath);
            if (!srcDir.exists()) srcDir.mkdirs();
            File srcFile = new File(srcDir, className + ".java");
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(srcFile), StandardCharsets.UTF_8))) {
                bw.write(source);
            }
            logLine("✓", "Archivo generado: " + srcFile.getPath(), COLOR_OK);

            // Compilar con el JDK en tiempo de ejecución
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                logLine("⚠", "JDK no disponible — reconstruye el proyecto para ejecutar la suite.", COLOR_SKIP);
                return null;
            }

            File   outDir    = new File("custom-classes");
            outDir.mkdirs();
            String classpath = System.getProperty("java.class.path", "");

            javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diag =
                new javax.tools.DiagnosticCollector<>();
            javax.tools.StandardJavaFileManager fm =
                compiler.getStandardFileManager(diag, null, StandardCharsets.UTF_8);
            Iterable<? extends javax.tools.JavaFileObject> units =
                fm.getJavaFileObjectsFromFiles(java.util.Collections.singletonList(srcFile));

            javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diag,
                Arrays.asList("-classpath", classpath, "-d", outDir.getAbsolutePath(),
                    "--source", "17", "--target", "17"),
                null, units);

            boolean ok = task.call();
            fm.close();

            if (!ok) {
                StringBuilder errs = new StringBuilder();
                for (javax.tools.Diagnostic<?> d : diag.getDiagnostics()) {
                    if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                        errs.append(d.getMessage(null)).append(" ");
                }
                logLine("⚠", "Compilación fallida: " + errs.toString().trim(), COLOR_SKIP);
                return null;
            }

            logLine("✓", "Compilación exitosa → " + outDir.getPath(), COLOR_OK);
            return new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toURI().toURL()},
                Main.class.getClassLoader());

        } catch (Exception e) {
            logLine("⚠", "Error generando clase: " + e.getMessage(), COLOR_SKIP);
            return null;
        }
    }

    private static String generateTestSource(String pkg, String className, String suiteName,
                                              org.json.JSONArray pruebas) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import base.BaseTest;\n");
        sb.append("import io.qameta.allure.Epic;\n");
        sb.append("import io.qameta.allure.Story;\n");
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import utils.TestSteps;\n\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("@Epic(\"Pruebas – ").append(suiteName.replace("\"", "'")).append("\")\n");
        sb.append("public class ").append(className).append(" extends BaseTest {\n\n");

        if (pruebas != null) {
            for (int i = 0; i < pruebas.length(); i++) {
                try {
                    org.json.JSONObject p     = pruebas.getJSONObject(i);
                    String label  = p.optString("etiqueta", "Test " + (i + 1));
                    String method = p.optString("metodo",   "test" + (i + 1));
                    String desc   = p.optString("descripcion", "");
                    String display = (desc.isBlank() ? label : label + " – " + desc).replace("\"", "'");

                    sb.append("    @Test\n");
                    sb.append("    @Order(").append(i + 1).append(")\n");
                    sb.append("    @DisplayName(\"").append(display).append("\")\n");
                    sb.append("    @Story(\"").append(suiteName.replace("\"", "'")).append("\")\n");
                    sb.append("    void ").append(method).append("() {\n");
                    sb.append("        TestSteps.run(\"").append(label.replace("\"", "'")).append("\", () -> {\n");
                    sb.append("            // TODO: implementar pasos\n");
                    sb.append("            throw new org.opentest4j.TestAbortedException(\"Pendiente de implementación\");\n");
                    sb.append("        }, driver);\n");
                    sb.append("    }\n\n");
                } catch (Exception ignored) {}
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ── Summary bar ───────────────────────────────────────────────

    private static JPanel buildSummaryBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(7, 11, 26));
                g2.fillRect(0, 0, w, h);
                g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,5), 0, h, new Color(255,255,255,0)));
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        bar.setOpaque(true);
        bar.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 1, 0, new Color(28, 42, 75)),
            new EmptyBorder(10, 18, 10, 18)));

        JLabel barIco = new JLabel("📊");
        barIco.setFont(new Font("Dialog", Font.PLAIN, 14));
        JLabel barLbl = new JLabel("  RESUMEN DE EJECUCIÓN  ");
        barLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        barLbl.setForeground(TEXT_DIM);

        passedVal  = bigNum("0");
        failedVal  = bigNum("0");
        skippedVal = bigNum("0");
        totalVal   = bigNum("0");
        timeVal    = new JLabel("--");
        timeVal.setFont(new Font("SansSerif", Font.BOLD, 13));
        timeVal.setForeground(TEXT_LBL);

        bar.add(barIco); bar.add(barLbl);
        bar.add(hSep());
        bar.add(statBlock("✓", COLOR_OK,   passedVal,  "PASSED"));
        bar.add(hSep());
        bar.add(statBlock("✗", COLOR_FAIL,  failedVal,  "FAILED"));
        bar.add(hSep());
        bar.add(statBlock("▶▶", COLOR_SKIP, skippedVal, "SKIPPED"));
        bar.add(hSep());
        bar.add(statBlock(null, TEXT_LBL, totalVal, "TOTAL"));
        bar.add(hSep());

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timePanel.setOpaque(false);
        JLabel clockIco = new JLabel("⏱");
        clockIco.setFont(new Font("Dialog", Font.PLAIN, 13));
        clockIco.setForeground(TEXT_DIM);
        JLabel timeLbl = new JLabel("Última ejecución: ");
        timeLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timeLbl.setForeground(TEXT_DIM);
        timePanel.add(clockIco); timePanel.add(timeLbl); timePanel.add(timeVal);
        bar.add(timePanel);
        return bar;
    }

    private static JPanel statBlock(String icon, Color color, JLabel numLbl, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        if (icon != null) {
            JLabel ico = new JLabel(icon);
            ico.setFont(new Font("Dialog", Font.BOLD, 16));
            ico.setForeground(color);
            p.add(ico);
        }
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        numLbl.setFont(new Font("SansSerif", Font.BOLD, 22));
        numLbl.setForeground(color);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 9));
        lbl.setForeground(TEXT_DIM);
        stack.add(numLbl); stack.add(lbl);
        p.add(stack);
        return p;
    }

    private static JLabel bigNum(String v) {
        JLabel l = new JLabel(v);
        l.setFont(new Font("SansSerif", Font.BOLD, 22));
        l.setForeground(TEXT_PRI);
        return l;
    }

    private static JLabel hSep() {
        JLabel s = new JLabel("  |  ");
        s.setForeground(BORDER);
        s.setFont(new Font("SansSerif", Font.PLAIN, 18));
        return s;
    }

    private static void updateStats(long ok, long fail, long skip) {
        statPassed  += (int) ok;
        statFailed  += (int) fail;
        statSkipped += (int) skip;
        statTotal    = statPassed + statFailed + statSkipped;
        String t     = TS.format(new Date());
        SwingUtilities.invokeLater(() -> {
            passedVal.setText(String.valueOf(statPassed));
            failedVal.setText(String.valueOf(statFailed));
            skippedVal.setText(String.valueOf(statSkipped));
            totalVal.setText(String.valueOf(statTotal));
            timeVal.setText(t);
        });
    }

    // ── Log panel ─────────────────────────────────────────────────

    private static JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_MAIN);
        panel.setBorder(new EmptyBorder(6, 14, 8, 14));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel logTitle = new JLabel(">_  LOG DE EJECUCIÓN");
        logTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        logTitle.setForeground(TEXT_DIM);

        abortBtn = new RoundedButton("■  ABORTAR EJECUCIÓN", new Color(185, 28, 28), 6);
        abortBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
        abortBtn.setForeground(Color.WHITE);
        abortBtn.setPreferredSize(new Dimension(170, 26));
        abortBtn.addActionListener(e -> abortExecution());
        abortBtn.setVisible(false);

        RoundedButton clearBtn = new RoundedButton("🗑  LIMPIAR LOG", BG_CARD, 6);
        clearBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
        clearBtn.setForeground(TEXT_DIM);
        clearBtn.setPreferredSize(new Dimension(130, 26));
        clearBtn.addActionListener(e -> { logPane.setText(""); resetStats(); });

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(abortBtn);
        rightBtns.add(clearBtn);

        titleRow.add(logTitle,  BorderLayout.WEST);
        titleRow.add(rightBtns, BorderLayout.EAST);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(3, 5, 15));
        logPane.setForeground(TEXT_PRI);
        logPane.setBorder(new EmptyBorder(10, 14, 10, 14));
        // Premium monospace font: prefer Cascadia Code / Consolas on Windows
        Font logFont = new Font("Cascadia Code", Font.PLAIN, 12);
        if (logFont.getFamily().equals("Dialog")) logFont = new Font("Consolas", Font.PLAIN, 12);
        if (logFont.getFamily().equals("Dialog")) logFont = new Font("Monospaced", Font.PLAIN, 12);
        logPane.setFont(logFont);

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(28, 42, 75), 1));
        scroll.setBackground(new Color(3, 5, 15));
        scroll.getViewport().setBackground(new Color(3, 5, 15));
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        return panel;
    }

    // ── Footer ────────────────────────────────────────────────────

    private static JPanel buildFooter() {
        JPanel f = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(6, 10, 24));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        f.setOpaque(true);
        f.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, new Color(28, 42, 75)),
            new EmptyBorder(8, 18, 8, 18)));
        JLabel copy = new JLabel("© 2026 Cinépolis - Automation QA Team");
        copy.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copy.setForeground(TEXT_DIM);
        f.add(copy, BorderLayout.EAST);
        return f;
    }

    // ── Abort / state ─────────────────────────────────────────────

    private static void abortExecution() {
        if (!running) return;
        logLine("⚠", "Abortando ejecución...", COLOR_SKIP);
        new Thread(() -> {
            // 1. Signal DriverFactory via system property (avoids cross-sourceSet compile dependency)
            System.setProperty("cinepolis.abort.requested", "true");

            // 2. Interrupt the JUnit runner thread
            Thread t = testThread;
            if (t != null && t.isAlive()) {
                t.interrupt();
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                if (t.isAlive()) t.interrupt();
            }

            running = false; testThread = null;
            java.util.List<JButton> snap = runningSnapshot;
            SwingUtilities.invokeLater(() -> { setRunningUi(false); if (snap != null) snap.forEach(b -> b.setEnabled(true)); });
            logLine("⚠", "Ejecución abortada.", COLOR_SKIP);
        }, "abort-thread").start();
    }

    private static void setRunningUi(boolean on) {
        if (on) {
            mainBtn.setText("■  DETENER");
            mainBtn.setAccent(new Color(185, 28, 28));
            statusDot.setForeground(COLOR_SKIP);
            statusText.setForeground(COLOR_SKIP);
            statusText.setText("Ejecutando...");
        } else {
            mainBtn.setText("▶  EJECUTAR PRUEBAS");
            mainBtn.setAccent(ACCENT);
            statusDot.setForeground(COLOR_OK);
            statusText.setForeground(COLOR_OK);
            statusText.setText("Ready");
        }
        mainBtn.repaint();
        abortBtn.setVisible(on);
        abortBtn.getParent().revalidate();
    }

    // ══════════════════════════════════════════════════════════════
    //  Execution
    // ══════════════════════════════════════════════════════════════

    private static void exec(String name, DiscoverySelector... selectors) {
        exec(name, null, selectors);
    }

    private static void exec(String name, ClassLoader classLoader, DiscoverySelector... selectors) {
        if (running) return;
        String label = selectedCountry.isEmpty() ? name : name + " - " + selectedCountry;
        final java.util.List<JButton> snapshot = new ArrayList<>(testButtons);
        final ClassLoader cl = classLoader;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                if (cl != null) Thread.currentThread().setContextClassLoader(cl);
                running = true; testThread = Thread.currentThread(); runningSnapshot = snapshot;
                SwingUtilities.invokeLater(() -> { snapshot.forEach(b -> b.setEnabled(false)); setRunningUi(true); });

                logLine("ℹ", "Iniciando ejecución de: " + label, ACCENT);
                log("─".repeat(56) + "\n", BORDER, false);

                try {
                    LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request()
                            .selectors(Arrays.asList(selectors)).build();
                    SummaryGeneratingListener listener = new SummaryGeneratingListener();
                    LauncherFactory.create().execute(req, listener);

                    TestExecutionSummary s = listener.getSummary();
                    long ms    = s.getTimeFinished() - s.getTimeStarted();
                    boolean ko = s.getTestsFailedCount() > 0;
                    long omit  = s.getTestsSkippedCount() + s.getTestsAbortedCount();

                    log("─".repeat(56) + "\n", BORDER, false);
                    logLine("✓", "Ejecutados : " + s.getTestsStartedCount(),   TEXT_PRI);
                    logLine("✓", "Pasaron    : " + s.getTestsSucceededCount(), COLOR_OK);
                    logLine(ko ? "✗" : "✓", "Fallaron   : " + s.getTestsFailedCount(), ko ? COLOR_FAIL : COLOR_OK);
                    logLine("⚠", "Omitidos   : " + omit,                       COLOR_SKIP);
                    logLine("ℹ", String.format("Duración   : %.1f seg", ms / 1000.0), TEXT_DIM);

                    if (ko) {
                        log("\n", TEXT_PRI, false);
                        logLine("✗", "Fallos detectados:", COLOR_FAIL);
                        for (TestExecutionSummary.Failure f : s.getFailures()) {
                            log("       • " + f.getTestIdentifier().getDisplayName() + "\n", COLOR_FAIL, false);
                            Throwable ex = f.getException();
                            if (ex != null && ex.getMessage() != null) {
                                String first = ex.getMessage().lines().findFirst().orElse("");
                                if (!first.isBlank()) log("         " + first + "\n", TEXT_DIM, false);
                            }
                        }
                    }
                    logLine("ℹ", "Ejecución finalizada", TEXT_DIM);
                    log("\n", TEXT_PRI, false);
                    updateStats(s.getTestsSucceededCount(), s.getTestsFailedCount(), omit);

                } catch (Exception ex) {
                    if (!isCancelled()) logLine("✗", "Error: " + ex.getMessage(), COLOR_FAIL);
                } finally {
                    System.setProperty("cinepolis.abort.requested", "false");
                    running = false; testThread = null; runningSnapshot = null;
                    SwingUtilities.invokeLater(() -> { snapshot.forEach(b -> b.setEnabled(true)); setRunningUi(false); });
                }
                return null;
            }
        }.execute();
    }

    // ══════════════════════════════════════════════════════════════
    //  Genymotion Cloud execution
    // ══════════════════════════════════════════════════════════════

    private static void execGenymotion() {
        if (running) return;
        Properties props = readAppiumProps();

        String email      = System.getProperty("genymotion.email",      props.getProperty("genymotion.email",      ""));
        String password   = System.getProperty("genymotion.password",   props.getProperty("genymotion.password",   ""));
        String apiToken   = System.getProperty("genymotion.api.token",  props.getProperty("genymotion.api.token",  ""));
        String recipeUuid = System.getProperty("genymotion.recipe.uuid",props.getProperty("genymotion.recipe.uuid",""));

        if (recipeUuid.isBlank()) {
            logLine("✗", "Genymotion: falta Recipe UUID (Config ⚙).", COLOR_FAIL); return;
        }
        if (email.isBlank() && apiToken.isBlank()) {
            logLine("✗", "Genymotion: falta Email+Password o API Token (Config ⚙).", COLOR_FAIL); return;
        }

        final java.util.List<JButton> snapshot = new ArrayList<>(testButtons);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                running = true; testThread = Thread.currentThread(); runningSnapshot = snapshot;
                SwingUtilities.invokeLater(() -> { snapshot.forEach(b -> b.setEnabled(false)); setRunningUi(true); });

                logLine("📱", "Iniciando instancia Genymotion Cloud...", ACCENT);
                log("─".repeat(56) + "\n", BORDER, false);

                String instanceUuid  = null;
                String savedUdid     = System.getProperty("udid", "");

                try {
                    // ── 1. Start instance
                    String[] inst = GenyCloudRunner.startInstance(recipeUuid, email, password, apiToken);
                    instanceUuid = inst[0];
                    logLine("✓", "Instancia creada: " + instanceUuid, COLOR_OK);

                    // ── 2. Wait for ONLINE
                    logLine("⏳", "Esperando que la instancia esté en línea...", TEXT_DIM);
                    final String fId = instanceUuid;
                    String adbUrl = GenyCloudRunner.waitForInstance(fId, email, password, apiToken,
                            () -> logLine("⏳", "Inicializando emulador Genymotion...", TEXT_DIM));
                    logLine("✓", "Instancia en línea. ADB: " + adbUrl, COLOR_OK);

                    // ── 3. ADB connect
                    connectAdb(adbUrl);
                    System.setProperty("udid", adbUrl);
                    logLine("✓", "UDID configurado: " + adbUrl, COLOR_OK);
                    try { Thread.sleep(2500); } catch (InterruptedException ignored) {}

                    // ── 4. Run JUnit tests (same logic as exec())
                    String label = "Genymotion Cloud" + (selectedCountry.isEmpty() ? "" : " · " + selectedCountry);
                    logLine("ℹ", "Ejecutando: " + label, ACCENT);
                    log("─".repeat(56) + "\n", BORDER, false);

                    DiscoverySelector[] selectors = switch (selectedCountry) {
                        case "México"    -> new DiscoverySelector[]{selectPackage("tests.México")};
                        case "Argentina" -> new DiscoverySelector[]{selectClass("tests.Argentina.NoAfectacionArgentina")};
                        case "Chile"     -> new DiscoverySelector[]{selectClass("tests.Chile.NoAfectacionChile")};
                        default          -> new DiscoverySelector[0];
                    };

                    if (selectors.length == 0) {
                        logLine("⚠", "Sin suite configurada para: " + selectedCountry, COLOR_SKIP);
                    } else {
                        LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request()
                                .selectors(Arrays.asList(selectors)).build();
                        SummaryGeneratingListener listener = new SummaryGeneratingListener();
                        LauncherFactory.create().execute(req, listener);
                        TestExecutionSummary s = listener.getSummary();
                        long ms   = s.getTimeFinished() - s.getTimeStarted();
                        boolean ko = s.getTestsFailedCount() > 0;
                        long omit  = s.getTestsSkippedCount() + s.getTestsAbortedCount();
                        log("─".repeat(56) + "\n", BORDER, false);
                        logLine("✓", "Ejecutados : " + s.getTestsStartedCount(),   TEXT_PRI);
                        logLine("✓", "Pasaron    : " + s.getTestsSucceededCount(), COLOR_OK);
                        logLine(ko ? "✗" : "✓", "Fallaron   : " + s.getTestsFailedCount(), ko ? COLOR_FAIL : COLOR_OK);
                        logLine("⚠", "Omitidos   : " + omit, COLOR_SKIP);
                        logLine("ℹ", String.format("Duración   : %.1f seg", ms / 1000.0), TEXT_DIM);
                        updateStats(s.getTestsSucceededCount(), s.getTestsFailedCount(), omit);
                    }

                } catch (Exception ex) {
                    if (!isCancelled()) logLine("✗", "Genymotion error: " + ex.getMessage(), COLOR_FAIL);
                } finally {
                    // ── 5. Stop instance
                    if (instanceUuid != null) {
                        try {
                            GenyCloudRunner.stopInstance(instanceUuid, email, password, apiToken);
                            logLine("✓", "Instancia Genymotion detenida.", COLOR_OK);
                        } catch (Exception e) {
                            logLine("⚠", "No se pudo detener la instancia: " + e.getMessage(), COLOR_SKIP);
                        }
                    }
                    System.setProperty("udid", savedUdid);
                    System.setProperty("cinepolis.abort.requested", "false");
                    running = false; testThread = null; runningSnapshot = null;
                    SwingUtilities.invokeLater(() -> { snapshot.forEach(b -> b.setEnabled(true)); setRunningUi(false); });
                }
                return null;
            }
        }.execute();
    }

    private static void connectAdb(String adbUrl) {
        try {
            logLine("📱", "adb connect " + adbUrl + " ...", TEXT_DIM);
            Process p = Runtime.getRuntime().exec(new String[]{"adb", "connect", adbUrl});
            p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logLine("⚠", "adb connect falló — verifica que ADB esté en PATH: " + e.getMessage(), COLOR_SKIP);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  AWS Device Farm execution
    // ══════════════════════════════════════════════════════════════

    private static void execDeviceFarm() {
        if (running) return;
        Properties props = readAppiumProps();

        String accessKeyId    = System.getProperty("devicefarm.access.key.id",  props.getProperty("devicefarm.access.key.id",  ""));
        String secretKey      = System.getProperty("devicefarm.secret.access.key", props.getProperty("devicefarm.secret.access.key", ""));
        String region         = System.getProperty("devicefarm.region",          props.getProperty("devicefarm.region",          "us-west-2"));
        String projectArn     = System.getProperty("devicefarm.project.arn",     props.getProperty("devicefarm.project.arn",     ""));
        String devicePoolArn  = System.getProperty("devicefarm.device.pool.arn", props.getProperty("devicefarm.device.pool.arn", ""));
        String apkPath        = System.getProperty("devicefarm.apk.path",        props.getProperty("devicefarm.apk.path",        ""));

        if (accessKeyId.isBlank() || secretKey.isBlank()) {
            logLine("✗", "Device Farm: falta Access Key ID o Secret Access Key (Config ⚙).", COLOR_FAIL); return;
        }
        if (projectArn.isBlank()) {
            logLine("✗", "Device Farm: falta Project ARN (Config ⚙).", COLOR_FAIL); return;
        }
        if (devicePoolArn.isBlank()) {
            logLine("✗", "Device Farm: falta Device Pool ARN (Config ⚙).", COLOR_FAIL); return;
        }

        // Locate the shadow JAR (built by ./gradlew shadowJar)
        java.nio.file.Path jarPath = java.nio.file.Paths.get("build", "libs", "cinepolis-tests.jar");
        if (!java.nio.file.Files.exists(jarPath)) {
            logLine("✗", "No se encontró build/libs/cinepolis-tests.jar — ejecuta './gradlew shadowJar' primero.", COLOR_FAIL); return;
        }

        final String finalRegion      = region;
        final String finalAccessKeyId = accessKeyId;
        final String finalSecretKey   = secretKey;
        final String finalProjectArn  = projectArn;
        final String finalPoolArn     = devicePoolArn;
        final String finalApkPath     = apkPath;
        final java.nio.file.Path finalJar = jarPath;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                running = true; testThread = Thread.currentThread();
                SwingUtilities.invokeLater(() -> setRunningUi(true));

                logLine("☁", "Iniciando ejecución en AWS Device Farm...", ACCENT);
                log("─".repeat(56) + "\n", BORDER, false);

                try {
                    String appArn = null;

                    // ── 1. Upload APK (optional – skip if path not set)
                    if (!finalApkPath.isBlank()) {
                        java.nio.file.Path apk = java.nio.file.Paths.get(finalApkPath);
                        if (!java.nio.file.Files.exists(apk)) {
                            logLine("⚠", "APK no encontrado en: " + finalApkPath + " — se omitirá.", COLOR_SKIP);
                        } else {
                            logLine("☁", "Subiendo APK: " + apk.getFileName() + " ...", TEXT_DIM);
                            String[] appUpload = AWSDeviceFarmRunner.createUpload(
                                    finalProjectArn, apk.getFileName().toString(), "ANDROID_APP",
                                    finalAccessKeyId, finalSecretKey, finalRegion);
                            AWSDeviceFarmRunner.uploadToPresignedUrl(appUpload[1], apk);
                            logLine("☁", "APK subido. Esperando procesamiento...", TEXT_DIM);
                            AWSDeviceFarmRunner.waitForUpload(appUpload[0],
                                    finalAccessKeyId, finalSecretKey, finalRegion,
                                    () -> logLine("⏳", "Procesando APK...", TEXT_DIM));
                            appArn = appUpload[0];
                            logLine("✓", "APK procesado: " + appArn, COLOR_OK);
                        }
                    }

                    // ── 2. Package and upload test JAR
                    logLine("☁", "Empaquetando test JAR para Device Farm...", TEXT_DIM);
                    java.nio.file.Path testZip = AWSDeviceFarmRunner.packageTestJar(finalJar);
                    logLine("☁", "Subiendo test package: " + testZip.getFileName() + " ...", TEXT_DIM);
                    String[] testUpload = AWSDeviceFarmRunner.createUpload(
                            finalProjectArn, "cinepolis-tests.zip", "APPIUM_JAVA_JUNIT_TEST_PACKAGE",
                            finalAccessKeyId, finalSecretKey, finalRegion);
                    AWSDeviceFarmRunner.uploadToPresignedUrl(testUpload[1], testZip);
                    logLine("☁", "Test package subido. Esperando procesamiento...", TEXT_DIM);
                    AWSDeviceFarmRunner.waitForUpload(testUpload[0],
                            finalAccessKeyId, finalSecretKey, finalRegion,
                            () -> logLine("⏳", "Procesando test package...", TEXT_DIM));
                    logLine("✓", "Test package procesado: " + testUpload[0], COLOR_OK);

                    // ── 3. Create run
                    String runName = "Cinépolis - " + selectedCountry + " - " +
                            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
                    logLine("☁", "Creando run: " + runName, TEXT_DIM);
                    String runArn = AWSDeviceFarmRunner.createRun(
                            finalProjectArn, finalPoolArn,
                            appArn != null ? appArn : "",
                            testUpload[0], runName,
                            finalAccessKeyId, finalSecretKey, finalRegion);
                    logLine("✓", "Run creado: " + runArn, COLOR_OK);

                    // ── 4. Poll until complete
                    logLine("☁", "Esperando resultado del run (puede tomar varios minutos)...", ACCENT);
                    String[] result = AWSDeviceFarmRunner.waitForRun(runArn,
                            finalAccessKeyId, finalSecretKey, finalRegion,
                            info -> logLine("⏳", "Estado: " + info[0] + " · passed=" + info[2] + " failed=" + info[3], TEXT_DIM));

                    log("─".repeat(56) + "\n", BORDER, false);
                    boolean ok = "PASSED".equalsIgnoreCase(result[1]);
                    logLine(ok ? "✓" : "✗", "Resultado : " + result[1],     ok ? COLOR_OK : COLOR_FAIL);
                    logLine("✓",             "Pasaron   : " + result[2],     COLOR_OK);
                    logLine("✗",             "Fallaron  : " + result[3],     Integer.parseInt(result[3]) > 0 ? COLOR_FAIL : COLOR_OK);
                    logLine("⚠",             "Omitidos  : " + result[4],     COLOR_SKIP);
                    logLine("ℹ",             "Run ARN   : " + runArn,        TEXT_DIM);
                    long passed  = Long.parseLong(result[2]);
                    long failed  = Long.parseLong(result[3]);
                    long skipped = Long.parseLong(result[4]);
                    updateStats(passed, failed, skipped);

                } catch (Exception ex) {
                    if (!isCancelled()) logLine("✗", "Device Farm error: " + ex.getMessage(), COLOR_FAIL);
                } finally {
                    System.setProperty("cinepolis.abort.requested", "false");
                    running = false; testThread = null;
                    SwingUtilities.invokeLater(() -> setRunningUi(false));
                }
                return null;
            }
        }.execute();
    }

    // ══════════════════════════════════════════════════════════════
    //  Log helpers
    // ══════════════════════════════════════════════════════════════

    private static void logLine(String icon, String message, Color color) {
        String ts = TS.format(new Date());
        log("[" + ts + "] " + icon + "  " + message + "\n", color, false);
    }

    private static void log(String text, Color color, boolean bold) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = logPane.getStyledDocument();
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, color);
                StyleConstants.setBold(a, bold);
                doc.insertString(doc.getLength(), text, a);
                logPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {}
        });
    }

    private static void redirectOutput() {
        OutputStream sink = new OutputStream() {
            private final StringBuilder buf = new StringBuilder();
            private boolean nl = true;
            @Override public void write(int b) {
                char c = (char)(b & 0xFF);
                synchronized (buf) {
                    if (nl) { buf.append("[").append(TS.format(new Date())).append("] "); nl = false; }
                    buf.append(c);
                    if (c == '\n') { nl = true; flush(); }
                }
            }
            @Override public void flush() {
                String line;
                synchronized (buf) { if (buf.length() == 0) return; line = buf.toString(); buf.setLength(0); }
                Color color = colorFor(line);
                String icon = color == COLOR_FAIL ? "✗" : color == COLOR_SKIP ? "⚠" : color == COLOR_OK ? "✓" : "ℹ";
                log(icon + "  " + line, color, false);
            }
        };
        PrintStream ps = new PrintStream(sink, true, StandardCharsets.UTF_8);
        System.setOut(ps); System.setErr(ps);
    }

    private static Color colorFor(String line) {
        String l = line.toLowerCase();
        if (l.contains("error") || l.contains("exception") || l.contains("failed") || l.contains("falló")) return COLOR_FAIL;
        if (l.contains("warn"))                                                                                        return COLOR_SKIP;
        if (l.contains("passed") || l.contains("exitoso"))                                                            return COLOR_OK;
        if (l.contains(" info ") || l.contains(" debug "))                                                            return TEXT_DIM;
        return TEXT_PRI;
    }

    // ─── Misc helpers ─────────────────────────────────────────────


    // ── Stats reset ───────────────────────────────────────────────

    private static void resetStats() {
        statPassed = 0; statFailed = 0; statSkipped = 0; statTotal = 0;
        SwingUtilities.invokeLater(() -> {
            passedVal.setText("0"); failedVal.setText("0");
            skippedVal.setText("0"); totalVal.setText("0");
            timeVal.setText("--");
        });
    }

    // ── Device config dialog ──────────────────────────────────────

    private static void showConfigDialog(Component anchor) {
        Properties props = readAppiumProps();
        String curDevice   = System.getProperty("deviceName",      props.getProperty("deviceName",      "Galaxy A56 5G"));
        String curVersion  = System.getProperty("platformVersion", props.getProperty("platformVersion", "15"));
        String curUdid     = System.getProperty("udid",            props.getProperty("udid",            ""));
        String curActivity = props.getProperty("appActivity", "");
        String curHub      = System.getProperty("appium.hub",      props.getProperty("appium.hub",      "http://127.0.0.1:4723/wd/hub"));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(anchor) instanceof java.awt.Frame f ? f : null,
                "Configuración del Dispositivo", true);
        int screenH = java.awt.Toolkit.getDefaultToolkit().getScreenSize().height;
        dialog.setSize(440, Math.min(880, screenH - 80));
        dialog.setLocationRelativeTo(anchor);
        dialog.setResizable(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_PANEL);

        // Header
        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        hdr.setBackground(BG_PANEL);
        hdr.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));
        JLabel hdrIco = new JLabel("⚙");
        hdrIco.setFont(new Font("Dialog", Font.BOLD, 18));
        hdrIco.setForeground(ACCENT);
        JLabel hdrTitle = new JLabel("Configuración del Dispositivo");
        hdrTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        hdrTitle.setForeground(TEXT_PRI);
        hdr.add(hdrIco); hdr.add(hdrTitle);

        // Fields – dispositivo + appium
        JTextField deviceField    = cfgField(curDevice);
        JTextField versionField   = cfgField(curVersion);
        JTextField udidField      = cfgField(curUdid);
        JTextField activityField  = cfgField(curActivity);
        activityField.setToolTipText("Ej: com.cinepolis.go/.MainActivity  (vacío = Appium detecta automáticamente)");
        JTextField hubField       = cfgField(curHub);

        // Fields – correo
        String curMailEnabled  = System.getProperty("mail.enabled",    props.getProperty("mail.enabled",    "NO"));
        String curNetlify      = System.getProperty("netlify.publish", props.getProperty("netlify.publish", "NO"));
        String curRecipients   = System.getProperty("mail.recipients", props.getProperty("mail.recipients", ""));
        JComboBox<String> mailBox     = cfgCombo(new String[]{"SI", "NO"}, curMailEnabled.equalsIgnoreCase("SI") ? "SI" : "NO");
        JComboBox<String> netlifyBox  = cfgCombo(new String[]{"SI", "NO"}, curNetlify.equalsIgnoreCase("SI")     ? "SI" : "NO");
        JTextField recipientsField    = cfgField(curRecipients);
        recipientsField.setToolTipText("Ej: qa@cinepolis.com, jefe@cinepolis.com");

        // Fields – AWS Device Farm
        String curDfEnabled       = System.getProperty("devicefarm.enabled",        props.getProperty("devicefarm.enabled",        "NO"));
        String curDfAccessKey     = System.getProperty("devicefarm.access.key.id",  props.getProperty("devicefarm.access.key.id",  ""));
        String curDfSecretKey     = System.getProperty("devicefarm.secret.access.key", props.getProperty("devicefarm.secret.access.key", ""));
        String curDfRegion        = System.getProperty("devicefarm.region",          props.getProperty("devicefarm.region",          "us-west-2"));
        String curDfProjectArn    = System.getProperty("devicefarm.project.arn",     props.getProperty("devicefarm.project.arn",     ""));
        String curDfDevicePoolArn = System.getProperty("devicefarm.device.pool.arn", props.getProperty("devicefarm.device.pool.arn", ""));
        String curDfApkPath       = System.getProperty("devicefarm.apk.path",        props.getProperty("devicefarm.apk.path",        ""));
        JComboBox<String> dfEnabledBox    = cfgCombo(new String[]{"NO", "SI"}, curDfEnabled.equalsIgnoreCase("SI") ? "SI" : "NO");
        JTextField dfAccessKeyField       = cfgField(curDfAccessKey);
        JTextField dfSecretKeyField       = cfgField(curDfSecretKey);
        JTextField dfRegionField          = cfgField(curDfRegion);
        JTextField dfProjectArnField      = cfgField(curDfProjectArn);
        JTextField dfDevicePoolArnField   = cfgField(curDfDevicePoolArn);
        JTextField dfApkPathField         = cfgField(curDfApkPath);
        dfSecretKeyField.setToolTipText("AWS Secret Access Key de tu cuenta IAM");
        dfProjectArnField.setToolTipText("arn:aws:devicefarm:us-west-2:ACCOUNT:project:GUID");
        dfDevicePoolArnField.setToolTipText("ARN del Device Pool (usa 'Cargar Device Pools' para obtenerlo)");
        dfApkPathField.setToolTipText("Ruta absoluta al APK de la app a probar");

        // Fields – Genymotion Cloud
        String curGmEnabled    = System.getProperty("genymotion.enabled",    props.getProperty("genymotion.enabled",    "NO"));
        String curGmEmail      = System.getProperty("genymotion.email",      props.getProperty("genymotion.email",      ""));
        String curGmPassword   = System.getProperty("genymotion.password",   props.getProperty("genymotion.password",   ""));
        String curGmApiToken   = System.getProperty("genymotion.api.token",  props.getProperty("genymotion.api.token",  ""));
        String curGmRecipeUuid = System.getProperty("genymotion.recipe.uuid",props.getProperty("genymotion.recipe.uuid",""));
        JComboBox<String> gmEnabledBox  = cfgCombo(new String[]{"NO", "SI"}, curGmEnabled.equalsIgnoreCase("SI") ? "SI" : "NO");
        JTextField gmEmailField         = cfgField(curGmEmail);
        JPasswordField gmPasswordField  = new JPasswordField(curGmPassword);
        gmPasswordField.setBackground(BG_CARD);
        gmPasswordField.setForeground(TEXT_PRI);
        gmPasswordField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        gmPasswordField.setCaretColor(TEXT_PRI);
        gmPasswordField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 75, 130)),
            new EmptyBorder(5, 8, 5, 8)));
        JTextField gmApiTokenField      = cfgField(curGmApiToken);
        JTextField gmRecipeUuidField    = cfgField(curGmRecipeUuid);
        gmPasswordField.setToolTipText("Contraseña de tu cuenta Genymotion Cloud");
        gmApiTokenField.setToolTipText("Token API (alternativa a email+password — genera en console.geny.io)");
        gmRecipeUuidField.setToolTipText("UUID del dispositivo virtual (usa 'Cargar dispositivos' para obtenerlo)");

        // Field – IA
        String curApiKey  = System.getProperty("claude.api.key", props.getProperty("claude.api.key", ""));
        JTextField apiKeyField = cfgField(curApiKey);

        // Content – scrollable
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_PANEL);
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        content.add(cfgSection("📱  DISPOSITIVO ANDROID"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Device Name",      deviceField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Platform Version", versionField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("UDID",             udidField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("App Activity",     activityField));
        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // ── Verify button ──────────────────────────────────────────
        RoundedButton verifyBtn = new RoundedButton("🔍  Verificar conexión", new Color(30, 70, 50), 6);
        verifyBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        verifyBtn.setForeground(Color.WHITE);
        verifyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        verifyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel verifyResult = new JLabel(" ");
        verifyResult.setFont(new Font("Monospaced", Font.PLAIN, 11));
        verifyResult.setAlignmentX(Component.LEFT_ALIGNMENT);

        verifyBtn.addActionListener(ev -> {
            String vUdid     = udidField.getText().trim();
            String vHub      = hubField.getText().trim();
            String vActivity = activityField.getText().trim();
            String vPackage  = "com.cinepolis.go";

            verifyBtn.setText("Verificando...");
            verifyBtn.setEnabled(false);
            verifyResult.setText(" ");

            new SwingWorker<java.util.List<String[]>, Void>() {
                @Override protected java.util.List<String[]> doInBackground() {
                    java.util.List<String[]> results = new java.util.ArrayList<>();

                    // 1. ADB: device connected
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"adb", "devices"});
                        String out = new String(p.getInputStream().readAllBytes()).trim();
                        if (vUdid.isBlank()) {
                            results.add(new String[]{"WARN", "UDID vacío — no se puede verificar dispositivo"});
                        } else if (out.contains(vUdid)) {
                            results.add(new String[]{"OK", "Dispositivo encontrado (UDID: " + vUdid + ")"});
                        } else {
                            results.add(new String[]{"FAIL", "UDID no encontrado en ADB — ¿dispositivo conectado?"});
                        }
                    } catch (Exception e) {
                        results.add(new String[]{"FAIL", "ADB no disponible: " + e.getMessage()});
                    }

                    // 2. ADB: app installed
                    if (!vUdid.isBlank()) {
                        try {
                            Process p = Runtime.getRuntime().exec(
                                new String[]{"adb", "-s", vUdid, "shell", "pm", "list", "packages", vPackage});
                            String out = new String(p.getInputStream().readAllBytes()).trim();
                            if (out.contains(vPackage)) {
                                results.add(new String[]{"OK", "App instalada (" + vPackage + ")"});
                            } else {
                                results.add(new String[]{"FAIL", "App NO instalada en el dispositivo: " + vPackage});
                            }
                        } catch (Exception e) {
                            results.add(new String[]{"WARN", "No se pudo verificar la app: " + e.getMessage()});
                        }
                    }

                    // 3. ADB: appActivity valid (only if provided)
                    if (!vActivity.isBlank() && !vUdid.isBlank()) {
                        try {
                            Process p = Runtime.getRuntime().exec(
                                new String[]{"adb", "-s", vUdid, "shell", "dumpsys", "package", vPackage});
                            String out = new String(p.getInputStream().readAllBytes());
                            String actShort = vActivity.contains("/") ? vActivity.split("/")[1] : vActivity;
                            if (out.contains(actShort) || out.contains(vActivity)) {
                                results.add(new String[]{"OK", "App Activity válida: " + vActivity});
                            } else {
                                results.add(new String[]{"FAIL", "App Activity no encontrada: " + vActivity});
                            }
                        } catch (Exception e) {
                            results.add(new String[]{"WARN", "No se pudo verificar Activity: " + e.getMessage()});
                        }
                    }

                    // 4. Appium hub reachable — tries v2 /status then v1 /wd/hub/status
                    try {
                        String base = vHub.replaceAll("(/wd/hub)?$", "");
                        String[] candidates = { base + "/status", base + "/wd/hub/status" };
                        int lastCode = -1;
                        boolean appiumOk = false;
                        for (String statusUrl : candidates) {
                            try {
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                    new java.net.URL(statusUrl).openConnection();
                                conn.setConnectTimeout(3000);
                                conn.setReadTimeout(3000);
                                lastCode = conn.getResponseCode();
                                if (lastCode == 200) { appiumOk = true; break; }
                            } catch (Exception ignored) {}
                        }
                        if (appiumOk) {
                            results.add(new String[]{"OK", "Appium accesible en: " + vHub});
                        } else if (lastCode > 0) {
                            results.add(new String[]{"WARN", "Appium respondió HTTP " + lastCode + " — puede estar iniciando"});
                        } else {
                            results.add(new String[]{"FAIL", "Appium NO responde en: " + vHub + " — ¿está corriendo?"});
                        }
                    } catch (Exception e) {
                        results.add(new String[]{"FAIL", "Appium NO accesible en: " + vHub});
                    }

                    return results;
                }

                @Override protected void done() {
                    try {
                        java.util.List<String[]> res = get();
                        verifyBtn.setEnabled(true);

                        boolean anyFail = res.stream().anyMatch(r -> "FAIL".equals(r[0]));
                        boolean anyWarn = res.stream().anyMatch(r -> "WARN".equals(r[0]));

                        StringBuilder html = new StringBuilder("<html>");
                        for (String[] r : res) {
                            String color = "FAIL".equals(r[0]) ? "#ff5555"
                                         : "WARN".equals(r[0]) ? "#ffaa00" : "#55ff99";
                            String icon  = "FAIL".equals(r[0]) ? "✗" : "WARN".equals(r[0]) ? "⚠" : "✓";
                            html.append("<font color='").append(color).append("'>")
                                .append(icon).append(" ").append(r[1]).append("</font><br>");
                        }
                        html.append("</html>");
                        verifyResult.setText(html.toString());

                        verifyBtn.setText(anyFail ? "✗  Hay errores — revisa los campos"
                                        : anyWarn ? "⚠  Verificado con advertencias"
                                                  : "✓  Todo correcto");
                    } catch (Exception ex) {
                        verifyBtn.setEnabled(true);
                        verifyBtn.setText("✗  Error al verificar");
                        verifyResult.setText("<html><font color='#ff5555'>✗ " + ex.getMessage() + "</font></html>");
                    }
                }
            }.execute();
        });

        content.add(verifyBtn);
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(verifyResult);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(cfgSection("🌐  SERVIDOR APPIUM"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Appium Hub URL",   hubField));
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(cfgSection("☁️  AWS DEVICE FARM · NUBE DE DISPOSITIVOS"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("¿Usar Device Farm?",   dfEnabledBox));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Access Key ID",         dfAccessKeyField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Secret Access Key",     dfSecretKeyField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Región",                dfRegionField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Project ARN",           dfProjectArnField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Device Pool ARN",       dfDevicePoolArnField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("APK Path",              dfApkPathField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));

        RoundedButton loadPoolsBtn = new RoundedButton("🔄  Cargar Device Pools", new Color(30, 80, 30), 6);
        loadPoolsBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        loadPoolsBtn.setForeground(Color.WHITE);
        loadPoolsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadPoolsBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        loadPoolsBtn.addActionListener(ev -> {
            String ak  = dfAccessKeyField.getText().trim();
            String sk  = dfSecretKeyField.getText().trim();
            String arn = dfProjectArnField.getText().trim();
            String reg = dfRegionField.getText().trim();
            if (ak.isBlank() || sk.isBlank() || arn.isBlank()) {
                JOptionPane.showMessageDialog(dialog,
                    "Ingresa Access Key ID, Secret Access Key y Project ARN primero.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            loadPoolsBtn.setText("Cargando...");
            loadPoolsBtn.setEnabled(false);
            new SwingWorker<java.util.List<String[]>, Void>() {
                @Override protected java.util.List<String[]> doInBackground() throws Exception {
                    return AWSDeviceFarmRunner.listDevicePools(arn, ak, sk, reg.isBlank() ? "us-west-2" : reg);
                }
                @Override protected void done() {
                    try {
                        java.util.List<String[]> pools = get();
                        loadPoolsBtn.setEnabled(true);
                        if (pools.isEmpty()) {
                            loadPoolsBtn.setText("❌  Sin device pools — verifica credenciales");
                        } else {
                            loadPoolsBtn.setText("✓  " + pools.size() + " pools disponibles");
                            StringBuilder sb = new StringBuilder();
                            for (String[] pool : pools)
                                sb.append(pool[1]).append(": ").append(pool[0]).append("\n");
                            JOptionPane.showMessageDialog(dialog,
                                "Device Pools encontrados:\n\n" + sb,
                                "Device Pools", JOptionPane.INFORMATION_MESSAGE);
                            if (!pools.isEmpty() && dfDevicePoolArnField.getText().isBlank())
                                dfDevicePoolArnField.setText(pools.get(0)[0]);
                        }
                    } catch (Exception ex) {
                        loadPoolsBtn.setEnabled(true);
                        loadPoolsBtn.setText("❌  Error: " + ex.getMessage());
                    }
                }
            }.execute();
        });
        content.add(loadPoolsBtn);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(cfgSection("📱  GENYMOTION CLOUD · EMULADORES EN LA NUBE"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("¿Usar Genymotion Cloud?", gmEnabledBox));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Email",                   gmEmailField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Password",                gmPasswordField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("API Token (opcional)",    gmApiTokenField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Recipe UUID",             gmRecipeUuidField));
        content.add(Box.createRigidArea(new Dimension(0, 8)));

        RoundedButton loadGenymotionBtn = new RoundedButton("🔄  Cargar dispositivos Genymotion", new Color(30, 55, 90), 6);
        loadGenymotionBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        loadGenymotionBtn.setForeground(Color.WHITE);
        loadGenymotionBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadGenymotionBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        loadGenymotionBtn.addActionListener(ev -> {
            String em  = gmEmailField.getText().trim();
            String pw  = new String(gmPasswordField.getPassword()).trim();
            String tok = gmApiTokenField.getText().trim();
            if (em.isBlank() && tok.isBlank()) {
                JOptionPane.showMessageDialog(dialog,
                    "Ingresa Email + Password o API Token primero.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            loadGenymotionBtn.setText("Cargando...");
            loadGenymotionBtn.setEnabled(false);
            new SwingWorker<java.util.List<String[]>, Void>() {
                @Override protected java.util.List<String[]> doInBackground() throws Exception {
                    return GenyCloudRunner.listRecipes(em, pw, tok.isBlank() ? "" : tok);
                }
                @Override protected void done() {
                    try {
                        java.util.List<String[]> recipes = get();
                        loadGenymotionBtn.setEnabled(true);
                        if (recipes.isEmpty()) {
                            loadGenymotionBtn.setText("❌  Sin dispositivos — verifica credenciales");
                        } else {
                            loadGenymotionBtn.setText("✓  " + recipes.size() + " dispositivos cargados");
                            populateGenymotionCombo(recipes);
                            if (gmRecipeUuidField.getText().isBlank())
                                gmRecipeUuidField.setText(recipes.get(0)[0]);
                        }
                    } catch (Exception ex) {
                        loadGenymotionBtn.setEnabled(true);
                        loadGenymotionBtn.setText("❌  Error: " + ex.getMessage());
                    }
                }
            }.execute();
        });
        content.add(loadGenymotionBtn);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(cfgSection("📧  CORREO Y NOTIFICACIONES"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("¿Enviar correos?",        mailBox));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("¿Publicar URL Dinámica?", netlifyBox));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Destinatarios (separados por coma)", recipientsField));
        content.add(Box.createRigidArea(new Dimension(0, 16)));
        content.add(cfgSection("🤖  ASISTENTE IA"));
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(cfgRow("Claude API Key", apiKeyField));

        JScrollPane contentScroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setBorder(null);
        contentScroll.setBackground(BG_PANEL);
        contentScroll.getViewport().setBackground(BG_PANEL);
        contentScroll.getVerticalScrollBar().setUnitIncrement(10);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(BG_PANEL);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        RoundedButton cancelBtn = new RoundedButton("Cancelar", BG_CARD, 6);
        cancelBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        cancelBtn.setForeground(TEXT_DIM);
        cancelBtn.setPreferredSize(new Dimension(100, 32));
        cancelBtn.addActionListener(e -> dialog.dispose());

        RoundedButton saveBtn = new RoundedButton("✓  Guardar", COLOR_OK, 6);
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setPreferredSize(new Dimension(120, 32));
        saveBtn.addActionListener(e -> {
            saveDeviceConfig(
                deviceField.getText().trim(),    versionField.getText().trim(),
                udidField.getText().trim(),      activityField.getText().trim(),
                hubField.getText().trim(),
                (String) mailBox.getSelectedItem(),
                (String) netlifyBox.getSelectedItem(),
                recipientsField.getText().trim(),
                apiKeyField.getText().trim(),
                (String) dfEnabledBox.getSelectedItem(),
                dfAccessKeyField.getText().trim(),
                dfSecretKeyField.getText().trim(),
                dfRegionField.getText().trim(),
                dfProjectArnField.getText().trim(),
                dfDevicePoolArnField.getText().trim(),
                dfApkPathField.getText().trim(),
                (String) gmEnabledBox.getSelectedItem(),
                gmEmailField.getText().trim(),
                new String(gmPasswordField.getPassword()).trim(),
                gmApiTokenField.getText().trim(),
                gmRecipeUuidField.getText().trim());
            boolean gmOn = "SI".equalsIgnoreCase((String) gmEnabledBox.getSelectedItem());
            String logDevice = gmOn
                ? "Genymotion: " + gmRecipeUuidField.getText().trim()
                : deviceField.getText().trim() + " | UDID: " + udidField.getText().trim();
            logLine("⚙", "Config guardada · " + logDevice, ACCENT);
            dialog.dispose();
        });

        footer.add(cancelBtn); footer.add(saveBtn);
        root.add(hdr,           BorderLayout.NORTH);
        root.add(contentScroll, BorderLayout.CENTER);
        root.add(footer,        BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private static JTextField cfgField(String value) {
        JTextField f = new JTextField(value);
        f.setBackground(BG_CARD);
        f.setForeground(TEXT_PRI);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setCaretColor(TEXT_PRI);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 75, 130)),
            new EmptyBorder(5, 8, 5, 8)));
        return f;
    }

    private static JComboBox<String> cfgCombo(String[] options, String selected) {
        JComboBox<String> box = new JComboBox<>(options);
        box.setSelectedItem(selected);
        box.setBackground(Color.WHITE);
        box.setForeground(Color.BLACK);
        box.setFont(new Font("SansSerif", Font.BOLD, 12));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 75, 130)),
            new EmptyBorder(3, 6, 3, 6)));
        return box;
    }

    private static void populateGenymotionCombo(java.util.List<String[]> recipes) {
        genymotionRecipeMap.clear();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String[] r : recipes) {
            String display = r[1] + (r[2].isBlank() ? "" : " (Android " + r[2] + ")");
            genymotionRecipeMap.put(display, r[0]);
            names.add(display);
        }
        SwingUtilities.invokeLater(() -> {
            if (deviceCombo == null) return;
            deviceCombo.removeAllItems();
            for (String n : names) deviceCombo.addItem(n);
            if (!names.isEmpty()) {
                deviceCombo.setSelectedIndex(0);
                System.setProperty("genymotion.recipe.uuid", recipes.get(0)[0]);
            }
        });
    }

    private static JLabel cfgSection(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(BLUE_TITLE);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel cfgRow(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(TEXT_LBL);
        p.add(lbl,   BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static Properties readAppiumProps() {
        Properties p = new Properties();
        File f = new File("appium.properties");
        if (!f.exists()) f = new File("src/test/resources/appium.properties");
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); }
            catch (Exception ignored) {}
        }
        return p;
    }

    private static void saveDeviceConfig(String device, String version, String udid, String activity,
                                          String hub, String mailEnabled, String netlifyPublish,
                                          String mailRecipients, String apiKey,
                                          String dfEnabled, String dfAccessKey, String dfSecretKey, String dfRegion,
                                          String dfProjectArn, String dfDevicePoolArn, String dfApkPath,
                                          String gmEnabled, String gmEmail, String gmPassword,
                                          String gmApiToken, String gmRecipeUuid) {
        System.setProperty("deviceName",                device);
        System.setProperty("platformVersion",           version);
        System.setProperty("udid",                      udid);
        if (activity.isBlank()) System.clearProperty("appActivity");
        else System.setProperty("appActivity", activity);
        System.setProperty("appium.hub",                hub);
        System.setProperty("mail.enabled",              mailEnabled);
        System.setProperty("netlify.publish",           netlifyPublish);
        System.setProperty("mail.recipients",           mailRecipients);
        System.setProperty("claude.api.key",            apiKey);
        System.setProperty("devicefarm.enabled",        dfEnabled);
        System.setProperty("devicefarm.access.key.id",  dfAccessKey);
        System.setProperty("devicefarm.secret.access.key", dfSecretKey);
        System.setProperty("devicefarm.region",         dfRegion);
        System.setProperty("devicefarm.project.arn",    dfProjectArn);
        System.setProperty("devicefarm.device.pool.arn", dfDevicePoolArn);
        System.setProperty("devicefarm.apk.path",       dfApkPath);
        System.setProperty("genymotion.enabled",        gmEnabled);
        System.setProperty("genymotion.email",          gmEmail);
        System.setProperty("genymotion.password",       gmPassword);
        System.setProperty("genymotion.api.token",      gmApiToken);
        System.setProperty("genymotion.recipe.uuid",    gmRecipeUuid);
        SwingUtilities.invokeLater(() -> {
            if (deviceCombo != null && genymotionRecipeMap.isEmpty()) {
                deviceCombo.removeAllItems();
                deviceCombo.addItem(device);
                deviceCombo.setSelectedItem(device);
            }
        });

        File f = new File("appium.properties");
        if (!f.exists()) f = new File("src/test/resources/appium.properties");
        if (!f.exists()) return;
        try {
            // Map key → value to write; LinkedHashMap preserves insertion order for appending
            Map<String, String> vals = new LinkedHashMap<>();
            vals.put("deviceName",                   device);
            vals.put("platformVersion",              version);
            vals.put("udid",                         udid);
            vals.put("appActivity",                  activity);
            vals.put("appium.hub",                   hub);
            vals.put("mail.enabled",                 mailEnabled);
            vals.put("netlify.publish",              netlifyPublish);
            vals.put("mail.recipients",              mailRecipients);
            vals.put("claude.api.key",               apiKey);
            vals.put("devicefarm.enabled",           dfEnabled);
            vals.put("devicefarm.access.key.id",     dfAccessKey);
            vals.put("devicefarm.secret.access.key", dfSecretKey);
            vals.put("devicefarm.region",            dfRegion);
            vals.put("devicefarm.project.arn",       dfProjectArn);
            vals.put("devicefarm.device.pool.arn",   dfDevicePoolArn);
            vals.put("devicefarm.apk.path",          dfApkPath);
            vals.put("genymotion.enabled",           gmEnabled);
            vals.put("genymotion.email",             gmEmail);
            vals.put("genymotion.password",          gmPassword);
            vals.put("genymotion.api.token",         gmApiToken);
            vals.put("genymotion.recipe.uuid",       gmRecipeUuid);

            StringBuilder sb = new StringBuilder();
            sb.append("# Config Android + UiAutomator2\n");
            sb.append("platformName=Android\n");
            sb.append("deviceName=").append(vals.get("deviceName")).append("\n");
            sb.append("platformVersion=").append(vals.get("platformVersion")).append("\n");
            sb.append("udid=").append(vals.get("udid")).append("\n");
            sb.append("\n");
            sb.append("# App target\n");
            sb.append("appPackage=com.cinepolis.go\n");
            sb.append("appActivity=").append(vals.get("appActivity")).append("\n");
            sb.append("\n");
            sb.append("automationName=UiAutomator2\n");
            sb.append("noReset=true\n");
            sb.append("fullReset=false\n");
            sb.append("newCommandTimeout=180\n");
            sb.append("autoGrantPermissions=true\n");
            sb.append("\n");
            sb.append("# Appium hub\n");
            sb.append("appium.hub=").append(vals.get("appium.hub")).append("\n");
            sb.append("\n");
            sb.append("# AWS Device Farm\n");
            sb.append("devicefarm.enabled=").append(vals.get("devicefarm.enabled")).append("\n");
            sb.append("devicefarm.region=").append(vals.get("devicefarm.region")).append("\n");
            sb.append("devicefarm.access.key.id=").append(vals.get("devicefarm.access.key.id")).append("\n");
            sb.append("devicefarm.secret.access.key=").append(vals.get("devicefarm.secret.access.key")).append("\n");
            sb.append("devicefarm.project.arn=").append(vals.get("devicefarm.project.arn")).append("\n");
            sb.append("devicefarm.device.pool.arn=").append(vals.get("devicefarm.device.pool.arn")).append("\n");
            sb.append("devicefarm.apk.path=").append(vals.get("devicefarm.apk.path")).append("\n");
            sb.append("\n");
            sb.append("# Genymotion Cloud\n");
            sb.append("genymotion.enabled=").append(vals.get("genymotion.enabled")).append("\n");
            sb.append("genymotion.email=").append(vals.get("genymotion.email")).append("\n");
            sb.append("genymotion.password=").append(vals.get("genymotion.password")).append("\n");
            sb.append("genymotion.api.token=").append(vals.get("genymotion.api.token")).append("\n");
            sb.append("genymotion.recipe.uuid=").append(vals.get("genymotion.recipe.uuid")).append("\n");
            sb.append("\n");
            sb.append("mail.enabled=").append(vals.get("mail.enabled")).append("\n");
            sb.append("netlify.publish=").append(vals.get("netlify.publish")).append("\n");
            sb.append("mail.recipients=").append(vals.get("mail.recipients")).append("\n");
            sb.append("claude.api.key=").append(vals.get("claude.api.key")).append("\n");
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
                bw.write(sb.toString());
            }
        } catch (Exception ignored) {}
    }

    private static String normalize(String s) {
        return s.toLowerCase()
            .replace(" ", "")
            .replace("á","a").replace("é","e").replace("í","i")
            .replace("ó","o").replace("ú","u").replace("ñ","n");
    }

    private static String normalizeCinema(String n) {
        return normalize(n.replace("Cinépolis ","").replace("Cinepolis ",""));
    }

    // ── Image helpers ─────────────────────────────────────────────

    private static ImageIcon loadScaledIcon(String path, int targetH) {
        try (InputStream is = Main.class.getResourceAsStream(path)) {
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            ImageIcon raw = new ImageIcon(bytes);
            if (raw.getIconWidth() <= 0) return null;
            int w = (int) Math.round(raw.getIconWidth() * ((double) targetH / raw.getIconHeight()));
            return new ImageIcon(raw.getImage().getScaledInstance(w, targetH, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }

    private static ImageIcon loadCountryIcon(String country, int h) {
        String lower = Character.toLowerCase(country.charAt(0)) + country.substring(1);
        for (String ext : new String[]{".ico", ".png"}) {
            ImageIcon ico = loadScaledIcon("/logos/" + country + ext, h);
            if (ico != null) return ico;
            ico = loadScaledIcon("/logos/" + lower + ext, h);
            if (ico != null) return ico;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  Inner classes
    // ══════════════════════════════════════════════════════════════

    record CardData(String icon, Color iconColor, String title, String description,
                    Color btnColor, Runnable action) {}

    record TestRow(String label, String description, Runnable action) {}

    static class RoundedPanel extends JPanel {
        private int   radius;
        private Color bg;
        RoundedPanel(int r, Color bg) { this.radius = r; this.bg = bg; setOpaque(false); }
        void setBg(Color c) { this.bg = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), arc = radius * 2;
            // Layered drop shadow (darker rects at offset, behind fill)
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRoundRect(4, 5, w - 4, h - 5, arc, arc);
            g2.setColor(new Color(0, 0, 0, 35));
            g2.fillRoundRect(2, 3, w - 2, h - 3, arc, arc);
            // Main gradient fill (subtle top-lighter)
            g2.setPaint(new GradientPaint(0, 0,
                new Color(Math.min(255, bg.getRed() + 10), Math.min(255, bg.getGreen() + 10), Math.min(255, bg.getBlue() + 18)),
                0, h, bg));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            // Top inner highlight streak
            g2.setColor(new Color(255, 255, 255, 16));
            g2.fillRoundRect(1, 1, w - 2, h / 4, arc, arc);
            // Border glow
            g2.setColor(new Color(255, 255, 255, 14));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class CircleIcon extends JPanel {
        private final String label;
        private final Color  cc;
        private final int    size;
        CircleIcon(String lbl, Color cc, int size) {
            this.label = lbl; this.cc = cc; this.size = size;
            setPreferredSize(new Dimension(size + 10, size + 10));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = (getWidth()  - size) / 2;
            int y = (getHeight() - size) / 2;
            g2.setColor(new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), 40));
            g2.fillOval(x - 6, y - 6, size + 12, size + 12);
            g2.setColor(cc);
            g2.fillOval(x, y, size, size);
            g2.setFont(new Font("Dialog", Font.BOLD, label.codePointCount(0, label.length()) > 2 ? 13 : 20));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, x + (size - fm.stringWidth(label)) / 2,
                                 y + (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static class RoundedButton extends JButton {
        private Color accent;
        private final int r;
        private boolean hov = false;
        RoundedButton(String text, Color accent, int r) {
            super(text); this.accent = accent; this.r = r;
            setContentAreaFilled(false); setBorderPainted(false);
            setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
            });
        }
        void setAccent(Color c) { this.accent = c; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Gradient: brighter top → accent bottom
            Color top = new Color(
                Math.min(255, accent.getRed()   + (hov ? 50 : 28)),
                Math.min(255, accent.getGreen() + (hov ? 50 : 28)),
                Math.min(255, accent.getBlue()  + (hov ? 50 : 28)));
            Color bot = hov ? new Color(
                Math.min(255, accent.getRed()   + 18),
                Math.min(255, accent.getGreen() + 18),
                Math.min(255, accent.getBlue()  + 18)) : accent;
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
            g2.fillRoundRect(0, 0, w, h, r * 2, r * 2);
            // Inner top highlight streak
            g2.setColor(new Color(255, 255, 255, hov ? 55 : 30));
            g2.fillRoundRect(2, 1, w - 4, h / 2, r * 2, r * 2);
            // Outer border glow
            g2.setColor(new Color(255, 255, 255, hov ? 45 : 18));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, r * 2, r * 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }
        @Override public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true); }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension d = layoutSize(target, false); d.width -= (getHgap() + 1); return d;
        }
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int maxW = target.getSize().width;
                Container c = target;
                while (c.getSize().width == 0 && c.getParent() != null) c = c.getParent();
                maxW = c.getSize().width;
                if (maxW == 0) maxW = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets ins = target.getInsets();
                maxW -= ins.left + ins.right + hgap * 2;
                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0, n = target.getComponentCount(); i < n; i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > maxW) { addRowW(dim, rowW, rowH, vgap); rowW = 0; rowH = 0; }
                    if (rowW != 0) rowW += hgap;
                    rowW += d.width; rowH = Math.max(rowH, d.height);
                }
                addRowW(dim, rowW, rowH, vgap);
                dim.width  += ins.left + ins.right + hgap * 2;
                dim.height += ins.top  + ins.bottom + vgap * 2;
                return dim;
            }
        }
        private void addRowW(Dimension dim, int rowW, int rowH, int vgap) {
            dim.width = Math.max(dim.width, rowW);
            if (dim.height > 0) dim.height += vgap;
            dim.height += rowH;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Console fallback (headless / CI)
    // ══════════════════════════════════════════════════════════════

    private static final Scanner SCANNER = new Scanner(System.in);

    private static void runConsole() {
        while (true) {
            System.out.println("\n  === CINEPOLIS AUTOMATION QA ===");
            System.out.println("  Selecciona un pais:");
            for (int i = 0; i < COUNTRIES.length; i++)
                System.out.println("  " + (i + 1) + ". " + COUNTRIES[i][2]);
            System.out.println("  0. Salir");
            System.out.print("  Elige: ");
            String choice = readLine();
            if (choice.equals("0")) { System.out.println("  Hasta luego."); return; }
            try {
                int idx = Integer.parseInt(choice) - 1;
                if (idx >= 0 && idx < COUNTRIES.length) {
                    selectedCountry = COUNTRIES[idx][2];
                    System.setProperty("country", selectedCountry);
                    consoleCountryMenu(selectedCountry);
                } else System.out.println("  Opcion no valida.");
            } catch (NumberFormatException e) { System.out.println("  Opcion no valida."); }
        }
    }

    private static void consoleCountryMenu(String c) {
        switch (c) {
            case "México"    -> consoleMenuMexico();
            case "Argentina" -> consoleMenuArgentina();
            case "Chile"     -> consoleMenuChile();
            default          -> System.out.println("\n  Sin tests para " + c + ". Proximamente.");
        }
    }

    private static void consoleMenuMexico() {
        while (true) {
            System.out.println("\n  Mexico: 1.Asientos  2.Alimentos  3.E2E  4.TODO  0.Volver");
            System.out.print("  Elige: ");
            switch (readLine()) {
                case "1" -> consoleMenuAsientos();
                case "2" -> consoleMenuAlimentos();
                case "3" -> consoleRun("E2E", selectClass("tests.México.E2E.FlujosCompraNoLogin"));
                case "4" -> consoleRun("Completo", selectPackage("tests.México"));
                case "0" -> { return; }
                default  -> System.out.println("  Opcion no valida.");
            }
        }
    }

    private static void consoleMenuArgentina() {
        String[] cities = ARGENTINA_CITIES.keySet().toArray(new String[0]);
        while (true) {
            System.out.println("\n  Argentina - Ciudades:");
            for (int i = 0; i < cities.length; i++) System.out.println("  " + (i + 1) + ". " + cities[i]);
            System.out.println("  " + (cities.length + 1) + ". Ejecutar TODO");
            System.out.println("  0. Volver");
            System.out.print("  Elige: ");
            String ch = readLine();
            if (ch.equals("0")) return;
            if (ch.equals(String.valueOf(cities.length + 1))) { consoleRun("Todo", selectClass("tests.Argentina.NoAfectacionArgentina")); continue; }
            try {
                int idx = Integer.parseInt(ch) - 1;
                if (idx >= 0 && idx < cities.length) consoleMenuCity(cities[idx], ARGENTINA_CITIES.get(cities[idx]));
                else System.out.println("  Opcion no valida.");
            } catch (NumberFormatException e) { System.out.println("  Opcion no valida."); }
        }
    }

    private static void consoleMenuChile() {
        final String cls = "tests.Chile.NoAfectacionChile";
        while (true) {
            System.out.println("\n  Chile - Cines:");
            for (int i = 0; i < CHILE_CINES.length; i++)
                System.out.println("  " + (i + 1) + ". " + CHILE_CINES[i][0] + " (" + CHILE_CINES[i][1] + ")");
            System.out.println("  " + (CHILE_CINES.length + 1) + ". Ejecutar TODO");
            System.out.println("  0. Volver");
            System.out.print("  Elige: ");
            String ch = readLine();
            if (ch.equals("0")) return;
            if (ch.equals(String.valueOf(CHILE_CINES.length + 1))) { consoleRun("Todo Chile", selectClass(cls)); continue; }
            try {
                int idx = Integer.parseInt(ch) - 1;
                if (idx >= 0 && idx < CHILE_CINES.length) {
                    String sfx = CHILE_CINES[idx][2];
                    consoleRun(CHILE_CINES[idx][0],
                        selectMethod(cls, "compraTicket"   + sfx),
                        selectMethod(cls, "compraMix"      + sfx),
                        selectMethod(cls, "compraAlimento" + sfx));
                } else System.out.println("  Opcion no valida.");
            } catch (NumberFormatException e) { System.out.println("  Opcion no valida."); }
        }
    }

    private static void consoleMenuCity(String city, String[] cinemas) {
        while (true) {
            System.out.println("\n  " + city + " - Cines:");
            for (int i = 0; i < cinemas.length; i++) System.out.println("  " + (i + 1) + ". " + cinemas[i]);
            System.out.println("  " + (cinemas.length + 1) + ". Todos");
            System.out.println("  0. Volver");
            System.out.print("  Elige: ");
            String ch = readLine();
            if (ch.equals("0")) return;
            if (ch.equals(String.valueOf(cinemas.length + 1))) { consoleRun("Todos - " + city, argCitySelectors(city)); continue; }
            try {
                int idx = Integer.parseInt(ch) - 1;
                if (idx >= 0 && idx < cinemas.length) {
                    String sfx = ARGENTINA_CINEMA_SUFFIX.getOrDefault(cinemas[idx], "");
                    consoleRun(cinemas[idx],
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraTicket" + sfx),
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraMix"    + sfx),
                        selectMethod("tests.Argentina.NoAfectacionArgentina", "compraFood"   + sfx));
                } else System.out.println("  Opcion no valida.");
            } catch (NumberFormatException e) { System.out.println("  Opcion no valida."); }
        }
    }

    private static void consoleMenuAsientos() {
        final String C = "tests.México.asientos.SeleccionAsientos";
        System.out.println("\n  1.Todos 2.1Asiento 3.Multiples 4.Consecutivos 5.Selec/Deselec 6.+10 7.Horario 8.3D 9.Especial 10.Junior");
        System.out.print("  Elige: ");
        switch (readLine()) {
            case "1"  -> consoleRun("Todos",       selectClass(C));
            case "2"  -> consoleRun("1 Asiento",   selectMethod(C, "seleccion1Asiento"));
            case "3"  -> consoleRun("Multiples",   selectMethod(C, "seleccionMultiplesAsientos"));
            case "4"  -> consoleRun("Consecutivos",selectMethod(C, "seleccionAsientosConsecutivos"));
            case "5"  -> consoleRun("Sel/Desel",   selectMethod(C, "seleccionAsientosYDeseleccion"));
            case "6"  -> consoleRun("Mas de 10",   selectMethod(C, "seleccion11Asientos"));
            case "7"  -> consoleRun("Horario",     selectMethod(C, "cambioHorarioAsientos"));
            case "8"  -> consoleRun("3D",          selectMethod(C, "asientos3D"));
            case "9"  -> consoleRun("Especial",    selectMethod(C, "alertaAsientoEspecial"));
            case "10" -> consoleRun("Sala Junior", selectMethod(C, "asientosSalaJunior"));
        }
    }

    private static void consoleMenuAlimentos() {
        System.out.println("\n  1.Todos 2.Tradicional 3.Atmosfera 4.VIP 5.CoffeeTree 6.MiCine");
        System.out.print("  Elige: ");
        switch (readLine()) {
            case "1" -> consoleRun("Todos",       selectPackage("tests.México.alimentos"));
            case "2" -> consoleRun("Tradicional", selectClass("tests.México.alimentos.MenuTradicional"));
            case "3" -> consoleRun("Atmosfera",   selectClass("tests.México.alimentos.MenuAtmosfera"));
            case "4" -> consoleRun("VIP",         selectClass("tests.México.alimentos.MenuVIP"));
            case "5" -> consoleRun("CoffeeTree",  selectClass("tests.México.alimentos.MenuCoffeTree"));
            case "6" -> consoleRun("MiCine",      selectClass("tests.México.alimentos.MenuMiCine"));
        }
    }

    private static void consoleRun(String name, DiscoverySelector... selectors) {
        String label = selectedCountry.isEmpty() ? name : name + " [" + selectedCountry + "]";
        System.out.println("\n  Ejecutando: " + label + "...");
        LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request()
                .selectors(Arrays.asList(selectors)).build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(req, listener);
        TestExecutionSummary s = listener.getSummary();
        System.out.printf("  Pasaron: %d  Fallaron: %d  Omitidos: %d  (%.1f seg)%n",
                s.getTestsSucceededCount(), s.getTestsFailedCount(),
                s.getTestsSkippedCount(), (s.getTimeFinished() - s.getTimeStarted()) / 1000.0);
    }

    private static String readLine() {
        try { return SCANNER.nextLine().trim(); } catch (Exception e) { return ""; }
    }
}
