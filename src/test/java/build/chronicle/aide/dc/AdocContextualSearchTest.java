package build.chronicle.aide.dc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdocContextualSearchTest {

    @Test
    void testFileNameMatchesEntireFileIncluded() throws IOException {
        // Create a file whose name matches the search pattern.
        List<String> content = List.of(
                "public class ServiceExample {",
                "    // implementation details",
                "}"
        );

        // The search pattern "Service" should match the file name.
        AdocContextualSearch search = new AdocContextualSearch("Service", 2);
        assertTrue(search.matches(Path.of("ServiceExample.java")), "Expected file name to match search pattern");

        List<int[]> matches = search.searchFile(Path.of("ServiceExample.java"), content);
        assertEquals(1, matches.size(), "Expected one match because the filename matches the pattern");
        int[] match = matches.get(0);
        assertEquals(0, match[0], "Match should start at line 1");
        assertEquals(2, match[1], "Match should end at line 3");
    }

    @Test
    void testContentMatchesReturnContextLines() throws IOException {
        List<String> content = List.of(
                "public class Example {",
                "   private int count;",         // match here ("count")
                "",
                "   /**",
                "    * Increment the number",
                "    */",
                "   public void increment() {",
                "     count++;",              // match here ("count")
                "   }",
                "}"
        );

        AdocContextualSearch search = new AdocContextualSearch("count", 1);
        assertFalse(search.matches(Path.of("Example.java")), "Expected file name to not match search pattern");

        List<int[]> matches = search.searchFile(Path.of("Example.java"), content);
        assertEquals(2, matches.size(), "Expected two matches in file content");

        int[] firstMatch = matches.get(0);
        assertEquals("[0, 3]", Arrays.toString(firstMatch), "First match should start at line 1 and end at line 4");

        int[] secondMatch = matches.get(1);
        assertEquals("[6, 9]", Arrays.toString(secondMatch), "First match should start at line 7 and end at line 10");

        AdocContextualSearch search2 = new AdocContextualSearch("count", 3);
        List<int[]> matches2 = search2.searchFile(Path.of("Example.java"), content);
        assertEquals(1, matches2.size(), "Expected one match in file content");
        int[] match = matches2.get(0);
        assertEquals("[0, 9]", Arrays.toString(match), "Match should start at line 1 and end at line 9");
    }

    @Test
    void testNoMatchesReturnsEmptyList() throws IOException {
        List<String> content = List.of(
                "public class NoMatch {",
                "    public void doNothing() {}",
                "}"
        );

        AdocContextualSearch search = new AdocContextualSearch("NonExistentPattern", 1);

        List<int[]> matches = search.searchFile(Path.of("NoMatch.java"), content);
        assertTrue(matches.isEmpty(), "Expected no matches when the pattern is not found");
    }
}
