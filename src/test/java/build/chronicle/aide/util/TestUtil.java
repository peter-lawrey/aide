package build.chronicle.aide.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestUtil {
    private TestUtil() {
    }

    public static void assertContains(String expected, String actual, String message) {
        assertTrue(actual.contains(expected), "Expected: " + expected + " " + message);
    }

    public static void assertDoesntContain(String expected, String actual, String message) {
        assertFalse(actual.contains(expected), "Unexpected: " + expected + " " + message);
    }
}
