package build.chronicle.aide.dc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import build.chronicle.aide.util.LocalTempDirFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AdocFileProcessorEdgeCaseTests {

    @TempDir(factory = LocalTempDirFactory.class)
    Path tempDir;

    @Test
    void handlesBomCharacters() throws IOException {
        // Create content with BOM
        byte[] bomContent = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF, 'H', 'e', 'l', 'l', 'o'};
        Path bomFile = tempDir.resolve("bom.txt");
        Files.write(bomFile, bomContent);
        
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> lines = processor.readFileLines(bomFile);
        
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("\uFEFF"), "BOM should be preserved");
        assertEquals("\uFEFFHello", lines.get(0));
    }

    @Test
    void handlesZeroWidthCharacters() throws IOException {
        String content = "Line\u200B 1\nLine\u200B 2"; // Contains zero-width spaces
        Path testFile = tempDir.resolve("zero-width.txt");
        Files.writeString(testFile, content);
        
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> lines = processor.readFileLines(testFile);
        
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\u200B"));
    }

    @Test
    void handlesNonBreakingSpaces() throws IOException {
        String content = "Line\u00A0One\nLine\u00A0Two"; // Contains non-breaking spaces
        Path testFile = tempDir.resolve("nbsp.txt");
        Files.writeString(testFile, content);
        
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> lines = processor.readFileLines(testFile);
        
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\u00A0"));
    }

    private static Stream<Arguments> complexCopyrightProvider() {
        return Stream.of(
            Arguments.of(
                "Nested Comments",
                Arrays.asList(
                    "/*",
                    " * Outer comment start",
                    " * /*",
                    " *  * Copyright (c) 2025",
                    " *  */",
                    " * Outer comment end",
                    " */",
                    "public class Test {}"
                ),
                // probably shouldn't do this, but this is a broken use case
                " * Outer comment end,  */, public class Test {}"
            ),
            Arguments.of(
                "Mixed Comment Styles",
                Arrays.asList(
                    "Preamble",
                    "////",
                    "// Copyright notice",
                    "/*",
                    " * Copyright (c) 2025",
                    " */",
                    "////",
                    "Content starts here"
                ),
                "Preamble, Content starts here"
            ),
            Arguments.of(
                "Unicode Copyright Symbol",
                Arrays.asList(
                    "/*",
                    " * © 2025 Company Name",
                    " */",
                    "public class Test {}"
                ),
                // doesn't remove the line with the copyright symbol
                "/*,  * © 2025 Company Name,  */, public class Test {}"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("complexCopyrightProvider")
    void handlesComplexCopyrightScenarios(String description, List<String> input, String expectedResult) {
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> result = processor.maybeRemoveCopyright(input);
        
        assertEquals(Arrays.asList(expectedResult.split(", ")), result,
            "Failed to handle " + description);
    }

    @Test
    void handlesEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);
        
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> lines = processor.readFileLines(emptyFile);
        
        assertTrue(lines.isEmpty());
    }

    @Test
    void handlesLongLines() throws IOException {
        // Create a very long line (>8192 characters)
        String longLine = "a".repeat(10000);
        
        Path longLineFile = tempDir.resolve("long-line.txt");
        Files.write(longLineFile, List.of(longLine));
        
        AdocFileProcessor processor = new AdocFileProcessor();
        List<String> lines = processor.readFileLines(longLineFile);
        
        assertEquals(1, lines.size());
        assertEquals(10000, lines.get(0).length());
    }
}