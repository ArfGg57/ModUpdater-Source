package com.ArfGg57.modupdater.selfupdate;

import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.pending.PendingUpdateOperation;
import com.ArfGg57.modupdater.pending.PendingUpdateOpsManager;
import com.ArfGg57.modupdater.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Simplified self-update coordinator for ModUpdater.
 * 
 * The system works as follows:
 * 1. Check GitHub API (https://api.github.com/repos/ArfGg57/ModUpdater-Source/releases/latest) for the latest release
 * 2. Compare the release file name with the file name in current_release.json
 * 3. If different, an update is available
 * 4. The update is shown in the confirmation dialog (new version to download, old version to delete)
 * 5. Uses the existing pending operations system for locked file handling
 * 6. If the old JAR was renamed, uses hash comparison to find and delete it
 */
public class SelfUpdateCoordinator {
    
    // Hard-coded GitHub repository URL
    private static final String GITHUB_REPO = "ArfGg57/ModUpdater-Source";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String SOURCE_BASE_URL = "https://github.com/" + GITHUB_REPO;
    
    // Path to current_release.json in the source repository
    private static final String CURRENT_RELEASE_JSON_URL = "https://raw.githubusercontent.com/" + GITHUB_REPO + "/main/current_release.json";
    
    /**
     * Logger interface for output messages
     */
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    
    public SelfUpdateCoordinator(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Check for ModUpdater self-updates.
     * 
     * @return SelfUpdateInfo containing update details if available, null otherwise
     */
    public SelfUpdateInfo checkForUpdate() {
        try {
            logger.log("Checking for ModUpdater self-updates...");
            
            // Step 1: Fetch current_release.json from the source repository
            JSONObject currentReleaseJson = fetchCurrentReleaseJson();
            if (currentReleaseJson == null) {
                logger.log("Could not fetch current_release.json from source repository");
                return null;
            }
            
            String currentFileName = currentReleaseJson.optString("current_file_name", "");
            String previousReleaseHash = currentReleaseJson.optString("previous_release_hash", "");
            
            if (currentFileName.isEmpty()) {
                logger.log("current_release.json is missing current_file_name");
                return null;
            }
            
            logger.log("Current release file name from source: " + currentFileName);
            
            // Step 2: Check GitHub API for the latest release
            JSONObject latestRelease = fetchLatestRelease();
            if (latestRelease == null) {
                logger.log("Could not fetch latest release from GitHub API");
                return null;
            }
            
            // Find the ModUpdater JAR asset in the release
            String latestFileName = null;
            String latestDownloadUrl = null;
            String latestSha256Hash = null;
            
            JSONArray assets = latestRelease.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.getString("name");
                    
                    // Look for the ModUpdater JAR
                    if (assetName.endsWith(".jar") && assetName.toLowerCase().contains("modupdater")) {
                        latestFileName = assetName;
                        latestDownloadUrl = asset.getString("browser_download_url");
                        logger.log("Found JAR asset: " + assetName);
                    }
                    
                    // Look for SHA256 hash file
                    if (assetName.endsWith(".sha256")) {
                        try {
                            String hashUrl = asset.getString("browser_download_url");
                            String hashContent = FileUtils.readUrlToString(hashUrl, null);
                            if (hashContent != null) {
                                latestSha256Hash = hashContent.trim().split("\\s+")[0];
                                logger.log("Found SHA256 hash: " + latestSha256Hash);
                            }
                        } catch (Exception e) {
                            logger.log("Could not read SHA256 hash file: " + e.getMessage());
                        }
                    }
                }
            }
            
            if (latestFileName == null || latestDownloadUrl == null) {
                logger.log("No ModUpdater JAR found in latest release");
                return null;
            }
            
            // Step 3: Compare file names to determine if update is needed
            if (currentFileName.equals(latestFileName)) {
                logger.log("ModUpdater is up to date (file name matches: " + currentFileName + ")");
                return null;
            }
            
            logger.log("ModUpdater update available!");
            logger.log("  Current: " + currentFileName);
            logger.log("  Latest:  " + latestFileName);
            
            // Step 4: Find the current installed JAR
            File currentJar = findCurrentJar(currentFileName, previousReleaseHash);
            String currentJarPath = currentJar != null ? currentJar.getAbsolutePath() : null;
            String currentJarName = currentJar != null ? currentJar.getName() : currentFileName;
            
            // Create and return the update info
            return new SelfUpdateInfo(
                currentJarName,
                currentJarPath,
                latestFileName,
                latestDownloadUrl,
                latestSha256Hash,
                previousReleaseHash
            );
            
        } catch (Exception e) {
            logger.log("Self-update check failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Fetch current_release.json from the source repository
     */
    private JSONObject fetchCurrentReleaseJson() {
        try {
            return FileUtils.readJsonFromUrl(CURRENT_RELEASE_JSON_URL);
        } catch (Exception e) {
            logger.log("Failed to fetch current_release.json: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Fetch the latest release from GitHub API
     */
    private JSONObject fetchLatestRelease() {
        try {
            return FileUtils.readJsonFromUrl(GITHUB_API_URL);
        } catch (Exception e) {
            logger.log("Failed to fetch latest release: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Find the current installed ModUpdater JAR file.
     * First tries to find by name, then falls back to hash comparison.
     * 
     * @param expectedFileName The expected file name from current_release.json
     * @param previousReleaseHash The hash of the previous release for fallback detection
     * @return The current JAR file, or null if not found
     */
    public File findCurrentJar(String expectedFileName, String previousReleaseHash) {
        File modsDir = new File("mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            logger.log("Mods directory not found");
            return null;
        }
        
        File[] files = modsDir.listFiles();
        if (files == null) {
            return null;
        }
        
        // First, try to find by exact file name
        for (File file : files) {
            if (file.getName().equals(expectedFileName)) {
                logger.log("Found current JAR by exact name: " + file.getName());
                return file;
            }
        }
        
        // Second, try to find any file containing "modupdater" in the name
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.contains("modupdater") && name.endsWith(".jar")) {
                logger.log("Found current JAR by name pattern: " + file.getName());
                return file;
            }
        }
        
        // Third, if we have a previous release hash, try to find by hash comparison
        if (previousReleaseHash != null && !previousReleaseHash.isEmpty()) {
            logger.log("Searching for current JAR by hash: " + previousReleaseHash);
            for (File file : files) {
                if (!file.getName().endsWith(".jar")) continue;
                
                try {
                    String fileHash = HashUtils.sha256Hex(file);
                    if (previousReleaseHash.equalsIgnoreCase(fileHash)) {
                        logger.log("Found current JAR by hash match: " + file.getName());
                        return file;
                    }
                } catch (Exception e) {
                    // Continue to next file
                }
            }
        }
        
        logger.log("Could not find current ModUpdater JAR");
        return null;
    }
    
    /**
     * Create pending operations for the self-update.
     * This will schedule the old JAR for deletion and the new JAR for download.
     * 
     * @param updateInfo The update information
     * @param pendingOps The pending operations manager to add operations to
     */
    public void createPendingOperations(SelfUpdateInfo updateInfo, PendingUpdateOpsManager pendingOps) {
        if (updateInfo.getCurrentJarPath() != null) {
            // Create UPDATE operation (delete old, download new)
            PendingUpdateOperation updateOp = PendingUpdateOperation.createUpdate(
                updateInfo.getCurrentJarPath(),
                updateInfo.getLatestDownloadUrl(),
                updateInfo.getLatestFileName(),
                "mods",
                updateInfo.getLatestSha256Hash(),
                "ModUpdater self-update: " + updateInfo.getCurrentFileName() + " -> " + updateInfo.getLatestFileName()
            );
            pendingOps.addOperation(updateOp);
            logger.log("Created pending UPDATE operation for ModUpdater self-update");
        } else {
            // No current JAR found - just download the new one
            // We'll handle this as a special case in the confirmation dialog
            logger.log("No current JAR found - will download new version directly");
        }
    }
    
    /**
     * Container class for self-update information
     */
    public static class SelfUpdateInfo {
        private final String currentFileName;
        private final String currentJarPath;
        private final String latestFileName;
        private final String latestDownloadUrl;
        private final String latestSha256Hash;
        private final String previousReleaseHash;
        
        public SelfUpdateInfo(String currentFileName, String currentJarPath, 
                             String latestFileName, String latestDownloadUrl,
                             String latestSha256Hash, String previousReleaseHash) {
            this.currentFileName = currentFileName;
            this.currentJarPath = currentJarPath;
            this.latestFileName = latestFileName;
            this.latestDownloadUrl = latestDownloadUrl;
            this.latestSha256Hash = latestSha256Hash;
            this.previousReleaseHash = previousReleaseHash;
        }
        
        public String getCurrentFileName() { return currentFileName; }
        public String getCurrentJarPath() { return currentJarPath; }
        public String getLatestFileName() { return latestFileName; }
        public String getLatestDownloadUrl() { return latestDownloadUrl; }
        public String getLatestSha256Hash() { return latestSha256Hash; }
        public String getPreviousReleaseHash() { return previousReleaseHash; }
        
        public boolean hasCurrentJar() { return currentJarPath != null; }
    }
}
