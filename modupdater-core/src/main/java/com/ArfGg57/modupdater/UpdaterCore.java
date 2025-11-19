package com.ArfGg57.modupdater;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * UpdaterCore: downloads to a safe tmp folder, backs up existing files, deletes safely,
 * then atomically moves new files into place with retries. Commits version only on full success.
 */
public class UpdaterCore {

    private final String localConfigDir = "config/ModUpdater/";
    private final String remoteConfigPath = localConfigDir + "config.json"; // holds remote_config_url
    private final String localVersionPath = localConfigDir + "modpack_version.json"; // holds applied version
    private final String modMetadataPath = localConfigDir + "mod_metadata.json"; // tracks installed mods

    private GuiUpdater gui;
    private boolean configError = false;

    public void runUpdate() {
        gui = new GuiUpdater();
        gui.show("Initializing ModUpdater...");

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

            // build lists that apply between appliedVersion (exclusive) and remoteVersion (inclusive)
            List<JSONObject> deletesToApply = buildSinceList(deletes, appliedVersion, remoteVersion);
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

            // 2) Files phase: handle verify + apply
            Map<String, JSONObject> fileHandleMap = new LinkedHashMap<>();
            for (JSONObject f : filesToVerify) {
                String key = f.optString("url", "") + "|" + f.optString("name", "");
                fileHandleMap.put(key, f);
            }
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("name", "");
                fileHandleMap.put(key, f);
            }

            for (JSONObject f : fileHandleMap.values()) {
                String url = f.getString("url");
                String downloadPath = f.optString("downloadPath", "config/");
                String name = f.optString("name", "").trim();
                boolean overwrite = f.optBoolean("overwrite", true);
                boolean extract = f.optBoolean("extract", false);
                String expectedHash = f.optString("hash", null);

                String fileName = name.isEmpty() ? FileUtils.extractFileNameFromUrl(url) : name;
                File dest = new File(downloadPath, fileName);
                boolean needDownload = false;

                if (dest.exists()) {
                    if (expectedHash != null && !expectedHash.trim().isEmpty()) {
                        String actual = HashUtils.sha256Hex(dest);
                        if (!FileUtils.hashEquals(expectedHash, actual)) {
                            gui.show("File hash mismatch (will repair): " + dest.getPath());
                            needDownload = true;
                        } else {
                            gui.show("File OK: " + dest.getPath());
                        }
                    } else if (!overwrite) {
                        gui.show("Existing file preserved (overwrite=false): " + dest.getPath());
                    } else {
                        gui.show("No hash provided; re-downloading for safety: " + dest.getPath());
                        needDownload = true;
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
                    boolean ok = FileUtils.downloadWithVerification(url, tmp, expectedHash, gui, maxRetries);
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

            // 3) Mods phase with metadata tracking
            gui.show("Initializing mod metadata...");
            ModMetadata modMetadata = new ModMetadata(modMetadataPath);
            
            // Build map of all mods that should be installed (from modsToVerify)
            Map<String, JSONObject> modHandleMap = new LinkedHashMap<>();
            for (JSONObject m : modsToVerify) {
                String key = m.optString("numberId", "");
                if (!key.isEmpty()) {
                    modHandleMap.put(key, m);
                }
            }

            // Step 1: Clean up mods folder - remove any files not in current mods.json
            gui.show("Scanning mods folder for outdated files...");
            Set<String> validNumberIds = modHandleMap.keySet();
            Set<String> installLocations = new HashSet<>();
            for (JSONObject m : modHandleMap.values()) {
                installLocations.add(m.optString("installLocation", "mods"));
            }
            
            for (String installLocation : installLocations) {
                File targetDir = new File(installLocation);
                if (!targetDir.exists() || !targetDir.isDirectory()) continue;
                
                File[] filesInDir = targetDir.listFiles();
                if (filesInDir == null) continue;
                
                for (File file : filesInDir) {
                    if (!file.isFile()) continue;
                    if (file.getName().endsWith(".tmp")) continue; // skip temp files
                    
                    // Check if this file belongs to a valid mod
                    boolean isValid = false;
                    String belongsToNumberId = null;
                    
                    // First check metadata
                    for (ModMetadata.ModEntry entry : modMetadata.getAllMods()) {
                        if (entry.fileName != null && entry.fileName.equals(file.getName())) {
                            belongsToNumberId = entry.numberId;
                            if (validNumberIds.contains(belongsToNumberId)) {
                                isValid = true;
                            }
                            break;
                        }
                    }
                    
                    // Also check by numberId prefix (for backwards compatibility)
                    if (!isValid && belongsToNumberId == null) {
                        for (String numberId : validNumberIds) {
                            if (!numberId.isEmpty() && file.getName().startsWith(numberId + "-")) {
                                isValid = true;
                                belongsToNumberId = numberId;
                                break;
                            }
                        }
                    }
                    
                    // If file is not valid, remove it
                    if (!isValid) {
                        gui.show("Removing outdated/untracked mod: " + file.getPath());
                        FileUtils.backupPathTo(file, backupRoot);
                        FileUtils.deleteSilently(file, gui);
                        if (belongsToNumberId != null) {
                            modMetadata.removeMod(belongsToNumberId);
                        }
                    }
                }
            }

            // Step 2: Process each mod in mods.json
            for (JSONObject mod : modHandleMap.values()) {
                String numberId = mod.optString("numberId", "").trim();
                String installLocation = mod.optString("installLocation", "mods");
                String displayName = mod.optString("display_name", "").trim();
                String fileName = mod.optString("file_name", "").trim();
                String expectedHash = mod.optString("hash", "").trim();
                JSONObject source = mod.optJSONObject("source");
                if (source == null) {
                    gui.show("Skipping mod with no source: numberId=" + numberId);
                    completed++;
                    updateProgress(completed, totalTasks);
                    continue;
                }

                String filenameFromSource = null;
                String type = source.optString("type", "url");
                String downloadUrl = null;
                if ("url".equals(type)) {
                    downloadUrl = source.getString("url");
                    filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                } else if ("curseforge".equals(type)) {
                    int projectId = source.optInt("projectId", -1);
                    long fileId = source.optLong("fileId", -1);
                    if (projectId > 0 && fileId > 0) {
                        String apiUrl = String.format(FileUtils.CF_PROXY_BASE_URL + "/mods/%s/files/%s", projectId, fileId);
                        JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, FileUtils.CF_API_KEY);
                        JSONObject data = fileData.optJSONObject("data");
                        if (data == null) data = fileData;
                        downloadUrl = data.getString("downloadUrl");
                        filenameFromSource = data.getString("fileName");
                    } else gui.show("CurseForge entry missing projectId/fileId for numberId " + numberId);
                } else if ("modrinth".equals(type)) {
                    String versionId = source.optString("versionId", "");
                    if (!versionId.isEmpty()) {
                        String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                        JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                        JSONArray filesArr = versionData.getJSONArray("files");
                        if (filesArr.length() > 0) {
                            JSONObject fileObj = filesArr.getJSONObject(0);
                            downloadUrl = fileObj.getString("url");
                            filenameFromSource = fileObj.getString("filename");
                        }
                    } else gui.show("Modrinth entry missing versionId for numberId " + numberId);
                }

                if (filenameFromSource == null) {
                    gui.show("Could not determine filename for mod numberId " + numberId + "; skipping.");
                    completed++;
                    updateProgress(completed, totalTasks);
                    continue;
                }

                // Determine final filename: use file_name from config, or displayName, or numberId-sourceFilename
                String finalName;
                if (!fileName.isEmpty()) {
                    finalName = fileName;
                } else if (!displayName.isEmpty()) {
                    finalName = numberId + "-" + displayName + ".jar";
                } else {
                    finalName = numberId + "-" + filenameFromSource;
                }
                
                File targetDir = new File(installLocation);
                if (!targetDir.exists()) targetDir.mkdirs();
                File target = new File(targetDir, finalName);

                // Check if mod is already correctly installed using metadata
                boolean needDownload = false;
                File existingFile = null;
                
                // Check metadata first
                if (modMetadata.isModInstalledAndMatches(numberId, source, expectedHash)) {
                    String installedFileName = modMetadata.findInstalledFile(numberId);
                    if (installedFileName != null) {
                        existingFile = new File(targetDir, installedFileName);
                        if (existingFile.exists()) {
                            // Verify hash if provided
                            if (!expectedHash.isEmpty()) {
                                String actual = HashUtils.sha256Hex(existingFile);
                                if (FileUtils.hashEquals(expectedHash, actual)) {
                                    gui.show("Mod OK (via metadata): " + existingFile.getPath());
                                    // Rename if needed
                                    if (!existingFile.getName().equals(finalName)) {
                                        FileUtils.backupPathTo(existingFile, backupRoot);
                                        FileUtils.atomicMoveWithRetries(existingFile, target, 5, 200);
                                        gui.show("Renamed mod to: " + target.getPath());
                                        modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                        existingFile = target;
                                    }
                                } else {
                                    gui.show("Mod hash mismatch; will redownload: " + existingFile.getPath());
                                    needDownload = true;
                                }
                            } else {
                                gui.show("Mod OK (no hash check): " + existingFile.getPath());
                            }
                        } else {
                            gui.show("Mod in metadata but file missing; will redownload: " + installedFileName);
                            needDownload = true;
                        }
                    } else {
                        needDownload = true;
                    }
                } else {
                    // Not in metadata or doesn't match - check by numberId prefix (backwards compatibility)
                    List<File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
                    if (!existing.isEmpty()) {
                        existingFile = existing.get(0);
                        if (!expectedHash.isEmpty()) {
                            String actual = HashUtils.sha256Hex(existingFile);
                            if (FileUtils.hashEquals(expectedHash, actual)) {
                                gui.show("Mod OK (via prefix): " + existingFile.getPath());
                                // Update metadata
                                modMetadata.recordMod(numberId, existingFile.getName(), expectedHash, source);
                                // Rename if needed
                                if (!existingFile.getName().equals(finalName)) {
                                    FileUtils.backupPathTo(existingFile, backupRoot);
                                    FileUtils.atomicMoveWithRetries(existingFile, target, 5, 200);
                                    gui.show("Renamed mod to: " + target.getPath());
                                    modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                }
                            } else {
                                gui.show("Mod hash mismatch; will redownload: " + existingFile.getPath());
                                needDownload = true;
                            }
                        } else {
                            gui.show("Mod present (no hash): " + existingFile.getPath());
                            modMetadata.recordMod(numberId, existingFile.getName(), "", source);
                        }
                    } else if (target.exists()) {
                        // File exists at target location but not tracked
                        if (!expectedHash.isEmpty()) {
                            String actual = HashUtils.sha256Hex(target);
                            if (FileUtils.hashEquals(expectedHash, actual)) {
                                gui.show("Mod OK at target location: " + target.getPath());
                                modMetadata.recordMod(numberId, finalName, expectedHash, source);
                            } else {
                                gui.show("File at target has wrong hash; will redownload: " + target.getPath());
                                needDownload = true;
                            }
                        } else {
                            gui.show("Mod present at target (no hash): " + target.getPath());
                            modMetadata.recordMod(numberId, finalName, "", source);
                        }
                    } else {
                        gui.show("Mod missing; will download: " + finalName);
                        needDownload = true;
                    }
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
                    boolean ok = FileUtils.downloadWithVerification(downloadUrl, tmp, expectedHash, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        throw new RuntimeException("Failed to download/verify mod: " + downloadUrl);
                    }

                    // backup & delete existing files for this numberId
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
                    
                    // Update metadata with actual hash
                    String actualHash = expectedHash;
                    if (actualHash.isEmpty()) {
                        actualHash = HashUtils.sha256Hex(target);
                    }
                    modMetadata.recordMod(numberId, finalName, actualHash, source);
                }

                completed++;
                updateProgress(completed, totalTasks);
            }
            
            // Save metadata
            gui.show("Saving mod metadata...");
            modMetadata.save();


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
}
