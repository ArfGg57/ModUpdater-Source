package com.ArfGg57.modupdater.restart;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Utility helper to launch the restart cleanup helper process. */
public final class CrashUtils {
    private CrashUtils() {}

    public static void launchRestartCleanupHelper(List<File> pendingDeletes, String message) {
        try {
            Path listFile = writeDeleteList(pendingDeletes);
            List<String> command = buildCommand(listFile, message);
            new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.dir")))
                    .start();
        } catch (IOException ex) {
            System.err.println("[ModUpdater] Failed to launch restart helper: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static Path writeDeleteList(List<File> files) throws IOException {
        Path listFile = Files.createTempFile("modupdater-restart", ".lst");
        try (BufferedWriter writer = Files.newBufferedWriter(listFile, StandardCharsets.UTF_8)) {
            if (files != null) {
                for (File f : files) {
                    writer.write(f.getAbsolutePath());
                    writer.newLine();
                }
            }
        }
        listFile.toFile().deleteOnExit();
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
            Path p = Files.createTempFile("modupdater-locked", ".lst");
            try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                if (files != null) {
                    for (File f : files) {
                        w.write(f.getAbsolutePath());
                        w.newLine();
                    }
                }
            }
            p.toFile().deleteOnExit();
            return p.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to persist locked file list: " + e.getMessage());
            return "";
        }
    }
}
