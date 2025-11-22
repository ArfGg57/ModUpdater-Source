package com.ArfGg57.modupdater.selfupdate;

import org.json.JSONObject;

import java.io.File;

/**
 * Configuration for ModUpdater self-update feature.
 * Loaded from config/ModUpdater/self_update.json or embedded defaults.
 */
public class SelfUpdateConfig {
    
    // Default configuration values
    private static final boolean DEFAULT_ENABLED = true;
    private static final String DEFAULT_SOURCE_TYPE = "github_releases";
    private static final String DEFAULT_GITHUB_REPO = "ArfGg57/ModUpdater-Source";
    private static final String DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/ArfGg57/ModUpdater-Source/main/manifest.json";
    private static final boolean DEFAULT_REQUIRE_SIGNATURE = false;
    private static final boolean DEFAULT_AUTO_INSTALL = false;  // If true, auto-install without user prompt
    private static final int DEFAULT_CHECK_INTERVAL_HOURS = 24;
    
    private final boolean enabled;
    private final String sourceType;  // "github_releases" or "static_manifest"
    private final String githubRepo;
    private final String manifestUrl;
    private final boolean requireSignature;
    private final boolean autoInstall;
    private final int checkIntervalHours;
    private final String publicKeyPath;  // Path to public key for signature verification
    
    public SelfUpdateConfig(boolean enabled, String sourceType, String githubRepo, 
                           String manifestUrl, boolean requireSignature, boolean autoInstall,
                           int checkIntervalHours, String publicKeyPath) {
        this.enabled = enabled;
        this.sourceType = sourceType;
        this.githubRepo = githubRepo;
        this.manifestUrl = manifestUrl;
        this.requireSignature = requireSignature;
        this.autoInstall = autoInstall;
        this.checkIntervalHours = checkIntervalHours;
        this.publicKeyPath = publicKeyPath;
    }
    
    /**
     * Create a config with default values
     */
    public static SelfUpdateConfig createDefault() {
        return new SelfUpdateConfig(
            DEFAULT_ENABLED,
            DEFAULT_SOURCE_TYPE,
            DEFAULT_GITHUB_REPO,
            DEFAULT_MANIFEST_URL,
            DEFAULT_REQUIRE_SIGNATURE,
            DEFAULT_AUTO_INSTALL,
            DEFAULT_CHECK_INTERVAL_HOURS,
            null
        );
    }
    
    /**
     * Load config from JSON object
     */
    public static SelfUpdateConfig fromJson(JSONObject json) {
        boolean enabled = json.optBoolean("enabled", DEFAULT_ENABLED);
        String sourceType = json.optString("source_type", DEFAULT_SOURCE_TYPE);
        String githubRepo = json.optString("github_repo", DEFAULT_GITHUB_REPO);
        String manifestUrl = json.optString("manifest_url", DEFAULT_MANIFEST_URL);
        boolean requireSignature = json.optBoolean("require_signature", DEFAULT_REQUIRE_SIGNATURE);
        boolean autoInstall = json.optBoolean("auto_install", DEFAULT_AUTO_INSTALL);
        int checkIntervalHours = json.optInt("check_interval_hours", DEFAULT_CHECK_INTERVAL_HOURS);
        String publicKeyPath = json.optString("public_key_path", null);
        
        return new SelfUpdateConfig(enabled, sourceType, githubRepo, manifestUrl,
                                   requireSignature, autoInstall, checkIntervalHours, publicKeyPath);
    }
    
    /**
     * Convert config to JSON object for saving
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("enabled", enabled);
        json.put("source_type", sourceType);
        json.put("github_repo", githubRepo);
        json.put("manifest_url", manifestUrl);
        json.put("require_signature", requireSignature);
        json.put("auto_install", autoInstall);
        json.put("check_interval_hours", checkIntervalHours);
        if (publicKeyPath != null) {
            json.put("public_key_path", publicKeyPath);
        }
        return json;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getSourceType() {
        return sourceType;
    }
    
    public String getGithubRepo() {
        return githubRepo;
    }
    
    public String getManifestUrl() {
        return manifestUrl;
    }
    
    public boolean isRequireSignature() {
        return requireSignature;
    }
    
    public boolean isAutoInstall() {
        return autoInstall;
    }
    
    public int getCheckIntervalHours() {
        return checkIntervalHours;
    }
    
    public String getPublicKeyPath() {
        return publicKeyPath;
    }
    
    @Override
    public String toString() {
        return "SelfUpdateConfig{" +
                "enabled=" + enabled +
                ", sourceType='" + sourceType + '\'' +
                ", githubRepo='" + githubRepo + '\'' +
                ", manifestUrl='" + manifestUrl + '\'' +
                ", requireSignature=" + requireSignature +
                ", autoInstall=" + autoInstall +
                ", checkIntervalHours=" + checkIntervalHours +
                ", publicKeyPath='" + publicKeyPath + '\'' +
                '}';
    }
}
