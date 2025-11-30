package com.ArfGg57.modupdater.model;

import org.json.JSONObject;

/**
 * FileArtifact: Unified model for all downloadable artifacts (mods, files, configs).
 * 
 * Encapsulates artifact metadata and provides a consistent interface for
 * download planning and action classification.
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class FileArtifact {
    
    private final String logicalName;        // Base name from config (may lack extension)
    private final String resolvedName;       // Final filename with extension
    private final String version;            // Version string from config (or "unknown")
    private final String downloadUrl;        // URL to download from
    private final boolean overwrite;         // Whether to overwrite on version change
    private final ArtifactType artifactType; // MOD or FILE
    private final String installLocation;    // Target directory
    private final String expectedHash;       // Expected SHA-256 hash (may be empty)
    private final JSONObject source;         // Source info (for mods: CurseForge, Modrinth, URL)
    private final String id;                 // Unique identifier (for mods) - renamed from numberId
    private final boolean extract;           // Whether to extract after download (for zip files)
    
    private FileArtifact(Builder builder) {
        this.logicalName = builder.logicalName;
        this.resolvedName = builder.resolvedName;
        this.version = builder.version;
        this.downloadUrl = builder.downloadUrl;
        this.overwrite = builder.overwrite;
        this.artifactType = builder.artifactType;
        this.installLocation = builder.installLocation;
        this.expectedHash = builder.expectedHash;
        this.source = builder.source;
        this.id = builder.id;
        this.extract = builder.extract;
    }
    
    public String getLogicalName() {
        return logicalName;
    }
    
    public String getResolvedName() {
        return resolvedName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public boolean isOverwrite() {
        return overwrite;
    }
    
    public ArtifactType getArtifactType() {
        return artifactType;
    }
    
    public String getInstallLocation() {
        return installLocation;
    }
    
    public String getExpectedHash() {
        return expectedHash;
    }
    
    public JSONObject getSource() {
        return source;
    }
    
    public String getId() {
        return id;
    }
    
    /**
     * @deprecated Use getId() instead
     */
    @Deprecated
    public String getNumberId() {
        return id;
    }
    
    public boolean isExtract() {
        return extract;
    }
    
    /**
     * Artifact type enumeration
     */
    public enum ArtifactType {
        MOD,
        FILE
    }
    
    /**
     * Builder for FileArtifact
     */
    public static class Builder {
        private String logicalName;
        private String resolvedName;
        private String version = "unknown";
        private String downloadUrl;
        private boolean overwrite = true;
        private ArtifactType artifactType = ArtifactType.FILE;
        private String installLocation = "config/";
        private String expectedHash = "";
        private JSONObject source;
        private String id;
        private boolean extract = false;
        
        public Builder logicalName(String logicalName) {
            this.logicalName = logicalName;
            return this;
        }
        
        public Builder resolvedName(String resolvedName) {
            this.resolvedName = resolvedName;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder downloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }
        
        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }
        
        public Builder artifactType(ArtifactType artifactType) {
            this.artifactType = artifactType;
            return this;
        }
        
        public Builder installLocation(String installLocation) {
            this.installLocation = installLocation;
            return this;
        }
        
        public Builder expectedHash(String expectedHash) {
            this.expectedHash = expectedHash;
            return this;
        }
        
        public Builder source(JSONObject source) {
            this.source = source;
            return this;
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        /**
         * @deprecated Use id() instead
         */
        @Deprecated
        public Builder numberId(String numberId) {
            this.id = numberId;
            return this;
        }
        
        public Builder extract(boolean extract) {
            this.extract = extract;
            return this;
        }
        
        public FileArtifact build() {
            // Validation
            if (resolvedName == null || resolvedName.isEmpty()) {
                throw new IllegalStateException("resolvedName is required");
            }
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new IllegalStateException("downloadUrl is required");
            }
            if (installLocation == null || installLocation.isEmpty()) {
                throw new IllegalStateException("installLocation is required");
            }
            
            return new FileArtifact(this);
        }
    }
    
    @Override
    public String toString() {
        return "FileArtifact{" +
                "resolvedName='" + resolvedName + '\'' +
                ", version='" + version + '\'' +
                ", type=" + artifactType +
                ", location='" + installLocation + '\'' +
                '}';
    }
}
