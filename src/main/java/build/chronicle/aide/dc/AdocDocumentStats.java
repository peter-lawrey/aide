package build.chronicle.aide.dc;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.regex.Pattern;

/**
 * Maintains counters for lines, blank lines, total tokens, and supports
 * per-file deltas.
 *
 * <p>Calculates line counts, blank lines, and a naive token count based on
 * splitting text on whitespace. Also maintains snapshots to report
 * deltas for each file processed.
 */
public class AdocDocumentStats {

    /**
     * Default GPT-like encoding for token counting.
     */
    private static final Encoding DEFAULT_ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    private long totalLines;
    private long totalBlanks;
    private long totalTokens;

    private long previousLines;
    private long previousBlanks;
    private long previousTokens;

    /**
     * Updates the stats (lines, blanks, tokens) based on a given line block.
     * Assumes at most one line is written at a time.
     *
     * @param line the input line
     */
    public void updateStats(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }

        if (line.trim().isEmpty()) {
            totalBlanks++;
        } else {
            totalLines++;
        }

        totalTokens += DEFAULT_ENCODING.countTokens(line);
    }

    /**
     * Captures a snapshot of the current totals, used to compute per-file deltas.
     */
    public void snapshotTotals() {
        previousLines = totalLines;
        previousBlanks = totalBlanks;
        previousTokens = totalTokens;
    }

    /**
     * @return the difference in lines between the current total and the last snapshot
     */
    public long getDeltaLines() {
        return totalLines - previousLines;
    }

    /**
     * @return the difference in blank lines between the current total and the last snapshot
     */
    public long getDeltaBlanks() {
        return totalBlanks - previousBlanks;
    }

    /**
     * @return the difference in tokens between the current total and the last snapshot
     */
    public long getDeltaTokens() {
        return totalTokens - previousTokens;
    }

    /**
     * @return total counted non-blank lines so far
     */
    public long getTotalLines() {
        return totalLines;
    }

    /**
     * @return total counted blank lines so far
     */
    public long getTotalBlanks() {
        return totalBlanks;
    }

    /**
     * @return total counted tokens so far
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
}
