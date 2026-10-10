This beta fixes incorrect power readings and circuit behavior after ground connections change. Adding or removing a ground, changing the circuit's voltage reference, or unloading a ground rod's chunk now updates the electrical equations correctly while keeping the existing circuit objects.

It includes beta.2's handheld-meter compatibility, startup-registration fix, and working heater demonstration. Supported equipment and dependency versions are unchanged.

## Update your installation

1. Stop Minecraft and your server, and back up the world.
2. Replace the older WWPG jar with **`wwpg-0.1.0-beta.3.jar`** on the server and every client. Keep only one WWPG jar in `mods`.
3. Keep the existing dependencies and PG settings, then restart. Your existing expanded yard and panel-demo worlds do not need to be downloaded again.

Use **Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, Power Grid 0.6.2, and Architectury 13.0.8**. PG uses `multiTicks = 16` with `solverBackend = "NATIVE"`; Java remains a tested fallback. Set the electrical timestep before loading the world. WWPG includes the same verified Windows/Linux native solver resources as beta.2.

## Checks and remaining issues

The suite contains **121 packaged electrical, gameplay, lifecycle, and startup checks**, including three regressions for physical-ground changes, reference changes, and actual ground-rod chunk unload/reload. Windows and Linux are checked with both native and Java backends, including saved upstream reference circuits and example-world restarts.

Real Windows native/Java clients check the final versioned jar in the downloaded expanded yard: **69 stations, 54 synchronized gauges, factory switching and capacitor delay, panel lamp control, and 282 retained PG wires**. The yard covers 85 of 134 declared behavior groups, including all 28 built-in PG board components and all 11 CEE panel attachment types. Parts cabinets are inventory rather than extra live tests. Two-client multiplayer and handheld-item interaction evidence comes from earlier checks and is not claimed as a new beta.3 run.

**Known issues remain:** an intermittent circuit reading of 0 V after chunk reload, an intermittent board-capacitor reading of 0 V after restart, and PG 0.6.2's circuit design-table saved-design load error. Passing retries do not establish the causes of the intermittent failures; the grounding fix does not claim to resolve them. Moving systems, optional integrations, and very large grids remain outside this beta's scope.

See [installation instructions](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md), [supported equipment](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/SUPPORT.md), and [current status and evidence](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/STATUS.md). WWPG's own code and documentation are MIT licensed; bundled PG native resources retain Apache 2.0 attribution. Earlier beta downloads and checksums are preserved.
