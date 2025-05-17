package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class AdocDocumentEngine {

    private final AdocFileFilter fileFilter;
    private final AdocDocumentWriter writer;
    private final AdocDocumentStats stats;
    private final AdocFileProcessor fileProcessor;
    private final List<Path> inputPaths;
    private final List<String> skippedFiles;
    private String contextAsciidoc;
    private String incrementalAsciidoc;
    private boolean removeCopyright;
    private long contextFileLastModified;
    private boolean incrementalMode;
    private boolean engineExecuted;
    // New fields for contextual search.
    private String searchPattern;
    private int linesOfContext;
    private boolean verbose;

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
        this.verbose = false;
        this.searchPattern = null;
        this.linesOfContext = 2;
    }

    // Setters
    public void setContextAsciidoc(String contextAsciidoc) {
        this.contextAsciidoc = contextAsciidoc;
    }

    public void setIncrementalAsciidoc(String incrementalAsciidoc) {
        this.incrementalAsciidoc = incrementalAsciidoc;
    }

    public void setRemoveCopyright(boolean remove) {
        this.removeCopyright = remove;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Configures the engine to run a contextual search with the specified pattern and options.
     * When set, each eligible file will be processed by searching for matches instead of
     * including the full file content.
     *
     * @param pattern        the regular expression pattern to search for
     * @param linesOfContext the number of context lines to include before and after each match
     */
    public void setSearchPattern(String pattern, int linesOfContext) {
        this.searchPattern = pattern;
        this.linesOfContext = linesOfContext;
    }

    public void addInputPath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) {
            return;
        }
        Path p = Paths.get(pathStr).toAbsolutePath().normalize();
        if (verbose) {
            System.out.println("VERBOSE: Adding input path: " + p);
        }
        inputPaths.add(p);
    }

    /**
     * Main execution method. Checks for an existing context file; if found,
     * sets incremental mode, otherwise runs full mode. Opens the respective
     * AsciiDoc file and processes the paths.
     *
     * @throws IOException if reading or writing fails
     */
    public void execute() throws IOException {
        if (engineExecuted) {
            throw new IllegalStateException("This AdocDocumentEngine has already executed. Use a new instance for another run.");
        }
        engineExecuted = true;

        // Check if context.asciidoc exists => set incremental mode.
        Path contextPath = Paths.get(contextAsciidoc).toAbsolutePath();
        if (Files.exists(contextPath)) {
            incrementalMode = true;
            contextFileLastModified = Files.getLastModifiedTime(contextPath).toMillis();
            if (verbose) {
                System.out.println("VERBOSE: Existing context file found; switching to incremental mode.");
            }
        } else {
            if (verbose) {
                System.out.println("VERBOSE: No existing context file; running in full mode.");
            }
        }

        // Open the correct output file.
        if (incrementalMode) {
            System.out.println("Incremental mode: " + incrementalAsciidoc);
            writer.open(incrementalAsciidoc, false);
            writer.write("= Directory Content (Incremental Mode)\n\n");
        } else {
            System.out.println("Full mode: " + contextAsciidoc);
            writer.open(contextAsciidoc, false);
            writer.write("= Directory Content\n\n");
        }

        // Process each path.
        for (Path inputPath : inputPaths) {
            processPath(inputPath);
        }
    }

    /**
     * Prints a summary of processing statistics and any skipped files.
     */
    public void printSummary() {
        writer.write("== Result instructions\n");
        writer.write(
                "File in the result starts with a Markdown-style heading. " +
                        "So, for example, the file decision-log.adoc should " +
                        "start with something like # File: src/main/adoc/decision-log.adoc. " +
                        "After that, the content should stay in AsciiDoc format, " +
                        "including the document title and any other AsciiDoc-specific text.\n\n");
        writer.write("== Summary\n\n");
        writer.write("Lines " + stats.getTotalLines() + ", Tokens " + stats.getTotalTokens() + "\n");

        double tokensPerLine = (stats.getTotalLines() == 0) ? 0.0 : (double) stats.getTotalTokens() / stats.getTotalLines();
        writer.write(String.format("Tokens/Line: %.1f\n", tokensPerLine));

        if (!skippedFiles.isEmpty()) {
            writer.write("\nSkipped Files:\n\n");
            for (String sf : skippedFiles) {
                writer.write(" - " + sf + "\n");
            }
        }
    }

    /**
     * Closes the underlying writer.
     */
    public void close() {
        writer.close();
    }

    // ------------------------------------------------------------------------
    // Internal Methods
    // ------------------------------------------------------------------------

    private void processPath(Path path) {
        AdocContextualSearch contextualSearch = new AdocContextualSearch(searchPattern, linesOfContext);
        try {
            if (!Files.exists(path)) {
                if (verbose) {
                    System.out.println("VERBOSE: Path does not exist, skipping: " + path);
                }
                return;
            }
            if (Files.isDirectory(path)) {
                if (verbose) {
                    System.out.println("VERBOSE: Recursing into directory: " + path);
                }
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        processSingleFile(file, contextualSearch);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        if (verbose) {
                            System.out.println("VERBOSE: Failed to process file: " + file + " (" + exc.getMessage() + ")");
                        }
                        skippedFiles.add(file.toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                processSingleFile(path, contextualSearch);
            }
        } catch (IOException e) {
            if (verbose) {
                System.out.println("VERBOSE: Exception processing path " + path + " (" + e.getMessage() + ")");
            }
            skippedFiles.add(path.toString());
        }
    }

    private void processSingleFile(Path path, AdocContextualSearch contextualSearch) {
        try {
            if (!fileFilter.include(path)) {
                if (verbose) {
                    System.out.println("VERBOSE: Skipping file (filtered out): " + path);
                }
                return;
            }

            if (incrementalMode) {
                long fileLastMod = Files.getLastModifiedTime(path).toMillis();
                if (fileLastMod <= contextFileLastModified) {
                    if (verbose) {
                        System.out.println("VERBOSE: Skipping unmodified file in incremental mode: " + path);
                    }
                    return;
                }
            }

            boolean pathMatches = searchPattern != null && !searchPattern.isEmpty() &&
                    contextualSearch.matches(path);

            List<String> lines = Files.readAllLines(path);
            int firstLine = 1;
            if (removeCopyright) {
                List<String> lines2 = fileProcessor.maybeRemoveCopyright(lines);
                firstLine += lines.size() - lines2.size();
                lines = lines2;
            }

            // Determine which portions of the file to include.
            List<int[]> matches;
            if (pathMatches) {
                // If the filename matches the pattern, include the entire file as one match.
                matches = List.of(new int[]{0, lines.size() - 1});
            } else if (searchPattern != null && !searchPattern.isEmpty()) {
                matches = contextualSearch.searchFile(lines);
                if (matches.isEmpty()) {
                    if (verbose) {
                        System.out.println("VERBOSE: No matches found in file: " + path);
                    }
                    return;
                }
            } else {
                matches = List.of(new int[]{0, lines.size() - 1});
            }
            Path currentPath = Paths.get(".").toAbsolutePath().normalize();
            Path relativePath = currentPath.relativize(path);
            writer.write("== File: " + relativePath + "\n");
            for (int[] match : matches) {
                if (match[0] != 0 || match[1] != lines.size() - 1) {
                    writer.write("\n.lines [" + (match[0] + firstLine) + ", " + (match[1] + firstLine) + "]\n");
                }
                writer.write("....\n");
                for (int i = match[0]; i <= match[1]; i++) {
                    writer.write(lines.get(i) + "\n");
                }
                writer.write("....\n");
            }

            // Summarize new lines, tokens
            long dLines = stats.getDeltaLines();
            long dTokens = stats.getDeltaTokens();

            writer.write(String.format("Lines %d, Tokens %d\n\n", dLines, dTokens));
            if (verbose) {
                System.out.println("VERBOSE: Finished processing file: " + path +
                        " (+" + dLines + " lines, +" + dTokens + " tokens)");
            }
        } catch (IOException e) {
            if (verbose) {
                System.out.println("VERBOSE: Error processing file " + path + " (" + e.getMessage() + ")");
            }
            skippedFiles.add(path.toString());
        }
    }
}
