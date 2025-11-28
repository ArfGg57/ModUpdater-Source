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
import java.util.ArrayList;
import java.util.List;

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
 * 
 * Multi-JAR update process:
 * - Phase 1 (during update): Download and install launchwrapper and mod JARs
 * - Phase 2 (after restart): Remove old launchwrapper/mod, then download and install cleanup JAR
 */
public class SelfUpdateCoordinator {
    
    // Hard-coded GitHub repository URL
    private static final String GITHUB_REPO = "ArfGg57/ModUpdater-Source";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String SOURCE_BASE_URL = "https://github.com/" + GITHUB_REPO;
    
    // Path to current_release.json in the source repository
    private static final String CURRENT_RELEASE_JSON_URL = "https://raw.githubusercontent.com/" + GITHUB_REPO + "/main/current_release.json";
    
    // JAR file patterns for identification
    private static final String LAUNCHWRAPPER_PATTERN = "modupdater";  // Main JAR (not -mod or -cleanup)
    private static final String MOD_PATTERN = "modupdater-mod";
    private static final String CLEANUP_PATTERN = "modupdater-cleanup";
    
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
            
            // Find all ModUpdater JAR assets in the release
            JarAsset launchwrapperAsset = null;
            JarAsset modAsset = null;
            JarAsset cleanupAsset = null;
            String launchwrapperSha256 = null;
            String modSha256 = null;
            String cleanupSha256 = null;
            
            JSONArray assets = latestRelease.optJSONArray("assets");
            if (assets != null) {
                // First pass: find all JAR files
                // Order matters: check cleanup and mod patterns before launchwrapper
                // since "modupdater" pattern would match all of them
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.getString("name");
                    String downloadUrl = asset.getString("browser_download_url");
                    
                    if (assetName.endsWith(".jar")) {
                        String nameLower = assetName.toLowerCase();
                        
                        // Check specific patterns first (cleanup, mod), then generic (launchwrapper)
                        if (nameLower.contains(CLEANUP_PATTERN)) {
                            cleanupAsset = new JarAsset(assetName, downloadUrl);
                            logger.log("Found cleanup JAR: " + assetName);
                        } else if (nameLower.contains(MOD_PATTERN)) {
                            modAsset = new JarAsset(assetName, downloadUrl);
                            logger.log("Found mod JAR: " + assetName);
                        } else if (nameLower.contains(LAUNCHWRAPPER_PATTERN) 
                                   && !nameLower.contains("-mod") 
                                   && !nameLower.contains("-cleanup")) {
                            // Main launchwrapper JAR - explicitly exclude mod and cleanup patterns
                            launchwrapperAsset = new JarAsset(assetName, downloadUrl);
                            logger.log("Found launchwrapper JAR: " + assetName);
                        }
                    }
                }
                
                // Second pass: find SHA256 hash files
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.getString("name");
                    
                    if (assetName.endsWith(".sha256")) {
                        try {
                            String hashUrl = asset.getString("browser_download_url");
                            String hashContent = FileUtils.readUrlToString(hashUrl, null);
                            if (hashContent != null) {
                                String hash = hashContent.trim().split("\\s+")[0];
                                
                                // Validate SHA-256 format (64 hex characters)
                                if (!isValidSha256(hash)) {
                                    logger.log("Invalid SHA256 format in file: " + assetName);
                                    continue;
                                }
                                
                                String baseName = assetName.substring(0, assetName.length() - 7); // Remove .sha256
                                
                                if (launchwrapperAsset != null && baseName.equals(launchwrapperAsset.fileName)) {
                                    launchwrapperSha256 = hash;
                                    logger.log("Found launchwrapper SHA256: " + hash.substring(0, 8) + "...");
                                } else if (modAsset != null && baseName.equals(modAsset.fileName)) {
                                    modSha256 = hash;
                                    logger.log("Found mod SHA256: " + hash.substring(0, 8) + "...");
                                } else if (cleanupAsset != null && baseName.equals(cleanupAsset.fileName)) {
                                    cleanupSha256 = hash;
                                    logger.log("Found cleanup SHA256: " + hash.substring(0, 8) + "...");
                                }
                            }
                        } catch (Exception e) {
                            logger.log("Could not read SHA256 hash file: " + e.getMessage());
                        }
                    }
                }
            }
            
            if (launchwrapperAsset == null) {
                logger.log("No ModUpdater launchwrapper JAR found in latest release");
                return null;
            }
            
            // Step 3: Compare file names to determine if update is needed
            if (currentFileName.equals(launchwrapperAsset.fileName)) {
                logger.log("ModUpdater is up to date (file name matches: " + currentFileName + ")");
                return null;
            }
            
            logger.log("ModUpdater update available!");
            logger.log("  Current: " + currentFileName);
            logger.log("  Latest:  " + launchwrapperAsset.fileName);
            
            // Step 4: Find current installed JARs
            File currentLaunchwrapper = findCurrentJar(currentFileName, previousReleaseHash);
            File currentMod = findJarByPattern(MOD_PATTERN);
            File currentCleanup = findJarByPattern(CLEANUP_PATTERN);
            
            // Create and return the update info with all JAR details
            return new SelfUpdateInfo(
                currentLaunchwrapper != null ? currentLaunchwrapper.getName() : currentFileName,
                currentLaunchwrapper != null ? currentLaunchwrapper.getAbsolutePath() : null,
                launchwrapperAsset.fileName,
                launchwrapperAsset.downloadUrl,
                launchwrapperSha256,
                previousReleaseHash,
                // Additional JARs
                modAsset != null ? modAsset.fileName : null,
                modAsset != null ? modAsset.downloadUrl : null,
                modSha256,
                currentMod != null ? currentMod.getAbsolutePath() : null,
                cleanupAsset != null ? cleanupAsset.fileName : null,
                cleanupAsset != null ? cleanupAsset.downloadUrl : null,
                cleanupSha256,
                currentCleanup != null ? currentCleanup.getAbsolutePath() : null
            );
            
        } catch (Exception e) {
            logger.log("Self-update check failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Simple container for JAR asset info
     */
    private static class JarAsset {
        final String fileName;
        final String downloadUrl;
        
        JarAsset(String fileName, String downloadUrl) {
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
        }
    }
    
    /**
     * Validate SHA-256 hash format (64 hexadecimal characters)
     */
    private boolean isValidSha256(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }
        for (char c : hash.toLowerCase().toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
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
     * Find a JAR file in the mods directory by pattern
     */
    private File findJarByPattern(String pattern) {
        File modsDir = new File("mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return null;
        }
        
        File[] files = modsDir.listFiles();
        if (files == null) return null;
        
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.contains(pattern) && name.endsWith(".jar")) {
                return file;
            }
        }
        return null;
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
        
        // Second, try to find main modupdater JAR (not mod or cleanup)
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.contains("modupdater") && name.endsWith(".jar") 
                && !name.contains("-mod") && !name.contains("-cleanup")) {
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
     * Container class for self-update information including all JAR files
     */
    public static class SelfUpdateInfo {
        // Launchwrapper JAR (main ModUpdater)
        private final String currentFileName;
        private final String currentJarPath;
        private final String latestFileName;
        private final String latestDownloadUrl;
        private final String latestSha256Hash;
        private final String previousReleaseHash;
        
        // Mod JAR (post-restart handler)
        private final String latestModFileName;
        private final String latestModDownloadUrl;
        private final String latestModSha256Hash;
        private final String currentModJarPath;
        
        // Cleanup JAR (cleanup helper)
        private final String latestCleanupFileName;
        private final String latestCleanupDownloadUrl;
        private final String latestCleanupSha256Hash;
        private final String currentCleanupJarPath;
        
        public SelfUpdateInfo(String currentFileName, String currentJarPath, 
                             String latestFileName, String latestDownloadUrl,
                             String latestSha256Hash, String previousReleaseHash,
                             String latestModFileName, String latestModDownloadUrl, String latestModSha256Hash, String currentModJarPath,
                             String latestCleanupFileName, String latestCleanupDownloadUrl, String latestCleanupSha256Hash, String currentCleanupJarPath) {
            this.currentFileName = currentFileName;
            this.currentJarPath = currentJarPath;
            this.latestFileName = latestFileName;
            this.latestDownloadUrl = latestDownloadUrl;
            this.latestSha256Hash = latestSha256Hash;
            this.previousReleaseHash = previousReleaseHash;
            this.latestModFileName = latestModFileName;
            this.latestModDownloadUrl = latestModDownloadUrl;
            this.latestModSha256Hash = latestModSha256Hash;
            this.currentModJarPath = currentModJarPath;
            this.latestCleanupFileName = latestCleanupFileName;
            this.latestCleanupDownloadUrl = latestCleanupDownloadUrl;
            this.latestCleanupSha256Hash = latestCleanupSha256Hash;
            this.currentCleanupJarPath = currentCleanupJarPath;
        }
        
        // Launchwrapper getters
        public String getCurrentFileName() { return currentFileName; }
        public String getCurrentJarPath() { return currentJarPath; }
        public String getLatestFileName() { return latestFileName; }
        public String getLatestDownloadUrl() { return latestDownloadUrl; }
        public String getLatestSha256Hash() { return latestSha256Hash; }
        public String getPreviousReleaseHash() { return previousReleaseHash; }
        public boolean hasCurrentJar() { return currentJarPath != null; }
        
        // Mod JAR getters
        public String getLatestModFileName() { return latestModFileName; }
        public String getLatestModDownloadUrl() { return latestModDownloadUrl; }
        public String getLatestModSha256Hash() { return latestModSha256Hash; }
        public String getCurrentModJarPath() { return currentModJarPath; }
        public boolean hasModJar() { return latestModFileName != null && latestModDownloadUrl != null; }
        public boolean hasCurrentModJar() { return currentModJarPath != null; }
        
        // Cleanup JAR getters
        public String getLatestCleanupFileName() { return latestCleanupFileName; }
        public String getLatestCleanupDownloadUrl() { return latestCleanupDownloadUrl; }
        public String getLatestCleanupSha256Hash() { return latestCleanupSha256Hash; }
        public String getCurrentCleanupJarPath() { return currentCleanupJarPath; }
        public boolean hasCleanupJar() { return latestCleanupFileName != null && latestCleanupDownloadUrl != null; }
        public boolean hasCurrentCleanupJar() { return currentCleanupJarPath != null; }
    }
}
