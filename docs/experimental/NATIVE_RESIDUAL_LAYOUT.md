# Offline native residual layouts, November 9, 2026

The four-row SIMD prototype reduces residual multiplication time by about
30% on three actual 14,925-row benchmark inputs. Both scalar row layouts are
slower. The SIMD result is an offline lead: it has not been integrated into
the game, and it establishes neither a TPS improvement nor a loading gain.
The retained `prototype-12g` runtime and its source inventory are unchanged.

## Actual inputs and capture checks

`goal-native-residual-capture-01` used a fresh copy of the 14,650-node yard,
the exact-reduction candidate plus a separately marked capture observer,
the native-work helper, and process affinity `fff000`. All graph, storage,
16-substep, source-save, native identity and normal shutdown checks passed.
The diagnostic keeps detailed timers enabled, so its tick timings are not a
clean performance comparison. It does not supply new client-loading evidence.

The observer copies the compressed-column matrix, state, residual input,
original result and floating-point status around the original `sp_dgemv` call.
It captures outer calls 4,096 and 40,960, then the first fitting call after
the latter threshold (outer ordinal 41,480 here). These ordinals do not prove
that a capture belongs to the stable sample. The original function executes
once; capture bookkeeping restores its input and output MXCSR. The three
standalone capture-path checks preserve output bytes and flags, and the parser
rejects truncated, extended and bad-magic recordings. Existing native imports
and exports match. All 294 gameplay class entries remain byte-identical.

Each capture has 14,925 rows and 57,097 entries. All values are finite, with
no zero state entries. Their row-degree distribution is identical:

| Entries per row | Number of rows |
| --- | ---: |
| 3 | 4,377 |
| 4 | 8,998 |
| 5 | 1,426 |
| 6 | 74 |
| 8 | 50 |

The saved reference outputs replay exactly against the frozen SuperLU archive.
The captures are under `local/benchmarks/goal-native-residual-capture-01`;
their hashes, runtime identity and all following observations are recorded in
[the result manifest](native-residual-results-20261109.json).

## Layout comparison

All versions multiply the original residual input by beta before adding
matrix contributions. Entries within each row retain their original column
order. Computing a dot product first and subtracting the residual afterward
would change rounding and is not this prototype.

The indexed row layout follows an extra index to current matrix values. The
packed scalar layout copies values into row order. Both retain original
zero-state comparisons and column scaling. They pass 192 exact full-vector
and MXCSR comparisons across the three captures, 16 rounding/DAZ/FTZ modes,
and clear/set initial status flags, but are slower than the reference.

The tiled layout groups rows by degree and processes four independent rows
with AVX2. Each lane retains its own ordered multiply and add operations;
there is no reassociation or fused multiply-add. It supports the residual's
alpha=1, beta=-1 case with finite matrix/input values, normal nonzero state
values, non-overlapping vectors and masked floating-point exceptions. Other
cases use the original `sp_dgemv`. Beta multiplication is preserved even for
subnormal residual inputs. Integer classification adds no FP exceptions.

The final offline build, `native-residual-tiled-02`, measured:

| Capture | Reference, ms/call | Indexed scalar | Packed scalar | Four-row SIMD | SIMD reduction |
| --- | ---: | ---: | ---: | ---: | ---: |
| Outer 4,096 | 0.044716 | 0.070709 | 0.054219 | 0.031389 | 29.80% |
| Outer 40,960 | 0.045096 | 0.070528 | 0.053993 | 0.031142 | 30.94% |
| Fitting | 0.044933 | 0.069953 | 0.053845 | 0.031055 | 30.89% |

Each capture uses 12 rotating-order blocks of 512 calls per implementation.
The same output reset is included in every measured call. Layout construction
and refresh are excluded. The first SIMD build independently measured
30.88-31.75% reductions; this remains repeated-input microbenchmark evidence,
with no CPU-frequency/thermal control or independent workload sampling.

At 48 residual calls in a 16-substep one-solve tick, the final per-call
difference projects to about 0.66 ms before integration overhead. This is an
arithmetic estimate, not a measured tick gain. It is unlikely to close the
remaining p95 gap alone. The scalar layouts are not candidates for integration.

## Focused numerical checks and limits

The final build passes all 288 actual-input full-vector/MXCSR comparisons
across the three alternative kernels, plus **79,120** additional tiled
output/status/MXCSR comparisons. These cover sizes 0-129, vector tails,
variable/full/empty row structures, signed zero, subnormal values, NaN/Inf,
overflow, underflow, cancellation, alternate alpha/beta values, overlapping
vectors, and exact inputs with exception masks disabled. Buffers are tested
at both guarded boundaries. There are 2,430 explicit value refreshes and
84 fresh plan builds across dimensions and structures.

These are standalone numerical checks. The fresh builds do not test automatic
invalidation in a live solver. The caller currently owns all rebuild/refresh
decisions, and no gameplay cache has been introduced. They also do not prove
all possible FP inputs, hardware, or mod functionality. No new full headless
or graphical acceptance was run.

The first scalar replay runner stopped before executing native code because
its in-memory integer histogram keys were compared directly with JSON string
keys. Its failed `checks.json` is preserved. `checks-02.json` uses normalized
metadata with the same immutable executable and captures; it passes. Later
builds snapshot their source and runner, preserving each attempt separately.

## Integration requirements

Before a gameplay comparison:

1. Give each native solver/matrix its own cache and clean it up on destruction,
   zeroing and resize. Allocation failure must fall back to the original path.
2. Invalidate structure on insertion, zeroing and resizing; refresh values on
   every set/add and callback-returned Jacobian change. Do not borrow the
   existing structure/refactor flags: logical-matrix construction and
   factorization consume them. Audit any raw matrix-pointer writer as well.
3. Resolve the current logical matrix after callback-driven changes, including
   the fitting loop. Its existing local `A` variable survives callbacks, so
   cache validity cannot be inferred from that pointer's identity alone.
4. Add focused live-matrix insertion, value-change, clear/repopulate, resize,
   refactor, cleanup, allocation-failure and callback tests. Preserve every
   callback, all 16 substeps, finite/permutation/factorization guards, rollback
   and convergence criteria. Keep this work separate from the retired worker
   and packed serial result-processing experiment.
5. Freeze a new native-only jar, verify its loaded DLL and unchanged gameplay
   classes, run the selected native fixtures, then compare clean copied-yard
   timings with the exact-reduction control. Measure plan setup/refresh cost
   as well as steady behavior. Recheck client loading if a candidate is retained.

Reusable triangular-solve workspace remains a separate possible saving. Its
allocation cost has not yet been isolated. Neither this layout nor the prior
reduction alone establishes consistent 20 TPS.

## Reproduce

Use fresh names and the preserved capture manifest. Compilation uses the
existing WSL distribution and isolated compiler; checks execute on Windows.
No compiler install or BLAS rebuild is required.

```text
wsl -d Ubuntu-22.04 -- python3 /mnt/g/WWPG/local/x27w/tools/experimental/build_native_residual_replay.py --name native-residual-replay-new --capture-build local/native-residual-capture-01/build.json
python -B tools/experimental/run_native_residual_replay.py --build local/native-residual-replay-new/build.json --captures local/benchmarks/goal-native-residual-capture-01/native-residual-captures.json
```

For a new capture, build/freeze through `build_native_residual_capture.py`
and `native_residual_capture.py`, then use its dedicated `run` entry point
with the retained flags, copied save, explicit native-work helper,
`--detailed --native-work`, and a fresh attempt name. The ordinary clean
benchmark entry point rejects this diagnostic jar.
