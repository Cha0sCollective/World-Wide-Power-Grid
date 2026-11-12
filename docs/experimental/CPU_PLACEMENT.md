# Explicit benchmark CPU placement

On November 9, 2026 the user requested manual assignment of benchmark runs to
an unused CPU group. The existing helper pinned only the Minecraft server
thread. The experimental launcher now supports pinning the entire owned JVM,
including its compiler, GC and loading workers, to one observed L3 group.

## Selected group and observation

The host exposes two L3 masks: `fff` (logical CPUs 0–11) and `fff000` (12–23),
inside Windows processor group 0. A six-second idle observation before the
first whole-process run found 12–23 averaging approximately **0.7%** busy,
with no individual logical processor above **1.6%**. CPUs 0–11 averaged
approximately **11.8%**. Logical CPUs 12–23 were manually selected.

The explicit option is `--cpu-mask fff000`. The launcher sets that affinity on
the exact process handle returned by its own `Popen`, immediately after launch,
before world restoration. It verifies the result and records
`process-affinity.json`. One-second process observations read the affinity back
throughout execution; an unexpected mask fails the run. The native helper also
records the server thread's original affinity, its actual CPU at tick entry
and exit, and restoration of its original thread mask on shutdown.

Five-second host samples now include cumulative per-core idle/kernel/user
counters. They support interval utilization checks alongside the existing
process CPU observations. Affinity restricts the benchmark; it does not reserve
those CPUs against other applications.

Subsequent launches additionally observe the selected group for six seconds
immediately before creating the JVM. A mean above 5% or any logical CPU above
25% declines that timing attempt before launch. These are experimental noise
guards, not a capacity threshold or assurance against later interference.
The group remains explicitly selected; the launcher does not silently switch
groups or change another application's affinity. Newly prepared attempts also
retain copies and hashes of their Python runner sources.

## Frozen helper and focused checks

`local/frozen/affinity-helper-01/inputs.json` identifies the new helper:

`f12cbf2f6499c499a6dd8961ef41203b793cd74e9a085bb36e6f37e3cd796c7c`.

Exactly two classes differ from the preceding queue-aware helper:
`CpuPlacement` and `FixedPlacement`. Hardware L3 discovery now uses the physical
masks independently of the process's narrower initial affinity. The requested
mask must match an observed whole L3 group and stay within the process's
original allowed mask. Tick evidence uses a neutral `fixed-l3` label, with the
actual mask stored separately. Loading gates, simulation, sample windows,
correctness checks and workload classes are byte-for-byte unchanged.

The original helper, gameplay jars and saved benchmarks remain preserved.
The seven queue-gate checks are reused unchanged, not rerun or relabeled.
Focused validation includes a disposable child process with actual affinity
readback, rejection of zero/out-of-system masks without changing that child,
processor-counter delta checks, and an occupied-group observation while the
owned benchmark was running. The latter correctly rejected the group as busy.

The first full-yard whole-process check is `goal-tps-pinned-01`. It was launched
after the manual preflight and before the automatic preflight/source-snapshot
additions. Do not claim those later launcher paths ran in this attempt. Its
process and server-start evidence both report `fff000`; subsequent snapshots
verify placement through the sample. The runtime remains the unchanged
`0.2.7-experimental.wiretree.4` artifact.

The run passed the full-yard correctness gates and normal shutdown, retaining
2,396 stable ticks and 305 matching process-affinity readbacks. It measured
**19.997 TPS**, **49.879 ms mean** and **57.569 ms p95**; server restoration was
**54.26 seconds**. It still misses the p95 consistency gate and excludes client
rendering. [Raw results and hashes](goal-baselines-20261109.json) also retain the
preceding server-thread-only baseline. This is a placement observation, not a
gameplay-code optimization A/B.

After shutdown, a separate six-second observation found the selected group at
1.33% mean / 2.34% maximum and the new quiet-group guard accepted it. The guard
had rejected the occupied group while the benchmark was running. The automatic
preflight branch and runner-source snapshot are scheduled for their first
end-to-end exercise in the next benchmark; neither was present at the first
whole-process run's launch.

## Server-thread subset comparison

The follow-up helper `affinity-helper-02` accepts an explicit server-thread
subset of the chosen whole-process group. Its only changed parent classes are
`CpuPlacement` and `FixedPlacement`; the queue gate and all electrical checks
remain unchanged. The runner rejects zero masks, masks outside the selected
process group, and helpers without explicit subset support. The process keeps
CPUs 12–23 available to other JVM work in both modes.

The two fresh copied-save runs used the same gameplay jar and helper SHA-256
`56434c77e56c6361aad1b54733eac5573b97bd24d52ae42b0d330216da7a34cb`, with
120 seconds of warmup followed by 120 seconds of measurement. They exercised
the automatic quiet-group preflight and frozen runner-source capture.

| Attempt | Server thread | TPS | Mean tick | p95 tick | Maximum tick | Restoration |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `goal-tps-single14-01` | CPU 14 (`4000`) | 19.121 | 52.118 ms | 61.127 ms | 238.188 ms | 97.483 s |
| `goal-tps-group-02` | CPUs 12–23 (`fff000`) | 19.801 | 50.373 ms | 58.583 ms | 236.486 ms | 90.669 s |

Pinning this server thread to CPU 14 increased mean tick cost by 3.5% and did
not reduce the longest tick. It is not adopted as the default. This is one
sample per mode in that order, not proof that every individual CPU would lose.
The earlier association between migration and slower ticks did not establish
causality. The matching group run also did not reproduce the preceding
54-second restoration: loading variation is still unresolved.

Both runs preserved the full 14,650-node graph, 1,050 pre-solve storage values,
all 16 substeps, unchanged stable topology and normal save/shutdown. Every
observed process mask and tick-entry/exit CPU matched its requested placement.
The competing game was absent; other monitored processes averaged 0.31 and
0.29 CPU cores across the overlapping run samples. Neither mode meets the
practical TPS/p95 gate. [Results and evidence hashes](single-core-results-20261109.json)
retain the complete protocols. No gameplay code changed for this comparison.

## Reproduction

```text
python -B tools/experimental/cpu_affinity.py --observe-seconds 6 --self-check-mask fff000 --output local/checks/cpu-placement-new/preflight.json
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-05b/wwpg-0.2.7-experimental.wiretree.4.jar --helper-inputs local/frozen/affinity-helper-01/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --name pinned-new
```

Choose new names and a quiet group. The runner requires the explicitly
affinity-aware helper when a process mask is supplied. The loading-only runner
accepts the same placement options. Neither command runs full headless or
graphical acceptance.

To reproduce the subset experiment, select `affinity-helper-02/inputs.json`
and add `--server-cpu-mask 4000`; omit that option for the matching group
control. Choose a fresh attempt name for every run.

Keep whole-process comparisons separate from the earlier server-thread-only
protocol. A change in JVM worker placement/available processors and helper
observation is not a runtime optimization. Future candidate/control pairs must
use the same placement, helper, save payload and measurement settings.
