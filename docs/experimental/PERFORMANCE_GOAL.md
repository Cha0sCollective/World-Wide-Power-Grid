# Continuing performance investigation

Active objective, November 9, 2026: sustain 20 TPS on the 14,650-node benchmark
and reach a restored, rendered benchmark scene in under one minute, preserving
all mod functionality. The user confirmed that the loading timer starts when
selecting the world, not when launching Minecraft.

Work stays on `dev/experimental-plain-wire-batching` in the isolated `local/x27w`
worktree. Original saves, release artifacts and normal development remain
untouched. Only focused checks and fresh benchmark-save copies are authorized;
no full headless or graphical acceptance suite is implied by this investigation.

## Current evidence and open requirements

- The bounded restoration runtime is `0.2.7-experimental.wiretree.4`, frozen at
  `local/frozen/prototype-05b/inputs.json` (runtime SHA-256
  `33fe56c713a63e3b70e045b7fc50667435b50923f569036e66e189ab8a063ac6`).
- [Retained completed-region coverage](RETAINED_COVERAGE.md) passed 12 units and
  110 integration checks but did not improve full-yard loading. Its candidate
  is preserved at `86229ab` and retired from the working runtime.
- `goal-tps-control-01`, using the unchanged `wiretree.4` runtime, passed its
  full-yard correctness gates and 120-second stable window after 120 seconds
  of warmup. It measured **19.861 TPS**, **50.134 ms mean**, **58.818 ms p95**,
  **64.806 ms p99** and **257.693 ms maximum** over 2,380 ticks. This misses the
  existing practical gate of at least 19.9 TPS and p95 no greater than 50 ms.
- That run's restoration took **115.37 seconds**, including **61.21 seconds**
  draining queued work after chunk readiness. The same preceding runtime's
  fresh diagnostic profile completed in 59.48 seconds. Neither is evidence of
  a consistently sub-minute load. Ordering/JIT/host and graph-work variation
  remain to be separated; a cause has not yet been established.
- Current measurements are headless and exclude rendering. An actual client
  measurement from world selection through restored visible-scene readiness
  is still required. Server health alone cannot satisfy that requirement.
- The previous bounded prototype still holds all electrical circuits in an
  affected dimension while its wire queue is pending. Preserving behavior
  during continuous streaming and narrowing that pause remain open concerns.

The user additionally requested manual assignment of benchmark runs to an
unused CPU group. The existing helper pins only the server thread to logical
CPUs 12–23 (`fff000`), the second observed L3 group. Whole-process placement and
per-core load observation are being added before subsequent comparisons. Keep
those comparisons separate from the earlier server-thread-only measurements.

## Next implementation targets

1. Finish whole-process CPU placement with readback evidence and focused checks.
2. Explain the repeated traversal of incomplete wire regions, including the
   separate node-holder initialization path, before choosing another batching
   algorithm. Retaining already-completed regions did not address the yard's
   dominant cost.
3. Reduce repeated preparation and terminal/world lookups for steady TPS.
   Inspect native geometry dependencies before any reuse; retain dynamic,
   custom, moving, unloaded and replaced-owner behavior. Do not reduce the
   16 electrical substeps, accuracy, storage, heating, or device callbacks.
4. Establish the actual client loading/rendering measurement and verify both
   targets on the resulting runtime. The goal remains active until that and
   functionality preservation have supporting evidence.

The headless goal runs use the unchanged queue-aware helper from
`local/frozen/budget-helper-01`. All raw logs, artifact manifests, topology and
storage assertions are under `local/benchmarks/<attempt>`. Passing focused
checks are evidence for their named scope, not proof of all mod functionality.
