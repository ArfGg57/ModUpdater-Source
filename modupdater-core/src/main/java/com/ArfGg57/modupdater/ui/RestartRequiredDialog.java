package com.ArfGg57.modupdater.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;

/**
 * RestartRequiredDialog: Shows when files could not be deleted due to locks
 * Prompts user to restart the game to apply updates.
 * Styled to match the theme of ModConfirmationDialog and other ModUpdater GUIs.
 */
public class RestartRequiredDialog {
    
    // Message constant
    public static final String RESTART_MESSAGE = "A restart was required for this modpack update. Please relaunch the game to complete the update.";
    
    // UI Constants matching other dialogs
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_LIST_BG = new Color(44, 47, 60);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT_WARNING = new Color(255, 153, 51);  // Orange for warning
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    private static final Color COLOR_BUTTON = new Color(70, 73, 85);
    private static final Color COLOR_BUTTON_HOVER = new Color(90, 93, 105);
    
    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final int BUTTON_CORNER_RADIUS = 10;
    private static final int MESSAGE_WIDTH_PX = 350;
    private static final int DIALOG_MIN_WIDTH = 450;
    private static final int DIALOG_MIN_HEIGHT = 300;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);
    
    private JDialog dialog;
    private Point initialClick;
    private boolean continued = false;
    private boolean closedWithoutContinue = false;
    
    public RestartRequiredDialog(List<File> lockedFiles) {
        dialog = new JDialog((Frame) null, true); // Modal
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        
        // Set up window close listener to track when user closes without clicking Continue
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                closedWithoutContinue = true;
            }
        });
        
        // Add ESC key binding
        JRootPane rootPane = dialog.getRootPane();
        rootPane.registerKeyboardAction(e -> {
            closedWithoutContinue = true;
            dialog.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        
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
        
        // Title (using Unicode symbol with accessible description)
        JLabel titleLabel = new JLabel("⚠ Restart Required", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_ACCENT_WARNING);
        titleLabel.getAccessibleContext().setAccessibleName("Restart Required");
        titleLabel.getAccessibleContext().setAccessibleDescription("A restart is required to complete the modpack update");
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Make dialog draggable
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
        
        // Center panel with message and file list
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        
        // Message - use HTML with width constraint for proper text wrapping
        JLabel messageLabel = new JLabel("<html><div style='width: " + MESSAGE_WIDTH_PX + "px; text-align: center;'>" + RESTART_MESSAGE + "</div></html>");
        messageLabel.setFont(FONT_BODY);
        messageLabel.setForeground(COLOR_TEXT_PRIMARY);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(messageLabel);
        centerPanel.add(Box.createVerticalStrut(20));
        
        // File list label (only if there are locked files)
        if (lockedFiles != null && !lockedFiles.isEmpty()) {
            JLabel fileListLabel = new JLabel("Files pending update:");
            fileListLabel.setFont(FONT_SMALL);
            fileListLabel.setForeground(COLOR_TEXT_SECONDARY);
            fileListLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(fileListLabel);
            centerPanel.add(Box.createVerticalStrut(8));
            
            // Scrollable file list with better styling
            JTextArea fileListArea = new JTextArea();
            fileListArea.setEditable(false);
            fileListArea.setFont(new Font("Consolas", Font.PLAIN, 11));
            fileListArea.setBackground(COLOR_LIST_BG);
            fileListArea.setForeground(COLOR_TEXT_SECONDARY);
            fileListArea.setCaretColor(COLOR_TEXT_SECONDARY);
            fileListArea.setMargin(new java.awt.Insets(8, 8, 8, 8));
            
            StringBuilder fileListText = new StringBuilder();
            for (File file : lockedFiles) {
                fileListText.append("  • ").append(file.getName()).append("\n");
            }
            fileListArea.setText(fileListText.toString().trim()); // trim trailing newline
            
            JScrollPane scrollPane = new JScrollPane(fileListArea);
            scrollPane.setPreferredSize(new Dimension(380, 100));
            scrollPane.setMaximumSize(new Dimension(400, 100));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 75), 1));
            scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(scrollPane);
            centerPanel.add(Box.createVerticalStrut(10));
        } else {
            // No files to show - just add some space
            centerPanel.add(Box.createVerticalStrut(20));
        }
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel with Close button
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
        bottomPanel.setOpaque(false);
        
        JButton closeButton = createStyledButton("Close");
        closeButton.addActionListener(e -> {
            continued = true;
            dialog.dispose();
        });
        bottomPanel.add(closeButton);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.pack();
        // Set minimum size to ensure proper layout
        dialog.setMinimumSize(new Dimension(DIALOG_MIN_WIDTH, DIALOG_MIN_HEIGHT));
        dialog.setLocationRelativeTo(null);
    }
    
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (getModel().isPressed()) {
                    g2.setColor(COLOR_BUTTON);
                } else if (getModel().isRollover()) {
                    g2.setColor(COLOR_BUTTON_HOVER);
                } else {
                    g2.setColor(COLOR_BUTTON);
                }
                
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 
                    BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS));
                
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
    
    /**
     * Show the dialog (blocks until user clicks Close)
     */
    public void showDialog() {
        dialog.setVisible(true);
    }
    
    /**
     * Check if user clicked Close
     */
    public boolean wasContinued() {
        return continued;
    }
    
    /**
     * Check if user closed dialog without clicking Close
     */
    public boolean wasClosedWithoutContinue() {
        return closedWithoutContinue;
    }
}
