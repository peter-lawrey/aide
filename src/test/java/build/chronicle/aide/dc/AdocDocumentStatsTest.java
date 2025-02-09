package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AdocDocumentStatsTest {

    private AdocDocumentStats stats;

    @BeforeEach
    void setUp() {
        stats = new AdocDocumentStats();
    }

    /**
     * Note: With the current implementation, if a flush occurs on an input string,
     * all newline characters in that string are counted. For example, the string
     * "Line 1\nLine 2\n" will add 2 to the total line count.
     */
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("lineCountScenarios")
    void testUpdateStats_lineCount(String scenario, List<String> inputLines, long expectedTotalLines, long expectedTokensLowerBound) {
        for (String line : inputLines) {
            stats.updateStats(line);
        }
        // Do not flush an extra newline if not needed.
        assertEquals(expectedTotalLines, stats.getTotalLines(), "Total line count mismatch for scenario: " + scenario);
        assertTrue(stats.getTotalTokens() >= expectedTokensLowerBound, "Token count should be at least expected for scenario: " + scenario);
    }

    static Stream<Object[]> lineCountScenarios() {
        return Stream.of(
                // In our implementation, "Line 1\n" and "Line 2\n" are processed in separate flushes:
                // "Line 1\n" adds 1; "Line 2\n" adds 1. However, if the flush call itself
                // results in an extra newline (for example, due to how the data is provided),
                // adjust the expectations accordingly.
                new Object[]{
                        "Two complete lines",
                        List.of("Line 1\n", "Line 2\n"),
                        2L,  // expected total lines
                        2L   // expected tokens lower bound (rough estimate)
                },
                new Object[]{
                        "Mixed content with an empty line",
                        List.of("Line 1\n", "\n", "Line 2\n"),
                        3L,  // expected total lines
                        3L
                },
                new Object[]{
                        "Partial line then newline",
                        List.of("Partial line", "\n"),
                        1L,  // expected total lines
                        1L
                }
        );
    }

    @Test
    void testSnapshotAndDelta() {
        // Process initial content.
        stats.updateStats("Line A\nLine B\n"); // Should count 2 newlines.
        stats.updateStats("\n");               // Adds 1 newline => total = 3.
        stats.snapshotTotals();                // Snapshot at 3 total lines.
        long baseLines = stats.getTotalLines();

        // Process additional content.
        stats.updateStats("Line C\n");         // +1 => total = baseLines+1.
        stats.updateStats("\n");               // +1 => total = baseLines+2.
        stats.updateStats("Line D\n");         // +1 => total = baseLines+3.
        stats.updateStats("\n");               // +1 => total = baseLines+4.

        // Expect final total to be baseLines + 4.
        assertEquals(baseLines + 4, stats.getTotalLines(), "Total line count should reflect additional lines");
        assertEquals(4, stats.getDeltaLines(), "Delta line count should be 4 after additional updates");
        assertTrue(stats.getDeltaTokens() > 0, "Delta tokens should be positive after new input");
    }
}
