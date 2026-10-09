# WWPG 0.1.0-beta.1 acceptance

Target: Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3 and PG 0.6.2. CEE 1.1.3 replaces the originally proposed 1.1.1 with the user's approval. Published dependencies remain fixed; [artifacts.json](../release/artifacts.json) records their hashes and matching source references.

This release covers stationary factory power, wiring and controls, all 11 built-in CEE panel attachments and all 28 built-in PG board components. [SUPPORT.md](SUPPORT.md) describes the 134 declared content behaviors. Cosmetic variants share their native behavior. Additional inventoried content stays explicitly unclaimed.

| Stage | Accepted behavior | Evidence |
| --- | --- | --- |
| 0 — Baseline | Fixed NeoForge build, packaged client/server, registry inventory and native upstream reference circuits. | Gradle wrapper, metadata, checksum/JNI verification, full published Create runtime, client launches, dedicated GameTests and saved reference-world comparison. |
| 1 — Lifecycle | CEE prepares devices, PG alone solves, CEE consumes results and commits gameplay. Unchanged graphs retain their objects. | Source/load changes, topology identity, zero CEE solves and exactly-once dynamic advancement assertions. |
| 2 — Wiring | Both wire systems connect across mods and between same-mod machines, with native terminal selection, costs, rendering and cutting. | Six wiring directions, branches/loops, survival spool/wire costs, removal/reconnection, cord sockets, sparse duplex terminals and real-client interactions. |
| 3 — Factory/controls | Actual lighting, heating, fluid pumping, switching, variable resistance, meaningful metering, protection trip and native repair. | Native block entities, real Create tanks/pipes, item interactions, redstone/mechanical controls, meter readings, fuses/breakers, wire burnout and repair. |
| 4 — Dynamics | Storage, reactive circuits, motor modes, AC generation/phase and transformer/variac behavior across mods. | RC/RL recurrences, accumulator charge/discharge, native PG battery item charging, RMS/phase, driven alternators/generators, motors, winding polarity and native transformer construction/configuration. |
| 5 — Electronics | All declared panel attachments and every built-in board component operate through PG, retaining assembly/edit/configuration workflows. | Per-behavior fixtures, nonlinear components and heaters, board design/assembly/native placement, panel item insertion, gauges/displays, edits and replacement. |
| 6 — Normal gameplay | Saves, process restarts, repeated actual chunk unloads, dimension isolation, split/merge, failures and two real clients. | Restored wire UUIDs/panel IDs/capacitor history, unload events, Overworld/Nether circuits, simultaneous TCP wiring/settings and synchronized readings/rendered wire data. Operator diagnostics identify mappings and recent errors. |
| 7 — Freeze/package | Declared support is tied to passing fixtures; packaged artifacts, both PG backends, install instructions, example world and release metadata are available. | Frozen matrix, [verification.json](../release/verification.json), [distribution.json](../release/distribution.json), reproducible jar/world, installation guide and changelog. |

## Verification

The packaged suite contains **114 electrical/gameplay/lifecycle fixtures**, run with native and Java backends on Windows and Ubuntu 24.04. Windows and Linux each run SETUP then VERIFY in separate processes against the same saved test world. Four JUnit tests check electrical equations. Every electrical acceptance run asserts the requested PG backend and zero independent CEE solves.

Two saved reference fixtures first operate with upstream solvers, then reopen with WWPG enabled. The example-world fixture runs in separate creation and restart processes. Multiplayer uses two real Minecraft clients connected to a loopback TCP GameTest server: clients place different native wire types, concurrently change settings, and verify synchronized voltage, resistance and wire-rendering data.

The committed CI workflow reproduces build, backend, restart, reference and example checks on Windows/Linux. Remote CI has not been executed in this local session. The release evidence reports local completed checks and their log hashes; it does not infer success from a workflow file or a zero exit code.

## Boundaries

PG's native v7 solver is primary. WWPG restores the omitted PG 0.6.2 native resources from the official 0.6.1 artifact after checksum and byte-identical JNI verification; the donor mod is not installed at runtime. Java remains tested regression/fallback. Native platform requirements are in [INSTALL.md](INSTALL.md).

The first release does not certify moving trains/contraptions, Sable sublevels, optional integrations, broad dependency ranges, every upstream item, or server-wide scale. Large-network benchmarks, asynchronous execution, topology reduction and comprehensive performance tuning remain future work. Unknown or invalid CEE electrical constructs receive diagnostics and zeroed isolated results.

The beta release artifacts and tag are prepared locally. Publishing to a mod distribution site or GitHub release is a separate operation.
