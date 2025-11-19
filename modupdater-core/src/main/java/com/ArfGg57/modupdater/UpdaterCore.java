package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.ModConfirmationDialog.ModEntry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

/**
 * UpdaterCore: downloads to a safe tmp folder, backs up existing files, deletes safely,
 * then atomically moves new files into place with retries. Commits version only on full success.
 * This version includes methods for pre-update confirmation lists (Mods, Deletes, and Files).
 *
 * NOTE: Hash-based verification removed per user request. Decision logic now uses filename comparison
 * for mods (compare installed filename after the id prefix to the expected filename). For files, we
 * rely on existence + overwrite/upgrade semantics.
 */
public class UpdaterCore {

    private final String localConfigDir = "config/ModUpdater/";
    private final String remoteConfigPath = localConfigDir + "config.json"; // holds remote_config_url
    private final String localVersionPath = localConfigDir + "modpack_version.json"; // holds applied version
    private List<ModConfirmationDialog.ModEntry> modsToDownload = new ArrayList<>();
    private List<String> filesToDownload = new ArrayList<>();
    private List<String> filesToDelete = new ArrayList<>();

    private GuiUpdater gui;
    private boolean configError = false;

    public UpdaterCore() {
        try {
            ensureLocalConfigExists();
        } catch (Exception e) {
            System.err.println("FATAL: Could not ensure local configuration files exist: " + e.getMessage());
            configError = true;
        }
    }

    // Convenience: populate the cached lists (safe to call from Tweaker / main thread).
    public void populateConfirmationLists() {
        try {
            this.modsToDownload = getModsForConfirmation();
        } catch (Exception e) {
            e.printStackTrace();
            this.modsToDownload = new ArrayList<>();
        }
        try {
            this.filesToDownload = getFilesForConfirmation();
        } catch (Exception e) {
            e.printStackTrace();
            this.filesToDownload = new ArrayList<>();
        }
        try {
            this.filesToDelete = getDeletesForConfirmation();
        } catch (Exception e) {
            e.printStackTrace();
            this.filesToDelete = new ArrayList<>();
        }
    }

    /**
     * Fetches the list of new/updated mods for display in the confirmation dialog.
     * Enhanced: robust error handling, clear logging of request URLs / JSON blobs,
     * and strict display-name/file-name fallback order:
     *   display_name -> filenameFromSource -> "Untitled File"
     *
     * Returns ModEntry objects compatible with ModConfirmationDialog.ModEntry.
     */
    public List<ModEntry> getModsForConfirmation() throws Exception {
        if (configError) {
            System.err.println("[DEBUG-UPDATER] UpdaterCore is in an error state. Skipping network fetch.");
            this.modsToDownload = new ArrayList<>();
            return Collections.emptyList();
        }

        List<ModEntry> modsList = new ArrayList<>();

        JSONObject localConfig = FileUtils.readJson(remoteConfigPath);
        String remoteConfigUrl = localConfig != null ? localConfig.optString("remote_config_url", "").trim() : "";
        System.out.println("[DEBUG-UPDATER] remote_config_path=" + remoteConfigPath + " remote_config_url='" + remoteConfigUrl + "'");
        if (remoteConfigUrl.isEmpty()) {
            throw new RuntimeException("ModUpdater configuration incomplete (remote_config_url missing).");
        }

        String appliedVersion = FileUtils.readAppliedVersion(localVersionPath);
        JSONObject remoteConfig;
        try {
            remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
        } catch (Exception e) {
            System.err.println("[ERROR-UPDATER] Failed to read remote config from URL: " + remoteConfigUrl);
            e.printStackTrace();
            throw e;
        }

        String remoteVersion = remoteConfig != null ? remoteConfig.optString("modpackVersion", "0.0.0") : "0.0.0";
        String baseUrl       = remoteConfig != null ? remoteConfig.optString("configsBaseUrl", "")        : "";
        String modsJsonName  = remoteConfig != null ? remoteConfig.optString("modsJson", "mods.json")     : "mods.json";

        String modsUrl = FileUtils.joinUrl(baseUrl, modsJsonName);
        System.out.println("[DEBUG-UPDATER] appliedVersion=" + appliedVersion + " remoteVersion=" + remoteVersion + " modsUrl=" + modsUrl);

        JSONArray mods = null;
        try {
            mods = FileUtils.readJsonArrayFromUrl(modsUrl);
        } catch (Exception e) {
            System.err.println("[ERROR-UPDATER] Failed to fetch/parse mods JSON from: " + modsUrl);
            e.printStackTrace();
        }

        int upstreamCount = (mods == null) ? 0 : mods.length();
        System.out.println("[DEBUG-UPDATER] upstream mods count = " + upstreamCount);

        if (mods == null || mods.length() == 0) {
            System.out.println("[DEBUG-UPDATER] no mods in upstream JSON; returning empty list.");
            this.modsToDownload = modsList;
            return modsList;
        }

        // NEW BEHAVIOR:
        // Always evaluate each remote mod entry and compare expected on-disk filename to installed file(s)
        // for the same numberId. Only add to modsList when missing or filename differs.
        for (int i = 0; i < mods.length(); i++) {
            JSONObject mod = mods.getJSONObject(i);
            String jsonDisplay    = mod.optString("display_name", "").trim();
            String jsonFileName   = mod.optString("file_name", "").trim();
            String numberId       = mod.optString("numberId", "").trim();
            String installLocation= mod.optString("installLocation", "mods");

            String displaySource  = "URL";
            String downloadUrl    = null;
            String filenameFromSource = null;

            // --- Source resolution (URL / CurseForge / Modrinth) ---
            JSONObject source = mod.optJSONObject("source");
            if (source != null) {
                String type = source.optString("type", "url").trim().toLowerCase();
                try {
                    if ("url".equals(type)) {
                        downloadUrl = source.optString("url", null);
                        displaySource = "URL";
                        if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                            filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                        }
                    } else if ("curseforge".equals(type)) {
                        displaySource = "CurseForge";
                        int projectId = source.optInt("projectId", -1);
                        long fileId   = source.optLong("fileId", -1);
                        if (projectId > 0 && fileId > 0) {
                            String apiUrl = String.format(FileUtils.CF_PROXY_BASE_URL + "/mods/%d/files/%d", projectId, fileId);
                            try {
                                JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, FileUtils.CF_API_KEY);
                                if (fileData != null) {
                                    JSONObject data = fileData.optJSONObject("data");
                                    if (data == null) data = fileData;

                                    if (data != null) {
                                        if (data.has("downloadUrl"))  downloadUrl       = optNonEmptyString(data, "downloadUrl", downloadUrl);
                                        if (data.has("fileName"))     filenameFromSource= optNonEmptyString(data, "fileName", filenameFromSource);

                                        if ((filenameFromSource == null || filenameFromSource.isEmpty()) && data.has("files")) {
                                            Object filesObj = data.opt("files");
                                            if (filesObj instanceof JSONArray) {
                                                JSONArray filesA = (JSONArray) filesObj;
                                                if (filesA.length() > 0) {
                                                    JSONObject f0 = filesA.getJSONObject(0);
                                                    filenameFromSource = optNonEmptyString(f0, "fileName", filenameFromSource);
                                                    if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                                        filenameFromSource = optNonEmptyString(f0, "filename", filenameFromSource);
                                                    if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                        downloadUrl = optNonEmptyString(f0, "downloadUrl", downloadUrl);
                                                    if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                        downloadUrl = optNonEmptyString(f0, "url", downloadUrl);
                                                }
                                            }
                                        }
                                        if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                && downloadUrl != null && !downloadUrl.isEmpty()) {
                                            filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                        }
                                    }
                                }
                            } catch (Exception eks) {
                                System.err.println("[ERROR-UPDATER] Error fetching CurseForge metadata for projectId="
                                        + projectId + " fileId=" + fileId + " : " + eks.getMessage());
                            }
                        }
                    } else if ("modrinth".equals(type)) {
                        displaySource = "Modrinth";
                        String versionId = source.optString("versionId", "").trim();
                        if (!versionId.isEmpty()) {
                            try {
                                String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                                JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                                if (versionData != null) {
                                    JSONArray filesArr = versionData.optJSONArray("files");
                                    if (filesArr != null && filesArr.length() > 0) {
                                        JSONObject fobj = filesArr.getJSONObject(0);
                                        downloadUrl       = optNonEmptyString(fobj, "url", downloadUrl);
                                        filenameFromSource= optNonEmptyString(fobj, "filename", filenameFromSource);
                                        if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                            filenameFromSource = optNonEmptyString(fobj, "fileName", filenameFromSource);
                                        if ((downloadUrl == null || downloadUrl.isEmpty()))
                                            downloadUrl = optNonEmptyString(fobj, "downloadUrl", downloadUrl);

                                        if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                && downloadUrl != null && !downloadUrl.isEmpty()) {
                                            filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                        }
                                    }
                                }
                            } catch (Exception exm) {
                                System.err.println("[ERROR-UPDATER] Error fetching Modrinth metadata for versionId=" + versionId + " : " + exm.getMessage());
                            }
                        }
                    } else {
                        displaySource = source.optString("type", displaySource);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR-UPDATER] Exception while resolving source for mod: " + mod.toString());
                    e.printStackTrace();
                }
            }

            if ((filenameFromSource == null || filenameFromSource.trim().isEmpty())
                    && downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                try {
                    filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                } catch (Exception ignore) {}
            }

            // UI display name
            String displayName;
            if (!jsonDisplay.isEmpty()) displayName = jsonDisplay;
            else if (filenameFromSource != null && !filenameFromSource.isEmpty()) displayName = filenameFromSource;
            else displayName = "Untitled File";

            // Base filename logic (same as original)
            String baseFileName;
            if (!jsonFileName.isEmpty()) {
                baseFileName = jsonFileName;
                if (!baseFileName.toLowerCase().endsWith(".jar")) baseFileName = baseFileName + ".jar";
            } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                baseFileName = filenameFromSource;
            } else {
                String safe = displayName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                if (!safe.toLowerCase().endsWith(".jar")) safe += ".jar";
                baseFileName = safe;
            }

            String diskFileName = baseFileName;
            if (!numberId.isEmpty() && !baseFileName.startsWith(numberId + "-")) {
                diskFileName = numberId + "-" + baseFileName;
            }

            String entryDownloadUrl = (downloadUrl == null) ? "" : downloadUrl;

            // --- NEW: per-mod check regardless of global version ---
            File targetDir = new File(installLocation);
            List<File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
            boolean needs = false;

            if (existing.isEmpty()) {
                // No installed file for this id -> install
                needs = true;
                System.out.println("[DEBUG-UPDATER] Mod missing for numberId=" + numberId + " (will propose): expected=" + diskFileName);
            } else {
                File found = existing.get(0);
                // If filename differs, replace.
                if (!found.getName().equals(diskFileName)) {
                    needs = true;
                    System.out.println("[DEBUG-UPDATER] Filename mismatch for numberId=" + numberId + " (will propose): found=" + found.getName() + " expected=" + diskFileName);
                } else {
                    // Optional: use remote Content-Length as an extra check (if available)
                    long remoteSize = -1L;
                    try {
                        if (entryDownloadUrl != null && !entryDownloadUrl.isEmpty()) remoteSize = fetchRemoteContentLength(entryDownloadUrl);
                    } catch (Exception ignored) {}
                    if (remoteSize > 0 && found.length() != remoteSize) {
                        needs = true;
                        System.out.println("[DEBUG-UPDATER] Size mismatch for numberId=" + numberId + " (will propose): local=" + found.length() + " remote=" + remoteSize);
                    } else {
                        // otherwise treat it as OK and skip
                        System.out.println("[DEBUG-UPDATER] Mod present and filename matches: " + found.getPath());
                    }
                }
            }

            if (needs) {
                ModEntry me = new ModEntry(displayName, entryDownloadUrl, diskFileName, displaySource, numberId, installLocation);
                modsList.add(me);
            }
        }

        this.modsToDownload = modsList;
        return modsList;
    }




    /**
     * Fetches a simple list of non-mod files that will be installed or updated.
     *
     * @return List of file paths (config/...) to be installed/updated.
     */
    public List<String> getFilesForConfirmation() throws Exception {
        if (configError) {
            this.filesToDownload = new ArrayList<>();
            return Collections.emptyList();
        }

        List<String> filePaths = new ArrayList<>();

        JSONObject localConfig = FileUtils.readJson(remoteConfigPath);
        String remoteConfigUrl = localConfig.optString("remote_config_url", "").trim();
        System.out.println("[DEBUG-UPDATER] files remote_config_url='" + remoteConfigUrl + "'");
        if (remoteConfigUrl.isEmpty()) {
            throw new RuntimeException("ModUpdater configuration incomplete (remote_config_url missing).");
        }

        String appliedVersion = FileUtils.readAppliedVersion(localVersionPath);
        JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
        String remoteVersion = remoteConfig.optString("modpackVersion", "0.0.0");
        String baseUrl = remoteConfig.optString("configsBaseUrl", "");
        String filesJsonName = remoteConfig.optString("filesJson", "files.json");

        String filesUrl = FileUtils.joinUrl(baseUrl, filesJsonName);
        System.out.println("[DEBUG-UPDATER] appliedVersion=" + appliedVersion + " remoteVersion=" + remoteVersion + " filesUrl=" + filesUrl);

        JSONObject filesRoot = FileUtils.readJsonFromUrl(filesUrl);
        JSONArray allFiles = filesRoot != null ? filesRoot.optJSONArray("files") : null;
        int upstreamCount = (allFiles == null) ? 0 : allFiles.length();
        System.out.println("[DEBUG-UPDATER] upstream files count = " + upstreamCount);
        if (allFiles == null || allFiles.length() == 0) {
            System.out.println("[DEBUG-UPDATER] no files in upstream JSON; returning empty list.");
            this.filesToDownload = filePaths;
            return filePaths;
        }

        // Evaluate each remote file entry and compare expected filename to local file.
        for (int i = 0; i < allFiles.length(); i++) {
            JSONObject file = allFiles.getJSONObject(i);
            String url = file.optString("url", "");
            String downloadPath = file.optString("downloadPath", "config/");
            String name = file.optString("file_name", "").trim();
            String srcName = (url == null || url.trim().isEmpty()) ? null : FileUtils.extractFileNameFromUrl(url);

            String fileName;
            if (name.isEmpty()) {
                fileName = (srcName == null || srcName.isEmpty()) ? "file" : srcName;
            } else if (name.indexOf('.') < 0 && srcName != null) {
                int dot = srcName.lastIndexOf('.');
                if (dot > 0 && dot < srcName.length() - 1) {
                    String ext = srcName.substring(dot + 1);
                    if (!ext.contains("/") && !ext.contains("\\")) fileName = name + "." + ext; else fileName = name;
                } else {
                    fileName = name;
                }
            } else {
                fileName = name;
            }

            File dest = new File(downloadPath, fileName);
            boolean needs = false;

            if (!dest.exists()) {
                needs = true;
                System.out.println("[DEBUG-UPDATER] File missing (will propose): " + dest.getPath());
            } else {
                // Optional: compare remote Content-Length when available
                long remoteSize = -1L;
                try {
                    if (url != null && !url.isEmpty()) remoteSize = fetchRemoteContentLength(url);
                } catch (Exception ignored) {}
                if (remoteSize > 0 && dest.length() != remoteSize) {
                    needs = true;
                    System.out.println("[DEBUG-UPDATER] File size mismatch (will propose): " + dest.getPath() + " local=" + dest.length() + " remote=" + remoteSize);
                } else {
                    // if file exists and size check passes (or not available), skip
                    System.out.println("[DEBUG-UPDATER] File present and OK: " + dest.getPath());
                }
            }

            if (needs) {
                filePaths.add("FILE: " + downloadPath + fileName);
            }
        }

        // cache for UI getters
        this.filesToDownload = filePaths;
        return filePaths;
    }


    /**
     * Fetches a simple list of files/folders that will be deleted by the update process,
     * intended for display in the confirmation dialog.
     *
     * @return List of file/folder paths to be deleted, prefixed (e.g., "FILE: path/to/file.txt").
     */
    public List<String> getDeletesForConfirmation() throws Exception {
        if (configError) {
            this.filesToDelete = new ArrayList<>();
            return Collections.emptyList();
        }

        List<String> deletePaths = new ArrayList<>();

        JSONObject localConfig = FileUtils.readJson(remoteConfigPath);
        String remoteConfigUrl = localConfig.optString("remote_config_url", "").trim();
        System.out.println("[DEBUG-UPDATER] deletes remote_config_url='" + remoteConfigUrl + "'");
        if (remoteConfigUrl.isEmpty()) {
            throw new RuntimeException("ModUpdater configuration incomplete (remote_config_url missing).");
        }

        String appliedVersion = FileUtils.readAppliedVersion(localVersionPath);
        JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
        String remoteVersion = remoteConfig.optString("modpackVersion", "0.0.0");
        String baseUrl = remoteConfig.optString("configsBaseUrl", "");
        String deletesJsonName = remoteConfig.optString("deletesJson", "deletes.json");

        if (FileUtils.compareVersions(appliedVersion, remoteVersion) >= 0) {
            this.filesToDelete = deletePaths;
            return deletePaths; // Not upgrading, no new deletes to show
        }

        String deletesUrl = FileUtils.joinUrl(baseUrl, deletesJsonName);
        System.out.println("[DEBUG-UPDATER] appliedVersion=" + appliedVersion + " remoteVersion=" + remoteVersion + " deletesUrl=" + deletesUrl);

        JSONObject deletesRoot = FileUtils.readJsonFromUrl(deletesUrl);
        JSONArray allDeletes = deletesRoot != null ? deletesRoot.optJSONArray("deletes") : null;
        int upstreamCount = (allDeletes == null) ? 0 : allDeletes.length();
        System.out.println("[DEBUG-UPDATER] upstream deletes count = " + upstreamCount);
        if (allDeletes == null || allDeletes.length() == 0) {
            System.out.println("[DEBUG-UPDATER] no deletes in upstream JSON; returning empty list.");
            this.filesToDelete = deletePaths;
            return deletePaths;
        }

        // Filter Deletes (Only those applicable between applied and remote version)
        List<JSONObject> deletesToApply = buildSinceList(allDeletes, appliedVersion, remoteVersion);
        System.out.println("[DEBUG-UPDATER] deletes after version filter = " + deletesToApply.size());

        // Extract paths into a flat list
        for (JSONObject del : deletesToApply) {
            JSONArray paths = del.optJSONArray("paths");
            if (paths != null) {
                for (int i = 0; i < paths.length(); i++) {
                    deletePaths.add("FILE: " + paths.getString(i));
                }
            }
            JSONArray folders = del.optJSONArray("folders");
            if (folders != null) {
                for (int i = 0; i < folders.length(); i++) {
                    deletePaths.add("FOLDER: " + folders.getString(i));
                }
            }
        }

        // Fallback: if filtered empty but upstream had entries, show upstream (for UI/debugging)
        if (deletePaths.isEmpty() && upstreamCount > 0) {
            System.out.println("[DEBUG-UPDATER] version-filter produced empty delete list but upstream has entries; using fallback (unfiltered) for UI.");
            List<String> fallback = new ArrayList<>();
            for (int i = 0; i < allDeletes.length(); i++) {
                JSONObject j = allDeletes.getJSONObject(i);
                JSONArray paths = j.optJSONArray("paths");
                if (paths != null) {
                    for (int k = 0; k < paths.length(); k++) fallback.add("FILE: " + paths.getString(k));
                }
                JSONArray folders = j.optJSONArray("folders");
                if (folders != null) {
                    for (int k = 0; k < folders.length(); k++) fallback.add("FOLDER: " + folders.getString(k));
                }
            }
            this.filesToDelete = fallback;
            return fallback;
        }

        // cache for UI getters
        this.filesToDelete = deletePaths;
        return deletePaths;
    }

    // --- EXECUTION METHODS (CALLED BY DIALOG AFTER CONFIRMATION) ---

    /**
     * Executes the full update process for selected mods and files.
     * This is called by the ModConfirmationDialog on a background thread.
     *
     * @param selectedMods The list of ModEntry objects the user confirmed to download.
     */
    public void runUpdateSelected(List<ModEntry> selectedMods) {
        gui = new GuiUpdater();
        gui.show("Starting ModUpdater (Confirmed Update)...");

        try {
            JSONObject localConfig = FileUtils.readJson(remoteConfigPath);
            String remoteConfigUrl = localConfig.optString("remote_config_url", "").trim();

            String appliedVersion = FileUtils.readAppliedVersion(localVersionPath);
            JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            String remoteVersion = remoteConfig.optString("modpackVersion", "0.0.0");
            String baseUrl = remoteConfig.optString("configsBaseUrl", "");
            String modsJsonName = remoteConfig.optString("modsJson", "mods.json");
            String filesJsonName = remoteConfig.optString("filesJson", "files.json");
            String deletesJsonName = remoteConfig.optString("deletesJson", "deletes.json");
            int maxRetries = remoteConfig.optInt("maxRetries", 3);
            int backupKeep = remoteConfig.optInt("backupKeep", 5);

            String modsUrl = FileUtils.joinUrl(baseUrl, modsJsonName);
            String filesUrl = FileUtils.joinUrl(baseUrl, filesJsonName);
            String deletesUrl = FileUtils.joinUrl(baseUrl, deletesJsonName);

            JSONObject filesRoot = FileUtils.readJsonFromUrl(filesUrl);
            JSONArray files = filesRoot.optJSONArray("files");
            if (files == null) files = new JSONArray();
            JSONObject deletesRoot = FileUtils.readJsonFromUrl(deletesUrl);
            JSONArray deletes = deletesRoot.optJSONArray("deletes");
            if (deletes == null) deletes = new JSONArray();

            boolean upgrading = FileUtils.compareVersions(appliedVersion, remoteVersion) < 0;

            // Build lists for execution
            // BUGFIX: Always use verify list for deletes so they run during upgrades too, even if
            // delete entries don't carry a proper 'since' field. This ensures cleanup happens
            // whenever moving to the target remoteVersion.
            List<JSONObject> deletesToApply = buildVerifyList(deletes, remoteVersion);
            List<JSONObject> filesToApply = buildSinceList(files, appliedVersion, remoteVersion);

            JSONArray modsFullList = FileUtils.readJsonArrayFromUrl(modsUrl);
            List<JSONObject> modsToApply = filterModsToApply(modsFullList, selectedMods, appliedVersion, remoteVersion);

            // For verification, use full lists
            List<JSONObject> filesToVerify = buildVerifyList(files, remoteVersion);
            List<JSONObject> modsToVerify = buildVerifyList(modsFullList, remoteVersion);

            // --- ACTUAL EXECUTION LOGIC (Copied from original runUpdate, but using filtered lists) ---
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backupRoot = new File("modupdater/backup/" + timestamp + "/");
            if (!backupRoot.exists()) backupRoot.mkdirs();

            int totalTasks = deletesToApply.size() + filesToApply.size() + modsToApply.size()
                    + Math.max(1, modsToVerify.size() + filesToVerify.size());
            if (totalTasks == 0) totalTasks = 1;
            gui.show("Starting update tasks (" + totalTasks + " steps)");
            int completed = 0;

            // 1) Deletes phase
            for (JSONObject del : deletesToApply) {
                String since = del.optString("since", "0.0.0");
                gui.show("Applying deletes for version " + since);

                JSONArray paths = del.optJSONArray("paths");
                if (paths != null) {
                    for (int i = 0; i < paths.length(); i++) {
                        String p = paths.getString(i);
                        File f = new File(p);
                        if (f.exists()) {
                            gui.show("Backing up then deleting: " + p);
                            FileUtils.backupPathTo(f, backupRoot);
                            FileUtils.deleteSilently(f, gui);
                        } else {
                            gui.show("Delete skip (not present): " + p);
                        }
                    }
                }
                JSONArray folders = del.optJSONArray("folders");
                if (folders != null) {
                    for (int i = 0; i < folders.length(); i++) {
                        String p = folders.getString(i);
                        File d = new File(p);
                        if (d.exists()) {
                            gui.show("Backing up then deleting folder: " + p);
                            FileUtils.backupPathTo(d, backupRoot);
                            FileUtils.deleteSilently(d, gui);
                        } else {
                            gui.show("Folder delete skip (not present): " + p);
                        }
                    }
                }

                completed++;
                updateProgress(completed, totalTasks);
            }

            // 2) Files phase: handle verify + apply (Uses original logic, combining verify/apply lists)
            Map<String, JSONObject> fileHandleMap = new LinkedHashMap<>();
            // Track which entries came from filesToApply so we can honor 'overwrite' on actual updates
            Set<String> filesApplyKeys = new HashSet<>();
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                filesApplyKeys.add(key);
            }
            // Add all files to verify
            for (JSONObject f : filesToVerify) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }
            // Overwrite/add files to apply (only new files or files needing update will be downloaded)
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }

            for (JSONObject f : fileHandleMap.values()) {
                String url = f.getString("url");
                String downloadPath = f.optString("downloadPath", "config/");
                String name = f.optString("file_name", "").trim();
                boolean overwrite = f.optBoolean("overwrite", true);
                boolean extract = f.optBoolean("extract", false);
                // hash removed -- we won't use it
                // String expectedHash = f.optString("hash", null);

                // Resolve final file name with extension handling:
                // If file_name is provided but has no extension, append extension from source filename.
                String srcName = (url == null || url.trim().isEmpty()) ? null : FileUtils.extractFileNameFromUrl(url);
                String fileName;
                if (name.isEmpty()) {
                    fileName = (srcName == null || srcName.isEmpty()) ? "file" : srcName;
                } else {
                    if (name.indexOf('.') < 0 && srcName != null) {
                        int dot = srcName.lastIndexOf('.');
                        if (dot > 0 && dot < srcName.length() - 1) {
                            String ext = srcName.substring(dot + 1);
                            if (!ext.contains("/") && !ext.contains("\\")) {
                                fileName = name + "." + ext;
                            } else {
                                fileName = name;
                            }
                        } else {
                            fileName = name;
                        }
                    } else {
                        fileName = name;
                    }
                }
                File dest = new File(downloadPath, fileName);
                boolean needDownload = false;
                String currentKey = f.optString("url", "") + "|" + f.optString("file_name", "");
                boolean isApplyItem = filesApplyKeys.contains(currentKey);

                if (dest.exists()) {
                    // No hash: for files we rely on overwrite + upgrading semantics
                    if (overwrite && upgrading) {
                        gui.show("Existing file present and overwrite=true (upgrade detected) — will re-download: " + dest.getPath());
                        needDownload = true;
                    } else {
                        gui.show("Existing file present; no hash specified — skipping download: " + dest.getPath());
                    }
                } else {
                    gui.show("File missing; will download: " + dest.getPath());
                    needDownload = true;
                }

                if (needDownload) {
                    // tmp root outside target directories
                    File tmpRoot = new File("modupdater/tmp/");
                    if (!tmpRoot.exists()) tmpRoot.mkdirs();
                    File tmp = new File(tmpRoot, fileName + "-" + UUID.randomUUID().toString() + ".tmp");

                    gui.show("Downloading file: " + url + " -> " + tmp.getPath());
                    // pass null for expected hash (no hash-based verification)
                    boolean ok = FileUtils.downloadWithVerification(url, tmp, null, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        throw new RuntimeException("Failed to download/verify file: " + url);
                    }

                    // backup and delete existing dest if needed
                    if (dest.exists() && overwrite) {
                        FileUtils.backupPathTo(dest, backupRoot);
                        FileUtils.deleteSilently(dest, gui);
                    } else if (dest.exists() && !overwrite) {
                        gui.show("Not overwriting existing file: " + dest.getPath());
                    }

                    // atomic move tmp -> dest
                    FileUtils.atomicMoveWithRetries(tmp, dest, 5, 200);
                    gui.show("Installed file: " + dest.getPath());
                }

                if (extract && dest.getName().toLowerCase().endsWith(".zip")) {
                    gui.show("Extracting " + dest.getPath());
                    FileUtils.unzip(dest, new File(downloadPath), overwrite, gui);
                }

                completed++;
                updateProgress(completed, totalTasks);
            }

            // 3) Mods phase (Uses original logic, combining verify/apply lists)
            Map<String, JSONObject> modHandleMap = new LinkedHashMap<>();
            // Add all mods to verify (keys include display_name/file_name to avoid accidental dedupe)
            for (JSONObject m : modsToVerify) {
                String key = m.optString("numberId", "") + "|" + m.optString("file_name", "") + "|" + m.optString("display_name", "");
                modHandleMap.put(key, m);
            }
            // Overwrite/add mods to apply (only new mods or mods needing update will be downloaded)
            for (JSONObject m : modsToApply) {
                String key = m.optString("numberId", "") + "|" + m.optString("file_name", "") + "|" + m.optString("display_name", "");
                modHandleMap.put(key, m);
            }

            for (JSONObject mod : modHandleMap.values()) {
                String numberId = mod.optString("numberId", "").trim();
                String installLocation = mod.optString("installLocation", "mods");
                String displayNameJson = mod.optString("display_name", "").trim();
                String fileNameJson = mod.optString("file_name", "").trim();
                // hash removed -- we won't use it
                // String expectedHash = mod.optString("hash", "").trim();
                JSONObject source = mod.optJSONObject("source");

                // --- Complex Mod Source Resolution ---
                String filenameFromSource = null;
                String downloadUrl = null;
                if (source != null) {
                    try {
                        String type = source.optString("type", "url").trim().toLowerCase();
                        if ("url".equals(type)) {
                            downloadUrl = source.optString("url", null);
                            if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                            }
                        } else if ("curseforge".equals(type)) {
                            int projectId = source.optInt("projectId", -1);
                            long fileId   = source.optLong("fileId", -1);
                            if (projectId > 0 && fileId > 0) {
                                String apiUrl = String.format(FileUtils.CF_PROXY_BASE_URL + "/mods/%d/files/%d", projectId, fileId);
                                try {
                                    JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, FileUtils.CF_API_KEY);
                                    if (fileData != null) {
                                        JSONObject data = fileData.optJSONObject("data");
                                        if (data == null) data = fileData;

                                        if (data != null) {
                                            if (data.has("downloadUrl"))  downloadUrl        = optNonEmptyString(data, "downloadUrl", downloadUrl);
                                            if (data.has("fileName"))     filenameFromSource = optNonEmptyString(data, "fileName", filenameFromSource);

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty()) && data.has("files")) {
                                                Object filesObj = data.opt("files");
                                                if (filesObj instanceof JSONArray) {
                                                    JSONArray filesA = (JSONArray) filesObj;
                                                    if (filesA.length() > 0) {
                                                        JSONObject f0 = filesA.getJSONObject(0);
                                                        filenameFromSource = optNonEmptyString(f0, "fileName", filenameFromSource);
                                                        if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                                            filenameFromSource = optNonEmptyString(f0, "filename", filenameFromSource);
                                                        if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                            downloadUrl = optNonEmptyString(f0, "downloadUrl", downloadUrl);
                                                        if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                            downloadUrl = optNonEmptyString(f0, "url", downloadUrl);
                                                    }
                                                }
                                            }

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                    && downloadUrl != null && !downloadUrl.isEmpty()) {
                                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // best-effort fallback
                                }
                            }
                        } else if ("modrinth".equals(type)) {
                            String versionId = source.optString("versionId", "").trim();
                            if (!versionId.isEmpty()) {
                                String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                                try {
                                    JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                                    if (versionData != null) {
                                        JSONArray filesArr = versionData.optJSONArray("files");
                                        if (filesArr != null && filesArr.length() > 0) {
                                            JSONObject fobj = filesArr.getJSONObject(0);
                                            downloadUrl        = optNonEmptyString(fobj, "url", downloadUrl);
                                            filenameFromSource = optNonEmptyString(fobj, "filename", filenameFromSource);
                                            if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                                filenameFromSource = optNonEmptyString(fobj, "fileName", filenameFromSource);
                                            if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                downloadUrl = optNonEmptyString(fobj, "downloadUrl", downloadUrl);

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                    && downloadUrl != null && !downloadUrl.isEmpty()) {
                                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // best-effort fallback
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // best-effort fallback, keep downloadUrl/filenameFromSource as null if not resolved
                    }
                }
                // If still no filename but have a URL, try extracting
                if ((filenameFromSource == null || filenameFromSource.isEmpty()) && downloadUrl != null && !downloadUrl.isEmpty()) {
                    filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                }

                if (filenameFromSource == null && (fileNameJson == null || fileNameJson.isEmpty()) && (downloadUrl == null || downloadUrl.isEmpty())) {
                    gui.show("Skipping mod with no source/filename: numberId=" + numberId);
                    completed++;
                    updateProgress(completed, totalTasks);
                    continue;
                }

                // Base filename (without numberId prefix):
                // 1) file_name from config (preferred)
                // 2) filenameFromSource
                // 3) safe(display_name or numberId) + ".jar"
                String baseName;
                if (!fileNameJson.isEmpty()) {
                    baseName = fileNameJson;
                    // Ensure .jar if file_name has no extension
                    if (!baseName.toLowerCase().endsWith(".jar")) {
                        baseName = baseName + ".jar";
                    }
                } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                    baseName = filenameFromSource;
                } else {
                    String base = !displayNameJson.isEmpty() ? displayNameJson : (numberId.isEmpty() ? "mod" : numberId);
                    String safe = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    if (!safe.toLowerCase().endsWith(".jar")) {
                        safe += ".jar";
                    }
                    baseName = safe;
                }

                // Apply numberId- prefix for on-disk name if we have a numberId
                String finalName = baseName;
                if (!numberId.isEmpty() && !baseName.startsWith(numberId + "-")) {
                    finalName = numberId + "-" + baseName;
                }

                File targetDir = new File(installLocation);
                if (!targetDir.exists()) targetDir.mkdirs();
                File target = new File(targetDir, finalName);

                List<File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
                if (existing.isEmpty() && target.exists()) existing.add(target);

                boolean needDownload = false;
                if (!existing.isEmpty()) {
                    File existingFile = existing.get(0);
                    // NAME-BASED check: if the existing file's name != expected finalName => replace it.
                    if (!existingFile.getName().equals(finalName)) {
                        gui.show("Mod filename mismatch; will redownload/replace: " + existingFile.getPath());
                        needDownload = true;
                    } else {
                        gui.show("Mod present: " + existingFile.getPath());
                    }
                } else {
                    gui.show("Mod missing; will download: " + finalName);
                    needDownload = true;
                }

                if (needDownload) {
                    // safe tmp dir
                    File tmpRoot = new File("modupdater/tmp/");
                    if (!tmpRoot.exists()) tmpRoot.mkdirs();
                    String safeTmpName = finalName + "-" + UUID.randomUUID().toString() + ".tmp";
                    File tmp = new File(tmpRoot, safeTmpName);

                    if (downloadUrl == null) {
                        gui.show("No download URL available for mod numberId " + numberId + "; skipping.");
                        completed++;
                        updateProgress(completed, totalTasks);
                        continue;
                    }
                    gui.show("Downloading mod: " + downloadUrl + " -> " + tmp.getPath());
                    // pass null for expected hash (no hash verification)
                    boolean ok = FileUtils.downloadWithVerification(downloadUrl, tmp, null, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        throw new RuntimeException("Failed to download/verify mod: " + downloadUrl);
                    }

                    // backup & delete existing files for this numberId (but don't delete tmp)
                    List<File> existingFiles = FileUtils.findFilesForNumberId(targetDir, numberId);
                    for (File f : existingFiles) {
                        try {
                            if (f.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                gui.show("Skipping deletion of file equal to download tmp: " + f.getPath());
                                continue;
                            }
                        } catch (Exception ex) {
                            gui.show("Warning resolving path, skipping delete of: " + f.getPath());
                            continue;
                        }
                        FileUtils.backupPathTo(f, backupRoot);
                        FileUtils.deleteSilently(f, gui);
                    }

                    // if target exists (with finalName) backup/delete
                    if (target.exists()) {
                        try {
                            if (!target.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                FileUtils.backupPathTo(target, backupRoot);
                                FileUtils.deleteSilently(target, gui);
                            } else {
                                gui.show("Target equals tmp (unexpected); skipping delete");
                            }
                        } catch (Exception ex) {
                            gui.show("Warning resolving target path; skipping delete: " + target.getPath());
                        }
                    }

                    // move tmp -> final
                    FileUtils.atomicMoveWithRetries(tmp, target, 5, 200);
                    gui.show("Installed mod: " + target.getPath());
                }

                completed++;
                updateProgress(completed, totalTasks);
            }
            // --- END EXECUTION LOGIC ---

            // commit applied version on full success
            FileUtils.writeAppliedVersion(localVersionPath, remoteVersion);
            gui.show("Update complete. Applied version: " + remoteVersion);
            gui.setProgress(100);

            FileUtils.pruneBackups("modupdater/backup", backupKeep, gui);

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (gui != null) gui.close();
        }
    }

    // --- UTILITY METHODS (FOR VERSION AND PROGRESS) ---

    /**
     * Original full update logic, kept but renamed to run synchronously (no pre-confirmation).
     * @deprecated Use {@link #runUpdateSelected(List)} for confirmed updates.
     */
    @Deprecated
    public void runUpdateSynchronous() {
        gui = new GuiUpdater();
        gui.show("Initializing ModUpdater (Synchronous Mode)...");

        try {
            ensureLocalConfigExists();

            JSONObject localConfig = FileUtils.readJson(remoteConfigPath);
            String remoteConfigUrl = localConfig.optString("remote_config_url", "").trim();
            if (remoteConfigUrl.isEmpty()) {
                gui.show("CONFIG ERROR: remote_config_url missing in " + remoteConfigPath);
                throw new RuntimeException("ModUpdater configuration incomplete.");
            }

            String appliedVersion = FileUtils.readAppliedVersion(localVersionPath);
            gui.show("Local applied version: " + appliedVersion);

            gui.show("Fetching remote config...");
            JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            String remoteVersion = remoteConfig.optString("modpackVersion", "0.0.0");
            String baseUrl = remoteConfig.optString("configsBaseUrl", "");
            String modsJsonName = remoteConfig.optString("modsJson", "mods.json");
            String filesJsonName = remoteConfig.optString("filesJson", "files.json");
            String deletesJsonName = remoteConfig.optString("deletesJson", "deletes.json");
            boolean checkCurrentVersion = remoteConfig.optBoolean("checkCurrentVersion", true);
            int maxRetries = remoteConfig.optInt("maxRetries", 3);
            int backupKeep = remoteConfig.optInt("backupKeep", 5);

            gui.show("Remote modpack version: " + remoteVersion);

            String modsUrl = FileUtils.joinUrl(baseUrl, modsJsonName);
            String filesUrl = FileUtils.joinUrl(baseUrl, filesJsonName);
            String deletesUrl = FileUtils.joinUrl(baseUrl, deletesJsonName);

            JSONArray mods = FileUtils.readJsonArrayFromUrl(modsUrl);
            JSONObject filesRoot = FileUtils.readJsonFromUrl(filesUrl);
            JSONArray files = filesRoot.optJSONArray("files");
            if (files == null) files = new JSONArray();
            JSONObject deletesRoot = FileUtils.readJsonFromUrl(deletesUrl);
            JSONArray deletes = deletesRoot.optJSONArray("deletes");
            if (deletes == null) deletes = new JSONArray();

            boolean upgrading = FileUtils.compareVersions(appliedVersion, remoteVersion) < 0;

            // Build lists for execution (align with runUpdateSelected semantics)
            // BUGFIX: Always use verify list for deletes so they run during upgrades too.
            List<JSONObject> deletesToApply = buildVerifyList(deletes, remoteVersion);
            List<JSONObject> filesToApply = buildSinceList(files, appliedVersion, remoteVersion);
            List<JSONObject> modsToApply = buildSinceList(mods, appliedVersion, remoteVersion);

            // verification lists (all items <= remoteVersion)
            List<JSONObject> filesToVerify = buildVerifyList(files, remoteVersion);
            List<JSONObject> modsToVerify = buildVerifyList(mods, remoteVersion);

            if (!upgrading && !checkCurrentVersion) {
                gui.show("Already at latest version and checkCurrentVersion=false. Nothing to do.");
                gui.setProgress(100);
                return;
            }

            // --- EXECUTION LOGIC (mirrors runUpdateSelected to keep behavior consistent) ---
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backupRoot = new File("modupdater/backup/" + timestamp + "/");
            if (!backupRoot.exists()) backupRoot.mkdirs();

            int totalTasks = deletesToApply.size() + filesToApply.size() + modsToApply.size()
                    + Math.max(1, modsToVerify.size() + filesToVerify.size());
            if (totalTasks == 0) totalTasks = 1;
            gui.show("Starting update tasks (" + totalTasks + " steps)");
            int completed = 0;

            // 1) Deletes phase
            for (JSONObject del : deletesToApply) {
                String since = del.optString("since", "0.0.0");
                gui.show("Applying deletes for version " + since);

                JSONArray paths = del.optJSONArray("paths");
                if (paths != null) {
                    for (int i = 0; i < paths.length(); i++) {
                        String p = paths.getString(i);
                        File f = new File(p);
                        if (f.exists()) {
                            gui.show("Backing up then deleting: " + p);
                            FileUtils.backupPathTo(f, backupRoot);
                            FileUtils.deleteSilently(f, gui);
                        } else {
                            gui.show("Delete skip (not present): " + p);
                        }
                    }
                }
                JSONArray folders = del.optJSONArray("folders");
                if (folders != null) {
                    for (int i = 0; i < folders.length(); i++) {
                        String p = folders.getString(i);
                        File d = new File(p);
                        if (d.exists()) {
                            gui.show("Backing up then deleting folder: " + p);
                            FileUtils.backupPathTo(d, backupRoot);
                            FileUtils.deleteSilently(d, gui);
                        } else {
                            gui.show("Folder delete skip (not present): " + p);
                        }
                    }
                }

                completed++;
                updateProgress(completed, totalTasks);
            }

            // 2) Files phase: verify + apply
            Map<String, JSONObject> fileHandleMap = new LinkedHashMap<>();
            // Track which entries came from filesToApply so we can honor 'overwrite' on actual updates
            Set<String> filesApplyKeys = new HashSet<>();
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                filesApplyKeys.add(key);
            }
            // Add all files to verify
            for (JSONObject f : filesToVerify) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }
            // Overwrite/add files to apply (only new files or files needing update will be downloaded)
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }

            for (JSONObject f : fileHandleMap.values()) {
                String url = f.getString("url");
                String downloadPath = f.optString("downloadPath", "config/");
                String name = f.optString("file_name", "").trim();
                boolean overwrite = f.optBoolean("overwrite", true);
                boolean extract = f.optBoolean("extract", false);
                // String expectedHash = f.optString("hash", null); // no hash

                // Resolve final file name with extension handling (same as runUpdateSelected)
                String srcName = (url == null || url.trim().isEmpty()) ? null : FileUtils.extractFileNameFromUrl(url);
                String fileName;
                if (name.isEmpty()) {
                    fileName = (srcName == null || srcName.isEmpty()) ? "file" : srcName;
                } else {
                    if (name.indexOf('.') < 0 && srcName != null) {
                        int dot = srcName.lastIndexOf('.');
                        if (dot > 0 && dot < srcName.length() - 1) {
                            String ext = srcName.substring(dot + 1);
                            if (!ext.contains("/") && !ext.contains("\\")) {
                                fileName = name + "." + ext;
                            } else {
                                fileName = name;
                            }
                        } else {
                            fileName = name;
                        }
                    } else {
                        fileName = name;
                    }
                }
                File dest = new File(downloadPath, fileName);
                boolean needDownload = false;
                String currentKey = f.optString("url", "") + "|" + f.optString("file_name", "");
                boolean isApplyItem = filesApplyKeys.contains(currentKey);

                if (dest.exists()) {
                    if (overwrite && upgrading) {
                        gui.show("Existing file present and overwrite=true (upgrade detected) — will re-download: " + dest.getPath());
                        needDownload = true;
                    } else {
                        gui.show("Existing file present; no hash specified — skipping download: " + dest.getPath());
                    }
                } else {
                    gui.show("File missing; will download: " + dest.getPath());
                    needDownload = true;
                }

                if (needDownload) {
                    // tmp root outside target directories
                    File tmpRoot = new File("modupdater/tmp/");
                    if (!tmpRoot.exists()) tmpRoot.mkdirs();
                    File tmp = new File(tmpRoot, fileName + "-" + UUID.randomUUID().toString() + ".tmp");

                    gui.show("Downloading file: " + url + " -> " + tmp.getPath());
                    boolean ok = FileUtils.downloadWithVerification(url, tmp, null, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        throw new RuntimeException("Failed to download/verify file: " + url);
                    }

                    // backup and delete existing dest if needed
                    if (dest.exists() && overwrite) {
                        FileUtils.backupPathTo(dest, backupRoot);
                        FileUtils.deleteSilently(dest, gui);
                    } else if (dest.exists() && !overwrite) {
                        gui.show("Not overwriting existing file: " + dest.getPath());
                    }

                    // atomic move tmp -> dest
                    FileUtils.atomicMoveWithRetries(tmp, dest, 5, 200);
                    gui.show("Installed file: " + dest.getPath());
                }

                if (extract && dest.getName().toLowerCase().endsWith(".zip")) {
                    gui.show("Extracting " + dest.getPath());
                    FileUtils.unzip(dest, new File(downloadPath), overwrite, gui);
                }

                completed++;
                updateProgress(completed, totalTasks);
            }

            // 3) Mods phase
            Map<String, JSONObject> modHandleMap = new LinkedHashMap<>();
            for (JSONObject m : modsToVerify) {
                String key = m.optString("numberId", "") + "|" + m.optString("file_name", "") + "|" + m.optString("display_name", "");
                modHandleMap.put(key, m);
            }
            for (JSONObject m : modsToApply) {
                String key = m.optString("numberId", "") + "|" + m.optString("file_name", "") + "|" + m.optString("display_name", "");
                modHandleMap.put(key, m);
            }

            for (JSONObject mod : modHandleMap.values()) {
                String numberId = mod.optString("numberId", "").trim();
                String installLocation = mod.optString("installLocation", "mods");
                String displayNameJson = mod.optString("display_name", "").trim();
                String fileNameJson = mod.optString("file_name", "").trim();
                // String expectedHash = mod.optString("hash", "").trim(); // removed
                JSONObject source = mod.optJSONObject("source");

                // --- Source resolution (same as runUpdateSelected) ---
                String filenameFromSource = null;
                String downloadUrl = null;
                if (source != null) {
                    try {
                        String type = source.optString("type", "url").trim().toLowerCase();
                        if ("url".equals(type)) {
                            downloadUrl = source.optString("url", null);
                            if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                            }
                        } else if ("curseforge".equals(type)) {
                            int projectId = source.optInt("projectId", -1);
                            long fileId   = source.optLong("fileId", -1);
                            if (projectId > 0 && fileId > 0) {
                                String apiUrl = String.format(FileUtils.CF_PROXY_BASE_URL + "/mods/%d/files/%d", projectId, fileId);
                                try {
                                    JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, FileUtils.CF_API_KEY);
                                    if (fileData != null) {
                                        JSONObject data = fileData.optJSONObject("data");
                                        if (data == null) data = fileData;

                                        if (data != null) {
                                            if (data.has("downloadUrl"))  downloadUrl        = optNonEmptyString(data, "downloadUrl", downloadUrl);
                                            if (data.has("fileName"))     filenameFromSource = optNonEmptyString(data, "fileName", filenameFromSource);

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty()) && data.has("files")) {
                                                Object filesObj = data.opt("files");
                                                if (filesObj instanceof JSONArray) {
                                                    JSONArray filesA = (JSONArray) filesObj;
                                                    if (filesA.length() > 0) {
                                                        JSONObject f0 = filesA.getJSONObject(0);
                                                        filenameFromSource = optNonEmptyString(f0, "fileName", filenameFromSource);
                                                        if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                                            filenameFromSource = optNonEmptyString(f0, "filename", filenameFromSource);
                                                        if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                            downloadUrl = optNonEmptyString(f0, "downloadUrl", downloadUrl);
                                                        if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                            downloadUrl = optNonEmptyString(f0, "url", downloadUrl);
                                                    }
                                                }
                                            }

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                    && downloadUrl != null && !downloadUrl.isEmpty()) {
                                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // best-effort fallback
                                }
                            }
                        } else if ("modrinth".equals(type)) {
                            String versionId = source.optString("versionId", "").trim();
                            if (!versionId.isEmpty()) {
                                String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                                try {
                                    JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                                    if (versionData != null) {
                                        JSONArray filesArr = versionData.optJSONArray("files");
                                        if (filesArr != null && filesArr.length() > 0) {
                                            JSONObject fobj = filesArr.getJSONObject(0);
                                            downloadUrl        = optNonEmptyString(fobj, "url", downloadUrl);
                                            filenameFromSource = optNonEmptyString(fobj, "filename", filenameFromSource);
                                            if ((filenameFromSource == null || filenameFromSource.isEmpty()))
                                                filenameFromSource = optNonEmptyString(fobj, "fileName", filenameFromSource);
                                            if ((downloadUrl == null || downloadUrl.isEmpty()))
                                                downloadUrl = optNonEmptyString(fobj, "downloadUrl", downloadUrl);

                                            if ((filenameFromSource == null || filenameFromSource.isEmpty())
                                                    && downloadUrl != null && !downloadUrl.isEmpty()) {
                                                filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // best-effort fallback
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // best-effort fallback
                    }
                }
                if ((filenameFromSource == null || filenameFromSource.isEmpty()) && downloadUrl != null && !downloadUrl.isEmpty()) {
                    filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                }

                if (filenameFromSource == null && (fileNameJson == null || fileNameJson.isEmpty()) && (downloadUrl == null || downloadUrl.isEmpty())) {
                    gui.show("Skipping mod with no source/filename: numberId=" + numberId);
                    completed++;
                    updateProgress(completed, totalTasks);
                    continue;
                }

                // Base filename resolution
                String baseName;
                if (!fileNameJson.isEmpty()) {
                    baseName = fileNameJson;
                    if (!baseName.toLowerCase().endsWith(".jar")) baseName = baseName + ".jar";
                } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                    baseName = filenameFromSource;
                } else {
                    String base = !displayNameJson.isEmpty() ? displayNameJson : (numberId.isEmpty() ? "mod" : numberId);
                    String safe = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    if (!safe.toLowerCase().endsWith(".jar")) safe += ".jar";
                    baseName = safe;
                }

                String finalName = baseName;
                if (!numberId.isEmpty() && !baseName.startsWith(numberId + "-")) {
                    finalName = numberId + "-" + baseName;
                }

                File targetDir = new File(installLocation);
                if (!targetDir.exists()) targetDir.mkdirs();
                File target = new File(targetDir, finalName);

                List<File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
                if (existing.isEmpty() && target.exists()) existing.add(target);

                boolean needDownloadMod = false;
                if (!existing.isEmpty()) {
                    File existingFile = existing.get(0);
                    if (!existingFile.getName().equals(finalName)) needDownloadMod = true;
                    else gui.show("Mod present (name matches): " + existingFile.getPath());
                } else {
                    gui.show("Mod missing; will download: " + finalName);
                    needDownloadMod = true;
                }

                if (needDownloadMod) {
                    File tmpRoot = new File("modupdater/tmp/");
                    if (!tmpRoot.exists()) tmpRoot.mkdirs();
                    String safeTmpName = finalName + "-" + UUID.randomUUID().toString() + ".tmp";
                    File tmp = new File(tmpRoot, safeTmpName);

                    if (downloadUrl == null) {
                        gui.show("No download URL available for mod numberId " + numberId + "; skipping.");
                        completed++;
                        updateProgress(completed, totalTasks);
                        continue;
                    }
                    gui.show("Downloading mod: " + downloadUrl + " -> " + tmp.getPath());
                    boolean ok = FileUtils.downloadWithVerification(downloadUrl, tmp, null, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        throw new RuntimeException("Failed to download/verify mod: " + downloadUrl);
                    }

                    // backup & delete existing files for this numberId (but don't delete tmp)
                    List<File> existingFiles = FileUtils.findFilesForNumberId(targetDir, numberId);
                    for (File f : existingFiles) {
                        try {
                            if (f.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                gui.show("Skipping deletion of file equal to download tmp: " + f.getPath());
                                continue;
                            }
                        } catch (Exception ex) {
                            gui.show("Warning resolving path, skipping delete of: " + f.getPath());
                            continue;
                        }
                        FileUtils.backupPathTo(f, backupRoot);
                        FileUtils.deleteSilently(f, gui);
                    }

                    // if target exists (with finalName) backup/delete
                    if (target.exists()) {
                        try {
                            if (!target.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                FileUtils.backupPathTo(target, backupRoot);
                                FileUtils.deleteSilently(target, gui);
                            } else {
                                gui.show("Target equals tmp (unexpected); skipping delete");
                            }
                        } catch (Exception ex) {
                            gui.show("Warning resolving target path; skipping delete: " + target.getPath());
                        }
                    }

                    // move tmp -> final
                    FileUtils.atomicMoveWithRetries(tmp, target, 5, 200);
                    gui.show("Installed mod: " + target.getPath());
                }

                completed++;
                updateProgress(completed, totalTasks);
            }
            // --- END EXECUTION LOGIC ---

            // commit applied version on full success
            FileUtils.writeAppliedVersion(localVersionPath, remoteVersion);
            gui.show("Update complete. Applied version: " + remoteVersion);
            gui.setProgress(100);

            FileUtils.pruneBackups("modupdater/backup", backupKeep, gui);

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (gui != null && !configError) gui.close();
        }
    }

    // Helper method to filter the full mods list based on what the user agreed to AND version range
    private List<JSONObject> filterModsToApply(JSONArray modsFullList, List<ModEntry> selectedMods, String appliedVersion, String remoteVersion) {
        // 1. Get the list of all mods that need to be applied based on version range
        List<JSONObject> versionFilteredMods = buildSinceList(modsFullList, appliedVersion, remoteVersion);

        // As the user confirms ALL changes in the dialog, we just return the version-filtered list.
        return versionFilteredMods;
    }

    private List<JSONObject> buildSinceList(JSONArray arr, String fromExclusive, String toInclusive) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String since = o.optString("since", "0.0.0");
            if (FileUtils.compareVersions(since, fromExclusive) > 0 && FileUtils.compareVersions(since, toInclusive) <= 0) {
                out.add(o);
            }
        }
        return out;
    }

    private List<JSONObject> buildVerifyList(JSONArray arr, String toInclusive) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String since = o.optString("since", "0.0.0");
            if (FileUtils.compareVersions(since, toInclusive) <= 0) out.add(o);
        }
        return out;
    }

    private void ensureLocalConfigExists() throws Exception {
        String defaultRemoteConfig = "{\"remote_config_url\": \"\"}";
        FileUtils.ensureFileAndDirectoryExist(remoteConfigPath, defaultRemoteConfig);
        String defaultLocalVersion = "\"0.0.0\"";
        FileUtils.ensureFileAndDirectoryExist(localVersionPath, defaultLocalVersion);
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

    /**
     * Try to fetch the content-length from the remote URL using HTTP HEAD.
     * Returns -1 if not available or not an HTTP(s) URL.
     */
    private long fetchRemoteContentLength(String urlStr) {
        if (urlStr == null) return -1L;
        try {
            URL url = new URL(urlStr);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) return -1L;
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "ModUpdater/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                long len = conn.getContentLengthLong();
                conn.disconnect();
                return len >= 0 ? len : -1L;
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return -1L;
    }


    // ---------- safeOptString helper - place at class scope (not inside another method) ----------
    /**
     * Safe optString wrapper that returns the defaultValue when the key is missing/empty.
     * Put this method at class scope inside UpdaterCore (e.g. after buildVerifyList or near other helpers).
     */
    private static String optNonEmptyString(JSONObject obj, String key, String defaultValue) {
        if (obj == null) return defaultValue;
        try {
            String s = obj.optString(key, null);
            if (s == null) return defaultValue;
            s = s.trim();
            return s.isEmpty() ? defaultValue : s;
        } catch (Exception e) {
            return defaultValue;
        }
    }


    // Returns the list of mods for the UI
    public List<ModConfirmationDialog.ModEntry> getModsForUI() {
        return Collections.unmodifiableList(this.modsToDownload);
    }

    public List<String> getFilesForUI() {
        return Collections.unmodifiableList(this.filesToDownload);
    }

    public List<String> getDeletesForUI() {
        return Collections.unmodifiableList(this.filesToDelete);
    }



    // Placeholder method for the main update loops in the deprecated synchronous function
    private void executeAllUpdatePhases(List<JSONObject> deletesToApply, List<JSONObject> filesToApply, List<JSONObject> modsToApply, List<JSONObject> filesToVerify, List<JSONObject> modsToVerify, JSONObject remoteConfig, String appliedVersion, String remoteVersion, int maxRetries, int backupKeep) {
        // This method is a placeholder for the massive update loops (Deletes, Files, Mods)
        // that exist in the original prompt's runUpdate method. It prevents repetition.
        System.out.println("Executing full update phases (Synchronous Mode)...");
        // ... (The entire body of the original runUpdate() from the prompt would be here) ...
    }
}
