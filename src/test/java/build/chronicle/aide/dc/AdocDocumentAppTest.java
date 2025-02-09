package build.chronicle.aide.dc;

import build.chronicle.aide.util.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static build.chronicle.aide.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class AdocDocumentAppTest {

    @TempDir
    Path tempDir;

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outContent;

    @AfterEach
    void cleanup() {
        // Reset system output if modified.
        System.setOut(originalOut);
    }

    @Test
    void testChatModeOutputGeneration() throws IOException {
        // Create a sample text file that should be included.
        Path sampleFile = tempDir.resolve("sample.txt");
        Files.write(sampleFile, List.of("This is a sample file.\nIt has two lines.\n"));

        // Create an aide.ignore file that does not exclude the sample file.
        Path ignoreFile = tempDir.resolve("aide.ignore");
        Files.write(ignoreFile, List.of("# No exclusion rules"));

        // Set system properties to use a context file within the tempDir.
        System.setProperty(AdocDocumentApp.PROP_CONTEXT, tempDir.resolve("context.asciidoc").toString());
        System.setProperty(AdocDocumentApp.PROP_REMOVE_COPYRIGHT, "true");

        // Run the main application using the tempDir as input.
        String[] args = {tempDir.toString()};
        AdocDocumentApp.main(args);

        // Verify that the context file was generated.
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "context.asciidoc should be generated in chat mode");

        String content = Files.readString(contextFile);
        // Verify that the sample file's content is included in the output.
        assertContains("sample.txt", content, "Output should include the sample file heading");
        assertContains("This is a sample file.", content, "Output should include sample file content");
    }

    @Test
    void testIgnoreRulesAreRespected() throws IOException {
        // Create a file that should be excluded according to ignore rules.
        Path excludedFile = tempDir.resolve("exclude.txt");
        Files.write(excludedFile, List.of("This file should be ignored.\n"));

        // Create an aide.ignore file that explicitly excludes "exclude.txt".
        Path ignoreFile = tempDir.resolve("aide.ignore");
        Files.write(ignoreFile, List.of("exclude.txt"));

        // Set system property for the context file.
        System.setProperty(AdocDocumentApp.PROP_CONTEXT, tempDir.resolve("context.asciidoc").toString());
        System.setProperty(AdocDocumentApp.PROP_REMOVE_COPYRIGHT, "true");

        // Run the application.
        String[] args = {tempDir.toString()};
        AdocDocumentApp.main(args);

        // Verify that the context file does not include the excluded file.
        Path contextFile = tempDir.resolve("context.asciidoc");
        assertTrue(Files.exists(contextFile), "context.asciidoc should be generated in chat mode");

        String content = Files.readString(contextFile);
        assertDoesntContain("exclude.txt", content, "The excluded file should not appear in the output");
        assertDoesntContain("This file should be ignored.", content, "Content of the excluded file should not appear");
    }

    @Test
    void testChatModeOutputWithVerbose() throws IOException {
        // Set the verbose property.
        System.setProperty("verbose", "true");

        // Capture the System.out output.
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Create a sample file.
        Path sampleFile = tempDir.resolve("verboseSample.txt");
        Files.write(sampleFile, List.of("Verbose sample content.\n"));

        // Create a basic aide.ignore file.
        Path ignoreFile = tempDir.resolve("aide.ignore");
        Files.write(ignoreFile, List.of("# No exclusion rules"));

        // Set system properties.
        System.setProperty(AdocDocumentApp.PROP_CONTEXT, tempDir.resolve("context.asciidoc").toString());
        System.setProperty(AdocDocumentApp.PROP_REMOVE_COPYRIGHT, "true");

        // Run the application.
        String[] args = {tempDir.toString()};
        AdocDocumentApp.main(args);

        // Verify that the output (console) contains verbose log markers.
        String consoleOutput = outContent.toString();
        assertContains("Using ignore file:", consoleOutput, "Console output should contain ignore file selection info");
        // In our updated engine, we assume additional verbose messages are printed (e.g., "VERBOSE: Processing file ...")
        assertContains("VERBOSE:", consoleOutput, "Verbose mode should output detailed processing logs");
        System.getProperties().remove("verbose");
    }
}
