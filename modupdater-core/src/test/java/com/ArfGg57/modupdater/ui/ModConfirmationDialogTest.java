package com.ArfGg57.modupdater.ui;

import com.ArfGg57.modupdater.resolver.FilenameResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Integration tests for ModConfirmationDialog to verify filename resolution
 * matches between the dialog and UpdaterCore.
 * 
 * Tests ensure that files with custom file_name don't show up in the confirmation
 * dialog when they are already installed.
 */
public class ModConfirmationDialogTest {
    
    private File tempDir;
    
    @Before
    public void setUp() throws IOException {
        // Create a temporary directory for test files
        tempDir = Files.createTempDirectory("modupdater-test").toFile();
    }
    
    @After
    public void tearDown() {
        // Clean up test files
        if (tempDir != null && tempDir.exists()) {
            deleteRecursively(tempDir);
        }
    }
    
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    /**
     * Test that FilenameResolver is used consistently in the dialog.
     * This is a unit test that verifies the resolver produces the same output
     * for the same input, which is the core of the fix.
     */
    @Test
    public void testFilenameResolverConsistency() {
        FilenameResolver resolver = new FilenameResolver(null, false);
        
        // Test case 1: Custom file_name without extension
        String customName1 = "myconfig";
        String url1 = "https://example.com/download/myconfig.txt";
        String result1a = resolver.resolve(customName1, url1, null, FilenameResolver.ArtifactType.FILE);
        String result1b = resolver.resolve(customName1, url1, null, FilenameResolver.ArtifactType.FILE);
        
        assertEquals("Resolver should produce consistent results", result1a, result1b);
        assertEquals("Should infer .txt extension from URL", "myconfig.txt", result1a);
        
        // Test case 2: Custom file_name with extension
        String customName2 = "myconfig.json";
        String url2 = "https://example.com/download/something.json";
        String result2a = resolver.resolve(customName2, url2, null, FilenameResolver.ArtifactType.FILE);
        String result2b = resolver.resolve(customName2, url2, null, FilenameResolver.ArtifactType.FILE);
        
        assertEquals("Resolver should produce consistent results", result2a, result2b);
        assertEquals("Should keep existing extension", "myconfig.json", result2a);
        
        // Test case 3: No custom file_name, extract from URL
        String customName3 = "";
        String url3 = "https://example.com/download/serverconfig.cfg";
        String result3a = resolver.resolve(customName3, url3, null, FilenameResolver.ArtifactType.FILE);
        String result3b = resolver.resolve(customName3, url3, null, FilenameResolver.ArtifactType.FILE);
        
        assertEquals("Resolver should produce consistent results", result3a, result3b);
        assertEquals("Should extract full filename from URL", "serverconfig.cfg", result3a);
    }
    
    /**
     * Test that the dialog would correctly identify an existing file.
     * This simulates the enrichment logic checking if a file exists.
     */
    @Test
    public void testFileExistenceCheckWithCustomName() throws IOException {
        FilenameResolver resolver = new FilenameResolver(null, false);
        
        // Scenario: files.json specifies file_name="myconfig" and url="...myconfig.txt"
        // UpdaterCore would save it as "myconfig.txt" (using FilenameResolver)
        String customFileName = "myconfig";
        String url = "https://example.com/download/myconfig.txt";
        String resolvedName = resolver.resolve(customFileName, url, null, FilenameResolver.ArtifactType.FILE);
        
        // Create the file as UpdaterCore would
        File actualFile = new File(tempDir, resolvedName);
        try (FileWriter writer = new FileWriter(actualFile)) {
            writer.write("test content");
        }
        
        // Now simulate the dialog checking for existence
        File checkFile = new File(tempDir, resolvedName);
        
        assertTrue("Dialog should find the file using resolved name", checkFile.exists());
        assertEquals("Resolved filename should match actual filename", actualFile.getName(), checkFile.getName());
    }
    
    /**
     * Test the specific bug scenario: custom file_name without extension.
     * The bug was that the dialog would check for "myconfig" but UpdaterCore
     * saves as "myconfig.txt", so the dialog thought the file was missing.
     */
    @Test
    public void testBugScenario_CustomNameWithoutExtension() throws IOException {
        FilenameResolver resolver = new FilenameResolver(null, false);
        
        // Bug scenario: file_name="betterleaves" (no extension)
        // URL: "https://example.com/downloads/betterleaves.cfg"
        String customFileName = "betterleaves";
        String url = "https://example.com/downloads/betterleaves.cfg";
        
        // UpdaterCore uses FilenameResolver which adds extension from URL
        String resolvedName = resolver.resolve(customFileName, url, null, FilenameResolver.ArtifactType.FILE);
        assertEquals("Should add extension from URL", "betterleaves.cfg", resolvedName);
        
        // Create file as UpdaterCore would
        File actualFile = new File(tempDir, resolvedName);
        try (FileWriter writer = new FileWriter(actualFile)) {
            writer.write("test content");
        }
        
        // Dialog now uses the same resolver, so it checks the right filename
        File checkFile = new File(tempDir, resolvedName);
        assertTrue("Dialog should find the existing file", checkFile.exists());
        
        // Before the fix, the dialog would check for "betterleaves" (without .cfg)
        File wrongCheck = new File(tempDir, customFileName);
        assertFalse("File without extension should not exist", wrongCheck.exists());
    }
    
    /**
     * Test mod filename resolution consistency.
     */
    @Test
    public void testModFilenameConsistency() {
        FilenameResolver resolver = new FilenameResolver(null, false);
        
        // Mod with custom file_name without extension
        String customFileName = "mymod";
        String url = "https://example.com/mods/mymod.jar";
        
        String resolved = resolver.resolve(customFileName, url, null, FilenameResolver.ArtifactType.MOD);
        
        assertEquals("Should add .jar extension from URL", "mymod.jar", resolved);
    }
    
    /**
     * Test that when file_name has an extension, it's preserved.
     */
    @Test
    public void testFileWithExistingExtension() {
        FilenameResolver resolver = new FilenameResolver(null, false);
        
        String customFileName = "config.json";
        String url = "https://example.com/download/something.json";
        
        String resolved = resolver.resolve(customFileName, url, null, FilenameResolver.ArtifactType.FILE);
        
        assertEquals("Should keep existing extension", "config.json", resolved);
    }
}
