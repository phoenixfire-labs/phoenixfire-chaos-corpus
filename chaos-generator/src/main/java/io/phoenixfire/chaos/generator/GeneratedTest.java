package io.phoenixfire.chaos.generator;

import io.phoenixfire.chaos.ExpectedOutcome;

public record GeneratedTest(
        String classId,
        int methodIndex,
        String testId,
        int order,
        boolean ordered,
        String javaBody,
        ExpectedOutcome expectedOutcome) {

    public String methodName() {
        return "test" + String.format("%02d", methodIndex);
    }
}
