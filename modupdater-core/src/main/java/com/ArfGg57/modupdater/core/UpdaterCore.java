package com.ArfGg57.modupdater.core;

import com.ArfGg57.modupdater.deletion.DeletionProcessor;
import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.hash.RenamedFileResolver;
import com.ArfGg57.modupdater.metadata.ModMetadata;
import com.ArfGg57.modupdater.resolver.FilenameResolver;
import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.ui.GuiUpdater;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
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
    private List<File> pendingDeletes = new ArrayList<>();  // Track files that failed to delete
    
    // Constants for pending delete cleanup
    private static final int DELETION_WAIT_MS = 2000;  // Wait time before attempting deletion
    private static final int DELETION_KEEP_ALIVE_MS = 3000;  // Keep process alive after deletion
    
    /**
     * Get list of files that failed to delete (locked by the game)
     */
    public List<File> getPendingDeletes() {
        return new ArrayList<>(pendingDeletes);
    }

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
            boolean debugMode = remoteConfig.optBoolean("debugMode", false);
            
            // Initialize FilenameResolver for extension inference
            final FilenameResolver filenameResolver = new FilenameResolver(
                new FilenameResolver.Logger() {
                    public void log(String message) {
                        gui.show(message);
                    }
                },
                debugMode
            );
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

            gui.show("Calculating tasks to perform...");

            // Initialize metadata and pending operations before any processing
            gui.show("=== Initializing Metadata ===");
            gui.show("Initializing metadata from: " + modMetadataPath);
            ModMetadata modMetadata = new ModMetadata(modMetadataPath);
            gui.show("Loaded metadata for " + modMetadata.getAllMods().size() + " mod(s) and " + modMetadata.getAllFiles().size() + " file(s)");
            
            // Initialize RenamedFileResolver for centralized hash-based detection
            RenamedFileResolver fileResolver = new RenamedFileResolver(modMetadata, new RenamedFileResolver.Logger() {
                public void log(String message) {
                    gui.show(message);
                }
            });

            // Build file and mod maps for deduplication (needed for accurate progress calculation)
            Map<String, Boolean> fileNeedsApply = new LinkedHashMap<>();
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileNeedsApply.put(key, true);
            }
            
            Map<String, JSONObject> fileHandleMap = new LinkedHashMap<>();
            for (JSONObject f : filesToVerify) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }
            for (JSONObject f : filesToApply) {
                String key = f.optString("url", "") + "|" + f.optString("file_name", "");
                fileHandleMap.put(key, f);
            }

            // Build map of all mods that should be installed
            Map<String, JSONObject> modHandleMap = new LinkedHashMap<>();
            for (JSONObject m : modsToVerify) {
                String key = m.optString("numberId", "");
                if (!key.isEmpty()) {
                    modHandleMap.put(key, m);
                }
            }

            // FIXED: Calculate totalTasks based on actual work to be done
            int totalTasks = deletesToApply.size() + fileHandleMap.size() + modHandleMap.size();
            if (totalTasks == 0) totalTasks = 1;
            gui.show("Starting update tasks (" + totalTasks + " steps)");
            int completed = 0;

            // 1) Deletes phase - Use new DeletionProcessor
            gui.show("=== Starting Deletes Phase ===");
            DeletionProcessor deletionProcessor = new DeletionProcessor(
                new DeletionProcessor.Logger() {
                    public void log(String message) {
                        gui.show(message);
                    }
                },
                modMetadata,
                backupRoot
            );
            
            int deletionCount = deletionProcessor.processDeletions(deletesRoot, appliedVersion, remoteVersion);
            gui.show("Deletion phase completed: " + deletionCount + " item(s) deleted");
            
            // Track any failed deletions from the deletion processor
            pendingDeletes.addAll(deletionProcessor.getFailedDeletes());
            
            // Note: Metadata is saved inside DeletionProcessor after deletions
            
            completed += deletesToApply.size(); // Update progress
            updateProgress(completed, totalTasks);

            // 2) Files phase: handle verify + apply with metadata tracking
            gui.show("=== Starting Files Phase ===");

            for (JSONObject f : fileHandleMap.values()) {
                String url = f.optString("url", "").trim();
                if (url.isEmpty()) {
                    gui.show("WARNING: Skipping file entry with missing URL");
                    completed++;
                    updateProgress(completed, totalTasks);
                    continue;
                }
                String downloadPath = f.optString("downloadPath", "config/");
                // CHANGED: Use file_name instead of name to match schema
                // IMPORTANT: file_name is for actual filename, NOT display_name
                String customFileName = f.optString("file_name", "").trim();
                boolean overwrite = f.optBoolean("overwrite", true);
                boolean extract = f.optBoolean("extract", false);
                String expectedHash = f.optString("hash", null);

                // Determine actual filename to use with extension inference
                // Priority: file_name (if provided), or extract from URL
                // NEVER use display_name for saved filename
                String fileName;
                if (!customFileName.isEmpty()) {
                    // Use custom file_name from config, but infer extension if missing
                    fileName = filenameResolver.resolve(customFileName, url, null, FilenameResolver.ArtifactType.FILE);
                } else {
                    // Extract from URL with extension inference
                    String extracted = FileUtils.extractFileNameFromUrl(url);
                    fileName = filenameResolver.resolve(extracted, url, null, FilenameResolver.ArtifactType.FILE);
                }
                
                String key = url + "|" + f.optString("file_name", "");
                boolean isInVersionRange = fileNeedsApply.containsKey(key);
                
                File dest = new File(downloadPath, fileName);
                boolean needDownload = false;

                // Check metadata first to see if file is already tracked
                if (modMetadata.isFileInstalledAndMatches(fileName, expectedHash)) {
                    if (dest.exists()) {
                        // File is in manifest with matching hash and exists on disk
                        // FIXED: Check if we're upgrading and overwrite=true ONLY if hash differs
                        if (overwrite && isInVersionRange && upgrading && expectedHash != null && !expectedHash.trim().isEmpty()) {
                            // Even though manifest matches, we're upgrading - verify hash against remote
                            try {
                                String actual = HashUtils.sha256Hex(dest);
                                if (!FileUtils.hashEquals(expectedHash, actual)) {
                                    gui.show("File hash changed in upgrade (overwrite=true); will update: " + dest.getPath());
                                    needDownload = true;
                                } else {
                                    gui.show("[ModUpdater] File OK (manifest + hash match): " + dest.getPath());
                                }
                            } catch (Exception ex) {
                                gui.show("Error verifying file hash: " + ex.getMessage() + "; will redownload: " + dest.getPath());
                                needDownload = true;
                            }
                        } else {
                            gui.show("[ModUpdater] File OK (manifest): " + dest.getPath());
                        }
                    } else {
                        // File in manifest but missing on disk - need to redownload
                        gui.show("File in manifest but missing on disk; will download: " + dest.getPath());
                        needDownload = true;
                    }
                } else if (dest.exists()) {
                    // File exists but not in manifest or hash doesn't match - check hash
                    if (expectedHash != null && !expectedHash.trim().isEmpty()) {
                        try {
                            String actual = HashUtils.sha256Hex(dest);
                            if (!FileUtils.hashEquals(expectedHash, actual)) {
                                gui.show("File hash mismatch (expected: " + expectedHash.substring(0, Math.min(8, expectedHash.length())) + "..., got: " + actual.substring(0, Math.min(8, actual.length())) + "...); will repair: " + dest.getPath());
                                needDownload = true;
                            } else {
                                gui.show("File OK (hash verified): " + dest.getPath());
                                // Add to manifest for future runs
                                modMetadata.recordFile(fileName, actual, url, downloadPath);
                            }
                        } catch (Exception ex) {
                            gui.show("Error verifying file hash: " + ex.getMessage() + "; will redownload: " + dest.getPath());
                            needDownload = true;
                        }
                    } else if (!overwrite) {
                        gui.show("Existing file preserved (overwrite=false): " + dest.getPath());
                        // Add to manifest even without hash
                        try {
                            String actual = HashUtils.sha256Hex(dest);
                            modMetadata.recordFile(fileName, actual, url, downloadPath);
                        } catch (Exception ex) {
                            modMetadata.recordFile(fileName, "", url, downloadPath);
                        }
                    } else {
                        // File exists, no hash to verify, treat as OK and add to manifest
                        gui.show("File exists (no hash to verify): " + dest.getPath());
                        // Add to manifest for future runs
                        try {
                            String actual = HashUtils.sha256Hex(dest);
                            modMetadata.recordFile(fileName, actual, url, downloadPath);
                        } catch (Exception ex) {
                            modMetadata.recordFile(fileName, "", url, downloadPath);
                        }
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

                    gui.show("Starting download: " + url);
                    gui.show("Downloading to temporary file: " + tmp.getName());
                    boolean ok = FileUtils.downloadWithVerification(url, tmp, expectedHash, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        gui.show("ERROR: Failed to download file after " + maxRetries + " attempts: " + url);
                        throw new RuntimeException("Failed to download/verify file: " + url);
                    }
                    gui.show("Download completed successfully");

                    // backup and delete existing dest if needed
                    if (dest.exists() && overwrite) {
                        gui.show("Backing up existing file: " + dest.getPath());
                        FileUtils.backupPathTo(dest, backupRoot);
                        FileUtils.deleteSilently(dest, gui);
                        gui.show("Old file backed up and removed");
                    } else if (dest.exists() && !overwrite) {
                        gui.show("Warning: File exists but overwrite=false; skipping: " + dest.getPath());
                    }

                    // atomic move tmp -> dest
                    gui.show("Installing file: " + fileName + " to " + downloadPath);
                    FileUtils.atomicMoveWithRetries(tmp, dest, 5, 200);
                    gui.show("Successfully installed file: " + dest.getPath());
                    
                    // Record in manifest with actual hash
                    String actualHash = expectedHash;
                    if (actualHash == null || actualHash.trim().isEmpty()) {
                        try {
                            actualHash = HashUtils.sha256Hex(dest);
                        } catch (Exception ex) {
                            actualHash = "";
                        }
                    }
                    modMetadata.recordFile(fileName, actualHash, url, downloadPath);
                }

                if (extract && dest.getName().toLowerCase().endsWith(".zip")) {
                    gui.show("Extracting " + dest.getPath());
                    FileUtils.unzip(dest, new File(downloadPath), overwrite, gui);
                }

                completed++;
                updateProgress(completed, totalTasks);
            }

            // 3) Mods phase with metadata tracking
            gui.show("=== Starting Mods Phase ===");
            gui.show("Expected mods from mods.json: " + modHandleMap.size());

            // Step 1: Clean up mods folder - remove ONLY ModUpdater-managed files that are outdated
            // CHANGED: Only delete files that are in metadata AND not in current mods.json
            // This prevents deletion of user-added mods or mods from other sources
            gui.show("--- Cleanup Phase: Scanning for outdated mods ---");
            Set<String> validNumberIds = modHandleMap.keySet();
            Set<String> installLocations = new HashSet<>();
            for (JSONObject m : modHandleMap.values()) {
                installLocations.add(m.optString("installLocation", "mods"));
            }
            gui.show("Scanning " + installLocations.size() + " install location(s)...");
            
            for (String installLocation : installLocations) {
                gui.show("Scanning directory: " + installLocation);
                File targetDir = new File(installLocation);
                if (!targetDir.exists() || !targetDir.isDirectory()) continue;
                
                File[] filesInDir = targetDir.listFiles();
                if (filesInDir == null) continue;
                
                for (File file : filesInDir) {
                    if (!file.isFile()) continue;
                    if (file.getName().endsWith(".tmp")) continue; // skip temp files
                    
                    // Use fileResolver to determine ownership
                    String belongsToNumberId = fileResolver.getOwnerNumberId(file);
                    boolean isTrackedByModUpdater = (belongsToNumberId != null);
                    boolean isStillValid = isTrackedByModUpdater && validNumberIds.contains(belongsToNumberId);
                    
                    // ONLY delete if:
                    // 1. File is tracked by ModUpdater (in metadata or has numberId prefix from metadata)
                    // 2. AND the mod is no longer in mods.json OR will be replaced by a different version
                    if (isTrackedByModUpdater && !isStillValid) {
                        gui.show("Removing outdated ModUpdater-managed mod: " + file.getPath());
                        FileUtils.backupPathTo(file, backupRoot);
                        
                        // Try to delete the file - track it if deletion fails
                        if (!file.delete()) {
                            gui.show("File locked - tracking for deletion after restart");
                            pendingDeletes.add(file);
                        }
                        
                        if (belongsToNumberId != null) {
                            modMetadata.removeMod(belongsToNumberId);
                        }
                    } else if (!isTrackedByModUpdater) {
                        // File is not tracked by ModUpdater - leave it alone
                        gui.show("Skipping unmanaged file (not installed by ModUpdater): " + file.getName());
                    } else if (isStillValid) {
                        // File is tracked and still valid - check if it was renamed
                        ModMetadata.ModEntry entry = modMetadata.getMod(belongsToNumberId);
                        if (entry != null && entry.fileName != null && !entry.fileName.equals(file.getName())) {
                            gui.show("Detected renamed mod: " + entry.fileName + " -> " + file.getName() + " (numberId=" + belongsToNumberId + ")");
                            entry.fileName = file.getName();
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

                // Determine final filename with extension inference
                // Priority: file_name from config, or sourceFilename, or displayName
                String finalName;
                if (!fileName.isEmpty()) {
                    // Use custom file_name from config, infer extension if missing
                    finalName = filenameResolver.resolve(fileName, downloadUrl, null, FilenameResolver.ArtifactType.MOD);
                } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                    // Use filename from source (URL/API), infer extension if missing
                    finalName = filenameResolver.resolve(filenameFromSource, downloadUrl, null, FilenameResolver.ArtifactType.MOD);
                } else if (!displayName.isEmpty()) {
                    // Fallback to display name with extension inference
                    finalName = filenameResolver.resolve(displayName, downloadUrl, null, FilenameResolver.ArtifactType.MOD);
                } else {
                    // Last resort fallback
                    finalName = "mod_" + numberId + ".jar";
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
                                try {
                                    String actual = HashUtils.sha256Hex(existingFile);
                                    if (FileUtils.hashEquals(expectedHash, actual)) {
                                        gui.show("Mod OK (via metadata): " + existingFile.getPath());
                                        // Rename if needed
                                        if (!existingFile.getName().equals(finalName)) {
                                            gui.show("Renaming mod from: " + existingFile.getName() + " to: " + finalName);
                                            FileUtils.backupPathTo(existingFile, backupRoot);
                                            
                                            // Try rename
                                            try {
                                                FileUtils.atomicMoveWithRetries(existingFile, target, 3, 100);
                                                gui.show("Successfully renamed mod to: " + target.getPath());
                                                modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                                existingFile = target;
                                            } catch (IOException e) {
                                                gui.show("RENAME FAILED: " + e.getMessage());
                                                gui.show("File may be locked - tracking for deletion after restart");
                                                pendingDeletes.add(existingFile);
                                                gui.show("Continuing with existing file (valid): " + existingFile.getPath());
                                                // Update metadata with current filename so we know it's the same file
                                                modMetadata.recordMod(numberId, existingFile.getName(), expectedHash, source);
                                                modMetadata.save();
                                            }
                                        }
                                    } else {
                                        gui.show("Mod hash mismatch (expected: " + expectedHash.substring(0, Math.min(8, expectedHash.length())) + "..., got: " + actual.substring(0, Math.min(8, actual.length())) + "...); will redownload: " + existingFile.getPath());
                                        needDownload = true;
                                    }
                                } catch (Exception ex) {
                                    gui.show("Error verifying mod hash: " + ex.getMessage() + "; will redownload: " + existingFile.getPath());
                                    needDownload = true;
                                }
                            } else {
                                gui.show("Mod OK (no hash check): " + existingFile.getPath());
                            }
                        } else {
                            // IMPROVED: File from metadata doesn't exist - maybe user renamed it?
                            // Try to find it by hash before downloading
                            if (!expectedHash.isEmpty()) {
                                gui.show("Mod in metadata but file missing: " + installedFileName + "; scanning for renamed file...");
                                File renamedFile = fileResolver.findFileByHash(targetDir, expectedHash);
                                if (renamedFile != null) {
                                    gui.show("Found renamed mod by hash: " + renamedFile.getName());
                                    existingFile = renamedFile;
                                    // Update metadata with new filename
                                    modMetadata.recordMod(numberId, renamedFile.getName(), expectedHash, source);
                                    // Rename to expected name if different
                                    if (!renamedFile.getName().equals(finalName)) {
                                        gui.show("Renaming mod from: " + renamedFile.getName() + " to: " + finalName);
                                        FileUtils.backupPathTo(renamedFile, backupRoot);
                                        
                                        // Try rename
                                        try {
                                            FileUtils.atomicMoveWithRetries(renamedFile, target, 3, 100);
                                            gui.show("Successfully renamed mod to: " + target.getPath());
                                            modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                        } catch (IOException e) {
                                            gui.show("RENAME FAILED: " + e.getMessage());
                                            gui.show("File may be locked - tracking for deletion after restart");
                                            pendingDeletes.add(renamedFile);
                                            gui.show("Continuing with existing file (valid): " + renamedFile.getPath());
                                            // Metadata already recorded with renamedFile.getName() above, save it
                                            modMetadata.save();
                                        }
                                    }
                                } else {
                                    gui.show("Could not find renamed file by hash scan; will redownload: " + installedFileName);
                                    needDownload = true;
                                }
                            } else {
                                gui.show("Mod in metadata but file missing (no hash to search); will redownload: " + installedFileName);
                                needDownload = true;
                            }
                        }
                    } else {
                        needDownload = true;
                    }
                } else {
                    // Not in metadata or doesn't match
                    // First check by numberId prefix (backwards compatibility with old installations)
                    List<File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
                    if (!existing.isEmpty()) {
                        existingFile = existing.get(0);
                        if (!expectedHash.isEmpty()) {
                            try {
                                String actual = HashUtils.sha256Hex(existingFile);
                                if (FileUtils.hashEquals(expectedHash, actual)) {
                                    gui.show("Mod OK (via prefix): " + existingFile.getPath());
                                    // Update metadata
                                    modMetadata.recordMod(numberId, existingFile.getName(), expectedHash, source);
                                    // Rename if needed
                                    if (!existingFile.getName().equals(finalName)) {
                                        gui.show("Renaming mod from: " + existingFile.getName() + " to: " + finalName);
                                        FileUtils.backupPathTo(existingFile, backupRoot);
                                        
                                        // Try rename
                                        try {
                                            FileUtils.atomicMoveWithRetries(existingFile, target, 3, 100);
                                            gui.show("Successfully renamed mod to: " + target.getPath());
                                            modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                        } catch (IOException e) {
                                            gui.show("RENAME FAILED: " + e.getMessage());
                                            gui.show("File may be locked - tracking for deletion after restart");
                                            pendingDeletes.add(existingFile);
                                            gui.show("Continuing with existing file (valid): " + existingFile.getPath());
                                            // Metadata already recorded with existingFile.getName() above, save it
                                            modMetadata.save();
                                        }
                                    }
                                } else {
                                    gui.show("Mod hash mismatch (expected: " + expectedHash.substring(0, Math.min(8, expectedHash.length())) + "..., got: " + actual.substring(0, Math.min(8, actual.length())) + "...); will redownload: " + existingFile.getPath());
                                    needDownload = true;
                                }
                            } catch (Exception ex) {
                                gui.show("Error verifying mod hash: " + ex.getMessage() + "; will redownload: " + existingFile.getPath());
                                needDownload = true;
                            }
                        } else {
                            gui.show("Mod present (no hash): " + existingFile.getPath());
                            modMetadata.recordMod(numberId, existingFile.getName(), "", source);
                        }
                    } else if (target.exists()) {
                        // File exists at target location but not tracked
                        if (!expectedHash.isEmpty()) {
                            try {
                                String actual = HashUtils.sha256Hex(target);
                                if (FileUtils.hashEquals(expectedHash, actual)) {
                                    gui.show("Mod OK at target location: " + target.getPath());
                                    modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                } else {
                                    gui.show("File at target has wrong hash (expected: " + expectedHash.substring(0, Math.min(8, expectedHash.length())) + "..., got: " + actual.substring(0, Math.min(8, actual.length())) + "...); will redownload: " + target.getPath());
                                    needDownload = true;
                                }
                            } catch (Exception ex) {
                                gui.show("Error verifying file hash: " + ex.getMessage() + "; will redownload: " + target.getPath());
                                needDownload = true;
                            }
                        } else {
                            gui.show("Mod present at target (no hash): " + target.getPath());
                            modMetadata.recordMod(numberId, finalName, "", source);
                        }
                    } else if (!expectedHash.isEmpty()) {
                        // IMPROVED: Scan directory for renamed mods by hash
                        // This handles the case where user renamed a mod file
                        gui.show("Mod not found by name; scanning directory for matching hash...");
                        
                        // Find files that shouldn't be considered (already tracked by other mods)
                        List<String> skipFileNames = new ArrayList<>();
                        for (ModMetadata.ModEntry entry : modMetadata.getAllMods()) {
                            if (entry.fileName != null && !entry.numberId.equals(numberId)) {
                                skipFileNames.add(entry.fileName);
                            }
                        }
                        
                        File renamedFile = fileResolver.findFileByHash(targetDir, expectedHash, 
                            skipFileNames.toArray(new String[0]));
                        
                        if (renamedFile != null) {
                            gui.show("Found renamed mod by hash: " + renamedFile.getName() + " -> will rename to: " + finalName);
                            existingFile = renamedFile;
                            // Record in metadata
                            modMetadata.recordMod(numberId, renamedFile.getName(), expectedHash, source);
                            // Rename to expected name
                            gui.show("Renaming mod from: " + renamedFile.getName() + " to: " + finalName);
                            FileUtils.backupPathTo(existingFile, backupRoot);
                            
                            // Try rename
                            try {
                                FileUtils.atomicMoveWithRetries(existingFile, target, 3, 100);
                                gui.show("Successfully renamed mod to: " + target.getPath());
                                modMetadata.recordMod(numberId, finalName, expectedHash, source);
                            } catch (IOException e) {
                                gui.show("RENAME FAILED: " + e.getMessage());
                                gui.show("File may be locked - tracking for deletion after restart");
                                pendingDeletes.add(existingFile);
                                gui.show("Continuing with existing file (valid): " + existingFile.getPath());
                                // Metadata already recorded with renamedFile.getName() above, save it
                                modMetadata.save();
                            }
                        } else {
                            gui.show("Mod missing after hash scan; will download: " + finalName);
                            needDownload = true;
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
                        gui.show("ERROR: No download URL available for mod numberId=" + numberId + " (displayName=" + displayName + "); skipping.");
                        completed++;
                        updateProgress(completed, totalTasks);
                        continue;
                    }
                    gui.show("Starting mod download: " + (displayName.isEmpty() ? finalName : displayName) + " (numberId=" + numberId + ")");
                    gui.show("Download URL: " + downloadUrl);
                    gui.show("Downloading to temporary file: " + tmp.getName());
                    boolean ok = FileUtils.downloadWithVerification(downloadUrl, tmp, expectedHash, gui, maxRetries);
                    if (!ok) {
                        if (tmp.exists()) tmp.delete();
                        gui.show("ERROR: Failed to download mod after " + maxRetries + " attempts: " + downloadUrl);
                        throw new RuntimeException("Failed to download/verify mod: " + downloadUrl);
                    }
                    gui.show("Download completed successfully");

                    // backup & delete existing files for this numberId
                    // FIXED: Use metadata to find old files, not just numberId prefix
                    List<File> existingFiles = findFilesForNumberIdViaMetadata(targetDir, numberId, modMetadata);
                    if (!existingFiles.isEmpty()) {
                        gui.show("Removing " + existingFiles.size() + " old version(s) of this mod...");
                    }
                    for (File f : existingFiles) {
                        try {
                            if (f.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                gui.show("Skipping deletion of file equal to download tmp: " + f.getPath());
                                continue;
                            }
                        } catch (Exception ex) {
                            gui.show("Warning: Error resolving path, skipping delete of: " + f.getPath());
                            continue;
                        }
                        gui.show("Removing old version: " + f.getPath());
                        FileUtils.backupPathTo(f, backupRoot);
                        
                        // Try to delete the file - track it if deletion fails
                        if (!f.delete()) {
                            gui.show("File locked - tracking for deletion after restart");
                            pendingDeletes.add(f);
                        }
                    }

                    // if target exists (with finalName) backup/delete
                    if (target.exists()) {
                        try {
                            if (!target.getCanonicalPath().equals(tmp.getCanonicalPath())) {
                                gui.show("Backing up existing target file: " + target.getPath());
                                FileUtils.backupPathTo(target, backupRoot);
                                
                                // Try to delete the file - track it if deletion fails
                                if (!target.delete()) {
                                    gui.show("File locked - tracking for deletion after restart");
                                    pendingDeletes.add(target);
                                }
                            } else {
                                gui.show("Warning: Target equals tmp (unexpected); skipping delete");
                            }
                        } catch (Exception ex) {
                            gui.show("Warning: Error resolving target path; skipping delete: " + target.getPath());
                        }
                    }

                    // move tmp -> final
                    gui.show("Installing mod: " + finalName + " to " + installLocation);
                    FileUtils.atomicMoveWithRetries(tmp, target, 5, 200);
                    gui.show("Successfully installed mod: " + target.getPath());
                    
                    // Update metadata with actual hash
                    String actualHash = expectedHash;
                    if (actualHash.isEmpty()) {
                        try {
                            actualHash = HashUtils.sha256Hex(target);
                            gui.show("Computed hash for installed mod: " + actualHash.substring(0, Math.min(8, actualHash.length())) + "...");
                        } catch (Exception ex) {
                            gui.show("Warning: Could not compute hash for installed mod: " + ex.getMessage());
                        }
                    }
                    modMetadata.recordMod(numberId, finalName, actualHash, source);
                    gui.show("Metadata updated for mod: " + finalName);
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
            
            // Check if there are any pending deletes (files that were locked)
            if (!pendingDeletes.isEmpty()) {
                gui.show("Some files could not be deleted and require a restart.");
                gui.close();  // Close the progress GUI before showing the restart dialog
                
                // Show restart required dialog
                com.ArfGg57.modupdater.ui.RestartRequiredDialog restartDialog = 
                    new com.ArfGg57.modupdater.ui.RestartRequiredDialog(pendingDeletes);
                restartDialog.showDialog();
                
                if (restartDialog.wasContinued()) {
                    // User clicked Continue - we need to crash the game but keep the updater running
                    System.out.println("[ModUpdater] User requested restart to complete updates.");
                    startDeletionThreadAndCrash();
                } else if (restartDialog.wasClosedWithoutContinue()) {
                    // User closed the dialog without clicking Continue - also crash to force restart
                    System.out.println("[ModUpdater] User closed restart dialog without clicking Continue.");
                    startDeletionThreadAndCrash();
                }
            }

        } catch (Exception e) {
            gui.show("Update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (gui != null && !configError) gui.close();
        }
    }

    /**
     * Start a deletion thread for pending files and crash the game to force restart.
     * This method will not return - it throws an Error to crash the game.
     */
    private void startDeletionThreadAndCrash() {
        System.out.println("[ModUpdater] Starting deletion thread for pending files...");
        
        // Start a non-daemon thread to delete the files after a short delay
        Thread deletionThread = new Thread(() -> {
            try {
                // Wait for the game to start shutting down
                Thread.sleep(DELETION_WAIT_MS);
                
                System.out.println("[ModUpdater] Attempting to delete " + pendingDeletes.size() + " pending file(s)...");
                int deletedCount = 0;
                for (File file : pendingDeletes) {
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            System.out.println("[ModUpdater] Deleted: " + file.getPath());
                            deletedCount++;
                        } else {
                            System.out.println("[ModUpdater] Still locked: " + file.getPath());
                        }
                    }
                }
                
                System.out.println("[ModUpdater] Deletion complete: " + deletedCount + " of " + pendingDeletes.size() + " files deleted.");
                
                // Keep the process alive for a few more seconds to ensure files are deleted
                Thread.sleep(DELETION_KEEP_ALIVE_MS);
                
                System.out.println("[ModUpdater] Deletion thread complete.");
                // Note: Don't call System.exit() as it's blocked by FMLSecurityManager
                // The game crash from the Error will terminate the process
            } catch (InterruptedException e) {
                System.err.println("[ModUpdater] Deletion thread interrupted: " + e.getMessage());
            }
        }, "ModUpdater-DeletionThread");
        deletionThread.setDaemon(false);  // Not a daemon - should keep running
        deletionThread.start();
        
        // Throw a custom Error to crash the game and force a restart
        // Using Error (not Exception) ensures it bypasses FML exception handlers
        // This is necessary because FMLSecurityManager blocks System.exit()
        System.err.println("[ModUpdater] Triggering game crash to apply updates...");
        throw new RestartRequiredError("[ModUpdater] Restart required to complete mod updates. Please restart the game.");
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
     * Find all files in the directory that belong to a given numberId.
     * Uses RenamedFileResolver for centralized hash-based detection.
     * @deprecated Use fileResolver.findAllFilesForMod() directly
     */
    private List<File> findFilesForNumberIdViaMetadata(File dir, String numberId, ModMetadata metadata) {
        // Create a temporary resolver for this operation
        RenamedFileResolver tempResolver = new RenamedFileResolver(metadata, null);
        File[] files = tempResolver.findAllFilesForMod(dir, numberId);
        return Arrays.asList(files);
    }
}
