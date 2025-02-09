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

    private final AdocDocumentStats stats = new AdocDocumentStats();
    private final AdocDocumentWriter writer = new AdocDocumentWriter(stats);

    @AfterEach
    void cleanup() {
        writer.close();
    }

    @Test
    void testWrite_updatesStatsAndFile() throws IOException {
        Path outputFile = tempDir.resolve("testOutput.adoc");
        writer.open(outputFile.toString(), false);

        // Write three lines; each line ends with a newline.
        writer.write("Line 1\n");
        writer.write("Line 2\n");
        writer.write("\n"); // blank line

        writer.close();

        // Verify file contents.
        List<String> lines = Files.readAllLines(outputFile);
        // Expect 3 lines (even blank lines are counted)
        assertEquals(3, lines.size(), "There should be 3 lines in the output file");
        assertEquals("Line 1", lines.get(0));
        assertEquals("Line 2", lines.get(1));
        assertEquals("", lines.get(2), "The third line should be blank");

        // Since each newline triggers a flush in AdocDocumentStats, we expect totalLines == 3.
        assertEquals(3, stats.getTotalLines(), "The stats should reflect 3 lines written");
    }

    @Test
    void testWrite_noFileOpen_throwsException() {
        AdocDocumentWriter newWriter = new AdocDocumentWriter(new AdocDocumentStats());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> newWriter.write("Should fail"),
                "Writing without opening a file should throw an IllegalStateException");
        assertEquals("No file is open for writing.", exception.getMessage());
    }

    @Test
    void testOpen_appendMode() throws IOException {
        Path outputFile = tempDir.resolve("appendTest.adoc");

        // Open in overwrite mode and write initial content.
        writer.open(outputFile.toString(), false);
        writer.write("First line\n");
        writer.close();

        // Verify initial file content.
        List<String> initialLines = Files.readAllLines(outputFile);
        assertEquals(1, initialLines.size(), "Initial file should contain one line");
        assertEquals("First line", initialLines.get(0));

        // Reopen in append mode and write additional content.
        writer.open(outputFile.toString(), true);
        writer.write("Appended line\n");
        writer.close();

        // Verify that the new content is appended.
        List<String> allLines = Files.readAllLines(outputFile);
        assertEquals(2, allLines.size(), "File should contain two lines after appending");
        assertEquals("First line", allLines.get(0));
        assertEquals("Appended line", allLines.get(1));
    }
}
