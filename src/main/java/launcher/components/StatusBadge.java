package launcher.components;

import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import java.awt.*;

/**
 * Animated status pill: Online · Offline · Running · Checking
 * Shows a pulsing dot + label with colored background pill.
 */
public class StatusBadge extends JPanel {

    public enum Status { ONLINE, OFFLINE, RUNNING, CHECKING, IDLE }

    private Status  current = Status.IDLE;
    private JLabel  dotLbl;
    private JLabel  textLbl;
    private Timer   pulseTimer;
    private float   pulseAlpha = 1f;
    private boolean pulseUp    = false;

    public StatusBadge(String initialText, Status initialStatus) {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

        dotLbl  = new JLabel("●");
        dotLbl.setFont(new Font("Dialog", Font.PLAIN, 9));
        textLbl = new JLabel(initialText);
        textLbl.setFont(Fonts.PILL);

        add(dotLbl);
        add(textLbl);

        pulseTimer = new Timer(60, e -> {
            if (current == Status.RUNNING || current == Status.CHECKING) {
                pulseAlpha += pulseUp ? 0.05f : -0.05f;
                if (pulseAlpha >= 1f) { pulseAlpha = 1f; pulseUp = false; }
                if (pulseAlpha <= 0.3f) { pulseAlpha = 0.3f; pulseUp = true; }
                applyColors();
            }
        });
        pulseTimer.start();

        setStatus(initialStatus, initialText);
    }

    public StatusBadge(String text) {
        this(text, Status.IDLE);
    }

    public void setStatus(Status status, String text) {
        this.current = status;
        this.pulseAlpha = 1f;
        SwingUtilities.invokeLater(() -> {
            textLbl.setText(text);
            applyColors();
            repaint();
        });
    }

    private void applyColors() {
        Color fg, bg;
        switch (current) {
            case ONLINE   -> { fg = Colors.STATUS_OK_FG;   bg = Colors.STATUS_OK_BG; }
            case OFFLINE  -> { fg = Colors.STATUS_FAIL_FG; bg = Colors.STATUS_FAIL_BG; }
            case RUNNING  -> { fg = Colors.STATUS_INFO_FG; bg = Colors.STATUS_INFO_BG; }
            case CHECKING -> { fg = Colors.STATUS_WARN_FG; bg = Colors.STATUS_WARN_BG; }
            default        -> { fg = Colors.STATUS_IDLE_FG; bg = Colors.STATUS_IDLE_BG; }
        }
        Color pulsedDot = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(),
                (int)(255 * pulseAlpha));
        dotLbl.setForeground(pulsedDot);
        textLbl.setForeground(fg);
        putClientProperty("__bg", bg);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Object bgProp = getClientProperty("__bg");
        Color bg = (bgProp instanceof Color) ? (Color) bgProp : Colors.STATUS_IDLE_BG;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Insets getInsets() {
        return new Insets(3, 8, 3, 10);
    }

    public void dispose() {
        if (pulseTimer != null) pulseTimer.stop();
    }
}
