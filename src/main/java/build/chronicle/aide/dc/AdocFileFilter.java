package build.chronicle.aide.dc;

import build.chronicle.aide.util.GitignoreFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static build.chronicle.aide.util.GitignoreFilter.*;

/**
 * Encapsulates the rules for including or excluding files in the AIDE scanning process.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Optionally check a .gitignore file first (via {@link GitignoreFilter}).</li>
 *   <li>Exclude directories, hidden files, large files, overshadowed by .ad, etc.</li>
 *   <li>Skip certain extensions (.asciidoc, images, PDFs, etc.).</li>
 *   <li>Exclude paths under 'target/' or files starting with 'out-'.</li>
 *   <li>Include relevant files (e.g., pom.xml, .adoc, Java source, etc.).</li>
 * </ul>
 */
public class AdocFileFilter {

    private static final long MAX_SIZE_BYTES = 64L * 1024L;  // 64 KB

    private final GitignoreFilter gitignoreFilter;

    // Some extensions or patterns we always skip
    private static final List<String> SKIP_EXTENSIONS = new ArrayList<>();

    static {
        // Some typical non-text or extremely large / irrelevant formats
        SKIP_EXTENSIONS.add(".asciidoc");
        SKIP_EXTENSIONS.add(".png");
        SKIP_EXTENSIONS.add(".jpg");
        SKIP_EXTENSIONS.add(".jpeg");
        SKIP_EXTENSIONS.add(".gif");
        SKIP_EXTENSIONS.add(".pdf");
        SKIP_EXTENSIONS.add(".class");
    }

    /**
     * Attempts to parse a .gitignore file for additional ignore rules.
     *
     * @param gitignorePath path to the .gitignore; may not exist
     */
    public AdocFileFilter(Path gitignorePath) {
        GitignoreFilter gf = null;
        if (gitignorePath != null) {
            try {
                gf = new GitignoreFilter(gitignorePath);
            } catch (IOException e) {
                // If parsing fails, we’ll just log a warning (or ignore).
                System.err.println("[WARN] Failed to parse .gitignore: " + e.getMessage());
            }
        }
        this.gitignoreFilter = gf;
    }

    /**
     * Determines if a given file should be included based on .gitignore
     * and local filter rules.
     *
     * @param path the file or directory path
     * @return true if included, false if excluded
     */
    public boolean include(Path path) {
        try {
            // 0) If it's a directory, skip it here (the engine handles recursion).
            if (Files.isDirectory(path)) {
                return false;
            }

            if (path.toString().contains(".git")) {
                return false;
            }

            // 1) Check if .gitignore is loaded; if so, see if it explicitly ignores or includes.
            if (gitignoreFilter != null) {
                MatchResult result = gitignoreFilter.isExcluded(path);
                if (result == MatchResult.IGNORED) {
                    return false; // explicitly ignored
                } else if (result == MatchResult.NOT_IGNORED) {
                    // explicitly included -> but still subject to overshadow logic, etc.
                    // we continue to local checks
                }
            }

            // Exclude hidden
            if (isHiddenOrDotFile(path)) {
                return false;
            }
            // Exclude overshadowed by .ad
            if (isOvershadowedByAd(path)) {
                return false;
            }
            // Exclude certain file extensions
            String fileName = path.getFileName().toString().toLowerCase();
            if (hasSkipExtension(fileName)) {
                return false;
            }

            // 5) Exclude "out-" prefix, e.g. out-something.txt
            if (fileName.startsWith("out-")) {
                return false;
            }
            // 7) Skip large files over MAX_SIZE_BYTES
            try {
                if (Files.size(path) > MAX_SIZE_BYTES) {
                    return false;
                }
            } catch (IOException e) {
                return false; // skip if can't read size
            }

            // If none of the rules exclude it, we include.
            return true;

        } catch (Exception ex) {
            // If anything unexpected happens, we fail safe by excluding
            return false;
        }
    }

    /**
     * Determines if the path is overshadowed by a companion ".ad" file.
     * E.g. if path is "someFile.txt" and "someFile.txt.ad" exists, skip.
     */
    private boolean isOvershadowedByAd(Path path) {
        String filePath = path.toString();
        Path overshadow = Path.of(filePath + ".ad");
        return Files.exists(overshadow);
    }

    /**
     * Checks for hidden or dot-file (like ".foo" or ".hidden.txt").
     */
    private boolean isHiddenOrDotFile(Path path) throws IOException {
        // isHidden is OS-dependent. Also check for filename starting with "."
        if (Files.isHidden(path)) {
            return true;
        }
        String fileName = path.getFileName().toString();
        return fileName.startsWith(".");
    }

    /**
     * Returns true if the file has one of our skip-extensions (like .png, .asciidoc).
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
