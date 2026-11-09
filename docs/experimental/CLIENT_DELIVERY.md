# Remaining client chunk-readiness delay

Focused diagnostic, November 9, 2026, following the
[restoration-priority improvement](UNRESOLVED_PRIORITY.md). The gameplay runtime
remains frozen `0.2.7-experimental.wiretree.6`; this investigation changes only
the separate observer and experimental tools. The 1920×1080, 12-chunk view,
copied saves, CPU group and existing electrical/rendering gates are unchanged.

## Finding

The remaining client wait is primarily before chunks become sendable on the
server. It is not an observed backlog of ready packets or exhausted batch
acknowledgements. At approximately one minute, only 214 of the requested 609
view chunks were fully available in the first diagnostic. The client received
chunks approximately as they became tick-ready and sendable.

| Approximate time after selecting world | Full server chunks | Tick-ready/sendable server chunks | Client chunks received |
| --- | ---: | ---: | ---: |
| 30 s | 174 | 142 | 143 |
| 40 s | 178 | 145 | 145 |
| 60 s | 214 | 179 | 179 |
| 80 s | 466 | 399 | 402 |
| 100 s | 609 | 577 | 577 |

Server probes run every 20 ticks; client render observations run about every
100 ms. The table selects the nearest observation from each stream, so they
are not simultaneous counts. Exact timestamps are retained in the
[machine-readable evidence](client-delivery-results-20261109.json).

After electrical readiness, both diagnostic captures observed zero chunks in
the pending-send queue at every server probe, at most one unacknowledged batch
against a limit of ten, and no waits for send dependencies. The unprofiled
inspector's requested rate had reached the server's 64-chunk/tick ceiling.
The profiled capture's lowest late requested rate was 42.56 chunks/tick. Both
used a local memory connection. The pinned `PlayerChunkSender` source confirms
that this path can collect all currently sendable pending chunks in a batch.
Raising a packet quota is not supported by this evidence.

The first diagnostic observed all 609 full chunks by 100.08 seconds and all
609 tick-ready chunks by 108.63 seconds. The profiled capture observed those
milestones by 104.15 and 111.73 seconds. These are sampled upper bounds on the
milestones, not exact event times. Their complete-readiness times of 109.92 and
113.19 seconds are diagnostic observations, not clean comparison samples.

## Late-loading profile

The JFR analysis covers electrical readiness at 65.55 seconds through complete
rendered readiness at 113.19 seconds. It contains 3,548 Java execution samples
on the server thread, none truncated. Of those, 3,538 include `tickServer` and
only ten include `pollTask`; three include the chunk source's polling method.
This is consistent with little server-thread time available for completing
chunk work, but does not alone establish the exact blocking queue or rule out
generation scheduling. Queue depth/age and task-completion observations are
the next way to distinguish those possibilities.

Specific inclusive samples include:

| Scope | Server Java samples |
| --- | ---: |
| Electrical schedule `preTick` | 1,645 |
| CEE bridge preparation | 630 |
| Hanging-wire entity ticking | 449 |
| Native terminal snapshot preparation | 385 |
| CEE stable-description update | 331 |
| Wire ambient-temperature lookup | 247 |

Scopes overlap and these counts are not additive wall-time percentages. Native
solver CPU time is not measured by these Java counts. The client main thread
has 497 execution samples, including 226 in CEE wire rendering; its compilation
queue remains mostly empty while chunks arrive. This reinforces reducing
repeated terminal/device work alongside examining chunk scheduling, rather than
assuming more rendering workers will solve the wait.

## Integrity and scope

The inspector reads existing chunk holders/futures, send-queue fields and batch
timing. It does not request chunks, change tickets, acknowledge batches, change
quotas, pause circuits or modify the solver. Its full-view lookup uses the
non-loading `getChunkNow` path; that path can update Minecraft's ordinary small
lookup cache/profiler counters. The extra probes and output add work, so both
captures remain diagnostic. Server probes averaged about 0.17 ms, with maxima
below 0.71 ms, once per 20 ticks.

The frozen helper is `local/frozen/client-delivery-helper-01/inputs.json`, SHA-256
`7b8b94b2626c96e3462bbe12660d10369ac5392378ce2cac1dfde5d94a91f8c1`.
Every shared client-observer class is byte-identical to the earlier clean
observer; only mod metadata and the added inspector classes/config differ.
Gameplay remains SHA-256
`522c4cb3c394d7c2f80fd2f73fc637cddab5401540128bfe881d4353db1959bb`.

`goal-client-delivery-profile-01` completed normally and passed the full-yard
graph/storage/substep checks, actual artifact checks, complete-view checks,
normal save/quit, affinity readbacks and unchanged reference inventories.
The first `goal-client-delivery-inspect-01` game also completed normally, but
its runner failed when the checker expected an extra mod ID that the unchanged
artifact observer does not enumerate. The original failed protocol remains
unchanged. `local/checks/client-delivery-revalidation-02` records a later audit
of that preserved capture using the already-verified shared helper jar. It is
not a fresh run. The preceding audit attempt failed only while creating its
output path and remains preserved separately.

The gameplay source inventory and frozen artifacts still match the preceding
15 unit and 134 integration checks. Those checks are reused as their original
evidence, not rerun or relabelled. Only observer compilation, these two focused
client diagnostics and their analysis/audit ran. No full headless or graphical
acceptance suite was launched.

## Task-queue follow-up

`goal-client-task-inspect-01` completed with the unchanged gameplay jar and a
separate task observer (`client-task-helper-01`). It records enqueue-to-start
latency and callback execution without polling or changing the game queues.
All full-yard, client-readiness, shutdown, artifact and reference checks passed.

During the remaining-view interval after electrical readiness, 30,777 chunk
callbacks waited a median **49.79 ms** (57.61 ms p95), while their mean execution
time was **0.0029 ms**. Another 1,871 server callbacks waited a median **50.07 ms**.
The observed callbacks consumed 147.09 ms total exclusive execution across the
roughly 40.69-second interval. Every observed execution matched its enqueue.
Queue-depth probes reached seven server tasks and 331 chunk tasks. This points
to waiting between short asynchronous completion stages rather than expensive
execution inside those callbacks. Other background chunk-generation work is
not included in these execution totals.

The dominant chunk task class belongs to `ServerChunkCache`'s direct lighting
notification callback; other large groups are future completions and chunk
priority-queue callbacks. The source's blocking off-thread chunk reads instead
submit `CompletableFuture` tasks, and the preceding late JFR has no parked
stack containing `ServerChunkCache`. The lambda class name alone should not be
described as evidence of blocking chunk reads.

The subsequent [bounded extra task-processing window](CHUNK_TASK_WINDOW.md)
preserves dispatch through the existing server queues, server-thread ownership
and the 16 electrical substeps. Its same-jar clean comparison reached complete
loading in 40.41/39.30 seconds enabled versus 108.91 seconds disabled. See that
snapshot for measured tick-gap limits and focused-check scope.
[Task timing evidence](task-queue-results-20261109.json).

```text
python -B tools/experimental/freeze_client_loading.py --name delivery-helper-new --inputs local/frozen/prototype-07a/inputs.json --base-helper-inputs local/frozen/affinity-helper-02/inputs.json --inspect-delivery
python -B tools/experimental/client_loading_benchmark.py --name delivery-new --inputs local/frozen/delivery-helper-new/inputs.json --tree-prioritize-unresolved --inspect-delivery --delivery-jfr
```

Always use new names. Omitting `--delivery-jfr` retains the readiness probes
without JFR; it still does not make a clean timing sample. The under-one-minute
complete-loading target was unmet in these diagnostic runs; the subsequent
task-window prototype meets it in two clean samples. Consistent 20 TPS remains open.
