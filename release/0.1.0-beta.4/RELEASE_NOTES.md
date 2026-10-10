This beta improves restoring your factory after leaving and returning to an area or restarting the world. It publishes the restart and chunk-loading corrections from [PR #7](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/7).

## What changed for players

- **Wires survive slow chunk loading.** Power Grid could treat a saved wire as missing while Minecraft was still loading it. WWPG now waits for the chunk's saved entities to load before starting the normal missing-wire cleanup timer. Wires that are actually missing still get cleaned up.
- **Circuit-board capacitors keep their saved charge.** A restored Power Grid board could save a temporary zero reading over its retained charge before its first electrical update. It now saves the capacitor's stored voltage instead.
- **Restoration tests check the right state.** Tests deliberately delay loading, check capacitor charge before the circuit can recharge, and require power to return promptly once the saved wires are ready.

Beta.4 includes the previous grounding, handheld-meter, heater-example, and startup-registration fixes. The supported stationary equipment, dependency versions, and native solver resources are unchanged. **Your existing example and expanded-yard worlds do not need replacing.** The update cannot recover wires or charge already lost and saved by an older version.

## Update your installation

1. Stop Minecraft and your server, and back up the world.
2. Replace the older WWPG jar with **`wwpg-0.1.0-beta.4.jar`** on the server and every client. Keep only one WWPG jar in `mods`.
3. Keep the existing dependencies and PG settings, then restart.

Use **Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, Power Grid 0.6.2, and Architectury 13.0.8**. PG uses `multiTicks = 16` with `solverBackend = "NATIVE"`; Java remains a tested fallback. Set these values before loading the world.

## Validation and remaining issues

The final beta.4 jar passed **123 packaged tests** in separate creation and restart processes on **Windows and Linux, with native and Java solvers**. The [versioned-jar CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38060676594) passed all four jobs on their first attempt; its downloaded jars match the local release jar byte for byte. Checks also cover four electrical equations, saved upstream reference circuits, and creation/restart of both example worlds. The exact downloaded expanded yard passed native and Java automated-server checks before and after saving/restarting each copy. Beta.4 changes only version metadata relative to the tested PR #7 jar; the release verification record compares their contents.

With both restoration fixes disabled, the two new regression tests reproduce premature wire cleanup and capacitor charge loss; the other 121 tests pass. The earlier intermittent failures did not record loading readiness or stored charge at the failing instant, so **we cannot conclusively identify the cause of every historical zero reading**. Passing current checks do not establish that every possible reload problem is fixed.

**PG 0.6.2's circuit design-table saved-design load error remains open.** The expanded yard excludes a placed design table. New graphical-client and two-player runs are not claimed for beta.4; their evidence remains in the earlier release records. Moving systems, optional integrations, and very large grids remain outside this beta's scope.

See [installation instructions](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md), [supported equipment](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/SUPPORT.md), and [current status and evidence](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/STATUS.md). WWPG's own code and documentation are MIT licensed; bundled PG native resources retain Apache 2.0 attribution. Earlier beta downloads and checksums are preserved.
