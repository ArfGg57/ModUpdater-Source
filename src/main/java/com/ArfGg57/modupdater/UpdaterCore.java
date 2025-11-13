package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class UpdaterCore {

    // New configuration paths
    private final String localConfigDir = "config/ModUpdater/";
    private final String localConfigPath = localConfigDir + "modupdater.json";
    private final String localVersionPath = localConfigDir + "modpack_version.json";

    private GuiUpdater gui;
    // Flag to prevent GUI from closing on a configuration error crash
    private boolean configError = false;

    public void runUpdate() {
        gui = new GuiUpdater();
        gui.show("Initializing ModUpdater...");

        try {
            // 1. Ensure config files and directory are present
            ensureConfigFilesExist();

            // 2. Read local config (now modupdater.json)
            JSONObject localConfig = FileUtils.readJson(localConfigPath);
            // Use optString with an empty string default to catch if the key is missing or null
            String remoteConfigUrl = localConfig.optString("remote_config_url", "");

            // 3. Read local version (now modpack_version.json)
            String localVersion = FileUtils.readVersion(localVersionPath);

            // --- Configuration Validation ---
            String urlPlaceholder = "";
            String versionPlaceholder = "";

            // Check if values are the empty string (""), which is the default placeholder value
            if (remoteConfigUrl.isEmpty()) {
                urlPlaceholder = "Please enter GitHub repo URL in " + localConfigPath + ". ";
                configError = true;
            }
            if (localVersion.isEmpty() || localVersion.equals("0.0")) {
                // Checks for empty string (default) or "0.0" (default if file was missing/corrupted)
                versionPlaceholder = "Please enter current modpack version in " + localVersionPath + ". ";
                configError = true;
            }

            if (configError) {
                String fullMessage = urlPlaceholder + versionPlaceholder + "Mod loading halted until configuration is complete.";
                gui.show("CONFIG ERROR: " + fullMessage);
                // Throw an exception to crash FML loading, keeping the GUI open because configError is true
                throw new RuntimeException("ModUpdater configuration is incomplete. See GUI for details.");
            }
            // --- End Configuration Validation ---

            // 4. Download latest config from remote
            gui.show("Checking for updates...");
            JSONObject latestConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            String remoteVersion = latestConfig.optString("version", "0.0");

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

            // 5. Multi-version delete
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

            // 6. Download mods
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

            // 7. Download and extract configs
            if (configTasks > 0) {
                String cfgUrl = latestConfig.getString("configs_zip_url");
                gui.show("Downloading configs...");
                FileUtils.downloadAndExtractZip(cfgUrl, "config/", true, gui);
                completedTasks[0]++;
                updateProgress(completedTasks[0], totalTasks);
            }

            // 8. Extra files (optional)
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

            // 9. Update local version
            FileUtils.writeVersion(localVersionPath, remoteVersion);
            gui.setProgress(100);
            gui.show("Update complete! Closing GUI now.");

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // This is critical: only close the GUI if no configuration error was encountered
            if (gui != null && !configError) {
                gui.close();
            }
            // If configError is true, the GUI stays open, and the RuntimeException thrown above crashes the game.
        }
    }

    /**
     * Ensures the config directory and essential files exist with default content if missing.
     * The files are initialized to contain a simple empty string, which triggers the validation check.
     */
    private void ensureConfigFilesExist() throws IOException {
        // modupdater.json default content: a JSON object with an empty URL
        String defaultUpdaterJson = "{\"remote_config_url\":\"\"}";
        FileUtils.ensureFileAndDirectoryExist(localConfigPath, defaultUpdaterJson);

        // modpack_version.json default content: a simple quoted empty string
        String defaultVersionJson = "\"\"";
        FileUtils.ensureFileAndDirectoryExist(localVersionPath, defaultVersionJson);
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