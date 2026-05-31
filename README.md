# Phoenixfire Chaos Corpus

A sister project that generates **stochastic and adversarial JUnit tests** for stress-testing [Phoenixfire](https://github.com/BenManifold/maven-phoenixfire-plugin). Tests are produced from a seeded tier definition — not hand-written — so you can scale from a fast smoke run to tens of thousands of methods without maintaining a giant source tree.

## Layout

```
phoenixfire-chaos-corpus/
├── chaos-runtime/     Shared failure/recovery helpers (on the fork classpath)
├── chaos-generator/   Seeded Java source + manifest generator (CLI + Maven hook)
├── chaos-corpus/      Maven module that generates and runs the corpus
└── manifest/tiers/    Human-readable tier metadata (smoke / medium / stress)
```

Generated output (gitignored) lands under:

- `chaos-corpus/src/test/java/io/phoenixfire/chaos/generated/` — JUnit classes
- `chaos-corpus/manifest/generated-*.json` — full manifest with expected outcomes
- `chaos-corpus/src/test/resources/chaos/expected-outcomes.json` — classpath resource for verifiers

## Tiers

| Tier   | Classes | Tests/class | ~Total tests | Intended use        |
|--------|---------|-------------|--------------|---------------------|
| smoke  | 20      | 5           | 100          | PR / quick sanity   |
| medium | 200     | 10          | 2,000        | Nightly             |
| stress | 3,000   | 10          | 30,000       | Local / weekly hammer |

Override at build time:

```bash
mvn test -pl chaos-corpus -Dchaos.tier=medium -Dchaos.seed=42
```

## Failure behaviors

Each generated test invokes `ChaosRuntime` with a specific shape:

| Behavior | What it does | Typical Phoenixfire expectation |
|----------|--------------|--------------------------------|
| `STABLE_PASS` | No-op | Always passes |
| `STATELESS_FLAKE` | Fail when `roll(seed, id) < N` | Retry → pass (`recovered`) |
| `RECOVER_AFTER_FILE` | Fail until attempt marker on disk exceeds N | Cross-fork recovery |
| `RECOVER_AFTER_IN_PROCESS` | In-JVM attempt counter | Same-fork retry |
| `STATIC_ORDER_PAIR` | Pollute static → victim fails | `FRESH_FORK` clears statics |
| `POLLUTION_CRASH_PAIR` | Victim fails → sibling halts fork | Escalation + file markers |
| `BROKEN_SINGLETON_PAIR` | Poisoned singleton init | Class isolation |
| `PERMANENT_FAIL` | Always fails | Stays failed |
| `INTERMITTENT_HALT` | `Runtime.halt(137)` with probability | `SIGKILL` → recover |
| `INTERMITTENT_OOM` / `HEAP_PRESSURE` | Heap pressure (capped vs max heap) | `OOM` → fresh fork |
| `INTERMITTENT_SLOW` / `DEADLOCK` | Slow spin / deadlock | Heartbeat stress |

The manifest records `expectedFailureMode`, `expectedRecovery`, and `expectedFinalState` per test for downstream report verification.

## Build and run

From this directory:

```bash
# Build generator + runtime + generate smoke corpus
mvn verify

# Run with Phoenixfire (install the plugin from ../maven-phoenixfire-plugin first)
cd ../maven-phoenixfire-plugin && mvn install -DskipTests
cd ../phoenixfire-chaos-corpus
mvn test -pl chaos-corpus -Pphoenixfire

# Scale up (explore without failing the build on permanent failures)
mvn clean test -pl chaos-corpus -Pphoenixfire,phoenixfire-explore -Dchaos.tier=medium
```

With `-Pphoenixfire` alone, **permanent failures and unrecovered flakes fail the build** — that is intentional. A typical smoke run reports many `Flaky(recovered)` tests alongside a handful of expected hard failures.

**Important:** run `mvn clean` before Phoenixfire stress runs so file-based attempt markers under `target/chaos/` do not carry over.

### Generator CLI

```bash
mvn -pl chaos-generator package
java -jar chaos-generator/target/chaos-generator-0.1.0-SNAPSHOT.jar \
  --tier smoke --seed 42 \
  --output chaos-corpus/src/test/java/io/phoenixfire/chaos/generated \
  --manifest chaos-corpus/manifest \
  --resources chaos-corpus/src/test/resources/chaos
```

## Phoenixfire configuration hints

For stress tiers:

- Increase `forkCount` and `maxAttempts` (defaults in `-Pphoenixfire`: 2 forks, 3 attempts).
- Set `heartbeatTimeout` high enough for `INTERMITTENT_SLOW` but low enough that `DEADLOCK` tests classify as heartbeat timeouts.
- Cap fork heap (`argLine`) when running `INTERMITTENT_OOM` / `HEAP_PRESSURE` heavy tiers so you stress **fork** OOM, not the CI agent.

## Relationship to Phoenixfire IT

`phoenixfire-it` keeps **small, precise** Invoker scenarios with Groovy verifiers. This corpus provides **volume and statistical coverage** — sharding, resume, report aggregation, fork pool sizing, and classification at scale.
