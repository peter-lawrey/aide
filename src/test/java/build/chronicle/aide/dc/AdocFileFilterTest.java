package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdocFileFilterTest {

    @TempDir
    Path tempDir;
    private AdocFileFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AdocFileFilter(Path.of(".", ".gitignore"));
    }

    @Test
    void testInclude_adocFile() throws IOException {
        Path file = Files.write(tempDir.resolve("sample.adoc"), List.of("Adoc content"));
        assertTrue(filter.include(file), "Should include a .adoc file");
    }

    @Test
    void testInclude_overshadowedByAd() throws IOException {
        Path original = Files.write(tempDir.resolve("myFile.txt"), List.of("Original text"));
        // Create overshadowing "myFile.txt.ad"
        Files.write(Paths.get(original.toString() + ".ad"), List.of("Summary text"));
        assertFalse(filter.include(original), "Should exclude original if overshadowed by .ad summary");
    }

    @Test
    void testInclude_hiddenFile() throws IOException {
        Path hidden = Files.write(tempDir.resolve(".hidden.adoc"), List.of("Secret"));
        assertFalse(filter.include(hidden), "Should exclude hidden files");
    }

    @Test
    void testInclude_imageFile() throws IOException {
        Path image = Files.write(tempDir.resolve("pic.png"), new byte[0]);
        assertFalse(filter.include(image), "Should exclude .png");
    }

    @Test
    void testInclude_outPrefix() throws IOException {
        Path outFile = Files.write(tempDir.resolve("out-blah.txt"), List.of("out file"));
        assertFalse(filter.include(outFile), "Should exclude files starting with out-");
    }

    @Test
    void testInclude_targetDirectory() throws IOException {
        Path inTarget = Files.write(Path.of("target", "insideTarget.adoc"), List.of("Inside target/classes"));
        assertFalse(filter.include(inTarget), "Should exclude files under target/ (unless test folder exception, etc.)");
    }

    @Test
    void testInclude_pomFile() throws IOException {
        Path pom = Files.write(tempDir.resolve("pom.xml"), List.of("<project></project>"));
        assertTrue(filter.include(pom), "Should include pom.xml");
    }

    @Test
    void testInclude_srcDirectoryFile() throws IOException {
        Path srcDir = Files.createDirectories(tempDir.resolve("src").resolve("main"));
        Path javaFile = Files.write(srcDir.resolve("MyClass.java"), List.of("public class MyClass{}"));
        assertTrue(filter.include(javaFile), "Should include files under src/");
    }
}
