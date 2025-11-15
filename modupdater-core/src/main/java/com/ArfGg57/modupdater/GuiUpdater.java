package com.ArfGg57.modupdater;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class GuiUpdater {
    private final JFrame frame;
    private final JLabel titleLabel;
    private final JTextArea logArea;
    private final JScrollPane scrollPane;
    private final JProgressBar progressBar;
    private final JButton cancelButton;
    private volatile boolean cancelled = false;

    public GuiUpdater() {
        frame = new JFrame("Mod Updater");
        frame.setUndecorated(true); // removes title bar (no close/minimize)
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // prevent closing

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(40, 44, 52));

        // Title label
        titleLabel = new JLabel("Updating Mods...", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(220, 220, 220));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setBackground(new Color(30, 34, 40));
        logArea.setForeground(new Color(200, 200, 200));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with progress and cancel
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(0, 180, 0));
        progressBar.setBackground(new Color(50, 50, 50));
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        cancelButton = new JButton("Cancel");
        cancelButton.setFocusPainted(false);
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setBackground(new Color(180, 50, 50));
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cancelButton.setBorder(BorderFactory.createLineBorder(new Color(150, 30, 30), 2));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelButton.addActionListener(e -> {
            cancelled = true;
            show("Cancellation requested...");
            cancelButton.setEnabled(false);
        });
        bottomPanel.add(cancelButton, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setSize(500, 350);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
    }

    public void show(final String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.out.println(message);
    }

    public void setProgress(final int percent) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(Math.max(0, Math.min(100, percent))));
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void close() {
        SwingUtilities.invokeLater(() -> frame.dispose());
    }
}
