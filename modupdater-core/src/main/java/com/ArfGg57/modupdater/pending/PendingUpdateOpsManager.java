package com.ArfGg57.modupdater.pending;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for pending update operations.
 * Operations are stored in config/ModUpdater/pending-update-ops.json and will be
 * processed by the modupdater-mod component after a restart.
 */
public class PendingUpdateOpsManager {
    
    private static final String PENDING_OPS_FILE = "config/ModUpdater/pending-update-ops.json";
    
    private final List<PendingUpdateOperation> operations = new ArrayList<>();
    
    /**
     * Add a pending operation.
     */
    public void addOperation(PendingUpdateOperation op) {
        if (op != null) {
            operations.add(op);
        }
    }
    
    /**
     * Check if there are any pending operations.
     */
    public boolean hasOperations() {
        return !operations.isEmpty();
    }
    
    /**
     * Get all pending operations.
     */
    public List<PendingUpdateOperation> getOperations() {
        return new ArrayList<>(operations);
    }
    
    /**
     * Save pending operations to the file.
     * Returns the path to the saved file.
     */
    public String save() throws IOException {
        Path filePath = Paths.get(PENDING_OPS_FILE);
        
        // Ensure parent directory exists
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        
        // Build JSON
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("timestamp", System.currentTimeMillis());
        
        JSONArray opsArray = new JSONArray();
        for (PendingUpdateOperation op : operations) {
            opsArray.put(op.toJson());
        }
        root.put("operations", opsArray);
        
        // Write to file
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(root.toString(2)); // Pretty print with indent=2
        }
        
        System.out.println("[ModUpdater] Saved " + operations.size() + " pending operation(s) to " + filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }
    
    /**
     * Load pending operations from the file.
     * Returns null if file doesn't exist or is empty.
     */
    public static PendingUpdateOpsManager load() {
        return load(Paths.get(PENDING_OPS_FILE));
    }
    
    /**
     * Load pending operations from a custom path.
     * Returns null if file doesn't exist or is empty.
     * 
     * @param filePath Path to the pending operations file
     */
    public static PendingUpdateOpsManager load(Path filePath) {
        if (!Files.exists(filePath)) {
            return null;
        }
        
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                return null;
            }
            
            JSONObject root = new JSONObject(content);
            JSONArray opsArray = root.optJSONArray("operations");
            
            if (opsArray == null || opsArray.length() == 0) {
                return null;
            }
            
            PendingUpdateOpsManager manager = new PendingUpdateOpsManager();
            for (int i = 0; i < opsArray.length(); i++) {
                JSONObject opJson = opsArray.getJSONObject(i);
                manager.operations.add(PendingUpdateOperation.fromJson(opJson));
            }
            
            System.out.println("[ModUpdater] Loaded " + manager.operations.size() + " pending operation(s) from " + filePath.toAbsolutePath());
            return manager;
            
        } catch (Exception e) {
            System.err.println("[ModUpdater] Failed to load pending operations: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Check if there are any pending operations (static method).
     */
    public static boolean hasPendingOperations() {
        Path filePath = Paths.get(PENDING_OPS_FILE);
        return Files.exists(filePath);
    }
    
    /**
     * Delete the pending operations file.
     * @return true if the file was deleted or didn't exist, false if deletion failed
     */
    public static boolean clearPendingOperations() {
        return clearPendingOperations(Paths.get(PENDING_OPS_FILE));
    }
    
    /**
     * Delete the pending operations file at a custom path.
     * @param filePath Path to the pending operations file
     * @return true if the file was deleted or didn't exist, false if deletion failed
     */
    public static boolean clearPendingOperations(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            System.out.println("[ModUpdater] Cleared pending operations file: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("[ModUpdater] Failed to clear pending operations file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the path to the pending operations file.
     */
    public static String getPendingOpsFilePath() {
        return PENDING_OPS_FILE;
    }
}
