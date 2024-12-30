package build.chronicle.aide.dc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the new AdocDocumentApp (CLI entry point) for scanning directories,
 * merging AsciiDoc files, and producing context.asciidoc or increment.asciidoc.
 */
class AdocDocumentAppTest {

    @TempDir
    Path tempDir;  // JUnit will create a unique temp directory for each test

    // Utility method for substring asserts
    private static void assertContains(String actual, String expected, String message) {
        assertTrue(actual.contains(expected), message + "\nExpected: [" + expected + "]\nActual: [" + actual + "]");
    }

    @Test
    void testSingleAdocFileFullContextMode() throws IOException {
        // 1. Create a sample .adoc file in the temp directory
        Path sampleAdoc = tempDir.resolve("testFile.adoc");
        Files.write(sampleAdoc, List.of(
                "= Sample Title",
                "",
                "Some content here.",
                "Another line."
        ));

        // 2. We expect "context.asciidoc" to be created because there's
        //    no existing context file. We'll pass the tempDir as our input path.
        //    Also note that by default AdocDocumentApp uses "context.asciidoc".
        String[] args = {
                tempDir.toString()  // The directory to scan
        };

        // 3. Invoke AdocDocumentApp, which will scan tempDir, find "testFile.adoc",
        //    and produce a "context.asciidoc" in the *current working directory*
        //    unless we override with system properties.
        //    Often, you might set working dir = tempDir or specify -Dcontext=...
        //    For a self-contained test, do something like:
        System.setProperty("context", tempDir.resolve("context.asciidoc").toString());
        System.clearProperty("increment"); // or set it if you want
        try {
            AdocDocumentApp.main(args);
        } finally {
            // Clean up the property so it doesn't affect other tests
            System.clearProperty("context");
        }

        // 4. Verify that context.asciidoc was created in the tempDir
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "context.asciidoc should be created in full mode");

        // 5. Check contents for the file heading, listing block, and stats
        String contextContent = Files.readString(contextFile);
        assertContains(contextContent, "= Directory Content",
                "Should have the main heading in context.asciidoc");
        assertContains(contextContent, "testFile.adoc",
                "Should include the name of the processed file");
        assertContains(contextContent, "Some content here.",
                "Should include the content of the .adoc file");
        assertContains(contextContent, "Lines 3, Blanks 1, Tokens ",
                "Should display line/blank/token stats for the file");
    }

    @Test
    void testIncrementalMode() throws IOException, InterruptedException {
        // 1. First, create a 'context.asciidoc' to simulate an existing context file
        Path mainContextFile = tempDir.resolve("context.asciidoc");
        Files.write(mainContextFile, List.of(
                "= Directory Content",
                "== File: oldFile.adoc",
                "....",
                "Old content",
                "...."
        ));
        long oldModTime = Files.getLastModifiedTime(mainContextFile).toMillis();

        // 2. Sleep to ensure any new file has a strictly newer timestamp
        Thread.sleep(10);

        // 3. Create a "newFile.adoc" that should appear in the incremental output
        Path newAdoc = tempDir.resolve("newFile.adoc");
        Files.write(newAdoc, List.of("= New File", "Line 1", "Line 2"));

        // 4. We run AdocDocumentApp again, expecting it to produce "increment.asciidoc"
        //    because "context.asciidoc" already exists.
        System.setProperty("context", mainContextFile.toString());
        System.setProperty("increment", tempDir.resolve("increment.asciidoc").toString());
        try {
            String[] args = {tempDir.toString()};
            AdocDocumentApp.main(args);
        } finally {
            // Cleanup
            System.clearProperty("context");
            System.clearProperty("increment");
        }

        // 5. Verify "increment.asciidoc" is created and includes newFile.adoc
        Path incFile = tempDir.resolve("increment.asciidoc");
        assertTrue(Files.exists(incFile),
                "increment.asciidoc should be created in incremental mode");
        String incContent = Files.readString(incFile);
        assertContains(incContent, "= Directory Content Increment",
                "Should have an incremental heading");
        assertContains(incContent, "newFile.adoc",
                "Should include newFile in the incremental file");
        assertContains(incContent, "Line 1",
                "Should include new file's content");

        // The main context should remain unchanged
        long newMainContextTime = Files.getLastModifiedTime(mainContextFile).toMillis();
        assertEquals(oldModTime, newMainContextTime,
                "Main context.asciidoc modification time should remain unchanged");
    }
}
