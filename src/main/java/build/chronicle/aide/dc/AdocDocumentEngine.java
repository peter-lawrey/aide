package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Central orchestrator for scanning, filtering, processing, and writing outputs.
 *
 * <p>This class decides whether to operate in full context mode (if the context file
 * does not exist) or incremental mode (if the context file already exists). It
 * processes files through the provided filter, reads/cleans lines, and writes
 * them into the AsciiDoc context or incremental file.
 */
public class AdocDocumentEngine implements AutoCloseable {

    private final AdocFileFilter fileFilter;
    private final AdocDocumentWriter writer;
    private final AdocDocumentStats stats;

    private final List<Path> inputPaths;
    private String contextAsciidoc;
    private String incrementalAsciidoc;
    private boolean removeCopyright;
    private long contextFileLastModified;
    private final List<String> skippedFiles;

    private boolean incrementalMode;
    private boolean engineExecuted;

    private final AdocFileProcessor fileProcessor;

    /**
     * Constructs the engine with the necessary filter, writer, and stats.
     *
     * @param fileFilter file inclusion/exclusion rules
     * @param writer     the writer that appends to output files
     * @param stats      the stats tracker for lines, blanks, tokens
     */
    public AdocDocumentEngine(AdocFileFilter fileFilter,
                              AdocDocumentWriter writer,
                              AdocDocumentStats stats) {
        this.fileFilter = fileFilter;
        this.writer = writer;
        this.stats = stats;
        this.inputPaths = new ArrayList<>();
        this.skippedFiles = new ArrayList<>();
        this.fileProcessor = new AdocFileProcessor();
    }

    /**
     * Adds a path (file or directory) for processing.
     *
     * @param pathStr the path string to add
     */
    public void addInputPath(String pathStr) {
        if (pathStr == null) {
            return;
        }
        Path p = Path.of(pathStr);
        inputPaths.add(p);
    }

    /**
     * Sets the main context AsciiDoc filename.
     *
     * @param contextFileName the file name for the context (full) output
     */
    public void setContextAsciidoc(String contextFileName) {
        this.contextAsciidoc = contextFileName;
    }

    /**
     * Sets the incremental AsciiDoc filename.
     *
     * @param incrementalFileName the file name for the incremental output
     */
    public void setIncrementalAsciidoc(String incrementalFileName) {
        this.incrementalAsciidoc = incrementalFileName;
    }

    /**
     * Specifies whether to remove copyright blocks.
     *
     * @param remove true to remove, false otherwise
     */
    public void setRemoveCopyright(boolean remove) {
        this.removeCopyright = remove;
    }

    /**
     * Executes the scanning and writing process.
     *
     * @throws IOException if an I/O error occurs
     */
    public void execute() throws IOException {
        if (engineExecuted) {
            // Prevent multiple calls
            return;
        }
        engineExecuted = true;

        // Determine if contextAsciidoc already exists
        Path contextPath = Path.of(contextAsciidoc);
        if (Files.exists(contextPath)) {
            incrementalMode = true;
            contextFileLastModified = Files.getLastModifiedTime(contextPath).toMillis();
        } else {
            incrementalMode = false;
        }

        if (incrementalMode) {
            // We open/append to the incremental file
            writer.open(incrementalAsciidoc, false);
            writer.write("= Directory Content Increment\n\n");
        } else {
            // We open/overwrite the context file
            writer.open(contextAsciidoc, false);
            writer.write("= Directory Content\n\n");
        }
        writer.snapshotStats();

        // Process input paths
        for (Path p : inputPaths) {
            processPath(p);
        }
    }

    /**
     * Prints a summary of lines, blanks, tokens, and skipped files.
     */
    public void printSummary() {
        long lines = stats.getTotalLines();
        long blanks = stats.getTotalBlanks();
        long tokens = stats.getTotalTokens();
        double ratio = (lines == 0) ? 0.0 : (double) tokens / (double) lines;

        writer.write("== Summary ==\n");
        writer.write("Total Lines:  " + lines + "\n");
        writer.write("Total Blanks: " + blanks + "\n");
        writer.write("Total Tokens: " + tokens + "\n");
        writer.write(String.format("Tokens/Line:  %.1f%n", ratio));

        if (!skippedFiles.isEmpty()) {
            writer.write("\nSkipped Files:\n");
            for (String f : skippedFiles) {
                writer.write(" - " + f + "\n");
            }
        }
    }

    /**
     * Closes any underlying resources (if not already closed).
     */
    @Override
    public void close() {
        writer.close();
    }

    /**
     * Recursively processes a file or directory path.
     *
     * @param path the path to process
     * @throws IOException if an I/O error occurs
     */
    private void processPath(Path path) throws IOException {
        if (!Files.exists(path)) {
            // Non-existent path, skip
            return;
        }
        if (Files.isDirectory(path)) {
            // Recurse
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (fileFilter.include(file)) {
                        // If incremental mode, only process if file is new/updated
                        if (incrementalMode) {
                            long lastMod = Files.getLastModifiedTime(file).toMillis();
                            if (lastMod > contextFileLastModified) {
                                processFile(file);
                            }
                        } else {
                            processFile(file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            // Single file
            if (fileFilter.include(path)) {
                if (incrementalMode) {
                    long lastMod = Files.getLastModifiedTime(path).toMillis();
                    if (lastMod > contextFileLastModified) {
                        processFile(path);
                    }
                } else {
                    processFile(path);
                }
            }
        }
    }

    /**
     * Processes a single file: reads lines, optionally removes copyright,
     * and writes them to the open output file.
     *
     * @param path the file to process
     */
    private void processFile(Path path) {
        try {
            List<String> lines = fileProcessor.readFileLines(path);
            if (removeCopyright) {
                lines = fileProcessor.maybeRemoveCopyright(lines);
            }

            writer.write("== File: " + relativePathString(path) + "\n");
            writer.write("....\n");
            writer.snapshotStats();
            for (String line : lines) {
                writer.write(line + "\n");
            }

            // Deltas (lines, blanks, tokens) for this file
            long dLines = stats.getDeltaLines();
            long dBlanks = stats.getDeltaBlanks();
            long dTokens = stats.getDeltaTokens();

            writer.write("....\n\n");
            writer.write("Lines " + dLines + ", Blanks " + dBlanks + ", Tokens " + dTokens + "\n\n");

        } catch (IOException e) {
            skippedFiles.add(path.toString());
        }
    }

    private String relativePathString(Path path) {
        try {
            // Attempt to show a relative path if possible
            Path current = Path.of("").toAbsolutePath();
            return current.relativize(path.toAbsolutePath()).toString();
        } catch (Exception e) {
            // Fallback
            return path.toString();
        }
    }
}
