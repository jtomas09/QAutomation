package launcher.ui;

import launcher.components.AnimatedProgressBar;
import launcher.components.GlowButton;
import launcher.components.GradientPanel;
import launcher.components.StatusBadge;
import launcher.theme.Colors;
import launcher.theme.Fonts;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Enterprise top navigation bar (70px tall).
 * Contains: Logo · Search bar · Status pills · Notifications · User · Run button.
 */
public class TopNavbar extends GradientPanel {

    // ── Public references for Main.java to update ─────────────────
    public final StatusBadge       backendPill;
    public final StatusBadge       runnerPill;
    public final GlowButton        runBtn;
    public final AnimatedProgressBar progressBar;

    private JLabel notifBadge;
    private int    notifCount = 0;

    public TopNavbar(String userName, String userRole,
                     Runnable onRun, Runnable onSettings) {
        super(GradientPanel.Preset.NAVBAR);
        setPreferredSize(new Dimension(0, 68));
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Colors.BORDER_L),
            new EmptyBorder(0, 22, 0, 22)));
        setLayout(new BorderLayout(0, 0));

        // ── LEFT: Logo + brand ────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        JPanel brand = buildBrand();
        left.add(brand);

        // Status pills (allocated for API compatibility, not shown in UI)
        backendPill = new StatusBadge("Backend: verificando...", StatusBadge.Status.CHECKING);
        runnerPill  = new StatusBadge("Runner: offline", StatusBadge.Status.OFFLINE);

        // ── CENTER: Search bar ────────────────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        JPanel searchWrap = buildSearchBar();
        center.add(searchWrap);

        // ── RIGHT: Progress + Actions + User ─────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        progressBar = new AnimatedProgressBar(3);
        progressBar.setPreferredSize(new Dimension(120, 14));
        progressBar.setVisible(false);
        right.add(progressBar);

        // Settings (⚙)
        GlowButton settingsBtn = GlowButton.ghost("⚙", Colors.TEXT_DIM);
        settingsBtn.setFont(Fonts.EMOJI_MD);
        settingsBtn.setForeground(Colors.TEXT_DIM);
        settingsBtn.setPreferredSize(new Dimension(36, 36));
        settingsBtn.setToolTipText("Configuración");
        settingsBtn.addActionListener(e -> { if (onSettings != null) onSettings.run(); });
        right.add(settingsBtn);

        // Notification bell with badge
        JPanel notifWrap = buildNotifButton();
        right.add(notifWrap);

        // Divider
        JLabel div2 = new JLabel("|");
        div2.setFont(new Font("Dialog", Font.PLAIN, 18));
        div2.setForeground(new Color(40, 58, 100));
        right.add(div2);

        // User avatar
        right.add(buildUserAvatar(userName, userRole));

        // Run button
        right.add(Box.createRigidArea(new Dimension(6, 0)));
        runBtn = new GlowButton("▶  Nueva Ejecución", Colors.ACCENT, GlowButton.Style.PRIMARY, 10);
        runBtn.setFont(Fonts.BTN_PRIMARY);
        runBtn.setForeground(Color.WHITE);
        runBtn.setPreferredSize(new Dimension(186, 38));
        runBtn.addActionListener(e -> { if (onRun != null) onRun.run(); });
        right.add(runBtn);

        add(left,   BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right,  BorderLayout.EAST);
    }

    // ── Brand section ─────────────────────────────────────────────

    private JPanel buildBrand() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);

        // Star icon fallback (logo loaded by Main.java)
        JLabel star = new JLabel("✦");
        star.setFont(new Font("Dialog", Font.BOLD, 20));
        star.setForeground(Colors.ACCENT);
        star.setOpaque(true);
        star.setBackground(new Color(59, 130, 246, 20));
        star.setBorder(new EmptyBorder(6, 8, 6, 8));

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        JLabel t1 = new JLabel("AUTOMATION QA");
        t1.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 13));
        t1.setForeground(Color.WHITE);

        JLabel t2 = new JLabel("Cinépolis · Platform");
        t2.setFont(new Font(Fonts.UI_FAMILY, Font.PLAIN, 10));
        t2.setForeground(Colors.TEXT_DIM);

        titleStack.add(t1);
        titleStack.add(t2);

        p.add(star);
        p.add(titleStack);
        return p;
    }

    // ── Search bar ────────────────────────────────────────────────

    private JPanel buildSearchBar() {
        JPanel wrap = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(10, 16, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Colors.BORDER_L);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wrap.setOpaque(false);
        wrap.setPreferredSize(new Dimension(340, 36));
        wrap.setBorder(new EmptyBorder(0, 12, 0, 12));

        JLabel ico = new JLabel("⌕");
        ico.setFont(new Font("Dialog", Font.PLAIN, 15));
        ico.setForeground(Colors.TEXT_DIM);

        JLabel hint = new JLabel("Buscar suites, tests, reportes...    ⌘K");
        hint.setFont(Fonts.UI_BODY);
        hint.setForeground(new Color(60, 80, 120));

        wrap.add(ico,  BorderLayout.WEST);
        wrap.add(hint, BorderLayout.CENTER);
        return wrap;
    }

    // ── Notification bell ─────────────────────────────────────────

    private JPanel buildNotifButton() {
        JPanel wrap = new JPanel(null);
        wrap.setOpaque(false);
        wrap.setPreferredSize(new Dimension(40, 40));

        JLabel bell = new JLabel("🔔");
        bell.setFont(Fonts.EMOJI_MD);
        bell.setForeground(Colors.TEXT_DIM);
        bell.setBounds(4, 8, 24, 24);
        bell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bell.setToolTipText("Notificaciones");

        notifBadge = new JLabel("0") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Colors.FAIL);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        notifBadge.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 8));
        notifBadge.setForeground(Color.WHITE);
        notifBadge.setHorizontalAlignment(SwingConstants.CENTER);
        notifBadge.setBounds(18, 4, 16, 16);
        notifBadge.setVisible(false);

        wrap.add(bell);
        wrap.add(notifBadge);
        return wrap;
    }

    public void addNotification(String message) {
        notifCount++;
        SwingUtilities.invokeLater(() -> {
            notifBadge.setText(notifCount > 9 ? "9+" : String.valueOf(notifCount));
            notifBadge.setVisible(true);
        });
    }

    // ── User avatar ───────────────────────────────────────────────

    private JPanel buildUserAvatar(String name, String role) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Gradient circle avatar
        JPanel avatar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = Math.min(getWidth(), getHeight());
                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(s / 2f, s / 2f), s / 2f,
                    new float[]{0f, 1f},
                    new Color[]{Colors.ACCENT, Colors.PURPLE}));
                g2.fillOval(0, 0, s, s);
                // Initials
                String init = name != null && !name.isBlank()
                    ? String.valueOf(name.charAt(0)).toUpperCase() : "U";
                g2.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(init, (s - fm.stringWidth(init)) / 2, (s - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(34, 34));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        JLabel nameLbl = new JLabel(name != null ? name : "Usuario");
        nameLbl.setFont(new Font(Fonts.UI_FAMILY, Font.BOLD, 12));
        nameLbl.setForeground(Colors.TEXT_PRI);
        JLabel roleLbl = new JLabel(role != null ? role : "QA Engineer");
        roleLbl.setFont(Fonts.UI_SMALL);
        roleLbl.setForeground(Colors.TEXT_DIM);
        textCol.add(nameLbl);
        textCol.add(roleLbl);

        p.add(avatar);
        p.add(textCol);
        return p;
    }

    // ── Status helpers (called from Main.java) ────────────────────

    /** Set the main run button to "running" (red stop) or "ready" (blue run) state. */
    public void setRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                runBtn.setText("■  Detener");
                runBtn.setAccentColor(Colors.FAIL);
                progressBar.start();
            } else {
                runBtn.setText("▶  Nueva Ejecución");
                runBtn.setAccentColor(Colors.ACCENT);
                progressBar.stop();
            }
            runBtn.repaint();
        });
    }
}
