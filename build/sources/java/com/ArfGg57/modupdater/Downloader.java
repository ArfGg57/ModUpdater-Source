package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloader with retries, file:// support, progress reporting, and multi-platform API stubs.
 */
public class Downloader {

    private static final int THREAD_COUNT = 3;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_RETRIES = 3;
    private static final String USER_AGENT = "ModUpdater/1.7.10-ArfGg57";

    /** Optional callback for each completed item */
    public interface ProgressCallback {
        void onItemComplete();
    }

    /**
     * Download mods and update GUI progress per-item.
     */
    public static void downloadMods(final JSONArray mods, final GuiUpdater gui) {
        downloadModsWithCallback(mods, gui, null);
    }

    /**
     * Download mods and call callback.onItemComplete() after each finished mod.
     */
    public static void downloadModsWithCallback(final JSONArray mods, final GuiUpdater gui, final ProgressCallback callback) {
        if (mods == null || mods.length() == 0) {
            gui.show("No mods to download.");
            gui.setProgress(100);
            return;
        }

        gui.show("Starting download of " + mods.length() + " mods.");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final int[] completed = new int[] {0};

        for (int i = 0; i < mods.length(); i++) {
            final JSONObject mod = mods.getJSONObject(i);
            final int index = i;
            executor.submit(() -> {
                try {
                    String source = mod.optString("source", "url");

                    if ("url".equals(source)) {
                        String url = mod.getString("url");
                        String fileName = extractFileName(url);
                        gui.show("[" + (index + 1) + "/" + mods.length() + "] Downloading " + fileName);
                        downloadWithRetries(url, "mods/" + fileName, gui);

                    } else if ("curseforge".equals(source)) {
                        handleCurseForgeDownload(mod, index, mods.length(), gui);

                    } else if ("modrinth".equals(source)) {
                        handleModrinthDownload(mod, index, mods.length(), gui);

                    } else {
                        gui.show("[" + (index + 1) + "/" + mods.length() + "] Unknown source: " + source);
                    }

                } catch (Exception e) {
                    gui.show("Error downloading mod index " + index + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    synchronized (completed) {
                        completed[0]++;
                        int overallPercent = (int)((completed[0] * 100L) / mods.length());
                        gui.setProgress(overallPercent);
                        gui.show("Overall progress: " + overallPercent + "% (" + completed[0] + "/" + mods.length() + ")");
                    }
                    if (callback != null) {
                        try { callback.onItemComplete(); } catch (Throwable ignored) {}
                    }
                }
            });
        }

        executor.shutdown();
        // Wait up to 30 seconds for all tasks to complete, otherwise proceed.
        int timeoutSeconds = 30;
        long startTime = System.currentTimeMillis();
        while (!executor.isTerminated() && (System.currentTimeMillis() - startTime) < (timeoutSeconds * 1000L)) {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }

        gui.setProgress(100);
        gui.show("All mods processed.");
    }

    // --- API Handlers ---

    private static void handleCurseForgeDownload(JSONObject mod, int index, int total, GuiUpdater gui) throws Exception {
        JSONObject cf = mod.getJSONObject("curseforge");
        int addonId = cf.getInt("addonId");
        long fileId = cf.getLong("fileId");

        // NOTE: The CurseForge API endpoint for V2 is fragile and may require an API key
        // or fail to provide a stable download URL. This is the best public endpoint available.
        String apiUrl = "https://addons-ecs.forgesvc.net/api/v2/addon/" + addonId + "/files/" + fileId;

        try {
            // NOTE: FileUtils.readJsonFromUrl MUST use the User-Agent header for the API call to work reliably.
            JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl);
            String downloadUrl = fileData.getString("downloadUrl");
            String fileName = fileData.getString("fileName");

            gui.show("[" + (index + 1) + "/" + total + "] Downloading CurseForge mod: " + fileName);
            downloadWithRetries(downloadUrl, "mods/" + fileName, gui);

        } catch (Exception e) {
            String errorMsg = "Failed to download CurseForge mod: addon " + addonId + ", file " + fileId + " - " + e.getMessage();
            gui.show(errorMsg);
            throw new IOException(errorMsg, e); // Re-throw to be caught by the executor's outer catch
        }
    }

    private static void handleModrinthDownload(JSONObject mod, int index, int total, GuiUpdater gui) throws Exception {
        JSONObject mr = mod.getJSONObject("modrinth");
        String versionId = mr.getString("versionId");
        String projectId = mr.optString("projectId", "unknown");

        String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;

        try {
            JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
            JSONArray filesArr = versionData.getJSONArray("files");

            if (filesArr.length() > 0) {
                JSONObject fileObj = filesArr.getJSONObject(0); // Default to first file

                // 🛑 FIX: Iterate to find the primary file, which is usually the main one
                for (int j = 0; j < filesArr.length(); j++) {
                    JSONObject currentFile = filesArr.getJSONObject(j);
                    // Check for primary flag (safely defaults to false)
                    if (currentFile.optBoolean("primary", false)) {
                        fileObj = currentFile;
                        break;
                    }
                }

                String downloadUrl = fileObj.getString("url");
                String fileName = fileObj.getString("filename");

                gui.show("[" + (index + 1) + "/" + total + "] Downloading Modrinth mod: " + fileName);
                downloadWithRetries(downloadUrl, "mods/" + fileName, gui);
            } else {
                gui.show("Modrinth version has no files: " + versionId);
            }
        } catch (Exception e) {
            String errorMsg = "Failed to download Modrinth mod: project " + projectId + ", version " + versionId + " - " + e.getMessage();
            gui.show(errorMsg);
            throw new IOException(errorMsg, e); // Re-throw
        }
    }


    // --- Helper download methods ---

    private static String extractFileName(String urlStr) {
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

    public static void downloadWithRetries(String urlStr, String destination, GuiUpdater gui) throws Exception {
        int attempt = 0;
        Exception lastEx = null;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                downloadFileOnce(urlStr, destination, gui);
                return;
            } catch (Exception e) {
                lastEx = e;
                gui.show("Attempt " + attempt + " failed for " + urlStr + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                // Exponential backoff: sleep for 1s, 2s, 3s...
                try { Thread.sleep(1000 * attempt); } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException("Failed to download after " + MAX_RETRIES + " attempts: " + urlStr, lastEx);
    }

    private static void downloadFileOnce(String urlStr, String destination, GuiUpdater gui) throws Exception {
        URL url = new URL(urlStr);
        String protocol = url.getProtocol().toLowerCase();

        File outFile = new File(destination);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

        if ("file".equals(protocol)) {
            // Use try-with-resources for local file system streams
            File f = Paths.get(url.toURI()).toFile();
            if (!f.exists()) throw new FileNotFoundException("Local file not found: " + f.getAbsolutePath());

            try (InputStream in = new FileInputStream(f);
                 OutputStream out = new FileOutputStream(outFile)) {
                copyStreamWithProgress(in, out, (int) f.length(), gui);
            }
        } else if ("http".equals(protocol) || "https".equals(protocol)) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(15000); // 15 seconds
            conn.setReadTimeout(15000); // 15 seconds
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code + " for " + urlStr + " (Response: " + conn.getResponseMessage() + ")");
            }

            int contentLength = conn.getContentLength();

            // Use try-with-resources for network streams
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(outFile)) {
                copyStreamWithProgress(in, out, contentLength, gui);
            } finally {
                conn.disconnect();
            }
        } else {
            // Generic connection (e.g., FTP if supported by Java 7/8)
            URLConnection uc = url.openConnection();
            int contentLength = uc.getContentLength();

            try (InputStream in = uc.getInputStream();
                 OutputStream out = new FileOutputStream(outFile)) {
                copyStreamWithProgress(in, out, contentLength, gui);
            }
        }

        gui.show("Downloaded: " + destination);
    }

    private static void copyStreamWithProgress(InputStream in, OutputStream out, int totalBytes, GuiUpdater gui) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        long totalRead = 0;
        int lastPercent = -1;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            totalRead += read;
            if (totalBytes > 0) {
                int percent = (int) ((totalRead * 100L) / totalBytes);
                if (percent != lastPercent) {
                    gui.setProgress(percent);
                    lastPercent = percent;
                }
            }
        }
        if (totalBytes > 0) gui.setProgress(100);
    }
}