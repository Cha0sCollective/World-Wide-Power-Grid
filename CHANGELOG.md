# Changelog

## Example world revision 2 — 9 October 2026

- Replace the quiet capacitor/resistor demonstration with a panel-controlled PG relay board that powers CEE RUN/OFF lamps from a PG supply. Its capacitor creates a visible delay after switch-off.
- Add an ammeter, instructions on signs, colored lamp bases, and goggles at spawn. Repeat native panel interactions in creation and restart checks, including saved capacitor state before recharging.
- Publish the revised world and evidence as additional downloads for the existing beta.2 jar, preserving earlier release artifacts and records.

## 0.1.0-beta.2 — 9 October 2026

- Let PG's multimeter read voltage at CEE terminals and mixed terminal pairs, with readings synchronized to the client.
- Let PG's multimeter measure current on CEE wires, and CEE's clamp meter measure current on PG wires. Retain each meter's native display and interaction workflow.
- Clear compatibility selections when probes disconnect, a selected wire is cut, or clamp measurement stops.
- Add handheld-meter regression tests and extend the real-client multiplayer fixture to check the actual item interactions and displayed readings.
- Give the example heater a separate 600 V supply so it reaches Create's working burner heat, without changing CEE's heater model. Add a basin and a chest of testing tools at spawn.
- Make Registrate's pending-callback updates and entry publication atomic per instance, addressing an intermittent startup registration failure. Add a regression that forces a callback to arrive during publication and checks that it is retained.
- Retain the unexplained chunk-reload and board-capacitor restart failure records alongside passing hotfix acceptance; neither is claimed fixed.
- Make WWPG's own code and documentation open source under MIT, including the WWPG portions of the existing beta.
- Declare MIT in mod metadata and include the license in newly built jars and release bundles.
- Preserve the bundled Power Grid native resources' Apache 2.0 license and attribution. Original published beta artifacts and checksums are unchanged.

## Documentation review — 9 October 2026

- Update publication and CI status after the beta release and merge into `main`.
- Add an overview for upstream authors and a shared status page with the unresolved chunk-reload failure.
- Clarify installation, support groups, test coverage, and the difference between beta scope and long-term requirements.
- Add navigation to the long-term specification and explain historical release records.

This review changes repository documentation. The published `0.1.0-beta.1` jar, example world, acceptance records, and hashes are unchanged.

## 0.1.0-beta.1 — 6 October 2026

First published stationary compatibility beta for CEE 1.21.1-1.1.3 and PG 0.6.2 on Minecraft 1.21.1 / NeoForge 21.1.231.

- Run prepared CEE circuits through PG's synchronous electrical lifecycle, preserving native gameplay, heat, protection, damage, and synchronization.
- Connect both native wire systems across supported terminals, including costs, resistance, cutting, cords/sockets, multiblock geometry, and sparse terminal IDs.
- Support the declared factory generation, motors, lighting, heating, pumping, controls, meters, storage, capacitors/inductors, transformers, and variacs.
- Include all 11 built-in CEE panel attachments and all 28 built-in PG board components, with native assembly, editing, configuration, and nonlinear electrical behavior.
- Retain PG objects on unchanged topology and advance electrical state once per substep.
- Restore mixed wires, capacitor/board state, and panel identities in the tested save/restart/unload paths. Track removed PG wire UUIDs to prevent unloaded entities from restoring cut connections.
- Avoid recursive network loading when saved PG wires resolve CEE terminals at world spawn.
- Adjust CEE variac and three-pole meter result lookup, frequency sampling, and motor RMS windows for PG's timestep.
- Isolate unknown or invalid CEE models with diagnostics, and provide `/wwpg`, `/wwpg errors`, and `/wwpg at x y z`.
- Queue configuration-watcher solver updates onto the server thread.
- Include verified native v7 binaries from official PG 0.6.1 to supply the resources missing from PG 0.6.2; retain PG 0.6.2 at runtime and test Java fallback.
- Add packaged tests, saved upstream reference circuits, an example world, and an opt-in two-client TCP check.

Later GitHub runs exposed an intermittent chunk-reload assertion failure. See [current status](docs/STATUS.md) for the failure and subsequent passing runs. Moving systems, optional integrations, unclaimed specialized variants, and server-wide scale testing remain deferred.
