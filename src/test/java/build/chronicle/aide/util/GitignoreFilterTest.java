package build.chronicle.aide.util;

import build.chronicle.aide.util.GitignoreFilter.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for GitignoreFilter in Java 11 style.
 * This version matches the new GitignoreFilter where isExcluded(...) returns a MatchResult.
 */
class GitignoreFilterTest {

    @TempDir(factory = LocalTempDirFactory.class)
    Path tempDir;

    private Path gitignoreFile;

    @BeforeEach
    void setUp() {
        // We'll create a dynamic .gitignore for each test
        gitignoreFile = tempDir.resolve(".gitignore");
    }

    @Test
    void testIgnoreCommentsAndBlankLines() throws IOException {
        // Lines for the .gitignore:
        //   # This is a comment
        //   (blank)
        //   *.log
        //   # Another comment
        Files.write(gitignoreFile, List.of(
                "# This is a comment",
                "",
                "*.log",
                "# Another comment"
        ));

        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        // *.log => should be MatchResult.IGNORED
        Path logFile = tempDir.resolve("myapp.log");
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(logFile),
                "*.log pattern must exclude log files"
        );

        // A random file "notes.txt" => no matching rule => MatchResult.DEFAULT
        Path txtFile = tempDir.resolve("notes.txt");
        assertEquals(
                MatchResult.DEFAULT,
                filter.isExcluded(txtFile),
                "No pattern => not excluded => DEFAULT"
        );
    }

    @Test
    void testWildcardPatterns() throws IOException {
        // Lines:
        //   *.tmp
        //   *.bak
        Files.write(gitignoreFile, List.of("*.tmp", "*.bak"));

        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        // data.tmp => IGNORED
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(tempDir.resolve("data.tmp")),
                "*.tmp => IGNORED"
        );
        // backup.bak => IGNORED
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(tempDir.resolve("backup.bak")),
                "*.bak => IGNORED"
        );
        // keep.txt => DEFAULT (no explicit rule)
        assertEquals(
                MatchResult.DEFAULT,
                filter.isExcluded(tempDir.resolve("keep.txt")),
                "No *.txt rule => DEFAULT"
        );
    }

    @Test
    void testDirectoryPattern() throws IOException {
        // Lines:
        //   target/
        //   .idea/
        Files.write(gitignoreFile, List.of("target/", ".idea/"));

        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        // target subdirectory => IGNORED
        Path targetFile = tempDir.resolve("target/classes/Example.class");
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, new byte[0]);
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(targetFile),
                "Files under target/ must be IGNORED"
        );

        // .idea subdirectory => IGNORED
        Path ideaFile = tempDir.resolve(".idea/workspace.xml");
        Files.createDirectories(ideaFile.getParent());
        Files.write(ideaFile, new byte[0]);
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(ideaFile),
                ".idea/ directory must be IGNORED"
        );

        // Some other directory => no rule => DEFAULT
        Path srcFile = tempDir.resolve("src/Hello.java");
        Files.createDirectories(srcFile.getParent());
        Files.write(srcFile, List.of("public class Hello {}"));
        assertEquals(
                MatchResult.DEFAULT,
                filter.isExcluded(srcFile),
                "No pattern => DEFAULT"
        );
    }

    @Test
    void testNegation() throws IOException {
        // Lines:
        //   *.log
        //   !keepthis.log
        Files.write(gitignoreFile, List.of("*.log", "!keepthis.log"));

        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        Path excludedLog = tempDir.resolve("debug.log");
        Path includedLog = tempDir.resolve("keepthis.log");

        // debug.log => matches *.log => IGNORED
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(excludedLog),
                "*.log => must be IGNORED"
        );

        // keepthis.log => negated => NOT_IGNORED
        assertEquals(
                MatchResult.NOT_IGNORED,
                filter.isExcluded(includedLog),
                "!keepthis.log => must be NOT_IGNORED"
        );
    }

    @Test
    void testLastMatchWins() throws IOException {
        // Conflicting patterns:
        //   *.tmp
        //   !file.tmp
        //   *.tmp
        // Explanation:
        //   1) *.tmp => ignore
        //   2) !file.tmp => do not ignore
        //   3) *.tmp => ignore again
        // => final rule is ignore => IGNORED
        Files.write(gitignoreFile, List.of("*.tmp", "!file.tmp", "*.tmp"));
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        Path testFile = tempDir.resolve("file.tmp");
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(testFile),
                "Last rule is *.tmp => IGNORED"
        );
    }

    @Test
    void testAnchoredRules() throws IOException {
        // Lines:
        //   docs/
        //   build/
        Files.write(gitignoreFile, List.of("docs/", "build/"));
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        // /docs => IGNORED
        Path docsFile = tempDir.resolve("docs/readme.md");
        Files.createDirectories(docsFile.getParent());
        Files.write(docsFile, new byte[0]);
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(docsFile),
                "Anchored docs/ => IGNORED"
        );

        // Something that merely ends with 'docs' => DEFAULT (no direct match)
        Path randomDocs = tempDir.resolve("src/mydocs/file.txt");
        Files.createDirectories(randomDocs.getParent());
        Files.write(randomDocs, List.of("stuff"));
        assertEquals(
                MatchResult.DEFAULT,
                filter.isExcluded(randomDocs),
                "Anchored 'docs/' shouldn't match 'mydocs' => DEFAULT"
        );

        // build/ => IGNORED
        Path buildFile = tempDir.resolve("build/classes/App.class");
        Files.createDirectories(buildFile.getParent());
        Files.write(buildFile, new byte[0]);
        assertEquals(
                MatchResult.IGNORED,
                filter.isExcluded(buildFile),
                "Anchored build/ => IGNORED"
        );
    }

    @Test
    void testAnchoredPatterns() throws IOException {
        // Create a .gitignore file with anchored directory patterns.
        Path gitignoreFile = tempDir.resolve(".gitignore");
        Files.write(gitignoreFile, List.of("docs/", "build/"));
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        // File under docs/ should be ignored.
        Path docsFile = tempDir.resolve("docs/readme.md");
        Files.createDirectories(docsFile.getParent());
        Files.write(docsFile, List.of("Documentation"));
        assertEquals(GitignoreFilter.MatchResult.IGNORED, filter.isExcluded(docsFile),
                "Files under 'docs/' must be ignored");

        // File under build/ should be ignored.
        Path buildFile = tempDir.resolve("build/classes/App.class");
        Files.createDirectories(buildFile.getParent());
        Files.write(buildFile, new byte[0]);
        assertEquals(GitignoreFilter.MatchResult.IGNORED, filter.isExcluded(buildFile),
                "Files under 'build/' must be ignored");
    }

    @Test
    void testWildcardAndNegationPatterns() throws IOException {
        // Create a .gitignore file with wildcard and negation rules.
        Path gitignoreFile = tempDir.resolve(".gitignore");
        Files.write(gitignoreFile, List.of("*.log", "!important.log"));
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        Path logFile = tempDir.resolve("debug.log");
        Files.write(logFile, List.of("Log data"));
        assertEquals(GitignoreFilter.MatchResult.IGNORED, filter.isExcluded(logFile),
                "*.log should be ignored");

        Path importantLog = tempDir.resolve("important.log");
        Files.write(importantLog, List.of("Important log"));
        assertEquals(GitignoreFilter.MatchResult.NOT_IGNORED, filter.isExcluded(importantLog),
                "important.log should be explicitly included");
    }

    @Test
    void testComplexConflictingRules() throws IOException {
        // Test a scenario with conflicting rules; last match should win.
        Path gitignoreFile = tempDir.resolve(".gitignore");
        Files.write(gitignoreFile, List.of("*.tmp", "!file.tmp", "*.tmp"));
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        Path testFile = tempDir.resolve("file.tmp");
        Files.write(testFile, List.of("Temporary file"));
        assertEquals(GitignoreFilter.MatchResult.IGNORED, filter.isExcluded(testFile),
                "Last rule wins: file.tmp should be ignored");
    }

    @Test
    void testEmptyGitignoreYieldsDefault() throws IOException {
        // Create an empty .gitignore file.
        Path gitignoreFile = tempDir.resolve(".gitignore");
        Files.write(gitignoreFile, List.of());
        GitignoreFilter filter = new GitignoreFilter(gitignoreFile);

        Path file = tempDir.resolve("file.txt");
        Files.write(file, List.of("content"));
        assertEquals(GitignoreFilter.MatchResult.DEFAULT, filter.isExcluded(file),
                "An empty .gitignore should yield DEFAULT for all files");
    }
}

