package com.ArfGg57.modupdater.ui;

public class TestGui {
    public static void main(String[] args) {
        GuiUpdater gui = new GuiUpdater();

        // Simulate updating for testing
        for (int i = 0; i <= 100; i += 10) {
            gui.setProgress(i);
            gui.show("Progress: " + i + "%");

            try {
                Thread.sleep(300); // pause 0.3s for demo
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        gui.show("Update complete!");
        // gui.close(); // optional: close GUI automatically
    }
}
