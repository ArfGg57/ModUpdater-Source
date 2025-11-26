package com.ArfGg57.modupdater.selfupdate;

import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.version.VersionComparator;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fetches update manifest information from either GitHub Releases API or static manifest JSON.
 */
public class ManifestFetcher {
    
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";
    
    /**
     * Logger interface for output messages
     */
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    
    public ManifestFetcher(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Fetch the latest update manifest based on configuration
     */
    public UpdateManifest fetchLatest(SelfUpdateConfig config) throws Exception {
        if ("github_releases".equals(config.getSourceType())) {
            return fetchFromGitHubReleases(config.getGithubRepo());
        } else if ("static_manifest".equals(config.getSourceType())) {
            return fetchFromStaticManifest(config.getManifestUrl());
        } else {
            throw new IllegalArgumentException("Unknown source type: " + config.getSourceType());
        }
    }
    
    /**
     * Fetch update manifest from GitHub Releases API
     */
    private UpdateManifest fetchFromGitHubReleases(String repo) throws Exception {
        logger.log("Fetching latest release from GitHub API: " + repo);
        
        String apiUrl = GITHUB_API_BASE + repo + "/releases/latest";
        JSONObject release = FileUtils.readJsonFromUrl(apiUrl);
        
        // Parse release information
        String tagName = release.getString("tag_name");
        String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
        String originalTag = version; // keep original for logging
        String changelog = release.optString("body", "");
        boolean prerelease = release.optBoolean("prerelease", false);
        
        logger.log("Found release: " + version + (prerelease ? " (prerelease)" : ""));
        
        // Find the JAR asset
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            
            // Look for the ModUpdater JAR (could be !!!!!modupdater-*.jar or modupdater-*.jar)
            if (name.endsWith(".jar") && name.contains("modupdater")) {
                String downloadUrl = asset.getString("browser_download_url");
                long fileSize = asset.optLong("size", 0);

                // If tag version isn't semantic (e.g. 'TEST'), try to derive from asset filename
                if (VersionComparator.parse(version) == null) {
                    String inferred = deriveVersionFromAssetName(name);
                    if (inferred != null && VersionComparator.parse(inferred) != null) {
                        logger.log("Inferred semantic version '" + inferred + "' from asset name (tag '" + originalTag + "' not semantic)");
                        version = inferred;
                    } else {
                        logger.log("Tag '" + originalTag + "' and asset name did not yield semantic version; treating as same version");
                    }
                }

                logger.log("Found JAR asset: " + name + " (" + fileSize + " bytes)");
                
                // Look for .sha256 file as a separate asset
                String sha256Hash = null;
                String signatureUrl = null;
                
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject hashAsset = assets.getJSONObject(j);
                    String hashName = hashAsset.getString("name");
                    
                    if (hashName.equals(name + ".sha256")) {
                        // Download and read the sha256 hash
                        String hashFileUrl = hashAsset.getString("browser_download_url");
                        try {
                            String hashContent = FileUtils.readUrlToString(hashFileUrl, null);
                            sha256Hash = hashContent.trim().split("\\s+")[0]; // First token is the hash
                            logger.log("Found SHA256 hash: " + sha256Hash);
                        } catch (Exception e) {
                            logger.log("Warning: Could not read SHA256 file: " + e.getMessage());
                        }
                    } else if (hashName.equals(name + ".sig") || hashName.equals(name + ".asc")) {
                        signatureUrl = hashAsset.getString("browser_download_url");
                        logger.log("Found signature file: " + hashName);
                    }
                }
                
                // If no separate hash file, we'll calculate it after download
                if (sha256Hash == null) {
                    logger.log("No SHA256 hash file found, will verify after download");
                    sha256Hash = null; // Keep as null to indicate we need to calculate it
                }
                
                return new UpdateManifest(
                    version,
                    downloadUrl,
                    sha256Hash,
                    signatureUrl,
                    changelog,
                    fileSize,
                    false  // GitHub releases are typically not required
                );
            }
        }
        
        throw new Exception("No suitable JAR asset found in release " + version);
    }
    
    /**
     * Fetch update manifest from static JSON manifest
     */
    private UpdateManifest fetchFromStaticManifest(String manifestUrl) throws Exception {
        logger.log("Fetching manifest from: " + manifestUrl);
        
        JSONObject manifest = FileUtils.readJsonFromUrl(manifestUrl);
        
        // Expected format:
        // {
        //   "modUpdater": {
        //     "latest": "1.1.0",
        //     "download": {
        //       "primary": "https://...",
        //       "sha256": "...",
        //       "signature": "https://... (optional)",
        //       "size": 12345,
        //       "required": false
        //     },
        //     "changelog": "..."
        //   }
        // }
        
        if (!manifest.has("modUpdater")) {
            throw new Exception("Manifest does not contain 'modUpdater' section");
        }
        
        JSONObject modUpdater = manifest.getJSONObject("modUpdater");
        String version = modUpdater.getString("latest");
        
        JSONObject download = modUpdater.getJSONObject("download");
        String downloadUrl = download.getString("primary");
        String sha256Hash = download.getString("sha256");
        String signatureUrl = download.optString("signature", null);
        long fileSize = download.optLong("size", 0);
        boolean required = download.optBoolean("required", false);
        
        String changelog = modUpdater.optString("changelog", "");
        
        logger.log("Found version: " + version);
        logger.log("Download URL: " + downloadUrl);
        
        return new UpdateManifest(
            version,
            downloadUrl,
            sha256Hash,
            signatureUrl,
            changelog,
            fileSize,
            required
        );
    }

    // Attempt to extract a semantic version (digits.digits[.digits]) from asset filename
    private String deriveVersionFromAssetName(String name) {
        if (name == null) return null;
        // Strip leading exclamation marks or prefixes
        String cleaned = name.replaceAll("^!+", "");
        // Common patterns: modupdater-2.21.jar, !!!!!modupdater-2.21.jar
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modupdater-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\\.jar").matcher(cleaned);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
