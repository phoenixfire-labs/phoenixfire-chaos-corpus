package io.phoenixfire.chaos.generator;

import io.phoenixfire.chaos.ExpectedOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ManifestWriter {

    private ManifestWriter() {
    }

    static void writeManifest(Path manifestFile, GenerationResult result) throws IOException {
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, renderJson(result), StandardCharsets.UTF_8);
    }

    static void writeOutcomesResource(Path resourceFile, List<ExpectedOutcome> outcomes) throws IOException {
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, renderOutcomesJson(outcomes), StandardCharsets.UTF_8);
    }

    private static String renderJson(GenerationResult result) {
        StringBuilder sb = new StringBuilder(Math.max(4096, result.totalTests() * 96));
        sb.append("{\n");
        sb.append("  \"tier\": \"").append(result.tier().name()).append("\",\n");
        sb.append("  \"seed\": ").append(result.seed()).append(",\n");
        sb.append("  \"classCount\": ").append(result.classCount()).append(",\n");
        sb.append("  \"testsPerClass\": ").append(result.testsPerClass()).append(",\n");
        sb.append("  \"totalTests\": ").append(result.totalTests()).append(",\n");
        sb.append("  \"outcomes\": [\n");
        appendOutcomes(sb, result.outcomes());
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String renderOutcomesJson(List<ExpectedOutcome> outcomes) {
        StringBuilder sb = new StringBuilder(Math.max(4096, outcomes.size() * 96));
        sb.append("{\n  \"outcomes\": [\n");
        appendOutcomes(sb, outcomes);
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static void appendOutcomes(StringBuilder sb, List<ExpectedOutcome> outcomes) {
        for (int i = 0; i < outcomes.size(); i++) {
            ExpectedOutcome outcome = outcomes.get(i);
            sb.append("    {\n");
            sb.append("      \"testId\": \"").append(escape(outcome.testId())).append("\",\n");
            sb.append("      \"behavior\": \"").append(escape(outcome.behavior())).append("\",\n");
            sb.append("      \"expectedFailureMode\": \"")
                    .append(escape(outcome.expectedFailureMode()))
                    .append("\",\n");
            sb.append("      \"expectedRecovery\": \"")
                    .append(escape(outcome.expectedRecovery()))
                    .append("\",\n");
            sb.append("      \"expectedFinalState\": \"")
                    .append(escape(outcome.expectedFinalState()))
                    .append("\"\n");
            sb.append("    }");
            if (i + 1 < outcomes.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
