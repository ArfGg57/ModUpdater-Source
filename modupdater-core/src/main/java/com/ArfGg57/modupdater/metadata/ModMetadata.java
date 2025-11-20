package com.ArfGg57.modupdater.metadata;

import com.ArfGg57.modupdater.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * ModMetadata: Tracks installed mods and auxiliary files by their identifiers.
 * This allows us to identify artifacts even when filenames change and prevents
 * repeated downloads of files that are already present.
 * 
 * Unified manifest format for both mods (with numberId) and auxiliary files (without).
 */
public class ModMetadata {

    private Map<String, ModEntry> installedMods; // key: numberId (for mods)
    private Map<String, ArtifactEntry> installedFiles; // key: fileName (for auxiliary files)
    private Set<String> processedDeletes; // Track completed delete operations by path
    private String metadataFilePath;

    public ModMetadata(String metadataFilePath) {
        this.metadataFilePath = metadataFilePath;
        this.installedMods = new LinkedHashMap<>();
        this.installedFiles = new LinkedHashMap<>();
        this.processedDeletes = new LinkedHashSet<>();
        load();
    }
    
    /**
     * Represents an auxiliary file (config, resource, etc.)
     */
    public static class ArtifactEntry {
        public String fileName;
        public String kind; // "FILE" or "MOD"
        public String version; // may be null for files
        public String checksum; // SHA-256 hash
        public String url; // last download URL
        public String installLocation; // target directory
        
        public ArtifactEntry() {}
        
        public ArtifactEntry(String fileName, String kind, String version, String checksum, String url, String installLocation) {
            this.fileName = fileName;
            this.kind = kind;
            this.version = version;
            this.checksum = checksum;
            this.url = url;
            this.installLocation = installLocation;
        }
        
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("fileName", fileName);
            obj.put("kind", kind);
            if (version != null && !version.isEmpty()) obj.put("version", version);
            if (checksum != null && !checksum.isEmpty()) obj.put("checksum", checksum);
            if (url != null && !url.isEmpty()) obj.put("url", url);
            if (installLocation != null && !installLocation.isEmpty()) obj.put("installLocation", installLocation);
            return obj;
        }
        
        public static ArtifactEntry fromJson(JSONObject obj) {
            ArtifactEntry entry = new ArtifactEntry();
            entry.fileName = obj.optString("fileName", "");
            entry.kind = obj.optString("kind", "FILE");
            entry.version = obj.optString("version", null);
            entry.checksum = obj.optString("checksum", "");
            entry.url = obj.optString("url", "");
            entry.installLocation = obj.optString("installLocation", "");
            return entry;
        }
    }

    public static class ModEntry {
        public String numberId;
        public String fileName;
        public String hash;
        public String sourceType; // "curseforge", "modrinth", "url"
        public Integer curseforgeProjectId;
        public Long curseforgeFileId;
        public String modrinthProjectSlug;
        public String modrinthVersionId;
        public String directUrl;

        public ModEntry() {}

        public ModEntry(String numberId, String fileName, String hash) {
            this.numberId = numberId;
            this.fileName = fileName;
            this.hash = hash;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("numberId", numberId);
            obj.put("fileName", fileName);
            if (hash != null && !hash.isEmpty()) obj.put("hash", hash);
            obj.put("sourceType", sourceType);
            if (curseforgeProjectId != null) obj.put("curseforgeProjectId", curseforgeProjectId);
            if (curseforgeFileId != null) obj.put("curseforgeFileId", curseforgeFileId);
            if (modrinthProjectSlug != null) obj.put("modrinthProjectSlug", modrinthProjectSlug);
            if (modrinthVersionId != null) obj.put("modrinthVersionId", modrinthVersionId);
            if (directUrl != null) obj.put("directUrl", directUrl);
            return obj;
        }

        public static ModEntry fromJson(JSONObject obj) {
            ModEntry entry = new ModEntry();
            entry.numberId = obj.optString("numberId", "");
            entry.fileName = obj.optString("fileName", "");
            entry.hash = obj.optString("hash", "");
            entry.sourceType = obj.optString("sourceType", "");
            if (obj.has("curseforgeProjectId")) entry.curseforgeProjectId = obj.getInt("curseforgeProjectId");
            if (obj.has("curseforgeFileId")) entry.curseforgeFileId = obj.getLong("curseforgeFileId");
            if (obj.has("modrinthProjectSlug")) entry.modrinthProjectSlug = obj.getString("modrinthProjectSlug");
            if (obj.has("modrinthVersionId")) entry.modrinthVersionId = obj.getString("modrinthVersionId");
            if (obj.has("directUrl")) entry.directUrl = obj.getString("directUrl");
            return entry;
        }
    }

    /**
     * Load metadata from disk
     */
    private void load() {
        try {
            File file = new File(metadataFilePath);
            if (!file.exists()) {
                installedMods = new LinkedHashMap<>();
                installedFiles = new LinkedHashMap<>();
                processedDeletes = new LinkedHashSet<>();
                return;
            }
            JSONObject root = FileUtils.readJson(metadataFilePath);
            
            // Load mods (backward compatible)
            JSONArray modsArray = root.optJSONArray("mods");
            if (modsArray == null) {
                installedMods = new LinkedHashMap<>();
            } else {
                installedMods = new LinkedHashMap<>();
                for (int i = 0; i < modsArray.length(); i++) {
                    ModEntry entry = ModEntry.fromJson(modsArray.getJSONObject(i));
                    installedMods.put(entry.numberId, entry);
                }
            }
            
            // Load auxiliary files (new unified manifest format)
            JSONArray filesArray = root.optJSONArray("files");
            if (filesArray == null) {
                installedFiles = new LinkedHashMap<>();
            } else {
                installedFiles = new LinkedHashMap<>();
                for (int i = 0; i < filesArray.length(); i++) {
                    ArtifactEntry entry = ArtifactEntry.fromJson(filesArray.getJSONObject(i));
                    installedFiles.put(entry.fileName, entry);
                }
            }
            
            // Load processed deletes (new field)
            JSONArray deletesArray = root.optJSONArray("processedDeletes");
            if (deletesArray == null) {
                processedDeletes = new LinkedHashSet<>();
            } else {
                processedDeletes = new LinkedHashSet<>();
                for (int i = 0; i < deletesArray.length(); i++) {
                    processedDeletes.add(deletesArray.getString(i));
                }
            }
        } catch (Exception e) {
            installedMods = new LinkedHashMap<>();
            installedFiles = new LinkedHashMap<>();
            processedDeletes = new LinkedHashSet<>();
        }
    }

    /**
     * Save metadata to disk
     */
    public void save() {
        try {
            JSONObject root = new JSONObject();
            
            // Save mods
            JSONArray modsArray = new JSONArray();
            for (ModEntry entry : installedMods.values()) {
                modsArray.put(entry.toJson());
            }
            root.put("mods", modsArray);
            
            // Save auxiliary files
            JSONArray filesArray = new JSONArray();
            for (ArtifactEntry entry : installedFiles.values()) {
                filesArray.put(entry.toJson());
            }
            root.put("files", filesArray);
            
            // Save processed deletes
            JSONArray deletesArray = new JSONArray();
            for (String deletePath : processedDeletes) {
                deletesArray.put(deletePath);
            }
            root.put("processedDeletes", deletesArray);

            File file = new File(metadataFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            // best effort
        }
    }

    /**
     * Record a mod installation
     */
    public void recordMod(String numberId, String fileName, String hash, JSONObject source) {
        ModEntry entry = new ModEntry(numberId, fileName, hash);
        if (source != null) {
            entry.sourceType = source.optString("type", "");
            if ("curseforge".equals(entry.sourceType)) {
                entry.curseforgeProjectId = source.optInt("projectId", -1);
                if (entry.curseforgeProjectId == -1) entry.curseforgeProjectId = null;
                entry.curseforgeFileId = source.optLong("fileId", -1);
                if (entry.curseforgeFileId == -1) entry.curseforgeFileId = null;
            } else if ("modrinth".equals(entry.sourceType)) {
                entry.modrinthProjectSlug = source.optString("projectSlug", null);
                entry.modrinthVersionId = source.optString("versionId", null);
            } else if ("url".equals(entry.sourceType)) {
                entry.directUrl = source.optString("url", null);
            }
        }
        installedMods.put(numberId, entry);
    }

    /**
     * Remove a mod from metadata
     */
    public void removeMod(String numberId) {
        installedMods.remove(numberId);
    }

    /**
     * Get mod entry by numberId
     */
    public ModEntry getMod(String numberId) {
        return installedMods.get(numberId);
    }

    /**
     * Find which file (if any) currently represents a given mod definition from mods.json
     * Returns the filename if found, or null if not installed
     */
    public String findInstalledFile(String numberId) {
        ModEntry entry = installedMods.get(numberId);
        return (entry != null) ? entry.fileName : null;
    }

    /**
     * Check if a mod with this numberId is installed and matches the expected source
     */
    public boolean isModInstalledAndMatches(String numberId, JSONObject expectedSource, String expectedHash) {
        ModEntry entry = installedMods.get(numberId);
        if (entry == null) return false;

        // Check hash if provided
        if (expectedHash != null && !expectedHash.trim().isEmpty()) {
            if (entry.hash == null || entry.hash.trim().isEmpty()) return false;
            if (!FileUtils.hashEquals(expectedHash, entry.hash)) return false;
        }

        // Check source matches
        if (expectedSource == null) return true;
        String type = expectedSource.optString("type", "");
        if (!type.equals(entry.sourceType)) return false;

        if ("curseforge".equals(type)) {
            Integer projectId = expectedSource.optInt("projectId", -1);
            Long fileId = expectedSource.optLong("fileId", -1);
            if (projectId == -1 || fileId == -1) return false;
            return Objects.equals(entry.curseforgeProjectId, projectId) && 
                   Objects.equals(entry.curseforgeFileId, fileId);
        } else if ("modrinth".equals(type)) {
            String versionId = expectedSource.optString("versionId", null);
            if (versionId == null || versionId.trim().isEmpty()) return false;
            return versionId.equals(entry.modrinthVersionId);
        } else if ("url".equals(type)) {
            String url = expectedSource.optString("url", null);
            if (url == null) return false;
            return url.equals(entry.directUrl);
        }

        return false;
    }

    /**
     * Get all installed mod entries
     */
    public Collection<ModEntry> getAllMods() {
        return new ArrayList<>(installedMods.values());
    }

    /**
     * Clear all metadata
     */
    public void clear() {
        installedMods.clear();
        installedFiles.clear();
    }
    
    // ========== Auxiliary File Management Methods ==========
    
    /**
     * Record an auxiliary file installation
     * 
     * @param fileName The actual filename (not display name)
     * @param checksum SHA-256 hash of the file
     * @param url The download URL
     * @param installLocation Target directory (e.g., "config/")
     */
    public void recordFile(String fileName, String checksum, String url, String installLocation) {
        recordFile(fileName, checksum, url, installLocation, null);
    }
    
    /**
     * Record an auxiliary file installation with version
     * 
     * @param fileName The actual filename (not display name)
     * @param checksum SHA-256 hash of the file
     * @param url The download URL
     * @param installLocation Target directory (e.g., "config/")
     * @param version Optional version string (may be null for files without version)
     */
    public void recordFile(String fileName, String checksum, String url, String installLocation, String version) {
        ArtifactEntry entry = new ArtifactEntry(fileName, "FILE", version, checksum, url, installLocation);
        installedFiles.put(fileName, entry);
    }
    
    /**
     * Get auxiliary file entry by filename
     * 
     * @param fileName The filename to look up
     * @return The ArtifactEntry if found, null otherwise
     */
    public ArtifactEntry getFile(String fileName) {
        return installedFiles.get(fileName);
    }
    
    /**
     * Check if an auxiliary file is installed and matches the expected checksum
     * 
     * @param fileName The filename to check
     * @param expectedChecksum The expected SHA-256 hash (can be null/empty)
     * @return true if file is tracked and checksum matches (or no checksum to verify)
     */
    public boolean isFileInstalledAndMatches(String fileName, String expectedChecksum) {
        ArtifactEntry entry = installedFiles.get(fileName);
        if (entry == null) return false;
        
        // If no expected checksum provided, just check presence
        if (expectedChecksum == null || expectedChecksum.trim().isEmpty()) {
            return true;
        }
        
        // Check checksum match
        if (entry.checksum == null || entry.checksum.trim().isEmpty()) {
            return false;
        }
        
        return FileUtils.hashEquals(expectedChecksum, entry.checksum);
    }
    
    /**
     * Check if an auxiliary file is installed and matches both version and checksum.
     * This method supports the overwrite=true logic by checking version changes.
     * 
     * @param fileName The filename to check
     * @param expectedVersion The expected version string (can be null for no version tracking)
     * @param expectedChecksum The expected SHA-256 hash (can be null/empty)
     * @return true if file is tracked and both version and checksum match
     */
    public boolean isFileInstalledAndMatchesVersion(String fileName, String expectedVersion, String expectedChecksum) {
        ArtifactEntry entry = installedFiles.get(fileName);
        if (entry == null) return false;
        
        // Check version match (if version tracking is used)
        if (expectedVersion != null && !expectedVersion.trim().isEmpty()) {
            // If entry has no version stored, treat as not matching (need to update)
            if (entry.version == null || entry.version.trim().isEmpty()) {
                return false;
            }
            // If versions differ, not a match
            if (!expectedVersion.equals(entry.version)) {
                return false;
            }
        }
        
        // Check checksum match if provided
        if (expectedChecksum != null && !expectedChecksum.trim().isEmpty()) {
            if (entry.checksum == null || entry.checksum.trim().isEmpty()) {
                return false;
            }
            if (!FileUtils.hashEquals(expectedChecksum, entry.checksum)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Remove an auxiliary file from metadata
     * 
     * @param fileName The filename to remove
     */
    public void removeFile(String fileName) {
        installedFiles.remove(fileName);
    }
    
    /**
     * Get all installed auxiliary file entries
     * 
     * @return Collection of all tracked auxiliary files
     */
    public Collection<ArtifactEntry> getAllFiles() {
        return new ArrayList<>(installedFiles.values());
    }
    
    // ========== Delete Tracking Methods ==========
    
    /**
     * Mark a delete operation as completed for the given path.
     * This prevents the delete from being re-proposed or re-executed.
     * 
     * @param path The file or folder path that was deleted
     */
    public void markDeleteCompleted(String path) {
        if (path != null && !path.trim().isEmpty()) {
            processedDeletes.add(path.trim());
        }
    }
    
    /**
     * Check if a delete operation has already been completed for the given path.
     * 
     * @param path The file or folder path to check
     * @return true if the delete has already been processed
     */
    public boolean isDeleteCompleted(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return processedDeletes.contains(path.trim());
    }
    
    /**
     * Clear all processed delete records.
     * This is useful for testing or forcing a full re-check.
     */
    public void clearProcessedDeletes() {
        processedDeletes.clear();
    }
}
