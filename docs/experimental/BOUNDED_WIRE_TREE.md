# Bounded wire-tree restoration experiment

This follows [unbounded wire-tree batching](WIRE_TREE_BATCHING.md), on
`dev/experimental-plain-wire-batching` from `a5e8aa4`. The experimental runtime
is `0.2.7-experimental.wiretree.4`. Plain-wire and CEE property aggregation
batching remain enabled; worker processing remains reverted.

## Loading and stall comparison

In the four-run timing comparison, the 20 ms / 32-wire budget reduced the largest loading tick from
**14.44 to 2.85 seconds (80.3%)**. Mean restoration grew from **85.68 to 92.71
seconds**, adding **7.02 seconds (8.2%)**. Both bounded runs had shorter worst
ticks and longer total loads than either unbounded control.

| Attempt, in execution order | Mode | Restoration seconds | Worst tick seconds | Longest gap between tick ends | Largest queued drain |
| --- | --- | ---: | ---: | ---: | ---: |
| `budget20-final-01` | Bounded | 92.67 | 2.825 | 2.829 s | 138.02 ms |
| `budget-unbounded-final-01` | Unbounded | 87.84 | 14.442 | 14.493 s | 13,941.46 ms |
| `budget-unbounded-final-02` | Unbounded | 83.53 | 13.884 | 13.922 s | 12,295.82 ms |
| `budget20-final-02` | Bounded | 92.74 | 2.846 | 2.878 s | 79.06 ms |

The gap metric includes work between ticks and ordinary waiting, excludes phase
boundaries, and has millisecond clock resolution. It confirms that the reduced
tick maximum did not merely move the same long pause between ticks. The total
restoration timer includes chunk requests, queue draining, graph recovery and
100 consecutive healthy ticks, but excludes process startup and shutdown.

The bounded runs reached chunk readiness at 560/522 ticks, then spent a further
40.43/42.30 seconds draining queued roots. The unbounded controls drained within
each prepare call and had no queue left at chunk readiness. Bounding the work
changes its distribution across frames; it does not make chunk readiness alone
a valid completion timer.

| Work during the cold restoration | Unbounded samples | Bounded samples |
| --- | ---: | ---: |
| Matrix assemblies | 611 / 597 | 1 / 1 |
| Network substep calls across all fragments | 1,397,424 / 1,287,776 | 1,936 / 1,936 |
| Deferred root traversals | 11,474 / 11,352 | 10,701 / 10,974 |
| Roots skipped by coverage | 312 / 434 | 1,085 / 812 |
| Endpoint visits in queued traversals | 50.58 / 49.98 million | 46.80 / 48.13 million |

Each run attached 5,893 saved wires, with 50 fallback calls. Both bounded runs
respected the 32-item cap, drained all queued work and discarded zero roots.
Their largest individual wire operations took 135.07/76.04 ms, explaining why
the elapsed-time target can be exceeded. Other preparation/loading work still
produced the approximately 2.8-second worst ticks. The queue does not bound all
restoration paths.

Holding the incomplete dimension eliminates almost all partial-circuit solves
and repeated matrix preparation. The bounded runs nevertheless take more total
time: tens of millions of tree visits remain, and preparation/commit and other
server work repeat across 1,318–1,331 ticks instead of 639–644. This is a
responsiveness tradeoff, not a demonstrated throughput improvement. Fewer calls
on partial circuits are not equivalent to fewer full-yard solves.

All four runs recovered 14,650 nodes, 20,637 edges, 14,925 matrix rows, one native
network and 953 chunks, with all 1,050 pre-solve storage identities/values
verified. The final 100 distinct ticks each ran 16 network substeps without
matrix rebuilds. Independent CEE solves and rejected shared solves were zero;
shutdown/save and affinity restoration completed normally, and original and
reference payload inventories were unchanged.

The previously interfering game process was absent in host samples. Other
monitored processes averaged 0.32–0.42 CPU cores. These are two fresh samples per
mode in bounded/unbounded/unbounded/bounded order, with identical runtime and
helper artifacts. They establish the observed tradeoff on this yard, without
statistical significance, thermal/frequency control or sustained-TPS evidence.

## What the budget actually bounds

The opt-in bounded mode uses these JVM flags:

```text
-Dwwpg.experimental.wireTreeBatch=true
-Dwwpg.experimental.wireTreeBudgetMillis=20
-Dwwpg.experimental.wireTreeMaxParts=32
```

Tree batching is still disabled by default. With tree batching enabled, a zero
millisecond budget retains the unbounded control. The new budget applies per
dimension, per server tick. Several simultaneously restoring dimensions can
consume several budgets in one server tick.

[RestorationQueue](../../src/main/java/org/cha0scollective/wwpg/experimental/RestorationQueue.java)
processes a FIFO of saved-wire attachments. Each work item finishes both native
root traversals and the final connection attempt before yielding. A drain stops
after 32 items or when the elapsed-time target has been reached, whichever
comes first. It permits one item to make progress even if that item alone
exceeds the time target. **Twenty milliseconds is a cooperative target, not a
hard maximum tick duration.** A traversal of one large component remains atomic;
ordinary chunk work, node-holder traversal and other game work are outside this
queue's budget.

The queue retains saved parts, their original entity owners and endpoint roots.
It does not retain a partially executed traversal or reuse visited coverage
between ticks. Removed/replaced owners and removed saved parts discard their
queued roots rather than recreating a deleted connection. A line-creation
attempt still invalidates coverage within a slice. New attachments join the
tail, and level unload clears retained references.

CEE preparation runs at `tickServer` HEAD, before Minecraft increments its
counter. Production draining therefore records the upcoming server tick.
Additional calls later in that same frame share that identity and cannot
obtain another budget. Frozen frames do not enter the production drain.

## Holding incomplete circuits

While a dimension has unfinished queued roots, its native island discovery,
matrix preparation and electrical advancement are held through the existing
restoration coordinator. This real work queue takes precedence over the ordinary
20-tick quiet-window deadline. Coupled pause propagation and existing unavailable
readings remain in use. CEE commit masks paused devices and retains storage;
native battery/filter updates use the same hold. All 16 substeps resume after
the queue drains and ordinary preparation can accept the graph.

This first prototype conservatively holds **the whole affected dimension**,
including unrelated circuits there. It does not yet identify a minimal affected
component. A continuous stream of new saved wires can therefore prolong that
hold. The finite copied benchmark and focused checks do not establish fairness
or acceptable gameplay behavior for continuous world streaming. Nothing is
added to the save format, and native solver binaries are unchanged.

## Focused checks

The final frozen runtime passed the following configurations:

| Selection | Backend | Budget / item cap | Tests |
| --- | --- | --- | ---: |
| Tree, loading, pause, contactor, recovery and budget fixtures | Native | 20 ms / 1 | 38 |
| Same | Java | 20 ms / 1 | 38 |
| Tree, loading, pause, contactor and recovery fixtures | Native | Unbounded | 36 |
| Unload and solver safety | Native | 20 ms / 32 | 11 |
| Unload and solver safety | Java | 20 ms / 32 | 11 |

That is **134 focused GameTests**, with the new namespace kept outside normal
acceptance groups. The one-item cap forces a real 32-wire NBT restoration to
span more than 20 ticks. Its check verifies native battery energy, CEE capacitor
history/temperature, unavailable readings, no extra drain within one frame, and
eventual resumption. Another case removes a queued wire and checks that the old
part is never resurrected. Saved identity, closed loops, reverse order, missing
parts, coupled behavior, contactors and native lifecycle checks remain included.

Ten unit checks passed: existing restoration-window/traversal/coverage checks,
plus deadline overshoot at an atomic boundary, FIFO/cap/coverage behavior,
discarded owners, and 200 randomized graph comparisons over bounded waves.
These are focused checks, not full headless or graphical acceptance. No stable
TPS window was run.

The separate `budget20-reload-01` lifecycle check passed a cold restoration
(94.07 seconds), actual absence of the 953 chunks, and an in-process reload
(16.95 seconds). It preserved pre-solve storage values and recovered the full
graph with another 100 healthy ticks. The reload's longest tick was 0.745
seconds; its preceding cold load had a 3.38-second maximum. That additional cold
observation is retained separately from the balanced four-run comparison and
shows the remaining variability in unbudgeted loading work. Duplicate helper
`RELOAD` rows are deduplicated by phase/tick before validation and analysis.

The first frozen attempt, `prototype-05a` / `wiretree.3`, passed its ten unit
checks but failed the repeated-drain GameTest. It exposed the difference between
CEE's pre-increment hook and Minecraft's later tick counter. `wiretree.4` corrects
that identity. The failing attempt and its logs are preserved; its tests are not
counted as passes for the final artifact.

## A queue-aware benchmark gate

The initial bounded attempt, `budget20-01`, used the old helper. It stopped at
tick 843 because graph recovery exceeded the fixed 300-tick allowance after
chunk readiness at tick 542. It had completed 6,710 root walks and still had
5,076 roots queued; the largest drain was 78.29 ms and the worst tick was 3.54
seconds. The server stopped normally
and the evidence is retained as a **failed, incomplete load**, not a timing pass.

The comparison uses a separately frozen helper overlay. Only `Session`,
`LoadingGate` and its stage enum differ from the preserved helper jar. It adds
an explicit `WORK` stage after chunk readiness, observes decreasing queued roots
as progress, and requires an empty queue before graph/electrical acceptance.
Chunks and queued work share the existing absolute 2,859-tick limit for this
953-chunk yard. The 30-second no-progress timeout, later 300-tick graph/acceptance
limits, full graph/storage checks, 100 healthy ticks and normal watchdog remain.
The total timer includes the added stage; `queue_drain_seconds` separately
identifies time spent waiting for the queue after chunk readiness.

Seven standalone gate checks verify healthy-tick counting, progress beyond
300 ticks, a truly stalled queue, the finite total bound, newly queued work
resetting acceptance, graph timeout, and lost chunk readiness. Both comparison
modes use this same helper. Original helper sources/jars and the failed run
remain unchanged.

## Evidence and reproduction

Final inputs: `local/frozen/prototype-05b/inputs.json`.

| Artifact | SHA-256 |
| --- | --- |
| Runtime | `33fe56c713a63e3b70e045b7fc50667435b50923f569036e66e189ab8a063ac6` |
| Fixtures | `a951e16e1d550b96eedf3cbc2cc18277adf804b747bccf1116b88b3725afc2c1` |
| CEE 1.1.4 | `2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f` |
| Queue-aware benchmark helper | `3655eb8f3c129e8c7d6801361f66551e9ee97e12fa5d0c56bfb5354f010ce9bf` |

Java 21.0.12, NeoForge 21.1.231, PG 0.6.2, Create 6.0.10-280, CEE 1.1.4 and
the frozen helper overlay remain pinned. Both timing modes use the same
jar, an 8 GiB maximum heap, the ordinary 60-second watchdog and `fff000`
server-thread affinity. Runs are sequential, on fresh copied saves, without
JFR or detailed loading instrumentation. Source/reference payload inventories
are checked before and after each attempt. No original world is opened by a
game server.

```text
gradlew.bat jar gameTestJar test --offline --no-daemon --tests "*WireTreeTraversalTest" --tests "*RestorationRootsTest" --tests "*RestorationQueueTest" --tests "*RestorationWindowTest"
python -B tools/experimental/freeze_tree.py --name prototype-budget-new
python -B tools/experimental/freeze_budget_helper.py --name budget-helper-new --inputs <frozen-inputs.json>
python -B tools/experimental/focused_checks.py --inputs <frozen-inputs.json> --backend NATIVE --selection budget --property-batch --tree-batch --tree-budget-ms 20 --tree-max-parts 1 --name budget-new-native
python -B tools/experimental/restoration_benchmark.py --reference-root <main-checkout> --runtime <frozen-runtime-jar> --helper-inputs <frozen-helper-inputs.json> --tree-batch --tree-budget-ms 20 --name budget-new-bounded
python -B tools/experimental/restoration_benchmark.py --reference-root <main-checkout> --runtime <frozen-runtime-jar> --helper-inputs <frozen-helper-inputs.json> --tree-batch --tree-budget-ms 0 --name budget-new-unbounded
```

Use Java 21 and new attempt names. The table lists the other focused
configurations. A separate lifecycle run adds `--reload` to the bounded
benchmark command; it is not included in the cold-load means.

The summary for this experiment was generated with:

```text
python -B tools/experimental/summarize_budget.py --attempts budget20-final-01 budget-unbounded-final-01 budget-unbounded-final-02 budget20-final-02 budget20-reload-01
```

[wire-tree-budget-results.json](wire-tree-budget-results.json) records the
measurements, per-run validation, tick and between-tick stall metrics, queue
counts, host CPU sampling, artifact/source hashes, and preserved raw evidence.
The five-second host sampler measures competing process CPU, not clock speed
or thermals. These are fresh-process saved-world loads, not flushed OS caches.
