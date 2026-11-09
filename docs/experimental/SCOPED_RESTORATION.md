# Scoped restoration prototype

Experimental snapshot, November 9, 2026. `wiretree.11` remains on
`dev/experimental-plain-wire-batching` in the isolated worktree. The new
`wwpg.experimental.scopedRestoration` switch defaults to false and requires
bounded saved-wire batching. No release or normal-development adoption is
implied. The [performance goal](PERFORMANCE_GOAL.md) remains open.

## Behavior

While saved-wire work is pending, an ephemeral connectivity calculation follows
saved `lineParts`, original queued endpoints, current native node ownership,
CEE/API internal owners, transmission lines and native split controllers.
Terminals belonging to one device position share a conservative hold. Only
components reachable from queued work are paused. Unknown physical mappings
explicitly fall back to the previous dimension-wide hold.

The calculation reads metadata on the server thread. It does not resolve wire
endpoints, initialize block entities, execute geometry callbacks or request
chunks. No graph index or additional save format is retained. Empty-queue
steady ticks use a fast path.

Snapshots bracket native topology preparation. Negative hold decisions are
rechecked against newly absorbed membership; holds already used for preparation
follow the nodes into their final owners. Scheduled native island discovery is
allowed to complete, so a disconnected circuit can split away while new saved
arrivals continue. The solve, public measurement and CEE/API commit paths use
the same hold.

Prepared internal-owner metadata covers live CEE/API nodes. An API internal
endpoint also retains its immutable device identity after model retirement;
its dimension and position are read without calling its provider. Otherwise a
retired saved endpoint could incorrectly force unrelated circuits into fallback.

## Work limits

The existing 20 ms / 32-part queue slice still bounds which atomic tree entries
are started. Individual native operations can overrun it. Scope snapshots and
scheduled native island discovery are measured separately and are **not**
included in that 20 ms budget. This prototype therefore does not establish a
20 ms bound on all restoration work.

The full saved graph is scanned twice on loading ticks. In the superseded
`prototype-12d` full-yard observation, scope work took 8.025 seconds across the
initial load and reload, with a 34.395 ms maximum individual scope operation.
Native discovery took 0.472 seconds in total, with a 78.536 ms maximum. Initial
restoration was 33.547 seconds and reload was 20.633 seconds. These are headless
measurements for that exact earlier artifact, not client-loading evidence for
the final prototype.

## Focused validation

The measured freeze is `local/frozen/prototype-12g/inputs.json`. Gameplay SHA-256:
`fa5a133d685311485dde7441db4ac55db87e54b236bf7fb9ece41da27bdd7ba9`.
Its fixture jar is
`24d2d70ca4b8895d71e1d74c9fc49b23a1873507f4f1c10627f2443d1fc51636`.

The nine targeted cases pass on native and Java solvers. They cover repeated
saved arrivals while an independent circuit takes exactly 16 substeps; removal
of a connecting wire during those arrivals; held native/CEE storage and heat;
independent native/CEE/API storage; affected API internal storage; unresolved
saved branches through missing owners; replacement node/network identities;
removed queued wires; native split-controller holds across worlds in either
iteration order; and unsupported endpoints whose resolution callbacks throw.
The unsupported-endpoint case is the only fallback in those runs.

The same artifact also passes 32 focused API ownership/loading/unload/solver
checks and 38 native restoration/pause/contactor/recovery checks. Another 38
checks with the new switch disabled confirm the previous behavior. That is
126 passing fixture executions across the named lanes, not 126 unique cases
or a full acceptance run. The API lifecycle lane has no bounded queue activity;
the nine dedicated cases supply the API-under-queue coverage.

The 17 preserved unit tests include connectivity comparisons of 200 random graphs with breadth-first
reachability and cover a 100,000-node chain/loop. Queue and quiet-window tests
are preserved separately. Five Python evidence checks reject missing,
duplicate, disabled or inconsistent execution reports and prevent matching
enabled scope results to disabled profile results.

Earlier failed attempts remain preserved. `scoped-native-01` lacked the new
namespace's empty test structure. `02` exposed missing CEE internal-owner
mapping; `03` also reproduced indefinite holding after disconnect. `05` first
hit overlapping fixture blocks, and `06` isolated retired API endpoint fallback
after correcting that placement. Each affected source inventory was archived
before further changes. No interrupted or failed run counts as a passing lane.

## Loading observations for the measured artifact

Both client runs use the identical gameplay jar and byte-identical observer,
fresh copies of the same 14,650-node save, 1920×1080, render/simulation distance
12 and the independently observed CPU group `fff000`. Timers start at world
selection. Detailed phase timers and JFR are disabled.

| Observation | Scope enabled | Scope disabled |
| --- | ---: | ---: |
| First world frame | 12.830 s | 13.601 s |
| Electrical readiness | 42.318 s | 37.221 s |
| Restored and rendered readiness | 44.399 s | 39.302 s |
| Worst observed loading tick | 3.122 s | 2.944 s |
| Frame interval p95 | 20.559 ms | 20.767 ms |

This pair shows a 5.098-second loading cost for the narrower pause, not a
loading-speed improvement. Both observations meet the one-minute load target.
The client checks verify all 609 view chunks, completed rendering queues, two
seconds of confirmed readiness, the expected electrical graph and normal save
and exit. Both captured screenshots were inspected and show the benchmark's
devices and wires. The single pair does not establish a repeatable change in
worst-tick stalls or rendering speed.

The final-artifact headless `goal-scoped-reload-02` run reached initial
restoration in 32.996 seconds and actual reload in 20.222 seconds. All 953 yard
chunks became genuinely absent before reloading. The 1,050 storage devices,
14,650 nodes, 20,637 edges, 14,925 matrix rows and final single native solver
network were checked, followed by 100 healthy ticks at 16 substeps. No scope
fallback occurred. Scope calculation took 8.228 seconds across the two loads;
the largest individual scope operation was 62.336 ms. Scheduled topology
discovery took 0.455 seconds total, with a 78.316 ms maximum.

Client evidence: [enabled](client-scoped-on-results-20261109.json) and
[disabled](client-scoped-off-results-20261109.json). The
[combined evidence audit](scoped-restoration-results-20261109.json) preserves
the source/artifact comparisons, successful and failed fixture attempts,
archived superseded source, reload and steady measurements.

## Steady comparison

Each headless control used 120 seconds of warmup followed by a nominal
120-second measured interval, identical frozen jars and helper, CPU group
`fff000`, 16 electrical substeps, and no detailed timers or JFR.

| Metric | Scope enabled | Scope disabled |
| --- | ---: | ---: |
| Measured ticks | 2,401 | 2,400 |
| Observed TPS | 20.030 | 20.043 |
| Mean tick | 48.319 ms | 48.965 ms |
| p95 tick | 55.104 ms | 55.425 ms |
| p99 tick | 58.378 ms | 60.299 ms |
| Maximum tick | 70.490 ms | 68.844 ms |

The small differences in this single enabled/disabled pair do not establish a
steady speedup. No steady regression was observed in the pair. Both miss the
50 ms p95 consistency gate despite averaging approximately 20 TPS. Other
processes used about 0.276 and 0.278 CPU cores on average, respectively; no
heavy competing process appeared in the captured windows. Both checks retain
the full graph, storage and substep assertions and exit normally.

This is focused evidence, not full acceptance. Broader gameplay and
moving/custom integrations remain outside these focused lanes. Unknown
physical endpoint types deliberately retain conservative dimension fallback.

## Remaining work

The metadata scans add measurable loading cost. A later experiment may reuse
allocation within one preparation epoch, but must still inspect final saved
edges and network membership, preserve every hold already used, and discard
the connectivity state at the next tick. No cross-tick connectivity cache is
authorized as an ownership shortcut by these results. Consistent steady TPS
and the largest native loading stalls remain separate requirements.

## Reproducing the focused lanes

Use fresh attempt names and preserved frozen inputs. The API examples are
independent add-on artifacts from `prototype-03d`; that manifest's retired
gameplay jar is not loaded.

```text
python -B tools/experimental/focused_checks.py --inputs local/frozen/prototype-12g/inputs.json --backend NATIVE --selection scope --tree-batch --tree-budget-ms 20 --tree-max-parts 1 --tree-prioritize-unresolved --property-batch --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --addon-inputs local/frozen/prototype-03d/inputs.json --name scope-new
python -B tools/experimental/client_loading_benchmark.py --inputs local/frozen/client-scoped-helper-01/inputs.json --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --name scope-client-new
```

These commands select only the dedicated fixtures or one copied-save client
observation. The normal headless and graphical acceptance suites remain manual.
