package com.ArfGg57.modupdater.model;

import java.io.File;

/**
 * PlannedAction: Represents a planned action for a file artifact.
 * 
 * Links an artifact to an action type and provides context for execution.
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class PlannedAction {
    
    private final FileArtifact artifact;
    private final ActionType actionType;
    private final File existingFile;  // For RENAME and UPDATE operations
    private final String reason;       // Human-readable reason for the action
    
    public PlannedAction(FileArtifact artifact, ActionType actionType, File existingFile, String reason) {
        this.artifact = artifact;
        this.actionType = actionType;
        this.existingFile = existingFile;
        this.reason = reason;
    }
    
    public FileArtifact getArtifact() {
        return artifact;
    }
    
    public ActionType getActionType() {
        return actionType;
    }
    
    public File getExistingFile() {
        return existingFile;
    }
    
    public String getReason() {
        return reason;
    }
    
    /**
     * Action type enumeration
     */
    public enum ActionType {
        NEW_DOWNLOAD,   // File doesn't exist locally
        UPDATE,         // File exists but version/hash differs
        RENAME,         // File exists with correct hash but wrong name
        DELETE,         // File should be deleted
        SKIP,           // File exists and overwrite=false
        NO_ACTION,      // File exists with correct version/hash
        DEFERRED        // Action deferred due to file lock
    }
    
    @Override
    public String toString() {
        return "PlannedAction{" +
                "type=" + actionType +
                ", artifact=" + artifact.getResolvedName() +
                ", existing=" + (existingFile != null ? existingFile.getName() : "none") +
                '}';
    }
}
