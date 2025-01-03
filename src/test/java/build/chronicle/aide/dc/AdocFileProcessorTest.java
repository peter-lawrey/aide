package build.chronicle.aide.dc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdocFileProcessorTest {

    private AdocFileProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AdocFileProcessor();
    }

    /**
     * Parameterized test that covers various scenarios for copyright removal.
     * Each scenario includes:
     * - A description
     * - A list of input lines
     * - The expected list of lines after removing the copyright block
     */
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("copyrightScenarios")
    void testMaybeRemoveCopyright(String scenario, List<String> input, List<String> expected) {
        List<String> result = processor.maybeRemoveCopyright(input);
        assertEquals(expected, result, () -> "Failed scenario: " + scenario);
    }

    /**
     * Supplies scenarios for the parameterized test.
     */
    static Stream<Object[]> copyrightScenarios() {
        return Stream.of(
                new Object[]{
                        "No copyright found",
                        List.of("Line 1", "Line 2"),
                        List.of("Line 1", "Line 2")
                },
                new Object[]{
                        "AsciiDoc block comment within first 20 lines",
                        List.of("= Actual Title",
                                "////",
                                "Copyright (c) 2025 My Company",
                                "More copyright information",
                                "////",
                                "== First Header"),
                        // Expect to remove lines 0..3 and keep only "= Actual Title"
                        List.of("= Actual Title",
                                "== First Header")
                },
                new Object[]{
                        "Copyright beyond 20 lines",
                        List.of(
                                "Line 0", "Line 1", "Line 2", "Line 3", "Line 4",
                                "Line 5", "Line 6", "Line 7", "Line 8", "Line 9",
                                "Line 10", "Line 11", "Line 12", "Line 13", "Line 14",
                                "Line 15", "Line 16", "Line 17", "Line 18", "Line 19",
                                "Line 20", "Copyright 2024"
                        ),
                        // Should remain unchanged
                        List.of(
                                "Line 0", "Line 1", "Line 2", "Line 3", "Line 4",
                                "Line 5", "Line 6", "Line 7", "Line 8", "Line 9",
                                "Line 10", "Line 11", "Line 12", "Line 13", "Line 14",
                                "Line 15", "Line 16", "Line 17", "Line 18", "Line 19",
                                "Line 20", "Copyright 2024"
                        )
                },
                new Object[]{
                        "Java block comment style 1",
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "/*",
                                " Copyright (c) 2025 Java Company",
                                " Licensed under the ...",
                                "*/",
                                "public class MyClass {}"
                        ),
                        // Suppose the processor removes lines 0..3 and keeps "public class MyClass {}"
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "public class MyClass {}")
                },
                new Object[]{
                        "Java block comment style 2",
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "/*",
                                " * one line comment",
                                " * second line comment",
                                " * Copyright (c) 2025 Java Company",
                                " * Licensed under the ...",
                                "*/",
                                "public class MyClass {}"
                        ),
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "public class MyClass {}")
                },
                new Object[]{
                        "Forward slash comment lines",
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "// not the first line",
                                "// Copyright (c) 2025 My Java Project",
                                "// Some other text",
                                "public class MyClass {}"
                        ),
                        List.of(
                                "package build.chronicle.aide.dc;",
                                "public class MyClass {}"
                        )
                },
                new Object[]{
                        "Shell script style comments",
                        List.of(
                                "#!/usr/bin/env bash",
                                "",
                                "# first line comment",
                                "# Copyright (c) 2025 My Shell Tools",
                                "# Some other shell comment",
                                "echo \"Hello World\""
                        ),
                        List.of(
                                "#!/usr/bin/env bash",
                                "",
                                "echo \"Hello World\""
                        )
                }
        );
    }
}
