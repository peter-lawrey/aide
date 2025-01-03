/**
 * This package provides core functionality for scanning directories/files,
 * filtering them based on specified rules, and merging content into AsciiDoc files.
 *
 * <p>Key classes include:</p>
 * <ul>
 *   <li>{@link build.chronicle.aide.dc.AdocDocumentEngine} - Orchestrates full vs. incremental mode.</li>
 *   <li>{@link build.chronicle.aide.dc.AdocDocumentApp} - CLI entry point for scanning and merging AsciiDoc.</li>
 *   <li>{@link build.chronicle.aide.dc.AdocDocumentStats} - Tracks lines, blanks, and GPT-like tokens.</li>
 *   <li>{@link build.chronicle.aide.dc.AdocDocumentWriter} - Writes scanned content, updating statistics.</li>
 *   <li>{@link build.chronicle.aide.dc.AdocFileFilter} - Applies .gitignore/aide.ignore filters and skip logic.</li>
 *   <li>{@link build.chronicle.aide.dc.AdocFileProcessor} - Reads files (UTF-8) and optionally removes copyright blocks.</li>
 * </ul>
 */
package build.chronicle.aide.dc;
