package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdocDocumentStatsTest {

    private AdocDocumentStats stats;

    @BeforeEach
    void setUp() {
        stats = new AdocDocumentStats();
    }

    @Test
    void testUpdateStats_simpleLinesAndBlanks() {
        stats.updateStats("Line 1\n");
        stats.updateStats("\n");
        stats.updateStats("Line 2\n");
        stats.updateStats("line 3\n");

        assertEquals(3, stats.getTotalLines(), "Should count 3 non-blank lines");
        assertEquals(1, stats.getTotalBlanks(), "Should count 1 blank line");

        long tokens = stats.getTotalTokens();
        assertTrue(tokens > 0, "Token count should be > 0 for text with words");
    }

    @Test
    void testSnapshotDeltas() {
        stats.updateStats("Line 1\n");
        stats.updateStats("Line 2\n");
        stats.updateStats("Line 3\n");
        stats.snapshotTotals();

        // Add more lines
        stats.updateStats("Line A\n");
        stats.updateStats("Line B\n");
        // Check deltas
        assertEquals(2, stats.getDeltaLines(), "Should show 2 lines in this delta");
    }

    @Test
    void testEmptyText() {
        stats.updateStats("");
        assertEquals(0, stats.getTotalLines(), "No lines in empty text");
        assertEquals(0, stats.getTotalBlanks(), "No blank lines in empty text");
        assertEquals(0, stats.getTotalTokens(), "No tokens in empty text");
    }
}
