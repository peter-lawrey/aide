package build.chronicle.aide.util;

import org.eclipse.jgit.ignore.IgnoreNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GitignoreFilter reads a .gitignore file and provides an isExcluded() method
 * to test whether a given file path should be excluded based on the parsed rules.
 */
public class GitignoreFilter {

    private final Path baseDir;
    private final IgnoreNode ignoreNode;

    /**
     * Constructs a GitignoreFilter by parsing a .gitignore file.
     *
     * @param gitignorePath The path to the .gitignore file
     * @throws IOException if the file cannot be read
     */
    public GitignoreFilter(Path gitignorePath) throws IOException {
        baseDir = gitignorePath.getParent().toAbsolutePath();
        ignoreNode = new IgnoreNode();
        if (Files.exists(gitignorePath)) {
            try (FileInputStream in = new FileInputStream(gitignorePath.toFile())) {
                ignoreNode.parse(in);
            }
        }
    }

    public enum MatchResult {
        // Explicitly ignored
        IGNORED,
        // Explicitly not ignored
        NOT_IGNORED,
        // No explicit rule
        DEFAULT
    }

    /**
     * Determines whether the given path is excluded based on the .gitignore rules.
     *
     * @param file the file path to test (absolute or relative)
     * @return MatchResult indicating whether the file is ignored, explicitly included, or default.
     */
    public MatchResult isExcluded(Path file) {
        return isExcluded(file, Files.isDirectory(file));
    }

    /**
     * Determines whether the given path is excluded based on the .gitignore rules.
     *
     * @param file        the file path to test (absolute or relative)
     * @param isDirectory true if the path is a directory
     * @return MatchResult indicating whether the file is ignored, explicitly included, or default.
     */
    public MatchResult isExcluded(Path file, boolean isDirectory) {
        if (file == null) {
            return MatchResult.DEFAULT;
        }

        // Convert the file path to something relative to the base directory
        Path absFile = file.toAbsolutePath().normalize();
        Path relFile = baseDir.relativize(absFile);
        if (relFile.getName(0).toString().equals("..")) {
            relFile = file;
        }
        IgnoreNode.MatchResult result = ignoreNode.isIgnored(relFile.toString(), isDirectory);
        return switch (result) {
            case CHECK_PARENT -> {
                Path parent = relFile.getParent();
                yield isExcluded(parent, true);
                // If the file is not explicitly ignored, check if it’s in a directory that is ignored
            }
            case IGNORED -> MatchResult.IGNORED;
            case NOT_IGNORED -> MatchResult.NOT_IGNORED;
            default -> MatchResult.DEFAULT;
        };
    }
}
