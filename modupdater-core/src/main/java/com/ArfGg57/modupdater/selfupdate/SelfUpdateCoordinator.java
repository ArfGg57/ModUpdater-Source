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
 * 1. Fetch current_release.json from the source repository to get the expected version and file info
 * 2. Check GitHub API for the latest release and find matching JAR assets
 * 3. Compare versions to determine if an update is available
 * 4. Apply filename_prefix from config to map GitHub filenames to local filenames
 * 5. The update is shown in the confirmation dialog (new version to download, old version to delete)
 * 6. Uses the existing pending operations system for locked file handling
 * 
 * current_release.json format:
 * {
 *   "version": "2.21",
 *   "filename_prefix": "!!!!!",
 *   "files": {
 *     "launchwrapper": { "github_name": "modupdater-2.21.jar", "local_name": "!!!!!modupdater-2.21.jar", "hash": "..." },
 *     "mod": { "github_name": "modupdater-mod-2.21.jar", "local_name": "!!!!!modupdater-mod-2.21.jar", "hash": "..." },
 *     "cleanup": { "github_name": "modupdater-cleanup-2.21.jar", "local_name": "modupdater-cleanup-2.21.jar", "hash": "..." }
 *   }
 * }
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
            
            // Parse new config format with backward compatibility for old format
            ReleaseConfig releaseConfig = parseReleaseConfig(currentReleaseJson);
            if (releaseConfig == null) {
                logger.log("Failed to parse current_release.json");
                return null;
            }
            
            logger.log("Current release version from source: " + releaseConfig.version);
            logger.log("Filename prefix: " + releaseConfig.filenamePrefix);
            
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
            
            // Step 3: Determine local filenames using prefix from config
            // GitHub can't have ! symbols in filenames, so we need to map:
            // GitHub name: "modupdater-2.21.jar" -> Local name: "!!!!!modupdater-2.21.jar"
            String launchwrapperLocalName = applyFilenamePrefix(launchwrapperAsset.fileName, releaseConfig.filenamePrefix);
            String modLocalName = modAsset != null ? applyFilenamePrefix(modAsset.fileName, releaseConfig.filenamePrefix) : null;
            // Cleanup JAR doesn't need prefix (it's not loaded by Forge)
            String cleanupLocalName = cleanupAsset != null ? cleanupAsset.fileName : null;
            
            // Use hashes from config if available, otherwise use hashes from GitHub release
            if (releaseConfig.launchwrapperHash != null && !releaseConfig.launchwrapperHash.isEmpty()) {
                launchwrapperSha256 = releaseConfig.launchwrapperHash;
            }
            if (releaseConfig.modHash != null && !releaseConfig.modHash.isEmpty()) {
                modSha256 = releaseConfig.modHash;
            }
            if (releaseConfig.cleanupHash != null && !releaseConfig.cleanupHash.isEmpty()) {
                cleanupSha256 = releaseConfig.cleanupHash;
            }
            
            // Step 4: Find current installed JARs using the local names from config
            File currentLaunchwrapper = findCurrentJar(releaseConfig.launchwrapperLocalName, launchwrapperSha256);
            File currentMod = findCurrentJarByLocalName(releaseConfig.modLocalName, MOD_PATTERN);
            File currentCleanup = findCurrentJarByLocalName(releaseConfig.cleanupLocalName, CLEANUP_PATTERN);
            
            // Step 5: Determine if update is needed by comparing installed files with latest
            // Track each file individually so we only show files that actually need updates
            boolean launchwrapperNeedsUpdate = false;
            boolean modNeedsUpdate = false;
            boolean cleanupNeedsUpdate = false;
            
            // Check if launchwrapper needs update (compare with expected local name)
            if (currentLaunchwrapper == null || !currentLaunchwrapper.getName().equals(launchwrapperLocalName)) {
                launchwrapperNeedsUpdate = true;
                logger.log("Launchwrapper update needed:");
                logger.log("  Current: " + (currentLaunchwrapper != null ? currentLaunchwrapper.getName() : "not found"));
                logger.log("  Expected: " + launchwrapperLocalName);
            }
            
            // Check if mod JAR needs update
            if (modAsset != null && modLocalName != null) {
                if (currentMod == null || !currentMod.getName().equals(modLocalName)) {
                    modNeedsUpdate = true;
                    logger.log("Mod JAR update needed:");
                    logger.log("  Current: " + (currentMod != null ? currentMod.getName() : "not found"));
                    logger.log("  Expected: " + modLocalName);
                }
            }
            
            // Check if cleanup JAR needs update
            if (cleanupAsset != null && cleanupLocalName != null) {
                if (currentCleanup == null || !currentCleanup.getName().equals(cleanupLocalName)) {
                    cleanupNeedsUpdate = true;
                    logger.log("Cleanup JAR update needed:");
                    logger.log("  Current: " + (currentCleanup != null ? currentCleanup.getName() : "not found"));
                    logger.log("  Expected: " + cleanupLocalName);
                }
            }
            
            // Only return update info if at least one file needs update
            boolean needsUpdate = launchwrapperNeedsUpdate || modNeedsUpdate || cleanupNeedsUpdate;
            
            if (!needsUpdate) {
                logger.log("ModUpdater is up to date (all files match expected versions)");
                return null;
            }
            
            logger.log("ModUpdater update available! (version " + releaseConfig.version + ")");
            
            // Create and return the update info with all JAR details
            // Use local names for the target filenames (what files should be named in mods folder)
            return new SelfUpdateInfo(
                currentLaunchwrapper != null ? currentLaunchwrapper.getName() : releaseConfig.launchwrapperLocalName,
                currentLaunchwrapper != null ? currentLaunchwrapper.getAbsolutePath() : null,
                launchwrapperLocalName,
                launchwrapperAsset.downloadUrl,
                launchwrapperSha256,
                releaseConfig.launchwrapperHash,
                // Additional JARs - use local names for target filenames
                modLocalName,
                modAsset != null ? modAsset.downloadUrl : null,
                modSha256,
                currentMod != null ? currentMod.getAbsolutePath() : null,
                cleanupLocalName,
                cleanupAsset != null ? cleanupAsset.downloadUrl : null,
                cleanupSha256,
                currentCleanup != null ? currentCleanup.getAbsolutePath() : null,
                // Include flags for which files actually need updates
                launchwrapperNeedsUpdate,
                modNeedsUpdate,
                cleanupNeedsUpdate
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
     * Parse the release configuration from current_release.json.
     * Supports both the new format and legacy format for backward compatibility.
     * 
     * New format:
     * {
     *   "version": "2.21",
     *   "filename_prefix": "!!!!!",
     *   "files": {
     *     "launchwrapper": { "github_name": "...", "local_name": "...", "hash": "..." },
     *     "mod": { "github_name": "...", "local_name": "...", "hash": "..." },
     *     "cleanup": { "github_name": "...", "local_name": "...", "hash": "..." }
     *   }
     * }
     * 
     * Legacy format:
     * {
     *   "current_file_name": "!!!!!modupdater-2.21.jar",
     *   "previous_release_hash": "..."
     * }
     */
    private ReleaseConfig parseReleaseConfig(JSONObject json) {
        ReleaseConfig config = new ReleaseConfig();
        
        // Check for new format first
        if (json.has("version") && json.has("files")) {
            config.version = json.optString("version", "");
            config.filenamePrefix = json.optString("filename_prefix", "");
            
            JSONObject files = json.optJSONObject("files");
            if (files != null) {
                // Parse launchwrapper config
                JSONObject launcher = files.optJSONObject("launchwrapper");
                if (launcher != null) {
                    config.launchwrapperGithubName = launcher.optString("github_name", "");
                    config.launchwrapperLocalName = launcher.optString("local_name", "");
                    config.launchwrapperHash = launcher.optString("hash", "");
                }
                
                // Parse mod config
                JSONObject mod = files.optJSONObject("mod");
                if (mod != null) {
                    config.modGithubName = mod.optString("github_name", "");
                    config.modLocalName = mod.optString("local_name", "");
                    config.modHash = mod.optString("hash", "");
                }
                
                // Parse cleanup config
                JSONObject cleanup = files.optJSONObject("cleanup");
                if (cleanup != null) {
                    config.cleanupGithubName = cleanup.optString("github_name", "");
                    config.cleanupLocalName = cleanup.optString("local_name", "");
                    config.cleanupHash = cleanup.optString("hash", "");
                }
            }
            
            logger.log("Parsed new config format: version=" + config.version + ", prefix=" + config.filenamePrefix);
            return config;
        }
        
        // Fall back to legacy format
        String currentFileName = json.optString("current_file_name", "");
        String previousReleaseHash = json.optString("previous_release_hash", "");
        
        if (currentFileName.isEmpty()) {
            logger.log("current_release.json has invalid format");
            return null;
        }
        
        // Extract version from filename (e.g., "!!!!!modupdater-2.21.jar" -> "2.21")
        config.version = extractVersionFromFilename(currentFileName);
        config.filenamePrefix = extractPrefixFromFilename(currentFileName);
        config.launchwrapperLocalName = currentFileName;
        config.launchwrapperHash = previousReleaseHash;
        
        // Generate expected names for mod and cleanup based on pattern
        String baseName = currentFileName.replace(config.filenamePrefix, "");
        config.modLocalName = config.filenamePrefix + baseName.replace("modupdater-", "modupdater-mod-");
        config.cleanupLocalName = baseName.replace("modupdater-", "modupdater-cleanup-");
        
        logger.log("Parsed legacy config format: version=" + config.version + ", prefix=" + config.filenamePrefix);
        return config;
    }
    
    /**
     * Extract version number from a filename like "!!!!!modupdater-2.21.jar"
     */
    private String extractVersionFromFilename(String filename) {
        // Remove prefix (all leading non-alphanumeric chars)
        String name = filename.replaceAll("^[^a-zA-Z0-9]+", "");
        
        // Remove .jar extension if present
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        
        // Try to extract version using regex pattern (digits and dots at the end)
        // This handles: modupdater-2.21, modupdater-mod-2.21, modupdater-cleanup-2.21
        java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("-(\\d+\\.\\d+(?:\\.\\d+)?)$");
        java.util.regex.Matcher matcher = versionPattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Fallback: try lastIndexOf approach
        int dashIdx = name.lastIndexOf('-');
        if (dashIdx > 0) {
            String versionPart = name.substring(dashIdx + 1);
            // Check if it looks like a version number
            if (versionPart.matches("\\d+(\\.\\d+)*")) {
                return versionPart;
            }
        }
        
        return "0.0.0";
    }
    
    /**
     * Extract prefix (like "!!!!!") from a filename
     */
    private String extractPrefixFromFilename(String filename) {
        StringBuilder prefix = new StringBuilder();
        for (char c : filename.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                prefix.append(c);
            } else {
                break;
            }
        }
        return prefix.toString();
    }
    
    /**
     * Apply filename prefix to a GitHub filename to get the local filename.
     * For example: "modupdater-2.21.jar" with prefix "!!!!!" -> "!!!!!modupdater-2.21.jar"
     * 
     * Note: The cleanup JAR doesn't need a prefix.
     */
    private String applyFilenamePrefix(String githubName, String prefix) {
        if (githubName == null || githubName.isEmpty()) {
            return githubName;
        }
        if (prefix == null || prefix.isEmpty()) {
            return githubName;
        }
        // Don't apply prefix to cleanup JAR (doesn't need early loading)
        if (githubName.toLowerCase().contains("-cleanup")) {
            return githubName;
        }
        return prefix + githubName;
    }
    
    /**
     * Find a JAR file by local name, with fallback to pattern matching.
     * Searches in both mods/ and config/ModUpdater/ directories.
     */
    private File findCurrentJarByLocalName(String localName, String fallbackPattern) {
        // Search in multiple directories
        String[] searchDirs = {"mods", "config/ModUpdater"};
        
        for (String dirPath : searchDirs) {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }
            
            File[] files = dir.listFiles();
            if (files == null) continue;
            
            // First try exact match with local name
            if (localName != null && !localName.isEmpty()) {
                for (File file : files) {
                    if (file.getName().equals(localName)) {
                        return file;
                    }
                }
            }
            
            // Fall back to pattern matching
            if (fallbackPattern != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.contains(fallbackPattern) && name.endsWith(".jar")) {
                        return file;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Configuration parsed from current_release.json
     */
    private static class ReleaseConfig {
        String version = "";
        String filenamePrefix = "";
        
        // Launchwrapper JAR
        String launchwrapperGithubName = "";
        String launchwrapperLocalName = "";
        String launchwrapperHash = "";
        
        // Mod JAR
        String modGithubName = "";
        String modLocalName = "";
        String modHash = "";
        
        // Cleanup JAR
        String cleanupGithubName = "";
        String cleanupLocalName = "";
        String cleanupHash = "";
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
        
        // Flags to indicate which files actually need updates
        private final boolean launchwrapperNeedsUpdate;
        private final boolean modNeedsUpdate;
        private final boolean cleanupNeedsUpdate;
        
        public SelfUpdateInfo(String currentFileName, String currentJarPath, 
                             String latestFileName, String latestDownloadUrl,
                             String latestSha256Hash, String previousReleaseHash,
                             String latestModFileName, String latestModDownloadUrl, String latestModSha256Hash, String currentModJarPath,
                             String latestCleanupFileName, String latestCleanupDownloadUrl, String latestCleanupSha256Hash, String currentCleanupJarPath,
                             boolean launchwrapperNeedsUpdate, boolean modNeedsUpdate, boolean cleanupNeedsUpdate) {
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
            this.launchwrapperNeedsUpdate = launchwrapperNeedsUpdate;
            this.modNeedsUpdate = modNeedsUpdate;
            this.cleanupNeedsUpdate = cleanupNeedsUpdate;
        }
        
        // Launchwrapper getters
        public String getCurrentFileName() { return currentFileName; }
        public String getCurrentJarPath() { return currentJarPath; }
        public String getLatestFileName() { return latestFileName; }
        public String getLatestDownloadUrl() { return latestDownloadUrl; }
        public String getLatestSha256Hash() { return latestSha256Hash; }
        public String getPreviousReleaseHash() { return previousReleaseHash; }
        public boolean hasCurrentJar() { return currentJarPath != null; }
        public boolean launchwrapperNeedsUpdate() { return launchwrapperNeedsUpdate; }
        
        // Mod JAR getters
        public String getLatestModFileName() { return latestModFileName; }
        public String getLatestModDownloadUrl() { return latestModDownloadUrl; }
        public String getLatestModSha256Hash() { return latestModSha256Hash; }
        public String getCurrentModJarPath() { return currentModJarPath; }
        public boolean hasModJar() { return latestModFileName != null && latestModDownloadUrl != null; }
        public boolean hasCurrentModJar() { return currentModJarPath != null; }
        public boolean modNeedsUpdate() { return modNeedsUpdate; }
        
        // Cleanup JAR getters
        public String getLatestCleanupFileName() { return latestCleanupFileName; }
        public String getLatestCleanupDownloadUrl() { return latestCleanupDownloadUrl; }
        public String getLatestCleanupSha256Hash() { return latestCleanupSha256Hash; }
        public String getCurrentCleanupJarPath() { return currentCleanupJarPath; }
        public boolean hasCleanupJar() { return latestCleanupFileName != null && latestCleanupDownloadUrl != null; }
        public boolean hasCurrentCleanupJar() { return currentCleanupJarPath != null; }
        public boolean cleanupNeedsUpdate() { return cleanupNeedsUpdate; }
    }
}
