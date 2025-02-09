package build.chronicle.aide.dc;

import build.chronicle.aide.util.GitignoreFilter;
import build.chronicle.aide.util.GitignoreFilter.MatchResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AdocFileFilter encapsulates the rules for including or excluding files in
 * a scanning or merging process. It integrates with {@link GitignoreFilter}
 * to respect .gitignore/aide.ignore patterns, and applies additional
 * local checks (hidden files, overshadow by .ad, skip large files, invalid text, etc.).
 *
 * <p>When the -Dverbose option is enabled, extra log messages are printed to aid debugging.</p>
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
 *   <li>Skip large files (>128 KB, configable with -Dlarge=N in KiB)</li>
 *   <li>Skip files detected as binary (using a heuristic based on invalid UTF‑8 text)</li>
 * </ol>
 */
public class AdocFileFilter {

    // Table marking byte values that should not appear in valid UTF-8 text.
    final static boolean[] INVALID_UTF8_TEXT = new boolean[256];
    /**
     * List of file extensions that are always excluded.
     * Note: Summary files have a ".ad" extension and are not excluded.
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
        SKIP_EXTENSIONS.add(".ignore");

        // Mark control characters as invalid (0x00-0x1F)...
        for (int i = 0; i < 32; i++) {
            INVALID_UTF8_TEXT[i] = true;
        }
        // ...but allow tab (\t), newline (\n), carriage return (\r), and bell (\b)
        INVALID_UTF8_TEXT['\r'] = false;
        INVALID_UTF8_TEXT['\n'] = false;
        INVALID_UTF8_TEXT['\t'] = false;
        INVALID_UTF8_TEXT['\b'] = false;

        // Mark bytes 0xC0 and 0xC1 as invalid (overlong encodings)
        INVALID_UTF8_TEXT[0xC0] = true;
        INVALID_UTF8_TEXT[0xC1] = true;
        // Mark 0xF5 through 0xFF as invalid (beyond Unicode range)
        for (int i = 0xF5; i < 0x100; i++) {
            INVALID_UTF8_TEXT[i] = true;
        }
    }

    /**
     * Max file size we allow
     */
    private final long maxSizeBytes;
    /**
     * Manages .gitignore / aide.ignore logic. May be null if no file is provided or parsing fails.
     */
    private final GitignoreFilter gitignoreFilter;
    private final boolean verbose;

    /**
     * Constructs an AdocFileFilter, optionally parsing a .gitignore or aide.ignore file.
     *
     * @param ignoreFilePath the path to aide.ignore or .gitignore; may be null or non-existent.
     * @param verbose        true to enable verbose logging
     */
    public AdocFileFilter(Path ignoreFilePath, long maxSizeBytes, boolean verbose) {
        this.maxSizeBytes = maxSizeBytes;
        this.verbose = verbose;
        GitignoreFilter gf = null;
        if (ignoreFilePath != null) {
            try {
                gf = new GitignoreFilter(ignoreFilePath);
                if (isVerbose()) {
                    System.out.println("VERBOSE: Parsed ignore file: " + ignoreFilePath);
                }
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
            // Normalize the path for consistent processing.
            Path normalized = path.toAbsolutePath().normalize();
            if (isVerbose()) {
                System.out.println("VERBOSE: Evaluating file: " + normalized);
            }
            if (Files.isDirectory(normalized)) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Skipping directory: " + normalized);
                }
                return false;
            }

            // 2) Check .gitignore / aide.ignore rules if available.
            if (gitignoreFilter != null) {
                // Use the normalized path so that the ignore rules (which use relative paths)
                // are applied consistently.
                MatchResult matchResult = gitignoreFilter.isExcluded(normalized, false);
                if (matchResult == MatchResult.IGNORED) {
                    if (isVerbose()) {
                        System.out.println("VERBOSE: Excluded by ignore file: " + normalized);
                    }
                    return false;
                }
                if (matchResult == MatchResult.NOT_IGNORED) {
                    if (isVerbose()) {
                        System.out.println("VERBOSE: Explicitly included by ignore file: " + normalized);
                    }
                    return true;
                }
            }
            // 3) Exclude hidden files and files in hidden directories.
            if (isHiddenOrInHiddenDirectory(normalized)) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding hidden file or file in hidden directory: " + normalized);
                }
                return false;
            }

            // 4) Exclude if a companion summary (.ad) file exists.
            if (isOvershadowedByAd(normalized)) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding file overshadowed by companion .ad: " + normalized);
                }
                return false;
            }

            // 5) Exclude files with disallowed extensions.
            String fileName = normalized.getFileName().toString().toLowerCase();
            if (hasSkipExtension(fileName)) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding file with disallowed extension: " + normalized);
                }
                return false;
            }

            // 6) Exclude files starting with "out-".
            if (fileName.startsWith("out-")) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding file with out- prefix: " + normalized);
                }
                return false;
            }

            // 7) Exclude files exceeding the maximum allowed size.
            try {
                long size = Files.size(normalized);
                if (size > maxSizeBytes) {
                    if (isVerbose()) {
                        System.out.println("VERBOSE: Excluding large file (" + size + " bytes): " + normalized);
                    }
                    return false;
                }
            } catch (IOException e) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding file due to size read error: " + normalized);
                }
                return false;
            }

            // 8) Exclude binary files (using a heuristic based on invalid UTF-8 bytes).
            if (isBinary(normalized)) {
                if (isVerbose()) {
                    System.out.println("VERBOSE: Excluding binary file: " + normalized);
                }
                return false;
            }
            if (isVerbose()) {
                System.out.println("VERBOSE: Including file: " + normalized);
            }
            return true;
        } catch (Exception ex) {
            if (isVerbose()) {
                System.out.println("VERBOSE: Exception in filtering file " + path + " (" + ex.getMessage() + ")");
            }
            return false;
        }
    }

    /**
     * Checks if the file is overshadowed by a companion summary file.
     * For example, "someFile.txt" is excluded if "someFile.txt.ad" exists.
     *
     * @param path the original file path
     * @return true if a companion .ad file exists, false otherwise
     */
    private boolean isOvershadowedByAd(Path path) {
        Path companion = Path.of(path.toString() + ".ad");
        return Files.exists(companion);
    }

    /**
     * Checks if the file or any of its parent directories have a name starting with a dot.
     *
     * @param path the file path
     * @return true if the file is hidden or is in a hidden directory, false otherwise
     * @throws IOException if an I/O error occurs
     */
    private boolean isHiddenOrInHiddenDirectory(Path path) throws IOException {
        // Check the file itself.
        if (Files.isHidden(path) || path.getFileName().toString().startsWith(".")) {
            return true;
        }
        // Walk up the directory hierarchy.
        Path parent = path.getParent();
        while (parent != null) {
            if (parent.getFileName() != null && parent.getFileName().toString().startsWith(".")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Checks whether the given file name ends with any extension in the skip list.
     *
     * @param fileName the file name in lower-case
     * @return true if the file name ends with a disallowed extension, false otherwise
     */
    private boolean hasSkipExtension(String fileName) {
        for (String ext : SKIP_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a file is binary by reading up to 1KB of its content and checking each byte
     * against the INVALID_UTF8_TEXT table.
     *
     * @param path the file path
     * @return true if a byte is found that is marked invalid for UTF-8 text, suggesting binary content; false otherwise
     */
    private boolean isBinary(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);
            if (bytesRead == -1) {
                // Empty file; treat as text.
                return false;
            }
            for (int i = 0; i < bytesRead; i++) {
                if (INVALID_UTF8_TEXT[buffer[i] & 0xFF]) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            // On error, default to exclusion.
            return true;
        }
    }

    private boolean isVerbose() {
        return this.verbose;
    }
}
