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

/**
 * Tests for {@link AdocDocumentEngine}.
 * <p>
 * Validates the engine's core responsibilities:
 * <ul>
 *   <li>Detects full vs. incremental mode (See Section 2.1 of adoc-document-engine.adoc)</li>
 *   <li>Recursively processes files, filtered by {@link AdocFileFilter}</li>
 *   <li>Writes output to context or increment .asciidoc files</li>
 *   <li>Handles I/O errors gracefully (skipping unreadable files)</li>
 * </ul>
 */
class AdocDocumentEngineTest {

    @TempDir
    Path tempDir;

    private AdocFileFilter filter;
    private AdocDocumentStats stats;
    private AdocDocumentWriter writer;
    private AdocDocumentEngine engine;

    /**
     * Creates fresh instances of filter, stats, writer, and engine before each test.
     *
     * @throws IOException in case of initialization errors
     */
    @BeforeEach
    void setUp() throws IOException {
        // By default, we point .gitignore to the local ".", ignoring actual parse failures for tests
        filter = new AdocFileFilter(Path.of(".", ".gitignore"));

        // Stats tracks lines, blanks, tokens
        stats = new AdocDocumentStats();

        // Writer ties stats to the final .asciidoc output
        writer = new AdocDocumentWriter(stats);

        // The engine orchestrates scanning, filtering, reading, and writing
        engine = new AdocDocumentEngine(filter, writer, stats);

        // Default output filenames in the temp directory
        engine.setContextAsciidoc(tempDir.resolve("context.asciidoc").toString());
        engine.setIncrementalAsciidoc(tempDir.resolve("increment.asciidoc").toString());
    }

    /**
     * Closes engine resources (if open) after each test to avoid file-lock issues.
     */
    @AfterEach
    void cleanup() {
        engine.close();
    }

    /**
     * Verifies that if {@code context.asciidoc} does not exist,
     * the engine runs in full context mode and creates a new context file.
     *
     * @throws IOException if file operations fail
     */
    @Test
    void testExecute_fullContextMode_noExistingFile() throws IOException {
        // Step 1: No "context.asciidoc" => engine should default to full mode
        Path sampleAdoc = tempDir.resolve("testFile.adoc");
        Files.write(sampleAdoc, List.of("= Title", "Line 1", "Line 2"));

        // Step 2: Provide the directory to the engine
        engine.addInputPath(tempDir.toString());

        // Step 3: Execute
        engine.execute();

        // Validate: "context.asciidoc" is created in full mode
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile),
                "Should create context.asciidoc in full mode");

        // Validate: no incremental file in full mode
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertFalse(Files.exists(incFile),
                "Should not create increment.asciidoc in full mode");
    }

    /**
     * Verifies incremental mode if a pre-existing {@code context.asciidoc} is found.
     * Only files newer than the context's last-modified time are processed into {@code increment.asciidoc}.
     *
     * @throws IOException          if file operations fail
     * @throws InterruptedException to handle timing between file modifications
     */
    @Test
    void testExecute_incrementalMode_existingContext() throws IOException, InterruptedException {
        // Step 1: Create a placeholder context.asciidoc to force incremental mode
        Path contextFile = tempDir.resolve("context.asciidoc");
        Files.write(contextFile, List.of("= Existing Context"));
        long oldModTime = Files.getLastModifiedTime(contextFile).toMillis();

        // Pause so the next file has a strictly newer timestamp
        Thread.sleep(5);

        // Step 2: Create a new .adoc file
        Path newAdoc = tempDir.resolve("newFile.adoc");
        Files.write(newAdoc, List.of("= New Title", "Content"));

        // Step 3: Add path and execute
        engine.addInputPath(tempDir.toString());
        engine.execute();

        // Validate: new "increment.asciidoc" is created for the updated file
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertTrue(Files.exists(incFile),
                "Incremental file should be created in incremental mode");

        // Validate: context.asciidoc remains unchanged
        long newModTime = Files.getLastModifiedTime(contextFile).toMillis();
        assertEquals(oldModTime, newModTime,
                "context.asciidoc should remain unmodified in incremental mode");
    }

    /**
     * Ensures the engine copes with non-existent paths without throwing
     * fatal exceptions and still produces a context file if any valid files remain.
     *
     * @throws IOException if file operations fail
     */
    @Test
    void testExecute_withNonExistentPath() throws IOException {
        // Provide a path that doesn't exist
        engine.addInputPath(tempDir.resolve("no-such-folder").toString());

        // Execute and expect no unhandled exception
        engine.execute();

        // We still expect a context file to be produced (even if empty or minimal)
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile),
                "Should still create context.asciidoc even if path doesn't exist");
    }

    /**
     * Confirms that files which cannot be read (e.g., lacking permissions)
     * are skipped rather than causing the engine to fail outright.
     *
     * @throws IOException if file operations fail
     */
    @Test
    void testProcessFile_unreadableFile() throws IOException {
        // Step 1: Create a file and make it unreadable (platform-dependent)
        Path lockedFile = tempDir.resolve("lockedFile.adoc");
        Files.write(lockedFile, List.of("Locked content"));
        lockedFile.toFile().setReadable(false);

        // Step 2: Add to engine and execute
        engine.addInputPath(lockedFile.toString());
        engine.execute();

        // Validate: Engine should not crash
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile),
                "Engine should still produce context.asciidoc despite unreadable file");

        // Optional: Inspect engine’s skippedFiles list if needed
        // e.g., assertTrue(engine.getSkippedFiles().contains(lockedFile.toString()));
    }
}
