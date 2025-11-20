package com.ArfGg57.modupdater.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;

/**
 * CheckingUpdatesDialog: Shows progress while checking for updates
 * Displays before ModConfirmationDialog to inform user of progress
 */
public class CheckingUpdatesDialog {
    
    // UI Constants matching ModConfirmationDialog style
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    
    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    
    // Accessibility constant
    private static final String PROGRESS_BAR_ACCESSIBLE_NAME = "Update check progress";
    
    private JDialog dialog;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private Point initialClick;
    
    public CheckingUpdatesDialog() {
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
        
        // Title
        JLabel titleLabel = new JLabel("Checking for Updates...", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
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
        
        // Center panel with status
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);
        
        statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
        statusLabel.setFont(FONT_BODY);
        statusLabel.setForeground(COLOR_TEXT_SECONDARY);
        centerPanel.add(statusLabel, BorderLayout.CENTER);
        
        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(0, 10));
        progressBar.setForeground(COLOR_ACCENT);
        progressBar.setBackground(COLOR_BG.brighter());
        progressBar.getAccessibleContext().setAccessibleName(PROGRESS_BAR_ACCESSIBLE_NAME);
        centerPanel.add(progressBar, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        dialog.setContentPane(mainPanel);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
    }
    
    public void show() {
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }
    
    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }
    
    public void close() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }
}
