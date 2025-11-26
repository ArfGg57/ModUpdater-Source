package com.ArfGg57.modupdater.ui;

import javax.swing.SwingUtilities;

/**
 * Test class to visually verify the PostRestartUpdateGui.
 * Run this to see the dialog with simulated update operations.
 */
public class TestPostRestartUpdateGui {
    public static void main(String[] args) throws Exception {
        // Create and show the GUI on EDT
        SwingUtilities.invokeAndWait(() -> {
            PostRestartUpdateGui gui = new PostRestartUpdateGui();
            gui.show();
            
            // Start simulation in background thread
            new Thread(() -> {
                try {
                    simulateUpdateOperations(gui);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }).start();
        });
    }
    
    private static void simulateUpdateOperations(PostRestartUpdateGui gui) throws InterruptedException {
        // Simulate some update operations
        int totalOps = 5;
        int completedOps = 0;
        
        Thread.sleep(1000);  // Initial delay to show the starting state
        
        // Simulate deletes
        gui.addLog("Processing 2 delete operations...");
        Thread.sleep(500);
        
        gui.addLog("Deleting: OldMod-1.0.0.jar");
        Thread.sleep(400);
        completedOps++;
        gui.setProgress((completedOps * 100) / totalOps);
        
        gui.addLog("Deleting: OutdatedConfig.cfg");
        Thread.sleep(400);
        completedOps++;
        gui.setProgress((completedOps * 100) / totalOps);
        
        // Simulate downloads
        gui.addLog("Processing 3 download operations...");
        Thread.sleep(500);
        
        gui.addLog("Downloading: NewMod-2.0.0.jar");
        Thread.sleep(800);
        gui.addLog("Installed: NewMod-2.0.0.jar");
        completedOps++;
        gui.setProgress((completedOps * 100) / totalOps);
        
        gui.addLog("Downloading: AnotherMod-1.5.0.jar");
        Thread.sleep(800);
        gui.addLog("Installed: AnotherMod-1.5.0.jar");
        completedOps++;
        gui.setProgress((completedOps * 100) / totalOps);
        
        gui.addLog("Downloading: ConfigPack.zip");
        Thread.sleep(600);
        gui.addLog("Installed: ConfigPack.zip");
        completedOps++;
        gui.setProgress((completedOps * 100) / totalOps);
        
        // Show completion
        Thread.sleep(500);
        gui.addLog("All operations completed");
        gui.showComplete();
        
        System.out.println("Test completed - GUI should show completion state with Close button");
    }
}
