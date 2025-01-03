package build.chronicle.aide.dc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AdocDocumentWriter}, verifying it writes text to disk and
 * updates {@link AdocDocumentStats} accordingly.
 */
class AdocDocumentWriterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        // If you need any cleanup after each test (e.g., closing the writer), do it here.
    }

    @Test
    void testWrite_updatesStatsAndFile() throws IOException {
        // Create a new stats instance
        AdocDocumentStats stats = new AdocDocumentStats();
        // Create the writer with that stats
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        Path outputFile = tempDir.resolve("testOutput.adoc");
        // Open (overwrite mode)
        writer.open(outputFile.toString(), false);

        // Write two non-blank lines
        writer.write("Line 1\n");
        writer.write("Line 2\n");

        // Verify stats
        assertEquals(2, stats.getTotalLines(), "We wrote 2 non-blank lines so far");
        assertEquals(0, stats.getTotalBlanks(), "No blank lines yet");
        assertTrue(stats.getTotalTokens() > 0, "Tokens should be > 0 if lines contain text");

        // Write a blank line
        writer.write("\n");
        assertEquals(2, stats.getTotalLines(), "Still 2 non-blank lines");
        assertEquals(1, stats.getTotalBlanks(), "Now 1 blank line total");

        writer.close();

        // Verify actual file contents
        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(3, lines.size(), "3 total lines in output file");
        assertEquals("Line 1", lines.get(0));
        assertEquals("Line 2", lines.get(1));
        assertEquals("", lines.get(2)); // blank line
    }

    @Test
    void testWrite_noFileOpen_throwsException() {
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        // Attempting to write without calling open(...) should throw
        assertThrows(IllegalStateException.class, () -> writer.write("Should fail"),
                "Should throw if no file is open for writing");
    }

    @Test
    void testOpen_appendMode() throws IOException {
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        Path outputFile = tempDir.resolve("appendTest.adoc");

        // 1) Open in overwrite mode first
        writer.open(outputFile.toString(), false);
        writer.write("First line\n");
        writer.close();

        // Check single line so far
        List<String> initialLines = Files.readAllLines(outputFile);
        assertEquals(1, initialLines.size());
        assertEquals("First line", initialLines.get(0));

        // 2) Re-open in append mode
        writer.open(outputFile.toString(), true);
        writer.write("Appended line\n");
        writer.close();

        // Verify the new line was appended
        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(2, lines.size());
        assertEquals("First line", lines.get(0));
        assertEquals("Appended line", lines.get(1));
    }
}
