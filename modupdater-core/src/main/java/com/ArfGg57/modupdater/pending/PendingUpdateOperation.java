package com.ArfGg57.modupdater.pending;

import org.json.JSONObject;

/**
 * Represents a pending update operation that needs to be completed after a restart.
 * This is used when a file cannot be deleted due to it being locked by Forge.
 */
public class PendingUpdateOperation {
    
    /**
     * The type of pending operation.
     */
    public enum OperationType {
        DELETE,      // Just delete the file (from deletes.json or mod removal)
        UPDATE       // Delete old file and download new file (mod update)
    }
    
    private final OperationType type;
    private final String oldFilePath;      // Path to the file that needs to be deleted
    private final String newFileUrl;       // URL to download the new file (null for DELETE operations)
    private final String newFileName;      // Name of the new file (null for DELETE operations)
    private final String installLocation;  // Where to install the new file (null for DELETE operations)
    private final String expectedHash;     // Expected SHA-256 hash of the new file (null for DELETE operations)
    private final String reason;           // Human-readable reason for the operation
    
    /**
     * Create a DELETE operation.
     */
    public static PendingUpdateOperation createDelete(String oldFilePath, String reason) {
        return new PendingUpdateOperation(OperationType.DELETE, oldFilePath, null, null, null, null, reason);
    }
    
    /**
     * Create an UPDATE operation (delete old file and download new file).
     */
    public static PendingUpdateOperation createUpdate(String oldFilePath, String newFileUrl, 
            String newFileName, String installLocation, String expectedHash, String reason) {
        return new PendingUpdateOperation(OperationType.UPDATE, oldFilePath, newFileUrl, 
            newFileName, installLocation, expectedHash, reason);
    }
    
    private PendingUpdateOperation(OperationType type, String oldFilePath, String newFileUrl,
            String newFileName, String installLocation, String expectedHash, String reason) {
        this.type = type;
        this.oldFilePath = oldFilePath;
        this.newFileUrl = newFileUrl;
        this.newFileName = newFileName;
        this.installLocation = installLocation;
        this.expectedHash = expectedHash;
        this.reason = reason;
    }
    
    public OperationType getType() {
        return type;
    }
    
    public String getOldFilePath() {
        return oldFilePath;
    }
    
    public String getNewFileUrl() {
        return newFileUrl;
    }
    
    public String getNewFileName() {
        return newFileName;
    }
    
    public String getInstallLocation() {
        return installLocation;
    }
    
    public String getExpectedHash() {
        return expectedHash;
    }
    
    public String getReason() {
        return reason;
    }
    
    /**
     * Convert to JSON for persistence.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", type.name());
        json.put("oldFilePath", oldFilePath);
        if (newFileUrl != null) {
            json.put("newFileUrl", newFileUrl);
        }
        if (newFileName != null) {
            json.put("newFileName", newFileName);
        }
        if (installLocation != null) {
            json.put("installLocation", installLocation);
        }
        if (expectedHash != null) {
            json.put("expectedHash", expectedHash);
        }
        if (reason != null) {
            json.put("reason", reason);
        }
        return json;
    }
    
    /**
     * Create from JSON.
     */
    public static PendingUpdateOperation fromJson(JSONObject json) {
        OperationType type = OperationType.valueOf(json.getString("type"));
        String oldFilePath = json.getString("oldFilePath");
        String newFileUrl = json.optString("newFileUrl", null);
        String newFileName = json.optString("newFileName", null);
        String installLocation = json.optString("installLocation", null);
        String expectedHash = json.optString("expectedHash", null);
        String reason = json.optString("reason", null);
        
        return new PendingUpdateOperation(type, oldFilePath, newFileUrl, 
            newFileName, installLocation, expectedHash, reason);
    }
}
