# CEE state-only terminal geometry experiment

Experimental development snapshot, November 9, 2026, on the isolated
`dev/experimental-plain-wire-batching` branch. The preceding
[chunk-task window](CHUNK_TASK_WINDOW.md) reached complete client loading in
39.30–40.41 seconds, but its steady sample still had 56.03 ms p95 ticks.
This prototype targets repeated terminal preparation while retaining that
loading behavior. Only focused checks and copied benchmark saves are used.

## Why this path

The prior [profile](BATCHED_PROFILE.md) and [client diagnosis](CLIENT_DELIVERY.md)
identified terminal/device preparation as substantial server work. The mixed
benchmark contains 1,475 CEE creative resistors, 500 CEE capacitors and 25 CEE
creative batteries, as well as 500 panels with editable terminal geometry.
`NativeApiReadings` rebuilds CEE terminal ID sets per owner during preparation;
`CeeElectric` repeatedly constructs sorted maps for native terminal operations.

Inspection of the pinned CEE 1.1.4 jar shows that `NodeConfigurator` already
memoizes rotations. The proposed saving is therefore not elimination of all
rotation computation: it avoids repeated state/configuration lookup, wrapper
construction for rolled states, sorted map copies and terminal ID sets in
WWPG's paths. The bytecode capture and extracted calls are preserved in
`local/cee-geometry-source-01`; the jar hash is
`2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f`.

## Prototype scope

`-Dwwpg.experimental.ceeStateGeometry=true` opts in; default is false. It reuses
immutable sorted coordinate maps and immutable terminal ID sets for ten exact
built-in classes: resistor, capacitor, inductor, creative battery, transformer,
resistive heater, connector, insulator, bulb and fuse. Their inspected coordinate
methods read only block state and fixed node configurations, and eligibility
also requires the inherited always-accessible CEE method. Subclasses are excluded.

Panels and unreviewed/custom types keep the original callback order and frequency.
Indicator bulbs are deliberately excluded because their coordinate method removes
entries from a shared memoized map. This prototype does not change that upstream
behavior. Node labels and sizes stay live even for eligible blocks.

The cache belongs to the server thread, holds no worlds, positions, entities or
circuits, and clears when that server stops. Client and off-thread requests keep
the original path. Keys are complete immutable block states, so rotation and
other state changes select different entries. At 2,048 stored states, new states
fall back to the original callbacks. Existing loaded-chunk, entity, ticking,
owner-identity and electrical-availability checks remain in place before reuse.
No simulation substeps, heating, storage, gameplay callbacks or solver work are
removed or moved to workers.

The runtime is `0.2.7-experimental.wiretree.8`, frozen in
`local/frozen/prototype-09c/inputs.json`, SHA-256
`7a8cfa0973a73ac490e7cb1f1e1d8b4eff6bdd1fc9933d067279852e4e8f1204`.
Native solver bytes are unchanged. The clean client observer remains
byte-identical to the preceding observer, SHA-256
`e8c164a8908cc519976abbd56a7709af2791bf1fa0e9d7e62271e8f6b5adb015`,
with its new manifest at `local/frozen/client-cee-geometry-helper-01/inputs.json`.

## Focused verification

Three completed runs each passed 33 checks: native enabled, Java enabled and
native disabled. They cover all **2,716 states across ten reviewed classes**,
coordinate/ID equality and ordering, immutable outputs, owner-independent reuse,
capacity fallback, custom sparse callback order and errors, powered panel edits,
owner replacement, native measurement preparation, unload and solver safety.
They are preserved at `local/checks/cee-geometry-native-03`,
`cee-geometry-java-01` and `cee-geometry-off-01`.

Two earlier failed fixture attempts remain preserved. `cee-geometry-native-01`
could not start the new namespace because its empty structure template was
missing. `cee-geometry-native-02` incorrectly required a cache entry after
exceeding the configured size bound. The final fixture separately verifies every
reviewed state on the cache path and overflow on the original callback path.
Both were fixture corrections; all three frozen `prototype-09{a,b,c}` runtime
jars are byte-identical, while their fixture jars/build inventories differ.

No full headless or graphical acceptance suite ran. Existing unrelated unit
checks were not rerun or relabelled as new evidence.

## Measurements

Three same-jar measurements ran in enabled/disabled/enabled order. They retain
plain/property-wire batching, a 20 ms wire-restoration budget, unresolved-wire
priority, the 2 ms chunk-task window, all 16 electrical substeps and CPU group
12–23 (`fff000`). Each uses a fresh save copy, 120 seconds of warmup and a
120-second stable sample. Actual loaded artifacts, graph/storage correctness,
affinity and reference-save preservation passed in all three runs.

| Attempt | Cache | TPS | Mean tick, ms | p95 tick, ms |
| --- | --- | ---: | ---: | ---: |
| `goal-cee-geometry-on-01` | Enabled | 20.027 | 49.077 | 55.781 |
| `goal-cee-geometry-off-01` | Disabled | 19.480 | 51.201 | 58.675 |
| `goal-cee-geometry-on-02` | Enabled | 20.031 | 48.616 | 55.303 |

Enabled mean tick time averages **48.847 ms**, **4.60% below** the disabled
control. Both enabled observations meet average 20 TPS, but neither meets the
existing practical gate of at least 19.9 TPS and p95 at or below 50 ms. These
finite samples support retaining this opt-in prototype; they do not establish
statistical significance, sustained capacity, or a 4.60% gain over every prior
runtime observation. [Steady evidence](cee-geometry-tps-results-20261109.json).

Server CPU time was 48.79 / 48.38 ms per enabled tick versus 50.94 ms disabled.
Whole-tick allocation was higher with caching: 13.04 / 13.02 MiB per tick
versus 11.89 MiB disabled. Compilation totals were 1.14 / 1.36 seconds enabled
and 1.26 seconds disabled; reported stable GC time was 87 / 82 ms versus 75 ms.
Thus the measured benefit is CPU time, not a demonstrated reduction in total
allocation. The source removes repeated geometry work, but a fresh profile is
needed to attribute the whole-runtime allocation difference.

The subsequent clean client reached full restored/rendered readiness in
**39.147 seconds**, with electrical readiness at **36.987 seconds** and the
first world frame at **14.047 seconds**. All 609 view chunks were received,
visible sections were compiled, render queues were empty, and readiness held
for more than two seconds. The screenshot was inspected; devices and wires are
visible. The largest loading tick was **2.862 seconds**, with a **2.879-second**
tick-to-tick gap. World-frame intervals had **20.542 ms p95**, **40.332 ms max**.
Settings remain 1920×1080, render/simulation distance 12, fancy graphics, 60 FPS
cap and VSync off. This preserves the sub-minute loading observation; without
a same-jar disabled client sample it does not establish a loading speedup from
the geometry cache. [Client evidence](client-cee-geometry-results-20261109.json).

`goal-cee-geometry-reload-01` also passed actual full-yard unload/reload, with
initial/reload restoration times of **28.639 / 17.069 seconds**. All 953 chunks
were genuinely unloaded before reloading; topology, 1,050 storage devices and
100 healthy ticks passed. These are headless restoration times, not complete
client-loading times. [Combined evidence](cee-geometry-results-20261109.json).

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-09c/wwpg-0.2.7-experimental.wiretree.8.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --name geometry-new
```

Omit `--cee-state-geometry` for the same-jar control. Always use a new name.
