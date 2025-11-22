package com.ArfGg57.modupdater.restart;

import com.ArfGg57.modupdater.ui.RestartRequiredDialog;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Standalone helper that deletes provided files then shows restart-required dialog. */
public class RestartCleanupMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) return;
        List<File> filesToDelete = readList(args[0]);
        deleteFiles(filesToDelete);
        
        // Show the styled RestartRequiredDialog
        SwingUtilities.invokeLater(() -> {
            RestartRequiredDialog dialog = new RestartRequiredDialog(filesToDelete);
            dialog.showDialog();
            System.exit(0);
        });
    }
    
    private static List<File> readList(String listPath) throws Exception {
        List<File> files = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(listPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    files.add(new File(line.trim()));
                }
            }
        }
        return files;
    }
    
    private static void deleteFiles(List<File> files) {
        for (File f : files) {
            if (f.exists()) {
                try {
                    Files.deleteIfExists(f.toPath());
                    System.out.println("[RestartCleanup] Deleted: " + f);
                } catch (Exception ex) {
                    System.err.println("[RestartCleanup] Failed to delete " + f + ": " + ex);
                }
            }
        }
    }
}
