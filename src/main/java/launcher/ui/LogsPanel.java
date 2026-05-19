package launcher.ui;

import launcher.components.GlowButton;
import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Enhanced terminal-style log panel.
 * Features: filter tabs (ALL / INFO / WARN / ERROR / SUCCESS),
 * timestamps, ANSI-color output, abort + clear buttons, auto-scroll.
 *
 * Main.java keeps references to the underlying JTextPane via getLogPane().
 * The existing log() / logLine() helpers continue to work unchanged.
 */
public class LogsPanel extends JPanel {

    public enum Level { ALL, INFO, WARN, ERROR, SUCCESS, TEST }

    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss");

    // ── References exposed to Main.java ───────────────────────────
    private final JTextPane logPane;
    private Level           activeFilter = Level.ALL;

    // Tab buttons
    private final JPanel[] tabs   = new JPanel[6];
    private final Level[]  levels = {Level.ALL, Level.INFO, Level.WARN, Level.ERROR, Level.SUCCESS, Level.TEST};

    public LogsPanel(Runnable onAbort, Runnable onClear) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        // ── Header row ────────────────────────────────────────────
        JPanel header = buildHeader(onAbort, onClear);
        add(header, BorderLayout.NORTH);

        // ── Terminal pane ─────────────────────────────────────────
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(3, 5, 15));
        logPane.setForeground(Colors.TEXT_PRI);
        logPane.setBorder(new EmptyBorder(10, 14, 10, 14));
        logPane.setFont(Fonts.MONO);

        // Line-number gutter effect via custom caret
        logPane.putClientProperty("caretWidth", 0);

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(20, 32, 65), 1));
        scroll.setBackground(new Color(3, 5, 15));
        scroll.getViewport().setBackground(new Color(3, 5, 15));
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        add(scroll, BorderLayout.CENTER);

    }

    // ── Header: title + filter tabs + action buttons ──────────────

    private JPanel buildHeader(Runnable onAbort, Runnable onClear) {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setOpaque(false);
        outer.setBorder(new EmptyBorder(6, 14, 4, 14));

        // Left: icon + title + filter tabs
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        JLabel ico = new JLabel(">_");
        ico.setFont(new Font(Fonts.MONO_FAMILY, Font.BOLD, 13));
        ico.setForeground(Colors.GREEN);
        ico.setBorder(new EmptyBorder(0, 0, 0, 10));
        left.add(ico);

        JLabel title = new JLabel("LOG DE EJECUCIÓN");
        title.setFont(Fonts.UI_SMALL_BOLD);
        title.setForeground(Colors.TEXT_DIM);
        title.setBorder(new EmptyBorder(0, 0, 0, 16));
        left.add(title);

        // Filter tab bar
        String[] tabLabels = {"ALL", "INFO", "WARN", "ERROR", "SUCCESS", "TEST"};
        Color[]  tabColors = {Colors.TEXT_DIM, Colors.INFO, Colors.WARN, Colors.FAIL, Colors.OK, Colors.PURPLE_L};

        for (int i = 0; i < levels.length; i++) {
            final Level lv  = levels[i];
            final Color clr = tabColors[i];
            JPanel tab = buildFilterTab(tabLabels[i], clr, lv == Level.ALL);
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { setFilter(lv); }
            });
            tabs[i] = tab;
            left.add(tab);
        }

        // Right: abort + clear buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        GlowButton abortB = new GlowButton("■  Abortar", Colors.FAIL, GlowButton.Style.DANGER, 6);
        abortB.setFont(Fonts.BTN_SMALL);
        abortB.setForeground(Color.WHITE);
        abortB.setPreferredSize(new Dimension(110, 26));
        abortB.setVisible(false);
        abortB.addActionListener(e -> { if (onAbort != null) onAbort.run(); });
        putClientProperty("__abortBtn__", abortB);

        GlowButton clearB = new GlowButton("🗑  Limpiar", Colors.BG_CARD, GlowButton.Style.SECONDARY, 6);
        clearB.setFont(Fonts.BTN_SMALL);
        clearB.setForeground(Colors.TEXT_DIM);
        clearB.setPreferredSize(new Dimension(110, 26));
        clearB.addActionListener(e -> {
            logPane.setText("");
            if (onClear != null) onClear.run();
        });

        right.add(abortB);
        right.add(clearB);

        // Store abort button so setAbortVisible() can find it
        putClientProperty("__abort_ref__", abortB);

        outer.add(left,  BorderLayout.WEST);
        outer.add(right, BorderLayout.EAST);
        return outer;
    }

    private JPanel buildFilterTab(String label, Color accent, boolean active) {
        JPanel tab = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        tab.setOpaque(active);
        if (active) tab.setBackground(Colors.BG_MAIN);

        JLabel lbl = new JLabel(label);
        lbl.setFont(Fonts.UI_SMALL_BOLD);
        lbl.setForeground(active ? Colors.TEXT_PRI : Colors.TEXT_DIM);
        tab.add(lbl);

        tab.setBorder(active
            ? new MatteBorder(0, 0, 2, 0, accent)
            : new EmptyBorder(0, 0, 2, 0));
        tab.putClientProperty("lbl",    lbl);
        tab.putClientProperty("accent", accent);
        return tab;
    }

    private void setFilter(Level lv) {
        activeFilter = lv;
        for (int i = 0; i < levels.length; i++) {
            JPanel tab   = tabs[i];
            JLabel lbl   = (JLabel) tab.getClientProperty("lbl");
            Color  accent = (Color) tab.getClientProperty("accent");
            boolean sel  = levels[i] == lv;
            tab.setOpaque(sel);
            if (sel) tab.setBackground(Colors.BG_MAIN);
            lbl.setForeground(sel ? Colors.TEXT_PRI : Colors.TEXT_DIM);
            tab.setBorder(sel
                ? new MatteBorder(0, 0, 2, 0, accent)
                : new EmptyBorder(0, 0, 2, 0));
        }
        repaint();
    }

    // ── Log write API (called from Main.java) ─────────────────────

    /** Append a styled log entry — mirrors Main.logLine() signature. */
    public void appendLine(String icon, String message, Color color) {
        String ts = TS.format(new Date());
        Level lv = detectLevel(color, message);
        if (activeFilter != Level.ALL && activeFilter != lv) return;
        append("[" + ts + "] " + icon + "  " + message + "\n", color, false);
    }

    /** Raw append for separator lines etc. */
    public void append(String text, Color color, boolean bold) {
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

    private Level detectLevel(Color c, String msg) {
        if (c == Colors.FAIL || c.equals(Colors.FAIL)) return Level.ERROR;
        if (c == Colors.WARN || c.equals(Colors.WARN)) return Level.WARN;
        if (c == Colors.OK   || c.equals(Colors.OK))   return Level.SUCCESS;
        String l = msg.toLowerCase();
        if (l.contains("test") || l.contains("passed") || l.contains("failed")) return Level.TEST;
        return Level.INFO;
    }

    // ── Abort button visibility (called from Main.java) ───────────

    public void setAbortVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            Object ref = getClientProperty("__abort_ref__");
            if (ref instanceof JButton btn) {
                btn.setVisible(visible);
                if (btn.getParent() != null) btn.getParent().revalidate();
            }
        });
    }

    // ── Exposed accessors for Main.java ───────────────────────────

    /** The JTextPane; Main.java stores this in its static logPane field. */
    public JTextPane getLogPane() { return logPane; }
}
