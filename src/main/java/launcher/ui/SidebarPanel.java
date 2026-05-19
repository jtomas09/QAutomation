package launcher.ui;

import launcher.components.GradientPanel;
import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise left sidebar (230 px).
 * Sections: MAIN | PAÍSES | ANALYTICS | RECURSOS + Enterprise card at bottom.
 * Calls back onPageChange(pageKey) and onCountrySelect(countryName).
 */
public class SidebarPanel extends GradientPanel {

    public interface NavListener {
        void onPage(String pageKey);
        void onCountry(String countryName);
    }

    // nav item definition
    private record NavItem(String icon, String label, String key, Color accent) {}

    private static final List<NavItem> MAIN_NAV = List.of(
        new NavItem("◉",  "Dashboard",        "dashboard",  Colors.ACCENT),
        new NavItem("▶",  "Ejecutar Pruebas",  "execute",    Colors.GREEN),
        new NavItem("☰",  "Ejecuciones",       "executions", Colors.PURPLE_L),
        new NavItem("◈",  "Suites",            "suites",     Colors.TEAL),
        new NavItem("📱", "Dispositivos",      "devices",    Colors.ORANGE),
        new NavItem("🌐", "Ambientes",         "environments", Colors.GOLD)
    );

    private static final List<NavItem> ANALYTICS_NAV = List.of(
        new NavItem("📊", "Reportes",  "reports",  Colors.GREEN),
        new NavItem("📈", "Métricas",  "metrics",  Colors.ACCENT),
        new NavItem("🕐", "Historial", "history",  Colors.PURPLE_L)
    );

    private static final List<NavItem> RESOURCES_NAV = List.of(
        new NavItem("📖", "Documentación", "docs",    Colors.BLUE_TITLE),
        new NavItem("💬", "Soporte",       "support", Colors.TEAL)
    );

    private static final String[][] COUNTRIES = {
        {"🇲🇽", "MX", "México"},
        {"🇦🇷", "AR", "Argentina"},
        {"🇨🇱", "CL", "Chile"},
        {"🇨🇴", "CO", "Colombia"},
        {"🇵🇪", "PE", "Perú"},
        {"🇪🇸", "ES", "España"},
    };

    private final NavListener listener;
    private final Map<String, JPanel> navRows       = new LinkedHashMap<>();
    private final Map<String, JPanel> countryRows   = new LinkedHashMap<>();
    private String activePage    = "dashboard";
    private String activeCountry = "";

    public SidebarPanel(NavListener listener) {
        super(GradientPanel.Preset.SIDEBAR);
        this.listener = listener;
        setPreferredSize(new Dimension(230, 0));
        setBorder(new MatteBorder(0, 0, 0, 1, Colors.BORDER_L));
        setLayout(new BorderLayout());

        JPanel content = buildContent();
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(10);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));

        add(scroll,       BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
    }

    private JPanel buildContent() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);

        // Brand
        p.add(buildBrand());

        // MAIN section
        p.add(groupLabel("PRINCIPAL"));
        for (NavItem item : MAIN_NAV) p.add(buildNavRow(item, false));
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // PAÍSES section
        p.add(groupLabel("PAÍSES"));
        for (String[] c : COUNTRIES) {
            JPanel row = buildCountryRow(c[0], c[1], c[2]);
            countryRows.put(c[2], row);
            p.add(row);
        }
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // ANALYTICS section
        p.add(groupLabel("ANALYTICS"));
        for (NavItem item : ANALYTICS_NAV) p.add(buildNavRow(item, false));
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // RECURSOS section
        p.add(groupLabel("RECURSOS"));
        for (NavItem item : RESOURCES_NAV) p.add(buildNavRow(item, false));
        p.add(Box.createVerticalGlue());

        return p;
    }

    // ── Brand ─────────────────────────────────────────────────────

    private JPanel buildBrand() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 14));
        p.setOpaque(false);
        p.setBorder(new MatteBorder(0, 0, 1, 0, Colors.BORDER_L));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JLabel star = new JLabel("✦");
        star.setFont(new Font("Dialog", Font.BOLD, 20));
        star.setForeground(Colors.GOLD);
        star.setOpaque(true);
        star.setBackground(new Color(59, 7, 100));
        star.setBorder(new EmptyBorder(5, 7, 5, 7));

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        JLabel t1 = new JLabel("AUTOMATION QA");
        t1.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 12));
        t1.setForeground(Color.WHITE);
        JLabel t2 = new JLabel("Test Launcher v1.0");
        t2.setFont(Fonts.UI_SMALL);
        t2.setForeground(Colors.TEXT_DIM);
        stack.add(t1);
        stack.add(t2);

        p.add(star);
        p.add(stack);
        return p;
    }

    // ── Group label ───────────────────────────────────────────────

    private JPanel groupLabel(String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(12, 0, 4, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel lbl = new JLabel(text);
        lbl.setFont(Fonts.NAV_GROUP);
        lbl.setForeground(new Color(60, 80, 120));
        p.add(lbl);
        return p;
    }

    // ── Nav row ───────────────────────────────────────────────────

    private JPanel buildNavRow(NavItem item, boolean isCountry) {
        JPanel row = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                boolean active = item.key().equals(activePage);
                if (active) {
                    g2.setColor(new Color(item.accent().getRed(), item.accent().getGreen(), item.accent().getBlue(), 18));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(item.accent());
                    g2.fillRect(0, 0, 3, getHeight());
                } else {
                    g2.setColor(Colors.TRANSPARENT);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.setBorder(new EmptyBorder(0, 14, 0, 14));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 9));
        inner.setOpaque(false);

        JLabel icoLbl = new JLabel(item.icon());
        icoLbl.setFont(item.icon().length() > 2 ? Fonts.EMOJI_SM : Fonts.SYMBOL);
        boolean active = item.key().equals(activePage);
        icoLbl.setForeground(active ? item.accent() : Colors.TEXT_DIM);

        JLabel lblLbl = new JLabel(item.label());
        lblLbl.setFont(active ? Fonts.NAV_ITEM_BOLD : Fonts.NAV_ITEM);
        lblLbl.setForeground(active ? Colors.TEXT_PRI : Colors.TEXT_DIM);

        inner.add(icoLbl);
        inner.add(lblLbl);
        row.add(inner, BorderLayout.CENTER);

        // Hover
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!item.key().equals(activePage)) {
                    row.setBackground(Colors.BG_HOVER);
                    row.setOpaque(true);
                    icoLbl.setForeground(item.accent());
                    lblLbl.setForeground(Colors.TEXT_SEC);
                    row.repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                boolean act = item.key().equals(activePage);
                row.setOpaque(false);
                icoLbl.setForeground(act ? item.accent() : Colors.TEXT_DIM);
                lblLbl.setForeground(act ? Colors.TEXT_PRI : Colors.TEXT_DIM);
                lblLbl.setFont(act ? Fonts.NAV_ITEM_BOLD : Fonts.NAV_ITEM);
                row.repaint();
            }
            @Override public void mouseClicked(MouseEvent e) {
                setActivePage(item.key());
                if (listener != null) listener.onPage(item.key());
            }
        });

        navRows.put(item.key(), row);
        return row;
    }

    // ── Country row ───────────────────────────────────────────────

    private JPanel buildCountryRow(String flag, String code, String name) {
        JPanel row = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                boolean active = name.equals(activeCountry);
                if (active) {
                    g2.setColor(new Color(Colors.ACCENT.getRed(), Colors.ACCENT.getGreen(), Colors.ACCENT.getBlue(), 18));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(Colors.ACCENT);
                    g2.fillRect(0, 0, 3, getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.setBorder(new EmptyBorder(0, 14, 0, 14));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        inner.setOpaque(false);

        JLabel flagLbl = new JLabel(flag);
        flagLbl.setFont(Fonts.EMOJI_SM);

        JLabel codeLbl = new JLabel(code);
        codeLbl.setFont(Fonts.UI_SMALL_BOLD);
        codeLbl.setForeground(new Color(160, 175, 215));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(Fonts.NAV_ITEM);
        nameLbl.setForeground(name.equals(activeCountry) ? Colors.TEXT_PRI : Colors.TEXT_DIM);

        inner.add(flagLbl);
        inner.add(codeLbl);
        inner.add(nameLbl);
        row.add(inner, BorderLayout.CENTER);

        JLabel arrow = new JLabel("›");
        arrow.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 14));
        arrow.setForeground(Colors.TEXT_DIM);
        arrow.setBorder(new EmptyBorder(0, 0, 0, 4));
        row.add(arrow, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!name.equals(activeCountry)) {
                    row.setOpaque(true);
                    row.setBackground(Colors.BG_HOVER);
                    nameLbl.setForeground(Colors.TEXT_SEC);
                    row.repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                nameLbl.setForeground(name.equals(activeCountry) ? Colors.TEXT_PRI : Colors.TEXT_DIM);
                row.repaint();
            }
            @Override public void mouseClicked(MouseEvent e) {
                setActiveCountry(name);
                setActivePage("execute");
                if (listener != null) {
                    listener.onCountry(name);
                    listener.onPage("execute");
                }
            }
        });

        return row;
    }

    // ── Bottom: Enterprise plan card ──────────────────────────────

    private JPanel buildBottom() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(new MatteBorder(1, 0, 0, 0, Colors.BORDER_L));

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 20, 50));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setPaint(new GradientPaint(0, 0, new Color(59, 130, 246, 40),
                    getWidth(), getHeight(), new Color(147, 51, 234, 40)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(59, 130, 246, 50));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("✦  Plan Enterprise");
        title.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 11));
        title.setForeground(Colors.ACCENT_HOV);

        JLabel sub = new JLabel("<html><div style='color:#647290;font-size:9px;width:155px;margin-top:2px'>"
            + "Automatiza, valida y entrega mejores experiencias con IA integrada.</div></html>");

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        btnRow.setOpaque(false);
        JLabel badge = new JLabel("ACTIVO");
        badge.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 9));
        badge.setForeground(Colors.GREEN);
        badge.setOpaque(true);
        badge.setBackground(new Color(34, 197, 94, 22));
        badge.setBorder(new EmptyBorder(2, 6, 2, 6));
        btnRow.add(badge);

        card.add(title);
        card.add(sub);
        card.add(btnRow);

        JPanel padding = new JPanel(new BorderLayout());
        padding.setOpaque(false);
        padding.setBorder(new EmptyBorder(8, 8, 10, 8));
        padding.add(card);
        outer.add(padding, BorderLayout.CENTER);

        return outer;
    }

    // ── Public state setters ──────────────────────────────────────

    public void setActivePage(String key) {
        activePage = key;
        SwingUtilities.invokeLater(() -> {
            for (NavItem item : MAIN_NAV) {
                JPanel r = navRows.get(item.key());
                if (r != null) r.repaint();
            }
            for (NavItem item : ANALYTICS_NAV) {
                JPanel r = navRows.get(item.key());
                if (r != null) r.repaint();
            }
            for (NavItem item : RESOURCES_NAV) {
                JPanel r = navRows.get(item.key());
                if (r != null) r.repaint();
            }
        });
    }

    public void setActiveCountry(String name) {
        activeCountry = name;
        SwingUtilities.invokeLater(() ->
            countryRows.values().forEach(JPanel::repaint));
    }
}
