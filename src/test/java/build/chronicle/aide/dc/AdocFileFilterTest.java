package build.chronicle.aide.dc;

import build.chronicle.aide.util.LocalTempDirFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AdocFileFilter} ensuring it respects .gitignore/aide.ignore
 * rules and local skip logic (directories, hidden/dot files, overshadowed by .ad,
 * certain extensions, out- prefix, large files, etc.).
 */
class AdocFileFilterTest {

    @TempDir(factory = LocalTempDirFactory.class)
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
        filter = new AdocFileFilter(null, 128 << 10, false);
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
        Path overshadowAd = Files.write(Path.of(baseFile + ".ad"), List.of("summary"));

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
        // Create a file just over 128 KB
        byte[] largeData = new byte[(128 << 10) + 1]; // 64KB + 1
        Path largeFile = tempDir.resolve("bigfile.bin");
        Files.write(largeFile, largeData);

        assertFalse(filter.include(largeFile),
                "File exceeding 128KB should be excluded");
    }

    @Test
    void testWithinSizeLimit() throws IOException {
        // Create a file of ~ 1 KB
        byte[] smallData = new byte[1024];
        for (int i = 0; i < 1024; i++) {
            smallData[i] = 'a'; // must be a valid character
        }
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
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);

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
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);

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
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);

        // Means everything is DEFAULT => local checks
        // We'll create a harmless file that passes local checks
        Path regularFile = Files.write(tempDir.resolve("data.txt"), List.of("some data"));
        assertTrue(filter.include(regularFile),
                "No ignoring pattern => falls to local checks => pass => true");

        // Create overshadow scenario
        Path overshadowFile = Files.write(tempDir.resolve("foo.bar"), new byte[10]);
        Path overshadowAd = Files.write(Path.of(overshadowFile + ".ad"), new byte[0]);
        // Should skip overshadowed
        assertFalse(filter.include(overshadowFile),
                "Overshadowed => false");
    }


    @Test
    void testHiddenFileExcluded() throws Exception {
        // Create a file that starts with a dot.
        Path hiddenFile = Files.createFile(tempDir.resolve(".hidden.txt"));
        AdocFileFilter filter = new AdocFileFilter(null, 128 << 10, false);
        assertFalse(filter.include(hiddenFile), "Hidden file (starting with '.') should be excluded");
    }

    @Test
    void testFileInHiddenDirectoryExcluded() throws Exception {
        // Create a hidden directory and a file within it.
        Path hiddenDir = Files.createDirectory(tempDir.resolve(".hiddenDir"));
        Path fileInHiddenDir = Files.createFile(hiddenDir.resolve("file.txt"));
        AdocFileFilter filter = new AdocFileFilter(null, 128 << 10, false);
        assertFalse(filter.include(fileInHiddenDir), "Files in directories starting with '.' should be excluded");
    }

    @Test
    void testCompanionAdFileReplacesOriginal() throws Exception {
        // Create a normal text file and a companion summary file with .ad extension.
        Path baseFile = Files.createFile(tempDir.resolve("document.txt"));
        Files.writeString(baseFile, "Full document content.");
        Path companionFile = Files.createFile(tempDir.resolve("document.txt.ad"));
        Files.writeString(companionFile, "Summary content.");

        AdocFileFilter filter = new AdocFileFilter(null, 128 << 10, false);
        // The base file must be excluded because a companion exists.
        assertFalse(filter.include(baseFile), "Base file should be excluded if a companion .ad file exists");
        // The companion file should be included if it passes other local checks.
        assertTrue(filter.include(companionFile), "Companion .ad file should be included");
    }

    @Test
    void testBinaryFileExclusion() throws Exception {
        // Create a file with binary content.
        Path binaryFile = Files.createFile(tempDir.resolve("image.bin"));
        byte[] binaryContent = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        Files.write(binaryFile, binaryContent);
        // For this test, assume files with a .bin extension are treated as binary and excluded.
        AdocFileFilter filter = new AdocFileFilter(null, 128 << 10, false);
        assertFalse(filter.include(binaryFile), "Binary files should be excluded");
    }

    // -------------------------
    // Tests for default local filtering
    // -------------------------

    @Test
    void testDirectoryIsExcluded() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("someDir"));
        assertFalse(filter.include(directory), "Directories must be excluded from processing");
    }

    @Test
    void testNestedHiddenDirectoryExcluded() throws IOException {
        // Create a nested structure: tempDir/sub/.hidden/subfile.txt
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Path hiddenNestedDir = Files.createDirectory(subDir.resolve(".hiddenSub"));
        Path nestedFile = Files.createFile(hiddenNestedDir.resolve("nested.txt"));
        Files.writeString(nestedFile, "Nested file in a hidden directory");
        assertFalse(filter.include(nestedFile),
                "Files in nested directories starting with '.' must be excluded");
    }

    @Test
    void testAllowedFileIncluded() throws IOException {
        // Create a normal text file that should pass all local checks.
        Path textFile = Files.write(tempDir.resolve("document.txt"), List.of("Regular content"));
        assertTrue(filter.include(textFile),
                "Regular text files that do not trigger any exclusion rule must be included");
    }

    @Test
    void testDisallowedExtensionsExcluded() throws IOException {
        // Files ending in .asciidoc or .png must always be excluded per updated requirements.
        Path adocFile = Files.write(tempDir.resolve("guide.png"), List.of("Some guide content"));
        Path asciidocFile = Files.write(tempDir.resolve("manual.asciidoc"), List.of("Manual content"));
        assertFalse(filter.include(adocFile),
                "Files ending with '.png' must be excluded");
        assertFalse(filter.include(asciidocFile),
                "Files ending with '.asciidoc' must be excluded");
    }

    // -------------------------
    // Tests for companion file (overshadowing) behavior
    // -------------------------

    @Test
    void testBaseFileExcludedWhenCompanionSummaryExists() throws IOException {
        // Create a base file.
        Path baseFile = Files.write(tempDir.resolve("report.log"), List.of("Full report content."));
        // Create a companion summary file.
        Path summaryFile = Files.write(tempDir.resolve("report.log.ad"), List.of("Summary of report."));
        // The base file should be excluded because the summary file replaces it.
        assertFalse(filter.include(baseFile),
                "Base file must be excluded when a companion summary (.ad) file exists");
        // The companion summary file should be processed normally.
        assertTrue(filter.include(summaryFile),
                "Companion summary (.ad) file should be included if it passes local checks");
    }

    // -------------------------
    // Tests for Gitignore-based filtering
    // -------------------------

    @Test
    void testGitignoreExcludesFile() throws IOException {
        // Write a .gitignore file that excludes *.tmp files.
        Files.write(gitignoreFile, List.of("*.tmp"));
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);
        Path tmpFile = Files.write(tempDir.resolve("debug.tmp"), new byte[10]);
        assertFalse(filter.include(tmpFile),
                "Files matching '*.tmp' per .gitignore must be excluded");
    }

    @Test
    void testGitignoreIncludesFileViaNegation() throws IOException {
        // Write a .gitignore file with an exclusion pattern and an explicit inclusion.
        Files.write(gitignoreFile, List.of("*.log", "!keep.log"));
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);
        Path includedFile = Files.write(tempDir.resolve("keep.log"), List.of("Log content"));
        Path excludedFile = Files.write(tempDir.resolve("debug.log"), List.of("Debug log"));
        assertTrue(filter.include(includedFile),
                "File explicitly included via negation must be processed");
        assertFalse(filter.include(excludedFile),
                "File matching '*.log' and not negated must be excluded");
    }

    @Test
    void testGitignoreDefaultFallsBackToLocalChecks() throws IOException {
        // Create a .gitignore that does not define any rules.
        Files.write(gitignoreFile, List.of("# No patterns"));
        filter = new AdocFileFilter(gitignoreFile, 128 << 10, false);
        // A normal file should then be processed based solely on local rules.
        Path normalFile = Files.write(tempDir.resolve("notes.txt"), List.of("Some notes"));
        assertTrue(filter.include(normalFile),
                "Without explicit ignore rules, local checks should allow a normal file");
    }
}

