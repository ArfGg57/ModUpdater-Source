package com.ArfGg57.modupdater.metadata;

import com.ArfGg57.modupdater.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * ModMetadata: Tracks installed mods by their source identifiers (projectId, versionId, etc.)
 * This allows us to identify mods even when filenames change.
 */
public class ModMetadata {

    private Map<String, ModEntry> installedMods; // key: numberId
    private String metadataFilePath;

    public ModMetadata(String metadataFilePath) {
        this.metadataFilePath = metadataFilePath;
        this.installedMods = new LinkedHashMap<>();
        load();
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
                return;
            }
            JSONObject root = FileUtils.readJson(metadataFilePath);
            JSONArray modsArray = root.optJSONArray("mods");
            if (modsArray == null) {
                installedMods = new LinkedHashMap<>();
                return;
            }
            installedMods = new LinkedHashMap<>();
            for (int i = 0; i < modsArray.length(); i++) {
                ModEntry entry = ModEntry.fromJson(modsArray.getJSONObject(i));
                installedMods.put(entry.numberId, entry);
            }
        } catch (Exception e) {
            installedMods = new LinkedHashMap<>();
        }
    }

    /**
     * Save metadata to disk
     */
    public void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray modsArray = new JSONArray();
            for (ModEntry entry : installedMods.values()) {
                modsArray.put(entry.toJson());
            }
            root.put("mods", modsArray);

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
    }
}
