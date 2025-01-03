package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * AdocDocumentEngine orchestrates scanning directories or files, filtering
 * unwanted items, and merging content into one or more AsciiDoc outputs.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Detect full vs. incremental mode based on the presence of {@code context.asciidoc}.</li>
 *   <li>Recurse over directories, filtering files via {@link AdocFileFilter}.</li>
 *   <li>Process each file by reading lines (optionally removing copyright)
 *       and writing them to either {@code context.asciidoc} (full mode) or
 *       {@code increment.asciidoc} (incremental mode).</li>
 *   <li>Maintain line/blank/token counts ({@link AdocDocumentStats}),
 *       printing a final summary that includes any skipped files.</li>
 *   <li>Handle I/O exceptions gracefully (skipping unreadable files).</li>
 * </ul>
 *
 * <p>Usage Flow:
 * <ol>
 *   <li>Instantiate with {@link #AdocDocumentEngine(AdocFileFilter, AdocDocumentWriter, AdocDocumentStats)}.</li>
 *   <li>Set {@link #setContextAsciidoc(String)}, {@link #setIncrementalAsciidoc(String)}, and {@link #setRemoveCopyright(boolean)}.</li>
 *   <li>Add one or more paths via {@link #addInputPath(String)}.</li>
 *   <li>Call {@link #execute()} to detect mode, open the appropriate file, and process inputs.</li>
 *   <li>Call {@link #printSummary()} to append final statistics.</li>
 *   <li>Call {@link #close()} to release resources.</li>
 * </ol>
 */
public class AdocDocumentEngine {

    /** Filters files according to .gitignore / aide.ignore / overshadowing rules, etc. */
    private final AdocFileFilter fileFilter;

    /** Handles writing text (and stats updates) to the output AsciiDoc. */
    private final AdocDocumentWriter writer;

    /** Tracks global line, blank, and token counts. */
    private final AdocDocumentStats stats;

    /** Processor for reading file lines and optionally removing blocks. */
    private final AdocFileProcessor fileProcessor;

    /** List of paths (files/directories) to scan. */
    private final List<Path> inputPaths;

    /** Path to the main context file (full mode). Defaults to "context.asciidoc". */
    private String contextAsciidoc;

    /** Path to the incremental output file. Defaults to "increment.asciidoc". */
    private String incrementalAsciidoc;

    /** Whether to remove recognized copyright blocks. */
    private boolean removeCopyright;

    /** Timestamp of the context file, used for incremental checks. */
    private long contextFileLastModified;

    /** True if we are in incremental mode. */
    private boolean incrementalMode;

    /** Prevents multiple executions in the same engine instance. */
    private boolean engineExecuted;

    /** Collects file paths that fail to process (I/O issues, etc.). */
    private final List<String> skippedFiles;

    /**
     * Constructs an engine with required collaborators.
     *
     * @param fileFilter the {@link AdocFileFilter} for include/exclude decisions
     * @param writer     the {@link AdocDocumentWriter} for writing AsciiDoc output
     * @param stats      the {@link AdocDocumentStats} tracking lines/blank/token counts
     */
    public AdocDocumentEngine(AdocFileFilter fileFilter,
                              AdocDocumentWriter writer,
                              AdocDocumentStats stats) {
        this.fileFilter = fileFilter;
        this.writer = writer;
        this.stats = stats;
        this.fileProcessor = new AdocFileProcessor();
        this.inputPaths = new ArrayList<>();
        this.contextAsciidoc = "context.asciidoc";
        this.incrementalAsciidoc = "increment.asciidoc";
        this.removeCopyright = true;
        this.contextFileLastModified = 0L;
        this.incrementalMode = false;
        this.engineExecuted = false;
        this.skippedFiles = new ArrayList<>();
    }

    // ------------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------------

    /**
     * Sets the path/name of the context file for full mode.
     *
     * @param contextAsciidoc path to context file
     */
    public void setContextAsciidoc(String contextAsciidoc) {
        this.contextAsciidoc = contextAsciidoc;
    }

    /**
     * Sets the path/name of the incremental file for incremental mode.
     *
     * @param incrementalAsciidoc path to incremental file
     */
    public void setIncrementalAsciidoc(String incrementalAsciidoc) {
        this.incrementalAsciidoc = incrementalAsciidoc;
    }

    /**
     * Whether to remove recognized copyright blocks.
     *
     * @param remove true if removal is desired
     */
    public void setRemoveCopyright(boolean remove) {
        this.removeCopyright = remove;
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Adds a single file or directory path for scanning.
     *
     * @param pathStr file or directory path
     */
    public void addInputPath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) {
            return;
        }
        Path p = Paths.get(pathStr).toAbsolutePath().normalize();
        inputPaths.add(p);
    }

    /**
     * Main execution method. Checks for an existing context file; if found,
     * sets incremental mode, otherwise runs full mode. Opens the respective
     * AsciiDoc file (overwriting by default) and processes the paths.
     *
     * @throws IOException if reading or writing fails
     */
    public void execute() throws IOException {
        if (engineExecuted) {
            throw new IllegalStateException("This AdocDocumentEngine has already executed. "
                    + "Use a new instance for another run.");
        }
        engineExecuted = true;

        // 1) Check if context.asciidoc exists => set incremental
        Path contextPath = Paths.get(contextAsciidoc).toAbsolutePath();
        if (Files.exists(contextPath)) {
            incrementalMode = true;
            contextFileLastModified = Files.getLastModifiedTime(contextPath).toMillis();
        }

        // 2) Open the correct output file and print console message
        if (incrementalMode) {
            System.out.println("Incremental mode: " + incrementalAsciidoc);
            // Overwrite incrementalAsciidoc by default
            writer.open(incrementalAsciidoc, false);
            writer.write("= Directory Content (Incremental Mode)\n\n");
        } else {
            System.out.println("Full mode: " + contextAsciidoc);
            // Overwrite context.asciidoc by default
            writer.open(contextAsciidoc, false);
            writer.write("= Directory Content\n\n");
        }

        // 3) Process each path
        for (Path inputPath : inputPaths) {
            processPath(inputPath);
        }
    }

    /**
     * Prints a summary of lines, blanks, tokens, tokens/line, and any skipped files
     * to the currently open output file. Should be called after {@link #execute()}.
     */
    public void printSummary() {
        writer.write("\n....\n");
        writer.write("Lines " + stats.getTotalLines() + ", "
                + "Blanks " + stats.getTotalBlanks() + ", "
                + "Tokens " + stats.getTotalTokens() + "\n");

        double tokensPerLine = (stats.getTotalLines() == 0)
                ? 0.0
                : (double) stats.getTotalTokens() / stats.getTotalLines();
        writer.write(String.format("Tokens/Line: %.1f\n", tokensPerLine));

        if (!skippedFiles.isEmpty()) {
            writer.write("\nSkipped Files:\n");
            for (String sf : skippedFiles) {
                writer.write(" - " + sf + "\n");
            }
        }
        writer.write("....\n");
    }

    /**
     * Closes the underlying writer. Safe to call multiple times.
     */
    public void close() {
        writer.close();
    }

    // ------------------------------------------------------------------------
    // Internal Methods
    // ------------------------------------------------------------------------

    /**
     * Recursively processes a directory or processes a single file.
     *
     * @param path path to directory or file
     */
    private void processPath(Path path) {
        try {
            if (!Files.exists(path)) {
                // skip non-existent
                return;
            }
            if (Files.isDirectory(path)) {
                // Walk the directory tree
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        processSingleFile(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        skippedFiles.add(file.toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                // Single file
                processSingleFile(path);
            }
        } catch (IOException e) {
            skippedFiles.add(path.toString());
        }
    }

    /**
     * Process a single file by applying filters, checking timestamps (in incremental mode),
     * and writing content to the open AsciiDoc file with stats.
     */
    private void processSingleFile(Path path) {
        try {
            // If not included by filter, skip
            if (!fileFilter.include(path)) {
                return;
            }

            // In incremental mode, check timestamps
            if (incrementalMode) {
                long fileLastMod = Files.getLastModifiedTime(path).toMillis();
                if (fileLastMod <= contextFileLastModified) {
                    return;
                }
            }

            // Read lines, remove copyright if needed
            List<String> lines = fileProcessor.readFileLines(path);
            if (removeCopyright) {
                lines = fileProcessor.maybeRemoveCopyright(lines);
            }

            // Write a heading for this file
            writer.write("== File: " + path.getFileName() + "\n");
            writer.write("....\n");

            // Snapshot stats, then write file content
            writer.snapshotStats();
            for (String line : lines) {
                writer.write(line + "\n");
            }

            // Summarize new lines, blanks, tokens
            long dLines = stats.getDeltaLines();
            long dBlanks = stats.getDeltaBlanks();
            long dTokens = stats.getDeltaTokens();

            writer.write("....\n");
            writer.write(String.format("Lines %d, Blanks %d, Tokens %d\n\n",
                    dLines, dBlanks, dTokens));

        } catch (IOException e) {
            skippedFiles.add(path.toString());
        }
    }
}
