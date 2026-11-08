# Packed result-processing experiment

This is the isolated `dev/experimental-plain-wire-batching` experiment on top
of the 0.2.7 plain/property-wire batching prototype. It is not a normal
development or release lane. Packed serial is slower in the clean comparison.
The two- and four-worker samples encountered an unrelated game running on the
host, so a clean worker-count comparison remains unresolved.

## Performance comparison

All numbers below are fresh 120-second stable samples after 120 seconds of
warmup and restoration, on separate copies of the same yard. No verification,
detailed timers, JFR or fixture jars are present in these timing runs.

| Mode | TPS | Mean tick, ms | p95 tick, ms |
| --- | ---: | ---: | ---: |
| Existing batching, packed off | 18.64 | 53.53 | 66.73 |
| Packed serial | 16.58 | 60.17 | 72.15 |
| Packed, 2 workers — host contention | 13.77 | 72.44 | 95.95 |
| Packed, 4 workers — host contention | 12.78 | 78.10 | 96.70 |

The first two-worker run measured 13.77 TPS / 72.44 ms mean / 95.95 ms p95,
but is **not comparable to the clean control**: an unrelated game started
during its stable sample and was subsequently observed consuming roughly three
CPU cores. Its successive 30-second block means were 63.68, 70.39, 82.61 and
75.75 ms. The original run is preserved as a correct but contended performance
sample, not silently discarded or relabeled. The clean control's block means
were 52.97–54.14 ms, and packed serial's were 59.16–61.11 ms. A second host
observation during the four-worker stable phase confirmed the unrelated game
was still using roughly three CPU cores. Neither worker sample establishes a
clean speedup, regression magnitude or reliable two-versus-four ranking.

The serial prototype adds 6.64 ms per tick in this comparison. Packing alone
has not reduced the cost: the snapshot and ordered publication passes still
walk the live objects, in addition to the primitive kernel. The new control is
faster than the previous profiling session's 17.17 TPS; it has packed processing
disabled, so that difference is not a packed-results improvement.

Server CPU time increased from 53.25 to 59.89 ms/tick in the clean serial
comparison; allocation was 11.84 versus 11.92 MiB/tick. Reported stable GC time
was 71 versus 66 ms for the entire sample. The clean regression is therefore
consistent with extra processing, not a GC spike. The observed worker wall/CPU
differences are not used to assign a scheduling or cache-traffic cost under the
changed host load.

No measured run met the existing practical target of at least 19.9 TPS and p95
at or below 50 ms. There is one complete timing sample per mode, not a capacity
certification. The [result index](packed-results.json) retains exact values,
30-second block means, CPU/allocation/compilation data, verification counters,
artifact/source hashes and paths to the raw local evidence.

## What this supports next

Keep this implementation opt-in. The useful clean result is that a second
capture/compute/publication pass over the same objects costs more than the
existing batching path. Before broadening threading, test a persistent packed
representation that reduces those serial passes and repeated row/property
lookups, with explicit topology invalidation and the same callback boundaries.
Continue to keep native solves, shared history and gameplay on the server.

A clean two-/four-worker comparison still requires the competing game to be
paused or closed. Rerun those modes and a fresh control on copied saves once
the host is quiet, retaining these attempts. The running game was observed
only; no unrelated application or system setting was changed.

## Implementation

`0.2.7-experimental.packed.1` keeps the existing batching flags and adds:

```text
-Dwwpg.experimental.packedResults=true
-Dwwpg.experimental.packedResults.workers=0
-Dwwpg.experimental.packedResults.verify=true
```

Workers `0`, `2` and `4` use the same primitive-array kernels. All new options
default off/zero. Verification is for correctness runs only. Timing runs disable
both power and packed verification. The same jar with packed processing disabled
provides the batched control.

The three staged operations are per-wire power, CEE branch current plus signed
sum/squared-current accumulation, and native terminal voltage copying/line
interpolation. Each invocation captures fresh numeric inputs on the server,
computes disjoint output ranges, then publishes in the original order. Reusable
arrays retain capacity, not samples or row mappings across callback boundaries.
Wire power retains the previous plan's node/owner invalidation checks.

The prototype deliberately keeps three separate publication boundaries. API
callbacks between the PG hook and bridge phase, and CEE history callbacks before
terminal sampling, prevent assuming one shared snapshot remains valid throughout.
Solver calls, live node/property reads, acceptance checks, transformer winding
combination, dynamic history, world queries and gameplay publication remain on the
server thread. The native binaries are byte-identical to the previous prototype.

CEE packing requires accepted, clean networks, sampled endpoints and the bridge's
existing built-in property whitelist, with no adapters. Unsupported/custom cases
use the existing scalar path. Non-finite current results return to the original
rejection sequence before any packed branch result is published. Terminal packing
requires exact native node/line classes and retains availability and sample-order
checks. Workers never receive worlds, devices, nodes, properties or native buffers.

A persistent bounded pool dispatches one range per worker. Batches below 1,024
items run serially; focused fixtures lower that threshold to one so they exercise
actual workers. The server waits for every range before publishing or returning
an error. An interrupted dispatch drains all workers before restoring the
interrupt flag and throwing. Shutdown joins the pool. Every dispatch has its own
generation; no work survives a call, unload, callback or buffer reuse.

## Evidence and scope

The frozen final inputs, source hashes and jar-entry comparison are under
`local/frozen/prototype-03d`. All benchmark attempts use fresh copies of the same
14,650-node reference save. The original save and earlier evidence are preserved.
No full suite, graphical acceptance, release or upstream repository is changed.

All five copied-yard runs retained 14,650 nodes, 20,637 edges, 14,925 matrix
rows, one native solver network, 953 chunks and 1,050 storage devices. Each
stable tick executed 16 solves, without matrix rebuilds or independent CEE
solves. Every run saved normally and passed the unchanged-topology and original
reference checks. Timing instances contain no fixture or API example mods.

All six final fixture runs passed: 43 tests each in serial/two/four-worker modes
on both native and Java backends, 258 required test passes. The full copied-yard
four-worker verification also passed: 4,158,452,988 raw-bit comparisons, zero
rejected solves, all 16 substeps and unchanged topology/reference save. Its timing
is excluded from performance comparisons because verification adds substantial
work. It executed 160,414 worker dispatches and drained all 641,656 ranges.

The standalone check passed 546,870 raw-bit comparisons and 150,804 rejected-NaN
classification checks. It compares finite values, infinities and signed zero bit for
bit. Deliberately invalid inputs compare NaN classification because Java does not
specify arithmetic NaN sign/payload. The first standalone attempt failed that
overstrict NaN-payload expectation; its log is retained at
`local/packed-unit/result-01.log`. The corrected check did not change runtime
arithmetic. Accepted in-game contributions use raw-bit
comparisons against the scalar calculations. Failure, interruption, generation
reuse, wrong-thread ownership and worker shutdown are checked separately.

Early attempts are retained as failures: the first build needed an absolute CEE
jar path; fixture 03a lacked the new namespace's empty structure; 03b lacked two
required API example jars; 03c lacked registration of the reused terminal probe
capability. Runtime hash `80eabf8dc7c30e1bcd435988efacf9a6bfcae7628960b99a45eff63df319e314`
is unchanged across those fixture corrections. None is counted as a passing run.

## Reproduce

Use Java 21 and the experimental worktree. Always choose fresh attempt names.

```text
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-03d/inputs.json --backend NATIVE --selection packed --name packed-focused-new --property-batch --packed --workers 4
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name packed-verify-new --runtime local/frozen/prototype-03d/wwpg-0.2.7-experimental.packed.1.jar --batch --property-batch --packed --workers 4 --verify-batch --verify-packed
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name packed-clean-new --runtime local/frozen/prototype-03d/wwpg-0.2.7-experimental.packed.1.jar --batch --property-batch --packed --workers 4
python tools/experimental/summarize_packed.py --attempts packed-verify-4-01 packed-control-01 packed-serial-01 packed-workers-2-01 packed-workers-4-01 --contended-attempts packed-workers-2-01 packed-workers-4-01 --output local/packed-summary-new.json
```

Use workers `0` or `2` for the other packed variants. Omit `--packed --workers 4`
for the same-jar batched control. Keep the existing 120-second warmup,
120-second stable sample, 16 substeps, frozen dependencies, heap and server-thread
affinity. Workers use ordinary OS scheduling; this experiment does not add worker
affinity tuning. The stable results are short focused comparisons, not capacity
certification.
