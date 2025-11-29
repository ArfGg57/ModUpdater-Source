package com.ArfGg57.modupdater.selfupdate;

/**
 * Represents an update manifest containing information about an available update.
 */
public class UpdateManifest {
    
    private final String version;
    private final String downloadUrl;
    private final String sha256Hash;
    private final String signatureUrl;
    private final String changelog;
    private final long fileSize;
    private final boolean required;
    
    /**
     * Create a new UpdateManifest with all parameters.
     */
    public UpdateManifest(String version, String downloadUrl, String sha256Hash,
                         String signatureUrl, String changelog, long fileSize,
                         boolean required) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.sha256Hash = sha256Hash;
        this.signatureUrl = signatureUrl;
        this.changelog = changelog;
        this.fileSize = fileSize;
        this.required = required;
    }
    
    // Getters
    
    public String getVersion() {
        return version;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public String getSha256Hash() {
        return sha256Hash;
    }
    
    public String getSignatureUrl() {
        return signatureUrl;
    }
    
    public String getChangelog() {
        return changelog;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    @Override
    public String toString() {
        return "UpdateManifest{" +
               "version='" + version + '\'' +
               ", downloadUrl='" + downloadUrl + '\'' +
               ", sha256Hash='" + sha256Hash + '\'' +
               ", signatureUrl='" + signatureUrl + '\'' +
               ", changelog='" + changelog + '\'' +
               ", fileSize=" + fileSize +
               ", required=" + required +
               '}';
    }
}
