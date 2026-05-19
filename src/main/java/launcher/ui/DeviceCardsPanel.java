package launcher.ui;

import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Connected-devices panel.
 * Shows device cards with status indicators (available / in-use / offline).
 * Device data can be refreshed at runtime via setDevices().
 */
public class DeviceCardsPanel extends JPanel {

    public enum DeviceStatus { AVAILABLE, IN_USE, OFFLINE }

    public record DeviceInfo(String name, String os, DeviceStatus status) {}

    private static final DeviceInfo[] DEFAULT_DEVICES = {
        new DeviceInfo("Galaxy A56 5G",  "Android 15",  DeviceStatus.AVAILABLE),
        new DeviceInfo("Pixel 9 Pro",    "Android 15",  DeviceStatus.IN_USE),
        new DeviceInfo("iPhone 16",      "iOS 18.4",    DeviceStatus.AVAILABLE),
        new DeviceInfo("Galaxy S25",     "Android 15",  DeviceStatus.AVAILABLE),
        new DeviceInfo("Redmi Note 13",  "Android 13",  DeviceStatus.IN_USE),
        new DeviceInfo("OnePlus 12",     "Android 14",  DeviceStatus.OFFLINE),
    };

    private JPanel grid;

    public DeviceCardsPanel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        // Card header
        JPanel header = buildHeader();
        add(header, BorderLayout.NORTH);

        // Horizontal scrollable device grid
        grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        grid.setOpaque(false);
        setDevices(DEFAULT_DEVICES);

        JScrollPane scroll = new JScrollPane(grid,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 4));
        scroll.setBorder(new EmptyBorder(16, 18, 12, 18));
        add(scroll, BorderLayout.CENTER);
    }

    // ── Header ────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new MatteBorder(0, 0, 1, 0, Colors.BORDER));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(14, 18, 12, 0));

        JLabel t1 = new JLabel("Dispositivos Conectados");
        t1.setFont(Fonts.UI_TITLE);
        t1.setForeground(Colors.TEXT_PRI);

        JLabel t2 = new JLabel("  ·  Dispositivos disponibles para pruebas");
        t2.setFont(Fonts.UI_BODY);
        t2.setForeground(Colors.TEXT_DIM);

        left.add(t1);
        left.add(t2);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 0, 0, 18));

        JButton manageBtn = new JButton("Gestionar Dispositivos");
        manageBtn.setFont(Fonts.UI_SMALL_BOLD);
        manageBtn.setForeground(new Color(165, 180, 252));
        manageBtn.setBackground(new Color(99, 102, 241, 38));
        manageBtn.setOpaque(true);
        manageBtn.setContentAreaFilled(true);
        manageBtn.setBorder(new EmptyBorder(6, 12, 6, 12));
        manageBtn.setFocusPainted(false);
        manageBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        right.add(manageBtn);

        p.add(left,  BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ── Device card ───────────────────────────────────────────────

    private JPanel buildDeviceCard(DeviceInfo dev) {
        boolean[] hov = {false};

        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                // Base fill
                g2.setColor(new Color(10, 15, 35));
                g2.fillRoundRect(0, 0, w, h, 14, 14);

                // Hover border glow
                Color borderColor = hov[0] ? Colors.ACCENT : Colors.BORDER_L;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

                // Status stripe at top
                Color stripeColor = statusColor(dev.status());
                g2.setColor(new Color(stripeColor.getRed(), stripeColor.getGreen(), stripeColor.getBlue(), 60));
                g2.fillRoundRect(0, 0, w, 4, 4, 4);
                g2.setColor(stripeColor);
                g2.fillRect(0, 0, w, 2);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(130, 140));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top row: emoji icon + "…" menu
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel phoneIco = new JLabel("📱");
        phoneIco.setFont(new Font("Dialog", Font.PLAIN, 28));

        JLabel more = new JLabel("⋮");
        more.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 16));
        more.setForeground(Colors.TEXT_DIM);
        more.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        topRow.add(phoneIco, BorderLayout.WEST);
        topRow.add(more,     BorderLayout.EAST);

        // Name + OS
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel nameL = new JLabel(dev.name());
        nameL.setFont(Fonts.UI_LABEL_BOLD);
        nameL.setForeground(Colors.TEXT_PRI);

        JLabel osL = new JLabel(dev.os());
        osL.setFont(Fonts.UI_SMALL);
        osL.setForeground(Colors.TEXT_DIM);
        osL.setBorder(new EmptyBorder(1, 0, 6, 0));

        info.add(nameL);
        info.add(osL);

        // Status pill
        JPanel pill = buildStatusPill(dev.status());

        card.add(topRow, BorderLayout.NORTH);
        card.add(info,   BorderLayout.CENTER);
        card.add(pill,   BorderLayout.SOUTH);

        MouseAdapter ha = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hov[0] = true;  card.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hov[0] = false; card.repaint(); }
        };
        card.addMouseListener(ha);
        topRow.addMouseListener(ha);
        info.addMouseListener(ha);

        return card;
    }

    private JPanel buildStatusPill(DeviceStatus status) {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = statusColor(status);
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 22));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Dialog", Font.PLAIN, 8));
        dot.setForeground(statusColor(status));

        JLabel lbl = new JLabel(statusLabel(status));
        lbl.setFont(Fonts.UI_SMALL_BOLD);
        lbl.setForeground(statusColor(status));

        pill.add(dot);
        pill.add(lbl);
        return pill;
    }

    private static Color statusColor(DeviceStatus s) {
        return switch (s) {
            case AVAILABLE -> Colors.OK;
            case IN_USE    -> Colors.WARN;
            case OFFLINE   -> Colors.FAIL;
        };
    }

    private static String statusLabel(DeviceStatus s) {
        return switch (s) {
            case AVAILABLE -> "Disponible";
            case IN_USE    -> "En uso";
            case OFFLINE   -> "Offline";
        };
    }

    // ── Public API ────────────────────────────────────────────────

    public void setDevices(DeviceInfo[] devices) {
        SwingUtilities.invokeLater(() -> {
            grid.removeAll();
            for (DeviceInfo d : devices) grid.add(buildDeviceCard(d));
            grid.revalidate();
            grid.repaint();
        });
    }
}
