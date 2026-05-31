package io.phoenixfire.chaos.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SourceEmitter {

    private static final String PACKAGE = "io.phoenixfire.chaos.generated";

    private SourceEmitter() {
    }

    static void emit(Path outputDir, GenerationResult result) throws IOException {
        Path packageDir = outputDir.resolve(PACKAGE.replace('.', '/'));
        Files.createDirectories(packageDir);
        for (GeneratedClass clazz : result.classes()) {
            Path file = packageDir.resolve(clazz.className() + ".java");
            Files.writeString(file, renderClass(clazz), StandardCharsets.UTF_8);
        }
    }

    private static String renderClass(GeneratedClass clazz) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("package ").append(PACKAGE).append(";\n\n");
        sb.append("import io.phoenixfire.chaos.ChaosRuntime;\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        if (clazz.requiresOrdering()) {
            sb.append("import org.junit.jupiter.api.MethodOrderer;\n");
            sb.append("import org.junit.jupiter.api.Order;\n");
            sb.append("import org.junit.jupiter.api.TestMethodOrder;\n");
        }
        sb.append("\n");
        if (clazz.requiresOrdering()) {
            sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        }
        sb.append("class ").append(clazz.className()).append(" {\n\n");
        for (GeneratedTest test : clazz.tests()) {
            sb.append("    @Test\n");
            if (clazz.requiresOrdering()) {
                sb.append("    @Order(").append(test.order()).append(")\n");
            }
            sb.append("    void ").append(test.methodName()).append("() throws Exception {\n");
            sb.append("        ").append(test.javaBody()).append("\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
