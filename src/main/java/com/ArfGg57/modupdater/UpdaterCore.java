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
            if (latestConfig.has("mods_json_url")) {
                JSONArray modsArr = FileUtils.readJsonArrayFromUrl(latestConfig.getString("mods_json_url"));
                modsTasks = modsArr.length();
            }

            int configTasks = latestConfig.has("configs_zip_url") && latestConfig.getString("configs_zip_url").length() > 0 ? 1 : 0;
            int filesTasks = 0;
            if (latestConfig.has("files")) {
                filesTasks = latestConfig.getJSONArray("files").length();
            }

            final int totalTasks = deleteTasks + modsTasks + configTasks + filesTasks;
            int completedTasks = 0;

            // 3. Multi-version delete
            if (latestConfig.has("delete_history_urls")) {
                JSONArray deleteUrls = latestConfig.getJSONArray("delete_history_urls");
                for (int i = 0; i < deleteUrls.length(); i++) {
                    String url = deleteUrls.getString(i);
                    gui.show("Fetching delete list: " + url);
                    JSONArray deleteList = FileUtils.readJsonArrayFromUrl(url);
                    FileUtils.deleteFiles(deleteList, latestConfig.optBoolean("backup_before_delete", true), gui);
                    completedTasks++;
                    updateProgress(completedTasks, totalTasks);
                }
            }

            // 4. Download mods
            if (latestConfig.has("mods_json_url")) {
                String modsUrl = latestConfig.getString("mods_json_url");
                gui.show("Fetching mods list...");
                JSONArray mods = FileUtils.readJsonArrayFromUrl(modsUrl);
                // Downloader will update GUI overall progress per-mod; to integrate with totalTasks,
                // we allow Downloader to handle per-mod progress and also bump completedTasks as each mod finishes.
                Downloader.downloadModsWithCallback(mods, gui, new Downloader.ProgressCallback() {
                    @Override
                    public void onItemComplete() {
                        // This callback will be invoked by Downloader when each mod finishes.
                        // We increment the completedTasks from here via a synchronized approach below.
                    }
                });
                // After downloadMods returns, we assume modsTasks completed (Downloader sets GUI overall percent)
                completedTasks += modsTasks;
                updateProgress(completedTasks, totalTasks);
            }

            // 5. Download and extract configs
            if (latestConfig.has("configs_zip_url") && latestConfig.getString("configs_zip_url").length() > 0) {
                String cfgUrl = latestConfig.getString("configs_zip_url");
                gui.show("Downloading configs...");
                FileUtils.downloadAndExtractZip(cfgUrl, "config/", true, gui);
                completedTasks++;
                updateProgress(completedTasks, totalTasks);
            }

            // 6. Extra files (optional)
            if (latestConfig.has("files")) {
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
                    completedTasks++;
                    updateProgress(completedTasks, totalTasks);
                }
            }

            // 7. Update local version
            FileUtils.writeVersion(localVersionPath, remoteVersion);
            gui.setProgress(100);
            gui.show("Update complete!");

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateProgress(int completed, int total) {
        if (total <= 0) {
            gui.setProgress(100);
            return;
        }
        int percent = (int) ((completed * 100L) / total);
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        gui.setProgress(percent);
    }
}
