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

- The current experimental prototype is `0.2.7-experimental.wiretree.11`,
  frozen at `local/frozen/prototype-12g/inputs.json`, with opt-in
  [scoped restoration](SCOPED_RESTORATION.md). Independent circuits now continue
  all 16 substeps during repeated saved arrivals in the dedicated fixtures;
  disconnected circuits can split away and resume, while affected storage and
  heat remain held. Known CEE/API internal owners, unresolved saved branches,
  replacement identities and cross-world split controllers are covered. The
  final artifact passes 126 focused fixture executions across enabled/disabled
  lanes, with 17 preserved unchanged unit tests and five evidence guards.
  Same-jar client loading is **44.399 seconds enabled / 39.302 disabled** at the
  confirmed settings: about five seconds slower for the narrower pause, while
  both remain below one minute. Steady results are **20.030 / 20.043 TPS**,
  **48.319 / 48.965 ms mean**, **55.104 / 55.425 ms p95**; this single pair shows
  no steady regression and does not establish a speedup. Actual initial/reload
  headless restoration is **32.996 / 20.222 seconds**. Scope discovery is outside
  the wire queue's 20 ms budget, and unknown physical endpoints retain
  conservative fallback. Consistent TPS, largest stalls and broader gameplay
  coverage remain open. [Evidence](scoped-restoration-results-20261109.json).
- The preceding retained performance baseline is `0.2.7-experimental.wiretree.8`, frozen at
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
- A subsequent [CPU profile with detailed timers disabled](CPU_PROFILE_WITHOUT_TIMERS.md)
  completed on the unchanged runtime/helper: **20.038 TPS**, **49.725 ms mean**,
  **56.724 ms p95**, with 1.496 seconds of compilation during the sample.
  Recording started in warmup; only the explicitly bounded steady interval is
  analyzed. Its 8,691 Java execution samples again identify CEE/terminal
  preparation, waveform iteration, wire calculations and entity updates.
  This is diagnostic evidence, not a new speedup or client-load result.
- The subsequent [indexed waveform trial](INDEXED_WAVEFORM.md), `wiretree.12`,
  passes 105 focused fixture executions, three units and four evidence guards.
  Its same-jar pair measured **49.885 / 48.792 ms mean** and
  **57.598 / 55.701 ms p95** enabled/disabled, with **19.992 / 20.017 TPS**.
  Allocation was lower by about 1.586 MiB/tick, without an elapsed-time benefit.
  The traversal change is retired, with the candidate preserved at `696734b`.
  All 450 source/build hashes and both rebuilt jars again match `prototype-12g`.
  Its named scoped-restoration evidence is reused after an explicit
  [retirement comparison](indexed-waveform-retirement-20261109.json).
  Neither run meets the consistency gate. No new client or actual reload
  observation was run for this candidate.
  [Evidence](indexed-waveform-results-20261109.json).
- A [frame-location follow-up](CPU_FRAME_SITES.md) on the unchanged `wiretree.11`
  runtime completed with **20.031 TPS**, **49.341 ms mean** and **56.623 ms p95**.
  It adds verified compiled-code debug metadata, retaining detailed timers off.
  All 8,576 Java and 188 native server CPU samples match the separately audited
  filtered recording. Broad hot scopes remain similar, but leaf attribution
  changes sharply and still assigns large counts to small bookkeeping sites.
  This motivated sparse timing within CEE preparation before selecting a
  further representation change. This is diagnostic progress, with no runtime
  change, measured speedup or new client-loading result.
  [Evidence](cpu-sites-results-20261109.json).
- The subsequent [preparation-section and phase observations](PREPARATION_SECTIONS.md)
  leave gameplay sources and jars unchanged. The same-helper clean control
  measured **20.035 TPS, 49.378 ms mean and 56.634 ms p95**. CEE preparation
  averaged 7.514 ms in its section diagnostic; only 0.095 ms separated its
  fast/slow tick groups. In the separate phase-only run, native solving
  averaged **1.797 / 9.086 ms** in the fast/slow groups, accounting for about
  **86% of their 8.484 ms tick-time difference**. Most slow samples have the
  added cost in all 16 steps. This redirects investigation toward native
  solve work and convergence behavior; it does not establish removable cost
  or a threading speedup. All three runs passed their focused whole-yard
  checks and exited normally. The 44.399-second client result remains prior
  same-jar evidence, with no new graphical run.
  [Preparation evidence](preparation-sections-results-20261109.json),
  [phase evidence](phase-timers-results-20261109.json),
  [substep analysis](native-phase-variation-20261109.json).
- A subsequent [JNI work observer](NATIVE_SOLVER_WORK.md) records 2,112 steady
  calls across 132 complete 16-step ticks. They split between an initial
  residual check and a path consistent with one solve and three residual
  checks, averaging **0.115 / 0.620 ms per native call**. All sampled initial
  and hook-returned matrix-command counts are zero. The observed iteration
  limit of 200 is not being exhausted. The diagnostic measured **20.041 TPS,
  47.609 ms mean and 54.404 ms p95**, without a gameplay or native-library
  change; it is not a clean performance comparison. Whole-yard checks and
  normal shutdown passed. An isolated compiler is prepared for measuring
  native residual, guard/buffer and triangular-solve work next.
  [Evidence](native-work-results-20261109.json).
- The [native C-stage diagnostic](NATIVE_STAGE_TIMINGS.md) directly confirms
  that the sampled slow path performs one sparse solve and three residual
  checks, with **zero factorizations**. Across 16 such calls, triangular
  solving costs about **4.28 ms**, and residual multiplication/norm together
  cost **4.52 ms**. Native time explains about 88% of the sampled fast/slow
  tick gap. The diagnostic measured **20.042 TPS, 49.079 ms mean and
  57.053 ms p95**, so consistency remains unmet. Both fresh native variants
  passed 37 standalone safety checks, 100 cleanup/refactor cycles and 16
  selected game fixtures each. This is a different compiler/native artifact
  with timers, not a speedup or retained gameplay change. All 294 gameplay
  class entries remain byte-identical to `prototype-12g`. Original and copied
  saves, whole-yard validation and shutdown passed; no new client load or
  full acceptance was run. [Evidence](native-stages-results-20261109.json).
- The [exact native reduction experiment](NATIVE_REDUCTION.md) passes 40,740
  exact numerical comparisons across 16 floating-point modes and 20 focused
  native fixtures per clean variant. Its first same-compiler clean pair is
  slower: **49.071 versus 47.954 ms mean**, **54.428 versus 54.077 ms p95**.
  It remains an unretained candidate; `prototype-12g` is unchanged. Separate
  path counters record **249,856 finite checks and 107,928 norm reductions,
  with zero observed fallbacks**, so that diagnostic does not support the
  subnormal-fallback hypothesis. Native-call timings from differently
  instrumented runs suggest a local reduction, but do not establish an
  end-to-end gain. Repeat the clean
  comparison in reversed order before deciding its value. No client load,
  actual full-yard reload or full acceptance was repeated for this trial.
  [Evidence](native-reduction-results-20261109.json).
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
   and retired after failing to demonstrate a repeatable speedup. The subsequent
   [CPU-only profile](CPU_PROFILE_WITHOUT_TIMERS.md) leaves detailed phase and
   substep timers disabled, with explicit recording boundaries and unchanged
   jars. Compilation fell from 16.051 to 1.496 seconds between the diagnostic
   observations; this is not a calibrated overhead comparison. The subsequent
   indexed waveform trial also fails to show an elapsed-time gain, despite
   538 of 652 sampling stacks ending in the list iterator. Treat that attribution
   as a sampled location, not a removable-cost estimate. The subsequent
   [compiled-location diagnostic](CPU_FRAME_SITES.md) reinforces that limit:
   the same broad scopes remain hot while iterator, map and thread-local leaf
   labels shift substantially. Its offline tick analysis does not associate
   higher allocation with the slow ticks. The subsequent
   [section/phase measurements](PREPARATION_SECTIONS.md) show that CEE
   preparation explains little of the fast/slow gap. Native solving explains
   about 86% in the separate phase diagnostic, with added work across all 16
   steps on most slow samples. The [JNI observer](NATIVE_SOLVER_WORK.md) now
   distinguishes an initial residual early exit from one solve with three
   residual checks. Initial and hook-returned matrix-command counts are zero
   throughout the sampled calls; the 200-iteration limit is not exhausted.
   The subsequent [C-stage timers](NATIVE_STAGE_TIMINGS.md) directly confirm
   zero sampled factorizations and split the one-solve tick between about
   4.28 ms of triangular solving and 4.52 ms of residual multiplication/norm.
   The first exact finite/max reduction candidate passes numerical checks but
   loses its first clean pair; it remains unretained. Resolve the discrepancy
   between local native-call timings and whole-tick performance with a reversed
   clean comparison before changing its algorithm. Residual layout and then
   triangular workspace or factor layout remain subsequent candidates.
   The norm alone cannot close the tail gap. Keep
   finite-state, permutation and rollback guards intact, along with
   floating-point edge cases, contribution order and stopping criteria.
   Small transformer-map or iterator changes are not the leading hypothesis
   for closing this gap. Wire temperature remains a separate world-access
   candidate.
   Retain fresh validation and
   callback behavior. Preserve dynamic, custom, moving, unloaded and
   replaced-owner behavior, all 16 electrical substeps, accuracy, storage,
   heating and device callbacks. The prior packed worker prototype stays retired.
4. The [scoped-restoration prototype](SCOPED_RESTORATION.md) now covers the
   dedicated streaming, saved-chain, ownership, split-controller and storage
   cases and has final-artifact client, reload and steady measurements. Retain
   it as an opt-in functionality prototype; it adds loading cost. If reducing
   that cost, preserve metadata-only discovery, final-owner rebinding and
   disconnect progress. Reuse within a preparation epoch needs its own measured
   comparison; do not substitute a cross-tick cached component for current
   ownership. Unknown endpoint fallback and broader gameplay remain limits of
   the focused evidence.
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
