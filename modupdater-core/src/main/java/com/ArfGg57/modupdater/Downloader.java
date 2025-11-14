package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Downloader {

    private static final int THREAD_COUNT = 3;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_RETRIES = 3;

    // We are relying on the community proxy to handle the API key securely.
    private static final String CF_API_KEY = null; // No key needed for the proxy endpoint

    // *** The base URL for the CurseForge API proxy. This is where you would update the URL. ***
    private static final String CF_PROXY_BASE_URL = "https://api.curse.tools/v1/cf";

    public static interface ProgressCallback {
        void onItemComplete();
    }

    public static void downloadMods(final JSONArray mods, final GuiUpdater gui) {
        downloadModsWithCallback(mods, gui, null);
    }

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
                        JSONObject cf = mod.getJSONObject("curseforge");
                        int addonId = cf.getInt("addonId");
                        long fileId = cf.getLong("fileId");

                        // Constructing the full URL using the new constant
                        String apiUrl = String.format(CF_PROXY_BASE_URL + "/mods/%s/files/%s", addonId, fileId);

                        try {
                            // CF_API_KEY is null, relying on the proxy to handle authentication
                            JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, CF_API_KEY);

                            // The JSON structure from this proxy is often slightly different
                            // from the official API, typically nested under "data".
                            JSONObject data = fileData.optJSONObject("data");
                            if (data == null) {
                                // If "data" isn't found, try treating the whole response as the data object
                                data = fileData;
                            }

                            String downloadUrl = data.getString("downloadUrl");
                            String fileName = data.getString("fileName");

                            if (downloadUrl == null || "null".equals(downloadUrl) || fileName == null) {
                                throw new IOException("Proxy returned incomplete data for ID " + fileId);
                            }

                            gui.show("[" + (index + 1) + "/" + mods.length() + "] Downloading CurseForge mod: " + fileName);
                            // CurseForge download links are often direct HTTP redirects
                            downloadWithRetries(downloadUrl, "mods/" + fileName, gui);

                        } catch (Exception e) {
                            gui.show("Failed to download CurseForge mod (via proxy): " + e.getMessage());
                            e.printStackTrace();
                        }

                    } else if ("modrinth".equals(source)) {
                        JSONObject mr = mod.getJSONObject("modrinth");
                        String versionId = mr.getString("versionId");

                        String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                        try {
                            JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                            JSONArray filesArr = versionData.getJSONArray("files");
                            if (filesArr.length() > 0) {
                                JSONObject fileObj = filesArr.getJSONObject(0);
                                String downloadUrl = fileObj.getString("url");
                                String fileName = fileObj.getString("filename");
                                gui.show("[" + (index + 1) + "/" + mods.length() + "] Downloading Modrinth mod: " + fileName);
                                downloadWithRetries(downloadUrl, "mods/" + fileName, gui);
                            } else {
                                gui.show("Modrinth version has no files: " + versionId);
                            }
                        } catch (Exception e) {
                            gui.show("Failed to download Modrinth mod: " + e.getMessage());
                            e.printStackTrace();
                        }

                    } else {
                        gui.show("Unknown source type: " + source);
                    }

                } catch (Exception e) {
                    gui.show("Error downloading mod index " + index + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    synchronized (completed) {
                        completed[0]++;
                        int overallPercent = (int)((completed[0] * 100L) / mods.length());
                        gui.setProgress(overallPercent);
                    }
                    if (callback != null) {
                        try { callback.onItemComplete(); } catch (Throwable ignored) {}
                    }
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }
    }

    // --- Helper download methods (omitted for brevity, assume contents are same as previous FileUtils file) ---

    public static String extractFileName(String urlStr) {
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
                gui.show("Retry " + attempt + "/" + MAX_RETRIES + " for " + destination);
                try { Thread.sleep(1000 * attempt); } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException("Failed to download after retries: " + urlStr, lastEx);
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
            if (!f.exists()) throw new FileNotFoundException("Local file not found");
            in = new FileInputStream(f);
            out = new FileOutputStream(outFile);
            copyStreamWithProgress(in, out, (int) f.length(), gui);
        } else if ("http".equals(protocol) || "https".equals(protocol)) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "ModUpdater/1.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code);
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
    }

    private static void copyStreamWithProgress(InputStream in, OutputStream out, int totalBytes, GuiUpdater gui) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}

