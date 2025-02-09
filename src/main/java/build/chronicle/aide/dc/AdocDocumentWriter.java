package build.chronicle.aide.dc;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Writes text to the current output file and updates statistics accordingly.
 *
 * <p>This writer is used by the engine to output the consolidated, chat-optimized documentation.
 * It delegates text statistics tracking to an instance of {@link AdocDocumentStats}.</p>
 */
public class AdocDocumentWriter {

    private final AdocDocumentStats stats;
    private PrintWriter currentWriter;

    /**
     * Constructs a writer with a reference to the stats for counting lines and tokens.
     *
     * @param stats the statistics instance to update
     */
    public AdocDocumentWriter(AdocDocumentStats stats) {
        this.stats = stats;
    }

    /**
     * Opens or creates the output file for writing.
     *
     * @param outputFile the path to the output file
     * @param append     if true, appends to the file; if false, overwrites any existing file
     * @throws IOException if an I/O error occurs while opening the file
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
     * Writes the given text to the currently open file and updates the statistics.
     *
     * @param text the text to write
     * @throws IllegalStateException if no file is open for writing
     */
    public void write(String text) {
        if (currentWriter == null) {
            throw new IllegalStateException("No file is open for writing.");
        }
        currentWriter.print(text);
        stats.updateStats(text);
    }

    /**
     * Captures a snapshot of the current statistics.
     */
    public void snapshotStats() {
        stats.snapshotTotals();
    }

    /**
     * Closes the current writer if open.
     */
    public void close() {
        if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
        }
    }
}
