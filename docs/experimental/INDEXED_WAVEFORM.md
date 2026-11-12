# Indexed terminal waveform traversal

Experimental development snapshot, November 9, 2026. This trial builds on
`wiretree.11` and its scoped-restoration checkpoint `e424f02`, in the isolated
`dev/experimental-plain-wire-batching` worktree. Original saves and normal
development remain untouched. The candidate is `0.2.7-experimental.wiretree.12`.
It is now retired; the working runtime is restored to `wiretree.11`.

## Change and rationale

The [CPU profile without detailed timers](CPU_PROFILE_WITHOUT_TIMERS.md)
identified 652 server-thread Java samples in terminal waveform sampling;
538 ended in `ArrayList$Itr.next`. That attribution identifies a candidate,
not removable elapsed time: inlining can place other work under an iterator.

The opt-in `wwpg.experimental.indexedWaveform` switch replaces the three
enhanced-for traversals in `NativeApiReadings.after` with indexed traversal
of the existing private lists. Those lists are populated during preparation
and read during sampling. The disabled path retains the original traversal.
No sample buffer is copied or repacked, and no worker threads are introduced.

Every current-node ownership check, voltage read, acceptance decision and
sample remains in the same order. Mid-line interpolation still waits for both
endpoint owners to reach the same substep; same-owner lines advance once.
Late invalidation, incomplete-waveform rejection, fresh preparation, public
measurement checks and all 16 substeps retain their existing behavior.

The enabled branch counts passes and direct/middle frame visits for execution
evidence, without per-frame counters or timers. It reports those counters at
server shutdown. The experimental runners reject missing, duplicate or
disabled execution reports and distinguish the switch when matching runs.

## Frozen inputs and focused checks

`local/frozen/prototype-13a/inputs.json` records 450 source/build entries.
Only `NativeApiReadings.java` and `gradle.properties` differ from `prototype-12g`.
Runtime SHA-256:
`363348d02d041efd50f9633471f6fe6d432bf29f6766e8cb9489ffa0578d60f0`.
Fixture SHA-256:
`665cc2bfccce58436edb022344f86dfcb61e24376431a6f5901e0f8af80b8973`.
CEE 1.1.4, PG 0.6.2, NeoForge 21.1.231 and native library bytes remain fixed.

The candidate passes 32 focused loading, ownership, unload and solver checks
with native sampling enabled, the same 32 on the Java solver, and 32 with the
new switch disabled. Both enabled lanes visit direct and interpolated frames.
The native sampling fixture covers one/two endpoint owners, reverse callback
order, a missing final endpoint sample, late rejection, independent readings
and next-tick recovery. Nine additional scoped-restoration checks pass with
indexed sampling enabled. This is 105 fixture executions, not 105 unique cases
or a full acceptance run.

Three waveform unit tests and four evidence guards also pass. Unit XML,
runner snapshots, logs and immutable jars are preserved under `local/checks`
and `local/frozen`. No full headless or graphical acceptance was requested.

## Measurement protocol

The finite headless comparison uses a fresh copy of the same 14,650-node save
for each launch. Both wire batches, unresolved-wire priority, the 20 ms / 32-part
restoration queue, 2 ms chunk-task window, CEE state geometry and scoped
restoration stay enabled. The same jar and helper run on CPUs 12–23 (`fff000`)
after a quiet-group observation, with continuous affinity readbacks and host
metrics. Each launch has 120 seconds of warmup and a nominal 120-second sample.
Detailed timers and JFR stay disabled. Only indexed traversal varies.

## Results and decision

Both runs completed normally, preserving 14,650 nodes, 20,637 edges, 14,925
matrix rows, one native network, 1,050 storage devices and all 16 substeps.
Stable topology and original/reference save hashes passed. The enabled run
recorded 936,577,600 direct frame visits across warmup and measurement; this
yard had no mid-line frame visits. The focused fixtures supply that coverage.

| Measurement | Indexed enabled | Same-jar disabled |
| --- | ---: | ---: |
| Steady ticks | 2,396 | 2,399 |
| Observed TPS | 19.992 | 20.017 |
| Mean tick | 49.885 ms | 48.792 ms |
| p95 / p99 tick | 57.598 / 62.344 ms | 55.701 / 61.067 ms |
| Maximum tick | 77.560 ms | 80.984 ms |
| Ticks over 50 ms | 44.03% | 41.23% |
| Server CPU per tick | 49.790 ms | 48.490 ms |
| Server allocation per tick | 11.491 MiB | 13.077 MiB |
| Compilation during sample | 1.218 s | 1.289 s |
| GC time | 66 ms | 85 ms |
| Initial headless restoration | 33.688 s | 32.517 s |

Mean tick time measured 2.24% higher with indexed traversal, and p95 was
1.897 ms higher. This single pair does not establish a repeatable slowdown;
it provides no elapsed-time benefit to justify retaining the extra path.
Lower allocation did not translate into faster ticks. Both runs miss the
50 ms p95 consistency gate. Captured competing-process CPU averaged 0.266
and 0.274 cores; no competing game was recorded. These observations do not
control CPU frequency or thermals.

The indexed traversal is retired, with its candidate preserved at `696734b`.
The working runtime is restored to the preceding `wiretree.11`. No client-load
or actual-unload/reload run was added for this unsuccessful candidate. Its
headless startup timings do not establish restored/rendered client readiness.
The [combined audit](indexed-waveform-results-20261109.json) preserves all
focused and benchmark evidence, source hashes, execution counters and runner
snapshots. The active performance goal remains open.

## Retirement verification

The offline `jar gameTestJar -x test` rebuild passes. All 450 source/build
hashes match `prototype-12g`, and both rebuilt artifacts are byte-identical:

| Artifact | Restored SHA-256 |
| --- | --- |
| Runtime | `fa5a133d685311485dde7441db4ac55db87e54b236bf7fb9ece41da27bdd7ba9` |
| Fixtures | `24d2d70ca4b8895d71e1d74c9fc49b23a1873507f4f1c10627f2443d1fc51636` |

The [retirement audit](indexed-waveform-retirement-20261109.json) verifies 216
hashed references from the retained scoped-restoration evidence and 529 from
this candidate's evidence. Changed historical source/tool bytes are verified
from their preserved Git commits. The retained 126 fixture executions and
client/reload/steady observations are reused, with these explicit artifact
comparisons; no old run is labeled as a fresh pass. Main development remains
tracked-clean. All copied-save benchmark processes have exited normally.

## Reproducing the archived candidate

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-13a/wwpg-0.2.7-experimental.wiretree.12.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --indexed-waveform --name waveform-new
```

Use fresh attempt names; omit `--indexed-waveform` for the same-jar control.
