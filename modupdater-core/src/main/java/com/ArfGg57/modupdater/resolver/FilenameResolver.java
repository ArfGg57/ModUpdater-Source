package com.ArfGg57.modupdater.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FilenameResolver: Intelligently resolves filenames and extensions for downloads.
 * 
 * When a filename lacks an extension, this class attempts to infer it using:
 * 1. URL path inspection
 * 2. HTTP Content-Type header mapping
 * 3. Magic bytes detection
 * 4. Fallback to .jar for mod files
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class FilenameResolver {
    
    // Content-Type to extension mapping
    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>();
    static {
        // Archives
        CONTENT_TYPE_MAP.put("application/java-archive", ".jar");
        CONTENT_TYPE_MAP.put("application/zip", ".jar"); // Treat ZIP as JAR for mods
        CONTENT_TYPE_MAP.put("application/x-zip-compressed", ".jar");
        
        // Text files
        CONTENT_TYPE_MAP.put("text/plain", ".txt");
        CONTENT_TYPE_MAP.put("text/json", ".json");
        CONTENT_TYPE_MAP.put("application/json", ".json");
        CONTENT_TYPE_MAP.put("text/xml", ".xml");
        CONTENT_TYPE_MAP.put("application/xml", ".xml");
        
        // Images
        CONTENT_TYPE_MAP.put("image/png", ".png");
        CONTENT_TYPE_MAP.put("image/jpeg", ".jpg");
        CONTENT_TYPE_MAP.put("image/gif", ".gif");
        CONTENT_TYPE_MAP.put("image/bmp", ".bmp");
        
        // Other common types
        CONTENT_TYPE_MAP.put("application/pdf", ".pdf");
        CONTENT_TYPE_MAP.put("application/octet-stream", ".jar"); // Generic binary
    }
    
    // Multi-part extensions (must be checked before single-part)
    private static final String[] MULTI_PART_EXTENSIONS = {
        ".tar.gz", ".tar.bz2", ".tar.xz"
    };
    
    // Valid single-part extension pattern: .{1-8 alphanumeric chars}
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("\\.[A-Za-z0-9]{1,8}$");
    
    // Magic bytes signatures
    private static final Map<String, String> MAGIC_BYTES_MAP = new HashMap<>();
    static {
        MAGIC_BYTES_MAP.put("504B0304", ".jar"); // PK\x03\x04 - ZIP/JAR
        MAGIC_BYTES_MAP.put("89504E47", ".png"); // PNG signature
        MAGIC_BYTES_MAP.put("47494638", ".gif"); // GIF87a / GIF89a
        MAGIC_BYTES_MAP.put("FFD8FF", ".jpg");   // JPEG
        MAGIC_BYTES_MAP.put("25504446", ".pdf"); // %PDF
    }
    
    private final Logger logger;
    private final boolean debugMode;
    
    /**
     * Logger interface for status messages
     */
    public interface Logger {
        void log(String message);
    }
    
    /**
     * Create a FilenameResolver with optional logging
     * @param logger Logger for debug messages (null for silent operation)
     * @param debugMode Enable verbose logging of resolution steps
     */
    public FilenameResolver(Logger logger, boolean debugMode) {
        this.logger = logger;
        this.debugMode = debugMode;
    }
    
    /**
     * Create a FilenameResolver with logging disabled
     */
    public FilenameResolver() {
        this(null, false);
    }
    
    /**
     * Check if a filename already has a valid extension
     * @param filename The filename to check
     * @return true if the filename has a recognized extension
     */
    public boolean hasExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        // Check multi-part extensions first
        String lowerName = filename.toLowerCase(Locale.ROOT);
        for (String multiExt : MULTI_PART_EXTENSIONS) {
            if (lowerName.endsWith(multiExt)) {
                return true;
            }
        }
        
        // Check single-part extension pattern
        Matcher m = EXTENSION_PATTERN.matcher(filename);
        return m.find();
    }
    
    /**
     * Extract extension from URL path
     * @param url The download URL
     * @return Extension with leading dot, or null if not found
     */
    public String extractExtensionFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            // Remove query parameters and fragments
            int queryIndex = url.indexOf('?');
            if (queryIndex != -1) {
                url = url.substring(0, queryIndex);
            }
            int fragmentIndex = url.indexOf('#');
            if (fragmentIndex != -1) {
                url = url.substring(0, fragmentIndex);
            }
            
            // Extract filename from path
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash == -1 || lastSlash == url.length() - 1) {
                return null;
            }
            
            String filename = url.substring(lastSlash + 1);
            
            // URL decode in case of encoded characters
            try {
                filename = URLDecoder.decode(filename, "UTF-8");
            } catch (Exception e) {
                // Use as-is if decode fails
            }
            
            // Check multi-part extensions
            String lowerName = filename.toLowerCase(Locale.ROOT);
            for (String multiExt : MULTI_PART_EXTENSIONS) {
                if (lowerName.endsWith(multiExt)) {
                    return multiExt;
                }
            }
            
            // Extract single-part extension
            int lastDot = filename.lastIndexOf('.');
            if (lastDot > 0 && lastDot < filename.length() - 1) {
                String ext = filename.substring(lastDot);
                // Validate extension length (1-8 chars after dot)
                if (ext.length() >= 2 && ext.length() <= 9) {
                    String extBody = ext.substring(1);
                    if (extBody.matches("[A-Za-z0-9]+")) {
                        return ext.toLowerCase(Locale.ROOT);
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            if (debugMode && logger != null) {
                logger.log("Error extracting extension from URL: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Map Content-Type header to file extension
     * @param contentType The Content-Type header value
     * @return Extension with leading dot, or null if not mapped
     */
    public String mapContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        
        // Normalize: lowercase and remove charset/parameters
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon != -1) {
            normalized = normalized.substring(0, semicolon);
        }
        normalized = normalized.trim();
        
        return CONTENT_TYPE_MAP.get(normalized);
    }
    
    /**
     * Detect extension from magic bytes (file signature)
     * @param stream InputStream to read first bytes from (will read up to 16 bytes)
     * @return Extension with leading dot, or null if not recognized
     */
    public String detectExtensionFromMagic(InputStream stream) {
        if (stream == null) {
            return null;
        }
        
        try {
            // Read first 16 bytes
            byte[] buffer = new byte[16];
            stream.mark(16);
            int bytesRead = stream.read(buffer, 0, 16);
            stream.reset();
            
            if (bytesRead < 4) {
                return null;
            }
            
            // Convert to hex string
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(bytesRead, 8); i++) {
                hex.append(String.format("%02X", buffer[i] & 0xFF));
            }
            String hexString = hex.toString();
            
            // Check magic bytes (longest matches first)
            for (Map.Entry<String, String> entry : MAGIC_BYTES_MAP.entrySet()) {
                if (hexString.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            
            return null;
        } catch (Exception e) {
            if (debugMode && logger != null) {
                logger.log("Error detecting magic bytes: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Get Content-Type from URL via HTTP HEAD request
     * @param url The URL to query
     * @return Content-Type header value, or null on error
     */
    private String getContentTypeFromUrl(String url) {
        HttpURLConnection conn = null;
        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "ModUpdater/1.7.10");
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return conn.getContentType();
            }
            
            return null;
        } catch (Exception e) {
            if (debugMode && logger != null) {
                logger.log("HEAD request failed: " + e.getMessage());
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * Resolve filename with extension inference
     * 
     * @param requestedName The filename from config (may lack extension)
     * @param downloadUrl The download URL
     * @param streamSupplier Optional supplier for input stream (for magic bytes detection)
     * @param artifactType Type of artifact (MOD or FILE) - affects fallback logic
     * @return Filename with inferred extension
     */
    public String resolve(String requestedName, String downloadUrl, StreamSupplier streamSupplier, ArtifactType artifactType) {
        if (requestedName == null || requestedName.isEmpty()) {
            // No name provided - extract from URL
            requestedName = extractFilenameFromUrl(downloadUrl);
            if (requestedName == null || requestedName.isEmpty()) {
                requestedName = "download";
            }
        }
        
        // If already has extension, use as-is
        if (hasExtension(requestedName)) {
            if (debugMode && logger != null) {
                logger.log("[FilenameResolver] Filename already has extension: " + requestedName);
            }
            return requestedName;
        }
        
        if (debugMode && logger != null) {
            logger.log("[FilenameResolver] Resolving extension for: " + requestedName);
        }
        
        // Try URL path extraction
        String extension = extractExtensionFromUrl(downloadUrl);
        if (extension != null) {
            if (debugMode && logger != null) {
                logger.log("[FilenameResolver] Extension from URL: " + extension);
            }
            return requestedName + extension;
        }
        
        // Try HTTP HEAD for Content-Type
        String contentType = getContentTypeFromUrl(downloadUrl);
        if (contentType != null) {
            extension = mapContentType(contentType);
            if (extension != null) {
                if (debugMode && logger != null) {
                    logger.log("[FilenameResolver] Extension from Content-Type (" + contentType + "): " + extension);
                }
                return requestedName + extension;
            }
        }
        
        // Try magic bytes if stream supplier provided
        if (streamSupplier != null) {
            try {
                InputStream stream = streamSupplier.get();
                if (stream != null) {
                    extension = detectExtensionFromMagic(stream);
                    if (extension != null) {
                        if (debugMode && logger != null) {
                            logger.log("[FilenameResolver] Extension from magic bytes: " + extension);
                        }
                        return requestedName + extension;
                    }
                }
            } catch (Exception e) {
                if (debugMode && logger != null) {
                    logger.log("[FilenameResolver] Error getting stream for magic bytes: " + e.getMessage());
                }
            }
        }
        
        // Fallback based on artifact type
        String fallbackExt = (artifactType == ArtifactType.MOD) ? ".jar" : ".jar";
        if (debugMode && logger != null) {
            logger.log("[FilenameResolver] Using fallback extension: " + fallbackExt);
        }
        return requestedName + fallbackExt;
    }
    
    /**
     * Extract filename from URL (without extension inference)
     */
    private String extractFilenameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            // Remove query and fragment
            int queryIndex = url.indexOf('?');
            if (queryIndex != -1) {
                url = url.substring(0, queryIndex);
            }
            int fragmentIndex = url.indexOf('#');
            if (fragmentIndex != -1) {
                url = url.substring(0, fragmentIndex);
            }
            
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash == -1 || lastSlash == url.length() - 1) {
                return null;
            }
            
            String filename = url.substring(lastSlash + 1);
            try {
                filename = URLDecoder.decode(filename, "UTF-8");
            } catch (Exception e) {
                // Use as-is
            }
            
            return filename;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Artifact type enum for context-aware resolution
     */
    public enum ArtifactType {
        MOD,
        FILE
    }
    
    /**
     * Functional interface for supplying input stream
     */
    public interface StreamSupplier {
        InputStream get() throws IOException;
    }
}
