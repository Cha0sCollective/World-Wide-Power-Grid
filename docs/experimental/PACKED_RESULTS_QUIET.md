# Packed results: quiet-host rerun

Follow-up to the [initial packed-results experiment](PACKED_RESULTS.md), after
the competing game was closed. Runtime implementation remains at `b1f939e`,
`0.2.7-experimental.packed.1`, on `dev/experimental-plain-wire-batching`.
The original measurements and contended attempts remain preserved.

Three fresh measurements completed: batched control, four workers, then two
workers, each on a new copy of the 14,650-node benchmark save. This reverses the
previous two-/four-worker order. Each run retains restoration, 120 seconds of
warmup and a 120-second stable sample, with both verification and detailed
profiling disabled. No test fixture or API example jars are installed in these
timing instances.

## Fresh measurements

| Mode | TPS | Mean tick, ms | p95 tick, ms |
| --- | ---: | ---: | ---: |
| Existing batching, packed off | 19.47 | 51.22 | 60.20 |
| Packed, 2 workers | 16.86 | 59.19 | 67.22 |
| Packed, 4 workers | 16.87 | 59.14 | 67.17 |

Two and four workers are effectively tied: only 0.052 ms/tick separates their
means, less than the variation within either sample. They add 7.97 and 7.92 ms
per tick respectively versus the fresh control, about 15.5% extra elapsed time.
There is no useful two-to-four-worker scaling signal in these measurements.
Neither worker mode improves the existing batching path or reaches 20 TPS.

The fresh control improves on the earlier control's 18.64 TPS, but still falls
short of the existing 19.9 TPS / p95 at or below 50 ms target. Runtime code and
flags for the control are unchanged. Use the fresh control when comparing the
new worker measurements; do not attribute the difference between sessions to
the packed implementation.

The earlier clean packed-serial result remains 16.58 TPS / 60.17 ms mean,
against its contemporary 18.64 TPS / 53.53 ms control. It was not rerun in this
follow-up. Its 6.64 ms packing regression remains a finding of that initial
comparison; the roughly 1 ms difference between that old serial sample and the
new worker samples is not evidence of a threading win across sessions.

## CPU and variability

| Measurement | Control | 2 workers | 4 workers |
| --- | ---: | ---: | ---: |
| Server CPU, ms/tick | 51.03 | 57.22 | 57.00 |
| Server allocation, MiB/tick | 12.21 | 11.88 | 11.87 |
| Process CPU, mean cores | 1.012 | 1.018 | 1.075 |
| GC, ms over stable sample | 70 | 63 | 139 |
| Compilation, ms over stable sample | 868 | 429 | 711 |
| Range of 30-second block means, ms/tick | 51.07–51.48 | 58.75–59.49 | 58.42–59.76 |

The extra wall time includes roughly 6 ms more server CPU per tick. Allocation
does not increase, and the whole-window GC differences are far too small to
explain an 8 ms/tick regression. This is consistent with serial capture and
ordered publication remaining expensive; these counters do not isolate each
capture, copy, scheduling or cache cost. Four workers consume more total process
CPU without a useful elapsed-time improvement over two.

Keep the prototype opt-in. The next useful experiment is reducing serial
capture/publication passes and repeated lookups through a persistent packed
representation, with topology invalidation and the existing callback boundaries.
Measure that serial version before revisiting workers. The current batching
path remains the better performance choice for this benchmark.

## Reused correctness evidence

No runtime rebuild or repeat correctness suite is needed. Before launch, all
431 recorded source files and all five frozen artifacts were compared with
`local/frozen/prototype-03d/inputs.json`. The six preserved Java/native
serial/two-/four-worker checks still match those artifacts: 258 prior required
test passes. These are reused passes, not new test executions. The comparison
is recorded in `local/packed-host/quiet-reuse-01.json`.

Runtime SHA-256:
`80eabf8dc7c30e1bcd435988efacf9a6bfcae7628960b99a45eff63df319e314`.
The native binaries are unchanged. The existing full-yard four-worker bitwise
verification also remains applicable to this identical runtime.

## Host and isolation

The preflight CPU observation found the competing game absent and only light
background activity. Five-second CPU spot checks during all three stable phases
also found the game absent. The busiest other process used 0.092, 0.053 and
0.111 CPU cores respectively during the control/four-/two-worker checks. These
are spot observations, not a claim of continuous exclusive use of the machine.
The High performance power plan and existing server-thread
affinity are unchanged. Workers use ordinary OS scheduling as in the first
prototype; no worker-affinity tuning is introduced.

Only the experimental worktree and new copied-save instances are used. The main
checkout, source benchmark save, frozen inputs and older evidence are preserved.
No full acceptance or graphical suite is run.

## Validation and evidence

All three new runs passed their focused validation: 14,650 nodes, 20,637 edges,
14,925 matrix rows, one native solver network, 953 chunks and 1,050 storage
devices. Every stable tick retained all 16 solves with no matrix rebuilds or
independent CEE solves. There were no rejected solves; topology and row identities
remained unchanged within each run. Every server shut down and saved normally,
and the original reference save remained unchanged.

The worker logs prove execution of the requested modes: two workers dispatched
215,443 batches and completed 430,886 ranges; four workers dispatched 212,344
batches and completed 849,376 ranges. Those counters cover each entire launch,
including restoration and warmup, rather than just the stable sample.

The [quiet result index](packed-quiet-results.json) records exact results,
source/artifact comparisons, reused correctness evidence, block means and hashes
of the raw logs, tick data and host observations. The earlier
[result index](packed-results.json) remains untouched, including the contended
attempts. These are short focused comparisons, one new sample per mode, not
sustained capacity or release acceptance.

| Attempt | Stable ticks | Purpose |
| --- | ---: | --- |
| `packed-quiet-control-01` | 2,334 | Fresh batching control |
| `packed-quiet-workers-4-01` | 2,021 | Four-worker result |
| `packed-quiet-workers-2-01` | 2,020 | Two-worker result |

To reproduce from the experimental worktree, choose unused attempt/output names:

```text
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name quiet-control-new --runtime local/frozen/prototype-03d/wwpg-0.2.7-experimental.packed.1.jar --batch --property-batch
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name quiet-four-new --runtime local/frozen/prototype-03d/wwpg-0.2.7-experimental.packed.1.jar --batch --property-batch --packed --workers 4
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name quiet-two-new --runtime local/frozen/prototype-03d/wwpg-0.2.7-experimental.packed.1.jar --batch --property-batch --packed --workers 2
python tools/experimental/summarize_packed.py --attempts packed-quiet-control-01 packed-quiet-workers-4-01 packed-quiet-workers-2-01 --reuse-evidence local/packed-host/quiet-reuse-01.json --output local/quiet-summary-new.json
```
