# First beta: scope and acceptance

**WWPG 0.1.0-beta.1** delivers a declared set of stationary devices working across CEE and PG. The [support matrix](SUPPORT.md) describes 134 checked behaviors, including all 11 built-in CEE panel attachments and all 28 built-in PG board components.

The beta is [published on GitHub](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1), and its implementation was [merged into `main`](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/1). See [STATUS.md](STATUS.md) for later CI results and the unresolved chunk-reload failure.

This page records the original beta.1 acceptance. Beta.2 adds handheld-meter coverage and corrects the example heater's power supply; see [current support](SUPPORT.md) and [hotfix records](../release/0.1.0-beta.2/).

## Fixed baseline

Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, PG 0.6.2, and Architectury 13.0.8. The dependency list is also in [INSTALL.md](INSTALL.md); published artifact hashes and source references are in [artifacts.json](../release/artifacts.json).

CEE 1.1.3 is the implemented target. The original development proposal targeted 1.1.1; it is not an additional supported version.

## Development stages and evidence

The stages describe the work leading to the beta. Passing evidence establishes the recorded test outcomes, not a guarantee that every future run or upstream configuration will pass.

| Stage | Delivered behavior | Evidence |
| --- | --- | --- |
| 0 — Baseline | Fixed NeoForge build, packaged client/server, content inventory, and native upstream reference circuits. | Wrapper, metadata, artifact/JNI verification, client launches, server tests, and saved reference worlds. |
| 1 — Electrical lifecycle | CEE prepares devices; PG solves; CEE receives results and applies gameplay. Unchanged topology retains its objects. | Source/load changes, object identity checks, zero independent CEE solves, and exactly-once state assertions. |
| 2 — Direct wiring | Both wire systems connect across mods using native terminals and interactions. | Connection directions, branches/loops, survival costs, cutting, reconnection, cords, and real-client selection/rendering. |
| 3 — Factory and controls | Lighting, heating, fluid transport, controls, meters, protection, and repair respond to solved power. | Native devices, Create tanks/pipes, mechanical/redstone controls, readings, fuse/breaker repair, and wire failure. |
| 4 — Dynamic circuits | Storage, capacitors/inductors, motor modes, AC generation, transformers, and variacs work in mixed circuits. | Charge/discharge and transient equations, RMS/phase, driven generation, native assembly/configuration, and winding polarity. |
| 5 — Electronics | All declared panel attachments and board components retain their electrical and native interaction workflows. | Component behavior, nonlinear circuits, board design/assembly, panel insertion/editing, gauges/displays, and replacement. |
| 6 — Persistence and play | Mixed mappings and state survive the tested save/restart/reload paths; two real clients can wire and configure circuits. | Wire UUIDs, panel IDs, stored charge, actual unload events, dimension isolation, TCP clients, and diagnostics. An intermittent reload assertion remains unresolved. |
| 7 — Packaging | The support matrix, jar, example world, documentation, and test evidence are published together. | Frozen records, artifact hashes, release downloads, changelog, and tag `0.1.0-beta.1`. |

## Recorded checks

The packaged suite contains **114 electrical, gameplay, and lifecycle tests**. Local acceptance ran native and Java backends on Windows and Ubuntu 24.04, with SETUP and VERIFY in separate processes against the same saved worlds. Four JUnit tests check electrical equations. Electrical acceptance asserts the requested backend and zero independent CEE solves.

Two reference tests first run with the upstream solvers, then reopen saved circuits with WWPG enabled. The example world has separate creation and restart tests. Two real Minecraft clients connect over loopback TCP, place different native wire types, change settings, and check synchronized readings and rendered wire data.

GitHub CI reproduces build, backend, restart, reference, and example checks. The [implementation merge run passed](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37990481752). Real-client multiplayer is a separate local check, not a headless CI job.

The [release records](../release/README.md) distinguish original local evidence from later GitHub results. The original JSON records and published assets remain unchanged.

## Boundaries

PG native v7 is primary, with Java checked as a fallback. Native resource provenance and platform requirements are in [INSTALL.md](INSTALL.md#native-solver-requirements).

Moving trains/contraptions, Sable sublevels, optional integrations, broad version ranges, unclaimed specialized devices, and server-wide scale testing are deferred. Large-network benchmarks, asynchronous execution, topology reduction, and comprehensive performance tuning remain future work. Unknown or invalid CEE electrical models receive diagnostics and isolated zero results.

The [long-term specification](../SPEC.md) retains the full product and scale goals. This beta's acceptance scope does not replace those requirements.
