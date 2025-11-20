package com.ArfGg57.modupdater.resolver;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Unit tests for FilenameResolver
 * 
 * These tests verify the extension inference logic works correctly.
 * Compatible with JUnit 4 for Java 8.
 */
public class FilenameResolverTest {
    
    @Test
    public void testHasExtension_ValidSinglePart() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertTrue("Should recognize .jar", resolver.hasExtension("file.jar"));
        assertTrue("Should recognize .txt", resolver.hasExtension("file.txt"));
        assertTrue("Should recognize .json", resolver.hasExtension("config.json"));
        assertTrue("Should recognize .png", resolver.hasExtension("image.png"));
    }
    
    @Test
    public void testHasExtension_ValidMultiPart() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertTrue("Should recognize .tar.gz", resolver.hasExtension("archive.tar.gz"));
        assertTrue("Should recognize .tar.bz2", resolver.hasExtension("archive.tar.bz2"));
        assertTrue("Should recognize .tar.xz", resolver.hasExtension("archive.tar.xz"));
    }
    
    @Test
    public void testHasExtension_NoExtension() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertFalse("Should not recognize no extension", resolver.hasExtension("file"));
        assertFalse("Should not recognize empty", resolver.hasExtension(""));
        assertFalse("Should not recognize null", resolver.hasExtension(null));
    }
    
    @Test
    public void testHasExtension_InvalidExtension() {
        FilenameResolver resolver = new FilenameResolver();
        
        // Extension too long (>8 chars)
        assertFalse("Should reject long extension", resolver.hasExtension("file.verylongextension"));
        
        // Extension with special chars
        assertFalse("Should reject special chars", resolver.hasExtension("file.jar!"));
    }
    
    @Test
    public void testExtractExtensionFromUrl_Simple() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertEquals(".jar", resolver.extractExtensionFromUrl("https://example.com/mod.jar"));
        assertEquals(".json", resolver.extractExtensionFromUrl("https://example.com/config.json"));
        assertEquals(".txt", resolver.extractExtensionFromUrl("https://example.com/readme.txt"));
    }
    
    @Test
    public void testExtractExtensionFromUrl_WithQuery() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertEquals(".jar", resolver.extractExtensionFromUrl("https://example.com/mod.jar?version=1.0"));
        assertEquals(".json", resolver.extractExtensionFromUrl("https://example.com/config.json?auth=token"));
    }
    
    @Test
    public void testExtractExtensionFromUrl_WithFragment() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertEquals(".jar", resolver.extractExtensionFromUrl("https://example.com/mod.jar#section"));
    }
    
    @Test
    public void testExtractExtensionFromUrl_NoExtension() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertNull(resolver.extractExtensionFromUrl("https://example.com/download"));
        assertNull(resolver.extractExtensionFromUrl("https://example.com/"));
    }
    
    @Test
    public void testMapContentType() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertEquals(".jar", resolver.mapContentType("application/java-archive"));
        assertEquals(".jar", resolver.mapContentType("application/zip"));
        assertEquals(".txt", resolver.mapContentType("text/plain"));
        assertEquals(".json", resolver.mapContentType("application/json"));
        assertEquals(".png", resolver.mapContentType("image/png"));
        assertEquals(".jpg", resolver.mapContentType("image/jpeg"));
    }
    
    @Test
    public void testMapContentType_WithCharset() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertEquals(".txt", resolver.mapContentType("text/plain; charset=UTF-8"));
        assertEquals(".json", resolver.mapContentType("application/json; charset=utf-8"));
    }
    
    @Test
    public void testMapContentType_Unknown() {
        FilenameResolver resolver = new FilenameResolver();
        
        assertNull(resolver.mapContentType("application/unknown"));
        assertNull(resolver.mapContentType(""));
        assertNull(resolver.mapContentType(null));
    }
    
    @Test
    public void testDetectExtensionFromMagic_JAR() throws Exception {
        FilenameResolver resolver = new FilenameResolver();
        
        // JAR/ZIP magic bytes: PK\x03\x04
        byte[] jarMagic = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        InputStream stream = new ByteArrayInputStream(jarMagic);
        
        assertEquals(".jar", resolver.detectExtensionFromMagic(stream));
    }
    
    @Test
    public void testDetectExtensionFromMagic_PNG() throws Exception {
        FilenameResolver resolver = new FilenameResolver();
        
        // PNG magic bytes: \x89PNG
        byte[] pngMagic = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        InputStream stream = new ByteArrayInputStream(pngMagic);
        
        assertEquals(".png", resolver.detectExtensionFromMagic(stream));
    }
    
    @Test
    public void testDetectExtensionFromMagic_Unknown() throws Exception {
        FilenameResolver resolver = new FilenameResolver();
        
        byte[] unknownBytes = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream stream = new ByteArrayInputStream(unknownBytes);
        
        assertNull(resolver.detectExtensionFromMagic(stream));
    }
    
    @Test
    public void testResolve_AlreadyHasExtension() {
        FilenameResolver resolver = new FilenameResolver();
        
        String result = resolver.resolve(
            "myfile.jar",
            "https://example.com/download",
            null,
            FilenameResolver.ArtifactType.MOD
        );
        
        assertEquals("myfile.jar", result);
    }
    
    @Test
    public void testResolve_FromUrl() {
        FilenameResolver resolver = new FilenameResolver();
        
        String result = resolver.resolve(
            "myfile",
            "https://example.com/myfile.jar",
            null,
            FilenameResolver.ArtifactType.MOD
        );
        
        assertEquals("myfile.jar", result);
    }
    
    @Test
    public void testResolve_FallbackToJar() {
        FilenameResolver resolver = new FilenameResolver();
        
        // URL without extension, no other info available
        String result = resolver.resolve(
            "myfile",
            "https://example.com/download?id=123",
            null,
            FilenameResolver.ArtifactType.MOD
        );
        
        // Should fall back to .jar for mods
        assertEquals("myfile.jar", result);
    }
    
    @Test
    public void testResolve_EmptyName() {
        FilenameResolver resolver = new FilenameResolver();
        
        String result = resolver.resolve(
            "",
            "https://example.com/somefile.txt",
            null,
            FilenameResolver.ArtifactType.FILE
        );
        
        // Should extract from URL
        assertEquals("somefile.txt", result);
    }
    
    @Test
    public void testResolve_MultiPartExtension() {
        FilenameResolver resolver = new FilenameResolver();
        
        String result = resolver.resolve(
            "archive.tar.gz",
            "https://example.com/download",
            null,
            FilenameResolver.ArtifactType.FILE
        );
        
        assertEquals("archive.tar.gz", result);
    }
}
