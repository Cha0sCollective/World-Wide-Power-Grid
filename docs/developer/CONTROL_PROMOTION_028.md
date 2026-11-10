# Promoting the experimental control

This branch prepares **0.2.8-rc.1** from `4549eb5`, with extension API 1.0.0.
It changes runtime code and native libraries. All release acceptance must therefore
be fresh against the final frozen inputs. Previous performance/functional results
remain historical and are not carried forward as release passes.

The control now enables its retained behavior without launcher arguments:

- Aggregate plain PG wires and reviewed CEE property-source wires while retaining
  electrical operation order, live conductance and required hooks.
- Queue saved wire-tree attachments, prioritize unresolved work with fair service,
  and yield after a cooperative 20 ms window or 32 attachments. A single attachment
  or native resolver call can exceed that window; it is not a hard tick limit.
- Offer up to 2 ms of extra chunk-task service while tracked views or saved wires
  need loading. Reuse reviewed state-only CEE geometry and scope restoration holds
  to relevant components, with the conservative fallback for unknown cases.
- Use exact integer AVX2 finite/max reductions in the guarded native solver, with
  the original scalar path for subnormal or nonfinite inputs. Refresh logical
  matrix storage during fitting and refactor when structural callbacks discarded
  the prior factors. Both OS builds use the same source patch.

All electrical substeps remain serial on the server thread. The rejected packed
result/worker prototype and residual row-layout cache are not promoted. Indexed
waveform and terminal bookkeeping/key experiments remain disabled.

The existing `wwpg.experimental.*` JVM properties remain compatibility escape
hatches for investigation; their names do not require players to supply them.
The intended defaults are recorded in source. Setting the corresponding boolean
to false restores that feature's ordinary fallback. Set the chunk budget to zero
to disable extra service, or the tree budget to zero for the unbounded queue path.

`release/0.2.8-rc.1/runtime-selection.json` records the current runtime selection.
The full catalog uses its `runtime_loader` for every lifecycle group and retains
older loader checks separately. Minimum CEE/Create/Architectury SDK compile
inputs stay distinct from the newer runtime jars. CC:Tweaked 1.121.0 is labelled
beta by its publisher; the earlier 1.120.2 was labelled alpha.

The new circuit fixtures run one assembly per fresh process at 16 substeps, on
both backends, OSes and installation profiles in the primary runtime lane.
They preserve their original functional input sequences and exact schematics.
The player runtime contains no test classes or fixture schematics.

The loading group also promotes the saved PG/CEE ring restoration and reviewed
geometry differential fixtures into ordinary acceptance. The stricter one-part
budget/scope stress fixtures retain their separate opt-in configuration.

No claim of reliable 20 TPS is made for the 14,650-node benchmark. Release
acceptance checks correctness, lifecycle behavior and the named client actions;
it is not a new capacity/performance certification.
