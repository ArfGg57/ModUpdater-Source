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

    public static interface ProgressCallback {
        void onItemComplete();
    }

    // Existing downloadModsWithCallback left unchanged (you can keep previous version)
    // ...

    // New: generic downloadToFile used by FileUtils.downloadToFile
    public static void downloadToFile(String urlStr, File dest, GuiUpdater gui) throws Exception {
        // simple single-attempt download; caller handles retries
        URL url = new URL(urlStr);
        String protocol = url.getProtocol();

        if (dest.getParentFile() != null) dest.getParentFile().mkdirs();

        InputStream in = null;
        OutputStream out = null;

        try {
            if ("file".equals(protocol)) {
                File f = new File(url.toURI());
                if (!f.exists()) throw new FileNotFoundException("Local file not found");
                in = new FileInputStream(f);
                out = new FileOutputStream(dest);
                copyStream(in, out);
            } else if ("http".equals(protocol) || "https".equals(protocol)) {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "ModUpdater/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);

                in = conn.getInputStream();
                out = new FileOutputStream(dest);
                copyStream(in, out);
                conn.disconnect();
            } else {
                URLConnection uc = url.openConnection();
                in = uc.getInputStream();
                out = new FileOutputStream(dest);
                copyStream(in, out);
            }
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    // Keep your existing extractFileName and downloadWithRetries helpers if you like.
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

    // Keep previous downloadWithRetries for compatibility
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
            copyStream(in, out);
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

            in = conn.getInputStream();
            out = new FileOutputStream(outFile);
            copyStream(in, out);
            conn.disconnect();
        } else {
            URLConnection uc = url.openConnection();
            in = uc.getInputStream();
            out = new FileOutputStream(outFile);
            copyStream(in, out);
        }

        if (in != null) try { in.close(); } catch (Exception ignored) {}
        if (out != null) try { out.close(); } catch (Exception ignored) {}
    }
}
