package com.ArfGg57.modupdater.ui;

import com.ArfGg57.modupdater.core.UpdaterCore;
import com.ArfGg57.modupdater.deletion.DeletionProcessor;
import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.hash.RenamedFileResolver;
import com.ArfGg57.modupdater.metadata.ModMetadata;
import com.ArfGg57.modupdater.resolver.FilenameResolver;
import com.ArfGg57.modupdater.selfupdate.SelfUpdateCoordinator;
import com.ArfGg57.modupdater.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * ModConfirmationDialog
 *
 * - Combines mods + files into single "Files to Add" list (left column).
 * - Deletes remain as the right column.
 * - Enforces deduplication, pretty rendering, and checkCurrentVersion scanning for mods/files/deletes.
 *
 * Note: Keeps ModEntry class (used by UpdaterCore) for compatibility.
 */
public class ModConfirmationDialog {

    // --- UI Constants ---
    private static final Color COLOR_BG = new Color(34, 37, 45);
    private static final Color COLOR_LIST_BG = new Color(44, 47, 60);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT = new Color(0, 200, 100);
    private static final Color COLOR_ACCENT_HOVER = new Color(0, 220, 120);
    private static final Color COLOR_CANCEL = new Color(200, 50, 50);
    private static final Color COLOR_CANCEL_HOVER = new Color(220, 70, 70);
    private static final Color COLOR_SCROLLBAR_THUMB = new Color(80, 85, 100);
    private static final Color COLOR_DIVIDER = new Color(50, 55, 65);

    private static final int WINDOW_CORNER_RADIUS = 25;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_ENTRY_TITLE = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_ENTRY_SUB = new Font("Segoe UI", Font.PLAIN, 12);

    // Deletion key prefixes
    private static final String DELETE_KEY_FILE = "FILE: ";
    private static final String DELETE_KEY_FOLDER = "FOLDER: ";
    private static final String DELETE_KEY_MOD = "MOD: ";
    
    // Timing constants
    private static final int CHECKING_DIALOG_CLOSE_DELAY_MS = 500;

    private JDialog dialog;

    // unified left-hand list of things to add (mods + files)
    private JList<UnifiedEntry> addList;
    private DefaultListModel<UnifiedEntry> addListModel;

    // right-hand deletes list (strings like "FILE: path" or "FOLDER: path")
    private JList<String> deleteList;
    private DefaultListModel<String> deleteListModel;

    private Point initialClick;
    private boolean agreed = false;

    // Incoming lists (kept for compatibility)
    private final List<ModEntry> modsToDownload;
    private final List<String> filesToApply;
    private final List<String> filesToDelete;
    private final UpdaterCore core; // may be null for test mode
    
    // Filename resolver for consistent filename derivation with UpdaterCore
    private final FilenameResolver filenameResolver;
    
    // Metadata and resolver for hash-based rename detection
    private ModMetadata modMetadata;
    private RenamedFileResolver renamedFileResolver;
    
    // Self-update info (stored for use by UpdaterCore)
    private SelfUpdateCoordinator.SelfUpdateInfo selfUpdateInfo;

    // Mapping to hold URL/source data for file entries (keyed by "FILE: <path>")
    // This allows showing a URL/source for file items when we learned it from files.json
    private final Map<String, String> fileEntryToSource = new HashMap<>();
    // Mapping to hold display_name for file entries (keyed by "FILE: <path>")
    private final Map<String, String> fileEntryToDisplayName = new HashMap<>();

    // -----------------------------
    // Constructor for Minecraft (UpdaterCore + prebuilt lists)
    // -----------------------------
    public ModConfirmationDialog(UpdaterCore core, List<ModEntry> mods, List<String> files, List<String> deletes) {
        this.core = core;
        this.modsToDownload = mods == null ? new ArrayList<>() : new ArrayList<>(mods);
        this.filesToApply = files == null ? new ArrayList<>() : new ArrayList<>(files);
        this.filesToDelete = deletes == null ? new ArrayList<>() : new ArrayList<>(deletes);
        
        // Initialize FilenameResolver with no logging for dialog (keeps UI clean)
        this.filenameResolver = new FilenameResolver(null, false);

        // Enrich lists with checkCurrentVersion scanning (mods, files, deletes)
        if (this.core != null) {
            try {
                enrichListsWithCheckCurrentVersion();
            } catch (Exception e) {
                System.err.println("[ModConfirmationDialog] Warning: enrichListsWithCheckCurrentVersion failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        setupDialog();
    }

    // -----------------------------
    // Constructor for testing with URLs
    // -----------------------------
    public ModConfirmationDialog(String modsJsonUrl, String filesJsonUrl, String deletesJsonUrl) {
        this.core = null;
        this.modsToDownload = loadMods(modsJsonUrl);
        this.filesToApply = loadFiles(filesJsonUrl);
        this.filesToDelete = loadDeletes(deletesJsonUrl);
        
        // Initialize FilenameResolver with no logging for dialog (keeps UI clean)
        this.filenameResolver = new FilenameResolver(null, false);
        
        // No remote enrichment in test constructor
        setupDialog();
    }

    // -----------------------------
    // Unified entry: used for the combined "Files to Add" list
    // -----------------------------
    private static class UnifiedEntry {
        enum Type { MOD, FILE }
        final Type type;
        final String displayName;   // primary line (prefer nice name)
        final String secondary;     // source text (url / curseforge info / path)
        final String key;           // unique key for dedupe (numberId|fileName|path|url)
        final ModEntry originMod;   // optional, if this was created from a ModEntry

        UnifiedEntry(Type type, String displayName, String secondary, String key, ModEntry originMod) {
            this.type = type;
            this.displayName = displayName;
            this.secondary = secondary;
            this.key = key;
            this.originMod = originMod;
        }
    }

    // -----------------------------
    // Enrichment: checkCurrentVersion -> scan mods/files/deletes and add proposals
    // -----------------------------
    private void enrichListsWithCheckCurrentVersion() {
        CheckingUpdatesDialog checkingDialog = null;
        try {
            // Ensure configs before any reads
            ensureLocalConfigsForDialog();
            // Show checking dialog
            checkingDialog = new CheckingUpdatesDialog();
            checkingDialog.show();
            checkingDialog.updateStatus("Loading metadata...");
            
            // Initialize ModMetadata for hash-based detection
            String modMetadataPath = "config/ModUpdater/mod_metadata.json";
            try {
                modMetadata = new ModMetadata(modMetadataPath);
                renamedFileResolver = new RenamedFileResolver(modMetadata, new RenamedFileResolver.Logger() {
                    public void log(String message) {
                        System.out.println("[ModConfirmationDialog] " + message);
                    }
                });
                System.out.println("[ModConfirmationDialog] Loaded metadata with " + modMetadata.getAllMods().size() + " mod(s)");
            } catch (Exception e) {
                System.out.println("[ModConfirmationDialog] Warning: Could not load metadata: " + e.getMessage());
                // Continue without metadata - will work in degraded mode
            }
            
            // ---------------------
            // SELF-UPDATE CHECK: Check for ModUpdater updates first
            // ---------------------
            checkingDialog.updateStatus("Checking for ModUpdater updates...");
            try {
                checkForSelfUpdate();
            } catch (Exception e) {
                System.out.println("[ModConfirmationDialog] Self-update check failed (non-critical): " + e.getMessage());
                // Continue - self-update is not critical
            }
            
            // Read local config to find remote_config_url
            JSONObject localConfig = FileUtils.readJson("config/ModUpdater/config.json");
            String remoteConfigUrl = localConfig != null ? localConfig.optString("remote_config_url", "").trim() : "";
            if (remoteConfigUrl.isEmpty()) {
                System.out.println("[ModConfirmationDialog] remote_config_url empty; skipping checkCurrentVersion enrichment.");
                if (checkingDialog != null) checkingDialog.close();
                return;
            }

            checkingDialog.updateStatus("Fetching remote configuration...");
            JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            if (remoteConfig == null) {
                System.out.println("[ModConfirmationDialog] remote config unreadable; skipping checkCurrentVersion enrichment.");
                if (checkingDialog != null) checkingDialog.close();
                return;
            }

            boolean checkCurrent = remoteConfig.optBoolean("checkCurrentVersion", false);
            if (!checkCurrent) {
                if (checkingDialog != null) checkingDialog.close();
                return;
            }

            String baseUrl = remoteConfig.optString("configsBaseUrl", "");
            String filesJsonName = remoteConfig.optString("filesJson", "files.json");
            String modsJsonName = remoteConfig.optString("modsJson", "mods.json");
            String deletesJsonName = remoteConfig.optString("deletesJson", "deletes.json");

            // Build dedupe sets from existing lists
            Set<String> addKeys = new HashSet<>();
            // For mods: key by numberId if present else displayName/fileName
            for (ModEntry me : modsToDownload) {
                String k = makeModKey(me);
                if (k != null) addKeys.add(k);
            }
            // For files: they come as strings "FILE: <path>" or "FOLDER: <path>"
            for (String f : filesToApply) {
                addKeys.add(f);
            }

            Set<String> deleteKeys = new HashSet<>(filesToDelete);

            // ---------------------
            // FILES: propose missing or flagged files (no hash checks)
            // ---------------------
            checkingDialog.updateStatus("Checking files...");
            try {
                String filesUrl = FileUtils.joinUrl(baseUrl, filesJsonName);
                JSONObject filesRoot = FileUtils.readJsonFromUrl(filesUrl);
                if (filesRoot != null) {
                    JSONArray allFiles = filesRoot.optJSONArray("files");
                    if (allFiles != null) {
                        for (int i = 0; i < allFiles.length(); i++) {
                            JSONObject f = allFiles.getJSONObject(i);
                            String url = f.optString("url", "").trim();
                            String downloadPath = f.optString("downloadPath", "config/");
                            // use file_name per new schema
                            String displayName = f.optString("display_name", "").trim();
                            String fileName = f.optString("file_name", "").trim();

                            // make safe fallbacks for display name only (do not modify fileName)
                            if (displayName.isEmpty()) {
                                if (!fileName.isEmpty()) displayName = fileName;
                                else if (!url.isEmpty()) displayName = FileUtils.extractFileNameFromUrl(url);
                                else displayName = "Unknown File";
                            }
                            // NOTE: Do NOT set fileName from displayName - let it remain empty
                            // so that the filename resolution logic below will extract from URL

                            // FIXED: Use FilenameResolver to derive filename consistently with UpdaterCore
                            // This ensures we check for the correct filename on disk
                            String finalName;
                            if (!fileName.isEmpty()) {
                                // Use custom file_name from config, infer extension if missing
                                finalName = filenameResolver.resolve(fileName, url, null, FilenameResolver.ArtifactType.FILE);
                            } else {
                                // Extract from URL with extension inference
                                String extracted = FileUtils.extractFileNameFromUrl(url);
                                finalName = filenameResolver.resolve(extracted, url, null, FilenameResolver.ArtifactType.FILE);
                            }

                            String entry = "FILE: " + downloadPath + finalName;

                            if (addKeys.contains(entry)) continue;

                            // Existence check only (no hash)
                            java.io.File dest = new java.io.File(downloadPath, finalName);
                            boolean needs = false;
                            if (!dest.exists()) {
                                needs = true;
                                System.out.println("[ModConfirmationDialog] Missing file detected (will propose): " + dest.getPath());
                            } else {
                                // If dest exists we do not compare hash here (user asked to avoid hash).
                                // We will propose replacement only if the JSON explicitly requests overwrite
                                // or other conditions, which are handled during the update execution.
                                // So do not propose by default if file exists.
                            }

                            if (needs) {
                                filesToApply.add(entry);
                                addKeys.add(entry);
                                // remember URL/source and pretty display name for nice display in the unified list
                                if (!url.isEmpty()) fileEntryToSource.put(entry, url);
                                if (!displayName.isEmpty()) fileEntryToDisplayName.put(entry, displayName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ModConfirmationDialog] Error while checking files.json for missing files: " + e.getMessage());
                e.printStackTrace();
            }

            // ---------------------
            // MODS: propose missing mods or filename-mismatched mods (no hash checks)
            // ---------------------
            checkingDialog.updateStatus("Checking mods...");
            try {
                String modsUrl = FileUtils.joinUrl(baseUrl, modsJsonName);
                JSONArray modsArr = FileUtils.readJsonArrayFromUrl(modsUrl);
                if (modsArr != null) {
                    for (int i = 0; i < modsArr.length(); i++) {
                        JSONObject mod = modsArr.getJSONObject(i);
                        String jsonDisplay = mod.optString("display_name", "").trim();
                        String jsonFileName = mod.optString("file_name", "").trim();
                        JSONObject source = mod.optJSONObject("source");
                        String installLocation = mod.optString("installLocation", "mods");
                        String numberId = mod.optString("numberId", "").trim();

                        // resolve filenameFromSource and downloadUrl from the declared source.
                        String filenameFromSource = null;
                        String downloadUrl = null;
                        String srcDisplay = "";

                        if (source != null) {
                            String type = source.optString("type", "url");
                            if ("url".equals(type)) {
                                downloadUrl = source.optString("url", null);
                                if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                                    filenameFromSource = FileUtils.extractFileNameFromUrl(downloadUrl);
                                    srcDisplay = downloadUrl;
                                }
                            } else if ("curseforge".equals(type)) {
                                int projectId = source.optInt("projectId", -1);
                                long fileId = source.optLong("fileId", -1);
                                if (projectId > 0 && fileId > 0) {
                                    srcDisplay = "CurseForge: project " + projectId + " / file " + fileId;
                                    try {
                                        String apiUrl = String.format(FileUtils.CF_PROXY_BASE_URL + "/mods/%s/files/%s", projectId, fileId);
                                        JSONObject fileData = FileUtils.readJsonFromUrl(apiUrl, FileUtils.CF_API_KEY);
                                        JSONObject data = fileData.optJSONObject("data");
                                        if (data == null) data = fileData;
                                        if (data != null) {
                                            if (data.has("downloadUrl")) downloadUrl = data.optString("downloadUrl", downloadUrl);
                                            if (data.has("fileName")) filenameFromSource = data.optString("fileName", filenameFromSource);
                                        }
                                    } catch (Exception eks) {
                                        System.out.println("[ModConfirmationDialog] CF metadata fetch failed for project " + projectId + " file " + fileId + " : " + eks.getMessage());
                                    }
                                } else {
                                    srcDisplay = "CurseForge";
                                }
                            } else if ("modrinth".equals(type)) {
                                String versionId = source.optString("versionId", "").trim();
                                String projectSlug = source.optString("projectSlug", "").trim();
                                if (!versionId.isEmpty()) {
                                    try {
                                        String apiUrl = "https://api.modrinth.com/v2/version/" + versionId;
                                        JSONObject versionData = FileUtils.readJsonFromUrl(apiUrl);
                                        if (versionData != null) {
                                            JSONArray filesArr2 = versionData.optJSONArray("files");
                                            if (filesArr2 != null && filesArr2.length() > 0) {
                                                JSONObject fobj = filesArr2.getJSONObject(0);
                                                filenameFromSource = fobj.optString("filename", filenameFromSource);
                                                downloadUrl = fobj.optString("url", downloadUrl);
                                            }
                                        }
                                        srcDisplay = "Modrinth: version " + versionId;
                                    } catch (Exception exm) {
                                        System.out.println("[ModConfirmationDialog] Modrinth version fetch failed for " + versionId + " : " + exm.getMessage());
                                    }
                                } else if (!projectSlug.isEmpty()) {
                                    try {
                                        String apiUrl = "https://api.modrinth.com/v2/project/" + projectSlug;
                                        JSONObject proj = FileUtils.readJsonFromUrl(apiUrl);
                                        srcDisplay = "Modrinth: project " + projectSlug;
                                    } catch (Exception exm2) {
                                        System.out.println("[ModConfirmationDialog] Modrinth project fetch failed for " + projectSlug + " : " + exm2.getMessage());
                                    }
                                } else {
                                    srcDisplay = "Modrinth";
                                }
                            } else {
                                srcDisplay = source.optString("type", "unknown");
                            }
                        }

                        // Display name priority: json display_name -> API file name -> "Unnamed File"
                        String displayName;
                        if (jsonDisplay != null && !jsonDisplay.isEmpty()) {
                            displayName = jsonDisplay;
                        } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                            displayName = filenameFromSource;
                        } else {
                            displayName = "Unnamed File";
                        }

                        // FIXED: Determine final filename using FilenameResolver to match UpdaterCore behavior
                        // Priority: file_name from config, or sourceFilename, or displayName
                        String finalName;
                        if (!jsonFileName.isEmpty()) {
                            // Use custom file_name from config, infer extension if missing
                            finalName = filenameResolver.resolve(jsonFileName, downloadUrl, null, FilenameResolver.ArtifactType.MOD);
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

                        // build a key for dedupe: prefer displayName + finalName
                        String modKey = "MODNAME:" + displayName + "|" + finalName;
                        if (addKeys.contains(modKey)) continue;

                        // Get expected hash from config
                        String expectedHash = mod.optString("hash", "").trim();
                        
                        // NEW: Smart file detection with hash-based rename handling
                        java.io.File targetDir = new java.io.File(installLocation);
                        java.io.File targetFile = new java.io.File(targetDir, finalName);
                        boolean needs = false;
                        
                        // Check if mod is already correctly installed by hash
                        if (renamedFileResolver != null && modMetadata != null && !expectedHash.isEmpty()) {
                            // Use metadata to check if mod is installed
                            if (modMetadata.isModInstalledAndMatches(numberId, source, expectedHash)) {
                                // Mod is in metadata with matching hash
                                String installedFileName = modMetadata.findInstalledFile(numberId);
                                if (installedFileName != null) {
                                    java.io.File installedFile = new java.io.File(targetDir, installedFileName);
                                    if (installedFile.exists()) {
                                        // File exists with correct hash
                                        if (!installedFileName.equals(finalName)) {
                                            // File was renamed by user - try to rename it back silently
                                            System.out.println("[ModConfirmationDialog] Detected renamed mod: " + installedFileName + " (expected: " + finalName + ")");
                                            System.out.println("[ModConfirmationDialog] Attempting silent rename...");
                                            boolean renamed = installedFile.renameTo(targetFile);
                                            if (renamed) {
                                                System.out.println("[ModConfirmationDialog] Successfully renamed mod back to: " + finalName);
                                                // Update metadata with new filename
                                                modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                                modMetadata.save();
                                            } else {
                                                System.out.println("[ModConfirmationDialog] Failed to rename (file may be locked); keeping existing file with correct hash");
                                                // Do NOT set needs = true - the file exists with correct hash, just wrong name
                                                // The existing file will continue to work
                                            }
                                        }
                                        // else: file has correct name, no action needed
                                    } else {
                                        // File from metadata doesn't exist - scan for renamed file by hash
                                        System.out.println("[ModConfirmationDialog] Mod file missing from metadata location, scanning by hash...");
                                        java.io.File renamedFile = renamedFileResolver.findFileByHash(targetDir, expectedHash);
                                        if (renamedFile != null) {
                                            System.out.println("[ModConfirmationDialog] Found renamed mod by hash: " + renamedFile.getName());
                                            System.out.println("[ModConfirmationDialog] Attempting silent rename to: " + finalName);
                                            boolean renamed = renamedFile.renameTo(targetFile);
                                            if (renamed) {
                                                System.out.println("[ModConfirmationDialog] Successfully renamed mod to: " + finalName);
                                                modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                                modMetadata.save();
                                            } else {
                                                System.out.println("[ModConfirmationDialog] Failed to rename; keeping existing file with correct hash");
                                                // Do NOT set needs = true - the file exists with correct hash, just wrong name
                                                // The existing file will continue to work
                                            }
                                        } else {
                                            // File not found by hash either - need to download
                                            needs = true;
                                        }
                                    }
                                } else {
                                    needs = true;
                                }
                            } else {
                                // Not in metadata or doesn't match - check filesystem by hash
                                System.out.println("[ModConfirmationDialog] Mod not in metadata, checking filesystem by hash...");
                                java.io.File foundFile = renamedFileResolver.findFileByHash(targetDir, expectedHash);
                                if (foundFile != null) {
                                    System.out.println("[ModConfirmationDialog] Found mod by hash: " + foundFile.getName());
                                    if (!foundFile.getName().equals(finalName)) {
                                        System.out.println("[ModConfirmationDialog] Attempting silent rename to: " + finalName);
                                        boolean renamed = foundFile.renameTo(targetFile);
                                        if (renamed) {
                                            System.out.println("[ModConfirmationDialog] Successfully renamed mod to: " + finalName);
                                            modMetadata.recordMod(numberId, finalName, expectedHash, source);
                                            modMetadata.save();
                                        } else {
                                            System.out.println("[ModConfirmationDialog] Failed to rename; keeping existing file with correct hash");
                                            // Do NOT set needs = true - the file exists with correct hash, just wrong name
                                            // The existing file will continue to work
                                        }
                                    }
                                    // else: file already has correct name
                                } else if (!targetFile.exists()) {
                                    // File not found by hash and target doesn't exist
                                    needs = true;
                                } else {
                                    // Target file exists but hash doesn't match - verify it
                                    try {
                                        String actualHash = HashUtils.sha256Hex(targetFile);
                                        if (!FileUtils.hashEquals(expectedHash, actualHash)) {
                                            System.out.println("[ModConfirmationDialog] File exists but hash mismatch; will propose download");
                                            needs = true;
                                        }
                                    } catch (Exception ex) {
                                        System.out.println("[ModConfirmationDialog] Error checking hash; will propose download: " + ex.getMessage());
                                        needs = true;
                                    }
                                }
                            }
                        } else {
                            // Fallback: no hash available or metadata not loaded - use legacy filename checking
                            List<java.io.File> existing = FileUtils.findFilesForNumberId(targetDir, numberId);
                            if (existing.isEmpty()) {
                                // nothing found by numberId; check for file with finalName
                                if (!targetFile.exists()) needs = true;
                            } else {
                                // Without hash: compare filename suffix (the part after numberId-)
                                boolean matches = FileUtils.fileNameSuffixMatches(existing.get(0), numberId, finalName);
                                if (!matches) {
                                    needs = true;
                                    System.out.println("[ModConfirmationDialog] Mod filename mismatch; will propose re-download: " + existing.get(0).getPath());
                                }
                            }
                        }

                        if (needs) {
                            // create ModEntry so existing UpdaterCore code remains compatible (if used)
                            ModEntry me = new ModEntry(displayName, downloadUrl, finalName, srcDisplay, mod.optString("numberId", ""), installLocation);
                            modsToDownload.add(me);
                            addKeys.add(modKey);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ModConfirmationDialog] Error while checking mods.json for missing mods: " + e.getMessage());
                e.printStackTrace();
            }

            // ---------------------
            // DETECT MODS TO BE DELETED: scan for mods that will be removed during update
            // ---------------------
            checkingDialog.updateStatus("Detecting outdated mods...");
            try {
                String modsUrl = FileUtils.joinUrl(baseUrl, modsJsonName);
                JSONArray modsArr = FileUtils.readJsonArrayFromUrl(modsUrl);
                if (modsArr != null && modMetadata != null && renamedFileResolver != null) {
                    // Build set of valid numberIds from mods.json
                    Set<String> validNumberIds = new HashSet<>();
                    Set<String> installLocations = new HashSet<>();
                    for (int i = 0; i < modsArr.length(); i++) {
                        JSONObject mod = modsArr.getJSONObject(i);
                        String numberId = mod.optString("numberId", "").trim();
                        String installLocation = mod.optString("installLocation", "mods");
                        if (!numberId.isEmpty()) {
                            validNumberIds.add(numberId);
                        }
                        installLocations.add(installLocation);
                    }
                    
                    System.out.println("[ModConfirmationDialog] Scanning for mods to be deleted (valid mods: " + validNumberIds.size() + ")");
                    
                    // Scan each install location for mods that will be deleted
                    for (String installLocation : installLocations) {
                        java.io.File targetDir = new java.io.File(installLocation);
                        if (!targetDir.exists() || !targetDir.isDirectory()) continue;
                        
                        java.io.File[] filesInDir = targetDir.listFiles();
                        if (filesInDir == null) continue;
                        
                        for (java.io.File file : filesInDir) {
                            if (!file.isFile()) continue;
                            if (file.getName().endsWith(".tmp")) continue;
                            
                            // Check if this file is owned by ModUpdater
                            String belongsToNumberId = renamedFileResolver.getOwnerNumberId(file);
                            boolean isTrackedByModUpdater = (belongsToNumberId != null);
                            boolean isStillValid = isTrackedByModUpdater && validNumberIds.contains(belongsToNumberId);
                            
                            // If tracked by ModUpdater but not in the valid list, it will be deleted
                            if (isTrackedByModUpdater && !isStillValid) {
                                String deleteKey = DELETE_KEY_MOD + file.getPath() + " (outdated version)";
                                if (!deleteKeys.contains(deleteKey)) {
                                    filesToDelete.add(deleteKey);
                                    deleteKeys.add(deleteKey);
                                    System.out.println("[ModConfirmationDialog] Detected mod to be deleted: " + file.getName() + " (numberId=" + belongsToNumberId + ")");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ModConfirmationDialog] Error while detecting mods to delete: " + e.getMessage());
                e.printStackTrace();
            }


            // ---------------------
            // DELETES: propose deletes if listed path/folder exists locally
            // Uses DeletionProcessor to handle new deletes.json format with version filtering
            // ---------------------
            checkingDialog.updateStatus("Checking for files to delete...");
            try {
                String deletesUrl = FileUtils.joinUrl(baseUrl, deletesJsonName);
                JSONObject deletesRoot = FileUtils.readJsonFromUrl(deletesUrl);
                if (deletesRoot != null) {
                    // Read local version to determine what deletions apply
                    String appliedVersion = "0.0.0";
                    try {
                        String versionPath = "config/ModUpdater/modpack_version.json";
                        appliedVersion = FileUtils.readAppliedVersion(versionPath);
                    } catch (Exception ex) {
                        System.out.println("[ModConfirmationDialog] Could not read applied version, using 0.0.0");
                    }
                    
                    // Get target version from remote config
                    String targetVersion = remoteConfig != null ? remoteConfig.optString("modpackVersion", "999.0.0") : "999.0.0";
                    
                    // Use DeletionProcessor to build list of deletions
                    DeletionProcessor deletionProcessor = new DeletionProcessor(
                        new DeletionProcessor.Logger() {
                            public void log(String message) {
                                System.out.println("[ModConfirmationDialog] " + message);
                            }
                        },
                        modMetadata,
                        null  // No backup root needed for just building the list
                    );
                    
                    List<String> deletionsList = deletionProcessor.buildDeletionsList(deletesRoot, appliedVersion, targetVersion);
                    for (String deletion : deletionsList) {
                        if (!deleteKeys.contains(deletion)) {
                            filesToDelete.add(deletion);
                            deleteKeys.add(deletion);
                        }
                    }
                    
                    System.out.println("[ModConfirmationDialog] Found " + deletionsList.size() + " deletion(s) from deletes.json");
                }
            } catch (Exception e) {
                System.err.println("[ModConfirmationDialog] Error while checking deletes.json for present paths: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("[ModConfirmationDialog] enrichListsWithCheckCurrentVersion() failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always close checking dialog
            if (checkingDialog != null) {
                final CheckingUpdatesDialog finalDialog = checkingDialog;
                // FIXED: Use Timer instead of Thread.sleep to avoid blocking EDT
                javax.swing.Timer closeTimer = new javax.swing.Timer(CHECKING_DIALOG_CLOSE_DELAY_MS, e -> finalDialog.close());
                closeTimer.setRepeats(false);
                closeTimer.start();
            }
        }
    }


    // -----------------------------
    // Self-update check
    // -----------------------------
    private void checkForSelfUpdate() {
        SelfUpdateCoordinator coordinator = new SelfUpdateCoordinator(
            new SelfUpdateCoordinator.Logger() {
                public void log(String message) {
                    System.out.println("[ModConfirmationDialog/SelfUpdate] " + message);
                }
            }
        );
        
        selfUpdateInfo = coordinator.checkForUpdate();
        
        if (selfUpdateInfo != null) {
            System.out.println("[ModConfirmationDialog] ModUpdater self-update available!");
            
            // Add the launchwrapper JAR to the "Files to Add" list
            ModEntry launchwrapperMod = new ModEntry(
                "ModUpdater Launchwrapper (Self-Update)",
                selfUpdateInfo.getLatestDownloadUrl(),
                selfUpdateInfo.getLatestFileName(),
                "https://github.com/ArfGg57/ModUpdater-Source",
                "MODUPDATER_SELF_LAUNCHWRAPPER",
                "mods"
            );
            modsToDownload.add(launchwrapperMod);
            
            // Add the mod JAR if available
            if (selfUpdateInfo.hasModJar()) {
                ModEntry modJar = new ModEntry(
                    "ModUpdater Post-Restart Handler (Self-Update)",
                    selfUpdateInfo.getLatestModDownloadUrl(),
                    selfUpdateInfo.getLatestModFileName(),
                    "https://github.com/ArfGg57/ModUpdater-Source",
                    "MODUPDATER_SELF_MOD",
                    "mods"
                );
                modsToDownload.add(modJar);
            }
            
            // Add the cleanup JAR if available (will be installed after restart)
            if (selfUpdateInfo.hasCleanupJar()) {
                ModEntry cleanupJar = new ModEntry(
                    "ModUpdater Cleanup Helper (Self-Update, post-restart)",
                    selfUpdateInfo.getLatestCleanupDownloadUrl(),
                    selfUpdateInfo.getLatestCleanupFileName(),
                    "https://github.com/ArfGg57/ModUpdater-Source",
                    "MODUPDATER_SELF_CLEANUP",
                    "mods"
                );
                modsToDownload.add(cleanupJar);
            }
            
            // Add old versions to the "Files to Delete" list
            if (selfUpdateInfo.hasCurrentJar()) {
                String deleteEntry = DELETE_KEY_MOD + selfUpdateInfo.getCurrentJarPath() + " (old ModUpdater launchwrapper)";
                filesToDelete.add(deleteEntry);
                System.out.println("[ModConfirmationDialog] Added old launchwrapper to delete list: " + selfUpdateInfo.getCurrentFileName());
            }
            
            if (selfUpdateInfo.hasCurrentModJar()) {
                String deleteEntry = DELETE_KEY_MOD + selfUpdateInfo.getCurrentModJarPath() + " (old ModUpdater mod)";
                filesToDelete.add(deleteEntry);
            }
            
            if (selfUpdateInfo.hasCurrentCleanupJar()) {
                String deleteEntry = DELETE_KEY_MOD + selfUpdateInfo.getCurrentCleanupJarPath() + " (old ModUpdater cleanup)";
                filesToDelete.add(deleteEntry);
            }
        }
    }
    
    /**
     * Get the self-update info for use by UpdaterCore.
     * @return SelfUpdateInfo if an update is available, null otherwise
     */
    public SelfUpdateCoordinator.SelfUpdateInfo getSelfUpdateInfo() {
        return selfUpdateInfo;
    }

    // -----------------------------
    // Common dialog setup
    // -----------------------------
    private void setupDialog() {
        dialog = new JDialog((Frame) null, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.setModal(true);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(), WINDOW_CORNER_RADIUS, WINDOW_CORNER_RADIUS));
                g2.dispose();
            }
        };
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(20,20,20,20));

        // Title
        JLabel titleLabel = new JLabel("Confirm Modpack Update", SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Draggable
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleLabel.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e){ initialClick=e.getPoint(); }});
        titleLabel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e){
                int xMoved=e.getX()-initialClick.x;
                int yMoved=e.getY()-initialClick.y;
                Point loc = dialog.getLocation();
                dialog.setLocation(loc.x+xMoved, loc.y+yMoved);
            }
        });

        // Build the unified model for "Files to Add"
        // Pre-populate FILE url index from remote files.json so we can always show URL + install path
        try {
            populateFileUrlIndexFromRemote();
        } catch (Exception e) {
            System.out.println("[ModConfirmationDialog] populateFileUrlIndexFromRemote failed: " + e.getMessage());
        }
        // Build the unified model for "Files to Add"
        addListModel = new DefaultListModel<>();
        buildUnifiedAddList(); // populates addListModel using modsToDownload + filesToApply (deduped, pretty)

        addList = new JList<>(addListModel);
        addList.setCellRenderer(new UnifiedEntryRenderer());
        addList.setBackground(COLOR_LIST_BG);
        addList.setFocusable(false);

        JScrollPane addScroll = new JScrollPane(addList);
        addScroll.setBorder(new LineBorder(COLOR_DIVIDER, 1, true));
        addScroll.getViewport().setBackground(COLOR_LIST_BG);
        addScroll.setBackground(COLOR_LIST_BG);

        addScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        addScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI());

        // Color the scrollpane corners so no white pixels appear
        JPanel addCorner = new JPanel();
        addCorner.setBackground(COLOR_LIST_BG);
        addScroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, addCorner);
        addScroll.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, addCorner);

        // Deletes list model
        deleteListModel = new DefaultListModel<>();
        for (String s : filesToDelete) deleteListModel.addElement(s);
        deleteList = new JList<>(deleteListModel);
        deleteList.setFont(FONT_ENTRY_TITLE);
        deleteList.setForeground(COLOR_TEXT_SECONDARY);
        deleteList.setBackground(COLOR_LIST_BG);
        deleteList.setFocusable(false);

        JScrollPane deleteScroll = new JScrollPane(deleteList);
        deleteScroll.setBorder(new LineBorder(COLOR_DIVIDER, 1, true));
        deleteScroll.getViewport().setBackground(COLOR_LIST_BG);
        deleteScroll.setBackground(COLOR_LIST_BG);

        deleteScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        deleteScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI());

        JPanel delCorner = new JPanel();
        delCorner.setBackground(COLOR_LIST_BG);
        deleteScroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, delCorner);
        deleteScroll.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, delCorner);

        // Layout: two columns (Files to Add | Deletes), 3/4 vs 1/4
        JPanel listsPanel = new JPanel(new GridBagLayout());
        listsPanel.setBackground(COLOR_BG);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1.0;

        // Left panel: Files to Add (3/4 width)
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBackground(COLOR_BG);
        JLabel leftLabel = new JLabel("Files to Add (" + addListModel.size() + ")", SwingConstants.LEFT);
        leftLabel.setForeground(COLOR_ACCENT);
        left.add(leftLabel, BorderLayout.NORTH);
        left.add(addScroll, BorderLayout.CENTER);

        gc.gridx = 0;
        gc.weightx = 0.75;
        listsPanel.add(left, gc);

        // Right panel: Deletes (1/4 width)
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBackground(COLOR_BG);
        JLabel rightLabel = new JLabel("Files to Delete (" + deleteListModel.size() + ")", SwingConstants.LEFT);
        rightLabel.setForeground(COLOR_CANCEL);
        right.add(rightLabel, BorderLayout.NORTH);
        right.add(deleteScroll, BorderLayout.CENTER);

        gc.gridx = 1;
        gc.weightx = 0.25;
        listsPanel.add(right, gc);

        mainPanel.add(listsPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        buttonPanel.setBackground(COLOR_BG);
        JButton agreeButton = new AnimatedButton("Agree", COLOR_ACCENT, COLOR_ACCENT_HOVER, COLOR_BG);
        JButton quitButton = new AnimatedButton("Quit", COLOR_CANCEL, COLOR_CANCEL_HOVER, COLOR_TEXT_PRIMARY);
        buttonPanel.add(quitButton); buttonPanel.add(agreeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        quitButton.addActionListener(e -> {
            agreed = false;
            closeDialog();
        });

        agreeButton.addActionListener(e -> {
            agreed = true;
            closeDialog();
        });

        dialog.setContentPane(mainPanel);
        dialog.setSize(950,550);
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
    }

    // -----------------------------
    // Show dialog
    // -----------------------------
    public void showDialog() {
        dialog.setVisible(true);
    }

    // Whether there is anything to present (adds or deletes)
    public boolean hasAnyItems() {
        try {
            int adds = (addListModel == null) ? 0 : addListModel.getSize();
            int dels = (deleteListModel == null) ? 0 : deleteListModel.getSize();
            return adds > 0 || dels > 0;
        } catch (Exception ignored) {
            return true; // be safe; if UI failed to init, default to showing
        }
    }

    // Whether the user pressed Agree (true) or Quit/closed (false)
    public boolean wasAgreed() {
        return agreed;
    }

    // -----------------------------
    // Build unified add list
    // -----------------------------
    private void buildUnifiedAddList() {
        addListModel.clear();
        Set<String> seenKeys = new HashSet<>();

        // Mods -> unified entries
        for (ModEntry me : modsToDownload) {
            String key = makeModKey(me);
            if (key == null || seenKeys.contains(key)) continue;

            String titleBase = (me.displayName != null && !me.displayName.trim().isEmpty())
                    ? me.displayName.trim()
                    : null;
            // Treat legacy fallback "Untitled File" as absent for display purposes
            if (titleBase != null && titleBase.equalsIgnoreCase("Untitled File")) {
                titleBase = null;
            }

            // Per requirement: confirmation dialog should not use file_name for display
            // Derive the actual filename from the download URL instead
            String actualFileName = null;
            if (me.downloadUrl != null && !me.downloadUrl.trim().isEmpty()) {
                actualFileName = FileUtils.extractFileNameFromUrl(me.downloadUrl.trim());
                if (actualFileName != null && actualFileName.trim().isEmpty()) actualFileName = null;
            }

            String title;
            // Display rules (do not use file_name):
            // - If displayName exists:
            //     * if actual file name exists and is different -> "displayName (actualfilename)"
            //     * else                                         -> "displayName"
            // - If displayName missing:
            //     * use actual file name or "Untitle File" if both fail
            if (titleBase != null) {
                title = (actualFileName != null && !actualFileName.equals(titleBase))
                        ? titleBase + " (" + actualFileName + ")"
                        : titleBase;
            } else {
                title = (actualFileName != null) ? actualFileName : "Untitle File";
            }

            // Secondary: per new requirement, show ONLY the download URL/source label (no install path)
            String urlPart = (me.downloadUrl != null && !me.downloadUrl.isEmpty())
                    ? me.downloadUrl
                    : (me.displaySource != null ? me.displaySource : "");
            String secondary = (urlPart != null) ? urlPart : "";

            UnifiedEntry ue = new UnifiedEntry(UnifiedEntry.Type.MOD, title, secondary, key, me);
            addListModel.addElement(ue);
            seenKeys.add(key);
        }

        // Files -> unified entries
        for (String s : filesToApply) {
            if (s == null) continue;

            String canonical = canonicalFileDedupeKey(s);
            if (seenKeys.contains(canonical)) continue;

            String displayName = s;
            String secondary = "";

            if (s.startsWith("FILE: ")) {
                String path = s.substring(6);
                int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                String pathBase = (lastSlash >= 0 && lastSlash < path.length() - 1) ? path.substring(lastSlash + 1) : path;

                String url = fileEntryToSource.get(s);
                if (url != null && !url.isEmpty()) secondary = url; else secondary = "";

                // Actual source filename should come from the URL, not from file_name
                String actualFileName = null;
                if (secondary != null && !secondary.isEmpty()) {
                    actualFileName = FileUtils.extractFileNameFromUrl(secondary);
                    if (actualFileName != null && actualFileName.trim().isEmpty()) actualFileName = null;
                }

                // Pretty display name from files.json when available
                String pretty = fileEntryToDisplayName.get(s);
                String titleBase = (pretty != null && !pretty.trim().isEmpty()) ? pretty.trim() : null;

                // Build title like mods: display_name (ActualFileName)
                if (titleBase != null) {
                    if (actualFileName != null && !actualFileName.equals(titleBase)) {
                        displayName = titleBase + " (" + actualFileName + ")";
                    } else {
                        displayName = titleBase;
                    }
                } else {
                    // No display name provided; show actual file name or fall back to path base
                    displayName = (actualFileName != null) ? actualFileName : pathBase;
                }
            } else if (s.startsWith("FOLDER: ")) {
                String path = s.substring(8);
                displayName = path;
                secondary = "Folder";
            }

            UnifiedEntry ue = new UnifiedEntry(UnifiedEntry.Type.FILE, displayName, secondary, canonical, null);
            addListModel.addElement(ue);
            seenKeys.add(canonical);
        }
    }

    // -----------------------------
    // JSON Loaders
    // -----------------------------
    private List<ModEntry> loadMods(String urlStr){
        List<ModEntry> mods = new ArrayList<>();
        try (InputStream in = new URL(urlStr).openStream()) {
            JSONArray arr = new JSONArray(new JSONTokener(in));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject modJson = arr.getJSONObject(i);

                String jsonDisplay  = modJson.optString("display_name", "").trim();
                String jsonFileName = modJson.optString("file_name", "").trim();
                String numberId     = modJson.optString("numberId", "").trim();
                String installLocation = modJson.optString("installLocation", "mods");

                String displaySource    = "URL";
                String downloadUrl      = null;
                String filenameFromSource = null;

                // ... existing source resolution ...

                // Display name: display_name -> source filename -> "Untitled File"
                String displayName;
                if (!jsonDisplay.isEmpty()) displayName = jsonDisplay;
                else if (filenameFromSource != null && !filenameFromSource.isEmpty()) displayName = filenameFromSource;
                else displayName = "Untitled File";

                // Base file name: file_name -> source filename -> safe(displayName)
                String baseName;
                if (!jsonFileName.isEmpty()) {
                    baseName = jsonFileName;
                    // Ensure .jar if no extension is present
                    if (!baseName.toLowerCase().endsWith(".jar")) {
                        baseName = baseName + ".jar";
                    }
                } else if (filenameFromSource != null && !filenameFromSource.isEmpty()) {
                    baseName = filenameFromSource;
                } else {
                    String safeFileName = displayName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    if (!safeFileName.toLowerCase().endsWith(".jar")) {
                        safeFileName += ".jar";
                    }
                    baseName = safeFileName;
                }

                // Apply numberId- prefix for test loader as well
                String diskFileName = baseName;
                if (!numberId.isEmpty() && !baseName.startsWith(numberId + "-")) {
                    diskFileName = numberId + "-" + baseName;
                }

                mods.add(new ModEntry(displayName,
                        downloadUrl == null ? "" : downloadUrl,
                        diskFileName,
                        displaySource,
                        numberId,
                        installLocation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mods;
    }

    private List<String> loadFiles(String urlStr){
        List<String> files = new ArrayList<>();
        try(InputStream in = new URL(urlStr).openStream()){
            JSONObject obj = new JSONObject(new JSONTokener(in));
            JSONArray arr = obj.getJSONArray("files");
            for(int i=0;i<arr.length();i++){
                JSONObject f = arr.getJSONObject(i);
                String path = f.optString("downloadPath", "config/") + f.optString("file_name", "");
                files.add("FILE: " + path);
            }
        }catch(Exception e){ e.printStackTrace(); }
        return files;
    }

    private List<String> loadDeletes(String urlStr){
        List<String> deletes = new ArrayList<>();
        try {
            // Load the deletes.json using the new format
            JSONObject deletesRoot = FileUtils.readJsonFromUrl(urlStr);
            
            // Use a dummy metadata and processor to build the list
            // In test mode, we don't have version info, so we'll show all deletions
            ModMetadata dummyMetadata = new ModMetadata(null);
            DeletionProcessor processor = new DeletionProcessor(null, dummyMetadata, null);
            
            // Get all deletions (use version range that includes everything)
            List<String> deletionsList = processor.buildDeletionsList(deletesRoot, "0.0.0", "999.0.0");
            deletes.addAll(deletionsList);
        } catch(Exception e) { 
            e.printStackTrace(); 
        }
        return deletes;
    }

    // -----------------------------
    // ModEntry class
    // -----------------------------
    public static class ModEntry {
        public final String displayName, downloadUrl, fileName, displaySource, numberId, installLocation;
        public ModEntry(String displayName, String downloadUrl, String fileName, String displaySource, String numberId, String installLocation){
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
            this.displaySource = displaySource;
            this.numberId = numberId == null ? "" : numberId;
            this.installLocation = installLocation == null ? "" : installLocation;
        }
    }

    // -----------------------------
    // Renderer for unified entries
    // -----------------------------
    private static class UnifiedEntryRenderer extends JPanel implements ListCellRenderer<UnifiedEntry> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel subLabel = new JLabel();

        public UnifiedEntryRenderer() {
            setLayout(new BorderLayout(2,2));
            setOpaque(true);
            titleLabel.setFont(FONT_ENTRY_TITLE);
            titleLabel.setForeground(COLOR_TEXT_PRIMARY);
            subLabel.setFont(FONT_ENTRY_SUB);
            subLabel.setForeground(COLOR_TEXT_SECONDARY);
            titleLabel.setBorder(new EmptyBorder(6,6,0,6));
            subLabel.setBorder(new EmptyBorder(0,6,6,6));
            add(titleLabel, BorderLayout.NORTH);
            add(subLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UnifiedEntry> list, UnifiedEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            String titleHtml = "<html><div style='width:100%;'><b>" + escapeHtml(value.displayName) + "</b></div></html>";
            titleLabel.setText(titleHtml);

            String sec = value.secondary == null ? "" : value.secondary;
            String secHtml = "<html><div style='width:100%;'><span style='font-size:11px;color:#A0A0A0;'>" + escapeHtml(sec) + "</span></div></html>";
            subLabel.setText(secHtml);

            setBackground(isSelected ? COLOR_BG.brighter() : COLOR_LIST_BG);
            return this;
        }

        private String escapeHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n","<br/>");
        }
    }

    // -----------------------------
    // Buttons
    // -----------------------------
    private static class AnimatedButton extends JButton{
        private Color normalColor, hoverColor, currentColor;
        private javax.swing.Timer timer;
        public AnimatedButton(String text, Color normal, Color hover, Color fg){
            super(text); normalColor=normal; hoverColor=hover; currentColor=normal;
            setForeground(fg); setFont(FONT_BUTTON); setContentAreaFilled(false); setFocusPainted(false); setBorder(new EmptyBorder(10,20,10,20));
            timer = new javax.swing.Timer(15, e ->{animateColor();});
            addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){ timer.start(); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); }
                public void mouseExited(MouseEvent e){ timer.start(); setCursor(Cursor.getDefaultCursor()); }
            });
        }
        private void animateColor(){
            boolean hover = getMousePosition()!=null;
            Color target = hover? hoverColor: normalColor;
            if(currentColor.equals(target)){ timer.stop(); return; }
            int r=Math.min(Math.max(currentColor.getRed() + (target.getRed()-currentColor.getRed())/2,0),255);
            int g=Math.min(Math.max(currentColor.getGreen() + (target.getGreen()-currentColor.getGreen())/2,0),255);
            int b=Math.min(Math.max(currentColor.getBlue() + (target.getBlue()-currentColor.getBlue())/2,0),255);
            currentColor = new Color(r,g,b); repaint();
        }
        protected void paintComponent(Graphics g){
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(currentColor);
            g2.fill(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),15,15));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // -----------------------------
    // ScrollBar
    // -----------------------------
    // -----------------------------
    // ScrollBar
    // -----------------------------
    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = COLOR_SCROLLBAR_THUMB;
            this.trackColor = COLOR_BG;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2,
                    r.width - 4, r.height - 4, 10, 10));
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0,0));
            b.setMinimumSize(new Dimension(0,0));
            b.setMaximumSize(new Dimension(0,0));
            return b;
        }
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private static String makeModKey(ModEntry me) {
        if (me == null) return null;
        // Prefer canonical numberId when present (ensures one entry per mod)
        if (me.numberId != null && !me.numberId.trim().isEmpty()) {
            return "MODID:" + me.numberId.trim();
        }
        String dn = me.displayName == null ? "" : me.displayName.trim();
        String fn = me.fileName == null ? "" : me.fileName.trim();
        return "MOD:" + dn + "|" + fn;
    }

    // Canonicalize FILE/FOLDER entry keys to prevent duplicates caused by different separators
    // e.g., "FILE: config\\woof.txt" and "FILE: config/woof.txt" become the same key
    private static String canonicalFileDedupeKey(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.startsWith("FILE: ") || trimmed.startsWith("FOLDER: ")) {
            int colon = trimmed.indexOf(':');
            if (colon >= 0 && colon + 1 < trimmed.length()) {
                String prefix = trimmed.substring(0, colon + 1); // "FILE:" or "FOLDER:"
                String rest = trimmed.substring(colon + 1).trim(); // path part
                // Normalize separators to forward slash for dedupe purposes
                String normalized = rest.replace('\\', '/');
                // Collapse duplicate slashes
                normalized = normalized.replaceAll("/+", "/");
                return prefix + " " + normalized;
            }
        }
        return trimmed;
    }

    // Build a URL index for FILE entries so we can display both URL and install path in all scenarios
    private void populateFileUrlIndexFromRemote() throws Exception {
        // Only applicable when running in Minecraft (core provided)
        if (core == null) return;
        try {
            JSONObject localConfig = FileUtils.readJson("config/ModUpdater/config.json");
            String remoteConfigUrl = localConfig != null ? localConfig.optString("remote_config_url", "").trim() : "";
            if (remoteConfigUrl.isEmpty()) return;

            JSONObject remoteConfig = FileUtils.readJsonFromUrl(remoteConfigUrl);
            if (remoteConfig == null) return;

            String baseUrl = remoteConfig.optString("configsBaseUrl", "");
            String filesJsonName = remoteConfig.optString("filesJson", "files.json");
            String filesUrl = FileUtils.joinUrl(baseUrl, filesJsonName);

            JSONObject filesRoot = FileUtils.readJsonFromUrl(filesUrl);
            if (filesRoot == null) return;
            JSONArray arr = filesRoot.optJSONArray("files");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                String url = f.optString("url", "").trim();
                String downloadPath = f.optString("downloadPath", "config/");
                String fileName = f.optString("file_name", "").trim();
                String disp = f.optString("display_name", "").trim();
                
                // FIXED: Use FilenameResolver to derive filename consistently with UpdaterCore
                String derived;
                if (!fileName.isEmpty()) {
                    derived = filenameResolver.resolve(fileName, url, null, FilenameResolver.ArtifactType.FILE);
                } else {
                    String extracted = FileUtils.extractFileNameFromUrl(url);
                    derived = filenameResolver.resolve(extracted, url, null, FilenameResolver.ArtifactType.FILE);
                }
                
                if (derived == null || derived.isEmpty()) continue;

                String key1 = "FILE: " + downloadPath + derived;
                if (!url.isEmpty()) fileEntryToSource.put(key1, url);
                if (!disp.isEmpty()) fileEntryToDisplayName.put(key1, disp);

                // If JSON also has a different explicit name, store that key as well
                if (!fileName.isEmpty() && !fileName.equals(derived)) {
                    String key2 = "FILE: " + downloadPath + fileName;
                    if (!url.isEmpty()) fileEntryToSource.put(key2, url);
                    if (!disp.isEmpty()) fileEntryToDisplayName.put(key2, disp);
                }
            }
        } catch (Exception ignored) {
            // best-effort; absence of URLs will just show install path
        }
    }


    /** Ensure local ModUpdater configs exist so reading doesn't fail. */
    private void ensureLocalConfigsForDialog() {
        try {
            String baseDir = "config/ModUpdater";
            FileUtils.ensureDir(baseDir);
            JSONObject defaultConfig = new JSONObject()
                    .put("remoteBaseUrl", "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Config/main/")
                    .put("modsJson", "mods.json")
                    .put("filesJson", "files.json")
                    .put("deletesJson", "deletes.json")
                    .put("sinceVersion", "1.0.0")
                    .put("selfUpdate", new JSONObject().put("enabled", true))
                    // Keep legacy key for compatibility with existing flow
                    .put("remote_config_url", "");
            FileUtils.ensureJsonFile(baseDir + "/config.json", defaultConfig);
            FileUtils.ensureJsonFile(baseDir + "/deletes.json", new JSONObject().put("deletes", new JSONArray()));
            FileUtils.ensureJsonFile(baseDir + "/files.json", new JSONObject().put("files", new JSONArray()));
        } catch (Exception e) {
            System.err.println("[ModConfirmationDialog] Failed to ensure default configs: " + e.getMessage());
        }
    }

    // Close dialog immediately without animation
    private void closeDialog() {
        if (dialog != null) {
            dialog.setVisible(false);
            dialog.dispose();
        }
        
        // Handle exit if user declined (no direct termination here; tweaker will crash)
        if (!agreed) {
            System.err.println("[ModConfirmationDialog] User declined update. Signaling tweaker to crash.");
        }
    }

    // Added: start the updater on a background thread (calls UpdaterCore.runUpdate with the current configuration)
    private void startUpdater() {
        if (core == null) return;
        new Thread(() -> {
            try {
                core.runUpdate();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "ModConfirmationDialog-Updater").start();
    }

    // -----------------------------
    // Standalone Test
    // -----------------------------
    public static void main(String[] args){
        SwingUtilities.invokeLater(() ->{
            ModConfirmationDialog dlg = new ModConfirmationDialog(
                    "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Config/main/mods.json",
                    "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Config/main/files.json",
                    "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Config/main/deletes.json"
            );
            dlg.showDialog();
        });
    }
}
