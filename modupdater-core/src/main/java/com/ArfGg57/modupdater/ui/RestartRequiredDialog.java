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
 * Prompts user to restart the game to apply updates
 */
public class RestartRequiredDialog {
    
    // UI Constants matching other dialogs
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT = new Color(255, 153, 51);  // Orange for warning
    private static final Color COLOR_BUTTON = new Color(70, 73, 85);
    private static final Color COLOR_BUTTON_HOVER = new Color(90, 93, 105);
    
    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final int BUTTON_CORNER_RADIUS = 10;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);
    
    private JDialog dialog;
    private Point initialClick;
    private boolean continued = false;
    
    public RestartRequiredDialog(List<File> lockedFiles) {
        dialog = new JDialog((Frame) null, true); // Modal
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        
        // Allow dialog to be closed with ESC key or window close
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Add ESC key binding
        JRootPane rootPane = dialog.getRootPane();
        rootPane.registerKeyboardAction(e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
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
        
        // Title
        JLabel titleLabel = new JLabel("⚠ Restart Required", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_ACCENT);
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
        
        // Message
        JLabel messageLabel = new JLabel("<html><center>Some files could not be deleted because they are currently in use.<br>" +
                                         "Please restart the game to complete the update.</center></html>");
        messageLabel.setFont(FONT_BODY);
        messageLabel.setForeground(COLOR_TEXT_PRIMARY);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(messageLabel);
        centerPanel.add(Box.createVerticalStrut(15));
        
        // File list label
        JLabel fileListLabel = new JLabel("Files pending deletion:");
        fileListLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        fileListLabel.setForeground(COLOR_TEXT_SECONDARY);
        fileListLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(fileListLabel);
        centerPanel.add(Box.createVerticalStrut(5));
        
        // Scrollable file list
        JTextArea fileListArea = new JTextArea();
        fileListArea.setEditable(false);
        fileListArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        fileListArea.setBackground(new Color(44, 47, 55));
        fileListArea.setForeground(COLOR_TEXT_SECONDARY);
        fileListArea.setCaretColor(COLOR_TEXT_SECONDARY);
        
        StringBuilder fileListText = new StringBuilder();
        for (File file : lockedFiles) {
            fileListText.append("  • ").append(file.getName()).append("\n");
        }
        fileListArea.setText(fileListText.toString());
        
        JScrollPane scrollPane = new JScrollPane(fileListArea);
        scrollPane.setPreferredSize(new Dimension(400, 150));
        scrollPane.setMaximumSize(new Dimension(450, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 75), 1));
        scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(scrollPane);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel with Continue button
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
        bottomPanel.setOpaque(false);
        
        JButton continueButton = createStyledButton("Continue");
        continueButton.addActionListener(e -> {
            continued = true;
            dialog.dispose();
        });
        bottomPanel.add(continueButton);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.pack();
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
     * Show the dialog (blocks until user clicks Continue)
     */
    public void showDialog() {
        dialog.setVisible(true);
    }
    
    /**
     * Check if user clicked Continue
     */
    public boolean wasContinued() {
        return continued;
    }
}
