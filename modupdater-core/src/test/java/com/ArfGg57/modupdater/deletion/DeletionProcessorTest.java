package com.ArfGg57.modupdater.deletion;

import com.ArfGg57.modupdater.metadata.ModMetadata;
import com.ArfGg57.modupdater.util.PendingOperations;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for DeletionProcessor
 */
public class DeletionProcessorTest {
    
    private File testDir;
    private File metadataFile;
    private File pendingOpsFile;
    private List<String> logMessages;
    
    @Before
    public void setUp() throws Exception {
        testDir = new File("test-deletion-" + System.currentTimeMillis());
        testDir.mkdirs();
        
        metadataFile = new File(testDir, "metadata.json");
        pendingOpsFile = new File(testDir, "pending-ops.json");
        
        logMessages = new ArrayList<String>();
    }
    
    @After
    public void tearDown() {
        // Clean up test directory
        deleteRecursive(testDir);
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
    
    private DeletionProcessor.Logger createLogger() {
        return new DeletionProcessor.Logger() {
            public void log(String message) {
                logMessages.add(message);
            }
        };
    }
    
    @Test
    public void testVersionRangeLogic() throws Exception {
        // Create test config
        JSONObject config = new JSONObject();
        config.put("safetyMode", false);
        
        JSONArray deletions = new JSONArray();
        
        // Deletion at version 1.5 - should apply
        JSONObject del1 = new JSONObject();
        del1.put("version", "1.5.0");
        JSONArray paths1 = new JSONArray();
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", testDir.getPath() + "/test1.txt");
        paths1.put(path1);
        del1.put("paths", paths1);
        deletions.put(del1);
        
        // Deletion at version 1.2 - should NOT apply (before current)
        JSONObject del2 = new JSONObject();
        del2.put("version", "1.2.0");
        JSONArray paths2 = new JSONArray();
        JSONObject path2 = new JSONObject();
        path2.put("type", "file");
        path2.put("path", testDir.getPath() + "/test2.txt");
        paths2.put(path2);
        del2.put("paths", paths2);
        deletions.put(del2);
        
        // Deletion at version 1.6 (equal to target) - should apply
        JSONObject del3 = new JSONObject();
        del3.put("version", "1.6.0");
        JSONArray paths3 = new JSONArray();
        JSONObject path3 = new JSONObject();
        path3.put("type", "file");
        path3.put("path", testDir.getPath() + "/test3.txt");
        paths3.put(path3);
        del3.put("paths", paths3);
        deletions.put(del3);
        
        // Deletion at version 1.7 (after target) - should NOT apply
        JSONObject del4 = new JSONObject();
        del4.put("version", "1.7.0");
        JSONArray paths4 = new JSONArray();
        JSONObject path4 = new JSONObject();
        path4.put("type", "file");
        path4.put("path", testDir.getPath() + "/test4.txt");
        paths4.put(path4);
        del4.put("paths", paths4);
        deletions.put(del4);
        
        config.put("deletions", deletions);
        
        // Create test files
        createTestFile(testDir.getPath() + "/test1.txt");
        createTestFile(testDir.getPath() + "/test2.txt");
        createTestFile(testDir.getPath() + "/test3.txt");
        createTestFile(testDir.getPath() + "/test4.txt");
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions (current: 1.4, target: 1.6)
        int count = processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // Verify: test1.txt and test3.txt should be deleted, test2.txt and test4.txt should not
        assertFalse("test1.txt should be deleted", new File(testDir, "test1.txt").exists());
        assertTrue("test2.txt should NOT be deleted", new File(testDir, "test2.txt").exists());
        assertFalse("test3.txt should be deleted", new File(testDir, "test3.txt").exists());
        assertTrue("test4.txt should NOT be deleted", new File(testDir, "test4.txt").exists());
        
        assertEquals("Should have deleted 2 files", 2, count);
    }
    
    @Test
    public void testSafetyMode() throws Exception {
        // Create test config with safetyMode enabled
        JSONObject config = new JSONObject();
        config.put("safetyMode", true);
        
        JSONArray deletions = new JSONArray();
        JSONObject del1 = new JSONObject();
        del1.put("version", "1.5.0");
        
        JSONArray paths1 = new JSONArray();
        
        // Path inside config/ - should be allowed
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", "config/test-safe.txt");
        paths1.put(path1);
        
        // Path outside config/ - should be blocked
        JSONObject path2 = new JSONObject();
        path2.put("type", "file");
        path2.put("path", "mods/test-unsafe.txt");
        paths1.put(path2);
        
        del1.put("paths", paths1);
        deletions.put(del1);
        config.put("deletions", deletions);
        
        // Create test files
        File configDir = new File("config");
        configDir.mkdirs();
        createTestFile("config/test-safe.txt");
        
        File modsDir = new File("mods");
        modsDir.mkdirs();
        createTestFile("mods/test-unsafe.txt");
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions
        processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // Verify: safe file deleted, unsafe file preserved
        assertFalse("config/test-safe.txt should be deleted", new File("config/test-safe.txt").exists());
        assertTrue("mods/test-unsafe.txt should NOT be deleted in safety mode", 
                   new File("mods/test-unsafe.txt").exists());
        
        // Check log for safety warning
        boolean foundWarning = false;
        for (String msg : logMessages) {
            if (msg.contains("Safety mode") && msg.contains("mods/test-unsafe.txt")) {
                foundWarning = true;
                break;
            }
        }
        assertTrue("Should log safety mode warning", foundWarning);
        
        // Clean up
        new File("config/test-safe.txt").delete();
        new File("mods/test-unsafe.txt").delete();
        configDir.delete();
        modsDir.delete();
    }
    
    @Test
    public void testFileVsFolderDistinction() throws Exception {
        // Create test config
        JSONObject config = new JSONObject();
        config.put("safetyMode", false);
        
        JSONArray deletions = new JSONArray();
        JSONObject del1 = new JSONObject();
        del1.put("version", "1.5.0");
        
        JSONArray paths1 = new JSONArray();
        
        // File entry
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", testDir.getPath() + "/test-file.txt");
        paths1.put(path1);
        
        // Folder entry
        JSONObject path2 = new JSONObject();
        path2.put("type", "folder");
        path2.put("path", testDir.getPath() + "/test-folder");
        paths1.put(path2);
        
        del1.put("paths", paths1);
        deletions.put(del1);
        config.put("deletions", deletions);
        
        // Create test file and folder
        createTestFile(testDir.getPath() + "/test-file.txt");
        File testFolder = new File(testDir, "test-folder");
        testFolder.mkdirs();
        createTestFile(testDir.getPath() + "/test-folder/inner.txt");
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions
        processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // Verify both deleted
        assertFalse("File should be deleted", new File(testDir, "test-file.txt").exists());
        assertFalse("Folder should be deleted", testFolder.exists());
    }
    
    @Test
    public void testLegacyFormatDetection() throws Exception {
        // Create legacy format config
        JSONObject config = new JSONObject();
        JSONArray deletes = new JSONArray();
        
        JSONObject del1 = new JSONObject();
        del1.put("since", "1.5.0"); // Legacy "since" field
        JSONArray paths1 = new JSONArray();
        paths1.put("config/old.txt");
        del1.put("paths", paths1);
        deletes.put(del1);
        
        config.put("deletes", deletes);
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions
        int count = processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // Should skip all deletions due to legacy format
        assertEquals("Should skip deletions in legacy format", 0, count);
        
        // Check log for warning
        boolean foundWarning = false;
        for (String msg : logMessages) {
            if (msg.contains("Legacy") && msg.contains("format")) {
                foundWarning = true;
                break;
            }
        }
        assertTrue("Should log legacy format warning", foundWarning);
    }
    
    @Test
    public void testMalformedVersionHandling() throws Exception {
        // Create test config with malformed version
        JSONObject config = new JSONObject();
        config.put("safetyMode", false);
        
        JSONArray deletions = new JSONArray();
        JSONObject del1 = new JSONObject();
        del1.put("version", "invalid.version.xyz"); // Malformed
        
        JSONArray paths1 = new JSONArray();
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", testDir.getPath() + "/test.txt");
        paths1.put(path1);
        del1.put("paths", paths1);
        deletions.put(del1);
        
        config.put("deletions", deletions);
        
        // Create test file
        createTestFile(testDir.getPath() + "/test.txt");
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions
        processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // File should not be deleted due to malformed version
        assertTrue("File should not be deleted with malformed version", 
                  new File(testDir, "test.txt").exists());
        
        // Check log for warning
        boolean foundWarning = false;
        for (String msg : logMessages) {
            if (msg.contains("Malformed") && msg.contains("version")) {
                foundWarning = true;
                break;
            }
        }
        assertTrue("Should log malformed version warning", foundWarning);
    }
    
    @Test
    public void testNonExistentPathHandling() throws Exception {
        // Create test config
        JSONObject config = new JSONObject();
        config.put("safetyMode", false);
        
        JSONArray deletions = new JSONArray();
        JSONObject del1 = new JSONObject();
        del1.put("version", "1.5.0");
        
        JSONArray paths1 = new JSONArray();
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", testDir.getPath() + "/nonexistent.txt"); // Doesn't exist
        paths1.put(path1);
        del1.put("paths", paths1);
        deletions.put(del1);
        
        config.put("deletions", deletions);
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions - should handle gracefully
        processor.processDeletions(config, "1.4.0", "1.6.0");
        
        // Should mark as completed even though file doesn't exist
        assertTrue("Non-existent file should be marked as completed", 
                  metadata.isDeleteCompleted(testDir.getPath() + "/nonexistent.txt"));
    }
    
    @Test
    public void testDeletionOnlyOnce() throws Exception {
        // Create test config
        JSONObject config = new JSONObject();
        config.put("safetyMode", false);
        
        JSONArray deletions = new JSONArray();
        JSONObject del1 = new JSONObject();
        del1.put("version", "1.5.0");
        
        JSONArray paths1 = new JSONArray();
        JSONObject path1 = new JSONObject();
        path1.put("type", "file");
        path1.put("path", testDir.getPath() + "/test-once.txt");
        paths1.put(path1);
        del1.put("paths", paths1);
        deletions.put(del1);
        
        config.put("deletions", deletions);
        
        // Create test file
        createTestFile(testDir.getPath() + "/test-once.txt");
        
        // Create processor
        ModMetadata metadata = new ModMetadata(metadataFile.getPath());
        PendingOperations pendingOps = new PendingOperations(pendingOpsFile.getPath(), null);
        DeletionProcessor processor = new DeletionProcessor(createLogger(), metadata, pendingOps, null);
        
        // Process deletions first time
        int count1 = processor.processDeletions(config, "1.4.0", "1.6.0");
        assertEquals("Should delete file on first run", 1, count1);
        assertFalse("File should be deleted", new File(testDir, "test-once.txt").exists());
        
        // Recreate file
        createTestFile(testDir.getPath() + "/test-once.txt");
        
        // Process deletions second time - should skip
        int count2 = processor.processDeletions(config, "1.4.0", "1.6.0");
        assertEquals("Should not delete file on second run", 0, count2);
        assertTrue("File should not be deleted again", new File(testDir, "test-once.txt").exists());
    }
    
    private void createTestFile(String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(file);
        writer.write("test content");
        writer.close();
    }
}
