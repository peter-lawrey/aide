package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AdocDocumentApp is the entry point for generating a consolidated,
 * chat‑optimized AsciiDoc file containing the project context.
 *
 * <p>This application always runs in chat mode. It determines the ignore file
 * by checking for an "aide.ignore" file (falling back to ".gitignore" if needed)
 * and then scans the provided paths (or defaults to the current directory) to
 * generate a new context file (by default, "context.asciidoc") in overwrite mode.
 * If the -Dverbose flag is set, additional log output is produced.</p>
 */
public class AdocDocumentApp {

    public static final String PROP_CONTEXT = "context";
    public static final String PROP_INCREMENT = "increment";
    public static final String PROP_REMOVE_COPYRIGHT = "disableRemoveCopyrightMessage";
    public static final String PROP_SEARCH_PATTERN = "searchPattern";

    /**
     * Main entry point for the AsciiDoc document generation application.
     *
     * @param args the command line arguments
     * @throws IOException if an I/O error occurs
     */
    public static void main(String... args) throws IOException {
        boolean verbose = getBooleanProperty("verbose");
        String contextFile = System.getProperty(PROP_CONTEXT, "context.asciidoc");
        if (verbose) {
            System.out.println("VERBOSE: Context file: " + contextFile);
        }
        String incrementFile = System.getProperty(PROP_INCREMENT, "increment.asciidoc");
        if (verbose) {
            System.out.println("VERBOSE: Increment file: " + incrementFile);
        }
        boolean disableRemoveCopyright = getBooleanProperty(PROP_REMOVE_COPYRIGHT);
        if (verbose) {
            System.out.println("VERBOSE: Disable remove copyright: " + disableRemoveCopyright);
        }

        // Optional search pattern.
        String searchPattern = System.getProperty(PROP_SEARCH_PATTERN, "").trim();
        if (verbose) {
            System.out.println("VERBOSE: Search pattern: " + (searchPattern.isEmpty() ? "none" : searchPattern));
        }

        // If no arguments are provided, default to current directory.
        if (args == null || args.length == 0) {
            args = new String[]{"."};
        }

        // Determine which ignore file to use:
        // Prefer aide.ignore in the first argument's directory; fall back to .gitignore.
        Path firstArgDir = Path.of(args[0]).toAbsolutePath();
        if (Files.isRegularFile(firstArgDir)) {
            firstArgDir = firstArgDir.getParent() == null ? Path.of(".") : firstArgDir.getParent();
        }
        Path[] ignorePaths = {
                firstArgDir.resolve("aide.ignore"),
                firstArgDir.resolve(".gitignore"),
                Path.of(".", "aide.ignore"),
                Path.of(".", ".gitignore")
        };
        Path ignoreFile = getIgnorePath(ignorePaths);

        if (verbose) {
            System.out.println("VERBOSE: Selected ignore file: " + (ignoreFile != null ? ignoreFile : "none"));
        }

        // Build core collaborators.
        long maxSizeBytes = Integer.getInteger("maxSize", 128) * 1024;
        if (verbose) {
            System.out.println("VERBOSE: Max file size: " + (maxSizeBytes / 1024) + " KiB");
        }

        AdocFileFilter fileFilter = new AdocFileFilter(ignoreFile, maxSizeBytes, verbose);
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);
        AdocDocumentEngine engine = new AdocDocumentEngine(fileFilter, writer, stats);

        // Set verbose flag in the engine based on system property.
        engine.setVerbose(verbose);

        // Always run in chat mode: set the context file (no incremental mode).
        engine.setContextAsciidoc(contextFile);
        engine.setIncrementalAsciidoc(incrementFile);
        engine.setRemoveCopyright(!disableRemoveCopyright);

        // Configure the engine with a search pattern if provided.
        if (!searchPattern.isEmpty()) {
            int linesOfContext = Integer.getInteger("linesOfContext", 2);
            engine.setSearchPattern(searchPattern, linesOfContext);
        }

        for (String pathStr : args) {
            engine.addInputPath(pathStr);
        }

        try {
            engine.execute();
            engine.printSummary();
        } finally {
            engine.close();
        }
    }

    private static boolean isVerbose() {
        return getBooleanProperty("verbose");
    }

    private static boolean getBooleanProperty(String property) {
        boolean hasVerbose = System.getProperties().containsKey(property);
        // -Dproperty is equivalent to -Dproperty=true
        return hasVerbose && !"false".equalsIgnoreCase(System.getProperty(property));
    }

    private static Path getIgnorePath(Path[] ignorePaths) {
        for (Path ignorePath : ignorePaths) {
            if (Files.exists(ignorePath)) {
                System.out.println("Using ignore file: " + ignorePath);
                return ignorePath;
            }
        }
        return null;
    }
}
