# Prioritizing unresolved saved wires

Experimental investigation, November 9, 2026. This prototype changes saved-wire
restoration order on `dev/experimental-plain-wire-batching`. It is opt-in and
does not change saves, the native solver or the 16-substep electrical schedule.
Normal development and original benchmark saves remain untouched.

## Diagnosis

The read-only `goal-unresolved-inspect-01` run inspected existing saved-part
maps every 100 ticks. It did not resolve endpoints, create owners or request
chunks. Its helper adds work, so its 101.33-second load is diagnostic evidence,
not a clean control for the new prototype.

At tick 600, after all 953 requested chunks were ready, 5,817 of 5,943 saved
parts had lines. Only 126 owned parts remained unresolved, yet 7,202 roots
remained queued. The same 126 parts were unresolved at tick 1,000. The examples
had loaded and ticking chunks, existing block entities, mapped saved nodes and
distinct endpoints. They comprised:

- 50 electrical-panel to electric-motor wires.
- 50 inductor to transformer wires.
- 25 capacitor to creative-battery wires.
- One CEE-resistor to PG-resistor wire.

All 5,943 parts eventually resolved. This does not establish that every delayed
wire was a cycle edge, but it rules out permanently missing endpoints in this
sample. The run revisited 48.83 million endpoints while its FIFO queue waited
to reach the remaining attachments. The existing coverage rule cannot skip a
region containing any unresolved part.

## Prototype

`-Dwwpg.experimental.wireTreePrioritizeUnresolved=true` prioritizes queued
attachments whose saved part has no line. Three priority entries are followed
by one ordinary entry; that fairness counter survives single-entry slices.
Priority is inspected again for each slice. Unprocessed entries retain their
arrival order, including work appended during a callback. The option defaults
to false and requires wire-tree batching.

Every processed attachment retains owner/identity validation, both native root
traversals subject to the existing fully-resolved coverage rule, and the final
connection attempt needed to seed a saved closed loop. No traversal coverage
survives a slice. No attachment is dropped simply because it already has a line.

The measured configuration retains a 20 ms cooperative target and a cap of
32 attachments per tick. Classifying and rebuilding the queue count toward
elapsed time and take linear work in queue length. An atomic connection can
overrun the target; this is not a hard 20 ms tick bound. The existing pause for
all circuits in an affected dimension is still present. Continuous-streaming
behavior and reducing that pause remain open requirements.

## Validation and measurement

The candidate is `0.2.7-experimental.wiretree.6`, frozen in
`local/frozen/prototype-07a/inputs.json`. Gameplay SHA-256:
`522c4cb3c394d7c2f80fd2f73fc637cddab5401540128bfe881d4353db1959bb`.
The guarded native libraries are unchanged. PG 0.6.2, CEE 1.1.4, Create
6.0.10-280, NeoForge 21.1.231 and Java 21.0.12 remain pinned.

Fifteen focused unit tests passed, including randomized resolution comparisons,
fairness across one-entry slices, changing priorities, appended work, discarded
owners, callback failure and deadline accounting. Focused integration checks
passed on the frozen jars: 38 restoration/budget checks and 11 lifecycle checks
on each of the native and Java backends, plus 36 native priority-off controls
(134 total). These are named-scope evidence, not full acceptance.

Clean timing uses the same jar and affinity helper with priority on/off/off/on,
fresh copied saves, the same 20 ms/32-part budget and logical CPUs 12–23
(`fff000`). Each launch checks that the CPU group is quiet and records affinity
throughout execution. No other game's processes or affinity are changed.
The read-only inspector is absent from these timing runs.

The four clean loads passed all graph, storage, 16-substep, shutdown and
reference-save checks:

| Mode | Restoration seconds | Queue recovery after chunk readiness | Endpoint visits | Worst loading ticks |
| --- | --- | --- | --- | --- |
| Priority off | 89.73, 94.44 | 40.50, 46.05 s | 46.23, 48.83 million | 3.11, 3.14 s |
| Priority on | 53.92, 53.10 | 5.21, 5.27 s | 20.86, 20.71 million | 3.06, 3.30 s |

Mean headless restoration falls from 92.08 to 53.51 seconds (41.9%); endpoint
visits fall by 56.3%. Chunk loading remains around 40–42 seconds. The largest
stall is not improved: it remains approximately three seconds. Priority is a
substantial total-loading improvement in these samples, not a stall fix or a
steady-TPS optimization. Two runs per mode do not establish statistical
significance or consistency across machines and other saves. Filesystem caches
were not flushed.

## Real client at 1920×1080 / 12 chunks

`goal-client-priority-01` passed the actual loaded-artifact, electrical,
rendering-readiness, screenshot, save/quit and unchanged-reference checks.
World opening to restored/rendered readiness took **104.81 seconds**. The
first world frame appeared at 18.95 seconds; electrical readiness was reached
at **63.08 seconds**. The prior `wiretree.4` client observation was 121.47
seconds overall and 118.60 seconds to electrical readiness. This is one client
sample per runtime, not the clean headless four-run comparison.

The new client's server restoration took 61.02 seconds: 0.80 requesting chunks,
47.34 loading them, 5.89 draining/recovering the graph and 6.99 settling. Receiving
all 609 requested client chunks remained incomplete after electrical readiness;
the renderer's compilation queue was usually empty during that wait. This
points to investigating delivery/receipt scheduling, not assuming more mesh
workers will fix the remaining delay. Final readiness still requires the full
view, completed visible sections and two stable seconds. None of those checks
were weakened to reach a timing target.

The captured scene shows benchmark devices, platforms and wires. The largest
server loading tick was 2.94 seconds. Frame intervals after the first world
frame averaged 16.79 ms (19.98 ms p95, 34.92 ms maximum); these do not include
the initial loading screen or establish sustained gameplay performance. The
observer jar is byte-identical to the earlier client observer, SHA-256
`e8c164a8908cc519976abbd56a7709af2791bf1fa0e9d7e62271e8f6b5adb015`.
See [client evidence](client-priority-results-20261109.json).

The actual full-yard unload/reload also passed: 14,650 nodes, 20,637 edges,
14,925 matrix rows, one native network and all 1,050 stored devices, with 100
healthy 16-substep ticks after each restoration. Its fresh initial load took
53.37 seconds and the subsequent reload 16.80 seconds. The latter reuses the
running process and is not a fresh world-opening measurement. See the
[diagnosis, focused checks and matched loading evidence](unresolved-priority-results-20261109.json).

## Steady performance

`goal-priority-tps-01` completed 120 seconds of warmup followed by a 120-second
sample: **20.019 TPS**, **48.889 ms mean**, **55.453 ms p95**, **60.356 ms p99**
and **65.931 ms maximum**, over 2,399 sampled ticks. The slightly above-20 rate
comes from the measurement window boundaries, not a higher game tick target.
All stable ticks retained 16 solves and no matrix rebuilds; graph fingerprints,
storage, normal shutdown, CPU placement and reference-save checks passed.

This reaches the average target in one sample but misses the existing p95
50 ms gate. It does not prove consistent 20 TPS or a steady-state speedup from
queue ordering: there is no paired priority-off steady sample. The clean loading
comparison supports the loading improvement independently. See
[steady measurement evidence](priority-tps-results-20261109.json).

The under-one-minute complete-loading target and consistent 20 TPS remain
unmet. The next work is client chunk-delivery attribution and reducing repeated
terminal/device preparation while preserving dynamic behavior.

```text
python -B tools/experimental/restoration_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-07a/wwpg-0.2.7-experimental.wiretree.6.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --name priority-new
```

Omit `--tree-prioritize-unresolved` for a same-jar control. Always choose a new
attempt name. No full headless or graphical acceptance suite was run.
