package com.ArfGg57.modupdater.restart;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for launching the cleanup helper process.
 * 
 * The cleanup helper is a separate JAR that runs in its own JVM.
 * It waits for the main game process to exit, then processes
 * pending operations (deletes and downloads) before the next game launch.
 */
public class CleanupHelperLauncher {
    
    private static final String CLEANUP_HELPER_JAR = "modupdater-cleanup.jar";
    private static final String PENDING_OPS_FILE = "config/ModUpdater/pending-update-ops.json";
    
    /**
     * Launch the cleanup helper process in the background.
     * 
     * @param gameDir The game directory (usually Minecraft's data directory)
     * @return true if the helper was launched successfully, false otherwise
     */
    public static boolean launchCleanupHelper(File gameDir) {
        System.out.println("[ModUpdater] Attempting to launch cleanup helper...");
        
        // Find the cleanup helper JAR
        File cleanupJar = findCleanupHelperJar(gameDir);
        if (cleanupJar == null || !cleanupJar.exists()) {
            System.err.println("[ModUpdater] Cleanup helper JAR not found. Looked in: " + gameDir);
            System.err.println("[ModUpdater] Expected file: " + CLEANUP_HELPER_JAR);
            return false;
        }
        
        System.out.println("[ModUpdater] Found cleanup helper JAR: " + cleanupJar.getAbsolutePath());
        
        // Get current JVM PID
        String pid = getCurrentPid();
        System.out.println("[ModUpdater] Current game PID: " + pid);
        
        // Build command to launch the helper
        List<String> command = new ArrayList<>();
        
        // Find Java executable
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javaBin += ".exe";
        }
        
        File javaFile = new File(javaBin);
        if (!javaFile.exists()) {
            // Fallback to just "java" and hope it's in PATH
            javaBin = "java";
        }
        
        command.add(javaBin);
        command.add("-jar");
        command.add(cleanupJar.getAbsolutePath());
        
        // Add arguments: PID, pending-ops path, game directory
        if (pid != null) {
            command.add(pid);
        } else {
            command.add(""); // Empty string for no PID
        }
        
        File pendingOpsFile = new File(gameDir, PENDING_OPS_FILE);
        command.add(pendingOpsFile.getAbsolutePath());
        command.add(gameDir.getAbsolutePath());
        
        System.out.println("[ModUpdater] Launching cleanup helper with command: " + String.join(" ", command));
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(gameDir);
            // Redirect output to files for debugging
            File logDir = new File(gameDir, "config/ModUpdater/logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File stdout = new File(logDir, "cleanup-helper-stdout.log");
            File stderr = new File(logDir, "cleanup-helper-stderr.log");
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(stdout));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(stderr));
            
            Process process = pb.start();
            System.out.println("[ModUpdater] Cleanup helper process started successfully");
            
            // Small delay to ensure process is running before we exit
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
            return process.isAlive();
            
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to launch cleanup helper: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Find the cleanup helper JAR file.
     * Searches in common locations:
     * 1. mods/ directory (alongside the main mod JAR)
     * 2. config/ModUpdater/ directory
     * 3. game directory root
     */
    private static File findCleanupHelperJar(File gameDir) {
        // Look for the cleanup helper JAR in various locations
        String[] searchPaths = {
            "mods/" + CLEANUP_HELPER_JAR,
            "mods/!!!!!modupdater-cleanup.jar",
            "mods/modupdater-cleanup-2.20.jar",
            "config/ModUpdater/" + CLEANUP_HELPER_JAR,
            CLEANUP_HELPER_JAR
        };
        
        for (String path : searchPaths) {
            File file = new File(gameDir, path);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        
        // Also try to find any JAR containing "modupdater-cleanup" in the mods folder
        File modsDir = new File(gameDir, "mods");
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] files = modsDir.listFiles((dir, name) -> 
                name.toLowerCase().contains("modupdater-cleanup") && name.endsWith(".jar"));
            if (files != null && files.length > 0) {
                return files[0];
            }
        }
        
        return null;
    }
    
    /**
     * Get the current JVM's process ID.
     */
    private static String getCurrentPid() {
        try {
            String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            if (jvmName != null && jvmName.contains("@")) {
                return jvmName.split("@")[0];
            }
        } catch (Exception e) {
            System.err.println("[ModUpdater] Failed to get PID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if there are pending operations that need processing.
     */
    public static boolean hasPendingOperations(File gameDir) {
        File pendingOpsFile = new File(gameDir, PENDING_OPS_FILE);
        return pendingOpsFile.exists() && pendingOpsFile.length() > 0;
    }
}
