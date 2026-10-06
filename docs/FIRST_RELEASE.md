# WWPG 0.1.0-beta.1 gates

Target: Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3 and PG 0.6.2. The user approved CEE 1.1.3 in place of the original 1.1.1 target. Dependency versions remain fixed.

The release covers stationary factory power, wiring/control, CEE base panels and PG base circuit-board components. Moving trains/contraptions, Sable sublevels, optional integrations, specialized equipment outside those categories and server-wide scale acceptance are deferred.

Every gate needs a working demonstration and regression checks. Completion is driven by evidence, not dates or amount of code written.

| Stage | Required exit gate | Current evidence / remaining work |
| --- | --- | --- |
| 0 — Baseline | Clean build, client and dedicated server with exact dependencies; committed registry matrix and upstream reference worlds. | Build, client startup, dedicated GameTests, wrapper, metadata, checksum/source lock, CI and a 150-entry registry matrix exist. Standalone upstream reference worlds and remote CI remain to verify. |
| 1 — Lifecycle | CEE source/load solved by PG; voltage/load changes work; zero CEE solves; unchanged objects retained. | Passing real server source/load fixture, parameter-change identity check and solver audit. Native and Java runs have passed. |
| 2 — Direct wiring | Each source/load direction and each wire system; same-mod cross-wires, branches/loops, removal without stale graph objects. | Six native wire interaction fixtures and branch/loop/cut/reconnect/removal pass. Immediate CEE removal handles replacement within one tick. Survival costs, client feedback/rendering and normal cutting tools still need acceptance. |
| 3 — Factory/controls | Mixed switched factory, variable power, meters, protection trip and repair; actual gameplay outputs. | Panel switch/gauge/indicator, lighting, heater heat/cooling, pump drive and fuse trip/state repair pass. Fluid transport, player repair workflow, remaining standalone controls/meters and failure behavior still need checks. |
| 4 — Dynamics | Mixed charging/discharging, reactive circuits, motor modes, AC/phase generation and transformer ratios; exactly-once state. | Capacitor RC and inductor RL recurrences, accumulator charge/discharge, PG item battery charging, AC RMS, transformer ratios/polarity, mixed motors and CEE variac pass. Real alternator/phase assemblies and remaining native machine configurations need full checks. |
| 5 — Electronics | All base CEE attachments and PG board components have passing mixed fixtures; panel/board edits preserve workflows. | Stable panel/internal node mappings and a CEE-powered native PG diode/resistor board are implemented; panel edit, board reconfiguration and nonlinear operation pass on both backends. Full per-component behavior/assembly/crafting coverage remains. |
| 6 — Persistence/play | Restart/chunk reload, split/merge, source/wire failures and two-client dedicated-server synchronization; compact diagnostics. | Separate server processes restore both wire systems, capacitor history, native settings and stable stamps. Actual chunk unload/reload rebinds native endpoints. Source removal and split/merge fixtures pass; status/error/position diagnostics exist. Repeated lifecycle stress, dimension changes and two-client acceptance remain. |
| 7 — Ship | Frozen verified matrix; packaged-jar acceptance, both backends, installation docs, example world, changelog and release tag. | Packaged-jar tests and restart checks exist; complete matrix, reference/example worlds, multiplayer, Linux native execution and release artifacts remain. No release tag; this jar is a development candidate. |

## Acceptance rules

- Each claimed behavior needs a repeatable mixed-network fixture with measurable electrical and gameplay outputs.
- Compare polarity, grounding, resistance, current direction, storage state, AC/RMS and transformer relationships with analytical expectations and the pinned upstream reference circuits.
- Exercise placement, configuration, removal, reconnection, network split/merge, chunk reload and restart.
- Preserve same-mod circuits, native nonlinear PG models and exactly-once dynamic state advancement.
- Check the packaged jar, dedicated server with two clients, and PG's Java and native backends.
- Fix material differences before promoting a matrix entry from `unverified` to `verified`.
- No release with open crashes, persistence failures, duplicate simulation or broken claimed interoperability.

Large-network benchmarking, a 10,000-node gate, asynchronous solving, topology reduction and comprehensive performance tuning are beyond this release. Performance defects that prevent normal functional play still block release.
