# Bounded chunk-task service experiment

Experimental development snapshot, November 9, 2026. Work is isolated on
`dev/experimental-plain-wire-batching`; all benchmark worlds are fresh copies.
This is a scheduling prototype, not release acceptance or a changed default.

The [task-queue trace](CLIENT_DELIVERY.md#task-queue-follow-up) found approximately
50 ms median waits for short lighting and future-completion callbacks after
electrical readiness. The corresponding callbacks used only 147 ms exclusive
execution across a 40.7-second interval. This motivates testing extra queue
service between ticks; it does not establish total chunk-generation CPU cost.

## Implementation and bounds

`0.2.7-experimental.wiretree.7` retains unresolved-wire priority and introduces
`-Dwwpg.experimental.chunkTaskBudgetMillis=2` (default zero, disabled). After
Minecraft's ordinary between-tick wait, it uses the existing `pollTask()` dispatch
for up to two additional milliseconds or 512 successful polls. Empty polls
yield to background workers within that same deadline. Queue priority and FIFO
order remain Minecraft's own; no worker executes world or electrical changes.

The temporary time allowance belongs to one server instance and the calling
server thread. It expires at the deadline, cannot nest, and clears in `finally`
even if dispatch fails. Original positive `haveTime()` results remain positive.
Each poll is atomic: one poll, including work nested inside it, can exceed the
budget. The limit is on successful polls, not every callback they might invoke.
Both maximum poll/window duration and end-to-end tick gaps must be measured.

Once per ten ticks, the runtime checks for queued saved-wire restoration or
missing sendable chunks in any player's existing tracked view. It reads existing
holders/futures without adding tickets or requesting chunks. Extra service
stops within ten ticks of those conditions clearing, and is disabled at server
shutdown. It does not change the view distance, send quotas, rendering criteria,
16 electrical substeps, storage state, heating or device callbacks. The existing
dimension-wide electrical hold during bounded wire restoration remains an open
concern; this prototype does not narrow that hold.

The frozen runtime is `local/frozen/prototype-08a/inputs.json`, SHA-256
`31efbb0099531a7a8db7aa0f52deb80b040080bada825dd33bc339560b85c7a1`.
The clean client helper is `local/frozen/client-task-window-helper-01/inputs.json`,
SHA-256 `e8c164a8908cc519976abbd56a7709af2791bf1fa0e9d7e62271e8f6b5adb015`,
byte-identical to the preceding clean observer. Native solver bytes are unchanged.

## Focused checks

Nineteen unit checks passed, including FIFO/deadline behavior, atomic overruns,
task caps, arrivals during empty polls, thread ownership, nested waits and
exception cleanup. Reports are preserved in `local/checks/task-window-unit-01`.
The native and Java backend runs each passed 38 restoration/budget fixtures and
11 unload/solver fixtures, with the option enabled. Their preserved directories
are `local/checks/task-window-{budget,lifecycle}-{native,java}-01`. These are
regression checks with the new mixin loaded: GameTestServer overrides the
between-tick wait, so their pump counters are zero. They do not exercise active
extra dispatch. The real copied-save client run does, and separately validates
the complete graph, stored energy and 16-substep operation. These are focused
checks only; no full headless or graphical acceptance suite ran.

## Comparison protocol

Use one unchanged runtime/helper pair, fresh copied saves, 1920×1080, render and
simulation distances 12, and process CPU group 12–23 (`fff000`). Start the timer
at normal world opening, excluding JVM startup. Require the complete 609-chunk
client view, compiled visible scene and a two-second stable confirmation, plus
the original 14,650-node circuit, 1,050 storage devices and 100 healthy
16-substep ticks. Verify actual loaded hashes, affinity, normal saving/exiting
and unchanged original/reference saves. Do not use task-observer or JFR timings
as the clean enabled/disabled comparison.

```text
python -B tools/experimental/client_loading_benchmark.py --name task-window-new --inputs local/frozen/client-task-window-helper-01/inputs.json --tree-prioritize-unresolved --chunk-task-budget-ms 2
```

Omit `--chunk-task-budget-ms` for the same-jar control. Always use new names.

## Clean client results

The completed order was enabled, disabled, enabled. All three runs passed the
same complete-view, circuit, storage, 16-substep, artifact, affinity, normal-save
and unchanged-reference checks. The two enabled screenshots were inspected;
the yard, devices and wires render. The [audited comparison](task-window-results-20261109.json)
also verifies all 443 frozen source/build hashes and preserves the focused
check evidence.

| Extra window | Restored and rendered | Electrical ready | First frame | Worst loading tick | Longest tick gap |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2 ms, first | 40.408 s | 38.281 s | 12.849 s | 3.266 s | 3.276 s |
| Disabled | 108.906 s | 63.763 s | 23.277 s | 3.401 s | 3.411 s |
| 2 ms, repeat | 39.298 s | 37.202 s | 11.819 s | 3.164 s | 3.174 s |

Enabled mean completion is **39.853 seconds**, **63.4% lower** than the matched
control. Both enabled observations meet the under-one-minute complete-loading
target. These are fresh-process, stationary-view samples; filesystem caches
were not flushed, and the small sample does not establish general statistical
significance or sustained client TPS.

The first enabled run loaded all 953 requested server chunks in 16.64 seconds,
versus 48.45 seconds for the control. Its subsequent wire-queue recovery took
11.54 seconds versus 5.57 seconds: more wire attachments had already arrived
at the chunk-ready boundary. The complete elapsed time, not that individual
phase boundary, establishes the gain. Full client view receipt completed before
electrical readiness in the enabled runs, removing the previous long tail.

Both enabled runs used 220 extra windows, servicing 38,190 and 37,346 successful
polls respectively. Maximum window duration was 5.104 ms and 2.265 ms; maximum
individual poll duration was 3.257 ms and 0.699 ms. The roughly three-second
loading stalls remain. Post-first-frame interval p95 was 20.77/20.68 ms enabled
and 19.99 ms disabled; their maxima were 41.23/44.10 ms and 37.47 ms. The added
service improves completion time, not every loading or frame-time metric.

## Actual reload and steady TPS

`goal-task-window-reload-01` passed the actual 953-chunk unload/reload protocol,
including proof that the chunks were absent, preserved pre-solve storage,
restored graph and 100 healthy 16-substep ticks after each load. Fresh headless
restoration took **29.604 seconds**; the warm-process reload took **15.849
seconds**. It exercised 400 extra windows and 5,198 successful polls. The maximum
window was 2.135 ms. Worst initial/reload tick durations were 3.658/0.350 seconds;
the corresponding end-to-end gaps were 3.659/0.367 seconds. This is lifecycle
evidence, not another complete-client timing sample.
[Reload evidence](task-window-reload-results-20261109.json).

`goal-task-window-tps-01` passed 120 seconds of warmup and a 120-second stable
sample, retaining 16 substeps, stable graph fingerprints and no stable-phase
matrix rebuilds. Across 2,400 sampled ticks it averaged **20.040 TPS** and
**48.716 ms/tick**, with **56.027 ms p95**, **60.814 ms p99** and **78.355 ms
maximum**. The slight above-20 value reflects sample-window boundaries. Its
initial headless restoration took 29.090 seconds. It serviced 210 extra windows
over the run, with a maximum window of 2.084 ms.
[Steady evidence](task-window-tps-results-20261109.json).

This retains the loading improvement but does not establish a steady speedup:
there is no matched window-disabled steady sample, and the p95 still misses the
50 ms gate. The consistent-20-TPS target and behavior during continuous streaming
remain open. Reduce repeated terminal/device preparation next, preserving
dynamic geometry and all simulation callbacks.
