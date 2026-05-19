package launcher.components;

import launcher.theme.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Reusable panel with configurable gradient/glow background.
 * Replaces ad-hoc paintComponent overrides scattered through Main.java.
 */
public class GradientPanel extends JPanel {

    public enum Preset {
        MAIN,       // Root background (deep navy + atmospheric glows)
        NAVBAR,     // Top navigation bar
        SIDEBAR,    // Left sidebar
        CARD,       // Card surface
        SURFACE,    // Generic dark surface
        TERMINAL    // Log terminal
    }

    private final Preset preset;
    private Color customBg;

    public GradientPanel(Preset preset) {
        this.preset = preset;
        setOpaque(true);
    }

    public GradientPanel(Color solid) {
        this.preset = null;
        this.customBg = solid;
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        int w = getWidth(), h = getHeight();

        if (preset == null) {
            g2.setColor(customBg != null ? customBg : Colors.BG_MAIN);
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            super.paintComponent(g);
            return;
        }

        switch (preset) {
            case MAIN -> paintMain(g2, w, h);
            case NAVBAR -> paintNavbar(g2, w, h);
            case SIDEBAR -> paintSidebar(g2, w, h);
            case CARD -> paintCard(g2, w, h);
            case TERMINAL -> paintTerminal(g2, w, h);
            default -> {
                g2.setColor(Colors.BG_PANEL);
                g2.fillRect(0, 0, w, h);
            }
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private void paintMain(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, new Color(6, 10, 30), 0, h, Colors.BG_MAIN));
        g2.fillRect(0, 0, w, h);
        // Blue atmospheric halo top-center
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, 0f), w * 0.65f,
            new float[]{0f, 0.55f, 1f},
            new Color[]{new Color(59, 130, 246, 28), new Color(59, 130, 246, 9), new Color(59, 130, 246, 0)}));
        g2.fillRect(0, 0, w, h / 2);
        // Top-left sidebar accent
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.13f, h * 0.06f), w * 0.38f,
            new float[]{0f, 1f},
            new Color[]{new Color(59, 130, 246, 35), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w / 2, h / 3);
        // Bottom-right purple ambient
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.88f, h * 0.94f), w * 0.42f,
            new float[]{0f, 1f},
            new Color[]{new Color(147, 51, 234, 22), new Color(0, 0, 0, 0)}));
        g2.fillRect(w / 2, h * 2 / 3, w, h);
        // Vignette
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.5f, h * 0.5f),
            (float) Math.max(w, h) * 0.75f,
            new float[]{0.35f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 65)}));
        g2.fillRect(0, 0, w, h);
    }

    private void paintNavbar(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(7, 12, 32));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 9), 0, h, new Color(255, 255, 255, 0)));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.72f, h * 0.5f), w * 0.28f,
            new float[]{0f, 1f},
            new Color[]{new Color(59, 130, 246, 22), new Color(0, 0, 0, 0)}));
        g2.fillRect(w / 2, 0, w / 2, h);
        // Bottom glow line
        g2.setPaint(new GradientPaint(0, h - 3, new Color(59, 130, 246, 50), 0, h, new Color(59, 130, 246, 0)));
        g2.fillRect(0, h - 3, w, 3);
    }

    private void paintSidebar(Graphics2D g2, int w, int h) {
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
    }

    private void paintCard(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, new Color(14, 22, 48), 0, h, Colors.BG_CARD));
        g2.fillRect(0, 0, w, h);
    }

    private void paintTerminal(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(3, 5, 15));
        g2.fillRect(0, 0, w, h);
    }
}
