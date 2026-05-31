package io.phoenixfire.chaos;

/**
 * Metadata attached to generated tests for downstream verification of Phoenixfire reports.
 */
public record ExpectedOutcome(
        String testId,
        String behavior,
        String expectedFailureMode,
        String expectedRecovery,
        String expectedFinalState) {
}
