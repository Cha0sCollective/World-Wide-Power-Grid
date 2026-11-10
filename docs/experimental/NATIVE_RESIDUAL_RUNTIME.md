# Native residual-cache runtime experiment

November 9, 2026 experimental snapshot, following the
[offline layout comparison](NATIVE_RESIDUAL_LAYOUT.md).

The native-only prototype gives each solver its own four-row AVX2 residual
layout for matrices with at least 64 rows. Jacobian writes invalidate cached
values independently of factorization flags. Insertions rebuild the layout;
clearing, resizing and destruction release it. Allocation failure uses the
original residual operation. The reviewed JNI command buffers are the writers
to this private matrix; arbitrary external native-memory writes are outside
that interface.

Both comparison variants resolve current logical A after callback changes,
including fitting iterations. Structural changes can discard old factors;
the next iteration requests fresh factorization when those factors are absent.
Only the candidate enables the layout cache. All 294 Java gameplay classes
remain unchanged, as do callbacks, 16 substeps, convergence criteria, guards
and rollback.

## Focused correctness evidence

`local/native-residual-runtime-02/checks.json` passes 2,048 live-matrix
comparisons for the control and 2,184 for the candidate, comparing output bits,
return status and MXCSR. The candidate passes 108 lifecycle cycles and all
17 cache-allocation failure points. DLL imports and exports match the baseline.
The exact kernels also retain the earlier offline numerical evidence.

Both frozen variants pass 24 selected game fixtures each, including four new
JNI cases for changing substeps, insertion/clear/resize, fitting-time value
changes and fitting-time structural insertions requiring buffer reallocation
and refactorization. Results are under `local/checks/native-residual-*-01`.
These focused checks do not establish all possible mod behavior.

## Measurement status

The control `goal-native-residual-control-01` completed normally with source-save,
graph, storage, native-identity and 16-substep checks passing. It measured
20.014 TPS, 47.830 ms mean, 55.778 ms p95, 68.765 ms p99 and 90.731 ms maximum
over 2,398 stable ticks. Headless restoration took 33.466 seconds.

The first candidate attempt stopped at the CPU quietness preflight, before
launching its game process: selected CPUs averaged 16.46% busy against a 5%
limit. Its files remain preserved at `goal-native-residual-candidate-01`.
This failed preflight supplies no candidate performance result.

The fresh `goal-native-residual-candidate-02` passed its own quiet preflight and
completed normally. It measured 17.842 TPS, 55.890 ms mean, 73.374 ms p95,
85.512 ms p99 and 102.938 ms maximum over 2,135 stable ticks. Headless
restoration took 42.528 seconds. All yard/storage/substep, native identity,
source-save and normal shutdown checks passed in both completed runs.

| Metric | Control | Candidate |
| --- | ---: | ---: |
| TPS | 20.014 | 17.842 |
| Mean tick, ms | 47.830 | 55.890 |
| p95 tick, ms | 55.778 | 73.374 |
| p99 tick, ms | 68.765 | 85.512 |
| Maximum tick, ms | 90.731 | 102.938 |
| Headless restoration, s | 33.466 | 42.528 |

The candidate mean was 8.060 ms higher, but the runs were separated by about
an hour and host conditions differed substantially. Recorded other-process
CPU averaged 0.335 cores for the control and 2.036 for the candidate. Another
`javaw.exe` process (PID 18784) averaged 1.563 cores and peaked at 3.835 during
the candidate observation. Its purpose was not identified. The monitor's
`competing_game_present=false` recognizes a specific game name and does not
rule out Java games or other workloads. A quiet startup check did not ensure
quietness throughout the run. No other application was altered.

Consequently this pair supplies no reliable causal speedup or regression
estimate. In the result manifest, `clean_comparison` describes the absence of
diagnostic timers; it does not establish matched host conditions. The practical
recommendation is **do not adopt the candidate** on this evidence. Keep its
source and frozen artifacts experimental, with the retained runtime unchanged.
No retry was launched after this completed pair, following the user's stop request.

[Recorded results](native-residual-runtime-results-20261109.json) include raw
host observations, identities and numerical/fixture evidence. The initial
preflight failure and subsequent quiet probe are preserved separately under
`local/benchmarks/goal-native-residual-candidate-01` and
`local/native-residual-runtime-02/quiet-recheck-01.json`.

The retained `prototype-12g` runtime remains unchanged. One completed pair
would still require repeatability evidence before claiming consistent 20 TPS.
Headless restoration does not measure complete rendered loading. No new full
headless or graphical acceptance is authorized or performed.
