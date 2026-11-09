# Terminal key lookup experiment

Experimental development snapshot, November 9, 2026, on
`dev/experimental-plain-wire-batching`. The preceding
[steady profile](STEADY_PROFILE_GEOMETRY.md) identified generated terminal-key
equality in 247 Java execution samples and approximately 0.448 GiB of sampled
allocation from `BlockWireEndpoint.hashCode` arrays in CEE preparation.
These are diagnostic observations, not predictions of a speedup.

## Decision

Retire the runtime change. The matched enabled/disabled comparison was
effectively flat: **49.882 versus 49.855 ms/tick**, with **57.523 versus
57.421 ms p95**. This is neither a demonstrated speedup nor evidence of a
meaningful regression. Lower allocation alone does not justify retaining the
prototype for the TPS/loading objective. Preserve its sources and measurements,
then restore the preceding `wiretree.8` candidate.

## Scope

`-Dwwpg.experimental.terminalKeyLookup=true` selects two changes:

- Private native-terminal frame keys use explicit equality and hash methods.
  The control uses the same generated record behavior as before. Both retain
  immutable positions and the complete terminal ID, with identical hash values.
  Position equality retains its reference shortcut and callback behavior.
- PG block endpoints compute the same `Objects.hash(position, terminal)` value
  directly, including null positions and integer overflow. The method reads
  both fields before calling position hashing, as PG does. It does not retain
  hashes: mutable positions and deserialization still affect the next call.

The flag defaults to false. Current/previous frame maps remain JDK `HashMap`s,
and owner lookup functions retain their original lifetime. The rejected
[bookkeeping prototype](TERMINAL_BOOKKEEPING.md) remains retired. Live owner,
chunk, geometry, node and network validation, external-node snapshots, all 16
substeps, publication, device callbacks, heating and storage remain unchanged.
No work moves to another thread and no persistent data format changes.

The measured candidate is `0.2.7-experimental.wiretree.10`, frozen at
`local/frozen/prototype-11b/inputs.json`. Its runtime SHA-256 is
`c844a469433b14d009059f2a529adbeaafad28d024eeade02aa976937f3744bf`;
the fixture SHA-256 is
`e15190c0d4493b9aebbe65c41ec68c314f6356559f5d2dfe22589cf67072ba5b`.
The manifest records 448 source/build entries. Native solver bytes are
unchanged; CEE 1.1.4, PG 0.6.2, Create 6.0.10-280 and NeoForge 21.1.231 remain
the pinned runtime dependencies.

## Focused verification

The corrected candidate passed **102 focused checks**, 34 per mode: native
enabled, Java enabled and native disabled. The existing selection covers
terminal preparation, complete/incomplete waveforms, late rejection, editable
terminals, owner replacement, actual unload, public API ownership and solver
safety. The two added differential fixtures check 512 key pairs, three custom
position equality cases and 40 endpoint cases per mode, including NBT
round-trips, mutable positions and large/negative terminal IDs. They compare
against generated record behavior and PG's original hash operation.

Independent API example jars are explicitly loaded and hash-checked using
`local/frozen/prototype-03d/inputs.json`; only its two addon roles are used.
The runtime and fixture roles come from `prototype-11b`. No full headless or
graphical acceptance suite ran.

An earlier `prototype-11a` also passed its 102 narrower checks. Source review
then found that its direct equality call omitted the generated record's
reference shortcut for a custom position object. The runtime and fixture were
corrected, rebuilt and checked again. The old two source files, frozen inputs
and completed checks are preserved. Its `goal-terminal-keys-on-01` benchmark
was deliberately cancelled during warmup; the owned server saved and exited
normally. That interrupted attempt contributes no steady-performance evidence.

## Measurement results

The corrected enabled/disabled comparison used the same candidate jar,
queue-aware helper and CPU group 12–23 (`fff000`), both wire batches, 20 ms
restoration budget, unresolved-wire priority, 2 ms task window and CEE state
geometry reuse. Each process had a fresh copied save, 120 seconds of warmup
and 120 seconds of steady measurement. The key-lookup flag was the only mode
difference.

| Attempt | Key lookup | TPS | Mean tick, ms | p95 tick, ms | Maximum tick, ms | Allocation, MiB/tick |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `goal-terminal-keys-on-02` | On | 19.993 | 49.882 | 57.523 | 74.690 | 10.675 |
| `goal-terminal-keys-off-02` | Off | 20.004 | 49.855 | 57.421 | 69.444 | 11.905 |

The enabled run was 0.055% longer on mean tick time. Server CPU time was
49.666 versus 49.632 ms/tick. Allocation fell by about 1.230 MiB/tick, but that
did not translate into a measured tick-time gain. Compilation counters were
1.205 versus 1.260 seconds, and GC counters were 62 versus 75 ms. Roughly
43–44% of ticks exceeded 50 ms, and neither run met the p95 consistency gate.
These are finite samples without statistical-significance or causal
attribution claims for individual allocation sites.

Both runs passed the complete 14,650-node yard checks: 20,637 edges, one native
solver network, 1,050 storage devices, 16 substeps per measured tick, unchanged
topology, normal save/shutdown, process affinity and reference-save preservation.
There were 2,396 enabled and 2,397 disabled steady ticks. Headless restoration
took 28.743 and 28.859 seconds respectively. No client load or actual reload
trial was launched for this rejected candidate, and these server timers do
not establish rendered-client loading performance.

The [aggregate evidence](terminal-keys-results-20261109.json) verifies the
corrected frozen artifacts, 448 source/build entries, focused checks and
superseded/interrupted evidence. The [steady report](terminal-keys-tps-results-20261109.json)
retains raw hashes, host observations, protocols and 30-second blocks. Run the
aggregate generator from this candidate's source snapshot; it rejects source
mismatches and existing output paths.

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-11b/wwpg-0.2.7-experimental.wiretree.10.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --terminal-key-lookup --name keys-new
```

Omit `--terminal-key-lookup` for the same-jar control. Always use a new attempt
name. The report generator is `tools/experimental/summarize_terminal_keys.py`;
it verifies frozen sources and evidence before creating a new output file.
