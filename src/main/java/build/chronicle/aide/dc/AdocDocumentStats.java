package build.chronicle.aide.dc;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Tracks the total number of lines and GPT-like tokens.
 *
 * <p>This class supports partial line accumulation: text added via {@link #updateStats(String)}
 * is appended to an internal buffer. When a newline is encountered anywhere in the buffer,
 * the entire buffer is processed and all newline characters are counted—each newline increments
 * the total line counter, regardless of whether the line is blank or not. In addition, the total
 * token count is updated for the processed chunk.</p>
 *
 * <p>The class also provides snapshot/delta functionality so that clients can measure changes
 * (for example, per-file statistics) relative to a snapshot.</p>
 */
public class AdocDocumentStats {

    private static final Encoding ENCODER =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    private final StringBuilder lineBuffer = new StringBuilder();

    private long totalLines;
    private long totalTokens;

    // Snapshot fields.
    private long previousLines;
    private long previousTokens;

    /**
     * Appends text to the internal buffer. If the text contains at least one newline,
     * the entire buffer is processed and flushed. Every newline character in the flushed text
     * increments the total line counter.
     *
     * @param text the incoming text (may be partial or multiple lines)
     */
    public void updateStats(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        lineBuffer.append(text);
        if (containsNewline(text)) {
            String chunk = lineBuffer.toString();
            processChunk(chunk);
            lineBuffer.setLength(0);
        }
    }

    /**
     * Takes a snapshot of the current total line and token counts.
     */
    public void snapshotTotals() {
        previousLines = totalLines;
        previousTokens = totalTokens;
    }

    /**
     * @return the total number of lines counted so far.
     */
    public long getTotalLines() {
        return totalLines;
    }

    /**
     * @return the total number of tokens counted so far.
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * @return the number of new lines added since the last snapshot.
     */
    public long getDeltaLines() {
        return totalLines - previousLines;
    }

    /**
     * @return the number of new tokens added since the last snapshot.
     */
    public long getDeltaTokens() {
        return totalTokens - previousTokens;
    }

    /**
     * Processes a complete chunk of text by counting newlines and tokens.
     *
     * @param chunk the text chunk (may contain multiple lines)
     */
    private void processChunk(String chunk) {
        countTotalLines(chunk);
        long chunkTokens = ENCODER.countTokens(chunk);
        totalTokens += chunkTokens;
    }

    /**
     * Increments the total line counter for each newline character in the given chunk.
     *
     * @param chunk the text chunk to process
     */
    private void countTotalLines(String chunk) {
        for (int i = 0; i < chunk.length(); i++) {
            if (chunk.charAt(i) == '\n') {
                totalLines++;
            }
        }
    }

    /**
     * Checks if the provided text contains a newline character.
     *
     * @param text the text to check
     * @return true if the text contains a newline, false otherwise
     */
    private boolean containsNewline(String text) {
        return text.indexOf('\n') >= 0;
    }
}
