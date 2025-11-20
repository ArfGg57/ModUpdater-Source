package com.ArfGg57.modupdater.deletion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DeletionConfig: Model for the new deletes.json schema.
 * 
 * Supports version-specific deletions with version ranges,
 * file vs folder distinction, and safetyMode flag.
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class DeletionConfig {
    
    private final boolean safetyMode;
    private final List<DeletionEntry> deletions;
    
    public DeletionConfig(boolean safetyMode, List<DeletionEntry> deletions) {
        this.safetyMode = safetyMode;
        this.deletions = deletions != null ? deletions : new ArrayList<>();
    }
    
    public boolean isSafetyMode() {
        return safetyMode;
    }
    
    public List<DeletionEntry> getDeletions() {
        return deletions;
    }
    
    /**
     * Parse a DeletionConfig from a JSONObject.
     * 
     * @param json JSONObject representing deletes.json
     * @return Parsed DeletionConfig
     */
    public static DeletionConfig fromJson(JSONObject json) {
        boolean safetyMode = json.optBoolean("safetyMode", false);
        List<DeletionEntry> deletions = new ArrayList<>();
        
        JSONArray deletionsArr = json.optJSONArray("deletions");
        if (deletionsArr != null) {
            for (int i = 0; i < deletionsArr.length(); i++) {
                JSONObject delObj = deletionsArr.optJSONObject(i);
                if (delObj != null) {
                    DeletionEntry entry = DeletionEntry.fromJson(delObj);
                    if (entry != null) {
                        deletions.add(entry);
                    }
                }
            }
        }
        
        return new DeletionConfig(safetyMode, deletions);
    }
    
    /**
     * DeletionEntry: Represents a set of paths to delete at a specific version.
     */
    public static class DeletionEntry {
        private final String version;
        private final List<PathEntry> paths;
        
        public DeletionEntry(String version, List<PathEntry> paths) {
            this.version = version;
            this.paths = paths != null ? paths : new ArrayList<>();
        }
        
        public String getVersion() {
            return version;
        }
        
        public List<PathEntry> getPaths() {
            return paths;
        }
        
        public static DeletionEntry fromJson(JSONObject json) {
            String version = json.optString("version", "");
            if (version.trim().isEmpty()) {
                return null; // Invalid entry
            }
            
            List<PathEntry> paths = new ArrayList<>();
            JSONArray pathsArr = json.optJSONArray("paths");
            if (pathsArr != null) {
                for (int i = 0; i < pathsArr.length(); i++) {
                    JSONObject pathObj = pathsArr.optJSONObject(i);
                    if (pathObj != null) {
                        PathEntry pathEntry = PathEntry.fromJson(pathObj);
                        if (pathEntry != null) {
                            paths.add(pathEntry);
                        }
                    }
                }
            }
            
            return new DeletionEntry(version, paths);
        }
    }
    
    /**
     * PathEntry: Represents a single path to delete (file or folder).
     */
    public static class PathEntry {
        private final PathType type;
        private final String path;
        
        public PathEntry(PathType type, String path) {
            this.type = type;
            this.path = path;
        }
        
        public PathType getType() {
            return type;
        }
        
        public String getPath() {
            return path;
        }
        
        public static PathEntry fromJson(JSONObject json) {
            String typeStr = json.optString("type", "").toLowerCase().trim();
            String path = json.optString("path", "").trim();
            
            if (path.isEmpty()) {
                return null; // Invalid entry
            }
            
            PathType type;
            if ("file".equals(typeStr)) {
                type = PathType.FILE;
            } else if ("folder".equals(typeStr)) {
                type = PathType.FOLDER;
            } else {
                return null; // Invalid type
            }
            
            return new PathEntry(type, path);
        }
    }
    
    /**
     * PathType: Enum for file vs folder.
     */
    public enum PathType {
        FILE,
        FOLDER
    }
}
