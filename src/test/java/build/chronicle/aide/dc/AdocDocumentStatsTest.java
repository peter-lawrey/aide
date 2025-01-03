package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the line, blank, and token counting in AdocDocumentStats,
 * including scenarios with partial lines vs. fully assembled lines.
 */
class AdocDocumentStatsTest {

    private AdocDocumentStats stats;

    @BeforeEach
    void setUp() {
        stats = new AdocDocumentStats();
    }

    /**
     * A parameterized test covering various scenarios of complete lines
     * (each string representing a full line, typically ending in '\n').
     */
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("lineBlankTokenScenarios")
    void testUpdateStats_lineBlankToken(
            String scenario,
            List<String> inputLines,
            long expectedNonBlank,
            long expectedBlanks,
            long expectedTokens
    ) {
        for (String line : inputLines) {
            stats.updateStats(line);
        }

        assertEquals(expectedNonBlank, stats.getTotalLines(),
                () -> "Scenario: " + scenario + " => Wrong non-blank line count");
        assertEquals(expectedBlanks, stats.getTotalBlanks(),
                () -> "Scenario: " + scenario + " => Wrong blank line count");
        assertEquals(expectedTokens, stats.getTotalTokens(),
                () -> "Scenario: " + scenario + " => Wrong total token count");
    }

    /**
     * Supplies scenarios of completed lines (including trailing '\n')
     * with expected line/blank/token counts.
     */
    static Stream<Object[]> lineBlankTokenScenarios() {
        return Stream.of(
                new Object[]{
                        "All non-blank lines",
                        List.of("Line 1\n", "Line 2\n", "Line 3\n"),
                        3L,   // expected non-blank lines
                        0L,   // expected blanks
                        12L   // expected tokens (example count, depends on GPT-like encoder)
                },
                new Object[]{
                        "Mixed blank and non-blank",
                        List.of("Line 1\n", "\n", "Line 2\n", "  \n", "\t\n", "Line 3\n"),
                        3L,
                        3L,   // blank lines
                        15L   // total tokens
                },
                new Object[]{
                        "All blank lines",
                        List.of("\n", "  \n", "\t \n", "\n"),
                        0L,
                        4L,
                        4L
                },
                new Object[]{
                        "Demonstrates how partial lines ",
                        List.of("Part", "ial ", "lin", "e\n"),
                        1L,
                        0L,
                        3L
                },
                new Object[]{
                        "Demonstrates how one line is the same ",
                        List.of("Partial line\n"),
                        1L,
                        0L,
                        3L
                }
        );
    }

    /**
     * Verifies that snapshotTotals() correctly captures baseline counts
     * and getDelta*() produces valid differences for lines processed after the snapshot.
     */
    @Test
    void testSnapshotDeltas() {
        // Start with some lines
        stats.updateStats("Line A\n"); // non-blank
        stats.updateStats("  \n");     // blank
        stats.updateStats("Line B\n"); // non-blank

        // So far: 2 non-blank lines, 1 blank line
        stats.snapshotTotals();

        // Add more lines
        stats.updateStats("Line C\n");
        stats.updateStats("\n");  // blank
        stats.updateStats("Line D\n");

        // We should see deltas for 2 new non-blank lines + 1 new blank
        assertEquals(2, stats.getDeltaLines(),
                "Delta lines should be 2 after snapshot");
        assertEquals(1, stats.getDeltaBlanks(),
                "Delta blanks should be 1 after snapshot");
        assertTrue(stats.getDeltaTokens() > 0,
                "Delta tokens should be > 0 for new lines after snapshot");

        // Summaries
        assertEquals(4, stats.getTotalLines(), "Total 4 non-blank lines so far");
        assertEquals(2, stats.getTotalBlanks(), "Total 2 blank lines so far");
        assertTrue(stats.getTotalTokens() > 0, "Total tokens should be > 0 overall");
    }
}
