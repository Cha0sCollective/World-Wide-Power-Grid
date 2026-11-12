# Terminal preparation bookkeeping experiment

Experimental development snapshot, November 9, 2026, on
`dev/experimental-plain-wire-batching`. The preceding
[profile](STEADY_PROFILE_GEOMETRY.md) measured 4.778 ms per tick in native
terminal preparation, with substantial allocation in map entries and capturing
owner-lookup functions. This prototype targets those costs. Only focused
checks and fresh copies of the preserved benchmark save are used.

## Decision

Do not retain the runtime change. It reduced observed allocation but did not
produce a repeatable tick-time gain. The enabled samples averaged **48.823 ms**,
versus **48.743 ms** for the disabled control, or **0.16% longer**. This small
comparison does not establish a statistically significant regression; it also
does not justify adding the prototype to the working candidate. Preserve its
source, frozen artifacts, failed setup and passing evidence, then restore the
preceding `wiretree.8` runtime. The overall performance goal remains open.

Candidate source is preserved at `7247ef9`. The following retirement restored
all 445 source/build entries from the preceding `64500e6` snapshot, with no
extra runtime source left behind. Rebuilt runtime and fixture jars are
byte-identical to `local/frozen/prototype-09c/inputs.json`; see the
[retirement proof](terminal-bookkeeping-retirement-20261109.json). The retained
runtime SHA-256 is
`7a8cfa0973a73ac490e7cb1f1e1d8b4eff6bdd1fc9933d067279852e4e8f1204`.
Its named earlier client, steady and reload results are reused through that
comparison. No gameplay test rerun is represented as a new pass.

## Scope

`-Dwwpg.experimental.terminalBookkeeping=true` selects two changes:

- The private current/previous frame maps use `Object2ObjectOpenHashMap`,
  retaining their existing swap-and-clear lifetime and unchanged terminal keys.
- One capturing owner-lookup function is created per preparation pass and reused
  for its endpoint lookups. The owner `HashMap.computeIfAbsent` path is retained,
  including retries for null results and its exception behavior.

The flag defaults to false. This is bookkeeping reuse, not retained ownership
or geometry validity. Every pass still checks loaded/ticking chunks, entities,
owners, terminal IDs and solver ownership. The external-node entry snapshot is
kept because geometry callbacks can change the world. Waveforms retain all
16 substeps, and stale/rejected/incomplete frames remain unavailable.

Frame-map iteration order changes only the final independent completeness
checks. Sampling lists are still populated in external-node snapshot order;
electrical accumulation and device callbacks retain their original order.
No solver work, storage, heating or callbacks move to workers.

The runtime is `0.2.7-experimental.wiretree.9`, frozen at
`local/frozen/prototype-10a/inputs.json`, SHA-256
`2b768c7e03017322338423affe6c5162ba8a575453e5d8646721504af57937dd`.
The fixture jar hash is
`3e81e63df19b019e2db076212f3d7e09c518c1754c4aa94e02535710818aeadf`.
Its 446 source/build entries are separately recorded; native solver bytes are
unchanged. CEE 1.1.4, PG 0.6.2, Create 6.0.10-280 and NeoForge 21.1.231 remain
the pinned runtime dependencies.

## Focused verification

Three completed runs passed **96 focused checks**, 32 per mode. The selected
fixtures cover native terminal preparation, complete/incomplete
mid-line waveforms, late rejection and recovery, public API ownership,
replacement, editable terminals, actual unload and solver safety. They run with
the feature enabled on native and Java solvers, plus a disabled native control.
No new tests duplicate the map implementation or standard collection behavior.

`terminal-bookkeeping-native-01` failed four ownership fixtures because the
runner omitted their independent API example addons. Its logs and failed result
are preserved. The corrected runner requires explicit addon inputs for this
selection and verifies their hashes before/after execution and in the packaged
classpath proof. The gameplay runtime and fixtures were not changed for that
correction.

The two addon jars are reused from `local/frozen/prototype-03d/inputs.json`:
public example `17ef99aa7fb06acab09efb67badd3b27aff30250167049177d952ee86b46fcef`
and upstream example `5ef5ab7ed965923d89834dc9823da5e110307559e11380cb650ec28db1acca11`.
Only those addon roles are loaded from that historical manifest; its retired
packed runtime is not loaded. Reusing the jars does not relabel old acceptance
as a new pass.

## Measurements

The matched enabled/disabled/enabled comparison retained CEE geometry reuse,
both wire batches, the 20 ms restoration budget, unresolved-wire priority, the
2 ms task window and CPU group 12–23 (`fff000`). Every process used the same
gameplay jar and helper, a fresh save copy, 120 seconds of warmup and a
120-second steady sample. All three completed with 2,401 measured ticks.

| Execution order | Bookkeeping | TPS | Mean tick, ms | p95 tick, ms | Maximum tick, ms | Allocation, MiB/tick |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `goal-terminal-bookkeeping-on-01` | On | 20.030 | 48.385 | 55.180 | 70.620 | 12.056 |
| `goal-terminal-bookkeeping-off-01` | Off | 20.030 | 48.743 | 55.320 | 66.515 | 12.998 |
| `goal-terminal-bookkeeping-on-02` | On | 20.047 | 49.262 | 56.795 | 74.947 | 12.146 |

Enabled allocation averaged 12.101 MiB/tick, approximately 0.898 MiB below the
control. Enabled server CPU time averaged 48.443 ms/tick versus 48.300 ms in
the control. Compilation time was 1.401 / 2.184 seconds in enabled samples
and 1.248 seconds in the control; GC pause counters were 75 / 106 ms versus
88 ms. These finite samples do not isolate JIT, host or run-order effects.
The first small speedup did not repeat. The p95 50 ms gate remained unmet in
all three runs; roughly 41–45% of ticks exceeded 50 ms.

Each run verified 14,650 nodes, 20,637 edges, one native solver network, all
1,050 storage devices, 16 substeps per measured tick, unchanged topology,
normal save/shutdown, process affinity and preserved reference saves. The
enabled counter reached 11,900 terminal frames in one pass. No full acceptance
suite ran. Headless initial restoration took 29.334 / 28.790 / 28.222 seconds;
these are not rendered-client loading measurements. No new client or actual
unload/reload run was launched for this rejected candidate.

The [aggregate evidence](terminal-bookkeeping-results-20261109.json) verifies
446 source/build entries, frozen artifacts and 96 passing focused checks.
The [steady report](terminal-bookkeeping-tps-results-20261109.json) retains
per-run protocols, raw-evidence hashes, host observations and 30-second blocks.
The report generator must be run from this candidate's source snapshot;
it deliberately rejects source mismatches and existing output paths.

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-10a/wwpg-0.2.7-experimental.wiretree.9.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --terminal-bookkeeping --name terminals-new
```

Omit `--terminal-bookkeeping` for the same-jar control. Always use a new name.
