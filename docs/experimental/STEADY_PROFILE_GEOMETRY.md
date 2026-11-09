# Steady profile after CEE geometry reuse

Experimental development snapshot, November 9, 2026, on
`dev/experimental-plain-wire-batching` at `cdc907b`. This profiles the unchanged
`0.2.7-experimental.wiretree.8` candidate, frozen at
`local/frozen/prototype-09c/inputs.json`. The runtime SHA-256 is
`7a8cfa0973a73ac490e7cb1f1e1d8b4eff6bdd1fc9933d067279852e4e8f1204`.
Only one focused diagnostic ran, on a fresh copied benchmark save. Its
[evidence report](cee-geometry-profile-results-20261109.json) verifies all 445
source/build hashes, runtime and helper hashes, and the matching clean runs.

The [clean comparison](CEE_STATE_GEOMETRY.md) remains the performance evidence:
20.027 / 20.031 TPS with 55.781 / 55.303 ms p95, and a subsequent 39.147-second
complete client load. The diagnostic is for finding costs, not replacing those
results or establishing a calibrated instrumentation overhead.

## Remaining measured costs

`goal-cee-geometry-profile-01` retained both wire batches, the 20 ms restoration
budget, unresolved-wire priority, the 2 ms task window and geometry reuse. It
used the same helper and CPU group 12–23 (`fff000`), 120 seconds of warmup and
120 seconds of steady measurement. JFR covered only the steady phase. All graph,
storage, 16-substep, affinity, normal-save and reference-preservation checks
passed; no full acceptance suite ran.

The instrumented process measured 19.272 TPS, 51.629 ms mean and 59.738 ms p95
over 2,310 ticks. There were 128 complete sampled traces: 116 ordinary samples
and 12 forced-edit samples. Weighting the two populations gives 50.791 ms mean
for the sampled ticks. Exclusive phase costs sum to that sampled mean:

| Work | Sampled mean, ms/tick |
| --- | ---: |
| CEE preparation, including synchronization | 7.951 |
| Native terminal preparation | 4.778 |
| Wire-power hooks | 5.615 |
| CEE after-solve processing | 4.025 |
| Terminal waveform sampling | 3.585 |
| Native solve | 4.360 |
| RHS construction | 2.736 |
| CEE gameplay commit | 2.525 |
| Other work inside the Minecraft tick | 10.748 |
| Other electrical scopes | 4.467 |

These are whole method scopes, including guards and callbacks, not estimates
of safely parallel arithmetic. The previously slower packed/worker prototype
remains retired. Native solving alone is still a small part of the total.

## Concrete next target

JFR recorded 8,655 server-thread Java execution samples with no truncated
stacks. Of those, 995 were in native terminal preparation; 447 included its
owner `computeIfAbsent`, 307 included `HashMap.getNode`, and 247 included the
generated terminal-key equality path. These inclusive counts overlap.

Allocation estimates identify avoidable bookkeeping in that preparation pass:
approximately 0.803 GiB of frame-map entries, 0.652 GiB of temporary terminal
keys, 0.616 GiB of capturing owner-lookup functions and 0.518 GiB of owner-map
entries during the recording. The next experiment should first reuse the
per-pass lookup function and replace private frame-map storage with an array
backed map, while retaining complete fresh owner/geometry validation. Frame
publication currently visits each frame independently, so its traversal order
does not determine electrical accumulation or device callback order.

Keep the snapshot of external-node entries: custom geometry callbacks can
change the world during preparation. Preserve null-owner retries, exceptions,
sparse terminal IDs, mutable panel geometry, node/owner replacement checks and
the current completed-tick publication rules. Do not retain owner validity
across ticks or remove the 16 waveform samples. Compare enabled and disabled
modes in the same jar after focused terminal/lifecycle checks. This is a
candidate, not an implemented or measured speedup.

CEE preparation remains another substantial target: 826 Java samples included
`StableDescription.update`, and 329 included `Terminals.refresh`. Wire thermal
updates accounted for 689 samples, including 450 in ambient-temperature/biome
lookup; wire entity queries accounted for 422. These world-dependent paths
need their own correctness analysis before any reuse or worker dispatch.

## Interpretation limits

The diagnostic's thread counters report 23.939 GiB allocated during tick bodies;
retained JFR allocation samples estimate 23.961 GiB across the steady recording.
The first allocation sample carries a 58.941 GiB weight from the recording
boundary and is preserved separately, excluded from site attribution. GC pause
events total 82.562 ms. Allocation matters here as processing and memory traffic,
not as evidence that GC pauses explain the tick budget.

Compilation totaled 16.051 seconds in the diagnostic, compared with 1.142 / 1.362
seconds in the matching clean enabled processes. Diagnostic allocation averaged
10.612 MiB/tick, versus 13.037 / 13.021 MiB in those clean processes. Therefore
this profile does not isolate the cause of the on/off whole-tick allocation
difference. JIT and instrumentation effects remain a limitation. The 149 native
method samples identify JNI entry points and use a different sampling period;
their percentages must not be combined with Java execution-sample percentages.

The practical p95 target remains unmet, loading still has multi-second stalls,
and the dimension-wide electrical hold during continuing wire restoration is
still an open functionality concern. The overall goal remains active.

```text
python -B tools/experimental/summarize_current_profile.py --attempt goal-cee-geometry-profile-01 --inputs local/frozen/prototype-09c/inputs.json --clean-report docs/experimental/cee-geometry-tps-results-20261109.json --output docs/experimental/new-profile-report.json
```

This command analyzes preserved evidence and writes a new report. It does not
launch Minecraft or rerun checks. Always use a new output path.
