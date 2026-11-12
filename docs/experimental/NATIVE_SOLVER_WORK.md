# Native solver work observation

Experimental development snapshot, November 9, 2026. This follows the
[preparation/phase investigation](PREPARATION_SECTIONS.md), which associated
most of the fast/slow tick difference with native solving. Gameplay remains
`0.2.7-experimental.wiretree.11`, frozen at `prototype-12g`; all 450 retained
source/build hashes, both gameplay jars and the loaded guarded native library
match. Work remains on the experimental branch with copied benchmark saves.

## What the new observation shows

The [completed diagnostic](native-work-results-20261109.json) observed 132
steady ticks, each with all 16 native calls. It passed the 14,650-node,
20,637-edge, 14,925-row, one-native-network, 1,050-storage-device and unchanged
topology checks. All 4,032 warmup/steady observer entries returned and produced
their corresponding rows, with zero invalid observations. Original references
were unchanged, and the owned JVM exited normally.

| Observed native path | Sampled calls | Sampled ticks | Mean JNI time per call |
| --- | ---: | ---: | ---: |
| One iteration-hook callback, one residual callback | 1,344 | 84 | 0.115 ms |
| Two iteration-hook callbacks, three residual callbacks | 768 | 48 | 0.620 ms |

Every sampled invocation received an iteration limit of **200** and **zero
initial Jacobian commands**. Every iteration-hook callback also returned zero
matrix commands. The first path received hook index `[0]`; the second received
`[0, 0]`. All 16 substeps within each sampled tick followed the same path.

Under the pinned [PowerGrid solver implementation](https://github.com/patryk3211/PowerGrid/blob/0938be067fb71f311b3e5fd6c5f7cdaed722e241/native/src/powergridNative/c/solver.c),
these sequences indicate an initial residual early exit, or one sparse solve,
one solution-fitting residual check and a final convergence residual check.
That interpretation comes from the source and observed callbacks; a native
solve counter was not added. The configured limit of 200 does not mean these
steps are performing 200 solves, so reducing it is not supported as a fix.

The sampled network metadata contains no nonlinear inner hooks. There are no
matrix commands within the observed calls, and their added cost repeats in
every substep. This does not support repeated refactorization as the principal
explanation for this pattern. A pending matrix change before an observed tick
is not directly counted; individual factorization durations remain unmeasured.

| Weighted sampled group | Tick time | JNI scope | Calls with a fitting hook |
| --- | ---: | ---: | --- |
| At or below 50 ms, 79 samples | 44.652 ms | 1.812 ms | None |
| Above 50 ms, 53 samples | 52.403 ms | 8.992 ms | All 16 in 48 ticks; none in five |

The entire run measured **20.041 TPS, 47.609 ms mean, 54.404 ms p95 and
59.825 ms p99**, with 2.932 seconds of compilation during the stable window.
Other monitored processes averaged 0.270 CPU cores; no competing game was
observed. This is a diagnostic with a different helper and instrumentation,
not a clean speedup comparison. It still misses the 50 ms p95 gate.

## Observer boundaries

`local/frozen/native-work-helper-02/inputs.json` binds the helper and sources.
Helper SHA-256:
`dafff27663c6d7b393a2f32a64e7b0d05644706a8087ac8493b0d458aab8c1c6`.
Relative to the retained affinity helper, only the mixin plugin/configuration
and tick observer wrapper change, with three added observation classes.
The gameplay jar and native DLL are unchanged.

The opt-in `--native-work` flag requires its supporting helper and existing
sparse detailed timers; JFR and preparation-section probes remain disabled.
The wrapper receives the actual JNI arguments after PowerGrid computes them,
calls the original exactly once and returns its buffer unchanged. Hook and
residual wrappers also call their originals once and preserve their results
and exceptions. The observer does not call the iteration-limit function again
or read/write electrical vectors. It records returned buffer capacity only.

Each sampled frame records argument counts, callback indices, callback command
counts and elapsed JNI time. CSV output occurs after `tickServer` returns.
Evidence validation pairs all 16 calls with the existing substep trace and
checks matching tick times, complete callback sequences, returned row counts,
balanced entries/returns and nested timing bounds. Four focused Python checks
cover incomplete/duplicate calls, invalid callback sequences and mismatched
or out-of-scope observations. The live full-yard validation then checks the
actual transformed runtime.

## Next native experiment

Measure residual multiplication/norm, buffer/finite checks and factorized
triangular solving separately before changing the solver. Preserve every
stopping criterion, all 16 substeps, nonlinear callbacks, finite-row checks,
permutation validation and failure rollback. Candidate improvements must be
compared with the same compiler and frozen sources, with focused native guard
and electrical behavior checks before a copied-yard timing comparison.

The pinned [SuperLU residual multiplication](https://github.com/xiaoyeli/superlu/blob/a390700c6a5fc8f2450417b3103710e1d90b5163/SRC/dsp_blas2.c)
iterates matrix columns and accumulates into output rows. A row-oriented
residual pass is one possible experiment if those operations dominate. It
would need to preserve contribution order, zero-input skipping, fresh matrix
values, structural invalidation and all failure checks. Reusing a previous
residual solely because there are no current matrix commands would be
insufficient: state and right-hand-side values can change. No such optimization
is implemented or claimed here.

A pinned MinGW toolchain is now extracted under `local/native-toolchain-01`.
Six Ubuntu package archives were checked against their indexed SHA-256 values,
then extracted into that directory without system package installation. Its
64-bit Windows compile/run probe passed. This prepares an isolated native
build; it does not change a library loaded by the game. The source archives
and solver revisions must still follow `tools/native/sources.lock.json`.

The [checkpoint verification](native-work-verification-20261109.json) preserves
runtime/helper comparisons, report references, focused checks and toolchain
provenance. Retained client readiness remains the prior **44.399-second**
observation at 1920×1080 / 12 chunks. There is no new client load, full headless
acceptance or graphical acceptance result. Consistent 20 TPS and broader
functionality evidence remain open.

## Reproduce

Every helper, attempt, toolchain directory and report needs a fresh name.

```text
python -B tools/experimental/freeze_native_work_helper.py --name native-work-new --inputs local/frozen/prototype-12g/inputs.json --base-helper-inputs local/frozen/affinity-helper-02/inputs.json
python -B -m unittest discover -s tools/experimental -p test_native_work.py -v
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-12g/wwpg-0.2.7-experimental.wiretree.11.jar --helper-inputs local/frozen/native-work-new/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --detailed --native-work --name native-work-run-new
python -B tools/experimental/summarize_native_work.py --attempt native-work-run-new --inputs local/frozen/prototype-12g/inputs.json --helper-inputs local/frozen/native-work-new/inputs.json --output docs/experimental/native-work-new.json
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/prepare_native_toolchain.py --name native-toolchain-new
```
