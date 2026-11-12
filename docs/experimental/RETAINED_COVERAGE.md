# Retaining completed wire-tree regions within a drain

Experimental investigation on November 9, 2026, following
[bounded restoration](BOUNDED_WIRE_TREE.md). The candidate is
`0.2.7-experimental.wiretree.5`, frozen in `local/frozen/prototype-06a`.
It is not a release input or a demonstrated improvement.
The candidate source is preserved at `86229ab`; the following retirement restores
the working runtime to `wiretree.4`, retaining the reports and benchmark tools.

## Decision and measured result

Retire the runtime change. Retaining multiple completed regions passed focused
correctness checks, but did not reduce loading time or endpoint visits on the
14,650-node yard. Two enabled samples averaged **62.24 seconds**, versus
**58.30 seconds** for two controls: **6.8% longer**. Both enabled samples took
longer than both controls, although these small samples do not establish
statistical significance.

| Execution order | Retained coverage | Restoration seconds | Worst tick seconds | Endpoint visits |
| --- | --- | ---: | ---: | ---: |
| `coverage-on-01` | On | 64.90 | 3.825 | 20,659,787 |
| `coverage-off-01` | Off | 59.11 | 3.437 | 20,263,157 |
| `coverage-off-02` | Off | 57.49 | 3.268 | 18,744,154 |
| `coverage-on-02` | On | 59.59 | 3.246 | 21,159,596 |

All four used the same candidate jar, queue-aware helper, plain/property wire
batching, 20 ms cooperative budget and 32-wire cap. Only the new coverage switch
changed. Each process used a fresh copied save; all recovered 14,650 nodes,
20,637 edges, 14,925 matrix rows, one native network and 953 chunks. All 1,050
pre-solve storage values were verified. The final 100 distinct healthy ticks
retained 16 substeps, with no independent CEE or rejected shared solves.
All queues emptied, no queued roots were discarded, saves/shutdowns completed,
and source/reference inventories stayed unchanged.

These are server restoration timers, including electrical settling. They do
not include rendering or JVM startup. They are not sustained-TPS measurements.
The historical 92.71-second bounded mean is from another session and must not
be used as the control for this change. Five-second CPU observations and their
limitations are retained in the machine-readable evidence; no disk-cache
flushes, thermal control or CPU-frequency control were performed.

## Hypothesis and implementation

The original queue remembers only the last fully resolved region. A queue that
alternates between separate completed regions can therefore retrace them.
The candidate unions their coverage within one synchronous drain. It clears
earlier coverage after a node migration, an unresolved connection attempt,
an unknown endpoint callback, a stale owner or the native final connection
attempt. No coverage survives a drain/tick, and no partially traversed graph,
save field, solver setting or worker is introduced.

The native traversal reports observed changes through a per-world revision.
Only fully resolved regions of exact block endpoints qualify for coverage.
The opt-in switch is `-Dwwpg.experimental.wireTreeRetainCoverage=true`; tree
batching must also be enabled. The original traversal and leaf rules remain.

The small fixture demonstrated the intended mechanism: the two PG/CEE terminal
rings each needed only one traversal in an unbounded drain. The full yard's
visits remained about 19–21 million in both modes. Thus the repeated completed
region case was not the dominant remaining cost in this comparison. The
candidate's bookkeeping does not earn a place in the working runtime on this
evidence. Its source, frozen artifacts and negative results are preserved.

## Focused validation

The candidate passed **12 unit checks** and **110 GameTests**:

- Native tree/loading/pause/contactor/recovery selection: 36.
- Native bounded selection, forcing one atomic wire per tick: 38.
- Java tree/loading/pause/contactor/recovery selection: 36.

Units include alternating regions across separate drains, mutation and final
connection invalidation, stale owners, deadline overshoot, and 200 randomized
graph comparisons. Integration checks retain saved identity, cold closed
loops, reverse order, missing parts, electrical pauses, storage and recovery.
No full headless or graphical suite was run. The four full-yard cold loads
passed; no additional actual unload/reload trial was run for this rejected
candidate.

## Fresh profile of the preceding runtime

Before building the candidate, `goal-budget-profile-01` profiled the unchanged
`wiretree.4` runtime with the same bounded settings and helper. It passed in
59.48 seconds, but instrumentation and cross-process variation make that a
diagnostic result, not a clean speed claim. Of 4,584 server Java execution
samples, **37.3%** included wire-tree traversal. Separately, **25.5%** included
the deferred wire queue and **12.3%** included node-holder initialization. These are sampled
stacks, not elapsed-time fractions. Inclusive percentages overlap.

Native terminal snapshots accounted for 6.8% of these samples, and CEE
synchronization for 9.3%. The existing stable-tick profiles also identify
preparation, terminal access and wire world queries. Removing actual repeated
traversal and preparation remains a stronger direction than extending this
particular completed-region cache.

## Evidence

- [Machine-readable measurements and hashes](retained-coverage-results.json).
- Candidate runtime SHA-256:
  `6602c3d51c4ad3a3db0135482fa0ca24de97a69b800e79c92e39c1476961ecc9`.
- Candidate fixture SHA-256:
  `660199f1e7602315490d207b41e5bcebd66328d9efe2c88724f64dfa5f4807af`.
- Helper SHA-256:
  `3655eb8f3c129e8c7d6801361f66551e9ee97e12fa5d0c56bfb5354f010ce9bf`.

The helper and its seven loading-gate checks are reused, unchanged, from the
bounded experiment; they are not new test passes. Native solver binaries are
unchanged. The result generator verifies frozen inputs, captured checks and
original/reference-save integrity before writing a new, exclusive report.

```text
python -B tools/experimental/summarize_coverage.py
python -B tools/experimental/restoration_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-06a/wwpg-0.2.7-experimental.wiretree.5.jar --helper-inputs local/frozen/budget-helper-01/inputs.json --tree-batch --tree-budget-ms 20 --tree-retain-coverage --name coverage-new
```

Use new attempt names. Omit `--tree-retain-coverage` for the same-jar control.
Regenerate the report from the candidate source snapshot, before its retirement;
the generator deliberately refuses mismatched source hashes or existing output.
