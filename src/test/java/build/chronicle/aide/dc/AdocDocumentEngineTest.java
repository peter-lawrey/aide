package build.chronicle.aide.dc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdocDocumentEngineTest {

    @TempDir
    Path tempDir;

    private AdocFileFilter filter;
    private AdocDocumentStats stats;
    private AdocDocumentWriter writer;
    private AdocDocumentEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        filter = new AdocFileFilter(Path.of(".", ".gitignore"));
        stats = new AdocDocumentStats();
        writer = new AdocDocumentWriter(stats);
        engine = new AdocDocumentEngine(filter, writer, stats);

        // Default output filenames
        engine.setContextAsciidoc(tempDir.resolve("context.asciidoc").toString());
        engine.setIncrementalAsciidoc(tempDir.resolve("increment.asciidoc").toString());
    }

    @AfterEach
    void cleanup() {
        // Ensure all files are closed
        engine.close();
    }

    @Test
    void testExecute_fullContextMode_noExistingFile() throws IOException {
        // No "context.asciidoc" yet => full mode
        Path sampleAdoc = Files.write(tempDir.resolve("testFile.adoc"),
                List.of("= Title", "Line 1", "Line 2"));

        engine.addInputPath(tempDir.toString());
        engine.execute();

        // Expect "context.asciidoc" to exist (full mode) 
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "Should create context.asciidoc in full mode");

        // incremental.asciidoc should NOT exist
        assertFalse(Files.exists(tempDir.resolve("increment.asciidoc")),
                "Should not create incremental.asciidoc in full mode");
    }

    @Test
    void testExecute_incrementalMode_existingContext() throws IOException, InterruptedException {
        // 1) Create context.asciidoc to force incremental mode
        Path contextFile = tempDir.resolve("context.asciidoc");
        Files.write(contextFile, List.of("= Existing Context"));

        long oldModTime = Files.getLastModifiedTime(contextFile).toMillis();
        Thread.sleep(5); // Ensure new file is strictly newer

        // 2) Create a fresh Adoc
        Path newAdoc = Files.write(tempDir.resolve("newFile.adoc"),
                List.of("= New Title", "Content"));

        // 3) Execute
        engine.addInputPath(tempDir.toString());
        engine.execute();

        // Because context.asciidoc exists, we expect incremental mode
        // => "increment.asciidoc" is created 
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertTrue(Files.exists(incFile), "Incremental file should be created");

        // context.asciidoc is unchanged
        long newModTime = Files.getLastModifiedTime(contextFile).toMillis();
        assertEquals(oldModTime, newModTime,
                "Main context.asciidoc should remain unmodified in incremental mode");
    }

    @Test
    void testExecute_withNonExistentPath() throws IOException {
        // Add a path that doesn't exist
        engine.addInputPath(tempDir.resolve("no-such-folder").toString());
        // No exceptions should be thrown, only a warning
        engine.execute();
        // Still expect a context.asciidoc
        assertTrue(Files.exists(tempDir.resolve("context.asciidoc")));
    }

    @Test
    void testProcessFile_unreadableFile() throws IOException {
        // We can simulate unreadable by writing a file then removing read permissions
        // or simply mocking AdocFileProcessor, but here is a simple approach:
        Path lockedFile = tempDir.resolve("lockedFile.adoc");
        Files.write(lockedFile, List.of("Locked content"));
        // Make the file read-only (platform-dependent)
        lockedFile.toFile().setReadable(false);

        engine.addInputPath(lockedFile.toString());
        engine.execute();

        // The file should be in the skipped list or at least cause a warning
        // We won't assert logs here, but we can confirm the engine didn't crash
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "Should still produce context.asciidoc");
    }
}
