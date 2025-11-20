package com.ArfGg57.modupdater.hash;

import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.metadata.ModMetadata;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * RenamedFileResolver: Centralized hash-based file resolution and rename detection.
 * 
 * This class consolidates all hash-based rename detection logic into a single, 
 * reusable utility. It builds a hash index once per run and provides methods to 
 * resolve files by hash, supporting early exit on first match.
 * 
 * Key features:
 * - One-time hash index construction from metadata
 * - Safe hashing with consistent error handling
 * - Callback interface for logging
 * - Early exit to avoid multiple matches for identical JARs
 */
public class RenamedFileResolver {
    
    /**
     * Callback interface for logging operations
     */
    public interface Logger {
        void log(String message);
    }
    
    private final ModMetadata metadata;
    private final Logger logger;
    private final Map<String, ModMetadata.ModEntry> hashIndex;
    
    /**
     * Create a new RenamedFileResolver with the given metadata and logger.
     * 
     * @param metadata The ModMetadata instance to use for resolution
     * @param logger Callback for logging operations (can be null for silent operation)
     */
    public RenamedFileResolver(ModMetadata metadata, Logger logger) {
        this.metadata = metadata;
        this.logger = logger;
        this.hashIndex = new HashMap<>();
        buildHashIndex();
    }
    
    /**
     * Build the hash -> ModEntry index from metadata.
     * This is called once during construction to enable O(1) hash lookups.
     */
    private void buildHashIndex() {
        if (metadata == null) return;
        
        for (ModMetadata.ModEntry entry : metadata.getAllMods()) {
            if (entry.hash != null && !entry.hash.isEmpty()) {
                // Only store first entry for each hash (early exit principle)
                if (!hashIndex.containsKey(entry.hash)) {
                    hashIndex.put(entry.hash, entry);
                }
            }
        }
        
        if (logger != null && !hashIndex.isEmpty()) {
            logger.log("Built hash index with " + hashIndex.size() + " entries");
        }
    }
    
    /**
     * Safely compute the SHA-256 hash of a file with error handling.
     * 
     * @param file The file to hash
     * @return The hex-encoded SHA-256 hash, or null if hashing failed
     */
    public String safeSha256Hex(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        
        try {
            return HashUtils.sha256Hex(file);
        } catch (Exception ex) {
            if (logger != null) {
                logger.log("Warning: Could not hash file " + file.getName() + " (possibly locked): " + ex.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Resolve a file by its hash to find the corresponding ModEntry.
     * 
     * @param file The file to resolve
     * @return The matching ModEntry from metadata, or null if not found
     */
    public ModMetadata.ModEntry resolveByHash(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        
        String hash = safeSha256Hex(file);
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        
        return hashIndex.get(hash);
    }
    
    /**
     * Resolve a file by hash with expected hash comparison.
     * This is useful when you know the expected hash and want to verify it.
     * 
     * @param file The file to check
     * @param expectedHash The expected hash value
     * @return true if the file's hash matches the expected hash
     */
    public boolean verifyHash(File file, String expectedHash) {
        if (file == null || expectedHash == null || expectedHash.isEmpty()) {
            return false;
        }
        
        String actualHash = safeSha256Hex(file);
        if (actualHash == null) {
            return false;
        }
        
        return FileUtils.hashEquals(expectedHash, actualHash);
    }
    
    /**
     * Find a file in a directory by matching its hash against an expected hash.
     * This method scans the directory and returns the first file whose hash matches.
     * 
     * @param directory The directory to scan
     * @param expectedHash The hash to match
     * @param skipFileNames File names to skip (e.g., files already processed)
     * @return The matching file, or null if not found
     */
    public File findFileByHash(File directory, String expectedHash, String... skipFileNames) {
        if (directory == null || !directory.isDirectory() || expectedHash == null || expectedHash.isEmpty()) {
            return null;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        
        // Build set of names to skip
        java.util.Set<String> skipSet = new java.util.HashSet<>();
        if (skipFileNames != null) {
            for (String name : skipFileNames) {
                if (name != null) skipSet.add(name);
            }
        }
        
        for (File candidate : files) {
            if (!candidate.isFile()) continue;
            if (candidate.getName().endsWith(".tmp")) continue;
            if (skipSet.contains(candidate.getName())) continue;
            
            if (verifyHash(candidate, expectedHash)) {
                if (logger != null) {
                    logger.log("Found file by hash: " + candidate.getName());
                }
                return candidate; // Early exit on first match
            }
        }
        
        return null;
    }
    
    /**
     * Check if a file belongs to a specific mod (by numberId).
     * This checks both the metadata entry and performs hash verification.
     * 
     * @param file The file to check
     * @param numberId The mod's numberId to check against
     * @return true if the file belongs to the specified mod
     */
    public boolean belongsToMod(File file, String numberId) {
        if (file == null || numberId == null || numberId.isEmpty()) {
            return false;
        }
        
        ModMetadata.ModEntry entry = resolveByHash(file);
        if (entry == null) {
            return false;
        }
        
        return numberId.equals(entry.numberId);
    }
    
    /**
     * Find all files in a directory that match any hash in the metadata for a specific numberId.
     * This is useful for cleanup operations where you need to find all versions of a mod.
     * 
     * @param directory The directory to scan
     * @param numberId The numberId to find files for
     * @return An array of files belonging to the specified mod (may be empty, never null)
     */
    public File[] findAllFilesForMod(File directory, String numberId) {
        if (directory == null || !directory.isDirectory() || numberId == null || numberId.isEmpty()) {
            return new File[0];
        }
        
        java.util.List<File> results = new java.util.ArrayList<>();
        
        // First check metadata for tracked file
        ModMetadata.ModEntry entry = metadata.getMod(numberId);
        if (entry != null && entry.fileName != null && !entry.fileName.isEmpty()) {
            File trackedFile = new File(directory, entry.fileName);
            if (trackedFile.exists() && trackedFile.isFile()) {
                results.add(trackedFile);
            } else if (entry.hash != null && !entry.hash.isEmpty()) {
                // File from metadata doesn't exist - try to find by hash
                File renamedFile = findFileByHash(directory, entry.hash);
                if (renamedFile != null) {
                    results.add(renamedFile);
                }
            }
        }
        
        // Also check for legacy numberId- prefix files
        File[] allFiles = directory.listFiles();
        if (allFiles != null) {
            for (File f : allFiles) {
                if (!f.isFile()) continue;
                if (f.getName().endsWith(".tmp")) continue;
                if (f.getName().startsWith(numberId + "-")) {
                    if (!results.contains(f)) {
                        results.add(f);
                    }
                }
            }
        }
        
        return results.toArray(new File[0]);
    }
    
    /**
     * Check if a file is tracked by any mod in the metadata.
     * 
     * @param file The file to check
     * @return The numberId of the mod that owns this file, or null if not tracked
     */
    public String getOwnerNumberId(File file) {
        if (file == null) return null;
        
        // First check by filename
        for (ModMetadata.ModEntry entry : metadata.getAllMods()) {
            if (entry.fileName != null && entry.fileName.equals(file.getName())) {
                return entry.numberId;
            }
        }
        
        // Then check by hash
        ModMetadata.ModEntry entry = resolveByHash(file);
        if (entry != null) {
            return entry.numberId;
        }
        
        return null;
    }
}
