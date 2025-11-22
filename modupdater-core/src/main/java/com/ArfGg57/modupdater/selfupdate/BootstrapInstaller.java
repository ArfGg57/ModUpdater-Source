package com.ArfGg57.modupdater.selfupdate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and manages a lightweight bootstrap launcher that replaces the ModUpdater JAR
 * on the next Minecraft launch.
 * 
 * The bootstrap is a small Java program that:
 * 1. Waits for the current process to exit
 * 2. Backs up the current JAR
 * 3. Replaces it with the new JAR
 * 4. Cleans up temporary files
 * 5. Exits so Minecraft can continue launching
 */
public class BootstrapInstaller {
    
    // Configurable delay before starting update (in seconds)
    private static final int BOOTSTRAP_DELAY_SECONDS = 5;
    
    /**
     * Logger interface for output messages
     */
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    
    public BootstrapInstaller(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Install the bootstrap that will replace the JAR on next launch
     * 
     * @param currentJar The current ModUpdater JAR file
     * @param newJar The new ModUpdater JAR file to install
     * @param stagingDir Directory for temporary files
     * @return true if bootstrap was installed successfully
     */
    public boolean installBootstrap(File currentJar, File newJar, File stagingDir) throws IOException {
        logger.log("Installing bootstrap for JAR replacement");
        logger.log("Current JAR: " + currentJar.getAbsolutePath());
        logger.log("New JAR: " + newJar.getAbsolutePath());
        
        // Validate inputs
        if (!newJar.exists()) {
            throw new FileNotFoundException("New JAR not found: " + newJar.getAbsolutePath());
        }
        
        if (!currentJar.exists()) {
            logger.log("Warning: Current JAR not found, will perform clean install");
        }
        
        // Create bootstrap JAR
        File bootstrapJar = new File(stagingDir, "modupdater-bootstrap.jar");
        createBootstrapJar(bootstrapJar, currentJar, newJar);
        
        // Create marker file that tells the coremod/tweaker to run the bootstrap
        File markerFile = new File(stagingDir, "pending-self-update.json");
        createMarkerFile(markerFile, currentJar, newJar, bootstrapJar);
        
        logger.log("Bootstrap installed successfully");
        logger.log("Update will be applied on next Minecraft launch");
        
        return true;
    }
    
    /**
     * Create a marker file that describes the pending update
     */
    private void createMarkerFile(File markerFile, File currentJar, File newJar, File bootstrapJar) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"operation\": \"self-update\",\n");
        sb.append("  \"current_jar\": \"").append(escape(currentJar.getAbsolutePath())).append("\",\n");
        sb.append("  \"new_jar\": \"").append(escape(newJar.getAbsolutePath())).append("\",\n");
        sb.append("  \"bootstrap_jar\": \"").append(escape(bootstrapJar.getAbsolutePath())).append("\",\n");
        sb.append("  \"timestamp\": ").append(System.currentTimeMillis()).append("\n");
        sb.append("}\n");
        
        try (FileOutputStream fos = new FileOutputStream(markerFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        logger.log("Created marker file: " + markerFile.getAbsolutePath());
    }
    
    /**
     * Create the bootstrap JAR - a minimal Java program
     * 
     * For simplicity, we'll create a shell script/batch file instead of a JAR
     * This is more reliable and easier to maintain
     */
    private void createBootstrapJar(File bootstrapFile, File currentJar, File newJar) throws IOException {
        // Determine if we're on Windows or Unix
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        
        if (isWindows) {
            createWindowsBootstrap(bootstrapFile, currentJar, newJar);
        } else {
            createUnixBootstrap(bootstrapFile, currentJar, newJar);
        }
    }
    
    /**
     * Create Windows batch file bootstrap
     */
    private void createWindowsBootstrap(File bootstrapFile, File currentJar, File newJar) throws IOException {
        File batchFile = new File(bootstrapFile.getParentFile(), "modupdater-bootstrap.bat");
        
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\n");
        sb.append("REM ModUpdater Self-Update Bootstrap\n");
        sb.append("REM This script replaces the old JAR with the new one\n");
        sb.append("\n");
        sb.append("echo ModUpdater Self-Update Bootstrap\n");
        sb.append("echo Waiting for Minecraft to close...\n");
        sb.append("\n");
        sb.append("REM Wait a bit for the game to fully close\n");
        sb.append("timeout /t ").append(BOOTSTRAP_DELAY_SECONDS).append(" /nobreak >nul\n");
        sb.append("\n");
        sb.append("REM Backup current JAR\n");
        sb.append("if exist \"").append(currentJar.getAbsolutePath()).append("\" (\n");
        sb.append("    echo Backing up current JAR...\n");
        sb.append("    copy /Y \"").append(currentJar.getAbsolutePath()).append("\" \"");
        sb.append(currentJar.getAbsolutePath()).append(".backup\" >nul\n");
        sb.append("    if errorlevel 1 (\n");
        sb.append("        echo ERROR: Failed to backup current JAR\n");
        sb.append("        pause\n");
        sb.append("        exit /b 1\n");
        sb.append("    )\n");
        sb.append(")\n");
        sb.append("\n");
        sb.append("REM Copy new JAR to replace old one\n");
        sb.append("echo Installing new ModUpdater JAR...\n");
        sb.append("copy /Y \"").append(newJar.getAbsolutePath()).append("\" \"");
        sb.append(currentJar.getAbsolutePath()).append("\" >nul\n");
        sb.append("if errorlevel 1 (\n");
        sb.append("    echo ERROR: Failed to install new JAR\n");
        sb.append("    echo Attempting to restore backup...\n");
        sb.append("    copy /Y \"").append(currentJar.getAbsolutePath()).append(".backup\" \"");
        sb.append(currentJar.getAbsolutePath()).append("\" >nul\n");
        sb.append("    pause\n");
        sb.append("    exit /b 1\n");
        sb.append(")\n");
        sb.append("\n");
        sb.append("echo ModUpdater updated successfully!\n");
        sb.append("echo Cleaning up...\n");
        sb.append("\n");
        sb.append("REM Clean up temporary files\n");
        sb.append("del /F /Q \"").append(newJar.getAbsolutePath()).append("\" 2>nul\n");
        sb.append("del /F /Q \"").append(batchFile.getAbsolutePath()).append("\" 2>nul\n");
        sb.append("\n");
        sb.append("exit /b 0\n");
        
        try (FileOutputStream fos = new FileOutputStream(batchFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        // Copy path to the "JAR" file so caller knows where it is
        try (FileOutputStream fos = new FileOutputStream(bootstrapFile)) {
            fos.write(batchFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        }
        
        logger.log("Created Windows bootstrap: " + batchFile.getAbsolutePath());
    }
    
    /**
     * Create Unix shell script bootstrap
     */
    private void createUnixBootstrap(File bootstrapFile, File currentJar, File newJar) throws IOException {
        File scriptFile = new File(bootstrapFile.getParentFile(), "modupdater-bootstrap.sh");
        
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("# ModUpdater Self-Update Bootstrap\n");
        sb.append("# This script replaces the old JAR with the new one\n");
        sb.append("\n");
        sb.append("echo \"ModUpdater Self-Update Bootstrap\"\n");
        sb.append("echo \"Waiting for Minecraft to close...\"\n");
        sb.append("\n");
        sb.append("# Wait a bit for the game to fully close\n");
        sb.append("sleep ").append(BOOTSTRAP_DELAY_SECONDS).append("\n");
        sb.append("\n");
        sb.append("# Backup current JAR\n");
        sb.append("if [ -f \"").append(currentJar.getAbsolutePath()).append("\" ]; then\n");
        sb.append("    echo \"Backing up current JAR...\"\n");
        sb.append("    cp -f \"").append(currentJar.getAbsolutePath()).append("\" \"");
        sb.append(currentJar.getAbsolutePath()).append(".backup\"\n");
        sb.append("    if [ $? -ne 0 ]; then\n");
        sb.append("        echo \"ERROR: Failed to backup current JAR\"\n");
        sb.append("        exit 1\n");
        sb.append("    fi\n");
        sb.append("fi\n");
        sb.append("\n");
        sb.append("# Copy new JAR to replace old one\n");
        sb.append("echo \"Installing new ModUpdater JAR...\"\n");
        sb.append("cp -f \"").append(newJar.getAbsolutePath()).append("\" \"");
        sb.append(currentJar.getAbsolutePath()).append("\"\n");
        sb.append("if [ $? -ne 0 ]; then\n");
        sb.append("    echo \"ERROR: Failed to install new JAR\"\n");
        sb.append("    echo \"Attempting to restore backup...\"\n");
        sb.append("    cp -f \"").append(currentJar.getAbsolutePath()).append(".backup\" \"");
        sb.append(currentJar.getAbsolutePath()).append("\"\n");
        sb.append("    exit 1\n");
        sb.append("fi\n");
        sb.append("\n");
        sb.append("echo \"ModUpdater updated successfully!\"\n");
        sb.append("echo \"Cleaning up...\"\n");
        sb.append("\n");
        sb.append("# Clean up temporary files\n");
        sb.append("rm -f \"").append(newJar.getAbsolutePath()).append("\"\n");
        sb.append("rm -f \"").append(scriptFile.getAbsolutePath()).append("\"\n");
        sb.append("\n");
        sb.append("exit 0\n");
        
        try (FileOutputStream fos = new FileOutputStream(scriptFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        // Make script executable
        scriptFile.setExecutable(true);
        
        // Copy path to the "JAR" file so caller knows where it is
        try (FileOutputStream fos = new FileOutputStream(bootstrapFile)) {
            fos.write(scriptFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        }
        
        logger.log("Created Unix bootstrap: " + scriptFile.getAbsolutePath());
    }
    
    /**
     * Escape JSON string
     */
    private String escape(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    /**
     * Check if there's a pending self-update
     */
    public static boolean hasPendingUpdate(File stagingDir) {
        File markerFile = new File(stagingDir, "pending-self-update.json");
        return markerFile.exists();
    }
    
    /**
     * Execute the bootstrap to perform the update
     * This should be called early in the launch process (e.g., from coremod or tweaker)
     */
    public static boolean executeBootstrap(File stagingDir, Logger logger) throws IOException {
        File markerFile = new File(stagingDir, "pending-self-update.json");
        if (!markerFile.exists()) {
            return false;  // No pending update
        }
        
        logger.log("Found pending self-update, executing bootstrap...");
        
        // Read the marker file to get bootstrap location
        File bootstrapFile = new File(stagingDir, "modupdater-bootstrap.jar");
        if (!bootstrapFile.exists()) {
            logger.log("Warning: Bootstrap marker exists but bootstrap file not found");
            markerFile.delete();
            return false;
        }
        
        // Read the actual script path
        String scriptPath;
        try (FileInputStream fis = new FileInputStream(bootstrapFile);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = fis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            scriptPath = new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
        }
        
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            logger.log("Warning: Bootstrap script not found: " + scriptPath);
            markerFile.delete();
            bootstrapFile.delete();
            return false;
        }
        
        // Execute the bootstrap script
        logger.log("Executing bootstrap: " + scriptPath);
        
        try {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");
            
            List<String> command = new ArrayList<>();
            if (isWindows) {
                command.add("cmd.exe");
                command.add("/c");
                command.add("start");
                command.add("/min");
                command.add(scriptPath);
            } else {
                command.add("/bin/sh");
                command.add(scriptPath);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.start();
            
            logger.log("Bootstrap started successfully");
            
            // Delete marker file to indicate we've started the update
            markerFile.delete();
            
            return true;
        } catch (Exception e) {
            logger.log("Failed to execute bootstrap: " + e.getMessage());
            return false;
        }
    }
}
