package io.phoenixfire.chaos.generator;

/**
 * Built-in corpus tiers. Scale is controlled by class count and tests-per-class; stress reaches
 * roughly tens of thousands of generated test methods.
 */
public enum TierName {
    SMOKE(20, 5, 42),
    MEDIUM(200, 10, 42),
    STRESS(3_000, 10, 42);

    private final int classCount;
    private final int testsPerClass;
    private final long defaultSeed;

    TierName(int classCount, int testsPerClass, long defaultSeed) {
        this.classCount = classCount;
        this.testsPerClass = testsPerClass;
        this.defaultSeed = defaultSeed;
    }

    public int classCount() {
        return classCount;
    }

    public int testsPerClass() {
        return testsPerClass;
    }

    public long defaultSeed() {
        return defaultSeed;
    }

    public int totalTests() {
        return classCount * testsPerClass;
    }

    public static TierName parse(String value) {
        if (value == null || value.isBlank()) {
            return SMOKE;
        }
        return TierName.valueOf(value.trim().toUpperCase());
    }
}
