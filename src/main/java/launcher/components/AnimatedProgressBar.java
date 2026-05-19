package launcher.components;

import launcher.theme.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Indeterminate progress bar with a scanning glow pulse.
 * Call start() when a test begins and stop() when it ends.
 */
public class AnimatedProgressBar extends JPanel {

    private Timer   timer;
    private float   offset   = 0f;
    private boolean active   = false;
    private Color   barColor = Colors.ACCENT;
    private final int trackH;

    public AnimatedProgressBar() {
        this(4);
    }

    public AnimatedProgressBar(int trackHeight) {
        this.trackH = trackHeight;
        setOpaque(false);
        setPreferredSize(new Dimension(200, trackHeight + 6));

        timer = new Timer(20, e -> {
            offset += 0.018f;
            if (offset > 1f) offset = 0f;
            repaint();
        });
    }

    public void start() {
        active = true;
        offset = 0f;
        timer.start();
        setVisible(true);
        repaint();
    }

    public void stop() {
        active = false;
        timer.stop();
        setVisible(false);
        repaint();
    }

    public boolean isActive() { return active; }

    public void setBarColor(Color c) { this.barColor = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!active) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int y = (h - trackH) / 2;

        // Track background
        g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 20));
        g2.fill(new RoundRectangle2D.Float(0, y, w, trackH, trackH, trackH));

        // Scanning glow block
        float glowW  = w * 0.35f;
        float glowX  = offset * (w + glowW) - glowW;
        int x1 = (int) glowX;
        int x2 = (int)(glowX + glowW);
        x1 = Math.max(0, x1);
        x2 = Math.min(w, x2);

        if (x2 > x1) {
            g2.setPaint(new LinearGradientPaint(
                x1, y, x2, y,
                new float[]{0f, 0.35f, 0.65f, 1f},
                new Color[]{
                    new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 0),
                    new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 180),
                    new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 180),
                    new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 0)
                }
            ));
            g2.fill(new RoundRectangle2D.Float(x1, y, x2 - x1, trackH, trackH, trackH));
        }

        // Solid leading edge spark
        if (x2 > 2) {
            g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 220));
            g2.fill(new RoundRectangle2D.Float(x2 - 3, y, 3, trackH, trackH, trackH));
        }

        g2.dispose();
    }
}
