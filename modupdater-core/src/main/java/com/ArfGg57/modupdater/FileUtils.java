package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets; // Recommended for consistent encoding
import java.nio.file.Files; // For java.nio.file.Files.move

/**
 * File utilities: JSON/network, backup, atomic move, unzip, simple version compare.
 */
public class FileUtils {

    public static final String CF_PROXY_BASE_URL = "https://api.curse.tools/v1/cf";
    public static final String CF_API_KEY = null;

    private static final String API_USER_AGENT = "ModUpdater/1.7.10-ArfGg57";
    private static final int API_TIMEOUT = 10000;

    // --- JSON helpers ---
    public static JSONObject readJson(String path) throws Exception {
        String s = readFileToString(path);
        return new JSONObject(new JSONTokener(s));
    }

    public static JSONObject readJsonFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr, null);
        return new JSONObject(new JSONTokener(s));
    }

    public static JSONObject readJsonFromUrl(String urlStr, String apiKey) throws Exception {
        String s = readUrlToString(urlStr, apiKey);
        return new JSONObject(new JSONTokener(s));
    }

    public static JSONArray readJsonArrayFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr, null);
        return new JSONArray(new JSONTokener(s));
    }

    private static String readFileToString(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("File not found: " + f.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String readUrlToString(String urlStr, String apiKey) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", API_USER_AGENT);
            connection.setConnectTimeout(API_TIMEOUT);
            connection.setReadTimeout(API_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            if (apiKey != null && !apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 400) {
                String err = readStream(connection.getErrorStream());
                throw new IOException("HTTP Error " + responseCode + " for " + urlStr + ". Details: " + err);
            }

            return readStream(connection.getInputStream());
        } catch (IOException e) {
            throw new IOException("Failed to connect to " + urlStr + ". " + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // --- version helpers (simple semver-ish compare) ---
    public static int compareVersions(String a, String b) {
        if (a == null) a = "0.0.0";
        if (b == null) b = "0.0.0";
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = (i < as.length) ? safeParseInt(as[i]) : 0;
            int bi = (i < bs.length) ? safeParseInt(bs[i]) : 0;
            if (ai < bi) return -1;
            if (ai > bi) return 1;
        }
        return 0;
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*", "")); } catch (Exception e) { return 0; }
    }

    // --- ensure file/dir exists ---
    public static void ensureFileAndDirectoryExist(String path, String defaultContent) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Failed to create dir: " + parent.getAbsolutePath());
        if (!file.exists()) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(defaultContent.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // --- applied version read/write (simple JSON string) ---
    public static String readAppliedVersion(String path) {
        try {
            String s = readFileToString(path).trim();
            if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
            return s;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    public static void writeAppliedVersion(String path, String version) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            String json = "\"" + version + "\"";
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    // --- download with verification (basic) ---
    /**
     * NOTE: This function keeps the same signature for compatibility but no longer uses hash.
     * It will attempt to download the file and return true on success. The `expectedHash`
     * parameter is ignored (kept for compatibility).
     */
    public static boolean downloadWithVerification(String urlStr, File tmpDest, String expectedHash, GuiUpdater gui, int maxRetries) {
        int attempt = 0;
        Exception last = null;
        while (attempt < maxRetries) {
            attempt++;
            InputStream in = null;
            OutputStream out = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                String protocol = url.getProtocol();

                if (tmpDest.getParentFile() != null && !tmpDest.getParentFile().exists()) tmpDest.getParentFile().mkdirs();

                if ("file".equals(protocol)) {
                    File f = new File(url.toURI());
                    if (!f.exists()) throw new FileNotFoundException("Local file not found: " + f.getAbsolutePath());
                    try (InputStream fis = new FileInputStream(f);
                         OutputStream fos = new FileOutputStream(tmpDest)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                } else {
                    // http(s) and generic URLConnection
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", API_USER_AGENT);
                    conn.setConnectTimeout(API_TIMEOUT);
                    conn.setReadTimeout(API_TIMEOUT);
                    conn.setInstanceFollowRedirects(true);

                    int code = conn.getResponseCode();
                    if (code >= 400) {
                        String err = readStream(conn.getErrorStream());
                        throw new IOException("HTTP " + code + " - " + err);
                    }

                    in = conn.getInputStream();
                    out = new FileOutputStream(tmpDest);
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                    out.flush();
                }

                // Previously: hash verification. Now omitted intentionally.
                return true;
            } catch (Exception e) {
                last = e;
                if (gui != null) gui.show("Download attempt " + attempt + " failed: " + e.getMessage());
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                if (tmpDest.exists()) tmpDest.delete();
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }
        if (last != null && gui != null) gui.show("Download failed after retries: " + last.getMessage());
        return false;
    }


    // --- atomic move with retries and fallback ---
    public static void atomicMoveWithRetries(File src, File dst, int maxRetries, long retryDelayMs) throws IOException {
        if (src == null || dst == null) throw new IllegalArgumentException("src/dst null");
        if (!src.exists()) throw new FileNotFoundException("Source not found: " + src.getAbsolutePath());

        IOException lastEx = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                File parent = dst.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                // fastest: simple rename
                if (src.renameTo(dst)) return;

                // try NIO atomic move
                try {
                    java.nio.file.Path srcP = src.toPath();
                    java.nio.file.Path dstP = dst.toPath();
                    Files.move(srcP, dstP, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    return;
                } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                    // fall back
                } catch (Throwable t) {
                    lastEx = new IOException("NIO move failed: " + t.getMessage(), t);
                }

                // fallback: copy and delete
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                if (!src.delete()) {
                    throw new IOException("Copied but failed to delete source: " + src.getAbsolutePath());
                }
                return;
            } catch (IOException e) {
                lastEx = e;
                try { Thread.sleep(retryDelayMs); } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException("Failed to move file after retries: " + src.getAbsolutePath() + " -> " + dst.getAbsolutePath(), lastEx);
    }

    // --- backup & restore helpers (simple copy preserving relative path) ---
    public static void backupPathTo(File src, File backupRoot) {
        try {
            if (!backupRoot.exists()) backupRoot.mkdirs();
            String rel = safeRelativePath(src);
            File dst = new File(backupRoot, rel);
            if (dst.getParentFile() != null && !dst.getParentFile().exists()) dst.getParentFile().mkdirs();
            copyFile(src, dst);
        } catch (Exception e) {
            // best-effort
        }
    }

    public static void deleteSilently(File f, GuiUpdater gui) {
        if (f == null) return;
        try {
            if (!f.exists()) {
                if (gui != null) gui.show("Delete skip (not found): " + f.getPath());
                return;
            }

            // If directory, delete recursively
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) {
                        deleteSilently(c, gui);
                    }
                }
                boolean ok = f.delete();
                if (gui != null) gui.show("Deleted directory: " + f.getAbsolutePath() + (ok ? "" : " (delete returned false)"));
            } else {
                boolean ok = f.delete();
                if (gui != null) gui.show("Deleted: " + f.getAbsolutePath() + (ok ? "" : " (delete returned false)"));
            }
        } catch (Exception e) {
            if (gui != null) gui.show("Error deleting " + f.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    public static void pruneBackups(String backupBasePath, int keep, GuiUpdater gui) {
        try {
            File base = new File(backupBasePath);
            if (!base.exists()) return;
            File[] children = base.listFiles(File::isDirectory);
            if (children == null || children.length <= keep) return;
            List<File> list = new ArrayList<>(Arrays.asList(children));
            Collections.sort(list, (a,b)-> b.getName().compareTo(a.getName())); // newest first
            for (int i = keep; i < list.size(); i++) {
                deleteRecursively(list.get(i));
                if (gui != null) gui.show("Pruned old backup: " + list.get(i).getPath());
            }
        } catch (Exception ignored) {}
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File ch : c) deleteRecursively(ch);
        }
        f.delete();
    }

    // --- utilities ---
    public static boolean hashEquals(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static void copyFile(File src, File dst) throws IOException {
        if (src.isDirectory()) return;
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    private static String safeRelativePath(File f) {
        String abs = f.getAbsolutePath().replace(':', '_').replace('\\', '/').replaceAll("^/+", "");
        return abs;
    }

    public static String extractFileNameFromUrl(String urlStr) {
        try {
            URL u = new URL(urlStr);
            String path = u.getPath();
            int idx = path.lastIndexOf('/');
            if (idx >= 0 && idx < path.length() - 1) return path.substring(idx + 1);
            return path;
        } catch (Exception e) {
            int idx = urlStr.lastIndexOf('/');
            if (idx >= 0 && idx < urlStr.length() - 1) return urlStr.substring(idx + 1);
            return urlStr;
        }
    }

    public static List<File> findFilesForNumberId(File dir, String numberId) {
        List<File> out = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File f : children) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.endsWith(".tmp")) continue; // skip tmp files
            if (numberId != null && !numberId.isEmpty()) {
                if (name.startsWith(numberId + "-")) out.add(f);
            } else {
                // if no numberId, match exact or anything
                out.add(f);
            }
        }
        return out;
    }

    public static String joinUrl(String base, String name) {
        if (base == null) base = "";
        if (!base.endsWith("/")) base = base + "/";
        return base + name;
    }

    // --- unzip (keeps behavior: do not overwrite) ---
    public static void unzip(File zipFile, File destDir, boolean overwrite, GuiUpdater gui) throws Exception {
        String destDirCanonical = destDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                // Skip empty entries
                if (entry.getName().trim().isEmpty()) {
                    if (gui != null) gui.show("Skipped empty zip entry");
                    continue;
                }

                File out = new File(destDir, entry.getName());
                String outCanonical = out.getCanonicalPath();

                // Zip-slip protection
                if (!outCanonical.startsWith(destDirCanonical + File.separator) && !outCanonical.equals(destDirCanonical)) {
                    if (gui != null) gui.show("Skipped suspicious zip entry (possible zip-slip): " + entry.getName());
                    continue;
                }

                // Create directories
                if (entry.isDirectory()) {
                    if (!out.exists()) out.mkdirs();
                    continue;
                }

                if (!out.getParentFile().exists()) out.getParentFile().mkdirs();

                // Skip existing file if not overwriting
                if (out.exists() && !overwrite) {
                    if (gui != null) gui.show("Skipped existing file (will not overwrite): " + out.getAbsolutePath());
                    continue;
                }

                // Extract file safely
                try (FileOutputStream fos = new FileOutputStream(out);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        bos.write(buf, 0, len);
                    }

                    if (gui != null) gui.show("Extracted: " + out.getAbsolutePath());
                } catch (Exception e) {
                    if (gui != null) gui.show("Failed to extract: " + out.getAbsolutePath() + " (" + e.getMessage() + ")");
                }
            }
        }
    } // <-- MISSING CLOSING BRACE for the unzip method was likely here

    // Helper: compare the suffix after the numberId- prefix of an existing file name
    // with an expected base filename. Returns true if they match (i.e., the installed file
    // is the expected version), false otherwise.
    public static boolean fileNameSuffixMatches(File existing, String numberId, String expectedBaseFileName) {
        if (existing == null || expectedBaseFileName == null) return false;
        String name = existing.getName();
        if (numberId != null && !numberId.isEmpty()) {
            String prefix = numberId + "-";
            if (!name.startsWith(prefix)) return false;
            String suffix = name.substring(prefix.length());
            return suffix.equals(expectedBaseFileName);
        } else {
            // no numberId: compare full filename with expected base
            return name.equals(expectedBaseFileName);
        }
    }

} // <-- FileUtils class end
