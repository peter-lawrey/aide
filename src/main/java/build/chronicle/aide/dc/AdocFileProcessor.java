package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles reading file lines in UTF-8 and optionally removing a multi-line or
 * single-line copyright comment block if it appears within the first 20 lines.
 */
public class AdocFileProcessor {

    /**
     * Reads all lines of a file in UTF-8 encoding.
     *
     * @param file the path to the file
     * @return list of lines
     * @throws IOException if an I/O error occurs
     */
    public List<String> readFileLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /**
     * Scans the first 20 lines for a recognized comment style containing "Copyright"
     * and removes that block. If none is found, returns the original list.
     */
    public List<String> maybeRemoveCopyright(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }

        // We only inspect the first 20 lines.
        int searchLimit = Math.min(lines.size(), 20);

        for (int startIdx = 0; startIdx < searchLimit; startIdx++) {
            final String line = lines.get(startIdx);

            // 1. AsciiDoc comment: "////"
            if (line.trim().startsWith("////")) {
                List<String> removedBlock = removeAsciiDocBlock(lines, startIdx);
                if (removedBlock != null) return removedBlock;
            }

            // 2. Java block comment: "/* ... */"
            if (line.trim().startsWith("/*")) {
                List<String> removedBlock = removeJavaBlock(lines, startIdx);
                if (removedBlock != null) return removedBlock;
            }

            // 3. Forward slash line comments: lines starting with "//"
            if (line.trim().startsWith("//")) {
                List<String> removedBlock = removeForwardSlashBlock(lines, startIdx);
                if (removedBlock != null) return removedBlock;
            }

            // 4. Shell script style: lines starting with "#" (but not "#!")
            // We'll remove consecutive "#" lines if they include "Copyright".
            if (line.trim().startsWith("#") && !line.trim().startsWith("#!")) {
                List<String> removedBlock = removeShellBlock(lines, startIdx);
                if (removedBlock != null) return removedBlock;
            }
        }

        // If no recognized block found or none included "Copyright", return original.
        return lines;
    }

    /**
     * Removes an AsciiDoc block from "////" up to the next "////" (inclusive) if
     * any line within that block contains "Copyright".
     */
    private List<String> removeAsciiDocBlock(List<String> lines, int startIdx) {
        int endIdx = findNextDelimiter(lines, startIdx + 1, "////");
        if (endIdx < 0) {
            // No closing "////" found, cannot remove the block
            return null;
        }
        // Check if there's "Copyright" in the block
        if (!containsCopyright(lines, startIdx, endIdx)) {
            return null;
        }
        return removeRange(lines, startIdx, endIdx);
    }

    private List<String> removeJavaBlock(List<String> lines, int startIdx) {
        int endIdx = findClosingJavaComment(lines, startIdx + 1);
        if (endIdx < 0) {
            // No closing "*/" found
            return null;
        }
        // Check for "Copyright"
        if (!containsCopyright(lines, startIdx, endIdx)) {
            return null;
        }
        return removeRange(lines, startIdx, endIdx);
    }

    /**
     * Removes consecutive forward-slash comment lines if any contain "Copyright".
     * Stops when a line is encountered that does not start with "//" or we pass
     * the search limit.
     */
    private List<String> removeForwardSlashBlock(List<String> lines, int startIdx) {
        int endIdx = startIdx;
        while (endIdx < lines.size() && lines.get(endIdx).trim().startsWith("//")) {
            endIdx++;
        }
        // endIdx is the first line not starting with "//" after startIdx
        int lastCommentLine = endIdx - 1;
        if (!containsCopyright(lines, startIdx, lastCommentLine)) {
            return null;
        }
        return removeRange(lines, startIdx, lastCommentLine);
    }

    /**
     * Removes consecutive "#" lines (excluding a shebang "#!") if any contain "Copyright".
     * Stops when it reaches a line that doesn't start with "#" or is "#!".
     */
    private List<String> removeShellBlock(List<String> lines, int startIdx) {
        int endIdx = startIdx;
        while (endIdx < lines.size()) {
            String ln = lines.get(endIdx).trim();
            if (ln.startsWith("#!")) {
                // Do not remove the shebang line; treat it like a stopper.
                break;
            }
            if (!ln.startsWith("#")) {
                break;
            }
            endIdx++;
        }
        int lastCommentLine = endIdx - 1;
        if (lastCommentLine < startIdx) {
            return null;
        }
        if (!containsCopyright(lines, startIdx, lastCommentLine)) {
            return null;
        }
        return removeRange(lines, startIdx, lastCommentLine);
    }

    // ----------------------------------------------------------
    //   Helper methods
    // ----------------------------------------------------------

    /**
     * Looks for a closing "////" starting from startLine. Returns index or -1 if none found.
     */
    private int findNextDelimiter(List<String> lines, int startLine, String delimiter) {
        for (int i = startLine; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(delimiter)) {
                return i;
            }
        }
        return -1;
    }

    private int findClosingJavaComment(List<String> lines, int startLine) {
        for (int i = startLine; i < lines.size(); i++) {
            String ln = lines.get(i).trim();
            if (ln.endsWith("*/")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks whether lines in [startIdx..endIdx] contain "Copyright".
     */
    private boolean containsCopyright(List<String> lines, int startIdx, int endIdx) {
        for (int i = startIdx; i <= endIdx && i < lines.size(); i++) {
            String ln = lines.get(i).toLowerCase();
            if (ln.contains("copyright")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a new list with lines in the range [start..end] removed.
     */
    private List<String> removeRange(List<String> lines, int start, int end) {
        List<String> newList = new ArrayList<>();
        // Add everything before start
        for (int i = 0; i < start; i++) {
            newList.add(lines.get(i));
        }
        // Add everything after end
        for (int i = end + 1; i < lines.size(); i++) {
            newList.add(lines.get(i));
        }
        return newList;
    }
}
