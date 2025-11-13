package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
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

    // --- JSON utilities ---
    public static JSONObject readJson(String path) throws Exception {
        String s = readFileToString(path);
        return new JSONObject(s);
    }

    public static JSONObject readJsonFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr);
        return new JSONObject(s);
    }

    public static JSONArray readJsonArrayFromUrl(String urlStr) throws Exception {
        String s = readUrlToString(urlStr);
        return new JSONArray(s);
    }

    private static String readFileToString(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("File not found: " + f.getAbsolutePath());
        FileInputStream fis = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            fis = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), "UTF-8");
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }

    private static String readUrlToString(String urlStr) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            URL url = new URL(urlStr);
            in = url.openStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), "UTF-8");
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    // --- Delete / backup ---
    public static void deleteFiles(JSONArray paths, boolean backup, GuiUpdater gui) throws Exception {
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

    private static void copyFile(File src, File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    // --- Download & unzip helpers ---

    public static void downloadAndExtractZip(String urlStr, String destDir, boolean overwrite, GuiUpdater gui) throws Exception {
        File tmp = File.createTempFile("modupdater_cfg", ".zip");
        Downloader.downloadWithRetries(urlStr, tmp.getAbsolutePath(), gui); // use Downloader helper
        unzip(tmp, new File(destDir), overwrite, gui);
        tmp.delete();
    }

    public static void unzip(File zipFile, File destDir, boolean overwrite, GuiUpdater gui) throws Exception {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.exists()) out.mkdirs();
                    continue;
                }
                if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
                if (!out.exists() || overwrite) {
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
        } finally {
            if (zis != null) try { zis.close(); } catch (Exception ignored) {}
        }
    }

    public static void downloadFile(String urlStr, String dest, boolean extract, boolean overwrite, GuiUpdater gui) throws Exception {
        Downloader.downloadWithRetries(urlStr, dest, gui);
        if (extract && dest.toLowerCase().endsWith(".zip")) {
            unzip(new File(dest), new File(new File(dest).getParent()), overwrite, gui);
        }
    }

    // --- Version read/write ---

    public static void writeVersion(String path, String version) throws Exception {
        FileOutputStream fos = null;
        try {
            File out = new File(path);
            if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
            fos = new FileOutputStream(out);
            fos.write(version.getBytes("UTF-8"));
        } finally {
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
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
