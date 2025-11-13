package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File utilities: read JSON, delete with backup, unzip, version read/write.
 * Implemented using java.io so it's compatible with Java 7+.
 */
public class FileUtils {

    // These constants are critical for API stability (CurseForge/Modrinth)
    private static final String API_USER_AGENT = "ModUpdater/1.7.10-ArfGg57";
    private static final int API_TIMEOUT = 10000; // 10 seconds

    // --- JSON utilities ---
    public static JSONObject readJson(String path) throws Exception {
        String s = readFileToString(path);
        return new JSONObject(s);
    }

    public static JSONObject readJsonFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr);
        return new JSONObject(new JSONTokener(s));
    }

    public static JSONArray readJsonArrayFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr);
        return new JSONArray(new JSONTokener(s));
    }

    private static String readFileToString(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("File not found: " + f.getAbsolutePath());

        // Use try-with-resources for resource safety
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), "UTF-8");
        }
    }

    // 🛑 CRITICAL FIX: Changed from simple url.openStream() to HttpURLConnection
    // to allow setting User-Agent and timeouts for API access.
    private static String readUrlToString(String urlStr) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();

            // Set mandatory headers and timeouts
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", API_USER_AGENT);
            connection.setConnectTimeout(API_TIMEOUT);
            connection.setReadTimeout(API_TIMEOUT);

            int responseCode = connection.getResponseCode();

            // Check for successful HTTP response code (200-299)
            if (responseCode < 200 || responseCode >= 400) {
                // Try reading error stream for more details if available
                String errorDetails = readStream(connection.getErrorStream());
                throw new IOException("HTTP Error " + responseCode + " for " + urlStr + ". Details: " + errorDetails);
            }

            // Read the successful response stream
            return readStream(connection.getInputStream());

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Helper method to read an InputStream fully into a String. */
    private static String readStream(java.io.InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        // Use try-with-resources to ensure BufferedReader is closed
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }


    // --- Delete / backup ---
    public static void deleteFiles(JSONArray paths, boolean backup, GuiUpdater gui) throws Exception {
        // FIX: The loop condition was incorrectly placed in the initialization block.
        // It must be 'for (int i = 0; i < paths.length(); i++)'
        for (int i = 0; i < paths.length(); i++) {
            String p = paths.getString(i);
            File target = new File(p);
            if (!target.exists()) {
                gui.show("Not found (skip): " + p);
                continue;
            }

            if (backup) {
                backupFile(target, gui);
            }

            // If directory: collect subtree and delete children first
            if (target.isDirectory()) {
                List<File> all = listFilesRecursively(target);
                // Sort descending so children are deleted before parents
                Collections.sort(all, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return b.getAbsolutePath().compareTo(a.getAbsolutePath());
                    }
                });
                for (File f : all) {
                    deleteSilently(f, gui);
                }
                // finally delete the directory itself
                deleteSilently(target, gui);
            } else {
                deleteSilently(target, gui);
            }
        }
    }

    private static void deleteSilently(File f, GuiUpdater gui) {
        try {
            if (f.exists()) {
                boolean ok = f.delete();
                gui.show("Deleted: " + f.getAbsolutePath() + (ok ? "" : " (delete returned false)"));
            }
        } catch (Exception e) {
            gui.show("Error deleting " + f.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static List<File> listFilesRecursively(File dir) {
        List<File> out = new ArrayList<File>();
        File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                File c = children[i];
                out.add(c);
                if (c.isDirectory()) {
                    out.addAll(listFilesRecursively(c));
                }
            }
        }
        return out;
    }

    /**
     * Backup a file or directory to modupdater_backups preserving relative path.
     */
    public static void backupFile(File fileOrDir, GuiUpdater gui) throws Exception {
        File backupRoot = new File("modupdater_backups");
        if (!backupRoot.exists()) backupRoot.mkdirs();

        // If it's a directory, zip/recursive copy would be better; keep simple: copy children
        if (fileOrDir.isDirectory()) {
            List<File> all = listFilesRecursively(fileOrDir);
            for (File f : all) {
                if (f.isDirectory()) continue;
                File dest = new File(backupRoot, safeRelativePath(f));
                if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
                copyFile(f, dest);
                gui.show("Backed up: " + f.getAbsolutePath() + " -> " + dest.getAbsolutePath());
            }
        } else {
            File dest = new File(backupRoot, safeRelativePath(fileOrDir));
            if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
            copyFile(fileOrDir, dest);
            gui.show("Backed up: " + fileOrDir.getAbsolutePath() + " -> " + dest.getAbsolutePath());
        }
    }

    /**
     * Create a safe relative path for backup (replace leading separators).
     */
    private static String safeRelativePath(File f) throws IOException {
        String abs = f.getAbsolutePath();
        // Replace drive letters and separators to create a filesystem-safe relative path
        String safe = abs.replace(":", "").replace("\\", "/");
        // Remove any leading slashes
        while (safe.startsWith("/")) safe = safe.substring(1);
        return safe;
    }

    // Fixed resource leaks by using try-with-resources
    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    // --- Download & unzip helpers ---

    public static void downloadAndExtractZip(String urlStr, String destDir, boolean overwrite, GuiUpdater gui) throws Exception {
        File tmp = File.createTempFile("modupdater_cfg", ".zip");
        Downloader.downloadWithRetries(urlStr, tmp.getAbsolutePath(), gui); // use Downloader helper
        unzip(tmp, new File(destDir), overwrite, gui);
        tmp.delete();
    }

    // Fixed resource leaks by using try-with-resources
    public static void unzip(File zipFile, File destDir, boolean overwrite, GuiUpdater gui) throws Exception {
        // Use try-with-resources for ZipInputStream
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.exists()) out.mkdirs();
                    continue;
                }
                if (!out.getParentFile().exists()) out.getParentFile().mkdirs();

                // --- STRICTLY NEVER OVERWRITE ---
                if (out.exists()) {
                    // If the file already exists, we skip it entirely to prevent overwriting.
                    gui.show("Skipped existing file (will not overwrite): " + out.getAbsolutePath());
                    continue;
                }
                // --- END STRICTLY NEVER OVERWRITE ---

                // Write the file (only if it doesn't exist)
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) != -1) fos.write(buf, 0, len);
                } finally {
                    if (fos != null) try { fos.close(); } catch (Exception ignored) {}
                }
                gui.show("Extracted: " + out.getAbsolutePath());
            }
        }
    }

    public static void downloadFile(String urlStr, String dest, boolean extract, boolean overwrite, GuiUpdater gui) throws Exception {
        Downloader.downloadWithRetries(urlStr, dest, gui);
        if (extract && dest.toLowerCase().endsWith(".zip")) {
            unzip(new File(dest), new File(new File(dest).getParent()), overwrite, gui);
        }
    }

    // --- Version read/write ---

    // Fixed resource leaks by using try-with-resources
    public static void writeVersion(String path, String version) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            File out = new File(path);
            if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
            fos.write(version.getBytes("UTF-8"));
        }
    }

    public static String readVersion(String path) {
        try {
            return readFileToString(path).trim();
        } catch (Exception e) {
            return "0.0";
        }
    }
}