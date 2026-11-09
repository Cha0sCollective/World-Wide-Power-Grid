# Focused client loading measurement

This is an isolated benchmark observation on November 9, 2026, using the
unchanged `0.2.7-experimental.wiretree.4` gameplay jar. It is not the graphical
acceptance suite. The user selected a windowed **1920×1080** client with a
**12-chunk render distance**. The timer starts at the normal world-opening
entry point, after reaching the title screen; process startup is excluded.

## First completed observation

`goal-client-load-02` passed its focused correctness, rendering-readiness and
normal-save checks. It **did not meet the under-one-minute target**.

| Milestone | Seconds from world opening |
| --- | ---: |
| First unobscured world frame | 21.699 |
| Full electrical restoration accepted | 118.602 |
| Restored visible scene, including two-second readiness confirmation | 121.474 |

The headless-style restoration timer inside this integrated server measured
116.557 seconds: 0.800 seconds requesting chunks, 50.001 seconds waiting for
chunk readiness, 58.881 seconds draining remaining tree work and recovering
the graph, and 6.875 seconds for electrical settling. Rendering and restoration
overlap, so these timers must not be added to the client milestone times or
subtracted to claim an isolated rendering cost.

The server's worst loading tick was **3.332 seconds**, with a **3.348-second**
maximum gap between loading tick observations. After the world first appeared,
5,940 recorded frame intervals averaged 16.797 ms, with 20.114 ms p95 and a
37.753 ms maximum at the configured 60 FPS limit. These frame intervals exclude
the initial loading screens and do not measure sustained electrical TPS.

At completion, all 609 chunks in Minecraft's requested 12-chunk tracking view
were present. The view contained 2,487 visible sections, including 394 with
renderable layers. None were dirty or uncompiled; compilation/upload queues
were empty, all 12 compile buffers were free, and the visibility graph had no
pending full or frustum update. The view stayed ready for 2.043 seconds. The
window was active. The saved 1920×1080 screenshot was inspected and shows the
benchmark's device rows, circuit platforms and connected wires.

The electrical gate recovered 14,650 nodes, 20,637 edges, one native network and
14,925 matrix rows, with all 1,050 saved storage values checked before solving.
Its final 100 distinct ticks executed 16 substeps without matrix rebuilds.
Independent CEE solves and rejected shared solves were zero. The restoration
queue drained completely, discarded no roots and used no forced fallback.
It still visited **46.14 million endpoints**, with 10,567 traversals and 1,219
covered roots; repeated traversal remains a major open target.

The helper returned through Save and Quit, reached the title screen, and exited
normally after the all-dimensions-saved log marker. Original and reference save
inventories stayed unchanged. All 140 process-affinity observations matched the
selected CPUs 12–23 (`fff000`), following a quiet-group preflight.

## Scope and reproducibility

The one stationary view uses simulation distance 12, Fancy graphics, FOV 70,
VSync off and a 60 FPS limit. Its camera is `(1038.5, 74, 1028.5)`, yaw −36°,
pitch 14°. The copied player's creative flight and camera are set during join;
that work is included in the timer. This is a development client launch with
frozen gameplay and helper jars, not a claim about an arbitrary player's mod
pack, video settings, walking route or continuous chunk streaming.

The observer opens the copied save through `WorldOpenFlows.openWorld`, the
normal world-selection operation. It does not bypass integrated-server
startup. Its clock includes startup, all restoration checks, chunk delivery,
visible-section compilation and the readiness confirmation. The readiness
check combines the electrical gate and actual rendering state; chunk
compilation alone is not completion. The screenshot is captured after a
completed frame. This does not establish every custom renderer's correctness,
interaction behavior or full mod functionality; full acceptance remains a
separate, manually requested lane.

The helper extends the preserved queue-aware server helper. Only its `Session`
class changes to locate the integrated save through Minecraft's world path and
expose completion to an observer on the server thread. Its queue gate and
electrical assertions remain unchanged. New client-only observers/accessors
read renderer state. No gameplay class, native solver or original helper jar
is modified. The helper and launcher live under `tools/experimental/` and have
no dependency from ordinary build or acceptance tasks.

| Artifact | SHA-256 |
| --- | --- |
| Gameplay | `33fe56c713a63e3b70e045b7fc50667435b50923f569036e66e189ab8a063ac6` |
| Client observer `client-loading-helper-03` | `e8c164a8908cc519976abbd56a7709af2791bf1fa0e9d7e62271e8f6b5adb015` |
| CEE 1.1.4 | `2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f` |
| Rendered screenshot | `e826d5ff762b3d73d5174ce6faa17545f563c793a2c11fc03be3b1208ac67ef1` |

The helper records actual loaded mod versions and file hashes, including PG
0.6.2, Create 6.0.10 and NeoForge 21.1.231. The runner verifies the gameplay,
CEE, PG, Create and helper hashes against pinned inputs. Java remains 21.0.12.
[Results and evidence hashes](client-loading-results-20261109.json) include the
protocol, loading counters, frame intervals and screenshot path. Raw evidence
is under `local/benchmarks/goal-client-load-02/`.

The preceding `goal-client-load-01` failed during observer initialization,
before opening the world, because the mod entry point shared a mixin-only
package. It was cancelled through its owned runner and is retained as a failed
attempt. A preceding helper build also failed while locating the named Gradle
CEE dependency; that build log is retained. Neither is counted as a timing
sample or passing check.

```text
python -B tools/experimental/freeze_client_loading.py --name client-helper-new --inputs local/frozen/prototype-05b/inputs.json --base-helper-inputs local/frozen/affinity-helper-02/inputs.json
python -B tools/experimental/client_loading_benchmark.py --name client-load-new --inputs local/frozen/client-helper-new/inputs.json
```

Every invocation requires a new output name. Preparation compiles only the
separate helper and prepares client arguments; it does not run either full
acceptance suite. The runner launches only when the selected CPU group passes
the quiet observation and retains failed/interrupted evidence without a pass.
