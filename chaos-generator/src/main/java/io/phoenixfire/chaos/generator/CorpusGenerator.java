package io.phoenixfire.chaos.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * CLI entry point: {@code java -jar chaos-generator.jar --tier smoke --seed 42 --output ...}.
 */
public final class CorpusGenerator {

    private CorpusGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        TierName tier = TierName.parse(arguments.tier());
        long seed = arguments.seed().orElse(tier.defaultSeed());

        System.out.printf(
                Locale.ROOT,
                "Generating chaos corpus: tier=%s seed=%d classes=%d testsPerClass=%d (~%d tests)%n",
                tier.name(),
                seed,
                tier.classCount(),
                tier.testsPerClass(),
                tier.totalTests());

        BehaviorPlanner planner = new BehaviorPlanner(seed);
        GenerationResult result = planner.plan(tier);

        Path outputDir = arguments.outputDir();
        Path manifestDir = arguments.manifestDir();

        cleanDirectory(outputDir);
        SourceEmitter.emit(outputDir, result);
        ManifestWriter.writeManifest(manifestDir.resolve("generated-" + tier.name().toLowerCase(Locale.ROOT) + ".json"),
                result);
        ManifestWriter.writeOutcomesResource(
                arguments.resourceDir().resolve("expected-outcomes.json"), result.outcomes());

        System.out.printf(
                Locale.ROOT,
                "Wrote %d classes and %d tests to %s%n",
                result.classes().size(),
                result.totalTests(),
                outputDir.toAbsolutePath());
    }

    private static void cleanDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to delete " + path, e);
                        }
                    });
        }
    }

    private record Arguments(
            String tier,
            java.util.OptionalLong seed,
            Path outputDir,
            Path manifestDir,
            Path resourceDir) {

        static Arguments parse(String[] args) {
            String tier = "smoke";
            Long seed = null;
            Path outputDir = null;
            Path manifestDir = null;
            Path resourceDir = null;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--tier", "-t" -> tier = requireValue(args, ++i, arg);
                    case "--seed", "-s" -> seed = Long.parseLong(requireValue(args, ++i, arg));
                    case "--output", "-o" -> outputDir = Path.of(requireValue(args, ++i, arg));
                    case "--manifest", "-m" -> manifestDir = Path.of(requireValue(args, ++i, arg));
                    case "--resources", "-r" -> resourceDir = Path.of(requireValue(args, ++i, arg));
                    case "--help", "-h" -> {
                        printUsage();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (outputDir == null) {
                throw new IllegalArgumentException("--output is required");
            }
            if (manifestDir == null) {
                manifestDir = outputDir.getParent().resolve("manifest");
            }
            if (resourceDir == null) {
                resourceDir = outputDir.getParent().resolve("resources/chaos");
            }

            return new Arguments(tier, seed == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(seed),
                    outputDir, manifestDir, resourceDir);
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return args[index];
        }

        private static void printUsage() {
            System.out.println("""
                    Usage: chaos-generator [options]

                      --tier, -t       smoke | medium | stress (default: smoke)
                      --seed, -s       RNG seed (default: tier default)
                      --output, -o     Generated test source directory (required)
                      --manifest, -m   Manifest JSON output directory
                      --resources, -r  Classpath resource directory for expected outcomes
                    """);
        }
    }
}
