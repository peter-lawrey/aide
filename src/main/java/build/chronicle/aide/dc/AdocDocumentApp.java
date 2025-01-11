package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A thin CLI wrapper which:
 * <ul>
 *   <li>Reads system properties (<code>context</code>, <code>increment</code>, <code>removeCopyrightMessage</code>)</li>
 *   <li>Accepts zero or more command-line arguments as scan paths (defaults to "." if none)</li>
 *   <li>Determines which ignore file to use (<code>aide.ignore</code> preferred, otherwise <code>.gitignore</code>)</li>
 *   <li>Constructs and configures an {@link AdocDocumentEngine}</li>
 *   <li>Executes the engine to merge AsciiDoc content (full or incremental mode)</li>
 *   <li>Prints a summary and closes resources</li>
 * </ul>
 *
 * <p>This class is the main entry point for scanning directories/files,
 * aggregating them into a single (or incremental) <code>.asciidoc</code> output.</p>
 */
public class AdocDocumentApp {

    /**
     * System property key for the context file name (defaults to "context.asciidoc").
     */
    public static final String PROP_CONTEXT = "context";

    /**
     * System property key for the incremental file name (defaults to "increment.asciidoc").
     */
    public static final String PROP_INCREMENT = "increment";

    /**
     * System property key for whether to remove copyright messages (defaults to "true").
     */
    public static final String PROP_REMOVE_COPYRIGHT = "removeCopyrightMessage";

    /**
     * Main entry point. Orchestrates file scanning, merges AsciiDoc content, and prints a summary.
     *
     * <p>Usage:
     * <pre>
     *   java -Dcontext=myContext.asciidoc \
     *        -Dincrement=myIncrement.asciidoc \
     *        -DremoveCopyrightMessage=false \
     *        -cp aide.jar build.chronicle.aide.dc.AdocDocumentApp [paths...]
     * </pre>
     *
     * @param args Zero or more file/directory paths to scan. If none provided, defaults to ".".
     */
    public static void main(String[] args) {
        // 1. Read system properties (with fallbacks).
        String contextFile = System.getProperty(PROP_CONTEXT, "context.asciidoc");
        String incrementFile = System.getProperty(PROP_INCREMENT, "increment.asciidoc");
        boolean removeCopyright = Boolean.parseBoolean(
                System.getProperty(PROP_REMOVE_COPYRIGHT, "true"));

        // 2. Determine paths to scan (default to ".")
        if (args == null || args.length == 0) {
            args = new String[]{"."};
        }

        // 3. Determine which ignore file to use (prefer aide.ignore in the first argument's directory, else .gitignore)
        Path firstArgDir = Path.of(args[0]).toAbsolutePath();
        if (Files.isRegularFile(firstArgDir)) {
            // If the first argument is a file, use its parent directory to look for ignore files.
            firstArgDir = firstArgDir.getParent() == null ? Path.of(".") : firstArgDir.getParent();
        }
        Path[] ignorePaths = {
                firstArgDir.resolve("aide.ignore"),
                firstArgDir.resolve(".gitignore"),
                Path.of(".", "aide.ignore"),
                Path.of(".", ".gitignore")
        };
        Path ignoreFile = getIgnorePath(ignorePaths);

        // 4. Build the core collaborators: filter, stats, writer, engine.
        AdocFileFilter fileFilter = new AdocFileFilter(ignoreFile);      // Takes .gitignore or aide.ignore
        AdocDocumentStats stats = new AdocDocumentStats();               // Tracks lines/blanks/tokens
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);       // Writes output + updates stats
        AdocDocumentEngine engine = new AdocDocumentEngine(fileFilter, writer, stats);

        // 5. Configure the engine with user-specified or default settings.
        engine.setContextAsciidoc(contextFile);
        engine.setIncrementalAsciidoc(incrementFile);
        engine.setRemoveCopyright(removeCopyright);

        // 6. Add all argument paths to the engine.
        for (String pathStr : args) {
            engine.addInputPath(pathStr);
        }

        // 7. Execute the engine, print a summary, and close.
        try {
            engine.execute();
            engine.printSummary();
        } catch (IOException e) {
            System.err.println("Error running AdocDocumentEngine: " + e.getMessage());
            // Optionally exit with non-zero status in real applications
            // System.exit(1);
        } finally {
            engine.close();
        }
    }

    private static Path getIgnorePath(Path[] ignorePaths) {
        for (Path ignorePath : ignorePaths) {
            if (Files.exists(ignorePath)) {
                System.out.println("loading ignore file: " + ignorePath);
                return ignorePath;
            }
        }
        return null;
    }
}
