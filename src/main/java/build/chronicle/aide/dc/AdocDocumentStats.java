package build.chronicle.aide.dc;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Tracks the number of non-blank lines, blank lines, and GPT-like tokens.
 * <p>
 * Supports partial line accumulation: text added via {@link #updateStats(String)}
 * is appended to an internal buffer. Only when a newline appears does the class
 * process the entire buffer, counting lines and tokens, then clearing the buffer.
 * <p>
 * Also provides snapshot/delta methods so a client can measure changes between
 * checkpoints (e.g., per-file statistics).
 */
public class AdocDocumentStats {

    // ------------------------------------------------------------------------
    // Fields for total counts
    // ------------------------------------------------------------------------
    private long totalLines;
    private long totalBlanks;
    private long totalTokens;

    // ------------------------------------------------------------------------
    // Fields for snapshot/delta calculations
    // ------------------------------------------------------------------------
    private long previousLines;
    private long previousBlanks;
    private long previousTokens;

    // ------------------------------------------------------------------------
    // Buffer + Token Encoder
    // ------------------------------------------------------------------------
    private final StringBuilder lineBuffer = new StringBuilder();

    /**
     * GPT-like token encoder (O200K_BASE).
     * Adjust to your preference or disable if not required.
     */
    private static final Encoding ENCODER = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Appends text to an internal buffer. If the text contains at least one newline,
     * the entire buffer is processed for line- and token-counting, and the buffer is
     * cleared for future calls.
     *
     * @param text the incoming text (may be partial or multiple lines)
     */
    public void updateStats(String text) {
        if (text == null || text.isEmpty()) {
            return; // nothing to do
        }
        lineBuffer.append(text);

        // If the new chunk contains a newline, process the entire buffer
        if (containsNewline(text)) {
            String chunk = lineBuffer.toString();
            processChunk(chunk);
            lineBuffer.setLength(0); // clear
        }
        // If there's no newline, we just wait for the next call that might supply one
    }

    /**
     * Takes a snapshot of the current totals so you can later get deltas
     * (new lines, blanks, tokens) added since the snapshot.
     */
    public void snapshotTotals() {
        previousLines = totalLines;
        previousBlanks = totalBlanks;
        previousTokens = totalTokens;
    }

    /**
     * @return how many lines have been counted so far (non-blank).
     */
    public long getTotalLines() {
        return totalLines;
    }

    /**
     * @return how many blank lines have been counted so far.
     */
    public long getTotalBlanks() {
        return totalBlanks;
    }

    /**
     * @return how many tokens have been counted so far.
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * @return lines added since last snapshot
     */
    public long getDeltaLines() {
        return totalLines - previousLines;
    }

    /**
     * @return blank lines added since last snapshot
     */
    public long getDeltaBlanks() {
        return totalBlanks - previousBlanks;
    }

    /**
     * @return tokens added since last snapshot
     */
    public long getDeltaTokens() {
        return totalTokens - previousTokens;
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    /**
     * Processes a complete chunk containing one or more lines, counting
     * blank vs. non-blank lines and adding to total tokens.
     */
    private void processChunk(String chunk) {
        // First, count lines vs blanks by scanning char by char
        countLinesAndBlanks(chunk);

        // Then count tokens for the entire chunk
        // (If you want line-by-line token counting, split them up and do each line.)
        long chunkTokens = ENCODER.countTokens(chunk);
        totalTokens += chunkTokens;
    }

    /**
     * Scan each character in 'chunk'. Each time we hit a newline,
     * we finalize the line as either blank or non-blank.
     */
    private void countLinesAndBlanks(String chunk) {
        boolean nonBlankLine = false;

        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);

            // Track if any non-whitespace char is found on this line
            if (!Character.isWhitespace(c)) {
                nonBlankLine = true;
            }

            // If we see a newline, finalize the line
            if (c == '\n') {
                if (nonBlankLine) {
                    totalLines++;
                } else {
                    totalBlanks++;
                }
                nonBlankLine = false;
            }
        }
    }

    /**
     * Quick check for whether the input text includes a newline.
     * If so, we'll proceed to parse the entire buffer.
     */
    private boolean containsNewline(String text) {
        return text.indexOf('\n') >= 0;
    }
}
