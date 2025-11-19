package com.ArfGg57.modupdater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Insane" SUPER modern updater GUI
 * UPDATED: Now compatible with UpdaterCore's API.
 */
public class GuiUpdater {

    // --- UI Theme Constants ---
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_LIST_BG = new Color(44, 47, 60);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(200, 200, 200);
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    private static final Color COLOR_ACCENT_DARK = new Color(0, 160, 80);
    private static final Color COLOR_SCROLLBAR_THUMB = new Color(80, 85, 100);

    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font FONT_LOG = new Font("Consolas", Font.PLAIN, 14);
    private static final Font FONT_PROGRESS = new Font("Segoe UI", Font.BOLD, 14);

    // --- Swing Components ---
    private final JFrame frame;
    private final JLabel titleLabel;
    private final JLabel currentTaskLabel;
    private final JList<String> taskList;
    private final DefaultListModel<String> taskListModel;
    private final JProgressBar progressBar;

    private Point initialClick;

    private Timer fadeOutTimer;
    private Timer fadeInTimer;
    private Timer progressAnimator;

    private int targetProgress = 0;

    // Added to match the old GuiUpdater API
    private volatile boolean cancelled = false;

    public GuiUpdater() {
        // Anti-aliasing
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Main panel with rounded corners
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        WINDOW_CORNER_RADIUS, WINDOW_CORNER_RADIUS));
                g2.dispose();
            }
        };
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title label (draggable)
        titleLabel = new JLabel("Updating Modpack...", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);

        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleLabel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }
        });
        titleLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int thisX = frame.getLocation().x;
                int thisY = frame.getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                frame.setLocation(thisX + xMoved, thisY + yMoved);
            }
        });
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Center panel
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);

        currentTaskLabel = new JLabel("Starting...", SwingConstants.LEFT);
        currentTaskLabel.setFont(FONT_BODY);
        currentTaskLabel.setForeground(COLOR_TEXT_SECONDARY);
        centerPanel.add(currentTaskLabel, BorderLayout.NORTH);

        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);

        // Instantiate the static renderer and pass the JList reference
        taskList.setCellRenderer(new AnimatedListRenderer(taskList));

        taskList.setBackground(COLOR_LIST_BG);
        taskList.setForeground(COLOR_TEXT_SECONDARY);
        taskList.setSelectionBackground(new Color(80, 120, 200, 50));
        taskList.setFocusable(false);
        JScrollPane scrollPane = new JScrollPane(taskList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(FONT_PROGRESS);
        progressBar.setForeground(COLOR_ACCENT);
        progressBar.setBackground(COLOR_LIST_BG);
        progressBar.setPreferredSize(new Dimension(0, 30));
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        progressBar.setUI(new InsaneProgressBarUI());

        mainPanel.add(progressBar, BorderLayout.SOUTH);

        // --- Timers for Animations ---
        fadeOutTimer = new Timer(10, e -> {
            float opacity = frame.getOpacity();
            opacity -= 0.25f;
            if (opacity <= 0) {
                fadeOutTimer.stop();
                frame.dispose();
            } else {
                frame.setOpacity(opacity);
            }
        });

        fadeInTimer = new Timer(10, e -> {
            float opacity = frame.getOpacity();
            opacity += 0.25f;
            if (opacity >= 1) {
                fadeInTimer.stop();
                frame.setOpacity(1.0f);
            } else {
                frame.setOpacity(opacity);
            }
        });

        progressAnimator = new Timer(20, e -> updateProgressAnimation());

        // Frame
        frame.setContentPane(mainPanel);
        frame.setSize(600, 500);
        frame.setLocationRelativeTo(null);
        frame.setOpacity(0.0f); // Start invisible
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        fadeInTimer.start(); // Start fade-in
    }

    // --- Animation logic for progress bar value ---
    private void updateProgressAnimation() {
        int current = progressBar.getValue();
        if (current < targetProgress) {
            progressBar.setValue(current + 1);
        } else if (current > targetProgress) {
            progressBar.setValue(current - 1);
        } else {
            progressAnimator.stop();
        }
    }

    // --- Public methods (to match UpdaterCore) ---

    public void showCurrentTask(String task) {
        SwingUtilities.invokeLater(() -> currentTaskLabel.setText("Current Task: " + task));
        addLog(task);
    }

    public void addLog(String log) {
        SwingUtilities.invokeLater(() -> {
            taskListModel.addElement(" > " + log);
            taskList.ensureIndexIsVisible(taskListModel.getSize() - 1);
        });
        System.out.println(log);
    }

    /**
     * This is the primary method called by UpdaterCore.
     */
    public void show(String message) {
        // Route the generic "show" call to our "addLog"
        addLog(message);
    }

    public void setProgress(int percent) {
        targetProgress = Math.max(0, Math.min(100, percent));
        if (!progressAnimator.isRunning()) {
            progressAnimator.start();
        }
    }

    public void close() {
        if (!fadeOutTimer.isRunning()) {
            progressAnimator.stop();
            ((InsaneProgressBarUI) progressBar.getUI()).stopAnimation();
            fadeOutTimer.start();
        }
    }

    // --- ADDED: Methods required by UpdaterCore ---

    /**
     * ADDED: Required by UpdaterCore's `runUpdateSelected`.
     * This GUI doesn't have a per-file name label, so we'll just log it.
     */
    public void setFileName(String name) {
        addLog("File: " + name);
    }

    /**
     * ADDED: Required by UpdaterCore's `runUpdateSelected`.
     * This GUI only shows overall progress, so this is a no-op.
     * The download progress is logged by FileUtils anyway.
     */
    public void setFileProgress(int percent) {
        // No-op. FileUtils.downloadWithVerification handles logging.
    }

    /**
     * ADDED: Required to match the old GuiUpdater API.
     * This GUI design doesn't have a cancel button, so it will always be false.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    // --- Custom Animated ListCellRenderer ---
    private static class AnimatedListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel label;
        private final Timer fadeTimer;

        private final ConcurrentHashMap<String, Float> itemOpacities = new ConcurrentHashMap<>();

        private final JList<String> listReference;

        public AnimatedListRenderer(JList<String> list) {
            super(new BorderLayout());

            this.listReference = list;

            label = new JLabel();
            label.setFont(FONT_LOG);
            label.setForeground(COLOR_TEXT_SECONDARY);
            setBorder(new EmptyBorder(2, 5, 2, 5));
            setOpaque(true);
            add(label, BorderLayout.CENTER);

            fadeTimer = new Timer(10, e -> {
                if (listReference != null) {
                    listReference.repaint();
                }

                boolean needsRepaint = itemOpacities.values().stream().anyMatch(o -> o < 1.0f);

                if (!needsRepaint) {
                    ((Timer)e.getSource()).stop();
                }
            });
            fadeTimer.setRepeats(true);
            fadeTimer.start();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            label.setText(value);
            setBackground(COLOR_LIST_BG);

            Float currentOpacity = itemOpacities.getOrDefault(value, 0.0f);

            if (currentOpacity < 1.0f) {
                currentOpacity = Math.min(1.0f, currentOpacity + 0.05f);
                itemOpacities.put(value, currentOpacity);
                if (!fadeTimer.isRunning()) {
                    fadeTimer.start();
                }
            }

            Color baseColor = isSelected ? list.getSelectionForeground() : list.getForeground();
            int alpha = (int) (currentOpacity * 255);
            alpha = Math.max(0, Math.min(255, alpha));

            label.setForeground(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha));

            return this;
        }
    }

    // --- "INSANE" Animated Progress Bar UI ---
    private static class InsaneProgressBarUI extends BasicProgressBarUI {

        private final Timer shimmerTimer;
        private int shimmerOffset = 0;
        private final GradientPaint shimmerPaint;
        private final GradientPaint barPaint;

        public InsaneProgressBarUI() {
            shimmerPaint = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 0),
                    40, 40, new Color(255, 255, 255, 70),
                    true
            );
            barPaint = new GradientPaint(
                    0, 0, COLOR_ACCENT,
                    0, 30, COLOR_ACCENT_DARK
            );

            shimmerTimer = new Timer(25, e -> {
                shimmerOffset = (shimmerOffset + 1) % 40;
                if (progressBar != null) {
                    progressBar.repaint();
                }
            });
            shimmerTimer.start();
        }

        public void stopAnimation() {
            shimmerTimer.stop();
        }

        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = progressBar.getWidth();
            int h = progressBar.getHeight();
            int fillWidth = (int) (w * progressBar.getPercentComplete());

            g2.setColor(progressBar.getBackground());
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 15, 15));

            RoundRectangle2D fillClip = new RoundRectangle2D.Double(0, 0, fillWidth, h, 15, 15);
            g2.clip(fillClip);
            g2.setPaint(barPaint);
            g2.fill(fillClip);

            AffineTransform oldTransform = g2.getTransform();
            g2.translate(shimmerOffset, 0);
            g2.setPaint(shimmerPaint);
            g2.fill(new Rectangle(-40, 0, w + 40, h));
            g2.setTransform(oldTransform);

            g2.dispose();

            if (progressBar.isStringPainted()) {
                paintString(g, 0, 0, w, h, 0, progressBar.getInsets());
            }
        }

        @Override
        protected Color getSelectionBackground() { return COLOR_BG; }
        @Override
        protected Color getSelectionForeground() { return COLOR_TEXT_PRIMARY; }
    }

    // --- Custom ScrollBar UI ---
    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = COLOR_SCROLLBAR_THUMB;
            this.trackColor = COLOR_BG;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fill(new RoundRectangle2D.Double(thumbBounds.x + 2, thumbBounds.y + 2,
                    thumbBounds.width - 4, thumbBounds.height - 4, 10, 10));
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor); g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }

        @Override
        protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0,0));
            button.setMinimumSize(new Dimension(0,0));
            button.setMaximumSize(new Dimension(0,0));
            return button;
        }
    }
}