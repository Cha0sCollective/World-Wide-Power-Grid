# Why the 14,650-node yard takes about two minutes to restore

The [packed prototype rollback](PACKED_ROLLBACK.md) is complete at `e691024`.
This investigation uses the restored `0.2.7-experimental.plainwire.2` runtime,
with plain-wire and CEE property-source batching enabled. No runtime, save
format or electrical scheduling change was made for the investigation.

Follow-up experiment: [wire-tree restoration batching](WIRE_TREE_BATCHING.md).
The measurements below remain the earlier unchanged-runtime profile.

The strongest next target is **repeated wire-tree resolution during loading**.
It accounts for 39.4% of observed server CPU samples. The server also keeps
preparing and simulating hundreds of temporary circuit fragments while entities
arrive. Loading more data on worker threads would not directly remove either
cost: wire reconstruction currently mutates live graph/world state on the server.

## What the restoration timer actually measures

One new diagnostic run, `restoration-profile-01`, restored a fresh copy of the
preserved benchmark. It stopped after restoration and its existing health gate;
there was no warmup, stable TPS window, full suite or graphical acceptance.
This is a fresh process loading a saved world, not an experiment with flushed
OS disk caches. The recorded duration is instrumented, not a clean speed claim.

| Recorded phase | Seconds |
| --- | ---: |
| Storage verification, initial chunk requests | 1.33 |
| Waiting for all block/entity/ticking readiness predicates | 111.82 |
| Remaining graph recovery after chunk readiness | 0.12 |
| Electrical acceptance, including 100 consecutive healthy ticks | 6.70 |
| Total | **119.96** |

The phase called `chunk_loading_seconds` is **not a disk-read timer**. All 953
block chunks are already present at the first snapshot, tick 20. The long phase
includes entity reconstruction, entity visibility/ticking promotion, ongoing
electrical work, graph mutation and server-task processing. Initial block/NBT
work is not zero; the evidence localizes most of the remaining delay elsewhere.
The 0.12-second final graph phase also does not mean graph rebuilding was cheap:
most graph work happened earlier, while readiness was still incomplete.

| Tick | Block chunks present | Ticking chunks | Entity chunks loaded | PG wire entities | Solver networks |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 953 | 0 | 184 | 1,050 | 351 |
| 300 | 953 | 0 | 592 | 3,355 | 351 |
| 500 | 953 | 816 | 690 | 3,910 | 351 |
| 700 | 953 | 953 | 816 | 5,028 | 111 |
| 900 | 953 | 953 | 880 | 5,637 | 12 |
| 1,000 | 953 | 953 | 935 | 5,880 | 2 |
| 1,100 | 953 | 953 | 953 | 5,943 | 1 |

All readiness predicates pass at tick 1,019; the expected graph follows at
1,020; the health gate completes at 1,122. Entity-load snapshots include both
`PENDING/TICKING` and `FRESH/HIDDEN` chunks. Thus the delay includes both already
requested entity work and chunks whose entity visibility has not yet advanced.
Snapshots every 20 ticks do not measure each individual queue's service time.

## The dominant call path

The recording captures this expensive chain:

```text
EntityStorage.loadEntities, on the server executor
  Entity.load -> BaseWireEntity.readAdditionalSaveData
    setEndpoint2 -> WireEntity.makeWire
      WorldNetworks.makeTransmissionLine -> tryGrabUnloadedPart
        resolveTree(endpoint1), resolveTree(endpoint2)
          WWPG WireTreeTraversal.trace
```

The pinned PG jar's bytecode confirms that `tryGrabUnloadedPart` resolves both
endpoint trees **before** looking up the already saved line part. `resolveTree`
creates a fresh visited set for each invocation. Additional resolution starts
when block-entity node holders are added. Across these operations, the same
connected wire structure can be visited repeatedly as it becomes available.

WWPG's [iterative traversal](../../src/main/java/org/cha0scollective/wwpg/wiring/WireTreeTraversal.java)
prevents the earlier recursive stack overflow, but retains PG's traversal
semantics. Its [mixin](../../src/main/java/org/cha0scollective/wwpg/mixin/PgWireTraceMixin.java)
still migrates/resolves endpoints and copies each visited part collection.
An already resolved edge sets a flag; it does not prune the rest of the walk.
This is repeated graph/world work, not just NBT parsing. The single yard does
not prove quadratic scaling or establish how many traversals could safely merge.

Of 7,848 server execution/native samples after the first startup tick:

| Disjoint stack group | Samples | Share |
| --- | ---: | ---: |
| Wire-tree resolution | 3,093 | **39.4%** |
| Electrical schedule and its children | 2,349 | 29.9% |
| CEE preparation through WWPG | 1,130 | 14.4% |
| CEE commit through WWPG | 203 | 2.6% |
| Other server work | 1,073 | 13.7% |

Within tree resolution, 2,400 samples come through saved wire reconstruction
and 693 through `nodeHolderAdded`. These are sample counts, not invocation
counts or exact seconds. Native solver work can be underrepresented in JFR.

**A substantial cost runs between measured ticks.** The tick-2-through-final
window spans 116.03 seconds, while the tick bodies total 85.34 seconds and
85.25 seconds of server CPU. About 30.70 seconds lies outside those tick bodies,
including post-tick observation and queued tasks. JFR puts 30.6% of server
samples under entity reconstruction via `waitUntilNextTick`/`ProcessorMailbox`;
that method name represents active queued work here. Server park events total
zero in the window. This is why optimizing only ordinary tick profiles misses
a large part of restoration.

## Rebuilding and simulating the partial grid

The always-recorded loading counters show:

* **700 matrix assemblies**, all attributed to topology changes; zero drift or
  timestep rebuilds. Of these, 351 occur in the first 20 ticks and 349 afterward.
  They are not 700 full-size yard matrices: there are many initial fragments.
* **12.50 seconds in matrix stamping**, versus only **0.054 seconds in solver
  allocation**. From ticks 351-1,019 alone, 287 assemblies consume 11.79 seconds.
* **1,909 island-discovery requests**, totaling **1.77 seconds** in their wrapped
  scope. This is separate from wire-tree resolution, despite both being graph work.
* **3,546,624 network substep calls** while restoring. These cover many small
  networks as well as the growing yard, not millions of full-yard solves.
* 700 returns without convergence in the loading instrumentation correspond to
  settling attempts; the runtime reports **zero rejected shared solves** and
  zero independent CEE solves. The final 100 ticks each have 16 substeps on one
  network, with no matrix rebuilds.

These measured method scopes and CPU sample shares overlap. Do not add their
seconds/percentages together or extrapolate them into an expected speedup.
The phase timers sampled every 20 ticks also align with periodic work; their
sampled means are not used to estimate whole-restoration totals.

[RestorationWindow](../../src/main/java/org/cha0scollective/wwpg/bridge/RestorationWindow.java)
releases a window after two quiet ready ticks or a hard limit of 20 ticks.
The [coordinator](../../src/main/java/org/cha0scollective/wwpg/bridge/RestorationCoordinator.java)
defers island discovery and network preparation for those windows. It does not
coalesce the `resolveTree` calls made during entity NBT reconstruction. A load
spanning roughly 1,000 ticks outlasts that bounded window repeatedly. This
explains why current restoration batching cannot act as one transaction for
the whole yard. The deadline deliberately prevents indefinite pauses; simply
increasing it would change gameplay and is not the proposed first fix.

## Allocation and endpoint lookup

JFR estimates 56.26 GiB of server allocation weight in the complete attribution
window; 33.28 GiB occurs under wire-tree resolution. These are sampled weights.
Tick-body counters separately measure 28.99 GiB, excluding allocations between
ticks. They cover different scopes and should not be compared as equal totals.
The first startup tick's uninitialized counter baseline is excluded entirely.

Prominent sampled sites include `ApiTerminalIdentities.ownersAt`,
`CeeElectric.accessible`, `BlockWireEndpoint.hashCode`, traversal frames and
copied part lists. `ownersAt` builds a stream/list over stored owner identities;
`accessible` rebuilds terminal geometry/maps. The traversal repeatedly reaches
these lookups through endpoint resolution. Reducing repeated resolution should
therefore cut more than the traversal loop alone.

GC pauses total only **0.231 seconds** in the attribution window. Allocation
still consumes CPU, but GC pause tuning is not the main opportunity supported
by this run. No file read/write events crossed JFR's configured reporting
threshold; that does not establish zero disk work.

## Next experimental steps, in order

1. **Instrument and coalesce restoration tree resolution on the server.** Count
   calls, visited endpoints/parts and repeat visits per component/generation.
   Separate saved-part attachment from graph resolution; gather dirty roots and
   resolve a component once per valid batch boundary. Preserve part identity,
   migration, leaf rules, line ordering and the iterative traversal. Do not just
   skip a tree because one part already has a line: newly loaded branches may
   still require migration or merging. Retain the ordinary path outside loading.
2. **Reduce endpoint-query work within a proven stable operation.** Reuse CEE
   terminal geometry and owner lookup results while their world/owner generation
   remains valid. A direct owner-position index is another candidate. Invalidate
   on loading/unloading, block state, panel geometry and owner changes. Unknown
   extension behavior should retain the existing lookup path.
3. **Coalesce matrix preparation after graph mutation batches.** Measure whether
   fewer traversals already reduce the 349 later assemblies. Then consider a
   bounded component restoration transaction, preserving stored electrical
   state and suppressing publication until acceptance. Do not advance partially
   restored storage or silently change the 16-substep electrical schedule.
4. **Reassess workers after redundant work is removed.** Immutable graph-index
   construction or independent components may offer parallel work. Current
   traversal invokes world queries, owner migration, line merging and graph
   mutation, so it is not safe to dispatch unchanged to workers. Minecraft
   already has background I/O; this profile does not support starting with
   additional chunk-read workers or another packed arithmetic worker prototype.

The next prototype's focused checks should cover saved wire identity, load
order, partially loaded endpoints, loop/branch/leaf graphs, unload/reload,
CEE terminal ownership, coupled circuits, stored-state preservation and the
existing traversal equivalence fixture. Follow with a fresh copied-yard load,
checking graph/node/edge totals, all storage identities, 16 accepted substeps,
normal shutdown and untouched references. Compare clean restoration wall time,
not just tick MSPT; retain diagnostic runs separately. No full suite is implied.

## Evidence and reproduction

The new diagnostic passed: 14,650 nodes, 20,637 edges, 14,925 matrix rows, one
native network, 953 chunks and 1,050 saved storage entries. Both source/reference
save inventories remain unchanged. The server saved all dimensions and exited
normally. The 60-second watchdog and `fff000` server-thread affinity remained.
Java 21.0.12, NeoForge 21.1.231, PG 0.6.2, CEE 1.1.4 and the frozen helper remain
pinned. There was no systematic concurrent-host sampling, so this single
instrumented run supports attribution, not a clean speedup estimate.

Historical unchanged-batching runs restored in 115.79-124.23 seconds. The later
packed-jar control restored in 104.02 seconds under different host/JVM conditions.
Those are explicitly retained historical observations, not reruns or controlled
evidence of a rollback regression. The new 119.96-second diagnostic is consistent
with the original batching runs.

[restoration-profile-results.json](restoration-profile-results.json) records raw
file hashes, validation, counters, JFR attribution, source/bytecode evidence and
the historical comparison. Raw snapshots/JFR stay under
`local/benchmarks/restoration-profile-01`. PG source was inspected at local
upstream commit `0938be0`; the relevant call order was confirmed against the
actual loaded jar with `javap`. Minecraft source is the pinned NeoForge 21.1.231
source artifact. No upstream repositories were changed.

```text
python -B tools/experimental/restoration_profile.py --reference-root <main-checkout> --name restoration-profile-new --runtime local/frozen/prototype-02c/wwpg-0.2.7-experimental.plainwire.2.jar
java tools/experimental/JfrCompact.java local/benchmarks/restoration-profile-new/loading.jfr local/benchmarks/restoration-profile-new/benchmark/loading-jfr-compact.json
jfr summary local/benchmarks/restoration-profile-new/loading.jfr > local/benchmarks/restoration-profile-new/benchmark/loading-jfr-summary.txt
python -B tools/experimental/summarize_restoration.py --attempt restoration-profile-new --output local/restoration-profile-new-results.json
```

Use new attempt/output names. The analyzer expects the locally inspected
source/bytecode inventory when available; those files are attribution evidence,
not runtime inputs. It refuses to overwrite an existing summary. Focused tool
checks comprised Python parsing, the actual finite diagnostic, Java JFR export,
analysis assertions and evidence-hash verification. The prior 74 correctness
checks remain explicitly reused through the byte-identical rollback record.
