package com.ArfGg57.modupdater.ui;

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

/**
 * PostRestartUpdateGui: Shows after a restart was required for modpack update.
 * Displays:
 * 1. Initial message: "A restart was required for this modpack update. Finishing Update..."
 * 2. Progress of file operations (deletes and downloads)
 * 3. Final message: "Update finished! Please relaunch your game."
 * 
 * Styled to match the other ModUpdater GUIs (GuiUpdater, etc.)
 */
public class PostRestartUpdateGui {

    // --- UI Theme Constants (matching GuiUpdater) ---
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_LIST_BG = new Color(44, 47, 60);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(200, 200, 200);
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    private static final Color COLOR_ACCENT_DARK = new Color(0, 160, 80);
    private static final Color COLOR_ACCENT_WARNING = new Color(255, 153, 51);  // Orange for "restart required" theme
    private static final Color COLOR_SCROLLBAR_THUMB = new Color(80, 85, 100);

    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font FONT_STATUS = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_LOG = new Font("Consolas", Font.PLAIN, 13);
    private static final Font FONT_PROGRESS = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);

    // --- Swing Components ---
    private final JFrame frame;
    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JList<String> taskList;
    private final DefaultListModel<String> taskListModel;
    private final JProgressBar progressBar;
    private final JButton closeButton;

    private Point initialClick;
    private Timer progressAnimator;
    private int targetProgress = 0;
    
    // State tracking
    private volatile boolean updateComplete = false;

    public PostRestartUpdateGui() {
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
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Header panel with title and status
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);

        // Title label (draggable) with warning accent color
        titleLabel = new JLabel("! Finishing Update", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_ACCENT_WARNING);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

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
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(8));

        // Status label showing main message
        statusLabel = new JLabel("<html><center>A restart was required for this modpack update.<br>Finishing Update...</center></html>");
        statusLabel.setFont(FONT_STATUS);
        statusLabel.setForeground(COLOR_TEXT_PRIMARY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(statusLabel);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center panel with task list
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);

        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);
        taskList.setCellRenderer(new TaskListRenderer());
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

        // Bottom panel with progress bar and close button
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setOpaque(false);

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(FONT_PROGRESS);
        progressBar.setForeground(COLOR_ACCENT);
        progressBar.setBackground(COLOR_LIST_BG);
        progressBar.setPreferredSize(new Dimension(0, 25));
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        progressBar.setUI(new AnimatedProgressBarUI());
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        // Close button (initially hidden)
        closeButton = createStyledButton("Close");
        closeButton.setVisible(false);
        closeButton.addActionListener(e -> {
            frame.dispose();
            System.exit(0);
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // --- Timer for progress animation ---
        progressAnimator = new Timer(20, e -> updateProgressAnimation());

        // Frame setup
        frame.setContentPane(mainPanel);
        frame.setSize(550, 400);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
    }

    // --- Animation logic for progress bar ---
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

    // --- Public API methods ---

    /**
     * Show the GUI
     */
    public void show() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    /**
     * Add a log message to the task list
     */
    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            taskListModel.addElement(" > " + message);
            taskList.ensureIndexIsVisible(taskListModel.getSize() - 1);
        });
        System.out.println("[PostRestartUpdate] " + message);
    }

    /**
     * Update the status message
     */
    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("<html><center>" + status + "</center></html>"));
    }

    /**
     * Set the progress percentage (0-100)
     */
    public void setProgress(int percent) {
        targetProgress = Math.max(0, Math.min(100, percent));
        if (!progressAnimator.isRunning()) {
            progressAnimator.start();
        }
    }

    /**
     * Mark the update as complete - shows completion message and close button
     */
    public void showComplete() {
        updateComplete = true;
        SwingUtilities.invokeLater(() -> {
            titleLabel.setText("Update Complete!");
            titleLabel.setForeground(COLOR_ACCENT);  // Green for success
            statusLabel.setText("<html><center>Update finished!<br>Please relaunch your game.</center></html>");
            setProgress(100);
            closeButton.setVisible(true);
            
            // Also add a final log entry
            taskListModel.addElement(" ✓ All operations completed successfully");
            taskList.ensureIndexIsVisible(taskListModel.getSize() - 1);
        });
    }

    /**
     * Close the GUI
     */
    public void close() {
        progressAnimator.stop();
        ((AnimatedProgressBarUI) progressBar.getUI()).stopAnimation();
        frame.setVisible(false);
        frame.dispose();
    }

    /**
     * Check if the update is complete
     */
    public boolean isComplete() {
        return updateComplete;
    }

    // --- Button styling ---
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor;
                if (getModel().isPressed()) {
                    bgColor = COLOR_ACCENT_DARK;
                } else if (getModel().isRollover()) {
                    bgColor = COLOR_ACCENT;
                } else {
                    bgColor = new Color(70, 73, 85);
                }

                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));

                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);

                g2.dispose();
            }
        };

        button.setFont(FONT_BUTTON);
        button.setForeground(COLOR_TEXT_PRIMARY);
        button.setPreferredSize(new Dimension(150, 40));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        return button;
    }

    // --- Custom ListCellRenderer for task list ---
    private static class TaskListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel label;

        public TaskListRenderer() {
            super(new BorderLayout());

            label = new JLabel();
            label.setFont(FONT_LOG);
            label.setForeground(COLOR_TEXT_SECONDARY);
            setBorder(new EmptyBorder(2, 5, 2, 5));
            setOpaque(true);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            label.setText(value);
            setBackground(COLOR_LIST_BG);

            // Highlight completion messages in green
            if (value != null && value.contains("✓")) {
                label.setForeground(COLOR_ACCENT);
            } else {
                Color baseColor = isSelected ? list.getSelectionForeground() : list.getForeground();
                label.setForeground(baseColor);
            }

            return this;
        }
    }

    // --- Animated Progress Bar UI (matching GuiUpdater) ---
    private static class AnimatedProgressBarUI extends BasicProgressBarUI {

        private final Timer shimmerTimer;
        private int shimmerOffset = 0;
        private final GradientPaint shimmerPaint;
        private final GradientPaint barPaint;

        public AnimatedProgressBarUI() {
            shimmerPaint = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 0),
                    40, 40, new Color(255, 255, 255, 70),
                    true
            );
            barPaint = new GradientPaint(
                    0, 0, COLOR_ACCENT,
                    0, 25, COLOR_ACCENT_DARK
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
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));

            RoundRectangle2D fillClip = new RoundRectangle2D.Double(0, 0, fillWidth, h, 12, 12);
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

    // --- Custom ScrollBar UI (matching GuiUpdater) ---
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
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
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
