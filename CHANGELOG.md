# 0.1.0-beta.1

- Run stationary CEE circuits through PG's synchronous prepare/solve/commit lifecycle. Preserve native gameplay, losses, protection, damage and synchronization; CEE performs no independent solve.
- Connect both upstream wire systems directly across mods, including native costs, resistance, cutting, cord/sockets, connector geometry and sparse panel/duplex terminal IDs.
- Support the declared factory generation, lighting, heating, pumping, motors, controls, meters, storage, reactives, transformers and variacs. Preserve unchanged PG graph objects and exactly-once dynamic advancement.
- Include all 11 base CEE panel attachments and all 28 base PG board components, with native assembly, configuration, board design/placement and nonlinear simulation.
- Restore mixed wiring, capacitor/board state and panel identities after saves, process restarts and repeated actual chunk unloads. Prevent removed saved PG wire entities from resurrecting connections.
- Resolve CEE terminals from PG's published network during saved-line loading, avoiding recursive network loads when mixed circuits are at world spawn.
- Correct pinned CEE variac result lookup, panel three-pole metering, frequency-meter sampling and motor RMS windows for PG's timestep. Isolate unknown/invalid electrical properties and provide `/wwpg` diagnostics.
- Queue PG configuration-watcher solver updates onto the server thread, preventing concurrent solver replacement during a solve.
- Bundle and verify PG's missing native v7 binaries from its official 0.6.1 artifact, retaining PG 0.6.2 at runtime and PG's Java fallback.
- Add packaged acceptance fixtures, upstream saved reference circuits, a mixed example world, and opt-in testing with two real TCP Minecraft clients.

This beta declares a bounded stationary support set. Moving contraptions/trains, optional integrations, Sable, broad version ranges, unclaimed specialized variants and server-wide scale testing remain future work.
