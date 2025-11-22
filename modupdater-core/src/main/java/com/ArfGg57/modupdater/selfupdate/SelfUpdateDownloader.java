package com.ArfGg57.modupdater.selfupdate;

import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.net.Downloader;
import com.ArfGg57.modupdater.ui.GuiUpdater;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Downloads and verifies ModUpdater JAR files for self-updates.
 */
public class SelfUpdateDownloader {
    
    /**
     * Logger interface for output messages
     */
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    private final GuiUpdater gui;
    
    public SelfUpdateDownloader(Logger logger, GuiUpdater gui) {
        this.logger = logger;
        this.gui = gui;
    }
    
    /**
     * Download the update JAR and verify its integrity
     * 
     * @param manifest The update manifest with download information
     * @param stagingDir Directory to download the JAR to
     * @return The downloaded and verified JAR file
     * @throws Exception If download or verification fails
     */
    public File downloadAndVerify(UpdateManifest manifest, File stagingDir) throws Exception {
        logger.log("Starting download of ModUpdater version " + manifest.getVersion());
        
        // Ensure staging directory exists
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw new IOException("Failed to create staging directory: " + stagingDir.getAbsolutePath());
        }
        
        // Extract filename from URL or use default
        String fileName = extractFileName(manifest.getDownloadUrl());
        if (fileName == null || fileName.isEmpty()) {
            fileName = "modupdater-" + manifest.getVersion() + ".jar";
        }
        
        File downloadedFile = new File(stagingDir, fileName);
        
        // Download the file
        logger.log("Downloading from: " + manifest.getDownloadUrl());
        try {
            Downloader.downloadToFile(manifest.getDownloadUrl(), downloadedFile, gui);
        } catch (Exception e) {
            throw new Exception("Failed to download update: " + e.getMessage(), e);
        }
        
        if (!downloadedFile.exists()) {
            throw new Exception("Download completed but file not found: " + downloadedFile.getAbsolutePath());
        }
        
        logger.log("Download completed: " + downloadedFile.length() + " bytes");
        
        // Verify file size if provided
        if (manifest.getFileSize() > 0) {
            if (downloadedFile.length() != manifest.getFileSize()) {
                downloadedFile.delete();
                throw new Exception("File size mismatch. Expected: " + manifest.getFileSize() + 
                                  ", Got: " + downloadedFile.length());
            }
            logger.log("File size verified: " + downloadedFile.length() + " bytes");
        }
        
        // Verify SHA-256 hash
        if (manifest.getSha256Hash() != null && !manifest.getSha256Hash().isEmpty()) {
            logger.log("Verifying SHA-256 hash...");
            String actualHash = HashUtils.sha256Hex(downloadedFile);
            String expectedHash = manifest.getSha256Hash().toLowerCase().trim();
            
            if (!actualHash.equals(expectedHash)) {
                downloadedFile.delete();
                throw new Exception("SHA-256 hash mismatch!\n" +
                                  "Expected: " + expectedHash + "\n" +
                                  "Got:      " + actualHash);
            }
            logger.log("SHA-256 hash verified successfully");
        } else {
            logger.log("Warning: No SHA-256 hash provided, skipping verification");
        }
        
        // TODO: Verify signature if provided and required
        if (manifest.getSignatureUrl() != null && !manifest.getSignatureUrl().isEmpty()) {
            logger.log("Note: Signature verification not yet implemented");
            // Future enhancement: implement GPG/RSA signature verification
        }
        
        logger.log("Update JAR downloaded and verified successfully");
        return downloadedFile;
    }
    
    /**
     * Extract filename from URL
     */
    private String extractFileName(String url) {
        if (url == null) return null;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            // Remove query parameters if present
            int queryStart = name.indexOf('?');
            if (queryStart >= 0) {
                name = name.substring(0, queryStart);
            }
            return name;
        }
        return null;
    }
    
    /**
     * Verify a signature file (placeholder for future implementation)
     */
    private boolean verifySignature(File jarFile, File signatureFile, File publicKeyFile) throws Exception {
        // TODO: Implement GPG/RSA signature verification
        // This would require adding a crypto library or using Java's built-in security APIs
        logger.log("Signature verification not yet implemented");
        return false;
    }
}
