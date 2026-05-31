package io.phoenixfire.chaos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry points for generated chaos tests. Each method models a distinct failure or recovery shape
 * that Phoenixfire should classify and handle.
 */
public final class ChaosRuntime {

    private static final Path CHAOS_DIR = Path.of("target", "chaos");
    private static final Map<String, Integer> STATIC_POLLUTION = new ConcurrentHashMap<>();

    private ChaosRuntime() {
    }

    /** Deterministic pass — baseline noise in large corpora. */
    public static void stablePass(String testId) {
        // no-op
    }

    /**
     * Stateless Bernoulli flake: fail when {@code roll(seed, testId) < pFail}.
     * Retries in a fresh JVM draw a new outcome when the seed mixes in attempt counters externally.
     */
    public static void statelessFlake(String testId, long seed, int pFail) {
        if (roll(seed, testId) < pFail) {
            fail(testId, "stateless flake (p=" + pFail + ")");
        }
    }

    /**
     * Fail until the on-disk attempt counter exceeds {@code recoverAfterAttempts}, then pass.
     * Markers persist across fork restarts in the same module working directory.
     */
    public static void recoverAfterAttempts(String testId, int recoverAfterAttempts) throws IOException {
        Path marker = attemptMarker(testId);
        int attempt = readAttemptCount(marker);
        writeAttemptCount(marker, attempt + 1);
        if (attempt < recoverAfterAttempts) {
            fail(testId, "recover-after attempt " + (attempt + 1) + "/" + recoverAfterAttempts);
        }
    }

    /** Sets a static pollution flag; poisons subsequent tests in the same JVM. */
    public static void polluteStatic(String testId, String pollutionKey) {
        STATIC_POLLUTION.put(pollutionKey, 1);
    }

    /** Fails when static pollution is present (order-dependent within a fork). */
    public static void failIfStaticPolluted(String testId, String pollutionKey) {
        if (STATIC_POLLUTION.containsKey(pollutionKey)) {
            fail(testId, "static pollution detected for key " + pollutionKey);
        }
    }

    /**
     * Writes a file marker so a sibling test can observe pollution after a fork crash + retry.
     * Pattern mirrors Phoenixfire IT {@code ForkCrashSuiteTest}.
     */
    public static void writePollutionMarker(String testId, String markerName) throws IOException {
        writeMarker(markerName);
    }

    /** Fails until a pollution marker exists (simulates cross-attempt recovery). */
    public static void failUntilPollutionMarker(String testId, String markerName) throws IOException {
        if (!Files.exists(markerPath(markerName))) {
            fail(testId, "pollution marker " + markerName + " not yet present");
        }
    }

    /**
     * On first execution: writes pollution + crash markers, then hard-halts the fork.
     * Subsequent attempts see the crash marker and become a no-op pass.
     */
    public static void crashOnceThenPass(String testId, String pollutionMarker, String crashMarker, int exitCode)
            throws IOException {
        Path crash = markerPath(crashMarker);
        if (!Files.exists(crash)) {
            writeMarker(pollutionMarker);
            writeMarker(crashMarker);
            Runtime.getRuntime().halt(exitCode);
        }
    }

    /** Always fails — should remain failed after exhausting retry budget. */
    public static void permanentFail(String testId, String message) {
        fail(testId, message);
    }

    /** Hard-halts the fork with probability {@code pFail}. */
    public static void intermittentHalt(String testId, long seed, int pFail, int exitCode) {
        if (roll(seed, testId) < pFail) {
            Runtime.getRuntime().halt(exitCode);
        }
    }

    /**
     * Triggers {@link OutOfMemoryError} with probability {@code pFail} by allocating up to
     * {@code megabytes} (capped relative to max heap to avoid killing the CI agent).
     */
    public static void intermittentOom(String testId, long seed, int pFail, int megabytes) {
        if (roll(seed, testId) >= pFail) {
            return;
        }
        long max = Runtime.getRuntime().maxMemory();
        long cap = Math.min((long) megabytes * 1024 * 1024, max / 2);
        if (cap < 1024 * 1024) {
            cap = 1024 * 1024;
        }
        try {
            @SuppressWarnings("unused")
            byte[] leak = new byte[(int) cap];
        } catch (OutOfMemoryError e) {
            throw e;
        }
    }

    /** Spins the current thread for {@code millis} (heartbeat / slow-test stress). */
    public static void slowSpin(String testId, long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * Spins near {@code millis} only on flaky draws — otherwise returns quickly so most runs stay
     * fast while occasional runs approach heartbeat timeout.
     */
    public static void intermittentSlowSpin(String testId, long seed, int pFail, long millis)
            throws InterruptedException {
        if (roll(seed, testId) < pFail) {
            Thread.sleep(millis);
        }
    }

    /**
     * Deadlocks with probability {@code pFail} — intended for heartbeat-timeout classification.
     * Use only with generous {@code heartbeatTimeout} in smoke tiers.
     */
    public static void intermittentDeadlock(String testId, long seed, int pFail) throws InterruptedException {
        if (roll(seed, testId) >= pFail) {
            return;
        }
        CountDownLatch never = new CountDownLatch(1);
        never.await();
    }

    /** Touches a broken singleton keyed by {@code pollutionKey}. */
    public static void brokenSingleton(String testId, String pollutionKey) {
        BrokenSingleton.forKey(pollutionKey).touch();
    }

    /** Fails when the keyed broken singleton reports poisoned state. */
    public static void failIfBrokenSingleton(String testId, String pollutionKey) {
        if (BrokenSingleton.forKey(pollutionKey).isPoisoned()) {
            fail(testId, "broken singleton is poisoned for key " + pollutionKey);
        }
    }

    /** Allocates several large arrays to pressure fork heap limits. */
    public static void heapPressure(String testId, long seed, int pFail, int arrayCount, int arrayMegabytes) {
        if (roll(seed, testId) >= pFail) {
            return;
        }
        long max = Runtime.getRuntime().maxMemory();
        int perArray = (int) Math.min((long) arrayMegabytes * 1024 * 1024, max / (arrayCount + 1));
        for (int i = 0; i < arrayCount; i++) {
            @SuppressWarnings("unused")
            byte[] block = new byte[Math.max(perArray, 256 * 1024)];
        }
    }

    /** In-process attempt counter — fails until {@code recoverAfter} in-memory attempts. */
    public static void recoverAfterInProcess(String testId, int recoverAfter) {
        AtomicInteger counter = InProcessAttempts.forTest(testId);
        int attempt = counter.incrementAndGet();
        if (attempt <= recoverAfter) {
            fail(testId, "in-process recover-after attempt " + attempt + "/" + recoverAfter);
        }
    }

    static int roll(long seed, String testId) {
        long mixed = seed ^ testId.hashCode() * 31L;
        mixed = mixed * 6364136223846793005L + 1;
        return (int) (Math.floorMod(mixed, 100) + 1);
    }

    private static void fail(String testId, String message) {
        throw new AssertionError("[" + testId + "] " + message);
    }

    private static Path attemptMarker(String testId) {
        return CHAOS_DIR.resolve("attempts").resolve(sanitize(testId) + ".count");
    }

    private static Path markerPath(String markerName) {
        return CHAOS_DIR.resolve("markers").resolve(sanitize(markerName) + ".marker");
    }

    private static void writeMarker(String markerName) throws IOException {
        Path marker = markerPath(markerName);
        Files.createDirectories(marker.getParent());
        Files.write(marker, new byte[0]);
    }

    private static int readAttemptCount(Path marker) throws IOException {
        if (!Files.exists(marker)) {
            return 0;
        }
        String text = Files.readString(marker).trim();
        if (text.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(text);
    }

    private static void writeAttemptCount(Path marker, int count) throws IOException {
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, Integer.toString(count));
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Per-key holder poisoned on first touch within a JVM (cleared by fresh fork). */
    static final class BrokenSingleton {
        private static final Map<String, Holder> HOLDERS = new ConcurrentHashMap<>();

        static BrokenSingleton forKey(String key) {
            return new BrokenSingleton(key);
        }

        private final String key;

        private BrokenSingleton(String key) {
            this.key = key;
        }

        void touch() {
            HOLDERS.computeIfAbsent(key, ignored -> new Holder()).touch();
        }

        boolean isPoisoned() {
            return HOLDERS.computeIfAbsent(key, ignored -> new Holder()).poisoned;
        }

        static final class Holder {
            private boolean poisoned;

            void touch() {
                poisoned = true;
            }
        }
    }

    static final class InProcessAttempts {
        private static final Map<String, AtomicInteger> COUNTERS = new ConcurrentHashMap<>();

        private InProcessAttempts() {
        }

        static AtomicInteger forTest(String testId) {
            return COUNTERS.computeIfAbsent(testId, ignored -> new AtomicInteger());
        }
    }
}
