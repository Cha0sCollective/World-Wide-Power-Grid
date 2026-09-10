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

## Recommended development sequence

Implementation work will likely be easier to reason about if it progresses roughly through these concerns:

1. **Lifecycle interception** — retain CEE model generation and result consumption while bypassing the native CEE solve.
2. **Persistent endpoint mapping** — stable CEE terminal/internal-node representations in PG.
3. **Basic property translation** — resistors and linear sources.
4. **Persistent graph synchronization** — desired-state diffing and topology lifecycle.
5. **Result bridging** — construct valid CEE results from PG-solved values.
6. **Direct PG-wire → CEE-terminal placement.**
7. **Direct CEE-wire → PG-terminal placement.**
8. **Stateful element integration** — capacitor, inductor, accumulator, AC behavior.
9. **Nonlinear solver-hook integration.**
10. **Transformer/coupled-property integration.**
11. **Chunk/restart/sublevel lifecycle hardening.**
12. **Performance instrumentation, optimization, and acceptance benchmarks.**

This sequence is guidance, not a replacement for the finished requirements in `SPEC.md`.

## Pull request expectations

A substantial pull request should explain:

- which `SPEC.md` section(s) it advances;
- what electrical ownership/lifecycle it changes;
- whether PG topology is added/removed or only numerically updated;
- how stateful components avoid double advancement;
- what tests were added or performed;
- known gaps that remain before the product specification is satisfied.

Performance-sensitive work should include before/after measurements when practical.

## Testing philosophy

Electrical interoperability should be tested at several levels:

### Unit/equation tests

Use these for property adapters, current/voltage relationships, timestep behavior, nonlinear residuals, and transformer equations.

### Integration circuits

Build small deterministic circuits that mix native PG elements with CEE compatibility elements and verify solved voltages/currents.

### Lifecycle tests

Exercise placement, removal, chunk unload/reload, world restart, network merge/split, and failure-driven topology changes.

### Gameplay tests

Verify that CEE post-solve behavior such as heating, damage, state updates, and wire failure still occurs correctly from PG-produced results.

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
- what player-visible or electrical-fidelity consequences result;
- how the revised requirement remains consistent with the project's purpose.

The specification should describe the product we actually want to finish—not merely the easiest implementation available today.
