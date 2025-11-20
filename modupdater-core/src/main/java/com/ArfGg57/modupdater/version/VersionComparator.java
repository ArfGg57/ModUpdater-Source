package com.ArfGg57.modupdater.version;

/**
 * VersionComparator: Semantic version parsing and comparison utility.
 * 
 * Parses and compares semantic versions in major.minor.patch format.
 * If patch is omitted, it defaults to 0.
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class VersionComparator {
    
    /**
     * Parse a semantic version string into a Version object.
     * 
     * @param versionString Version string (e.g., "1.2.3", "1.2", "1")
     * @return Parsed Version object, or null if parsing fails
     */
    public static Version parse(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            return null;
        }
        
        versionString = versionString.trim();
        String[] parts = versionString.split("\\.");
        
        try {
            int major = 0;
            int minor = 0;
            int patch = 0;
            
            if (parts.length >= 1) {
                major = Integer.parseInt(parts[0]);
            }
            if (parts.length >= 2) {
                minor = Integer.parseInt(parts[1]);
            }
            if (parts.length >= 3) {
                patch = Integer.parseInt(parts[2]);
            }
            
            return new Version(major, minor, patch);
        } catch (NumberFormatException e) {
            // Malformed version
            return null;
        }
    }
    
    /**
     * Compare two version strings.
     * 
     * @param a First version
     * @param b Second version
     * @return Negative if a < b, 0 if a == b, positive if a > b
     *         Returns 0 if either version is malformed
     */
    public static int compare(String a, String b) {
        Version vA = parse(a);
        Version vB = parse(b);
        
        if (vA == null || vB == null) {
            return 0; // Treat malformed versions as equal
        }
        
        return vA.compareTo(vB);
    }
    
    /**
     * Check if version is valid (can be parsed).
     * 
     * @param versionString Version string to check
     * @return true if version can be parsed, false otherwise
     */
    public static boolean isValid(String versionString) {
        return parse(versionString) != null;
    }
    
    /**
     * Version: Represents a semantic version with major, minor, and patch components.
     */
    public static class Version implements Comparable<Version> {
        private final int major;
        private final int minor;
        private final int patch;
        
        public Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }
        
        public int getMajor() {
            return major;
        }
        
        public int getMinor() {
            return minor;
        }
        
        public int getPatch() {
            return patch;
        }
        
        @Override
        public int compareTo(Version other) {
            if (this.major != other.major) {
                return Integer.compare(this.major, other.major);
            }
            if (this.minor != other.minor) {
                return Integer.compare(this.minor, other.minor);
            }
            return Integer.compare(this.patch, other.patch);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Version)) return false;
            Version other = (Version) obj;
            return this.major == other.major && 
                   this.minor == other.minor && 
                   this.patch == other.patch;
        }
        
        @Override
        public int hashCode() {
            return major * 10000 + minor * 100 + patch;
        }
        
        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
