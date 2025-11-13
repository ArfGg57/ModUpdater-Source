package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

public class UpdaterCore {

    private final String localConfigPath = "config/modupdater_config.json";
    private final String localVersionPath = "config/modpack_version.txt";
    private GuiUpdater gui;

    public void runUpdate() {
        gui = new GuiUpdater();
        gui.show("Checking for updates...");

        try {
            // 1. Read local config
            JSONObject localConfig = FileUtils.readJson(localConfigPath);
            String remoteConfigUrl = localConfig.getString("remote_config_url");

            // 2. Download latest config from remote
            JSONObject latestConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            String remoteVersion = latestConfig.optString("version", "0.0");
            String localVersion = FileUtils.readVersion(localVersionPath);

            if (remoteVersion.equals(localVersion)) {
                gui.show("Modpack is up-to-date.");
                gui.setProgress(100);
                return;
            }

            gui.show("Updating from " + localVersion + " to " + remoteVersion);

            // Build task counts for progress (delete entries + mods + configs + files)
            int deleteTasks = 0;
            if (latestConfig.has("delete_history_urls")) {
                JSONArray deleteUrls = latestConfig.getJSONArray("delete_history_urls");
                deleteTasks = deleteUrls.length();
            }

            int modsTasks = 0;
            JSONArray mods = new JSONArray(); // Initialize empty array
            if (latestConfig.has("mods_json_url")) {
                gui.show("Fetching mods list...");
                mods = FileUtils.readJsonArrayFromUrl(latestConfig.getString("mods_json_url"));
                modsTasks = mods.length();
            }

            int configTasks = latestConfig.has("configs_zip_url") && latestConfig.getString("configs_zip_url").length() > 0 ? 1 : 0;
            int filesTasks = 0;
            if (latestConfig.has("files")) {
                filesTasks = latestConfig.getJSONArray("files").length();
            }

            final int totalTasks = deleteTasks + modsTasks + configTasks + filesTasks;
            // Use a final array wrapper so the value can be safely modified inside the inner class/callback.
            final int[] completedTasks = new int[] { 0 };

            // 3. Multi-version delete
            if (latestConfig.has("delete_history_urls")) {
                JSONArray deleteUrls = latestConfig.getJSONArray("delete_history_urls");
                for (int i = 0; i < deleteUrls.length(); i++) {
                    String url = deleteUrls.getString(i);
                    gui.show("Fetching delete list: " + url);
                    JSONArray deleteList = FileUtils.readJsonArrayFromUrl(url);
                    FileUtils.deleteFiles(deleteList, latestConfig.optBoolean("backup_before_delete", true), gui);
                    completedTasks[0]++;
                    updateProgress(completedTasks[0], totalTasks);
                }
            }

            // 4. Download mods
            if (modsTasks > 0) {
                // Downloader will update GUI overall progress per-mod and use the callback
                // to update the UpdaterCore's global progress count.
                Downloader.downloadModsWithCallback(mods, gui, new Downloader.ProgressCallback() {
                    @Override
                    // Use synchronized to ensure thread-safe updates to the completedTasks counter
                    public synchronized void onItemComplete() {
                        completedTasks[0]++;
                        updateProgress(completedTasks[0], totalTasks);
                    }
                });
                // When downloadModsWithCallback returns, ALL mods are done.
            }

            // 5. Download and extract configs
            if (configTasks > 0) {
                String cfgUrl = latestConfig.getString("configs_zip_url");
                gui.show("Downloading configs...");
                FileUtils.downloadAndExtractZip(cfgUrl, "config/", true, gui);
                completedTasks[0]++;
                updateProgress(completedTasks[0], totalTasks);
            }

            // 6. Extra files (optional)
            if (filesTasks > 0) {
                JSONArray files = latestConfig.getJSONArray("files");
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);
                    FileUtils.downloadFile(
                            file.getString("url"),
                            file.getString("destination"),
                            file.optBoolean("extract", false),
                            file.optBoolean("overwrite", true),
                            gui
                    );
                    completedTasks[0]++;
                    updateProgress(completedTasks[0], totalTasks);
                }
            }

            // 7. Update local version
            FileUtils.writeVersion(localVersionPath, remoteVersion);
            gui.setProgress(100);
            gui.show("Update complete! Closing GUI now."); // Changed message

            // --- Removed 3-second wait ---

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // This is critical: ensure the GUI closes immediately after the try block finishes
            if (gui != null) {
                gui.close();
            }
        }
    }

    private void updateProgress(int completed, int total) {
        if (total <= 0) {
            gui.setProgress(100);
            return;
        }
        // Ensure percent calculation is done using long to prevent overflow
        int percent = (int) ((completed * 100L) / total);
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        gui.setProgress(percent);
    }
}