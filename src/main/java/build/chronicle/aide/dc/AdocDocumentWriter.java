package build.chronicle.aide.dc;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Writes text to the current output file (either the context or incremental)
 * and updates statistics accordingly.
 */
public class AdocDocumentWriter {

    private final AdocDocumentStats stats;
    private PrintWriter currentWriter;

    /**
     * Constructor with a reference to the stats for counting lines/blanks/tokens.
     *
     * @param stats the stats object
     */
    public AdocDocumentWriter(AdocDocumentStats stats) {
        this.stats = stats;
    }

    /**
     * Opens or creates a file for writing, optionally in append mode.
     *
     * @param outputFile the path to the output file
     * @param append     true if appending, false to overwrite
     * @throws IOException if an I/O error occurs
     */
    public void open(String outputFile, boolean append) throws IOException {
        if (currentWriter != null) {
            // close any existing writer first
            close();
        }
        FileWriter fw = new FileWriter(outputFile, append);
        currentWriter = new PrintWriter(fw, true);
    }

    /**
     * Writes text to the open file, updating stats.
     *
     * @param text the text to write
     */
    public void write(String text) {
        if (currentWriter == null) {
            throw new IllegalStateException("No file is open for writing.");
        }
        currentWriter.print(text);
        // Also update stats
        stats.updateStats(text);
    }

    /**
     * Captures a snapshot of stats for delta calculations.
     */
    public void snapshotStats() {
        stats.snapshotTotals();
    }

    /**
     * Closes the writer if open.
     */
    public void close() {
        if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
        }
    }
}
