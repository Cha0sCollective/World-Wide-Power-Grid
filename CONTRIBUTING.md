# Contributing to World-Wide Power Grid

World-Wide Power Grid is a **specification-driven interoperability project**.

The project is pursuing the finished product defined in [`SPEC.md`](SPEC.md). Early milestones may implement only part of that target, but implementation progress must not silently redefine what “complete” means.

## The rule that matters most

Before implementing a feature, identify which requirement(s) in `SPEC.md` the work advances.

A pull request that intentionally changes the product target should update the specification explicitly and explain the architectural reason for doing so. A pull request that merely has not reached the target yet should document the remaining work instead of weakening the specification.

## Architectural invariants

Changes should preserve these project-level invariants unless an explicit specification amendment is being proposed:

- Power Grid is the sole authoritative electrical solver while compatibility mode is active.
- CEE electrical content participates directly in persistent Power Grid topology.
- CEE and PG wires can connect directly to compatible terminals without player-visible adapters.
- Interoperability does not use FE conversion or a previous-tick equivalent-source bridge.
- Stateful electrical properties are advanced exactly once.
- Stable CEE topology is synchronized incrementally rather than destroyed/recreated every tick.
- CEE retains ownership of its non-solver gameplay behavior.
- Large, server-wide connected networks are a supported architectural target.
- Compatibility-provided numerical solver callbacks do not depend on mutable Minecraft world state.
- Electrical integration maintains an explicit **prepare → solve → commit** separation even while those phases execute synchronously.
- All compatibility-induced electrical topology mutations pass through the centralized topology manager.
- Compatibility-induced topology mutation never occurs while an authoritative electrical solve is in progress.

## Prepare, solve, and commit discipline

Compatibility code should be written as though the numerical Power Grid solve may execute off-thread in the future.

The **prepare phase** may read mutable Minecraft/CEE gameplay state and should capture the immutable or solver-owned inputs required by electrical elements.

The **solve phase** must operate only on captured/solver-owned state. Solver callbacks, nonlinear hooks, dynamic element callbacks, and equivalent numerical paths must not directly read or mutate blocks, block entities, entities, levels, inventories, gameplay SavedData, or other mutable world state.

The **commit phase** consumes solved electrical results and applies resulting gameplay state/effects through the normal server-safe lifecycle.

Initial synchronous execution is acceptable. Collapsing these responsibilities together because they currently share a thread is not.

## Topology mutation discipline

Compatibility code should have one structural authority: the centralized topology manager described by `SPEC.md`.

Code that discovers a structural change should submit a **topology change request** rather than directly modifying the compatibility-provided PG graph.

Examples include:

- CEE/PG wire placement or removal;
- machine creation/destruction;
- terminal creation/removal;
- chunk/sublevel lifecycle changes;
- circuit-structure changes;
- compatibility endpoint migration;
- network split/merge consequences.

The topology manager is responsible for ordering/coalescing requests, preserving stable identities, avoiding duplicate operations, and applying structural changes only during a phase in which no authoritative electrical solve is running.

Numerical parameter updates that do not change graph structure may follow their appropriate PG update path, but they must not be disguised structural mutations performed from solver callbacks.

## Recommended development sequence

Implementation work will likely be easier to reason about if it progresses roughly through these concerns:

1. **Lifecycle interception** — retain CEE model generation and result consumption while bypassing the native CEE solve.
2. **Prepare/solve/commit boundaries** — establish solver-safe captured state before writing property adapters.
3. **Central topology manager** — establish the only compatibility-owned path for PG structural mutation.
4. **Persistent endpoint mapping** — stable CEE terminal/internal-node representations in PG.
5. **Basic property translation** — resistors and linear sources.
6. **Persistent graph synchronization** — desired-state diffing that emits parameter updates and topology requests.
7. **Result bridging** — construct valid CEE results from PG-solved values during commit.
8. **Direct PG-wire → CEE-terminal placement.**
9. **Direct CEE-wire → PG-terminal placement.**
10. **Stateful element integration** — capacitor, inductor, accumulator, AC behavior.
11. **Nonlinear solver-hook integration.**
12. **Transformer/coupled-property integration.**
13. **Chunk/restart/sublevel lifecycle hardening.**
14. **Performance instrumentation, concurrency validation, and acceptance benchmarks.**

This sequence is guidance, not a replacement for the finished requirements in `SPEC.md`.

## Pull request expectations

A substantial pull request should explain:

- which `SPEC.md` section(s) it advances;
- what electrical ownership/lifecycle it changes;
- whether PG topology is added/removed or only numerically updated;
- which topology-manager request/path is used for any structural mutation;
- how it guarantees that no structural mutation occurs during a solve;
- what mutable gameplay/world state it reads during prepare;
- what state is exposed to numerical solver callbacks and why it is safe for future off-thread execution;
- what gameplay effects/state are deferred until commit;
- how stateful components avoid double advancement;
- what tests were added or performed;
- known gaps that remain before the product specification is satisfied.

Performance-sensitive work should include before/after measurements when practical.

## Testing philosophy

Electrical interoperability should be tested at several levels:

### Unit/equation tests

Use these for property adapters, current/voltage relationships, timestep behavior, nonlinear residuals, transformer equations, and verification that numerical callbacks depend only on captured/solver-owned state.

### Integration circuits

Build small deterministic circuits that mix native PG elements with CEE compatibility elements and verify solved voltages/currents.

### Lifecycle tests

Exercise placement, removal, chunk unload/reload, world restart, network merge/split, and failure-driven topology changes. Verify that all compatibility structural changes pass through the topology manager.

### Concurrency-boundary tests

Instrument or assert the topology manager so structural mutation attempted during an active solve fails loudly in development rather than racing silently. Exercise prepare/solve/commit boundaries with tests that make mutable-world access from solver callbacks detectable where practical.

### Gameplay tests

Verify that CEE post-solve behavior such as heating, damage, state updates, and wire failure still occurs correctly from PG-produced results during the commit phase.

### Scale tests

Verify that stable topology stays stable and that compatibility synchronization does not devolve into per-tick graph reconstruction. The release-scale benchmark is defined in `SPEC.md`.

## Avoiding compatibility debt

Do not introduce a temporary adapter architecture that is likely to become permanent if it violates core invariants.

In particular, avoid normalizing around:

- FE conversion;
- adapter blocks;
- last-tick equivalent sources;
- running both solvers;
- rebuilding the translated graph every tick;
- direct PG topology mutation from arbitrary compatibility classes;
- topology mutation from solver callbacks;
- mutable-world access from numerical solver hooks simply because the initial solve is synchronous;
- one-off machine adapters when a reusable property-family adapter is appropriate.

A temporary development stub is acceptable when clearly isolated and tracked, but it must not be presented as completion of the corresponding specification requirement.

## Upstream compatibility

World-Wide Power Grid depends on implementation details in both upstream projects. Keep upstream assumptions narrow and documented.

Prefer:

- public APIs and normal extension points;
- targeted Mixins/accessors when no suitable API exists;
- stable model/property abstractions over machine-specific patches;
- explicit supported version declarations.

Avoid unnecessary permanent forks of either upstream mod.

## Specification changes

Changes to the north-star product definition deserve the same scrutiny as major code architecture changes.

A specification-changing pull request should clearly state:

- the existing requirement;
- the proposed replacement;
- why the old requirement is undesirable, impossible, or no longer appropriate;
- what player-visible, electrical-fidelity, concurrency, or scalability consequences result;
- how the revised requirement remains consistent with the project's purpose.

The specification should describe the product we actually want to finish—not merely the easiest implementation available today.
