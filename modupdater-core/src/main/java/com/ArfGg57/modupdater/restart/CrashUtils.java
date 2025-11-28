package com.ArfGg57.modupdater.restart;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Utility helper to launch the restart cleanup helper process and manage persistent restart artifacts. */
public final class CrashUtils {
    private CrashUtils() {}
    
    // Persistent artifact paths under config/ModUpdater/
    private static final String PERSISTENT_LOCKED_LIST = "config/ModUpdater/locked_files.lst";
    private static final String RESTART_FLAG_FILE = "config/ModUpdater/restart_required.flag";
    private static final String RESTART_MESSAGE_FILE = "config/ModUpdater/restart_message.txt";

    /**
     * Launches the restart cleanup helper process.
     * This method delegates to CleanupHelperLauncher for the actual launch,
     * using the cleanup JAR instead of classpath-based execution.
     * 
     * @param pendingDeletes List of files that need to be deleted (for legacy compatibility, may be ignored)
     * @param message Message to display (for legacy compatibility, may be ignored)
     */
    public static void launchRestartCleanupHelper(List<File> pendingDeletes, String message) {
        // Use the CleanupHelperLauncher which properly launches the cleanup JAR
        File gameDir = new File(System.getProperty("user.dir"));
        boolean launched = CleanupHelperLauncher.launchCleanupHelper(gameDir);
        
        if (!launched) {
            // Fallback: try the legacy classpath-based approach
            System.out.println("[ModUpdater] CleanupHelperLauncher failed, trying legacy classpath approach...");
            try {
                Path listFile = writeDeleteList(pendingDeletes);
                List<String> command = buildCommand(listFile, message);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(gameDir);
                // Redirect output to log files for debugging
                File logDir = new File(gameDir, "config/ModUpdater/logs");
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                File stdout = new File(logDir, "restart-cleanup-stdout.log");
                File stderr = new File(logDir, "restart-cleanup-stderr.log");
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(stdout));
                pb.redirectError(ProcessBuilder.Redirect.appendTo(stderr));
                pb.start();
                System.out.println("[ModUpdater] Legacy restart helper started");
            } catch (IOException ex) {
                System.err.println("[ModUpdater] Failed to launch restart helper (legacy): " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            System.out.println("[ModUpdater] Cleanup helper launched via CleanupHelperLauncher");
        }
    }

    private static Path writeDeleteList(List<File> files) throws IOException {
        // Write to a persistent location instead of temp file
        Path listFile = Paths.get("config/ModUpdater/restart-deletes.lst");
        Files.createDirectories(listFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(listFile, StandardCharsets.UTF_8)) {
            if (files != null) {
                for (File f : files) {
                    writer.write(f.getAbsolutePath());
                    writer.newLine();
                }
            }
        }
        // Don't mark for deleteOnExit since the parent JVM will be killed
        return listFile;
    }

    private static List<String> buildCommand(Path listFile, String message) {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaBinary());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("com.ArfGg57.modupdater.restart.RestartCleanupMain");
        cmd.add(listFile.toAbsolutePath().toString());
        cmd.add(message == null ? "" : message);
        return cmd;
    }

    private static String resolveJavaBinary() {
        String javaHome = System.getProperty("java.home");
        String exe = isWindows() ? "java.exe" : "java";
        Path candidate = Paths.get(javaHome, "bin", exe);
        if (Files.exists(candidate)) {
            return candidate.toAbsolutePath().toString();
        }
        return "java"; // fallback to PATH
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    public static String writeLockedFileList(List<File> files) {
        try {
            // Write to a persistent location instead of temp file
            Path p = Paths.get("config/ModUpdater/locked-files-temp.lst");
            Files.createDirectories(p.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                if (files != null) {
                    for (File f : files) {
                        w.write(f.getAbsolutePath());
                        w.newLine();
                    }
                }
            }
            // Don't mark for deleteOnExit since the parent JVM will be killed
            return p.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to persist locked file list: " + e.getMessage());
            return "";
        }
    }
    
    // ========== Persistent Artifact Methods ==========
    
    /**
     * Writes the list of locked files to a persistent file under config/ModUpdater/.
     * This file will survive JVM restarts and can be used on the next game launch.
     */
    public static void writePersistentLockedFileList(List<File> files) {
        try {
            Path listPath = Paths.get(PERSISTENT_LOCKED_LIST);
            Files.createDirectories(listPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(listPath, StandardCharsets.UTF_8)) {
                if (files != null) {
                    for (File f : files) {
                        writer.write(f.getAbsolutePath());
                        writer.newLine();
                    }
                }
            }
            System.out.println("[ModUpdater] Wrote " + (files != null ? files.size() : 0) + 
                             " locked files to " + listPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to write persistent locked file list: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Writes a restart flag file and message file to indicate a restart is required.
     */
    public static void writeRestartFlag(String message) {
        try {
            Path flagPath = Paths.get(RESTART_FLAG_FILE);
            Path messagePath = Paths.get(RESTART_MESSAGE_FILE);
            Files.createDirectories(flagPath.getParent());
            
            // Create empty flag file
            Files.write(flagPath, new byte[0]);
            
            // Write message
            if (message != null && !message.isEmpty()) {
                Files.write(messagePath, message.getBytes(StandardCharsets.UTF_8));
            }
            
            System.out.println("[ModUpdater] Restart flag written to " + flagPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to write restart flag: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if a restart is required by checking for the existence of the restart flag file.
     */
    public static boolean isRestartRequired() {
        return Files.exists(Paths.get(RESTART_FLAG_FILE));
    }
    
    /**
     * Reads the restart message from the persistent message file.
     * Returns a default message if the file doesn't exist or can't be read.
     */
    public static String readRestartMessage() {
        try {
            Path messagePath = Paths.get(RESTART_MESSAGE_FILE);
            if (Files.exists(messagePath)) {
                return new String(Files.readAllBytes(messagePath), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to read restart message: " + e.getMessage());
        }
        return "Modpack update requires a restart. Locked files will be removed on next launch.";
    }
    
    /**
     * Reads the list of pending locked files from the persistent file.
     * Returns an empty list if the file doesn't exist or can't be read.
     */
    public static List<File> readPendingLockedFiles() {
        List<File> files = new ArrayList<>();
        try {
            Path listPath = Paths.get(PERSISTENT_LOCKED_LIST);
            if (Files.exists(listPath)) {
                try (BufferedReader reader = Files.newBufferedReader(listPath, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            files.add(new File(line.trim()));
                        }
                    }
                }
                System.out.println("[ModUpdater] Read " + files.size() + " locked files from " + listPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to read pending locked files: " + e.getMessage());
            e.printStackTrace();
        }
        return files;
    }
    
    /**
     * Clears all restart artifacts (flag, message, and locked files list).
     * Should be called after successfully processing the restart.
     */
    public static void clearRestartArtifacts() {
        try {
            Path flagPath = Paths.get(RESTART_FLAG_FILE);
            Path messagePath = Paths.get(RESTART_MESSAGE_FILE);
            Path listPath = Paths.get(PERSISTENT_LOCKED_LIST);
            
            Files.deleteIfExists(flagPath);
            Files.deleteIfExists(messagePath);
            Files.deleteIfExists(listPath);
            
            System.out.println("[ModUpdater] Cleared restart artifacts");
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to clear restart artifacts: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
