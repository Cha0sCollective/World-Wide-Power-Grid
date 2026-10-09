# Contributing to WWPG

WWPG is a compatibility project for CEE and Power Grid. The current beta's priority is reliable stationary interoperability; [SPEC.md](SPEC.md) retains the long-term product requirements.

Before changing a feature, identify the relevant beta behavior in the [support matrix](release/content-matrix.json) and the design requirement it advances. Use [STATUS.md](docs/STATUS.md) for unresolved issues and [DEVELOPMENT.md](docs/DEVELOPMENT.md) for build/test commands. Changes to the beta's scope should update its documentation explicitly.

## License

WWPG's own code and documentation use the [MIT License](LICENSE). By submitting a contribution, you agree to license it under MIT. Preserve third-party licenses and attribution notices, including those for the bundled Power Grid native resources.

## Architectural rules

- **One electrical solver:** PG owns the authoritative electrical solve while compatibility is active.
- **Persistent topology:** represent CEE terminals and models in PG; retain existing objects on unchanged topology.
- **Direct wiring:** preserve native tools and terminal interactions without requiring adapter blocks or FE conversion.
- **Single state advancement:** advance each electrical state variable exactly once per authoritative substep.
- **Native gameplay:** CEE owns its machine state, visuals, controls, heat, damage, and physical wires; PG retains its native content models.
- **Explicit phases:** preparation, solving, and gameplay commit remain separate even while execution is synchronous.
- **Single topology owner:** compatibility structural changes pass through the topology manager and execute outside an active solve.

The finished architecture must remain suitable for server-wide networks and future asynchronous solving. Large-network testing and asynchronous execution are deferred for this beta, as described in [FIRST_RELEASE.md](docs/FIRST_RELEASE.md).

## Preparation, solving, and commit

**Prepare** may read mutable world and gameplay state. Capture the electrical inputs the solver needs as immutable or solver-owned values.

**Solve** operates on captured values and electrical model state. Numerical callbacks must not read or mutate blocks, block entities, entities, levels, inventories, or gameplay SavedData. Synchronous execution does not remove this boundary.

**Commit** consumes solved values and applies resulting gameplay state and effects through the normal server lifecycle.

Configuration, wire failures, and other gameplay events can discover required structural changes. Route those requests to the topology owner; defer consequences discovered during commit until the next safe preparation phase.

## Topology changes

The topology owner orders and applies structural requests, preserves stable identities, and avoids duplicate changes. Relevant events include wire or machine placement/removal, terminal edits, chunk lifecycle changes, network split/merge, and endpoint migration.

Changing voltage, resistance, or another numerical parameter should update an existing element where its model permits. Treat a genuine change in circuit structure as a structural request. Neither path may race an active solve.

Persistent identity must come from the owning device, terminal, attachment, or internal node. Temporary graph indices are unsuitable for saved identity.

## Testing a change

Run the checks that establish the behavior changed:

| Change | Relevant checks |
| --- | --- |
| Property translation or result calculations | Equation tests and mixed circuits with analytical expectations |
| Wire or terminal interaction | Both wire tools, native selection/cost/cutting, reconnect, and removal |
| Storage, AC, or coupled elements | State evolution, RMS/phase, polarity, and exactly-once advancement |
| Controls and gameplay outputs | Native interactions, readings, outputs, protection, and repair |
| Topology or persistence | Actual unload/reload, separate-process restart, split/merge, and source removal |
| Synchronization | Two real clients using native packets and checking displayed state |
| Documentation | Links, formatting, version/scope claims, and consistency with recorded evidence |

Preserve same-mod circuits and exercise both PG backends where the change affects electrical solving. A test should measure behavior, not merely mirror the implementation. See [the current reload failure](docs/STATUS.md#unresolved-chunk-reload-failure) before interpreting a repeat run as proof of a fix.

For solver or ownership changes, add assertions that detect structural mutation during a solve and double state advancement. For scale work, use the benchmark defined in [SPEC.md](SPEC.md#39-performance-acceptance-benchmark); passing that benchmark is a long-term completion requirement.

## Pull requests

Describe the problem and resulting behavior for a reviewer who has not seen the development conversation. Include the relevant scope, validation, and remaining limitations.

For electrical or lifecycle work, explain:

- which model, hook, or ownership boundary changes;
- whether parameters or circuit structure change;
- where required gameplay inputs are captured;
- how topology changes stay outside the solve;
- how state advancement and native gameplay remain correct;
- which behavior tests establish the result.

Keep the contribution focused. Use the pinned versions and matching sources in [artifacts.json](release/artifacts.json); similarity to an upstream development API does not establish compatibility. Prefer existing extension points and narrow Mixins/accessors. Avoid unnecessary permanent forks.

## Specification and release records

An implementation limitation belongs in known issues or milestone documentation. It does not silently redefine a requirement for full interoperability.

A change to the long-term product requirements must state the old requirement, the proposed replacement, the reason, and the effects on electrical fidelity, gameplay, concurrency, and scale. Preserve [the specification's change policy](SPEC.md#47-specification-change-policy).

Published tags, assets, hashes, and original acceptance records identify the tested release. Preserve them when updating current documentation; [release/README.md](release/README.md) explains how the historical records relate to live status.
