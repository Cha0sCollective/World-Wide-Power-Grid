# Native C-stage timing — 2026-11-09

This experimental snapshot follows the [JNI work observation](NATIVE_SOLVER_WORK.md).
It measures the native operations behind the early-convergence and one-solve
paths on the copied 14,650-node yard. It is a diagnostic, not a retained
performance change or full acceptance result.

## Findings

The completed `goal-native-stages-02` diagnostic records **2,112 native calls
across 132 complete sampled ticks**, with all 16 substeps intact. Across warmup
and steady observation, all 4,032 entries, returns and CSV rows match, with
zero invalid frames. The native counters directly confirm two paths:

| Native work per call, weighted mean | Early convergence, 1,344 calls | One solve, 768 calls |
| --- | ---: | ---: |
| Residual multiplication | 0.05385 ms | 0.15471 ms |
| Residual norm | 0.02410 ms | 0.12768 ms |
| Sparse solve, inclusive | 0 | 0.28386 ms |
| Triangular solve, inside sparse solve | 0 | 0.26738 ms |
| Permutation validation, inside sparse solve | 0 | 0.01640 ms |
| Finite-vector checks | 0.02834 ms | 0.04068 ms |
| Copies | 0.00702 ms | 0.01655 ms |
| JNI total | 0.11512 ms | 0.62856 ms |

The paths perform one or three residual checks respectively. **Every sampled
factorization count is zero.** Initial and callback-returned matrix-command
counts are also zero. The slow path is not repeatedly rebuilding the LU
factorization or exhausting the 200-iteration allowance.

For a tick with 16 one-solve calls, triangular solving accounts for about
**4.28 ms**, while residual multiplication and norm together account for
**4.52 ms**. Finite-vector checks contribute about 0.65 ms, permutation
validation 0.26 ms and copies 0.26 ms. These are measured costs of required
work, not demonstrated removable time or worker speedups.

The 78 sampled ticks at or below 50 ms average **45.894 ms**, including
**1.829 ms** in JNI. The 54 slower samples average **53.960 ms**, including
**8.924 ms** in JNI. Native time explains about **88%** of this 8.066 ms gap.
All 16 calls take the one-solve path in 48 slow ticks; the other six slow ticks
still take the early-exit path. Native work therefore does not explain every
slow tick.

The full stable window measured **20.042 TPS, 49.079 ms mean, 57.053 ms p95,
62.007 ms p99 and 78.704 ms maximum**, with 3.052 seconds of compilation during
that window. Other monitored processes averaged 0.276 CPU cores, with no
competing game observed. The selected CPU group passed its quiet preflight
and affinity readbacks. Whole-yard checks, original/copy integrity, storage,
save and normal shutdown validation passed. This still misses the 50 ms p95
consistency gate.

The preceding `goal-native-stages-01` stopped before JVM launch because the
six-second CPU preflight averaged 5.053%, just above its 5% limit. It remains
preserved and is not counted as a performance result or pass. The retry used
a fresh attempt directory and passed a new preflight.

[Timing and run evidence](native-stages-results-20261109.json) and the
[checkpoint verification](native-stages-verification-20261109.json) bind the
artifacts, compiler inputs, source transformations, focused checks and loaded
libraries. No new client run was launched. The retained restored/rendered
client result remains the prior **44.399 seconds at 1920×1080 / 12 chunks**.
The goal of consistent 20 TPS with preserved functionality remains open.

## Next performance experiments

Residual evaluation and factorized triangular solving now have directly
measured budgets. Investigate an exact finite/max reduction and residual
layout first, then triangular-solve workspace or factor layout if needed.
Preserve NaN/Inf rejection, floating-point edge cases, residual contribution
order, current matrix values, structural invalidation and every stopping
criterion. A reduction or layout change needs focused numerical comparisons
against this same-compiler baseline before a clean copied-yard comparison.

The norm alone costs about 2.04 ms in a one-solve tick; it cannot close the
remaining tail gap by itself. The triangular solve costs about 4.28 ms but
contains data dependencies. Threading entire substeps would violate their
sequential state relationship. Parallel residual work would require accounting
for dispatch and synchronization across up to 48 residual evaluations per
tick. These timings justify studying those operations; they do not establish
that two or four workers will help. The retired packed worker prototype stays
retired.

## Isolation and preserved behavior

The retained gameplay runtime remains `prototype-12g` / `wiretree.11`.
Two separate jars replace its guarded Windows library using the same pinned
PowerGrid, SuperLU and OpenBLAS sources and isolated MinGW compiler. One is an
ordinary compiler baseline; the other adds timers. They share their compiled
SuperLU and OpenBLAS dependencies. The new native hashes differ from the
retained library, so measurements cannot be presented as a clean comparison
with the earlier JNI diagnostic.

All 294 gameplay class entries and the fixture jar are byte-identical to the
retained artifacts. Each new jar changes only the guarded Windows DLL and its
checksum property, and adds an explicit experimental marker. Mod metadata
continues to identify the unchanged Java code as `wiretree.11`; distinct file
names and hashes identify the native variants. Published assets and original
saves are untouched.

The source transformation preserves the original guarded solver body and
proves that removing the inserted observation scopes restores it. Every
wrapped expression executes once with its original result. Stopping criteria,
all 16 substeps, callbacks, finite-state and permutation checks, failure
rollback, and storage behavior remain in place. The original Java-owned
one-byte auxiliary buffer is unchanged. Counters live in private native
storage, and the helper immediately copies its borrowed read-only observation
without retaining the direct-buffer view.

## Timing boundaries

Thirteen native scopes record counts and Windows performance-counter ticks:
the solver body, residual multiplication, residual norm, sparse solve,
factorization, triangular solve, permutation validation, finite-vector checks,
copies, vector deltas, Java callbacks, matrix-command processing, and RHS
processing. Timers execute on every native invocation; the helper records the
existing sparse tick sample. JNI elapsed time excludes copying the diagnostic
counters, but includes native timer setup and the measured body.

These are nested scopes. The solver body contains the root operations; sparse
solve contains factorization, triangular solve and permutation validation.
Permutation validation can also occur inside factorization. The report
retains inclusive costs and only subtracts disjoint scopes when computing
remainders. Adding all listed times would double-count work.

The evidence validator pairs every sampled tick with all 16 native calls,
checks counter identity and call counts against Java callbacks, verifies
nested time bounds, and checks the actual loaded DLL hash. The runner requires
both the compatible helper and explicitly marked diagnostic runtime. It
rejects an instrumented library presented as a clean timing run.

## Focused checks

Both native variants passed **37 native safety checks and 100 valid
refactor/cleanup cycles each**. The instrumented executable additionally
passed four active-counter assertions. DLL inspection verified 64-bit Windows
format, matching imports, all original JNI exports and the extra diagnostic
getter. Fault-injection wrappers are confined to the test executables.

Both packaged variants passed the same **16 focused game fixtures** from
`wwpg_unload`, `wwpg_solver_safety` and `wwpg_native_guard`, for 32 fixture
executions. Six focused evidence checks cover counter identity, callback
counts, nesting and diagnostic/runtime pairing. These checks do not establish
every mod feature or replace full acceptance.

## Reproduce

Use fresh names for every preparation, frozen artifact, check and benchmark
directory. Preparation and compilation run in the existing `Ubuntu-22.04`
WSL distribution with two compiler workers; the executables, fixtures and
benchmark run on Windows. No system packages are installed.

```text
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/prepare_native_profile.py --name native-stages-new --download
python -B tools/experimental/instrument_native_profile.py --directory local/native-stages-new
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/build_native_profile.py --directory local/native-stages-new --toolchain local/native-toolchain-01/inputs.json --java-home "/mnt/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot" --jobs 2
python -B tools/experimental/run_native_profile_checks.py --build local/native-stages-new/build.json --toolchain local/native-toolchain-01/inputs.json
python -B tools/experimental/freeze_native_profile.py --name native-stage-profile-new --base-inputs local/frozen/prototype-12g/inputs.json --native-build local/native-stages-new/build.json --native-checks local/native-stages-new/native-safety-checks.json --role profile
```

Repeat freezing with `--role baseline` and a separate name. Run the selected
native fixture group against each frozen manifest before collecting a copied
yard diagnostic. The helper is frozen independently using
`freeze_native_stage_helper.py`; the preserved helper for this snapshot is
`local/frozen/native-stage-helper-01/inputs.json`.

```text
python -B tools/experimental/freeze_native_stage_helper.py --name native-stage-helper-new --inputs local/frozen/prototype-12g/inputs.json --base-helper-inputs local/frozen/native-work-helper-02/inputs.json
python -B tools/experimental/focused_checks.py --inputs local/frozen/native-stage-profile-new/inputs.json --backend NATIVE --selection native --name native-profile-guard-new --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/native-stage-profile-new/wwpg-0.2.7-experimental.native-stages-profile.jar --helper-inputs local/frozen/native-stage-helper-new/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --detailed --native-work --native-stages --name native-stages-run-new
python -B tools/experimental/summarize_native_stages.py --attempt native-stages-run-new --inputs local/frozen/native-stage-profile-new/inputs.json --helper-inputs local/frozen/native-stage-helper-new/inputs.json --focused-checks local/checks/native-profile-guard-new/result.json --output docs/experimental/native-stages-new.json
```
