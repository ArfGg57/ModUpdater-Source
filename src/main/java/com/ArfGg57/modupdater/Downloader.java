package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloader with retries, file:// support, progress reporting and a callback hook.
 */
public class Downloader {

    private static final int THREAD_COUNT = 3;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_RETRIES = 3;

    /** Optional callback for each completed item */
    public static interface ProgressCallback {
        void onItemComplete();
    }

    /**
     * Download mods and update GUI progress per-item.
     * Simpler: does not call back to UpdaterCore.
     */
    public static void downloadMods(final JSONArray mods, final GuiUpdater gui) {
        downloadModsWithCallback(mods, gui, null);
    }

    /**
     * Download mods and call callback.onItemComplete() after each finished mod (success or fail).
     */
    public static void downloadModsWithCallback(final JSONArray mods, final GuiUpdater gui, final ProgressCallback callback) {
        if (mods == null) {
            gui.show("No mods to download (mods == null).");
            gui.setProgress(100);
            return;
        }

        final int total = mods.length();
        if (total == 0) {
            gui.show("No mods listed to download.");
            gui.setProgress(100);
            return;
        }

        gui.show("Starting download of " + total + " mods.");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final int[] completed = new int[] {0};

        for (int i = 0; i < total; i++) {
            final JSONObject mod = mods.getJSONObject(i);
            final int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        String source = mod.optString("source", "url");
                        if ("url".equals(source)) {
                            String url = mod.getString("url");
                            String fileName = extractFileName(url);
                            gui.show("[" + (index+1) + "/" + total + "] Downloading " + fileName);
                            downloadWithRetries(url, "mods/" + fileName, gui);
                        } else if ("curseforge".equals(source)) {
                            JSONObject cf = mod.getJSONObject("curseforge");
                            gui.show("[" + (index+1) + "/" + total + "] (CurseForge placeholder) addon " + cf.optInt("addonId"));
                            // TODO: implement CurseForge logic
                        } else if ("modrinth".equals(source)) {
                            JSONObject mr = mod.getJSONObject("modrinth");
                            gui.show("[" + (index+1) + "/" + total + "] (Modrinth placeholder) addon " + mr.optString("addonId"));
                            // TODO: implement Modrinth logic
                        } else {
                            gui.show("[" + (index+1) + "/" + total + "] Unknown source: " + source);
                        }
                    } catch (Exception e) {
                        gui.show("Error downloading mod index " + index + ": " + e.getClass().getName() + " - " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        synchronized (completed) {
                            completed[0] = completed[0] + 1;
                            int overallPercent = (int) ((completed[0] * 100L) / total);
                            gui.setProgress(overallPercent);
                            gui.show("Overall mods progress: " + overallPercent + "% (" + completed[0] + "/" + total + ")");
                        }
                        if (callback != null) {
                            try {
                                callback.onItemComplete();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }

        gui.setProgress(100);
        gui.show("All mods processed.");
    }

    // --- Helper download methods with retries and protocol support ---

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

    /**
     * Made public so other helpers (FileUtils) can use it.
     */
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
                e.printStackTrace();
                try { Thread.sleep(1000 * attempt); } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException("Failed to download after " + MAX_RETRIES + " attempts: " + urlStr, lastEx);
    }

    private static void downloadFileOnce(String urlStr, String destination, GuiUpdater gui) throws Exception {
        URL url = new URL(urlStr);
        String protocol = url.getProtocol();

        File outFile = new File(destination);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

        InputStream in = null;
        OutputStream out = null;

        if ("file".equals(protocol)) {
            File f = new File(url.toURI());
            if (!f.exists()) throw new FileNotFoundException("Local file not found: " + f.getAbsolutePath());
            in = new FileInputStream(f);
            out = new FileOutputStream(outFile);
            copyStreamWithProgress(in, out, (int) f.length(), gui);
        } else if ("http".equals(protocol) || "https".equals(protocol)) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "ModUpdater/1.7.10");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code + " for " + urlStr);
            }

            int contentLength = conn.getContentLength();
            in = conn.getInputStream();
            out = new FileOutputStream(outFile);
            copyStreamWithProgress(in, out, contentLength, gui);
            conn.disconnect();
        } else {
            URLConnection uc = url.openConnection();
            in = uc.getInputStream();
            out = new FileOutputStream(outFile);
            copyStreamWithProgress(in, out, uc.getContentLength(), gui);
        }

        if (in != null) try { in.close(); } catch (Exception ignored) {}
        if (out != null) try { out.close(); } catch (Exception ignored) {}

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
