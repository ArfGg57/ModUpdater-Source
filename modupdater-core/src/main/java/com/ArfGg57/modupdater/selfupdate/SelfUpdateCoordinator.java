package com.ArfGg57.modupdater.selfupdate;

import com.ArfGg57.modupdater.ui.GuiUpdater;
import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.version.VersionComparator;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Main coordinator for ModUpdater self-update process.
 * 
 * Workflow:
 * 1. Load configuration
 * 2. Check if self-update is enabled
 * 3. Check for available updates
 * 4. Download and verify new version
 * 5. Stage update for next launch
 * 6. Notify user
 */
public class SelfUpdateCoordinator {
    
    private static final String CONFIG_DIR = "config/ModUpdater/";
    private static final String CONFIG_FILE = CONFIG_DIR + "self_update.json";
    private static final String VERSION_FILE = CONFIG_DIR + "modupdater_version.txt";
    private static final String LAST_CHECK_FILE = CONFIG_DIR + "last_update_check.txt";
    private static final String STAGING_DIR = CONFIG_DIR + "staging/";
    
    // Default fallback version - should match current release
    // This is used when version cannot be determined from JAR manifest
    private static final String FALLBACK_VERSION = "2.1.0";
    
    /**
     * Logger interface for output messages
     */
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    private final GuiUpdater gui;
    private SelfUpdateConfig config;
    
    public SelfUpdateCoordinator(Logger logger, GuiUpdater gui) {
        this.logger = logger;
        this.gui = gui;
    }
    
    /**
     * Check for and apply self-updates if available
     * 
     * @return true if an update was found and staged
     */
    public boolean checkAndUpdate() {
        try {
            // Load configuration
            config = loadConfig();
            
            if (!config.isEnabled()) {
                logger.log("Self-update is disabled in configuration");
                return false;
            }
            
            logger.log("Self-update check enabled");
            
            // Check if we should check for updates based on interval
            if (!shouldCheckForUpdates()) {
                logger.log("Skipping update check (checked recently)");
                return false;
            }
            
            // Get current version
            String currentVersion = getCurrentVersion();
            logger.log("Current ModUpdater version: " + currentVersion);
            
            // Fetch latest version manifest
            ManifestFetcher fetcher = new ManifestFetcher(
                new ManifestFetcher.Logger() {
                    public void log(String message) {
                        logger.log(message);
                    }
                }
            );
            UpdateManifest manifest = fetcher.fetchLatest(config);
            
            logger.log("Latest available version: " + manifest.getVersion());
            
            // Compare versions
            VersionComparator comparator = new VersionComparator();
            int comparison = comparator.compare(currentVersion, manifest.getVersion());
            
            if (comparison >= 0) {
                logger.log("Already running latest version");
                updateLastCheckTime();
                return false;
            }
            
            logger.log("Update available: " + currentVersion + " -> " + manifest.getVersion());
            
            // Show update notification to user
            if (!config.isAutoInstall()) {
                boolean userAccepted = promptUserForUpdate(manifest);
                if (!userAccepted) {
                    logger.log("User declined update");
                    updateLastCheckTime();
                    return false;
                }
            }
            
            // Download and verify the update
            logger.log("Downloading update...");
            File stagingDir = new File(STAGING_DIR);
            if (!stagingDir.exists()) {
                stagingDir.mkdirs();
            }
            
            SelfUpdateDownloader downloader = new SelfUpdateDownloader(
                new SelfUpdateDownloader.Logger() {
                    public void log(String message) {
                        logger.log(message);
                    }
                },
                gui
            );
            File newJar = downloader.downloadAndVerify(manifest, stagingDir);
            
            // Find current JAR location
            File currentJar = findCurrentJar();
            if (currentJar == null) {
                logger.log("Warning: Could not locate current JAR, will perform clean install");
                // Use pattern-based default location - the exclamation marks ensure early loading
                // The actual version in filename doesn't matter as it will be replaced
                currentJar = new File("mods/!!!!!modupdater.jar");
            }
            
            // Install bootstrap for next launch
            logger.log("Installing update bootstrap...");
            BootstrapInstaller installer = new BootstrapInstaller(
                new BootstrapInstaller.Logger() {
                    public void log(String message) {
                        logger.log(message);
                    }
                }
            );
            boolean success = installer.installBootstrap(currentJar, newJar, stagingDir);
            
            if (success) {
                logger.log("Update staged successfully!");
                logger.log("Update will be applied on next Minecraft launch");
                
                // Save new version info
                saveVersion(manifest.getVersion());
                updateLastCheckTime();
                
                // Show success notification
                if (gui != null) {
                    gui.show("ModUpdater will be updated to version " + manifest.getVersion() + " on next launch");
                }
                
                return true;
            } else {
                logger.log("Failed to stage update");
                return false;
            }
            
        } catch (Exception e) {
            logger.log("Self-update failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Load configuration from file or create default
     */
    private SelfUpdateConfig loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (!configFile.exists()) {
                logger.log("No self-update config found, creating default");
                SelfUpdateConfig defaultConfig = SelfUpdateConfig.createDefault();
                saveConfig(defaultConfig);
                return defaultConfig;
            }
            
            JSONObject json = FileUtils.readJson(CONFIG_FILE);
            return SelfUpdateConfig.fromJson(json);
        } catch (Exception e) {
            logger.log("Failed to load config, using defaults: " + e.getMessage());
            return SelfUpdateConfig.createDefault();
        }
    }
    
    /**
     * Save configuration to file
     */
    private void saveConfig(SelfUpdateConfig config) {
        try {
            File configFile = new File(CONFIG_FILE);
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            JSONObject json = config.toJson();
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.log("Failed to save config: " + e.getMessage());
        }
    }
    
    /**
     * Get the current ModUpdater version
     */
    private String getCurrentVersion() {
        try {
            File versionFile = new File(VERSION_FILE);
            if (versionFile.exists()) {
                return FileUtils.readAppliedVersion(VERSION_FILE);
            }
        } catch (Exception e) {
            logger.log("Failed to read version file: " + e.getMessage());
        }
        
        // Try to get version from JAR manifest
        Package pkg = this.getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        
        // Return fallback version constant
        return FALLBACK_VERSION;
    }
    
    /**
     * Save the current version to file
     */
    private void saveVersion(String version) {
        try {
            FileUtils.writeAppliedVersion(VERSION_FILE, version);
        } catch (Exception e) {
            logger.log("Failed to save version: " + e.getMessage());
        }
    }
    
    /**
     * Check if we should check for updates based on last check time
     */
    private boolean shouldCheckForUpdates() {
        try {
            File lastCheckFile = new File(LAST_CHECK_FILE);
            if (!lastCheckFile.exists()) {
                return true;
            }
            
            String lastCheckStr = FileUtils.readAppliedVersion(LAST_CHECK_FILE);
            long lastCheck = Long.parseLong(lastCheckStr);
            long now = System.currentTimeMillis();
            long hoursSinceCheck = (now - lastCheck) / (1000 * 60 * 60);
            
            return hoursSinceCheck >= config.getCheckIntervalHours();
        } catch (Exception e) {
            return true; // If we can't read, check anyway
        }
    }
    
    /**
     * Update the last check timestamp
     */
    private void updateLastCheckTime() {
        try {
            File lastCheckFile = new File(LAST_CHECK_FILE);
            File parent = lastCheckFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            try (FileOutputStream fos = new FileOutputStream(lastCheckFile)) {
                fos.write(timestamp.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.log("Failed to update check time: " + e.getMessage());
        }
    }
    
    /**
     * Find the current ModUpdater JAR file
     */
    private File findCurrentJar() {
        try {
            // Try to get the JAR from which this class was loaded
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (path != null && path.endsWith(".jar")) {
                File jar = new File(path);
                if (jar.exists()) {
                    logger.log("Found current JAR: " + jar.getAbsolutePath());
                    return jar;
                }
            }
        } catch (Exception e) {
            logger.log("Could not determine current JAR location: " + e.getMessage());
        }
        
        // Fallback: search in mods directory
        File modsDir = new File("mods");
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] files = modsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.contains("modupdater") && name.endsWith(".jar")) {
                        logger.log("Found current JAR in mods: " + file.getAbsolutePath());
                        return file;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Prompt user to accept or decline update
     */
    private boolean promptUserForUpdate(UpdateManifest manifest) {
        if (gui == null) {
            // No GUI available, assume acceptance
            return true;
        }
        
        // Build message
        StringBuilder message = new StringBuilder();
        message.append("A new version of ModUpdater is available!\n\n");
        message.append("Current version: ").append(getCurrentVersion()).append("\n");
        message.append("New version: ").append(manifest.getVersion()).append("\n\n");
        
        if (manifest.getChangelog() != null && !manifest.getChangelog().isEmpty()) {
            message.append("Changes:\n");
            // Limit changelog to first 200 characters
            String changelog = manifest.getChangelog();
            if (changelog.length() > 200) {
                changelog = changelog.substring(0, 200) + "...";
            }
            message.append(changelog).append("\n\n");
        }
        
        message.append("Update will be applied on next Minecraft launch.\n");
        message.append("Would you like to download this update?");
        
        // For now, just show the message and return true
        // In a real implementation, this would show a dialog with Yes/No buttons
        gui.show(message.toString());
        
        // TODO: Implement actual user prompt dialog
        // For now, default to accepting the update
        return true;
    }
    
    /**
     * Check if there's a pending update that needs to be applied
     */
    public static boolean hasPendingUpdate() {
        File stagingDir = new File(STAGING_DIR);
        return BootstrapInstaller.hasPendingUpdate(stagingDir);
    }
    
    /**
     * Execute any pending updates (called early in launch)
     */
    public static boolean executePendingUpdate(Logger logger) {
        try {
            File stagingDir = new File(STAGING_DIR);
            return BootstrapInstaller.executeBootstrap(
                stagingDir, 
                new BootstrapInstaller.Logger() {
                    public void log(String message) {
                        logger.log(message);
                    }
                }
            );
        } catch (Exception e) {
            logger.log("Failed to execute pending update: " + e.getMessage());
            return false;
        }
    }
}
