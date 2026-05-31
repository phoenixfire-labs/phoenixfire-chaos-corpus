package io.phoenixfire.chaos.generator;

import io.phoenixfire.chaos.ExpectedOutcome;

import java.util.ArrayList;
import java.util.List;

public record GenerationResult(
        TierName tier,
        long seed,
        int classCount,
        int testsPerClass,
        List<GeneratedClass> classes,
        List<ExpectedOutcome> outcomes) {

    public int totalTests() {
        return outcomes.size();
    }

    public static GenerationResult empty(TierName tier, long seed, int classCount, int testsPerClass) {
        return new GenerationResult(tier, seed, classCount, testsPerClass, List.of(), List.of());
    }

    public GenerationResult withClasses(List<GeneratedClass> classes) {
        List<ExpectedOutcome> outcomes = new ArrayList<>();
        for (GeneratedClass clazz : classes) {
            for (GeneratedTest test : clazz.tests()) {
                outcomes.add(test.expectedOutcome());
            }
        }
        return new GenerationResult(tier, seed, classCount, testsPerClass, classes, List.copyOf(outcomes));
    }
}
