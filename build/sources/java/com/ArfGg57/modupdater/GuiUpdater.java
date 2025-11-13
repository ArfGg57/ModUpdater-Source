package com.ArfGg57.modupdater;

import javax.swing.*;

public class GuiUpdater {

    private final JFrame frame;
    private final JLabel label;
    private final JProgressBar progressBar;

    public GuiUpdater() {
        frame = new JFrame("ModUpdater");
        label = new JLabel("Initializing...", SwingConstants.CENTER);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        frame.setLayout(new java.awt.BorderLayout());
        frame.add(label, java.awt.BorderLayout.NORTH);
        frame.add(progressBar, java.awt.BorderLayout.SOUTH);
        frame.setSize(420, 110);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // In a headless dev server this may throw; wrap to avoid crash
        try {
            frame.setVisible(true);
        } catch (Throwable t) {
            System.out.println("GUI not available: " + t.getMessage());
        }
    }

    public void show(final String message) {
        // update label on EDT
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    label.setText(message);
                }
            });
        } catch (Throwable ignored) {}
        System.out.println(message);
    }

    public void setProgress(final int percent) {
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressBar.setValue(Math.max(0, Math.min(100, percent)));
                }
            });
        } catch (Throwable ignored) {}
        System.out.println("Progress: " + percent + "%");
    }
}
