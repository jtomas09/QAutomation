package launcher.ui;

import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * Row of 5 KPI metric cards: Total · Passed · Failed · Skipped · Avg Time.
 * Each card has: accent circle icon, big number, subtitle label, SVG-style mini sparkline.
 * updateStats() is called from Main.java after each test run.
 */
public class KpiCardsPanel extends JPanel {

    private record KpiDef(String icon, String label, String sub, Color accent, int index) {}

    private static final KpiDef[] DEFS = {
        new KpiDef("◎",  "0", "Ejecuciones Totales",  Colors.ACCENT,  0),
        new KpiDef("✓",  "0", "Pruebas Exitosas",      Colors.OK,      1),
        new KpiDef("✗",  "0", "Pruebas Fallidas",      Colors.FAIL,    2),
        new KpiDef("▶▶", "0", "Pruebas Omitidas",      Colors.WARN,    3),
        new KpiDef("⏱",  "--","Tiempo Promedio",        Colors.TEAL,    4),
    };

    private final JLabel[] numLabels   = new JLabel[5];
    private final int[][]  sparkData   = new int[5][12];
    private final JPanel[] sparkPanels = new JPanel[5];

    public KpiCardsPanel() {
        setOpaque(false);
        setLayout(new GridLayout(1, 5, 14, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        for (KpiDef def : DEFS) {
            JPanel card = buildCard(def);
            add(card);
        }
    }

    // ── Card builder ──────────────────────────────────────────────

    private JPanel buildCard(KpiDef def) {
        boolean[] hov = {false};

        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int r = def.accent().getRed(), gr = def.accent().getGreen(), b = def.accent().getBlue();

                // Base card fill
                g2.setPaint(new GradientPaint(0, 0, new Color(14, 22, 48), 0, h, Colors.BG_CARD));
                g2.fillRoundRect(0, 0, w, h, 16, 16);

                // Accent radial glow top-left
                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(w * 0.15f, h * 0.15f), w * 0.65f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(r, gr, b, hov[0] ? 50 : 30), new Color(r, gr, b, 0)}));
                g2.fillRoundRect(0, 0, w, h, 16, 16);

                // Bottom wave accent
                Path2D.Float wave = new Path2D.Float();
                wave.moveTo(0, h * 0.72f);
                wave.curveTo(w * 0.25f, h * 0.60f, w * 0.65f, h * 0.78f, w, h * 0.64f);
                wave.lineTo(w, h); wave.lineTo(0, h); wave.closePath();
                g2.setColor(new Color(r, gr, b, hov[0] ? 50 : 35));
                g2.fill(wave);

                // Border
                g2.setColor(hov[0]
                    ? new Color(r, gr, b, 120)
                    : new Color(25, 38, 75));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 18, 12, 18));

        // ── Top row: icon circle + trend badge ────────────────────
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JPanel iconCircle = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = Math.min(getWidth(), getHeight());
                g2.setColor(new Color(def.accent().getRed(), def.accent().getGreen(), def.accent().getBlue(), 30));
                g2.fillOval(0, 0, s, s);
                g2.setColor(def.accent());
                g2.fillOval(4, 4, s - 8, s - 8);
                g2.setFont(def.icon().length() > 2 ? Fonts.UI_SMALL_BOLD : new Font(Fonts.UI_FAMILY, Font.BOLD, 13));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String t = def.icon();
                g2.drawString(t, (s - fm.stringWidth(t)) / 2, (s - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        iconCircle.setOpaque(false);
        iconCircle.setPreferredSize(new Dimension(38, 38));

        topRow.add(iconCircle, BorderLayout.WEST);

        // ── Center: big number + subtitle ─────────────────────────
        JPanel centerCol = new JPanel();
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setOpaque(false);
        centerCol.setBorder(new EmptyBorder(10, 0, 4, 0));

        JLabel numLbl = new JLabel(def.label());
        numLbl.setFont(Fonts.KPI_NUMBER);
        numLbl.setForeground(def.accent());
        numLabels[def.index()] = numLbl;

        JLabel subLbl = new JLabel(def.sub());
        subLbl.setFont(Fonts.KPI_LABEL);
        subLbl.setForeground(Colors.TEXT_DIM);

        centerCol.add(numLbl);
        centerCol.add(Box.createRigidArea(new Dimension(0, 2)));
        centerCol.add(subLbl);

        // ── Sparkline ─────────────────────────────────────────────
        JPanel spark = buildSparkline(def.index(), def.accent());
        sparkPanels[def.index()] = spark;

        card.add(topRow,    BorderLayout.NORTH);
        card.add(centerCol, BorderLayout.CENTER);
        card.add(spark,     BorderLayout.SOUTH);

        // Hover
        MouseAdapter ha = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hov[0] = true;  card.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hov[0] = false; card.repaint(); }
        };
        card.addMouseListener(ha);
        centerCol.addMouseListener(ha);
        topRow.addMouseListener(ha);
        iconCircle.addMouseListener(ha);

        return card;
    }

    // ── Mini sparkline (SVG-style path) ───────────────────────────

    private JPanel buildSparkline(int idx, Color accent) {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int[] data = sparkData[idx];
                int w = getWidth(), h = getHeight();
                if (w < 10 || h < 6) return;
                int max = 1;
                for (int v : data) if (v > max) max = v;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int n = data.length;
                float dx = (float) w / (n - 1);

                // Build path
                Path2D.Float line = new Path2D.Float();
                Path2D.Float area = new Path2D.Float();
                for (int i = 0; i < n; i++) {
                    float x = i * dx;
                    float y = h - (data[i] / (float) max) * (h - 2) - 1;
                    if (i == 0) { line.moveTo(x, y); area.moveTo(x, h); area.lineTo(x, y); }
                    else        { line.lineTo(x, y); area.lineTo(x, y); }
                }
                area.lineTo((n - 1) * dx, h); area.closePath();

                // Area fill
                g2.setPaint(new GradientPaint(0, 0,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40),
                    0, h,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)));
                g2.fill(area);

                // Line
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(accent);
                g2.draw(line);

                // Last point dot
                float lx = (n - 1) * dx;
                float ly = h - (data[n - 1] / (float) max) * (h - 2) - 1;
                g2.setColor(accent);
                g2.fillOval((int) lx - 3, (int) ly - 3, 6, 6);
                g2.setColor(Color.WHITE);
                g2.fillOval((int) lx - 1, (int) ly - 1, 3, 3);

                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(0, 36));
        return panel;
    }

    // ── Public update API ─────────────────────────────────────────

    /**
     * Called from Main.updateStats() after each run.
     * @param total   cumulative total
     * @param passed  cumulative passed
     * @param failed  cumulative failed
     * @param skipped cumulative skipped
     * @param time    formatted time string, e.g. "12.3s"
     */
    public void updateStats(int total, int passed, int failed, int skipped, String time) {
        SwingUtilities.invokeLater(() -> {
            numLabels[0].setText(String.valueOf(total));
            numLabels[1].setText(String.valueOf(passed));
            numLabels[2].setText(String.valueOf(failed));
            numLabels[3].setText(String.valueOf(skipped));
            numLabels[4].setText(time);

            pushSparkValue(0, total);
            pushSparkValue(1, passed);
            pushSparkValue(2, failed);
            pushSparkValue(3, skipped);

            for (JPanel sp : sparkPanels) if (sp != null) sp.repaint();
        });
    }

    public void resetStats() {
        SwingUtilities.invokeLater(() -> {
            numLabels[0].setText("0");
            numLabels[1].setText("0");
            numLabels[2].setText("0");
            numLabels[3].setText("0");
            numLabels[4].setText("--");
            for (int i = 0; i < sparkData.length; i++)
                sparkData[i] = new int[12];
            for (JPanel sp : sparkPanels) if (sp != null) sp.repaint();
        });
    }

    private void pushSparkValue(int idx, int value) {
        int[] d = sparkData[idx];
        System.arraycopy(d, 1, d, 0, d.length - 1);
        d[d.length - 1] = value;
    }
}
