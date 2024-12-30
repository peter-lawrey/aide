package build.chronicle.aide.dc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * Handles reading file lines (UTF-8) and optionally removing a leading
 * multi-line or single-line copyright block.
 */
public class AdocFileProcessor {

    /**
     * Reads all lines from a file as UTF-8.
     *
     * @param file the file to read
     * @return the lines
     * @throws IOException if an I/O error occurs
     */
    public List<String> readFileLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /**
     * Removes a block of text if it contains "Copyright " within
     * the first 20 lines. The block is identified by scanning for
     * matching comment markers (like ////) or by removing a single line
     * if it only appears on one line.
     *
     * <p>If "Copyright " is found beyond the 20th line, does nothing.
     *
     * @param lines the original lines
     * @return updated lines with block removed if applicable
     */
    public List<String> maybeRemoveCopyright(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        int index = indexOfCopyright(lines);
        if (index == -1) {
            // none found
            return lines;
        }
        if (index >= 20) {
            // beyond the first 20 lines => ignore
            return lines;
        }

        // Attempt to remove a block if we have recognizable comment markers
        // We'll do a simple approach: if we detect something like //// around it, remove that region.
        // Otherwise, if it appears on a single line, remove just that line.

        // Check lines at or above "index" for a start marker
        int startMarker = -1;
        int endMarker = -1;

        // We'll search upward for a line that starts with "////"
        for (int i = index; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("////")) {
                startMarker = i;
                break;
            }
        }
        // We'll search downward for a line that starts with "////"
        for (int i = index; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("////")) {
                endMarker = i;
                break;
            }
        }

        if (startMarker != -1 && endMarker != -1 && startMarker < endMarker) {
            // remove lines from startMarker..endMarker (inclusive)
            List<String> newLines = new ArrayList<>();
            for (int i = 0; i < startMarker; i++) {
                newLines.add(lines.get(i));
            }
            for (int i = endMarker + 1; i < lines.size(); i++) {
                newLines.add(lines.get(i));
            }
            return newLines;
        } else {
            // remove just the line with the "Copyright "
            List<String> newLines = new ArrayList<>(lines);
            newLines.remove(index);
            return newLines;
        }
    }

    private int indexOfCopyright(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Copyright ")) {
                return i;
            }
        }
        return -1;
    }
}
