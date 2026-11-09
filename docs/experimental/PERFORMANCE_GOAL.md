# Continuing performance investigation

Active objective, November 9, 2026: sustain 20 TPS on the 14,650-node benchmark
and reach a restored, rendered benchmark scene in under one minute, preserving
all mod functionality. The user confirmed that the loading timer starts when
selecting the world, not when launching Minecraft. The confirmed client view
is windowed 1920×1080 at a 12-chunk render distance.

Work stays on `dev/experimental-plain-wire-batching` in the isolated `local/x27w`
worktree. Original saves, release artifacts and normal development remain
untouched. Only focused checks and fresh benchmark-save copies are authorized;
no full headless or graphical acceptance suite is implied by this investigation.

## Current evidence and open requirements

- The current candidate is `0.2.7-experimental.wiretree.8`, frozen at
  `local/frozen/prototype-09c/inputs.json` (runtime SHA-256
  `7a8cfa0973a73ac490e7cb1f1e1d8b4eff6bdd1fc9933d067279852e4e8f1204`).
  Its opt-in [CEE state geometry reuse](CEE_STATE_GEOMETRY.md) measured
  **20.027 / 20.031 TPS**, **49.077 / 48.616 ms mean** and **55.781 / 55.303 ms
  p95**, versus the same-jar disabled control's **19.480 TPS**, **51.201 ms mean**
  and **58.675 ms p95**. Mean tick time was 4.60% lower across the enabled samples;
  total allocation was higher, so this is not an allocation-reduction claim.
  Complete client readiness remained below one minute at **39.147 seconds**,
  with a worst loading tick of **2.862 seconds**. All 99 focused checks and an
  actual full-yard unload/reload passed; initial/reload headless restoration
  took **28.639 / 17.069 seconds**. The cache covers only reviewed state-only
  built-ins, preserving original callbacks for dynamic/custom geometry.
  The p95 gate and continuous-streaming requirement remain open.
  [Evidence](cee-geometry-results-20261109.json).
- The subsequent [terminal bookkeeping prototype](TERMINAL_BOOKKEEPING.md),
  `wiretree.9`, passed 96 focused checks but is retired. Two enabled samples
  averaged **48.823 ms**, versus **48.743 ms** for the same-jar disabled control;
  p95 was **55.180 / 56.795 ms** versus **55.320 ms**. Allocation decreased by
  about 0.898 MiB/tick, without a repeatable tick-time gain. Candidate source is
  preserved at `7247ef9`. The working source inventory and rebuilt runtime and
  fixture jars now match `prototype-09c` exactly. The
  [retirement comparison](terminal-bookkeeping-retirement-20261109.json)
  permits reuse of that runtime's named prior evidence; no new client or
  actual reload trial was run for the rejected candidate.
- The subsequent [terminal-key arithmetic prototype](TERMINAL_KEY_LOOKUP.md),
  `wiretree.10`, also failed to demonstrate a tick-time gain. Enabled/disabled
  means were **49.882 / 49.855 ms**, with **57.523 / 57.421 ms p95**, despite
  about 1.230 MiB/tick less allocation. Its corrected build passed 102 focused
  checks and both full-yard steady observations. A superseded warmup was
  interrupted for a compatibility correction and contributes no timing result.
  Candidate source is preserved at `6949dff`; it is retired and the working
  source and rebuilt jars again match `prototype-09c` exactly.
  [Retirement proof](terminal-keys-retirement-20261109.json).
- The preceding `0.2.7-experimental.wiretree.7` candidate is frozen at
  `local/frozen/prototype-08a/inputs.json` (runtime SHA-256
  `31efbb0099531a7a8db7aa0f52deb80b040080bada825dd33bc339560b85c7a1`).
  Its opt-in [bounded chunk-task window](CHUNK_TASK_WINDOW.md) reached complete
  restored/rendered readiness in **40.408 and 39.298 seconds**, versus
  **108.906 seconds** with the same jar and window disabled. All three runs
  passed the full-yard and complete-view checks at 1920×1080 / 12 chunks.
  This meets the loading target in two focused samples. Its subsequent steady
  observation averaged **20.040 TPS**, **48.716 ms mean** and **56.027 ms p95**,
  so the p95 consistency gate remains unmet. Actual full-yard unload/reload
  passed, with initial/reload times of **29.604/15.849 seconds**. Behavior during
  continuous streaming remains open. Worst loading ticks
  remain approximately three seconds. Nineteen unit and 98 focused regression
  checks passed; GameTestServer bypasses extra dispatch, which the real client
  runs exercise. [Evidence](task-window-results-20261109.json).
- The preceding `0.2.7-experimental.wiretree.6` candidate is frozen at
  `local/frozen/prototype-07a/inputs.json` (runtime SHA-256
  `522c4cb3c394d7c2f80fd2f73fc637cddab5401540128bfe881d4353db1959bb`).
  [Unresolved-wire priority](UNRESOLVED_PRIORITY.md) reduces mean headless
  restoration from **92.08 to 53.51 seconds** in the same-jar on/off/off/on
  comparison, with **56.3% fewer endpoint visits**. Worst loading ticks remain
  approximately three seconds. Fifteen units, 134 focused integration checks
  and an actual full-yard unload/reload passed. The priority flag is opt-in;
  the old FIFO behavior is preserved with it disabled.
- The priority runtime's focused steady sample averaged **20.019 TPS**,
  **48.889 ms mean**, **55.453 ms p95**, **60.356 ms p99** and **65.931 ms
  maximum** over 2,399 ticks after two minutes of warmup. All graph, storage and
  16-substep checks passed. This meets the average in one sample, but still
  misses the p95 50 ms gate and does not establish consistency or a causal
  steady-state speedup. [Evidence](priority-tps-results-20261109.json).
- [Retained completed-region coverage](RETAINED_COVERAGE.md) passed 12 units and
  110 integration checks but did not improve full-yard loading. Its candidate
  is preserved at `86229ab` and retired from the working runtime.
- `goal-tps-control-01`, using the unchanged `wiretree.4` runtime, passed its
  full-yard correctness gates and 120-second stable window after 120 seconds
  of warmup. It measured **19.861 TPS**, **50.134 ms mean**, **58.818 ms p95**,
  **64.806 ms p99** and **257.693 ms maximum** over 2,380 ticks. This misses the
  existing practical gate of at least 19.9 TPS and p95 no greater than 50 ms.
- That run's restoration took **115.37 seconds**, including **61.21 seconds**
  draining queued work after chunk readiness. The same preceding runtime's
  fresh diagnostic profile completed in 59.48 seconds. Neither is evidence of
  a consistently sub-minute load. Ordering/JIT/host and graph-work variation
  remain to be separated; a cause has not yet been established.
- The first [focused client observation](CLIENT_LOADING.md) on `wiretree.4` was:
  **121.474 seconds** from world opening to restored visible-scene readiness
  at 1920×1080 / 12 chunks. The first frame appeared at 21.699 seconds and full
  electrical readiness at 118.602 seconds. All circuit, rendering-readiness
  and normal-save checks passed, but the load target was missed. It does not
  establish sustained client TPS or complete graphical acceptance.
- The new priority client observation reached restored/rendered readiness in
  **104.81 seconds**, with electrical readiness at **63.08 seconds**, at the
  same confirmed settings. Delivery of the full 609-chunk client view continued
  for roughly 40 seconds after electrical readiness; the rendering compilation
  queue was usually empty. This misses the complete-loading goal and identifies
  a separate delivery/receipt bottleneck to investigate. The client observer
  is byte-identical to the baseline's. [Evidence](client-priority-results-20261109.json).
- The bounded implementation still holds all electrical circuits in an
  affected dimension while its wire queue is pending. Preserving behavior
  during continuous streaming and narrowing that pause remain open concerns.
  The existing finite-reload fixture deliberately holds independent circuits;
  it does not prove their continued operation during streaming. See the
  [source review and required focused evidence](SCOPED_RESTORATION_INVESTIGATION.md).

The user additionally requested manual assignment of benchmark runs to an
unused CPU group. [Whole-process placement](CPU_PLACEMENT.md) now restricts the
owned JVM to logical CPUs 12–23 (`fff000`), selected after a quiet-core
observation, with continuous readback and per-core load evidence. The completed
`goal-tps-pinned-01` run measured **19.997 TPS**, **49.879 ms mean**, **57.569 ms
p95**, **62.530 ms p99**, and **74.245 ms maximum**, over 2,396 stable ticks.
Server restoration took **54.26 seconds**. This remains one headless sample,
misses the p95 gate, and does not measure rendering. Both the process and native
server-start observations confirm `fff000`; all 305 process readbacks matched.

[Baseline evidence](goal-baselines-20261109.json) preserves both protocols.
Do not attribute their different loading times to a code optimization: the
gameplay jar is identical, while process placement, JVM worker availability
and the explicit helper overlay differ. The retained-coverage candidate remains
retired. At that checkpoint all 439 recorded source hashes matched the frozen
`wiretree.4` inputs. The priority prototype now has a separate frozen source
inventory. Full-yard graph/storage/substep checks passed in both earlier steady
samples.

## Next implementation targets

1. The [server-thread subset comparison](CPU_PLACEMENT.md#server-thread-subset-comparison)
   is complete. CPU 14 produced 19.121 TPS / 61.127 ms p95, versus 19.801 TPS /
   58.583 ms p95 for the matching group control. Both used the same gameplay
   jar, helper and whole-process group. The narrower mask is not adopted;
   reducing observed migrations did not improve this sample. Their loads
   took 97.48 and 90.67 seconds, so the prior 54-second load is not consistent.
2. The unresolved-part diagnosis and priority prototype are complete. A small
   number of delayed attachments prevented fully-resolved coverage and caused
   repeated traversal. Prioritizing them cuts post-chunk recovery from 40–46
   seconds to about five. The [client delivery diagnosis](CLIENT_DELIVERY.md)
   showed that remaining view chunks were becoming ready on the server,
   rather than waiting in a send queue. Task instrumentation then found about
   50 ms median waits for short lighting/future callbacks. The bounded extra
   dispatch window now brings clean complete-client loading below one minute
   in two samples. Its actual unload/reload and steady-TPS checks are complete;
   the p95 tick target is still missed. The separate node-holder initialization
   path and largest stalls also remain.
3. Reviewed CEE geometry reuse is implemented and has a matched on/off/on
   comparison, a client load and an actual reload. The subsequent
   [steady profile](STEADY_PROFILE_GEOMETRY.md) measured 7.951 ms CEE preparation
   and 4.778 ms terminal preparation in its weighted samples. Per-pass function
   reuse, private frame-map storage and terminal-key arithmetic were measured
   and retired after failing to demonstrate a repeatable speedup. Before
   another small tick optimization, collect a CPU profile with detailed phase
   and substep timers disabled. The existing helper starts JFR inside its
   sampled substep path; a new mode needs explicit recording boundaries, flags,
   artifact hashes and shutdown evidence. The diagnostic run recorded 16.051
   seconds of compilation versus about 1.1–1.4 seconds in clean observations,
   so its cost distribution may be distorted. Stable-description validation
   remains a candidate, not a proven improvement; retain fresh validation and
   callback behavior. Preserve dynamic, custom, moving, unloaded and
   replaced-owner behavior, all 16 electrical substeps, accuracy, storage,
   heating and device callbacks. The prior packed worker prototype stays retired.
4. Narrow restoration pauses to the affected electrical components, following
   the [scoped-restoration investigation](SCOPED_RESTORATION_INVESTIGATION.md).
   Prove that independent circuits continue all 16 substeps during ongoing
   restoration arrivals, while affected storage and heat remain held until
   their topology is ready. Account for unresolved saved chains, couplers and
   topology changes before attempting a full-yard comparison.
5. Verify both targets on subsequent improvements. The goal remains active until
   consistency and functionality preservation have supporting evidence.
   Headless loads below one minute do not establish complete client readiness.

The headless goal runs retain the queue-gate logic from
`local/frozen/budget-helper-01`, with explicitly recorded affinity overlays
where applicable. The client observer additionally adapts the helper's world
path and publishes completion; its electrical checks remain unchanged. All raw
logs, artifact manifests, topology and storage assertions are under
`local/benchmarks/<attempt>`. Passing focused checks are evidence for their
named scope, not proof of all mod functionality.
