# CPU profile with detailed timers disabled

Experimental development snapshot, November 9, 2026. This profiles the retained
`wiretree.8` runtime on `dev/experimental-plain-wire-batching`, after retiring
the terminal bookkeeping and key-arithmetic prototypes. No gameplay or helper
jar changed. All 445 source/build hashes still match `prototype-09c`.

The [result report](cpu-profile-results-20261109.json) records the frozen
artifacts, completed run, recording boundaries and matching earlier clean
samples. The diagnostic supports the next investigation; it is not a new
performance optimization or proof of the complete goal.

## Recording and validation

`goal-cpu-profile-01` used a fresh 14,650-node save copy, both wire batches,
20 ms restoration budget, unresolved-wire priority, 2 ms chunk-task window,
CEE state geometry reuse and CPUs 12–23 (`fff000`). Warmup and steady sampling
each lasted 120 seconds. The owned JVM exited normally after 281.266 seconds.

The new `--cpu-jfr` runner mode starts JFR using the selected JDK's `jcmd`
during warmup. Detailed phase timers, substep tracing, RHS counters and the
old substep-triggered recording are disabled. Recording startup completed
115.888 seconds before the first steady tick began. The runner preserves the
command, owned PID, observed warmup state, timestamps, output and JDK profile
configuration hash in `cpu-profile-start.json`.

The raw recording includes warmup and shutdown. Analysis uses the inner
119.716-second steady interval from the existing tick/placement observations:
the first tick and two milliseconds at each boundary are omitted. Events
crossing a boundary are excluded whole. Wall/monotonic offset spread was
2.000 ms; the tool rejects mismatched tick populations, enabled detailed
timers and clock changes exceeding 20 ms. The first retained server allocation
weight is excluded from site attribution because it may span the boundary.
Raw and filtered exports remain separate, with hashes.

Ten focused tool checks passed, including nanosecond ordering, crossing events,
clock changes and incompatible diagnostic modes. A separate six-second owned
JVM probe recorded 389 CPU samples and exited normally. Evidence is under
`local/checks/cpu-profile-mode-01`. No gameplay correctness suite was rerun:
the identical runtime retains its named prior checks. The new benchmark passed
its own complete-yard checks: 14,650 nodes, 20,637 edges, 14,925 matrix rows,
one native network, 1,050 storage devices, all 16 substeps per tick, stable
topology, normal save/shutdown, CPU-affinity readbacks and unchanged references.

## Observations

| Measurement | CPU-only diagnostic |
| --- | ---: |
| Steady ticks | 2,400 |
| TPS | 20.038 |
| Mean tick | 49.725 ms |
| p95 / p99 tick | 56.724 / 63.705 ms |
| Maximum tick | 74.169 ms |
| Server CPU per tick | 49.453 ms |
| Server allocation per tick | 12.008 MiB |
| Compilation during sample | 1.496 s |
| GC pauses inside filtered interval | 118.885 ms |

The preceding detailed/JFR diagnostic recorded 16.051 seconds of compilation,
51.629 ms mean ticks and 59.738 ms p95. The earlier matching clean enabled
samples recorded 1.142/1.362 seconds of compilation and 49.077/48.616 ms means.
This new recording has much less compilation activity than the detailed one,
but these separate observations do not calibrate instrumentation overhead.
The p95 target of at most 50 ms remains unmet. Allocation differences between
these launches are not an optimization claim.

There are 8,691 retained server-thread Java execution samples, with these
exclusive enclosing scopes. Percentages refer only to those Java samples;
they are not measured phase times or predicted removable CPU time.

| Scope | Samples | Share |
| --- | ---: | ---: |
| CEE preparation | 1,658 | 19.08% |
| Native terminal preparation | 1,051 | 12.09% |
| CEE after-solve processing | 812 | 9.34% |
| Wire-power hooks | 775 | 8.92% |
| Wire temperature updates | 699 | 8.04% |
| Terminal waveform sampling | 652 | 7.50% |
| Other wire-entity updates | 580 | 6.67% |
| Other electrical work | 527 | 6.06% |
| CEE gameplay commit | 504 | 5.80% |
| Block entities | 479 | 5.51% |

The remaining samples cover chunk ticking, result publication and other work.
The 195 native-method samples are separate: 184 identify the native solve
entry point and 11 RHS JNI calls. Their sampling period differs from Java's,
and they do not expose solver internals. Do not combine their percentages.

## Decisions from the stacks

- Keep the packed/worker prototype retired. The profile again shows substantial
  preparation, publication and world-access costs outside the native solve.
  It does not establish an eligible parallel workload or a worker speedup.
- Fix the [scope of restoration pauses](SCOPED_RESTORATION_INVESTIGATION.md)
  before treating the retained loading improvement as functionally complete.
  An independent circuit must continue during ongoing saved-wire arrivals.
- For the next serial TPS experiment, inspect waveform iteration without
  packing/copying the samples. Of 652 terminal-sampling stacks, 538 end in
  `ArrayList$Itr.next`. The private per-network frame list is populated during
  preparation; an indexed traversal could remove iterator work while retaining
  every node read, availability check, sample and callback order. Inlining can
  attribute other work to the iterator, so only a matched benchmark can show
  whether this changes elapsed time.
- CEE preparation still includes repeated descriptor validation and adjacency
  lookup: 205 leaf samples in `LinearBranch.validate`, 138 in
  `Int2ObjectArrayMap.get`, and 114 in `StableDescription.update`. Fresh owner,
  geometry, topology and parameter validation remain required. The two retired
  allocation experiments are evidence against keeping changes merely because
  they allocate less.
- Wire temperature remains a separate world-access candidate. Its stacks
  include 217 leaf samples in a visible-chunk map lookup and 146 in biome
  palette lookup. Any reuse must preserve biome changes, movement, chunk
  availability and temperature/current equations; none is implemented here.

Thread-local preparation checks also appear below voltage reads. The retained
source and frozen bytecode short-circuit these checks when a circuit is not
paused; stack attribution alone does not prove a missing guard. Preserve
server/client isolation rather than replacing it with an unscoped global flag.

The retained 39.147-second restored/rendered client result at 1920×1080 and
12 chunks still applies to the unchanged jar. This headless diagnostic does
not supply another client-load result. Consistent TPS, streaming behavior and
the largest loading stalls remain open.

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-09c/wwpg-0.2.7-experimental.wiretree.8.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --cpu-jfr --name cpu-profile-new
python -B tools/experimental/summarize_cpu_profile.py --attempt cpu-profile-new --inputs local/frozen/prototype-09c/inputs.json --clean-report docs/experimental/cee-geometry-tps-results-20261109.json --output docs/experimental/cpu-profile-new.json
```

Use fresh attempt and output names. The summarizer verifies frozen sources and
artifacts, completed benchmark evidence and the explicitly filtered recording.
