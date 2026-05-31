package io.phoenixfire.chaos.generator;

import java.util.List;

public record GeneratedClass(String classId, String className, List<GeneratedTest> tests) {

    public boolean requiresOrdering() {
        return tests.stream().anyMatch(GeneratedTest::ordered);
    }
}
