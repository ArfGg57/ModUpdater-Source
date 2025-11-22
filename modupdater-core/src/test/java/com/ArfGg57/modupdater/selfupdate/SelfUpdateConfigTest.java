package com.ArfGg57.modupdater.selfupdate;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for SelfUpdateConfig
 */
public class SelfUpdateConfigTest {
    
    @Test
    public void testDefaultConfig() {
        SelfUpdateConfig config = SelfUpdateConfig.createDefault();
        
        assertTrue("Default config should be enabled", config.isEnabled());
        assertEquals("github_releases", config.getSourceType());
        assertEquals("ArfGg57/ModUpdater-Source", config.getGithubRepo());
        assertFalse("Signature should not be required by default", config.isRequireSignature());
        assertFalse("Auto-install should be disabled by default", config.isAutoInstall());
        assertEquals(24, config.getCheckIntervalHours());
    }
    
    @Test
    public void testFromJson() {
        JSONObject json = new JSONObject();
        json.put("enabled", false);
        json.put("source_type", "static_manifest");
        json.put("github_repo", "test/repo");
        json.put("manifest_url", "http://test.com/manifest.json");
        json.put("require_signature", true);
        json.put("auto_install", true);
        json.put("check_interval_hours", 12);
        json.put("public_key_path", "/path/to/key");
        
        SelfUpdateConfig config = SelfUpdateConfig.fromJson(json);
        
        assertFalse(config.isEnabled());
        assertEquals("static_manifest", config.getSourceType());
        assertEquals("test/repo", config.getGithubRepo());
        assertEquals("http://test.com/manifest.json", config.getManifestUrl());
        assertTrue(config.isRequireSignature());
        assertTrue(config.isAutoInstall());
        assertEquals(12, config.getCheckIntervalHours());
        assertEquals("/path/to/key", config.getPublicKeyPath());
    }
    
    @Test
    public void testToJson() {
        SelfUpdateConfig config = new SelfUpdateConfig(
            true,
            "github_releases",
            "owner/repo",
            "http://example.com/manifest.json",
            false,
            false,
            24,
            null
        );
        
        JSONObject json = config.toJson();
        
        assertTrue(json.getBoolean("enabled"));
        assertEquals("github_releases", json.getString("source_type"));
        assertEquals("owner/repo", json.getString("github_repo"));
        assertEquals("http://example.com/manifest.json", json.getString("manifest_url"));
        assertFalse(json.getBoolean("require_signature"));
        assertFalse(json.getBoolean("auto_install"));
        assertEquals(24, json.getInt("check_interval_hours"));
    }
    
    @Test
    public void testRoundTrip() {
        SelfUpdateConfig original = SelfUpdateConfig.createDefault();
        JSONObject json = original.toJson();
        SelfUpdateConfig restored = SelfUpdateConfig.fromJson(json);
        
        assertEquals(original.isEnabled(), restored.isEnabled());
        assertEquals(original.getSourceType(), restored.getSourceType());
        assertEquals(original.getGithubRepo(), restored.getGithubRepo());
        assertEquals(original.getManifestUrl(), restored.getManifestUrl());
        assertEquals(original.isRequireSignature(), restored.isRequireSignature());
        assertEquals(original.isAutoInstall(), restored.isAutoInstall());
        assertEquals(original.getCheckIntervalHours(), restored.getCheckIntervalHours());
    }
}
