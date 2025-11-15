package com.ArfGg57.modupdater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class HashUtils {

    private static final int BUFFER_SIZE = 8192;

    // Your original sha256Hex method
    public static String sha256Hex(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[BUFFER_SIZE];
            int r;
            while ((r = fis.read(buf)) != -1) md.update(buf, 0, r);
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calculates the hash of a file using a specified algorithm (e.g., "MD5", "SHA-1", "SHA-256").
     * This is the method required by FileUtils.java.
     * @param file The file to hash.
     * @param algorithm The hashing algorithm to use (e.g., "MD5", "SHA-1").
     * @return The hash as a hex string.
     * @throws Exception if the file cannot be read or the algorithm is invalid.
     */
    public static String digestHex(File file, String algorithm) throws Exception {
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException("File not found or is a directory: " + file.getAbsolutePath());
        }

        MessageDigest digest;
        try {
            // Note: The algorithm name should typically be uppercased for MessageDigest.getInstance()
            digest = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT).replaceAll("-", ""));
        } catch (NoSuchAlgorithmException e) {
            // Re-throw as a checked exception (or RuntimeException) if you prefer
            throw new IllegalArgumentException("Unknown hashing algorithm: " + algorithm, e);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[BUFFER_SIZE];
            int bytesCount;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        } catch (IOException e) {
            throw new IOException("Failed to read file for hashing: " + file.getAbsolutePath(), e);
        }

        byte[] hashedBytes = digest.digest();

        // Convert byte array to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hashedBytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}