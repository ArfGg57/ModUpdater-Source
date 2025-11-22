package com.ArfGg57.modupdater.selfupdate;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for UpdateManifest
 */
public class UpdateManifestTest {
    
    @Test
    public void testManifestCreation() {
        UpdateManifest manifest = new UpdateManifest(
            "1.2.3",
            "http://example.com/modupdater.jar",
            "abc123",
            "http://example.com/modupdater.jar.sig",
            "Bug fixes and improvements",
            1234567,
            false
        );
        
        assertEquals("1.2.3", manifest.getVersion());
        assertEquals("http://example.com/modupdater.jar", manifest.getDownloadUrl());
        assertEquals("abc123", manifest.getSha256Hash());
        assertEquals("http://example.com/modupdater.jar.sig", manifest.getSignatureUrl());
        assertEquals("Bug fixes and improvements", manifest.getChangelog());
        assertEquals(1234567, manifest.getFileSize());
        assertFalse(manifest.isRequired());
    }
    
    @Test
    public void testManifestWithNulls() {
        UpdateManifest manifest = new UpdateManifest(
            "1.0.0",
            "http://example.com/mod.jar",
            "",
            null,
            null,
            0,
            true
        );
        
        assertEquals("1.0.0", manifest.getVersion());
        assertEquals("", manifest.getSha256Hash());
        assertNull(manifest.getSignatureUrl());
        assertNull(manifest.getChangelog());
        assertEquals(0, manifest.getFileSize());
        assertTrue(manifest.isRequired());
    }
    
    @Test
    public void testToString() {
        UpdateManifest manifest = new UpdateManifest(
            "2.0.0",
            "http://test.com/file.jar",
            "hash",
            null,
            "changes",
            100,
            false
        );
        
        String str = manifest.toString();
        assertTrue(str.contains("2.0.0"));
        assertTrue(str.contains("http://test.com/file.jar"));
        assertTrue(str.contains("hash"));
    }
}
