package com.ArfGg57.modupdater.restart;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for CrashUtils persistent artifact methods.
 */
public class CrashUtilsTest {
    
    private static final String TEST_CONFIG_DIR = "config/ModUpdater/";
    
    @Before
    public void setUp() {
        // Clean up any existing test artifacts
        cleanupTestArtifacts();
    }
    
    @After
    public void tearDown() {
        // Clean up test artifacts after each test
        cleanupTestArtifacts();
    }
    
    private void cleanupTestArtifacts() {
        try {
            Files.deleteIfExists(Paths.get(TEST_CONFIG_DIR + "restart_required.flag"));
            Files.deleteIfExists(Paths.get(TEST_CONFIG_DIR + "restart_message.txt"));
            Files.deleteIfExists(Paths.get(TEST_CONFIG_DIR + "locked_files.lst"));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
    
    @Test
    public void testWriteAndReadRestartFlag() {
        // Initially, no restart should be required
        assertFalse("Restart should not be required initially", CrashUtils.isRestartRequired());
        
        // Write restart flag
        String message = "Test restart message";
        CrashUtils.writeRestartFlag(message);
        
        // Verify flag exists
        assertTrue("Restart should be required after writing flag", CrashUtils.isRestartRequired());
        
        // Verify message can be read
        String readMessage = CrashUtils.readRestartMessage();
        assertEquals("Message should match", message, readMessage);
        
        // Clear artifacts
        CrashUtils.clearRestartArtifacts();
        
        // Verify flag is cleared
        assertFalse("Restart should not be required after clearing", CrashUtils.isRestartRequired());
    }
    
    @Test
    public void testWriteAndReadLockedFiles() throws IOException {
        // Create test file list using cross-platform temp directory
        String tmpDir = System.getProperty("java.io.tmpdir");
        List<File> testFiles = new ArrayList<>();
        testFiles.add(new File(tmpDir, "test1.jar"));
        testFiles.add(new File(tmpDir, "test2.jar"));
        testFiles.add(new File(tmpDir, "test3.jar"));
        
        // Write locked files
        CrashUtils.writePersistentLockedFileList(testFiles);
        
        // Read back locked files
        List<File> readFiles = CrashUtils.readPendingLockedFiles();
        
        // Verify count
        assertEquals("Should have same number of files", testFiles.size(), readFiles.size());
        
        // Verify paths match
        for (int i = 0; i < testFiles.size(); i++) {
            assertEquals("File path should match", 
                testFiles.get(i).getAbsolutePath(), 
                readFiles.get(i).getAbsolutePath());
        }
    }
    
    @Test
    public void testReadNonExistentArtifacts() {
        // Clear any existing artifacts
        CrashUtils.clearRestartArtifacts();
        
        // Should return false for non-existent flag
        assertFalse("Should return false when flag doesn't exist", CrashUtils.isRestartRequired());
        
        // Should return default message when message file doesn't exist
        String message = CrashUtils.readRestartMessage();
        assertNotNull("Should return a message", message);
        assertTrue("Should return default message", 
            message.contains("Modpack update requires a restart"));
        
        // Should return empty list when locked files list doesn't exist
        List<File> files = CrashUtils.readPendingLockedFiles();
        assertNotNull("Should return a list", files);
        assertTrue("Should return empty list", files.isEmpty());
    }
    
    @Test
    public void testClearArtifacts() {
        // Write all artifacts
        CrashUtils.writeRestartFlag("Test message");
        List<File> testFiles = new ArrayList<>();
        testFiles.add(new File(System.getProperty("java.io.tmpdir"), "test.jar"));
        CrashUtils.writePersistentLockedFileList(testFiles);
        
        // Verify they exist
        assertTrue("Flag should exist", CrashUtils.isRestartRequired());
        
        // Clear all artifacts
        CrashUtils.clearRestartArtifacts();
        
        // Verify they're gone
        assertFalse("Flag should be cleared", CrashUtils.isRestartRequired());
        assertTrue("Locked files list should be empty", 
            CrashUtils.readPendingLockedFiles().isEmpty());
    }
}
