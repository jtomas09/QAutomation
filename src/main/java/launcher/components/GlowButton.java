package launcher.components;

import launcher.theme.Colors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
/**
 * Premium gradient button with glow effect on hover.
 * Supports primary (colored) and secondary (dark bg) styles.
 */
public class GlowButton extends JButton {

    public enum Style { PRIMARY, SECONDARY, DANGER, SUCCESS, GHOST }

    private Color accentColor;
    private final Style style;
    private final int   arc;
    private boolean     hov = false;
    private boolean     pressed = false;

    public GlowButton(String text, Color accent, Style style, int arc) {
        super(text);
        this.accentColor = accent;
        this.style       = style;
        this.arc         = arc;
        init();
    }

    public GlowButton(String text, Color accent) {
        this(text, accent, Style.PRIMARY, 10);
    }

    public GlowButton(String text) {
        this(text, Colors.ACCENT, Style.PRIMARY, 10);
    }

    private void init() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e)  { hov = true;    repaint(); }
            @Override public void mouseExited(MouseEvent e)   { hov = false;   pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e)  { pressed = true;  repaint(); }
            @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
        });
    }

    public void setAccentColor(Color c) { this.accentColor = c; repaint(); }
    public Color getAccentColor()       { return accentColor; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();
        float scale = pressed ? 0.96f : 1f;
        int offX    = (int)((1 - scale) * w / 2);
        int offY    = (int)((1 - scale) * h / 2);
        int dw      = (int)(w * scale);
        int dh      = (int)(h * scale);

        if (style == Style.PRIMARY || style == Style.DANGER || style == Style.SUCCESS) {
            // Outer glow on hover
            if (hov && isEnabled()) {
                int cr = accentColor.getRed(), cg = accentColor.getGreen(), cb = accentColor.getBlue();
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(new Color(cr, cg, cb, 20 * i));
                    g2.fillRoundRect(offX - i * 2, offY - i * 2, dw + i * 4, dh + i * 4, arc * 2 + i * 2, arc * 2 + i * 2);
                }
            }
            // Main gradient fill
            Color top = brighten(accentColor, hov ? 55 : 30);
            Color bot = hov ? brighten(accentColor, 18) : accentColor;
            if (!isEnabled()) { top = Colors.BG_CARD; bot = Colors.BG_CARD; }
            g2.setPaint(new GradientPaint(offX, offY, top, offX, offY + dh, bot));
            g2.fillRoundRect(offX, offY, dw, dh, arc * 2, arc * 2);
            // Top inner shine
            g2.setColor(new Color(255, 255, 255, hov ? 50 : 25));
            g2.fillRoundRect(offX + 2, offY + 1, dw - 4, dh / 2, arc * 2, arc * 2);
            // Border
            g2.setColor(new Color(255, 255, 255, hov ? 40 : 16));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(offX, offY, dw - 1, dh - 1, arc * 2, arc * 2);

        } else if (style == Style.SECONDARY) {
            Color fill = hov ? Colors.BG_HOVER : Colors.BG_CARD;
            g2.setColor(fill);
            g2.fillRoundRect(offX, offY, dw, dh, arc * 2, arc * 2);
            g2.setColor(hov ? Colors.BORDER_HL : Colors.BORDER_L);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(offX, offY, dw - 1, dh - 1, arc * 2, arc * 2);

        } else if (style == Style.GHOST) {
            if (hov) {
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 18));
                g2.fillRoundRect(offX, offY, dw, dh, arc * 2, arc * 2);
            }
            g2.setColor(hov ? accentColor : Colors.TEXT_DIM);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(offX, offY, dw - 1, dh - 1, arc * 2, arc * 2);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    private static Color brighten(Color c, int amt) {
        return new Color(
            Math.min(255, c.getRed()   + amt),
            Math.min(255, c.getGreen() + amt),
            Math.min(255, c.getBlue()  + amt));
    }

    /** Quick factory for the main "▶ EJECUTAR" primary button */
    public static GlowButton primary(String text) {
        GlowButton b = new GlowButton(text, Colors.ACCENT, Style.PRIMARY, 10);
        b.setFont(launcher.theme.Fonts.BTN_PRIMARY);
        b.setForeground(Color.WHITE);
        return b;
    }

    /** Quick factory for a secondary/ghost button */
    public static GlowButton secondary(String text) {
        GlowButton b = new GlowButton(text, Colors.ACCENT, Style.SECONDARY, 8);
        b.setFont(launcher.theme.Fonts.BTN_SMALL);
        b.setForeground(Colors.TEXT_DIM);
        return b;
    }

    /** Quick factory for a ghost/icon button */
    public static GlowButton ghost(String text, Color accent) {
        GlowButton b = new GlowButton(text, accent, Style.GHOST, 8);
        b.setFont(launcher.theme.Fonts.BTN_SMALL);
        b.setForeground(Colors.TEXT_DIM);
        return b;
    }
}
