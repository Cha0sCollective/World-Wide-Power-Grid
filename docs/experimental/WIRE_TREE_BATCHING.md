# Experimental wire-tree restoration batching

This experiment follows the [restoration profile](RESTORATION_PROFILE.md) on
`dev/experimental-plain-wire-batching`, based on `84bb53e`. The packed/worker
prototype remains reverted. Plain-wire and CEE property-source aggregation
batching remain enabled. The new runtime is `0.2.7-experimental.wiretree.2`.
It is isolated research, with disposable benchmark copies and focused checks;
it is not normal acceptance or a release candidate.

## Comparison

On the 14,650-node mixed benchmark, the final prototype reduced the mean
restoration timer from **103.41 to 86.73 seconds**, a **16.1% reduction**
(16.68 seconds). Both enabled samples finished sooner than either control.
However, its longest ticks rose from **2.11–2.60 to 13.56–15.41 seconds**.
This is useful evidence for restoration batching, but this unbounded flush
should remain an opt-in experiment: overall completion improved while the
worst individual loading stalls became much longer.

All four runs used the identical `wiretree.2` jar and fresh save copies, in
off/on/on/off order. Detailed loading timers and JFR were disabled.

| Attempt, in execution order | Tree batch | Restoration seconds | Longest tick seconds | Chunk-ready tick | Matrix assemblies |
| --- | --- | ---: | ---: | ---: | ---: |
| `tree2-control-01` | Off | 106.66 | 2.60 | 959 | 717 |
| `tree2-enabled-01` | On | 89.05 | 13.56 | 557 | 602 |
| `tree2-enabled-02` | On | 84.41 | 15.41 | 549 | 596 |
| `tree2-control-02` | Off | 100.16 | 2.11 | 902 | 698 |

The timer includes initial chunk requests and the existing gate of 100
consecutive healthy ticks. Mean time waiting for block/entity/ticking readiness
fell from 95.69 to 79.07 seconds; final electrical settling stayed near 6.4
seconds. As in the preceding profile, readiness includes reconstruction and
graph work, not just disk reads. Earlier readiness in tick numbers must not be
mistaken for a proportional wall-time improvement when ticks themselves grow.

The helper verified 14,650 nodes, 20,637 edges, 14,925 matrix rows, one native
network, 953 chunks and 1,050 saved storage identities. The final 100 ticks each
executed 16 network substeps with no matrix rebuilds. There were zero independent
CEE or rejected shared solves, normal saves/shutdowns, restored thread affinity,
and unchanged original/reference inventories in every attempt.

Read-only host samples found the previously interfering `FactoryGame` process
absent. Other monitored processes averaged 0.34–0.41 CPU cores during the
overlapping sample intervals. Two samples per mode, without frequency/thermal
control or flushed disk caches, establish an observed improvement on this yard;
they do not establish statistical significance or sustained 20 TPS.

## Why this is only a first step

The conservative coverage test eliminates little of the deferred traversal work:

| Enabled-run counter | First sample | Second sample |
| --- | ---: | ---: |
| Saved attachments / fallback calls | 5,893 / 50 | 5,893 / 50 |
| Queued roots | 11,786 | 11,786 |
| Actual traversals | 11,495 | 11,387 |
| Covered roots skipped | 291 (2.47%) | 399 (3.39%) |
| Endpoint visits | 50,682,352 | 50,153,152 |
| Flushes | 145 | 141 |
| Largest flush, in roots | 5,626 | 6,488 |
| Final native connection attempts | 95 | 103 |

These counters describe this deferred path, not all PG tree resolution. The
disabled control does not collect equivalent tree counters. More than 50 million
endpoint visits remain in each enabled run, and one flush can contain thousands
of roots. Nothing currently bounds that flush's duration.

Network substep calls across partial circuits fell from 3.18–3.38 million in
the controls to 1.37 million with batching; matrix assemblies also fell. This
suggests that changed attachment/mutation grouping and less simulation of
temporary fragments contribute to the gain. The data does not isolate those
effects from root deduplication, and calls on small fragments are not equivalent
to full-yard solves. Moving reconstruction into the prepare boundary also moves
work that previously ran between measured ticks into tick bodies. Tick-body
time alone is therefore a misleading before/after metric.

The next experiment should bound restoration work per tick while keeping
affected circuits out of electrical preparation until their batch is coherent.
It needs an explicit invalidation/generation rule before extending coverage
across mutations or ticks. Simply truncating the queue or retaining today's
visited set would permit incomplete or stale graph state. The remaining endpoint
lookup/allocation work identified by the profile is another target. Moving the
current mutating traversals directly onto worker threads is still not justified.

## What changed

[WireTreeBatch](../../src/main/java/org/cha0scollective/wwpg/experimental/WireTreeBatch.java)
is disabled by default. Enable it with
`-Dwwpg.experimental.wireTreeBatch=true`, with existing restoration batching
enabled. It intercepts PG's saved-wire reconstruction only while the native
`BaseWireEntity.readAdditionalSaveData` call is active on the server thread.

Eligibility is deliberately narrow: an exact PG `HangingWireEntity`, two exact
`BlockWireEndpoint` objects, a matching simple wire UUID, and an already saved,
unowned part with matching endpoint orientation. PG and CEE block terminals
both use that endpoint type. Missing parts, custom wire/endpoint classes,
ordinary placement and calls outside the load scope use PG's original path.

The eligible path grabs the existing `TransmissionLinePart`, preserving its
object identity and persistent UUID, then queues its two roots. The queue drains
before `WorldBridge` enters electrical preparation. It uses the existing
stack-safe PG traversal, including its migration, leaf and connection behavior.
After both roots, it retains PG's final line-creation attempt for a still
unresolved owned part; this is essential to seed a closed saved loop.

[RestorationRoots](../../src/main/java/org/cha0scollective/wwpg/experimental/RestorationRoots.java)
skips a root only if it belongs to the preceding traversal's visited set and
every visited endpoint has known adjacency whose parts all have resolved lines.
Any actual traversal replaces that coverage; a line-creation attempt clears it.
Coverage never crosses a flush. Unknown adjacency or unresolved parts preserve
the walks. There is no persistent component cache.

The queue is cleared on level unload/server stop. Nothing is added to saved
data, native solver binaries are byte-identical to the retained batching
baseline, and the electrical schedule still uses all 16 substeps. This
prototype changes when restoration mutations occur; it does not dispatch
world access or graph mutation to worker threads.

## Focused correctness evidence

The final frozen runtime and fixtures passed **130 GameTests** in five launches:

| Selection | Native, enabled | Java, enabled | Native, disabled |
| --- | ---: | ---: | ---: |
| Saved-tree, loading, pause, contactor and wire-recovery fixtures | 36 | 36 | 36 |
| Unload and solver-safety fixtures | 11 | 11 | — |

The six new saved-tree cases exercise real wire NBT serialization and loading,
saved part/UUID identity, PG and CEE terminals, reverse arrival order, a missing
saved part falling back, and completely unresolved closed loops. They check
that live placement does not enter the queue, production preparation drains it,
and the resulting circuit has the expected voltage. The namespace is explicitly
opt-in and is not added to normal acceptance groups.

Six focused unit tests also passed: randomized traversal equivalence and a
10,000-node chain; 800 graph cases comparing root coalescing with scalar
resolution; coverage expiration; and existing restoration-window checks.
The randomized model tests resolution/migration invariants, not every possible
Minecraft or third-party endpoint behavior. Actual PG/CEE loading is covered
by the focused GameTests and copied yard.

A separate copied-save check, `tree2-reload-01`, passed a cold restoration
(87.49 seconds), actual absence of all requested chunks, and one in-process
reload (9.50 seconds, longest reload tick 0.73 seconds). It verified stored
values before solving again, recovered the full graph, and completed another
100 healthy ticks. The preserved helper writes duplicate `RELOAD` rows; the
analyzer deduplicates by phase/tick before calculating tick metrics, and the
runner checks the last 100 distinct ticks. This is a lifecycle check, not a
third sample in the cold-load comparison or a controlled warm-load speedup.

No full suite, graphical acceptance, stable TPS window or worker experiment
was run for this change.

## Preserved development attempts

All attempts remain separately named. Early unit runs failed the required
coverage-count assertion; the randomized sample was extended to include fully
resolved graphs. The first GameTest attempt failed two PG fixtures before
restoration because their default low resistance burned wires; explicit
1,000-ohm loads fixed the fixtures. Those attempts are not passing evidence.

The first runtime, `wiretree.1`, subsequently passed 124 focused GameTests and
completed an off/on/on/off loading comparison. Code review then identified
that its deferred path omitted PG's final line-creation step for entirely
unresolved closed loops. `wiretree.2` restores that step and adds two cold-loop
fixtures. The final five GameTest launches and benchmark comparison all use
the revised runtime; none of the earlier passes is relabeled as current.

The superseded comparison (`tree-control-01`, `tree-enabled-01`,
`tree-enabled-02`, `tree-control-02`) measured 104.27/92.79/87.89/101.91 seconds.
Those times describe the earlier runtime only. Its enabled runs also exposed
approximately 15-second tick stalls. Their raw evidence remains in the results
file and under `local/benchmarks`.

## Inputs and reproduction

Frozen final inputs: `local/frozen/prototype-04c/inputs.json`.

| Artifact | SHA-256 |
| --- | --- |
| Runtime | `5c5d4d9e6a652f5cabe589ac1a2b59d64c3954f5cbb05320c56899a448a50e1b` |
| GameTest fixtures | `b60bc743051432b67cf8c65d29a6a2da0f27f7075e9314d4edd2d31f262fa46c` |
| CEE 1.1.4 | `2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f` |

The benchmark uses Java 21.0.12, NeoForge 21.1.231, PG 0.6.2, Create 6.0.10-280,
the preserved helper, an 8 GiB maximum heap, the original 60-second watchdog,
and `fff000` server-thread affinity. Both modes use the identical final jar.
Each attempt creates a fresh world copy. Startup and shutdown are outside the
reported restoration duration. This is a fresh-process saved-world load, not
an experiment that flushes OS file caches.

```text
gradlew.bat jar gameTestJar test --offline --no-daemon --tests "*WireTreeTraversalTest" --tests "*RestorationRootsTest" --tests "*RestorationWindowTest"
python -B tools/experimental/freeze_tree.py --name prototype-tree-new
python -B tools/experimental/focused_checks.py --inputs local/frozen/prototype-tree-new/inputs.json --backend NATIVE --selection tree --property-batch --tree-batch --name tree-new-native-on
python -B tools/experimental/restoration_benchmark.py --reference-root <main-checkout> --name tree-new-control --runtime <frozen-runtime-jar>
python -B tools/experimental/restoration_benchmark.py --reference-root <main-checkout> --name tree-new-enabled --runtime <frozen-runtime-jar> --tree-batch
python -B tools/experimental/restoration_benchmark.py --reference-root <main-checkout> --name tree-new-reload --runtime <frozen-runtime-jar> --tree-batch --reload
```

Use Java 21 and fresh names. Run the five focused configurations in the table;
omit `--tree-batch` for the disabled control. Run timing attempts sequentially,
without Gradle, a game or another benchmark competing for CPU. The runner
records read-only host CPU samples every five seconds; this does not control
CPU frequency or thermals. Detailed loading instrumentation is off for timing.
`--diagnostic` is a separate, optional attribution mode, not used in this
comparison.

The result file for this specific experiment was generated with:

```text
python -B tools/experimental/summarize_tree.py --attempts tree2-control-01 tree2-enabled-01 tree2-enabled-02 tree2-control-02 tree2-reload-01
```

[wire-tree-results.json](wire-tree-results.json) records artifact/source hashes,
per-attempt validation, timings, execution counts, host samples, tick metrics
and hashes of raw evidence. The summary tool refuses to overwrite an existing
output and verifies that current sources still match the frozen inputs.
