# CEE preparation section timing

Experimental development snapshot, November 9, 2026. The gameplay runtime and
fixture jars remain byte-identical to `0.2.7-experimental.wiretree.11`, frozen
at `local/frozen/prototype-12g/inputs.json`. All 450 source/build hashes match.
Work remains on `dev/experimental-plain-wire-batching`, using fresh benchmark
save copies and focused checks only.

The [result report](preparation-sections-results-20261109.json) audits the
section diagnostic and its same-helper disabled control. This follows the
[frame-location investigation](CPU_FRAME_SITES.md), which found that sampled
leaf locations were insufficient to choose a useful optimization.

## Observations

| Run | TPS | Mean tick | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Section observation enabled | 20.037 | 48.794 ms | 55.772 ms | 59.372 ms |
| Same helper, observation disabled | 20.035 | 49.378 ms | 56.634 ms | 62.666 ms |

The enabled run is a diagnostic, not a performance change. One sequential pair
does not calibrate instrumentation overhead or establish a speedup. Compilation
during the stable samples was 1.299 / 1.813 seconds. Monitored other-process CPU
averaged 0.262 / 0.258 cores, with no competing game observed; processor
frequency and thermal behavior were not controlled.

Both copied-yard runs passed their 14,650-node, 20,637-edge, 14,925-row,
one-native-network, 1,050-storage-device and 16-substep checks, with stable
topology, unchanged original references and normal save/shutdown. Both used
120 seconds of warmup and a 120-second sample on CPUs 12–23 (`fff000`), with
the retained batching, restoration, task-window, geometry and scope flags.
Neither meets the p95 50 ms gate.

## Measured preparation sections

The diagnostic recorded 132 sampled steady ticks across all three world
bridges: 120 ordinary samples and all 12 source-edit samples. Ordinary ticks
are sampled at residue seven of each 20-tick period; source-edit ticks are
also retained at residue one of each 200-tick period. Forced samples have
weight one, and ordinary samples share the remaining steady population.

| Whole section | Weighted mean |
| --- | ---: |
| Initial guards and transformer ownership | 0.048 ms |
| Fresh block/entity anchor validation | 0.754 ms |
| Descriptor, adjacency-size, ground and native-node validation | 0.938 ms |
| Branch type, attachment and parameter validation | 1.149 ms |
| Fresh descriptor rebinding | 0.250 ms |
| Branch and transformer application | 0.658 ms |
| Changing-branch index construction | 0.188 ms |
| Stable-description update total | **3.984 ms** |
| Remaining CEE preparation | 3.530 ms |
| Complete CEE preparation | **7.514 ms** |

These are measured diagnostic scopes, including observer effects. They do not
estimate how much work can safely be removed. In particular, the winding-key
lookup from the preceding profile sits inside the entire 0.658 ms application
section. Its sampled count does not support treating it as several milliseconds
of independently removable work. A second adjacency lookup or transformer-key
capture might save part of that section, but cannot by itself explain the
remaining consistency gap.

The sampled ticks at or below 50 ms averaged 45.437 ms; those above 50 ms
averaged 53.227 ms after the same weighting. CEE preparation averaged
7.471 / 7.566 ms in those groups: only 0.095 ms of a 7.790 ms difference.
The corresponding stable-description update means were 3.957 / 4.017 ms.
Most observed variation is outside preparation. This is an association within
the diagnostic, not proof of a particular cause elsewhere in the tick.

## Phase follow-up: native solver variation

A third copied-yard run enabled the existing sparse phase/substep timers, with
JFR and the preparation-section observer disabled throughout the process. It
completed with **20.022 TPS, 48.943 ms mean, 55.330 ms p95 and 59.385 ms p99**.
Compilation during the sample was 1.397 seconds. The same runtime, helper,
affinity, warmup, sample duration and whole-yard checks were retained.
The [phase report](phase-timers-results-20261109.json) and
[offline substep analysis](native-phase-variation-20261109.json) preserve the
new observation separately from the clean control above.

| Sampled group, weighted means | Tick time | Native solve scope | CEE preparation |
| --- | ---: | ---: | ---: |
| At or below 50 ms, 79 samples | 45.670 ms | 1.797 ms | 7.012 ms |
| Above 50 ms, 53 samples | 54.153 ms | 9.086 ms | 7.263 ms |
| Difference | **8.484 ms** | **7.289 ms** | 0.251 ms |

Native solving accounts for about **86% of the observed group difference**.
This scope includes the Java entry wrapper and nested native callbacks; the
measured Java iteration/residual callbacks together contribute only about
0.001 / 0.003 ms per tick in the two groups. The large difference remains
outside those callbacks.

All 79 fast samples have every native step below 0.3 ms. Of the 53 slow
samples, 48 have all 16 steps above 0.3 ms, while five have none above it.
The threshold describes the observed timing separation; it does not identify
a solver branch. The main pattern is repeated work throughout a tick, rather
than one expensive first step. Both alternating source-voltage settings have
similar weighted native means, about 4.30 / 4.32 ms. Equal aggregate means do
not establish equal physical state or rule out transient storage effects.

The pinned upstream [native solver implementation](https://github.com/patryk3211/PowerGrid/blob/0938be067fb71f311b3e5fd6c5f7cdaed722e241/native/src/powergridNative/c/solver.c)
can return after a residual check or proceed through a sparse solve and
solution fitting. Its [sparse matrix implementation](https://github.com/patryk3211/PowerGrid/blob/0938be067fb71f311b3e5fd6c5f7cdaed722e241/native/src/powergridNative/c/sparse.c)
distinguishes factorization from reuse of the existing factors. Our guarded
native build additionally preserves finite-state, permutation, failure and
rollback checks. These are possible sources of different work, not counts
measured by this diagnostic.

Inspection also closes one tempting hypothesis: although `TransformerStamp`
calls `setRatio` every tick, PowerGrid 0.6.2's `alterConductanceMatrix` drops
changes whose absolute value is below `1e-9`, including unchanged ratios.
Those calls do not automatically send zero-delta updates to native code.
Pinned source excerpts and loaded-jar bytecode are retained in
`local/preparation-review-02/`.

The next diagnostic should observe actual JNI iteration limits, initial
Jacobian-command counts and iteration/residual callback counts per substep.
This can distinguish convergence early exits from repeated solves without
changing the native library, electrical precision, substep count or callback
behavior. Matrix-factorization cost may then need its own observation.
The present results do not demonstrate that multithreading will recover the
remaining time, and the rejected packed-worker prototype remains retired.

## How the observer is isolated

`local/frozen/preparation-helper-01/inputs.json` preserves the helper overlay
and its source inventory. Helper SHA-256:
`a410f9201b19754471116e6595040efd440ba2518a968e4a761059fae02821cc`.
Only the benchmark mixin configuration/plugin and new observation classes
change; the original loading gate, graph, storage and solver checks remain.

`--preparation-sections` explicitly enables it and requires this helper. It
cannot be combined with detailed phase timers or JFR. With the flag disabled,
the section mixin and instruction insertions do not apply, and none of the
four observer output files may exist. Ordinary runner defaults remain off.

The helper inserts calls at method entry/return and six whole-section
boundaries. There is no timer in an element-loop body. The descriptor boundary
is verified to precede its loop initializer, outside the back edge. Existing
instructions, jumps, exception handlers and return values are preserved.
For both actual transformed classes, removing the inserted calls and restoring
the stack bound reproduced the preceding serialized class bytes exactly;
reinsertion reproduced the instrumented bytes. These proofs are captured
before the observed methods run.

Offline checks also verified both serialized methods' operand stacks, rejected
duplicate instrumentation for both targets, and rejected a changed field
anchor. Five Python evidence checks cover missing/duplicate observations,
incomplete stages, unbalanced durations and failed steady updates. The live
run matched all 15,405 preparation entries/returns and all 15,402 update
entries/returns, with 792 warmup/steady rows and zero invalid observations.
Every sampled steady tick has all three owners and complete section coverage;
its section sum exactly equals its measured update duration.

No gameplay correctness suite was rerun for identical gameplay jars. The
retained scoped-restoration checks and client evidence keep their original
identity; the new benchmark supplies its own focused whole-yard observation.
No full acceptance or graphical suite was launched.

## Reproduce

```text
python -B tools/experimental/freeze_preparation_helper.py --name preparation-helper-new --inputs local/frozen/prototype-12g/inputs.json --base-helper-inputs local/frozen/affinity-helper-02/inputs.json
python -B -m unittest discover -s tools/experimental -p test_preparation_sections.py -v
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-12g/wwpg-0.2.7-experimental.wiretree.11.jar --helper-inputs local/frozen/preparation-helper-new/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --preparation-sections --name preparation-on-new
```

Run the matching control with the same arguments, omitting
`--preparation-sections` and choosing a fresh attempt name. Then:

```text
python -B tools/experimental/summarize_preparation_sections.py --diagnostic preparation-on-new --control preparation-off-new --inputs local/frozen/prototype-12g/inputs.json --helper-inputs local/frozen/preparation-helper-new/inputs.json --output docs/experimental/preparation-new.json
```

Every freeze, attempt and output needs a new name. Existing evidence is never
overwritten. The retained 44.399-second restored/rendered client result at
1920×1080 / 12 chunks remains its named prior observation, not a new result
from these headless runs. The full performance and functionality goal remains
active.

For the phase follow-up, use a fresh attempt with the same retained options
and `--detailed`, omitting `--preparation-sections`. Analyze a completed run:

```text
python -B tools/experimental/summarize_phase_timers.py --attempt phase-new --inputs local/frozen/prototype-12g/inputs.json --clean-report docs/experimental/preparation-sections-results-20261109.json --output docs/experimental/phase-new.json
python -B tools/experimental/summarize_native_variation.py --phase-report docs/experimental/phase-new.json --output docs/experimental/native-variation-new.json
```

The [checkpoint verification](preparation-verification-20261109.json) checks
all 450 retained source/build hashes, both rebuilt jars, helper provenance,
new report references and normal completion of all three runs. The source and
jars are unchanged; the enabled helper instruments loaded classes for
observation. None of these measurements is a new gameplay optimization.
