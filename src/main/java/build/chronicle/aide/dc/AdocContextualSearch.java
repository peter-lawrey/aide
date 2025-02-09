package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides functionality for contextual search within a single file.
 *
 * <p>This class does not perform directory traversal; the caller (e.g. the document engine)
 * is responsible for finding files to search. When invoked on a file, the search operation
 * first checks if the file name itself matches the search pattern. If so, the entire file
 * content is returned as a match. Otherwise, the file is processed line‐by‐line, and any
 * lines that match the pattern are returned with a configurable number of context lines
 * before and after the match.</p>
 */
public class AdocContextualSearch {
    private final Pattern searchPattern;
    private final int linesOfContext;

    /**
     * Constructs a new contextual search instance.
     *
     * @param pattern        the search pattern (regular expression)
     * @param linesOfContext the number of context lines to include before and after each match
     */
    public AdocContextualSearch(String pattern, int linesOfContext) {
        this.searchPattern = pattern == null ? null :
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        this.linesOfContext = linesOfContext;
    }

    private static boolean isNonTrivial(List<String> lines, int index) {
        return lines.get(index).trim().length() > 1;
    }

    /**
     * Does the path match the search pattern?
     *
     * @param path the path to check
     * @return true if the path matches the search pattern
     */
    public boolean matches(Path path) {
        return searchPattern == null ||
                searchPattern.matcher(path.toString()).find();
    }

    /**
     * Searches the specified file for the search pattern.
     *
     * <p>If the file name itself matches the search pattern, the entire file content is returned as a single match.
     * Otherwise, the file is searched line-by-line and each matching line is returned along with the configured number
     * of context lines before and after the match.</p>
     *
     * @param lines the lines of the file to search
     * @return a list of matches found in the file, 0 indexed
     */
    public List<int[]> searchFile(List<String> lines) {
        List<int[]> matches = new ArrayList<>();
        int index = 0;
        int prevStart = -1, prevEnd = -1;
        for (; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher matcher = searchPattern.matcher(line);
            if (matcher.find()) {
                int start = findStart(lines, index);
                int end = findEnd(lines, index);
                if (prevStart == -1) {
                    prevStart = start;
                } else if (start > prevEnd) {
                    matches.add(new int[]{prevStart, prevEnd});
                    prevStart = start;
                }
                prevEnd = end;
            }
        }
        if (prevStart != -1) {
            matches.add(new int[]{prevStart, prevEnd});
        }
        return matches;
    }

    private int findStart(List<String> lines, int index) {
        int nonTrivialLines = linesOfContext;
        do {
            index--;
            if (index < 0) {
                return 0;
            }
            if (isNonTrivial(lines, index)) {
                nonTrivialLines--;
            }
        } while (nonTrivialLines > 0);
        return index;
    }

    private int findEnd(List<String> lines, int index) {
        int nonTrivialLines = linesOfContext;
        do {
            index++;
            if (index >= lines.size()) {
                return lines.size() - 1;
            }
            if (isNonTrivial(lines, index)) {
                nonTrivialLines--;
            }
        } while (nonTrivialLines > 0);
        return index;
    }

}
