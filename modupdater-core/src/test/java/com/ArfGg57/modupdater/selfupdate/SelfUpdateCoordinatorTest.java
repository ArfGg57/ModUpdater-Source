package com.ArfGg57.modupdater.selfupdate;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for SelfUpdateCoordinator
 */
public class SelfUpdateCoordinatorTest {
    
    private List<String> logMessages;
    private SelfUpdateCoordinator coordinator;
    
    @Before
    public void setUp() {
        logMessages = new ArrayList<>();
        coordinator = new SelfUpdateCoordinator(new SelfUpdateCoordinator.Logger() {
            public void log(String message) {
                logMessages.add(message);
            }
        });
    }
    
    @Test
    public void testParseNewConfigFormat() throws Exception {
        // Create a JSON object in the new format
        JSONObject json = new JSONObject();
        json.put("version", "2.21");
        json.put("filename_prefix", "!!!!!");
        
        JSONObject files = new JSONObject();
        
        JSONObject launchwrapper = new JSONObject();
        launchwrapper.put("github_name", "modupdater-2.21.jar");
        launchwrapper.put("local_name", "!!!!!modupdater-2.21.jar");
        launchwrapper.put("hash", "abc123def456");
        files.put("launchwrapper", launchwrapper);
        
        JSONObject mod = new JSONObject();
        mod.put("github_name", "modupdater-mod-2.21.jar");
        mod.put("local_name", "!!!!!modupdater-mod-2.21.jar");
        mod.put("hash", "def456abc123");
        files.put("mod", mod);
        
        JSONObject cleanup = new JSONObject();
        cleanup.put("github_name", "modupdater-cleanup-2.21.jar");
        cleanup.put("local_name", "modupdater-cleanup-2.21.jar");
        cleanup.put("hash", "789xyz");
        files.put("cleanup", cleanup);
        
        json.put("files", files);
        
        // Use reflection to test the private parseReleaseConfig method
        Method parseMethod = SelfUpdateCoordinator.class.getDeclaredMethod("parseReleaseConfig", JSONObject.class);
        parseMethod.setAccessible(true);
        Object config = parseMethod.invoke(coordinator, json);
        
        assertNotNull("Config should not be null", config);
        
        // Check that version was parsed
        assertTrue("Log should contain version info", 
            logMessages.stream().anyMatch(msg -> msg.contains("version=2.21")));
    }
    
    @Test
    public void testParseLegacyConfigFormat() throws Exception {
        // Create a JSON object in the legacy format
        JSONObject json = new JSONObject();
        json.put("current_file_name", "!!!!!modupdater-2.21.jar");
        json.put("previous_release_hash", "abc123def456");
        
        // Use reflection to test the private parseReleaseConfig method
        Method parseMethod = SelfUpdateCoordinator.class.getDeclaredMethod("parseReleaseConfig", JSONObject.class);
        parseMethod.setAccessible(true);
        Object config = parseMethod.invoke(coordinator, json);
        
        assertNotNull("Config should not be null", config);
        
        // Check that legacy format was detected
        assertTrue("Log should indicate legacy format", 
            logMessages.stream().anyMatch(msg -> msg.contains("legacy config format")));
    }
    
    @Test
    public void testExtractVersionFromFilename() throws Exception {
        Method method = SelfUpdateCoordinator.class.getDeclaredMethod("extractVersionFromFilename", String.class);
        method.setAccessible(true);
        
        assertEquals("2.21", method.invoke(coordinator, "!!!!!modupdater-2.21.jar"));
        assertEquals("1.0.0", method.invoke(coordinator, "modupdater-1.0.0.jar"));
        assertEquals("3.5", method.invoke(coordinator, "modupdater-mod-3.5.jar"));
    }
    
    @Test
    public void testExtractPrefixFromFilename() throws Exception {
        Method method = SelfUpdateCoordinator.class.getDeclaredMethod("extractPrefixFromFilename", String.class);
        method.setAccessible(true);
        
        assertEquals("!!!!!", method.invoke(coordinator, "!!!!!modupdater-2.21.jar"));
        assertEquals("", method.invoke(coordinator, "modupdater-2.21.jar"));
        assertEquals("__", method.invoke(coordinator, "__test-1.0.jar"));
    }
    
    @Test
    public void testApplyFilenamePrefix() throws Exception {
        Method method = SelfUpdateCoordinator.class.getDeclaredMethod("applyFilenamePrefix", String.class, String.class);
        method.setAccessible(true);
        
        // Normal case - apply prefix
        assertEquals("!!!!!modupdater-2.21.jar", 
            method.invoke(coordinator, "modupdater-2.21.jar", "!!!!!"));
        
        // Mod JAR - apply prefix
        assertEquals("!!!!!modupdater-mod-2.21.jar", 
            method.invoke(coordinator, "modupdater-mod-2.21.jar", "!!!!!"));
        
        // Cleanup JAR - should NOT apply prefix
        assertEquals("modupdater-cleanup-2.21.jar", 
            method.invoke(coordinator, "modupdater-cleanup-2.21.jar", "!!!!!"));
        
        // Empty prefix - no change
        assertEquals("modupdater-2.21.jar", 
            method.invoke(coordinator, "modupdater-2.21.jar", ""));
        
        // Null prefix - no change
        assertEquals("modupdater-2.21.jar", 
            method.invoke(coordinator, "modupdater-2.21.jar", null));
    }
    
    @Test
    public void testIsValidSha256() throws Exception {
        Method method = SelfUpdateCoordinator.class.getDeclaredMethod("isValidSha256", String.class);
        method.setAccessible(true);
        
        // Valid 64-char hex string
        assertTrue((Boolean) method.invoke(coordinator, 
            "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"));
        
        // Too short
        assertFalse((Boolean) method.invoke(coordinator, "abc123"));
        
        // Too long
        assertFalse((Boolean) method.invoke(coordinator, 
            "abc123def456abc123def456abc123def456abc123def456abc123def456abc12"));
        
        // Invalid characters
        assertFalse((Boolean) method.invoke(coordinator, 
            "xyz123def456abc123def456abc123def456abc123def456abc123def456abc1"));
        
        // Null
        assertFalse((Boolean) method.invoke(coordinator, (String) null));
    }
    
    @Test
    public void testSelfUpdateInfoCreation() {
        // Test creating a SelfUpdateInfo with all fields
        SelfUpdateCoordinator.SelfUpdateInfo info = new SelfUpdateCoordinator.SelfUpdateInfo(
            "!!!!!modupdater-2.20.jar",  // currentFileName
            "/mods/!!!!!modupdater-2.20.jar",  // currentJarPath
            "!!!!!modupdater-2.21.jar",  // latestFileName
            "https://example.com/modupdater-2.21.jar",  // latestDownloadUrl
            "abc123",  // latestSha256Hash
            "def456",  // previousReleaseHash
            "!!!!!modupdater-mod-2.21.jar",  // latestModFileName
            "https://example.com/modupdater-mod-2.21.jar",  // latestModDownloadUrl
            "mod123",  // latestModSha256Hash
            "/mods/!!!!!modupdater-mod-2.20.jar",  // currentModJarPath
            "modupdater-cleanup-2.21.jar",  // latestCleanupFileName
            "https://example.com/modupdater-cleanup-2.21.jar",  // latestCleanupDownloadUrl
            "cleanup123",  // latestCleanupSha256Hash
            "/mods/modupdater-cleanup-2.20.jar"  // currentCleanupJarPath
        );
        
        // Test launchwrapper getters
        assertEquals("!!!!!modupdater-2.20.jar", info.getCurrentFileName());
        assertEquals("/mods/!!!!!modupdater-2.20.jar", info.getCurrentJarPath());
        assertEquals("!!!!!modupdater-2.21.jar", info.getLatestFileName());
        assertEquals("https://example.com/modupdater-2.21.jar", info.getLatestDownloadUrl());
        assertEquals("abc123", info.getLatestSha256Hash());
        assertTrue(info.hasCurrentJar());
        
        // Test mod JAR getters
        assertEquals("!!!!!modupdater-mod-2.21.jar", info.getLatestModFileName());
        assertEquals("https://example.com/modupdater-mod-2.21.jar", info.getLatestModDownloadUrl());
        assertEquals("mod123", info.getLatestModSha256Hash());
        assertTrue(info.hasModJar());
        assertTrue(info.hasCurrentModJar());
        
        // Test cleanup JAR getters
        assertEquals("modupdater-cleanup-2.21.jar", info.getLatestCleanupFileName());
        assertEquals("https://example.com/modupdater-cleanup-2.21.jar", info.getLatestCleanupDownloadUrl());
        assertEquals("cleanup123", info.getLatestCleanupSha256Hash());
        assertTrue(info.hasCleanupJar());
        assertTrue(info.hasCurrentCleanupJar());
    }
    
    @Test
    public void testSelfUpdateInfoWithMissingJars() {
        // Test creating a SelfUpdateInfo with only launchwrapper (no mod or cleanup)
        SelfUpdateCoordinator.SelfUpdateInfo info = new SelfUpdateCoordinator.SelfUpdateInfo(
            "!!!!!modupdater-2.20.jar",  // currentFileName
            null,  // currentJarPath - not found
            "!!!!!modupdater-2.21.jar",  // latestFileName
            "https://example.com/modupdater-2.21.jar",  // latestDownloadUrl
            "abc123",  // latestSha256Hash
            "def456",  // previousReleaseHash
            null,  // latestModFileName - no mod in release
            null,  // latestModDownloadUrl
            null,  // latestModSha256Hash
            null,  // currentModJarPath
            null,  // latestCleanupFileName - no cleanup in release
            null,  // latestCleanupDownloadUrl
            null,  // latestCleanupSha256Hash
            null   // currentCleanupJarPath
        );
        
        // Launchwrapper is present in release but not installed
        assertFalse(info.hasCurrentJar());
        
        // Mod JAR is not in the release
        assertFalse(info.hasModJar());
        assertFalse(info.hasCurrentModJar());
        
        // Cleanup JAR is not in the release
        assertFalse(info.hasCleanupJar());
        assertFalse(info.hasCurrentCleanupJar());
    }
    
    @Test
    public void testParseInvalidConfig() throws Exception {
        // Create an invalid JSON object (empty)
        JSONObject json = new JSONObject();
        
        // Use reflection to test the private parseReleaseConfig method
        Method parseMethod = SelfUpdateCoordinator.class.getDeclaredMethod("parseReleaseConfig", JSONObject.class);
        parseMethod.setAccessible(true);
        Object config = parseMethod.invoke(coordinator, json);
        
        assertNull("Config should be null for invalid input", config);
    }
    
    @Test
    public void testNewConfigWithOptionalFields() throws Exception {
        // Create a JSON object in the new format with only launchwrapper (mod and cleanup optional)
        JSONObject json = new JSONObject();
        json.put("version", "2.21");
        json.put("filename_prefix", "!!!!!");
        
        JSONObject files = new JSONObject();
        
        JSONObject launchwrapper = new JSONObject();
        launchwrapper.put("github_name", "modupdater-2.21.jar");
        launchwrapper.put("local_name", "!!!!!modupdater-2.21.jar");
        // No hash provided
        files.put("launchwrapper", launchwrapper);
        
        // No mod or cleanup entries
        
        json.put("files", files);
        
        // Use reflection to test the private parseReleaseConfig method
        Method parseMethod = SelfUpdateCoordinator.class.getDeclaredMethod("parseReleaseConfig", JSONObject.class);
        parseMethod.setAccessible(true);
        Object config = parseMethod.invoke(coordinator, json);
        
        assertNotNull("Config should not be null with minimal fields", config);
    }
}
