package com.ArfGg57.modupdater.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;

/**
 * WaitingForUpdateDialog: Shown when the game needs to crash/restart for updates.
 * Displays "Waiting for game to finish updates" with a progress indicator.
 * Styled to match the theme of ModConfirmationDialog and other ModUpdater GUIs.
 */
public class WaitingForUpdateDialog {
    
    // UI Constants matching ModConfirmationDialog style
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_LIST_BG = new Color(44, 47, 60);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT_WARNING = new Color(255, 153, 51);  // Orange for warning
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    private static final Color COLOR_ACCENT_DARK = new Color(0, 160, 80);
    
    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    
    // Accessibility constant
    private static final String PROGRESS_BAR_ACCESSIBLE_NAME = "Update progress";
    
    private JDialog dialog;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea fileListArea;
    private Point initialClick;
    
    /**
     * Create a new WaitingForUpdateDialog.
     * 
     * @param lockedFiles Optional list of files that are locked (can be null or empty)
     */
    public WaitingForUpdateDialog(List<File> lockedFiles) {
        dialog = new JDialog((Frame) null, false); // Non-modal
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        
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
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));
        
        // Header panel with title
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        
        // Title with warning icon
        JLabel titleLabel = new JLabel("⟳ Waiting for game to finish updates", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_ACCENT_WARNING);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(10));
        
        // Draggable
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleLabel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }
        });
        titleLabel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                Point loc = dialog.getLocation();
                dialog.setLocation(loc.x + xMoved, loc.y + yMoved);
            }
        });
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Center panel with status and optional file list
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);
        
        // Status message
        statusLabel = new JLabel("<html><center>The game will restart automatically to complete the update.<br>Please wait...</center></html>", SwingConstants.CENTER);
        statusLabel.setFont(FONT_BODY);
        statusLabel.setForeground(COLOR_TEXT_PRIMARY);
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        
        // Show locked files if any
        if (lockedFiles != null && !lockedFiles.isEmpty()) {
            JPanel filesPanel = new JPanel(new BorderLayout(5, 5));
            filesPanel.setOpaque(false);
            
            JLabel filesLabel = new JLabel("Files pending update:", SwingConstants.LEFT);
            filesLabel.setFont(FONT_SMALL);
            filesLabel.setForeground(COLOR_TEXT_SECONDARY);
            filesPanel.add(filesLabel, BorderLayout.NORTH);
            
            fileListArea = new JTextArea();
            fileListArea.setEditable(false);
            fileListArea.setFont(new Font("Consolas", Font.PLAIN, 11));
            fileListArea.setBackground(COLOR_LIST_BG);
            fileListArea.setForeground(COLOR_TEXT_SECONDARY);
            fileListArea.setCaretColor(COLOR_TEXT_SECONDARY);
            
            StringBuilder fileListText = new StringBuilder();
            for (File file : lockedFiles) {
                fileListText.append("  • ").append(file.getName()).append("\n");
            }
            fileListArea.setText(fileListText.toString());
            
            JScrollPane scrollPane = new JScrollPane(fileListArea);
            scrollPane.setPreferredSize(new Dimension(350, 100));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 75), 1));
            filesPanel.add(scrollPane, BorderLayout.CENTER);
            
            centerPanel.add(filesPanel, BorderLayout.CENTER);
        }
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Progress bar at bottom
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setOpaque(false);
        
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(0, 15));
        progressBar.setForeground(COLOR_ACCENT);
        progressBar.setBackground(COLOR_LIST_BG);
        progressBar.getAccessibleContext().setAccessibleName(PROGRESS_BAR_ACCESSIBLE_NAME);
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        
        // Info text at bottom
        JLabel infoLabel = new JLabel("Do not close this window", SwingConstants.CENTER);
        infoLabel.setFont(FONT_SMALL);
        infoLabel.setForeground(COLOR_TEXT_SECONDARY);
        bottomPanel.add(infoLabel, BorderLayout.SOUTH);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.setContentPane(mainPanel);
        dialog.setSize(450, lockedFiles != null && !lockedFiles.isEmpty() ? 320 : 200);
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
    }
    
    /**
     * Show the dialog.
     */
    public void show() {
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.setVisible(true);
        } else {
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        }
    }
    
    /**
     * Update the status message.
     */
    public void updateStatus(String status) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText("<html><center>" + status + "</center></html>");
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText("<html><center>" + status + "</center></html>"));
        }
    }
    
    /**
     * Set progress to determinate mode with a specific value.
     */
    public void setProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(Math.max(0, Math.min(100, percent)));
        });
    }
    
    /**
     * Close the dialog.
     */
    public void close() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }
    
    /**
     * Check if the dialog is currently visible.
     */
    public boolean isVisible() {
        return dialog.isVisible();
    }
}
