package com.ArfGg57.modupdater.version;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for VersionComparator
 */
public class VersionComparatorTest {
    
    @Test
    public void testParseFullVersion() {
        VersionComparator.Version v = VersionComparator.parse("1.2.3");
        assertNotNull(v);
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getPatch());
    }
    
    @Test
    public void testParseMajorMinor() {
        VersionComparator.Version v = VersionComparator.parse("1.2");
        assertNotNull(v);
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(0, v.getPatch()); // Patch defaults to 0
    }
    
    @Test
    public void testParseMajorOnly() {
        VersionComparator.Version v = VersionComparator.parse("5");
        assertNotNull(v);
        assertEquals(5, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
    }
    
    @Test
    public void testParseInvalidVersion() {
        assertNull(VersionComparator.parse("abc"));
        assertNull(VersionComparator.parse("1.x.3"));
        assertNull(VersionComparator.parse(""));
        assertNull(VersionComparator.parse(null));
    }
    
    @Test
    public void testCompareVersions() {
        assertTrue(VersionComparator.compare("1.2.3", "1.2.4") < 0);
        assertTrue(VersionComparator.compare("1.3.0", "1.2.9") > 0);
        assertTrue(VersionComparator.compare("2.0.0", "1.9.9") > 0);
        assertEquals(0, VersionComparator.compare("1.2.3", "1.2.3"));
    }
    
    @Test
    public void testCompareWithDefaultPatch() {
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"));
        assertTrue(VersionComparator.compare("1.2", "1.2.1") < 0);
    }
    
    @Test
    public void testCompareInvalidVersions() {
        // Invalid versions are treated as equal (comparison returns 0)
        assertEquals(0, VersionComparator.compare("abc", "1.2.3"));
        assertEquals(0, VersionComparator.compare("1.2.3", "xyz"));
    }
    
    @Test
    public void testIsValid() {
        assertTrue(VersionComparator.isValid("1.2.3"));
        assertTrue(VersionComparator.isValid("1.2"));
        assertTrue(VersionComparator.isValid("5"));
        assertFalse(VersionComparator.isValid("abc"));
        assertFalse(VersionComparator.isValid(""));
        assertFalse(VersionComparator.isValid(null));
    }
    
    @Test
    public void testVersionEquality() {
        VersionComparator.Version v1 = VersionComparator.parse("1.2.3");
        VersionComparator.Version v2 = VersionComparator.parse("1.2.3");
        VersionComparator.Version v3 = VersionComparator.parse("1.2.4");
        
        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
    }
    
    @Test
    public void testVersionToString() {
        VersionComparator.Version v = VersionComparator.parse("1.2.3");
        assertEquals("1.2.3", v.toString());
        
        VersionComparator.Version v2 = VersionComparator.parse("5");
        assertEquals("5.0.0", v2.toString());
    }
}
