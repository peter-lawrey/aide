package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdocFileProcessorTest {

    @TempDir
    Path tempDir;
    private AdocFileProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AdocFileProcessor();
    }

    @Test
    void testReadFileLines() throws IOException {
        Path sample = Files.write(tempDir.resolve("sample.txt"),
                List.of("Line 1", "Line 2", "Line 3"));
        List<String> lines = processor.readFileLines(sample);
        assertEquals(3, lines.size());
        assertEquals("Line 1", lines.get(0));
    }

    @Test
    void testMaybeRemoveCopyright_noneFound() {
        List<String> lines = List.of(
                "Line 1",
                "Line 2"
        );
        List<String> result = processor.maybeRemoveCopyright(lines);
        assertEquals(lines, result, "Should be unchanged if no 'Copyright ' is found");
    }

    @Test
    void testMaybeRemoveCopyright_inFirst20Lines() {
        // Lines simulating an AsciiDoc block comment
        List<String> lines = List.of(
                "////",
                "Copyright (c) 2025 My Company",
                "////",
                "= Actual Title"
        );
        // indexOfCopyright(...) would detect line 1
        List<String> result = processor.maybeRemoveCopyright(lines);
        // Lines 0..2 are removed (the block), leaving only the last line
        assertEquals(1, result.size());
        assertEquals("= Actual Title", result.get(0));
    }

    @Test
    void testMaybeRemoveCopyright_beyond20Lines() {
        // Put "Copyright " at line 21
        List<String> lines = List.of(
                "Line 0", "Line 1", "Line 2", "Line 3", "Line 4",
                "Line 5", "Line 6", "Line 7", "Line 8", "Line 9",
                "Line 10", "Line 11", "Line 12", "Line 13", "Line 14",
                "Line 15", "Line 16", "Line 17", "Line 18", "Line 19",
                "Line 20", "Copyright 2024"
        );
        List<String> result = processor.maybeRemoveCopyright(lines);
        // Should remain unchanged
        assertEquals(lines, result);
    }
}
