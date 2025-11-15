package com.ArfGg57.modupdater;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class HashUtils {
    public static String sha256Hex(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
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
}
