package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Main entry point for the command-line application.
 *
 * <p>Reads system properties for:
 * <ul>
 *   <li><b>context</b>: name of the full context file (default "context.asciidoc")</li>
 *   <li><b>increment</b>: name of the incremental file (default "increment.asciidoc")</li>
 *   <li><b>removeCopyrightMessage</b>: whether to remove copyright blocks (default "true")</li>
 * </ul>
 *
 * <p>Accepts command-line arguments for directories or files to scan. If none are provided,
 * scans the current directory.
 *
 * <p>After scanning and processing, prints a final summary.
 */
public class AdocDocumentApp {

    /**
     * CLI entry point that configures and runs the document engine.
     *
     * @param args paths to scan (if empty, uses current directory)
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {

        // Read system properties
        String contextFile = System.getProperty("context", "context.asciidoc");
        String incrementFile = System.getProperty("increment", "increment.asciidoc");
        boolean removeCopyright = Boolean.parseBoolean(
                System.getProperty("removeCopyrightMessage", "true"));

        // Create filter, stats, writer, and engine
        Path gitignorePath = Path.of(args.length > 0 ? args[0] : ".", ".gitignore");
        AdocFileFilter fileFilter = new AdocFileFilter(gitignorePath);
        AdocDocumentStats stats = new AdocDocumentStats();
        AdocDocumentWriter writer = new AdocDocumentWriter(stats);
        AdocDocumentEngine engine = new AdocDocumentEngine(fileFilter, writer, stats);

        // Configure engine
        engine.setContextAsciidoc(contextFile);
        engine.setIncrementalAsciidoc(incrementFile);
        engine.setRemoveCopyright(removeCopyright);

        // If no args provided, use "."
        if (args.length == 0) {
            engine.addInputPath(".");
        } else {
            for (String path : args) {
                engine.addInputPath(path);
            }
        }

        // Run
        engine.execute();
        engine.printSummary();
        engine.close();
    }
}
