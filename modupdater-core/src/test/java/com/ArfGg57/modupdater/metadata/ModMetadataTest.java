package com.ArfGg57.modupdater.metadata;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.File;

/**
 * Unit tests for ModMetadata
 * 
 * Tests verify delete tracking, auxiliary file tracking, and version-aware matching.
 * Compatible with JUnit 4 for Java 8.
 */
public class ModMetadataTest {
    
    private static final String TEST_METADATA_PATH = "/tmp/test-mod-metadata.json";
    private ModMetadata metadata;
    
    @Before
    public void setUp() {
        // Clean up any existing test file
        File testFile = new File(TEST_METADATA_PATH);
        if (testFile.exists()) {
            testFile.delete();
        }
        metadata = new ModMetadata(TEST_METADATA_PATH);
    }
    
    @After
    public void tearDown() {
        // Clean up test file
        File testFile = new File(TEST_METADATA_PATH);
        if (testFile.exists()) {
            testFile.delete();
        }
    }
    
    // NOTE: Delete tracking feature removed per user request (files should be deletable multiple times)
    // Tests removed: testDeleteTracking_MarkAndCheck, testDeleteTracking_Persistence, testDeleteTracking_EmptyPath
    
    
    @Test
    public void testAuxiliaryFile_BasicTracking() {
        String fileName = "config/settings.cfg";
        String checksum = "abc123def456";
        String url = "https://example.com/settings.cfg";
        String location = "config/";
        
        // Record file
        metadata.recordFile(fileName, checksum, url, location);
        
        // Should be tracked
        assertNotNull("File should be tracked", metadata.getFile(fileName));
        assertTrue("File should match checksum", 
                   metadata.isFileInstalledAndMatches(fileName, checksum));
    }
    
    @Test
    public void testAuxiliaryFile_ChecksumMismatch() {
        String fileName = "config/data.txt";
        String checksum1 = "abc123";
        String checksum2 = "def456";
        String url = "https://example.com/data.txt";
        String location = "config/";
        
        // Record with checksum1
        metadata.recordFile(fileName, checksum1, url, location);
        
        // Should match checksum1
        assertTrue("File should match original checksum", 
                   metadata.isFileInstalledAndMatches(fileName, checksum1));
        
        // Should NOT match checksum2
        assertFalse("File should not match different checksum", 
                    metadata.isFileInstalledAndMatches(fileName, checksum2));
    }
    
    @Test
    public void testAuxiliaryFile_NoChecksum() {
        String fileName = "config/legacy.txt";
        String url = "https://example.com/legacy.txt";
        String location = "config/";
        
        // Record without checksum
        metadata.recordFile(fileName, "", url, location);
        
        // Should be tracked
        assertNotNull("File should be tracked", metadata.getFile(fileName));
        
        // Should match when no checksum expected
        assertTrue("File should match without checksum", 
                   metadata.isFileInstalledAndMatches(fileName, ""));
        assertTrue("File should match with null checksum", 
                   metadata.isFileInstalledAndMatches(fileName, null));
    }
    
    @Test
    public void testAuxiliaryFile_VersionTracking() {
        String fileName = "config/versioned.cfg";
        String version = "1.2.3";
        String checksum = "xyz789";
        String url = "https://example.com/versioned.cfg";
        String location = "config/";
        
        // Record with version
        metadata.recordFile(fileName, checksum, url, location, version);
        
        // Should match with same version and checksum
        assertTrue("File should match with same version", 
                   metadata.isFileInstalledAndMatchesVersion(fileName, version, checksum));
        
        // Should NOT match with different version
        assertFalse("File should not match with different version", 
                    metadata.isFileInstalledAndMatchesVersion(fileName, "2.0.0", checksum));
    }
    
    @Test
    public void testMod_BasicTracking() {
        String numberId = "12345";
        String fileName = "examplemod-1.0.jar";
        String hash = "abc123";
        JSONObject source = new JSONObject();
        source.put("type", "url");
        source.put("url", "https://example.com/mod.jar");
        
        // Record mod
        metadata.recordMod(numberId, fileName, hash, source);
        
        // Should be tracked
        assertNotNull("Mod should be tracked", metadata.getMod(numberId));
        assertEquals("Mod filename should match", fileName, 
                     metadata.findInstalledFile(numberId));
    }
    
    @Test
    public void testMod_SourceMatching() {
        String numberId = "12345";
        String fileName = "mod.jar";
        String hash = "abc123";
        JSONObject source = new JSONObject();
        source.put("type", "curseforge");
        source.put("projectId", 123);
        source.put("fileId", 456L);
        
        // Record mod
        metadata.recordMod(numberId, fileName, hash, source);
        
        // Should match with same source
        assertTrue("Mod should match with same source", 
                   metadata.isModInstalledAndMatches(numberId, source, hash));
        
        // Should NOT match with different source
        JSONObject differentSource = new JSONObject();
        differentSource.put("type", "curseforge");
        differentSource.put("projectId", 999);
        differentSource.put("fileId", 456L);
        
        assertFalse("Mod should not match with different source", 
                    metadata.isModInstalledAndMatches(numberId, differentSource, hash));
    }
    
    @Test
    public void testPersistence_FullCycle() {
        // Add various entries (delete tracking removed per user request)
        metadata.recordFile("config/test.cfg", "hash123", "url", "config/");
        metadata.recordMod("99", "mod.jar", "modhash", new JSONObject().put("type", "url").put("url", "test"));
        
        // Save
        metadata.save();
        
        // Load in new instance
        ModMetadata loaded = new ModMetadata(TEST_METADATA_PATH);
        
        // Verify all data persisted
        assertNotNull("File should persist", loaded.getFile("config/test.cfg"));
        assertNotNull("Mod should persist", loaded.getMod("99"));
    }
}
