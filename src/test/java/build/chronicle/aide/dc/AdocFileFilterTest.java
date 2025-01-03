package build.chronicle.aide.dc;

import build.chronicle.aide.util.GitignoreFilter;
import build.chronicle.aide.util.GitignoreFilter.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AdocFileFilter} ensuring it respects .gitignore/aide.ignore
 * rules and local skip logic (directories, hidden/dot files, overshadowed by .ad,
 * certain extensions, out- prefix, large files, etc.).
 */
class AdocFileFilterTest {

    @TempDir
    Path tempDir;

    private AdocFileFilter filter;
    private Path gitignoreFile;

    @BeforeEach
    void setUp() {
        // By default, no .gitignore at the start
        // We'll create or modify it in tests that need it.
        gitignoreFile = tempDir.resolve(".gitignore");
        // For now, we pass null or a path that doesn’t exist
        // We'll create a real file in tests if needed.
        filter = new AdocFileFilter(null);
    }

    @Test
    void testDirectoryReturnsFalse() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("someDir"));
        assertFalse(filter.include(directory),
                "Directories should be skipped (return false)");
    }

    @Test
    void testHiddenDotFile() throws IOException {
        Path hiddenFile = Files.write(tempDir.resolve(".hiddenFile"), List.of("secret"));
        assertFalse(filter.include(hiddenFile),
                "Dot-file (hidden) should be excluded");
    }

    @Test
    void testOvershadowedByAd() throws IOException {
        // Create base file
        Path baseFile = Files.write(tempDir.resolve("myData.txt"), List.of("data"));

        // Create overshadow .ad
        Path overshadowAd = Files.write(Path.of(baseFile.toString() + ".ad"), List.of("summary"));

        // Filter should exclude the base file
        assertFalse(filter.include(baseFile),
                "Base file must be excluded if overshadowed by .ad");
        // For completeness, overshadowAd is presumably included if it passes other checks,
        // but we won't test overshadowAd here — that’s usually included as normal,
        // assuming no extension skip, etc.
    }

    @Test
    void testSkipExtensions() throws IOException {
        // e.g. .asciidoc, .png, .pdf
        // Test one of the known skip-extensions, e.g. .pdf
        Path pdfFile = Files.write(tempDir.resolve("report.pdf"), new byte[0]);
        assertFalse(filter.include(pdfFile),
                ".pdf files should be excluded by extension");
    }

    @Test
    void testOutPrefixExclusion() throws IOException {
        Path outFile = Files.write(tempDir.resolve("out-log.txt"), List.of("some log"));
        assertFalse(filter.include(outFile),
                "Files starting with 'out-' should be excluded");
    }

    @Test
    void testLargeFileExclusion() throws IOException {
        // Create a file just over 64 KB
        byte[] largeData = new byte[65537]; // 64KB + 1
        Path largeFile = tempDir.resolve("bigfile.bin");
        Files.write(largeFile, largeData);

        assertFalse(filter.include(largeFile),
                "File exceeding 64KB should be excluded");
    }

    @Test
    void testWithinSizeLimit() throws IOException {
        // Create a file of ~ 1 KB
        byte[] smallData = new byte[1024];
        Path smallFile = tempDir.resolve("smallFile.txt");
        Files.write(smallFile, smallData);

        // Should pass local checks (assuming it's not overshadowed, not out-, etc.)
        assertTrue(filter.include(smallFile),
                "File under 64KB and not excluded by other rules => included");
    }

    @Test
    void testNoGitignore() throws IOException {
        // We have no .gitignore => only local checks
        // e.g., a normal file that's small, no overshadow, not hidden, no out- prefix, etc.
        Path normalFile = Files.write(tempDir.resolve("myDoc.adoc"), List.of("some doc text"));

        // Wait, .adoc is typically "allowed" by local logic? Actually, by default,
        // we skip .asciidoc, but do we skip .adoc? We skip .asciidoc specifically, not .adoc
        // => So .adoc might be included unless overshadowed or large or hidden, etc.
        assertTrue(filter.include(normalFile),
                "No .gitignore => local checks => .adoc is not in skip-ext (.asciidoc is). => Should pass");
    }

    // -----------------------
    //  Test .gitignore logic
    // -----------------------
    @Test
    void testGitignoreIgnored() throws IOException {
        // Suppose .gitignore says: *.tmp
        Files.write(gitignoreFile, List.of("*.tmp"));
        // Re-instantiate filter with that .gitignore
        filter = new AdocFileFilter(gitignoreFile);

        // Create file with .tmp extension
        Path tmpFile = Files.write(tempDir.resolve("debug.tmp"), new byte[0]);
        // Should be ignored
        assertFalse(filter.include(tmpFile),
                "debug.tmp => .gitignore => IGNORE");
    }

    @Test
    void testGitignoreNotIgnored() throws IOException {
        // Suppose .gitignore says:
        //   *.log
        //   !myApp.log
        Files.write(gitignoreFile, List.of("*.log", "!myApp.log"));
        filter = new AdocFileFilter(gitignoreFile);

        Path includedFile = tempDir.resolve("myApp.log");
        Files.write(includedFile, List.of("Log data..."));

        Path excludedFile = tempDir.resolve("other.log");
        Files.write(excludedFile, List.of("Other log data"));

        // myApp.log => .gitignore => NOT_IGNORED => immediate true
        assertTrue(filter.include(includedFile),
                "Explicitly included => should pass, ignoring local checks");
        // other.log => *.log => IGNORED => false
        assertFalse(filter.include(excludedFile),
                "Should be ignored by *.log pattern");
    }

    @Test
    void testGitignoreDefault_localChecksApply() throws IOException {
        // Suppose .gitignore says: # no pattern
        Files.write(gitignoreFile, List.of("# no pattern"));
        filter = new AdocFileFilter(gitignoreFile);

        // Means everything is DEFAULT => local checks
        // We'll create a harmless file that passes local checks
        Path regularFile = Files.write(tempDir.resolve("data.txt"), List.of("some data"));
        assertTrue(filter.include(regularFile),
                "No ignoring pattern => falls to local checks => pass => true");

        // Create overshadow scenario
        Path overshadowFile = Files.write(tempDir.resolve("foo.bar"), new byte[10]);
        Path overshadowAd = Files.write(Path.of(overshadowFile.toString() + ".ad"), new byte[0]);
        // Should skip overshadowed
        assertFalse(filter.include(overshadowFile),
                "Overshadowed => false");
    }
}
