package build.chronicle.aide.dc;

import build.chronicle.aide.util.GitignoreFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Encapsulates the rules for including or excluding files in the scanning process.
 * <p>
 * Now also integrates a GitignoreFilter to respect any .gitignore patterns found
 * in the project root (or another configured location).
 */
public class AdocFileFilter {

    /**
     * Optional: We store a GitignoreFilter reference if we want to parse .gitignore.
     */
    private final GitignoreFilter gitignoreFilter;

    /**
     * Constructor accepting a custom path to a .gitignore file. If the file
     * doesn’t exist or fails to parse, gitignoreFilter will be null.
     *
     * @param gitignorePath path to a .gitignore file, or null to skip
     */
    public AdocFileFilter(Path gitignorePath) {
        GitignoreFilter tmp = null;
        if (gitignorePath != null && Files.exists(gitignorePath)) {
            try {
                tmp = new GitignoreFilter(gitignorePath);
            } catch (IOException e) {
                // If parsing fails, we’ll just log a warning (or ignore).
                System.err.println("[WARN] Failed to parse .gitignore: " + e.getMessage());
            }
        }
        this.gitignoreFilter = tmp;
    }

    /**
     * Determines if the given path should be processed.
     *
     * @param path the file or directory path
     * @return true if included, false if excluded
     */
    public boolean include(Path path) {
        // 1) If we have a GitignoreFilter, check if .gitignore excludes this path
        if (gitignoreFilter != null) {
            GitignoreFilter.MatchResult result = gitignoreFilter.isExcluded(path);
            if (result != GitignoreFilter.MatchResult.DEFAULT)
                return result != GitignoreFilter.MatchResult.IGNORED;
        }

        try {
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
            if (fileName.endsWith(".asciidoc")
                    || fileName.endsWith(".png")
                    || fileName.endsWith(".jpg")
                    || fileName.endsWith(".pdf")
                    || fileName.endsWith(".class")) {
                return false;
            }
            // Exclude out-* prefix
            if (fileName.startsWith("out-")) {
                return false;
            }
            // Optional: skip if bigger than 64KB
            if (Files.isRegularFile(path)) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                return attrs.size() <= 65536;
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isHiddenOrDotFile(Path path) throws IOException {
        if (Files.isHidden(path))
            return true;
        for (int i = 0; i < path.getNameCount(); i++) {
            String string = path.getName(i).toString();
            if (string.startsWith(".") && !string.equals("."))
                return true;
        }
        return false;
    }

    private boolean isOvershadowedByAd(Path path) {
        // If the path is "myFile.txt", check if "myFile.txt.ad" exists
        String fileName = path.getFileName().toString();
        Path overshadow = path.getParent() == null
                ? null
                : path.getParent().resolve(fileName + ".ad");
        return overshadow != null && Files.exists(overshadow);
    }
}
