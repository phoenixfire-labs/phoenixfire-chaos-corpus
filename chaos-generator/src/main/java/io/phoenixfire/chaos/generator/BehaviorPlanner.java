package io.phoenixfire.chaos.generator;

import io.phoenixfire.chaos.ExpectedOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns behaviors to generated tests using a seeded distribution.
 */
final class BehaviorPlanner {

    private static final BehaviorTemplate[] POOL = {
            BehaviorTemplate.STABLE_PASS,
            BehaviorTemplate.STABLE_PASS,
            BehaviorTemplate.STATELESS_FLAKE,
            BehaviorTemplate.STATELESS_FLAKE,
            BehaviorTemplate.STATELESS_FLAKE,
            BehaviorTemplate.RECOVER_AFTER_FILE,
            BehaviorTemplate.RECOVER_AFTER_FILE,
            BehaviorTemplate.RECOVER_AFTER_IN_PROCESS,
            BehaviorTemplate.PERMANENT_FAIL,
            BehaviorTemplate.INTERMITTENT_HALT,
            BehaviorTemplate.INTERMITTENT_OOM,
            BehaviorTemplate.INTERMITTENT_SLOW,
            BehaviorTemplate.HEAP_PRESSURE,
            BehaviorTemplate.STATIC_ORDER_PAIR,
            BehaviorTemplate.POLLUTION_CRASH_PAIR,
            BehaviorTemplate.BROKEN_SINGLETON_PAIR,
            BehaviorTemplate.DEADLOCK
    };

    private final long seed;

    BehaviorPlanner(long seed) {
        this.seed = seed;
    }

    GenerationResult plan(TierName tier) {
        int classCount = tier.classCount();
        int testsPerClass = tier.testsPerClass();
        List<GeneratedClass> classes = new ArrayList<>(classCount);

        for (int classIndex = 0; classIndex < classCount; classIndex++) {
            String classId = String.format("%04d", classIndex);
            List<GeneratedTest> tests = new ArrayList<>(testsPerClass);
            int methodIndex = 0;
            while (methodIndex < testsPerClass) {
                BehaviorTemplate template = pickTemplate(classId, methodIndex);
                int consumed = template.plan(this, classId, methodIndex, tests);
                methodIndex += consumed;
            }
            classes.add(new GeneratedClass(classId, "Flake" + classId + "Test", List.copyOf(tests)));
        }
        return GenerationResult.empty(tier, seed, classCount, testsPerClass).withClasses(classes);
    }

    private BehaviorTemplate pickTemplate(String classId, int methodIndex) {
        int idx = (int) (Math.floorMod(mix(classId, methodIndex), POOL.length));
        return POOL[idx];
    }

    long mix(String classId, int methodIndex) {
        long mixed = seed ^ classId.hashCode() * 31L ^ (long) methodIndex * 997L;
        return mixed * 6364136223846793005L + 1;
    }

    int roll(String classId, int methodIndex, int bound) {
        return (int) (Math.floorMod(mix(classId, methodIndex), bound));
    }

    enum BehaviorTemplate {
        STABLE_PASS {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.stablePass(\"" + testId + "\");",
                        outcome(testId, name(), "NONE", "SHARED_FORK_POOL", "PASSED")));
                return 1;
            }
        },
        STATELESS_FLAKE {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 10 + planner.roll(classId, methodIndex, 40);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.statelessFlake(\"" + testId + "\", " + testSeed + "L, " + pFail + ");",
                        outcome(testId, name(), "ASSERTION_FAILURE", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        RECOVER_AFTER_FILE {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int recoverAfter = 1 + planner.roll(classId, methodIndex, 2);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.recoverAfterAttempts(\"" + testId + "\", " + recoverAfter + ");",
                        outcome(testId, name(), "ASSERTION_FAILURE", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        RECOVER_AFTER_IN_PROCESS {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int recoverAfter = 1 + planner.roll(classId, methodIndex, 2);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.recoverAfterInProcess(\"" + testId + "\", " + recoverAfter + ");",
                        outcome(testId, name(), "ASSERTION_FAILURE", "SHARED_FORK_POOL", "PASSED")));
                return 1;
            }
        },
        PERMANENT_FAIL {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.permanentFail(\"" + testId + "\", \"permanent failure\");",
                        outcome(testId, name(), "ASSERTION_FAILURE", "ONE_FORK_PER_CLASS", "FAILED")));
                return 1;
            }
        },
        INTERMITTENT_HALT {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 8 + planner.roll(classId, methodIndex, 12);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.intermittentHalt(\"" + testId + "\", " + testSeed + "L, " + pFail + ", 137);",
                        outcome(testId, name(), "SIGKILL", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        INTERMITTENT_OOM {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 8 + planner.roll(classId, methodIndex, 12);
                int mb = 32 + planner.roll(classId, methodIndex, 64);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.intermittentOom(\"" + testId + "\", " + testSeed + "L, " + pFail + ", " + mb + ");",
                        outcome(testId, name(), "OOM", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        INTERMITTENT_SLOW {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 5 + planner.roll(classId, methodIndex, 10);
                long millis = 200L + planner.roll(classId, methodIndex, 400);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.intermittentSlowSpin(\"" + testId + "\", " + testSeed + "L, " + pFail + ", "
                                + millis + "L);",
                        outcome(testId, name(), "NONE", "SHARED_FORK_POOL", "PASSED")));
                return 1;
            }
        },
        HEAP_PRESSURE {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 10 + planner.roll(classId, methodIndex, 15);
                int arrays = 2 + planner.roll(classId, methodIndex, 3);
                int mb = 16 + planner.roll(classId, methodIndex, 32);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.heapPressure(\"" + testId + "\", " + testSeed + "L, " + pFail + ", " + arrays
                                + ", " + mb + ");",
                        outcome(testId, name(), "OOM", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        DEADLOCK {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String testId = testId(classId, methodIndex);
                int pFail = 3 + planner.roll(classId, methodIndex, 5);
                long testSeed = planner.mix(classId, methodIndex);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        testId,
                        methodIndex + 1,
                        false,
                        "ChaosRuntime.intermittentDeadlock(\"" + testId + "\", " + testSeed + "L, " + pFail + ");",
                        outcome(testId, name(), "HEARTBEAT_TIMEOUT", "FRESH_FORK", "PASSED")));
                return 1;
            }
        },
        STATIC_ORDER_PAIR {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String pollutionKey = classId + ".static." + methodIndex;
                String polluteId = testId(classId, methodIndex);
                String victimId = testId(classId, methodIndex + 1);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        polluteId,
                        methodIndex + 1,
                        true,
                        "ChaosRuntime.polluteStatic(\"" + polluteId + "\", \"" + pollutionKey + "\");",
                        outcome(polluteId, name() + "_POLLUTE", "NONE", "SHARED_FORK_POOL", "PASSED")));
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex + 1,
                        victimId,
                        methodIndex + 2,
                        true,
                        "ChaosRuntime.failIfStaticPolluted(\"" + victimId + "\", \"" + pollutionKey + "\");",
                        outcome(victimId, name() + "_VICTIM", "ASSERTION_FAILURE", "FRESH_FORK", "PASSED")));
                return 2;
            }
        },
        POLLUTION_CRASH_PAIR {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String pollutionMarker = classId + ".pollution." + methodIndex;
                String crashMarker = classId + ".crash." + methodIndex;
                String victimId = testId(classId, methodIndex);
                String crashId = testId(classId, methodIndex + 1);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        victimId,
                        methodIndex + 1,
                        true,
                        "ChaosRuntime.failUntilPollutionMarker(\"" + victimId + "\", \"" + pollutionMarker + "\");",
                        outcome(victimId, name() + "_VICTIM", "ASSERTION_FAILURE", "FRESH_FORK", "PASSED")));
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex + 1,
                        crashId,
                        methodIndex + 2,
                        true,
                        "ChaosRuntime.crashOnceThenPass(\"" + crashId + "\", \"" + pollutionMarker + "\", \""
                                + crashMarker + "\", 137);",
                        outcome(crashId, name() + "_CRASH", "SIGKILL", "FRESH_FORK", "PASSED")));
                return 2;
            }
        },
        BROKEN_SINGLETON_PAIR {
            @Override
            int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink) {
                String pollutionKey = classId + ".broken." + methodIndex;
                String touchId = testId(classId, methodIndex);
                String victimId = testId(classId, methodIndex + 1);
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex,
                        touchId,
                        methodIndex + 1,
                        true,
                        "ChaosRuntime.brokenSingleton(\"" + touchId + "\", \"" + pollutionKey + "\");",
                        outcome(touchId, name() + "_TOUCH", "NONE", "SHARED_FORK_POOL", "PASSED")));
                sink.add(new GeneratedTest(
                        classId,
                        methodIndex + 1,
                        victimId,
                        methodIndex + 2,
                        true,
                        "ChaosRuntime.failIfBrokenSingleton(\"" + victimId + "\", \"" + pollutionKey + "\");",
                        outcome(victimId, name() + "_VICTIM", "ASSERTION_FAILURE", "FRESH_FORK", "PASSED")));
                return 2;
            }
        };

        abstract int plan(BehaviorPlanner planner, String classId, int methodIndex, List<GeneratedTest> sink);

        static String testId(String classId, int methodIndex) {
            return classId + "." + String.format("%02d", methodIndex);
        }

        static ExpectedOutcome outcome(
                String testId,
                String behavior,
                String failureMode,
                String recovery,
                String finalState) {
            return new ExpectedOutcome(testId, behavior, failureMode, recovery, finalState);
        }
    }
}
