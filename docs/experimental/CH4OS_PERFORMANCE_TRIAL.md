# Ch4oS Public server-performance trial

November 9, 2026. User-requested focused trial in the isolated
`dev/experimental-plain-wire-batching` worktree, following the
[runtime audit](CH4OS_RUNTIME_AUDIT.md). All game runs use copied benchmark saves.

## Scope and frozen setup

The comparison uses the exact last-control WWPG/native jar and affinity helper:
`local/frozen/native-residual-control-01/inputs.json` and
`local/frozen/affinity-helper-02/inputs.json`. The reference observation is
`goal-native-residual-control-01`. It is historical, not a new contemporaneous
control. The trial is `goal-ch4os-public-performance-01`.

The new lane uses the Public instance's Microsoft Java 21.0.7+6-LTS,
`-Xms1024M -Xmx14000M -XX:+UseZGC -XX:+ZGenerational`, and twelve exact installed
server-relevant jars: Lithium, ModernFix, FerriteCore, AllTheLeaks, Smoothchunk,
Leaky, Clumps, Connectivity, Packet Fixer, Recipe Essentials, Structure
Essentials and their Cupboard dependency. Twelve matching config files are
copied. The inputs are frozen at
`local/frozen/ch4os-public-performance-01/inputs.json`; original launcher mods,
configs, runtime and saves are read-only inputs.

The Public Sable jar declares four Lithium exclusions. Their exact rule/value
pairs are extracted into the copied Lithium config so those features remain
disabled without installing Sable's gameplay systems. That is a reproduced
compatibility policy, not the complete Public modpack. ModernFix's dynamic
resources and faster item rendering overrides are retained, though client-only
features have no rendering work in this headless lane.

NeoForge 21.1.231, Create 6.0.10-280, Architectury 13.0.8, PG 0.6.2, CEE 1.1.4,
the native DLL, all experimental batching/restoration flags and CPU mask
`fff000` remain the last-control inputs. Public uses newer NeoForge/Create/
Architectury, which are deliberately outside this performance-stack comparison.
The headless server properties select Public's 12-chunk view / 10-chunk
simulation distances, but the protocol has no player view and explicitly loads
the same 953-chunk yard. This does not measure a render-distance benefit.

Sodium, Sodium Extra, ImmediatelyFast and GPU Memory Leak Fix are not exercised:
they require the client/rendering lane. The other Public gameplay mods and
shader/resource-pack load are absent. This trial can address server TPS and
headless restoration, not the user's entire visual experience in Public.

## Protocol and startup observations

The copied yard retains 14,650 electrical nodes, 20,637 edges, 14,925 matrix
rows, 1,050 storage owners and all 16 substeps. The normal two-minute warmup and
two-minute stable sample, quiet CPU preflight, ongoing affinity/host metrics,
source-save checks and normal save/shutdown validation are retained. There is
no full headless or graphical acceptance run.

The trial preflight passed with 1.91% mean and 3.13% maximum selected-CPU load.
Startup output confirms the Microsoft JVM, ZGC, generational mode and a
14,680,064,000-byte maximum heap. The GC log explicitly identifies ZGC.
Startup also reports a ModernFix/Lithium overlap on `SortedArraySet.removeIf`;
Lithium's existing implementation wins and ModernFix skips that method.

GC info logging and startup command-line flags are enabled for collector
identity. No detailed phase timers, JFR or native instrumentation are enabled.
The `gc_ms` summary is not directly comparable between these collectors:
ZGC cycle time includes concurrent collection work, whereas it must not be
presented as equivalent stop-the-world pause time.

## Completed observation

The trial exited normally and passed graph/storage, all-16-substep, actual
native-library identity, affinity and original/reference-save checks. It did
not improve this headless workload:

| Metric | Last control | Public server-performance stack |
| --- | ---: | ---: |
| Average TPS | 20.014 | 16.845 |
| Mean tick, ms | 47.830 | 59.208 |
| p95 tick, ms | 55.778 | 69.917 |
| p99 tick, ms | 68.765 | 77.483 |
| Maximum tick, ms | 90.731 | 91.587 |
| Headless restoration, s | 33.466 | 45.605 |

Mean tick time was 11.378 ms (23.79%) higher. Other monitored process CPU
averaged 0.687 cores versus 0.335 for the historical control. The observations
are not contemporaneous, and this combined trial changes multiple inputs, so
it cannot establish which individual mod or JVM choice caused the difference.
It supplies no evidence for adopting this combined setup as a WWPG TPS fix.
Client rendering performance remains unmeasured here.

The measured heap peak was 13,908,312,064 bytes and process RSS peak was
14,768,746,496 bytes. The GC log shows occupancy reaching 13,264 MiB and
dropping to roughly 506–598 MiB after young collections, so the high sampled
peak should not be labeled a retained-memory leak. Collector cycle times
include concurrent work and are not stop-the-world pause durations.

Eleven copied configuration files remained byte-identical. Packet Fixer
rewrote only its comment timestamp; its setting values stayed the same.
The frozen originals, initial config hashes and post-run hashes are preserved
in [the comparison result](ch4os-performance-results-20261109.json), with
[verification](ch4os-performance-verification-20261109.json). All twelve
performance jars, four reproduced Lithium exclusions, Public JVM/GC/heap
settings and unchanged WWPG/native identities were verified. All 450 retained
source/build hashes and the retained runtime/fixture jars still match.

The next useful attribution would separate GC/heap/Java from the mod stack,
using a fresh matched control. That follow-up was not run automatically.
Testing stopped after this requested trial; no default or published jar was
replaced by its performance stack.

## Reproduction commands

`tools/experimental/ch4os_performance_trial.py freeze` snapshots the applicable
Public files and probes the selected JVM; `run` extends the existing copied-yard
runner without editing its historical scripts or artifacts. Always choose fresh
bundle and attempt names. A new local bundle is needed if Public inputs change.

```text
python -B tools/experimental/ch4os_performance_trial.py freeze --name ch4os-public-performance-new
python -B tools/experimental/ch4os_performance_trial.py run --bundle-inputs local/frozen/ch4os-public-performance-new/inputs.json --reference-root G:/WWPG --runtime local/frozen/native-residual-control-01/wwpg-0.2.7-experimental.native-residual-control.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --name goal-ch4os-public-performance-new
```

A successful combined trial would still not identify individual mod/GC/heap
contributions. Separate matched lanes and repeated observations are needed
before adopting a default or making a general capacity claim.
