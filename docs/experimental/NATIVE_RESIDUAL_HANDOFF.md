# Model-switch handoff: native residual cache

**Completed follow-up:** the control and candidate comparison has now finished.
See [the runtime report](NATIVE_RESIDUAL_RUNTIME.md). The candidate's first
preflight failed; attempt 02 completed, with host contention making performance
attribution inconclusive. The remaining text preserves the original handoff
snapshot and is no longer current process state. Testing is stopped after this pair.

Updated November 9, 2026, 6:48 p.m. America/Chicago (00:48 UTC November 10).

## User direction

The overall goal is consistent 20 TPS on the 14,650-node yard and world-selection
to restored/rendered readiness under one minute, preserving mod functionality.
The user has now explicitly said **stop after this comparison**. Finish the
control/candidate pair, document and commit the result, then pause the active
goal with `update_goal(status="paused")`. Do not start another optimization.
The immediate request is just this handoff so the user can switch models.

Use only the isolated worktree `G:\WWPG\local\x27w`, branch
`dev/experimental-plain-wire-batching`. Only focused checks and copied saves
are authorized. No full headless or graphical acceptance. No subagents.
The retained runtime remains `local/frozen/prototype-12g/inputs.json`.
Client settings, if needed later, are 1920x1080, render/simulation distance 12;
there is no new client run in this comparison.

## Live work: do not duplicate or restart

**Control benchmark is confirmed live**, unified exec session **28472**, at
00:48:21 UTC. The latest returned progress is STABLE tick 3200, approximately
23.4 seconds into its 120-second sample. It began at 00:45:11 UTC, after the
six-second quiet CPU preflight. It should normally finish around 00:50 UTC.
Poll that same handle with `write_stdin`; a model switch or observation timeout
does not mean the process stopped. If the handle is unavailable, inspect the
owned process and run files before doing anything else.

Run directory: `local/benchmarks/goal-native-residual-control-01`.
No `runner-exit.json` or final summary existed at the handoff check.
**Candidate benchmark has not started**; its intended directory does not exist.
Wait for the control's normal save/shutdown and successful validation before
starting the candidate. Do not run simultaneous benchmarks or alter other apps.
CPU mask `fff000` restricts the owned JVM to logical CPUs 12-23; the runner
checks quietness and records ongoing affinity readbacks.

## What was completed

The prior committed checkpoint is **27b2e63**, described in
[the offline investigation](NATIVE_RESIDUAL_LAYOUT.md). Its four-row AVX2
residual layout was about 30% faster on three real captured inputs, with 288
captured-vector/status comparisons and 79,120 edge-case comparisons passing.
Scalar row layouts were slower. This projects to about 0.66 ms for a 48-call
tick; it is not a measured TPS gain. Do not revive the retired worker/packed
serial result-processing prototype; this matrix layout is a different change.

This continuation integrated those exact numerical kernels into native-only
experimental DLLs. The cache belongs to each solver, applies at 64+ rows,
refreshes after Jacobian command-buffer writes, rebuilds when insertion changes
nnz, and is freed on clear/resize/destruction. Its independent dirty flag
survives native factorization consuming existing matrix flags. Allocation
failure falls back to the original residual operation. Reviewed JNI entry
points are the only writers to the solver's private matrix; this is not a
general cache for arbitrary external native-memory writes.

Both control and candidate now resolve current logical A at every residual,
including fitting callbacks. When structural changes discard old factors,
the next iteration requests fresh factorization instead of claiming reusable
factors exist. Both variants share this correction; only the candidate enables
the cache. Every callback, all 16 substeps, finite/permutation guards, rollback
and convergence criteria remain. All 294 Java gameplay classes are unchanged.

## Exact frozen artifacts and passing checks

- Final build: `local/native-residual-runtime-02/build.json`, SHA-256
  `251b765fb0d7224144655205e578ad9493171c04fd4b817c15d966a68bb6eb14`.
- Native checks: `local/native-residual-runtime-02/checks.json`, passed.
  Control: 2,048 matrix comparisons / 8 lifecycle cycles. Candidate: 2,184
  comparisons / 108 lifecycle cycles / all 17 cache-allocation failure points.
  Output bits, return status and MXCSR match. Native imports/exports match.
- Fixture manifest: `local/frozen/native-residual-fixtures-01/inputs.json`.
  Fixture SHA `14d97c3462ea9af4190c3c7c07272a7e454f6c76773b95c64244cce484149cb9`.
  Four new live JNI fixtures cover 16 changing substeps, insertion/clear/resize,
  value updates during fitting, and fitting-time insertions forcing native
  buffer reallocation and refactorization.
- Both `local/checks/native-residual-control-01/result.json` and
  `local/checks/native-residual-candidate-01/result.json` passed **24 selected
  in-game tests each**, with normal shutdown. Their sessions 5187 and 94359
  completed exit 0. These are focused native/unload/solver-safety checks only.
- Control frozen manifest: `local/frozen/native-residual-control-01/inputs.json`.
  Runtime SHA `f8e0bf53deb7549fbe18e1507686084123fab2965922e5f56bfc5c8f6dc08f98`;
  DLL SHA `e9476995415ac0a1466188c237dddf741349cf879ec4d24cd4a9247b5e29357d`.
- Candidate manifest: `local/frozen/native-residual-candidate-01/inputs.json`.
  Runtime SHA `6be7132916b852a272e3dad81f098b3f07f811bdf4da64f51c89a0d820a5146e`;
  DLL SHA `065d9ea713caf7874f35bb9a634b959a676e7b5d772babf72307e294bd11f1dc`.
- Shared clean helper: `local/frozen/affinity-helper-02/inputs.json`, helper
  SHA `56434c77e56c6361aad1b54733eac5573b97bd24d52ae42b0d330216da7a34cb`.
- Preserve `native-residual-runtime-01`: its standalone checks passed, but it
  predates the fitting refactor correction. Use build **02**, not 01, for the
  comparison. Both builds and all completed logs are preserved.

## Exact remaining commands

After the control finishes successfully, from the experimental worktree:

```powershell
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/native-residual-candidate-01/wwpg-0.2.7-experimental.native-residual-candidate.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --name goal-native-residual-candidate-01
```

This uses a new copy of the same yard, two minutes warmup and two minutes stable
sampling, with detailed timers/JFR/native observers disabled. Expect about five
minutes including startup/restoration. Preserve any failed attempt and use a
fresh name if a justified retry is necessary. WindowsApps Python/WSL/Gradle/git
writes have required `require_escalated`; automatic review has approved them.

An unexecuted summary/verifier has been written:

```powershell
python -B tools/experimental/summarize_native_residual_runtime.py --controls goal-native-residual-control-01 --candidates goal-native-residual-candidate-01 --output docs/experimental/native-residual-runtime-results-20261109.json --verification-output docs/experimental/native-residual-runtime-verification-20261109.json
```

**Before this summary command**, create `docs/experimental/NATIVE_RESIDUAL_RUNTIME.md`:
the verifier expects it for link checks. The verifier itself has not yet been
executed, so fix harness errors if found, preserving already-written result
files; it has `--verify-existing-result` for a second audit without overwriting.
It verifies the two native identities, 48 fixture executions, source/save
integrity, quiet preflight and affinity, raw metrics and unchanged retained jars.
It also reports headless restoration, which is not complete rendered loading.

Update `PERFORMANCE_GOAL.md` with the observed pair and add a historical follow-up
link from `NATIVE_RESIDUAL_LAYOUT.md`. Do not claim one ordered pair establishes
repeatability or consistent 20 TPS. Assess mean/p95/p99/max and loading; retain
the default runtime unchanged unless stronger evidence supports promotion.
Plan setup/refresh cost is included in actual run behavior but is not separately
isolated. No additional experiment is requested before stopping today.

## Uncommitted work to preserve

At handoff, all tracked files were clean; these eight new files were untracked:

- `tools/experimental/build_native_residual_runtime.py`
- `tools/experimental/freeze_native_residual_fixtures.py`
- `tools/experimental/native-residual/NativeResidualGameTests.java`
- `tools/experimental/native-residual/cache_checks.c`
- `tools/experimental/native-residual/residual_cache.c`
- `tools/experimental/native-residual/residual_cache.h`
- `tools/experimental/native_residual_runtime.py`
- `tools/experimental/summarize_native_residual_runtime.py`

This handoff is another new file. No current-turn integration commit exists.
Commit the finished experiment and report on the experimental branch after the
comparison. Preserve the main worktree's 307 historical untracked entries;
its tracked files were clean at the prior verified checkpoint. Do not publish,
merge, replace normal jars, touch original saves, or run full suites.

After recording the result, pause the goal as explicitly requested and tell
the user the result and that testing has stopped. The broad goal is not proven
complete. Previous retained client readiness was 44.399 seconds with scoped
restoration enabled, but steady p95 remained above 50 ms. See
[the performance goal](PERFORMANCE_GOAL.md) for the longer investigation.
