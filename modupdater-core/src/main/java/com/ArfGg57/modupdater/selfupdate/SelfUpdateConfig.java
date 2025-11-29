package com.ArfGg57.modupdater.selfupdate;

import org.json.JSONObject;

/**
 * Configuration class for ModUpdater self-update feature.
 */
public class SelfUpdateConfig {
    
    private final boolean enabled;
    private final String sourceType;
    private final String githubRepo;
    private final String manifestUrl;
    private final boolean requireSignature;
    private final boolean autoInstall;
    private final int checkIntervalHours;
    private final String publicKeyPath;
    
    /**
     * Create a new SelfUpdateConfig with all parameters.
     */
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
     * Create a default configuration.
     */
    public static SelfUpdateConfig createDefault() {
        return new SelfUpdateConfig(
            true,
            "github_releases",
            "ArfGg57/ModUpdater-Source",
            null,
            false,
            false,
            24,
            null
        );
    }
    
    /**
     * Create a SelfUpdateConfig from a JSON object.
     */
    public static SelfUpdateConfig fromJson(JSONObject json) {
        return new SelfUpdateConfig(
            json.optBoolean("enabled", true),
            json.optString("source_type", "github_releases"),
            json.optString("github_repo", "ArfGg57/ModUpdater-Source"),
            json.optString("manifest_url", null),
            json.optBoolean("require_signature", false),
            json.optBoolean("auto_install", false),
            json.optInt("check_interval_hours", 24),
            json.optString("public_key_path", null)
        );
    }
    
    /**
     * Convert this configuration to a JSON object.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("enabled", enabled);
        json.put("source_type", sourceType);
        json.put("github_repo", githubRepo);
        if (manifestUrl != null) {
            json.put("manifest_url", manifestUrl);
        }
        json.put("require_signature", requireSignature);
        json.put("auto_install", autoInstall);
        json.put("check_interval_hours", checkIntervalHours);
        if (publicKeyPath != null) {
            json.put("public_key_path", publicKeyPath);
        }
        return json;
    }
    
    // Getters
    
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
}
