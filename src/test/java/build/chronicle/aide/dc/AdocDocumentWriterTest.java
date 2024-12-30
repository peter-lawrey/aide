package build.chronicle.aide.dc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdocDocumentWriterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        // Just in case we opened any file, the writer should be closed
    }

    @Test
    void testWrite_updatesStatsAndFile() throws IOException {
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        Path output = tempDir.resolve("testOutput.adoc");
        writer.open(output.toString(), false);

        writer.write("Line 1\n");
        writer.write("Line 2\n");
        assertEquals(2, stats.getTotalLines());
        assertEquals(0, stats.getTotalBlanks());
        assertTrue(stats.getTotalTokens() > 0);

        writer.write("\n"); // Another blank line
        assertEquals(2, stats.getTotalLines());
        assertEquals(1, stats.getTotalBlanks());

        writer.close();

        // Verify file contents
        List<String> fileLines = Files.readAllLines(output);
        assertEquals(3, fileLines.size());
        assertEquals("Line 1", fileLines.get(0));
        assertEquals("Line 2", fileLines.get(1));
        assertEquals("", fileLines.get(2)); // blank
    }

    @Test
    void testWrite_noFileOpen_throwsException() {
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        assertThrows(IllegalStateException.class, () -> {
            writer.write("Should fail");
        });
    }

    @Test
    void testOpen_appendMode() throws IOException {
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);

        Path output = tempDir.resolve("appendTest.adoc");
        writer.open(output.toString(), false);
        writer.write("First line\n");
        writer.close();

        // Re-open in append mode
        writer.open(output.toString(), true);
        writer.write("Appended line\n");
        writer.close();

        List<String> lines = Files.readAllLines(output);
        assertEquals(2, lines.size());
        assertEquals("First line", lines.get(0));
        assertEquals("Appended line", lines.get(1));
    }
}
