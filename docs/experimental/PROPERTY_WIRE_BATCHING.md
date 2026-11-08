# Experimental CEE property-source batching

This is the second isolated performance prototype, based on commit `32da30b`
of `dev/experimental-plain-wire-batching`. Its runtime version is
`0.2.7-experimental.plainwire.2`. It continues the
[first plain-wire experiment](PLAIN_WIRE_BATCHING.md); that experiment's jars,
results, copied worlds and evidence remain preserved.

The worktree remains `local/x27w`. All benchmark attempts receive fresh copies
of the hash-checked reference save. These are focused experiments, not release
or sustained-capacity acceptance. The normal development branch remains on its
original 0.2.7 commit. There is no save-format or native-library change.

## Measured result

Property batching moved the 14,650-node yard closer to 20 TPS, but did not meet
the target. These three fresh processes used the same frozen prototype jar;
only the property flag changed. All used clean timing, with verification and
detailed timers disabled.

| Attempt | Property batching | Mean tick ms | p95 tick ms | Measured TPS | Total restoration s |
| --- | --- | ---: | ---: | ---: | ---: |
| `property-plain-control-01` | Off | 56.37 | 70.62 | 17.70 | 116.38 |
| `property-enabled-clean-01` | On | 51.51 | 62.11 | 19.34 | 117.99 |
| `property-enabled-clean-02` | On | 55.15 | 68.74 | 18.09 | 115.79 |

The enabled samples show **2.1-8.6% lower mean tick time** than this fresh
plain-only control. The better sample is still 1.51 ms over the 50 ms mean
budget, and its p95 is 12.11 ms over. Its 49.62 ms median does not establish
sustained 20 TPS. Both enabled attempts fail the practical target described
below. There is no clear chunk-restoration improvement in these measurements.

The first prototype's preserved 19.14 TPS plain-only result came from a
different run and runtime jar. Today's plain-only control is slower at 17.70
TPS. Different matrix row and hook orders across JVM launches, plus the spread
between the two enabled samples, limit causal attribution from these short
windows. The observed reductions are not a guaranteed speedup or a capacity
claim. No attempt was discarded or substituted with the historical result.

Both enabled clean runs reached the complete 9,443-property batch and recorded
over 703 million optimized property contributions, with no property scalar
fallbacks. The control recorded zero property contributions. Counters cover
the entire process, including restoration and small property-only networks;
their step totals are not comparable to just the large yard's tick count.

The separate `property-verified-detailed-01` run completed **1,247,595,702**
optimized contributions with bitwise scalar checks, including **596,340,405**
property contributions. It reported no mismatches or scalar fallbacks. All
1,847 stable ticks retained 16 solves; all 103 sampled traces contained all 16
accepted substeps, with no partial or rejected solves. It also passed topology,
save, normal-shutdown and original-reference checks. Its instrumented timing
is retained in the result index but excluded from the performance comparison.

## Change and limits

The cached power-aggregation plan now optionally includes the exact, final WWPG
`PropertyCurrentSourceWire` class used for CEE property-source stamps. It reuses
the same voltage snapshot and accumulator as plain wires. The power calculation
remains `(voltage * conductance) * voltage`, added as `power / tickCount` in the
original hook position. Conductance is read on every substep. Source current,
RHS preparation, native solving, CEE simulation and gameplay callbacks retain
their existing paths.

This is still serial server-thread execution with all 16 electrical substeps.
The original network/node/backend eligibility checks, row/owner validation,
restoration guards and topology invalidation apply. A property-only network can
now batch when its nodes and hooks meet those same restrictions. Unknown hooks,
custom nodes, tracked leaves and unavailable networks retain the original loop.
Nonpositive or non-finite conductance retains the scalar callback rather than
entering the resistive accumulator.

The new property is separately opt-in and requires the original batch flag:

```text
-Dwwpg.experimental.plainWireBatch=true
-Dwwpg.experimental.plainWireBatch.properties=true
```

Keep `-Dwwpg.experimental.plainWireBatch.verify=true` for correctness runs only.
It checks each optimized power and resulting aggregate against the scalar
calculation, bit for bit. All three flags default off. Using plain batching
with the property flag off provides the control in the exact same runtime jar.

The large yard contains 4,425 ordinary wires, 5,943 transmission lines and 9,443
property-source wires. The latter are now separate entries in the plan. Its
shutdown counters distinguish optimized property contributions, property scalar
fallbacks and the largest number of eligible property stamps in one network.
The runner requires both plain coverage and property coverage, not just a
successful server exit. It verifies that property counters stay zero in the
plain-only control.

## Frozen inputs and focused checks

The runtime SHA-256 is
`8f59be1043aaaf47c33561f3327ef5f52b5ca01eac8aef7039bd1066cb849aa2`.
The final frozen runtime, fixture companion and CEE jar are in
`local/frozen/prototype-02c`, with their hashes and compiled-source inventory in
`inputs.json`. The runtime is unchanged across all fixture attempts. Compared
with the first prototype, only the batch implementation/interfaces, its mixin
methods and version metadata differ. All native resources are byte-identical.

Java 21.0.12, NeoForge 21.1.231, PG 0.6.2, CEE 1.1.4, Create 6.0.10-280 and the
unchanged benchmark helper remain pinned to the first experiment's inputs.
The benchmark uses an 8 GiB heap, server-thread CPU mask `fff000`, the normal
60-second watchdog, 120 seconds of warmup and 120 seconds of stable measurement.
Voltage edits every 200 ticks and all 16 solves remain enabled. No GameTest
companion is loaded into a timing process. Detailed traces and scalar
verification are confined to a separate correctness run.

The focused wire selection adds these cases:

* A powered property-only network changes conductance and injected current
  between substeps at positive, negative and zero supply voltage. It checks
  bitwise power/aggregation and that the source current and ideal supply are
  preserved. The property counter must match the selected on/off mode.
* The actual catenary `reconnect` method changes a retained property stamp from
  one powered endpoint to another. It checks the graph edge, ownership, matrix
  size, changed operating point and resumed aggregation after invalidation.
* Unstamped probes test both ground orientations, signed zero, subnormal/large
  values, non-finite voltage clamping and exceptional conductance. Invalid
  conductance never enters a solver matrix; scalar fallback is checked explicitly.
* The existing real PG-source/CEE-resistor restoration fixture checks masking,
  resumed power, reversed supply and zero supply under the new launch flags.

The previous plain-wire live-edit fixture now explicitly supplies a nonzero
voltage and requires a powered wire before checking edits.

| Focused selection | Native | Java |
| --- | ---: | ---: |
| Wire power, property batching on | 13/13 | 13/13 |
| Wire power, property batching off | 13/13 | 13/13 |
| Unload and solver safety, property batching on | 11/11 | 11/11 |

All **74 focused checks passed** with scalar verification enabled. No full
headless suite or graphical acceptance was run.

Two earlier fixture attempts remain failed evidence. The first could not launch
because the test companion shared a package with the runtime module. The second
completed with two failures caused by treating the three-argument PG voltage
source constructor's resistance argument as voltage. The final fixtures use
their own package and the four-argument resistance/voltage constructor. The
earlier input sets remain in `prototype-02` and `prototype-02b`; their failed
runs are not counted as passing checks. The separately recorded first
prototype's evidence is not relabelled as having this strengthened coverage.

## Reproduce

Use Java 21 and Python in the experimental worktree. The frozen manifest below
is the final input set. Use new attempt names; existing evidence is never reused
as a fresh run. Building a future variant requires freezing its jars into another
directory before launching checks.

```text
gradlew.bat jar gameTestJar -x test --offline --no-daemon -I tools/experimental/plain-wire.gradle -PexperimentalCee=<frozen-CEE-1.1.4-jar>
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend NATIVE --property-batch --name property-wire-native-new
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend JAVA --property-batch --name property-wire-java-new
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend NATIVE --name property-wire-native-off-new
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend JAVA --name property-wire-java-off-new
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend NATIVE --selection lifecycle --property-batch --name property-lifecycle-native-new
python tools/experimental/focused_checks.py --inputs local/frozen/prototype-02c/inputs.json --backend JAVA --selection lifecycle --property-batch --name property-lifecycle-java-new
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name property-plain-control-new --runtime local/frozen/prototype-02c/wwpg-0.2.7-experimental.plainwire.2.jar --batch
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name property-enabled-clean-new --runtime local/frozen/prototype-02c/wwpg-0.2.7-experimental.plainwire.2.jar --batch --property-batch
python tools/experimental/plain_wire_benchmark.py --reference-root <main-checkout> --name property-verified-detailed-new --runtime local/frozen/prototype-02c/wwpg-0.2.7-experimental.plainwire.2.jar --batch --property-batch --verify-batch --detailed
python tools/experimental/summarize_properties.py
```

The summary tool records only `property-*` attempts in a separate
[machine-readable results and evidence index](property-wire-results.json).
The original result index stays unchanged. Each successful control must retain
14,650 electrical nodes, 20,637 connections, 14,925 matrix rows, 953 chunks,
1,050 storage devices, zero rejected/CEE worker solves, unchanged graph
fingerprints within the measured run, no stable-tick matrix rebuilds and normal
save/shutdown. The original reference files are hash-checked after every run.

Fresh JVMs still choose different matrix row and hook orders. The fixed CPU
mask and identical inputs do not remove that variation. Timing should therefore
be read as short-window experimental evidence. The existing practical target is
at least 19.9 measured TPS and p95 tick time at or below 50 ms. Passing that gate
in these controls does not establish long-duration capacity or release readiness.
