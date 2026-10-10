# Downloaded-world client acceptance

This check opens a copy of the published **expanded test yard revision 1** in a real Minecraft client with the unreleased grounding-fix jar. The original ZIP and published beta.2 downloads are preserved. [The record](../client-verification.json) identifies the exact inputs, successful runs, and earlier setup or test-driver failures.

The separate helper mod runs the yard's saved-world acceptance checks in the integrated server, then visits 69 stations. It compares 54 client gauge readings with server readings, checks client board and panel contents, captures screenshots, and checks that both wire systems have client rendering data. Actual client interaction packets switch the factory off and on through its capacitor delay and operate the P2 panel lamp. The server must retain all 282 PG wire entities without bridge diagnostics. Minecraft saves and closes normally when the check finishes.

This covers the yard's **85 live behavior groups out of 134 declared groups**, including all 28 built-in PG board components and all 11 CEE panel attachment types. Parts-cabinet contents are not additional live tests. This client run does not repeat the handheld-meter interaction or two-client multiplayer tests, and passing it does not resolve the previously documented intermittent restart/reload failures.

The helper is test equipment, not part of the WWPG release jar. It teleports the player, equips goggles, forces the yard chunks to load, operates controls, and saves the working copy. Use a disposable world copy. The Gradle task requires a successful client marker; a normal process exit alone is insufficient.

Selected client screenshots show the [native transformer readings](native-transformer.png), [native factory with its heater burning](native-factory.png), [Java breaker-panel circuit](java-breaker-panel.png), and [Java resistor board with both wire systems](java-resistor-board.png). The [native](native-client.log) and [Java](java-client.log) checkpoint logs retain the station readings and interaction results.

## Reproduce on Windows

Use the pinned dependencies, Java 21, and the source revision in the record. Build the packaged WWPG jar normally. Copy this directory's `helper/` contents into `build/expanded-client-harness/`; the helper's init script uses that ignored directory for its compiled classes and response files.

Extract the [published yard ZIP](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2-fixture-world-v1.zip) into a new runtime directory's `saves/` folder, retaining the `WWPG Expanded Test Yard/` directory. Create `config/powergrid-server.toml` in that runtime directory **before starting the client**, using the [documented solver settings](../../../../docs/INSTALL.md). Its `[electricity.solver]` section must have `multiTicks = 16` and the selected `solverBackend = "NATIVE"` or `"JAVA"`. Do not change the electrical timestep during play.

Set `onboardAccessibility:false` and `pauseOnLostFocus:false` in that directory's `options.txt` to allow the automated launch to proceed. Then run, for example:

```powershell
.\gradlew.bat -I build/expanded-client-harness/client.init.gradle runExpandedWorldClient -PtestBackend=NATIVE -PexpandedDirectory=run/downloaded-yard-native --no-daemon
```

Run the same command again against that directory to verify a save made by the new jar. For Java, extract another clean copy, select `JAVA` in its configuration, and change both Gradle properties to its backend and directory. Keep the packaged WWPG jar unchanged between runs.

Screenshots are saved in each runtime directory's `screenshots/` folder. The retained checkpoint logs and selected screenshots accompany the record. Full local logs are ignored build output and are identified by hash.
