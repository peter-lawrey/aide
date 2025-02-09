package build.chronicle.aide.dc;

import build.chronicle.aide.util.LocalTempDirFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static build.chronicle.aide.util.TestUtil.assertContains;
import static build.chronicle.aide.util.TestUtil.assertDoesntContain;
import static org.junit.jupiter.api.Assertions.*;

class AdocDocumentEngineTest {

    @TempDir(factory = LocalTempDirFactory.class)
    Path tempDir;

    private AdocFileFilter filter;
    private AdocDocumentStats stats;
    private AdocDocumentWriter writer;
    private AdocDocumentEngine engine;
    private PrintStream originalOut;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void setUp() {
        // Set up for capturing System.out when verbose is enabled.
        originalOut = System.out;
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Instantiate core components.
        filter = new AdocFileFilter(Path.of(".", "test.ignore"), 128 << 10, false);
        stats = new AdocDocumentStats();
        writer = new AdocDocumentWriter(stats);
        engine = new AdocDocumentEngine(filter, writer, stats);

        // Set output filenames in the temp directory.
        engine.setContextAsciidoc(tempDir.resolve("context.asciidoc").toString());
        engine.setIncrementalAsciidoc(tempDir.resolve("increment.asciidoc").toString());
    }

    @AfterEach
    void cleanup() {
        engine.close();
        System.setOut(originalOut);
    }

    @Test
    void testExecute_fullContextMode_noExistingFile() throws IOException {
        // Create a sample .adoc file.
        Path sampleAdoc = tempDir.resolve("testFile.adoc");
        Files.write(sampleAdoc, List.of("= Title", "Line 1", "Line 2"));

        engine.addInputPath(tempDir.toString());
        engine.execute();

        // Validate: context.asciidoc is created.
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "Should create context.asciidoc in full mode");

        // Validate: incremental file is not created.
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertFalse(Files.exists(incFile), "Should not create increment.asciidoc in full mode");
    }

    @Test
    void testExecute_incrementalMode_existingContext() throws IOException, InterruptedException {
        // Create a placeholder context.asciidoc to force incremental mode.
        Path contextFile = tempDir.resolve("context.asciidoc");
        Files.write(contextFile, List.of("= Existing Context"));
        long oldModTime = Files.getLastModifiedTime(contextFile).toMillis();

        // Wait so that the next file is newer.
        Thread.sleep(5);

        // Create a new .adoc file.
        Path newAdoc = tempDir.resolve("newFile.adoc");
        Files.write(newAdoc, List.of("= New Title", "Content"));

        engine.addInputPath(tempDir.toString());
        engine.execute();

        // Validate: incremental file is created.
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertTrue(Files.exists(incFile), "Incremental file should be created in incremental mode");

        // Validate: context.asciidoc remains unchanged.
        long newModTime = Files.getLastModifiedTime(contextFile).toMillis();
        assertEquals(oldModTime, newModTime, "context.asciidoc should remain unmodified in incremental mode");
    }

    @Test
    void testExecute_withNonExistentPath() throws IOException {
        engine.addInputPath(tempDir.resolve("no-such-folder").toString());
        engine.execute();

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "Should create context.asciidoc even if path doesn't exist");
    }

    @Test
    void testProcessFile_unreadableFile() throws IOException {
        Path lockedFile = tempDir.resolve("lockedFile.adoc");
        Files.write(lockedFile, List.of("Locked content"));
        lockedFile.toFile().setReadable(false);

        engine.addInputPath(lockedFile.toString());
        engine.execute();

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "Engine should produce context.asciidoc despite unreadable file");
    }

    @Test
    void testEngineGeneratesChatOptimizedOutput() throws IOException {
        // Create a variety of files (normal, ignored, hidden, binary, etc.)
        Path textFile = tempDir.resolve("readme.txt");
        Files.write(textFile, List.of("This is the readme file.", "It contains important information."));

        Path aideIgnore = tempDir.resolve("aide.ignore");
        Files.write(aideIgnore, List.of("exclude.txt"));
        Path excludedFile = tempDir.resolve("exclude.txt");
        Files.write(excludedFile, List.of("This file should be excluded."));

        Path hiddenFile = tempDir.resolve(".hiddenFile.txt");
        Files.write(hiddenFile, List.of("Hidden content."));

        Path binaryFile = tempDir.resolve("binary.dat");
        Files.write(binaryFile, new byte[]{0, 1, 2, 3, 4});

        Path hiddenDir = Files.createDirectory(tempDir.resolve(".hiddenDir"));
        Path fileInHiddenDir = Files.createFile(hiddenDir.resolve("file.txt"));
        Files.write(fileInHiddenDir, List.of("Content in hidden directory."));

        Path baseFile = tempDir.resolve("document.log");
        Files.write(baseFile, List.of("Full document content."));
        Path summaryFile = tempDir.resolve("document.log.ad");
        Files.write(summaryFile, List.of("Summary content."));

        AdocFileFilter localFilter = new AdocFileFilter(aideIgnore, 128 << 10, false);
        AdocDocumentStats localStats = new AdocDocumentStats();
        AdocDocumentWriter localWriter = new AdocDocumentWriter(localStats);
        AdocDocumentEngine localEngine = new AdocDocumentEngine(localFilter, localWriter, localStats);
        localEngine.setContextAsciidoc(tempDir.resolve("context.asciidoc").toString());
        localEngine.setIncrementalAsciidoc(tempDir.resolve("increment.asciidoc").toString());
        localEngine.setRemoveCopyright(true);
        localEngine.addInputPath(tempDir.toString());

        localEngine.execute();
        localEngine.printSummary();
        localEngine.close();

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "The context.asciidoc file should be generated");

        String content = Files.readString(contextFile);
        assertContains("readme.txt", content, "readme.txt should be included in the output");
        assertContains("Summary content", content, "Summary file content should be included");
        assertDoesntContain("exclude.txt", content, "Excluded files must not appear");
        assertDoesntContain(".hiddenFile.txt", content, "Hidden files must not be included");
        assertDoesntContain("binary.dat", content, "Binary files must not be included");
        assertDoesntContain("file.txt", content, "Files in hidden directories must be excluded");
    }

    @Test
    void testVerboseLoggingOutput() throws IOException {
        // In our updated engine, if verbose is enabled, each processed file prints a log starting with "VERBOSE:".
        // Create a sample file to trigger processing.
        Path sampleFile = tempDir.resolve("verboseTest.adoc");
        Files.write(sampleFile, List.of("Sample verbose content.\n"));

        engine.addInputPath(tempDir.toString());
        engine.setVerbose(true);
        engine.execute();

        // Capture output from System.out (already captured in outContent).
        String output = outContent.toString();

        assertContains("VERBOSE: No existing context file; running in full mode.", output, "Verbose mode should output detailed log entries");
        assertContains("VERBOSE: Recursing into directory:", output, "Verbose mode should output detailed log entries");
        assertContains("VERBOSE: Skipping file ", output, "Verbose mode should output detailed log entries");
    }

    @Test
    void testExecute_withSearchPattern() throws IOException {
        // Create a file that does not match by name but contains matching content.
        Path file = tempDir.resolve("Example.java");
        List<String> content = List.of(
                "// This is a sample Java file.", // no match
                "public class Example {",
                "    private int count;",         // match here ("count")
                "    public void increment() {",
                "        count++;",              // match here ("count")
                "    }",
                "}"
        );
        Files.write(file, content);

        // Configure the engine to search for "count" with one line of before/after context.
        engine.setSearchPattern("count", 1);

        engine.addInputPath(tempDir.toString());
        engine.execute();
        engine.printSummary();
        engine.close();

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "The context.asciidoc file should be generated");

        String output = Files.readString(contextFile);
        // Verify that the output contains a marker (">>") for the matched line.
        assertContains(".lines [", output,  "Output should contain match marker for search pattern");
    }
}
