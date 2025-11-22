package com.ArfGg57.modupdater.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class to visually verify the RestartRequiredDialog.
 * Run this to see the dialog with sample locked files.
 */
public class TestRestartRequiredDialog {
    public static void main(String[] args) {
        // Create sample locked files list
        List<File> lockedFiles = new ArrayList<>();
        lockedFiles.add(new File("mods/ExampleMod-1.0.0.jar"));
        lockedFiles.add(new File("mods/AnotherMod-2.1.3.jar"));
        lockedFiles.add(new File("config/someconfig.cfg"));
        lockedFiles.add(new File("config/modpack/settings.json"));
        
        // Create and show the dialog
        RestartRequiredDialog dialog = new RestartRequiredDialog(lockedFiles);
        dialog.showDialog();
        
        // Check results
        System.out.println("Dialog closed.");
        System.out.println("User clicked Close: " + dialog.wasContinued());
        System.out.println("User closed without clicking: " + dialog.wasClosedWithoutContinue());
        
        System.exit(0);
    }
}
