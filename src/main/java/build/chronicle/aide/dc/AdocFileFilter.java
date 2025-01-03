package build.chronicle.aide.dc;

import build.chronicle.aide.util.GitignoreFilter;
import build.chronicle.aide.util.GitignoreFilter.MatchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AdocFileFilter encapsulates the rules for including or excluding files in
 * a scanning or merging process. It integrates with {@link GitignoreFilter}
 * to respect .gitignore/aide.ignore patterns, and applies additional
 * local checks (hidden files, overshadow by .ad, skip large files, etc.).
 *
 * <p>See the updated adoc-file-filter.adoc for further details.</p>
 *
 * <h2>Key Steps in {@link #include(Path)}</h2>
 * <ol>
 *   <li>Check if path is a directory (skip)</li>
 *   <li>Check .gitignore/aide.ignore (via {@link GitignoreFilter})</li>
 *   <li>Skip hidden or dot files</li>
 *   <li>Skip if overshadowed by .ad</li>
 *   <li>Skip known file extensions (e.g. .asciidoc, images, etc.)</li>
 *   <li>Skip files beginning with "out-" prefix</li>
 *   <li>Skip large files (>64 KB)</li>
 * </ol>
 */
public class AdocFileFilter {

    /** Max file size we allow (64 KB). */
    private static final long MAX_SIZE_BYTES = 64L * 1024L;

    /** Manages .gitignore / aide.ignore logic. Could be null if parsing failed or no file is provided. */
    private final GitignoreFilter gitignoreFilter;

    /**
     * Common extensions or file types to skip, typically due to
     * size, binary format, or irrelevance to text-based processing.
     */
    private static final List<String> SKIP_EXTENSIONS = new ArrayList<>();

    static {
        SKIP_EXTENSIONS.add(".asciidoc");
        SKIP_EXTENSIONS.add(".png");
        SKIP_EXTENSIONS.add(".jpg");
        SKIP_EXTENSIONS.add(".jpeg");
        SKIP_EXTENSIONS.add(".gif");
        SKIP_EXTENSIONS.add(".pdf");
        SKIP_EXTENSIONS.add(".class");
    }

    /**
     * Constructs an AdocFileFilter, optionally parsing a .gitignore/aide.ignore file at the given path.
     *
     * @param ignoreFilePath path to .gitignore or aide.ignore. May be null or non-existent.
     */
    public AdocFileFilter(Path ignoreFilePath) {
        GitignoreFilter gf = null;
        if (ignoreFilePath != null) {
            try {
                gf = new GitignoreFilter(ignoreFilePath);
            } catch (IOException e) {
                System.err.println("[WARN] Failed to parse ignore file: " + ignoreFilePath
                        + " (" + e.getMessage() + ")");
            }
        }
        this.gitignoreFilter = gf;
    }

    /**
     * Determines whether a given file should be included based on
     * .gitignore/aide.ignore patterns and local skip logic.
     *
     * @param path path to a file (directories are skipped)
     * @return true if this file is accepted for processing, false otherwise
     */
    public boolean include(Path path) {
        try {
            // 1) Skip directories (the engine or orchestrator will handle recursion).
            if (Files.isDirectory(path)) {
                return false;
            }

            // 2) Check .gitignore / aide.ignore rules if available.
            if (gitignoreFilter != null) {
                MatchResult matchResult = gitignoreFilter.isExcluded(path, false);
                if (matchResult == MatchResult.IGNORED) {
                    return false; // explicitly ignored
                }
                if (matchResult == MatchResult.NOT_IGNORED) {
                    return true; // explicitly included
                }
                // matchResult == DEFAULT => continue with local checks
            }

            // 3) Skip hidden or dot files
            if (isHiddenOrDotFile(path)) {
                return false;
            }

            // 4) Skip if overshadowed by .ad
            if (isOvershadowedByAd(path)) {
                return false;
            }

            // 5) Skip known undesired extensions
            final String fileName = path.getFileName().toString().toLowerCase();
            if (hasSkipExtension(fileName)) {
                return false;
            }

            // 6) Skip files starting with "out-"
            if (fileName.startsWith("out-")) {
                return false;
            }

            // 7) Skip large files
            try {
                long size = Files.size(path);
                if (size > MAX_SIZE_BYTES) {
                    return false;
                }
            } catch (IOException e) {
                // If we can't get size, exclude by default
                return false;
            }

            // If no rule excludes the file, include it
            return true;

        } catch (Exception ex) {
            // Fail-safe: if an error occurs, exclude
            return false;
        }
    }

    /**
     * Check if this file is overshadowed by a companion ".ad" file.
     * E.g., "someFile.txt" is overshadowed if "someFile.txt.ad" exists.
     */
    private boolean isOvershadowedByAd(Path path) {
        Path overshadowCandidate = Path.of(path.toString() + ".ad");
        return Files.exists(overshadowCandidate);
    }

    /**
     * Checks if the file is hidden or has a name beginning with '.'.
     */
    private boolean isHiddenOrDotFile(Path path) throws IOException {
        if (Files.isHidden(path)) {
            return true;
        }
        String fileName = path.getFileName().toString();
        return fileName.startsWith(".");
    }

    /**
     * Checks whether the file name ends with any extension in {@link #SKIP_EXTENSIONS}.
     */
    private boolean hasSkipExtension(String fileName) {
        for (String ext : SKIP_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
