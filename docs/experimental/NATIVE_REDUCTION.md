# Exact native reduction experiment — 2026-11-09

This experiment follows the [native stage timings](NATIVE_STAGE_TIMINGS.md).
It changes only the finite-vector checks and maximum-residual reduction inside
the guarded Windows solver. The existing 16-substep sequence, matrix and
residual calculations, callbacks, stopping criteria, permutation checks,
failure rollback and storage paths remain intact.

## Implementation boundary

For finite normal binary64 values and zero, the sign-cleared bit encodings
have the same order as their magnitudes. The candidate classifies four rows
at a time with AVX2 integer operations and reduces their maximum without
floating-point reassociation or rounding. Negative zero becomes positive zero
as in the scalar `fabs` path. Loads cover complete four-row blocks, followed
by a scalar tail; arrays smaller than 16 rows use the original routines.

If any value is subnormal, NaN or infinite, the candidate calls the original
scalar routine over the original input. This preserves its handling of DAZ,
nonfinite rejection and floating-point status. The original scalar function
bodies remain present; removing the inserted wrapper and restoring their names
reproduces the guarded source. The candidate does not alter the floating-point
control word or cache results across calls.

Only the solver object is recompiled. The other four native objects, compiler
flags, SuperLU and OpenBLAS archives come from the frozen compiler baseline.
Their hashes are checked before and after the build. The candidate retains
the same JNI exports and Windows imports. CPU capability checks and exception
handling use the unchanged baseline objects.

The two runtime jars have identical gameplay classes. They differ only in the
guarded Windows DLL, its checksum property and explicit experimental marker.
Both are separate from the retained `prototype-12g` artifact. Published native
assets and the normal build are untouched.

## Copied-yard result

The first clean pair does **not** support retaining this candidate. Both use
the same compiler, expanded fixture jar, affinity helper, CPU group `fff000`
and retained restoration flags. Detailed timers, JFR and native observers are
disabled. Each run uses a fresh save copy, 120 seconds of warmup and a
120-second stable sample.

| Metric | Scalar control | Reduction candidate |
| --- | ---: | ---: |
| Mean tick | 47.954 ms | 49.071 ms |
| p95 tick | 54.077 ms | 54.428 ms |
| p99 tick | 57.875 ms | 59.461 ms |
| Maximum tick | 66.511 ms | 68.114 ms |
| Ticks above 50 ms | 36.82% | 43.33% |
| Average TPS | 20.033 | 20.030 |
| Stable ticks | 2,401 | 2,400 |

The candidate adds **1.117 ms to the mean** and **0.350 ms to p95** in this
pair. It is slower in all four corresponding 30-second blocks. Stable-window
compilation is similar (1,346 versus 1,362 ms), as is other-process CPU use
(0.264 versus 0.276 cores); neither run records a competing game. This is one
pair, not repeatability evidence, but it provides no practical speedup.
Average TPS near 20 does not meet the tick-consistency requirement.

Both runs pass the 14,650-node / 20,637-edge graph, 14,925-row native system,
1,050 storage-owner and all-16-substep checks, preserve the source saves and
exit normally after saving. The raw comparison is preserved at
`local/native-reduction-01/comparison-first.json`; the run names are
`goal-native-reduction-control-01` and `goal-native-reduction-candidate-01`.
The retained gameplay runtime remains `prototype-12g`. No client loading,
actual full-yard reload or full acceptance was rerun for this slower candidate.

## Focused numerical and gameplay checks

The native executable compares the actual extracted scalar routines against
the candidate: **40,740 cases** compare exact return bits and the complete
MXCSR value. Cases cover 16 combinations of rounding, DAZ and FTZ with masked
exceptions, initially clear/set status flags, random encodings, signed zero,
normal extremes, subnormals, quiet/signaling NaNs and infinities. Sizes range
from empty to 14,925 rows, with varied alignment and protected pages at both
array boundaries. Inputs are also checked for mutation.

An alternating-order microbenchmark uses 14,925 rows. For ordinary random
normal values and sparse zero/normal values, candidate norm time is about
17–18% of scalar time and finite-check time about 25%. The deliberately
subnormal case is **22–24% slower**, because detection precedes the scalar
fallback. These are repeated-array native measurements, not game throughput.
The copied-yard measurements determine practical value.

The original fixture jar is copied and extended with four experimental tests;
every original entry is unchanged. They exercise 16, 17, 32, 33 and 67 rows,
all 16 changing-input substeps, NaN/Inf rejection in vector blocks and tails,
exact rollback/recovery and subnormal stopping behavior. The same expanded
fixture jar is used with both libraries. Each variant passes **20 selected
native safety/unload fixtures**, for 40 passing executions.

The first control fixture attempt is preserved as a failure, not a pass. The
four new fixtures initially asserted convergence before PowerGrid completed
its startup warmup. The loaded `NativeMNA` bytecode shows that successful native
solves still withhold acceptance while `warmUpTicks` is positive. Fixture
initialization now performs the same three-call settling used by the existing
solver fixtures; subsequent single-step assertions are unchanged. The fixed
fixture jar is `native-reduction-fixtures-02`; no native candidate code was
changed for that correction.

## Separate path-count diagnostic

The clean candidate has no counters. A separate marked DLL adds four atomic
counters only for 14,925-row arrays: fast and scalar-fallback counts for the
finite check and norm. Removing the instrumentation reproduces the candidate
source. The diagnostic jar preserves all 294 gameplay class entries and uses
the same expanded fixture jar. The normal benchmark entry point rejects its
diagnostic marker; the dedicated entry point requires detailed/native-work
observation and rejects the C-stage snapshot mode.

The first observer (`goal-native-reduction-counts-01`) passed the yard, storage,
16-substep and save checks and the JVM exited normally. Its **counter check
failed**: it printed only at solver destruction, and all those reports were
zero, before the live full-sized yard was running. No final teardown report
followed. It supplies no fast/fallback conclusion and is preserved separately
from passing diagnostics. Its native artifact had passed 20 focused fixtures.

The revised observer retains the original source proof and adds a report every
4,096 calls of each path, with serialized output. These counts include loading
and warmup and can omit calls after the last periodic report. They do not
distinguish subnormal from nonfinite values or identify individual substeps.
Detailed timers, atomic counts and logging make this a diagnostic observation,
not a clean throughput comparison.

`goal-native-reduction-live-counts-01` completes with **249,856 finite fast
checks and 107,928 norm fast reductions**, with **zero reported fallbacks**.
The revised artifact passes its 20 selected native fixtures, the complete
yard/storage/substep checks and normal save/shutdown. Its last reported
interval therefore provides no evidence that subnormal fallback is defeating
the fast path. It cannot establish what occurred in the uninstrumented clean
pair or in calls after its final periodic report.

The JNI observer records 2,112 complete sampled stable calls. The earlier
C-stage diagnostic and both reduction diagnostics each contain 1,344 initial
residual exits and 768 calls with two iteration callbacks / three residual
checks. Native-call time is lower in the reduction diagnostics, but the
instrumentation differs, so this is exploratory evidence rather than a
matched speedup measurement. The live-counter run measures 47.337 ms mean and
52.852 ms p95 overall; it remains outside the 50 ms p95 target and is not a
clean comparison.

The next check is a reversed-order clean pair using the already-tested jars.
Do not change the fallback semantics on the strength of this result. The
retained gameplay build stays unchanged while the difference between local
native time and end-to-end performance is unresolved.

[Recorded results and hashes](native-reduction-results-20261109.json) retain
the clean comparison, numerical checks, diagnostic counts and failed first
observer separately.

## Reproduce

Use fresh directory names. Compilation reuses immutable native objects and
archives from `native-stages-01` instead of rebuilding BLAS. Numerical checks
and the microbenchmark run on Windows; compilation uses the existing WSL
distribution and isolated compiler.

```text
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/build_native_reduction.py --name native-reduction-new --baseline-build local/native-stages-01/build.json --toolchain local/native-toolchain-01/inputs.json
python -B tools/experimental/run_native_reduction_checks.py --build local/native-reduction-new/build.json --toolchain local/native-toolchain-01/inputs.json --baseline-checks local/native-stages-01/native-safety-checks.json
python -B tools/experimental/freeze_native_reduction_fixtures.py --name native-reduction-fixtures-new --inputs local/frozen/prototype-12g/inputs.json
python -B tools/experimental/freeze_native_reduction.py --name native-reduction-candidate-new --role candidate --base-inputs local/frozen/prototype-12g/inputs.json --native-build local/native-reduction-new/build.json --native-checks local/native-reduction-new/checks.json --fixture-inputs local/frozen/native-reduction-fixtures-new/inputs.json
```

Freeze a separate `--role control` artifact with the same fixture input, then
run `focused_checks.py --selection native --backend NATIVE` for each manifest
with the retained batching/restoration flags. Use the clean affinity helper
and disable detailed timers/JFR for the copied-yard comparison.

For the separate path observation, preserve both build stages. The first
observer's source proof is reused by the live-report observer; use a fresh
name for each attempt:

```text
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/build_native_reduction_observer.py --name reduction-counts-build-new --candidate-build local/native-reduction-new/build.json
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/build_native_reduction_live_observer.py --name reduction-live-counts-build-new --base-observer-build local/reduction-counts-build-new/build.json
python -B tools/experimental/freeze_native_reduction_observer.py --name reduction-live-counts-new --candidate-inputs local/frozen/native-reduction-candidate-new/inputs.json --observer-build local/reduction-live-counts-build-new/build.json
```

Run `run_native_reduction_live_observation.py` with the retained flags,
`--detailed --native-work`, the explicitly frozen `native-work-helper-02`
input, and a fresh copied-save attempt name. The teardown-only entry point is
preserved for the failed attempt's provenance; it is not the recommended
observer for this yard's shutdown lifecycle.
