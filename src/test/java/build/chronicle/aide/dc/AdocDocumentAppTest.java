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
 * Unit tests for {@link AdocDocumentApp}.
 */
class AdocDocumentAppTest {

    @TempDir
    Path tempDir;

    private static final String CONTEXT_PROP = "context";
    private static final String INCREMENT_PROP = "increment";
    private static final String REMOVE_COPYRIGHT_PROP = "removeCopyrightMessage";

    @BeforeEach
    void setup() {
        System.clearProperty(CONTEXT_PROP);
        System.clearProperty(INCREMENT_PROP);
        System.clearProperty(REMOVE_COPYRIGHT_PROP);
    }

    @AfterEach
    void cleanup() {
        System.clearProperty(CONTEXT_PROP);
        System.clearProperty(INCREMENT_PROP);
        System.clearProperty(REMOVE_COPYRIGHT_PROP);
    }

    @Test
    void testSingleAdocFileFullContextMode() throws IOException {
        Path sampleAdoc = tempDir.resolve("testFile.adoc");
        Files.write(sampleAdoc, List.of("= Sample Title", "", "Some content here.", "Another line."));

        String[] args = { tempDir.toString() };
        System.setProperty("context", tempDir.resolve("context.asciidoc").toString());

        AdocDocumentApp.main(args);

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "context.asciidoc should be created in full mode");
        assertFalse(Files.exists(tempDir.resolve("increment.asciidoc")),
                "Should not create increment.asciidoc in full mode");

        String contextContent = Files.readString(contextFile);
        assertTrue(contextContent.contains("Some content here."),
                "Should include content from the sample .adoc file");
    }

    @Test
    void testIncrementalMode() throws IOException, InterruptedException {
        Path mainContextFile = tempDir.resolve("context.asciidoc");
        Files.write(mainContextFile, List.of("= Directory Content", "== Old File: Something"));
        long oldModTime = Files.getLastModifiedTime(mainContextFile).toMillis();

        Thread.sleep(5);

        Path newAdoc = tempDir.resolve("newFile.adoc");
        Files.write(newAdoc, List.of("= New File", "Line 1", "Line 2"));

        System.setProperty(CONTEXT_PROP, mainContextFile.toString());
        System.setProperty(INCREMENT_PROP, tempDir.resolve("increment.asciidoc").toString());

        String[] args = { tempDir.toString() };
        AdocDocumentApp.main(args);

        Path incFile = tempDir.resolve("increment.asciidoc");
        assertTrue(Files.exists(incFile), "increment.asciidoc should be created in incremental mode");

        long newModTime = Files.getLastModifiedTime(mainContextFile).toMillis();
        assertEquals(oldModTime, newModTime, "context.asciidoc should remain unchanged in incremental mode");

        String incContent = Files.readString(incFile);
        assertTrue(incContent.contains("Line 1"), "Should include new .adoc content in incremental file");
    }

    @Test
    void testNoArgumentsDefaultsToDot() throws IOException {
        System.setProperty(CONTEXT_PROP, tempDir.resolve("context.asciidoc").toString());
        System.setProperty(INCREMENT_PROP, tempDir.resolve("increment.asciidoc").toString());

        String[] args = {};
        assertDoesNotThrow(() -> AdocDocumentApp.main(args),
                "No arguments should not cause a crash; default to '.'");

        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile),
                "context.asciidoc should be created even if no arguments are provided");
    }

    @Test
    void testSystemPropertiesOverride() throws IOException {
        Path customContext = tempDir.resolve("myContext.adoc");
        Path customIncrement = tempDir.resolve("myIncrement.adoc");
        System.setProperty(CONTEXT_PROP, customContext.toString());
        System.setProperty(INCREMENT_PROP, customIncrement.toString());

        String[] args = { tempDir.toString() };
        AdocDocumentApp.main(args);

        assertTrue(Files.exists(customContext), "Should create user-defined context file in full mode");
        assertFalse(Files.exists(customIncrement), "No need for incremental file on first run");

        // Run again -> incremental mode
        AdocDocumentApp.main(args);
        assertTrue(Files.exists(customIncrement),
                "Second run should now create the user-defined increment file");
    }

    /**
     * Demonstrates that if an aide.ignore file is present, it should override .gitignore logic.
     * This test will create both an 'aide.ignore' and a '.gitignore' in the tempDir, ensuring
     * that a pattern in 'aide.ignore' excludes a file that would otherwise be included by .gitignore.
     */
    @Test
    void testAideIgnoreOverridesGitignore() throws IOException {
        // 1) Create both an 'aide.ignore' and a '.gitignore' with conflicting rules
        Path aideIgnore = tempDir.resolve("aide.ignore");
        Files.write(aideIgnore, List.of(
                "# aide-specific pattern",
                "*.skip"
        ));

        Path gitIgnore = tempDir.resolve(".gitignore");
        Files.write(gitIgnore, List.of(
                "# fallback pattern, or none at all",
                "!"
        ));

        // 2) Create two files:
        //    a) one that ends with .skip -> This should be excluded by 'aide.ignore'
        //    b) one that ends with .txt -> This should be included
        Path skipFile = tempDir.resolve("shouldBeSkipped.skip");
        Files.write(skipFile, List.of("I should be skipped by aide.ignore"));

        Path includedFile = tempDir.resolve("shouldBeIncluded.txt");
        Files.write(includedFile, List.of("I should be included"));

        // 3) Directly set 'context' to produce our test output in the tempDir
        System.setProperty(CONTEXT_PROP, tempDir.resolve("context.asciidoc").toString());

        // 4) Invoke the AdocDocumentApp with the tempDir as argument
        String[] args = { tempDir.toString() };
        AdocDocumentApp.main(args);

        // 5) Validate the resulting context.asciidoc was created in full mode
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "context.asciidoc should be created in full mode");

        // 6) Read the context.asciidoc and ensure that:
        //    - 'shouldBeSkipped.skip' is NOT present
        //    - 'shouldBeIncluded.txt' IS present
        String contextContent = Files.readString(contextFile);

        assertFalse(contextContent.contains("shouldBeSkipped.skip"),
                "The .skip file should be excluded by aide.ignore");
        assertTrue(contextContent.contains("shouldBeIncluded.txt"),
                "The .txt file should be included (not excluded by aide.ignore)");
    }
}
