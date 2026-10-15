# Build, install and debug an add-on

This is the supported **external-instance workflow** for the examples and adjustable-load starter. It uses Java 21, Minecraft 1.21.1, WWPG 0.2.2-beta.3 and the [tested dependency set](INSTALL.md). The source projects compile/package jars; they do not define game-run tasks.

## Prepare once

Keep source, extracted SDK Maven repository, and game instance in separate directories. Use a throwaway Creative world for development. Install the required runtime mods, your add-on jar and the general example if you need its ideal 20 V source. Use the same versions on a dedicated server and its clients. SDK jars do not go in `mods`.

## Repeat after an edit

1. Edit your Java or resources. Build from the project root with the quoted absolute `-PapiRepository=...` argument from the [tutorial](CREATE_ADDON.md).
2. Stop the client/server cleanly. Copy the new `build/libs/tutorial-load-0.1.0.jar` into its `mods` directory, replacing the previous starter. If you renamed `archivesName` or `version`, use the resulting `<archivesName>-<version>.jar` instead. Do not leave duplicate jars.
3. Start the instance and reopen the small 20 V circuit. Check the settings, lamp, current and power against the tutorial. Check `logs/latest.log` if the mod fails to load or a device is isolated.
4. For server testing, copy the same rebuilt jar to the server and every client, then restart all of them. Run settings/interaction checks from a connected client.

NeoForge hot code replacement is not assumed. Rebuild and restart after topology, registration, saved-format or resource changes. Back up any world you intend to keep.

## Attach a debugger

First import the starter as a Gradle project using Java 21. Add `apiRepository=C:/Dev/WWPG API Maven` (Unix: its absolute path) to your **local** `gradle.properties` before the first IDE sync; property values in this file are not quoted. Do not commit your machine's path. The same value can be supplied through the IDE's Gradle arguments as a quoted `-PapiRepository=...`. Build `sdkProvenance` and compare its `jar_sha256` with the installed jar before debugging; source and installed bytecode must match.

Use your IDE's **Remote JVM / Attach** configuration on `localhost:5005`. Add this JVM argument to the separate launcher's Java/JVM options, before game arguments:

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005
```

For a dedicated server, place the same JVM argument in its `user_jvm_args.txt`, then start it normally. Use a different local port, such as 5006, if you debug client and server together. `suspend=y` waits before startup and lets you catch registration; attach to resume. Remove the option when you finish. Keep the debugger bound to localhost.

Useful breakpoints: `TutorialLoadMod.registerElectricalDevice`, `LoadDevice.prepare`, `LoadDevice.commit`, and the native `loadAdditional` / `saveAdditional` methods. Registration occurs on the mod bus during startup. Electrical preparation and commit run on the logical server; a client breakpoint may never be reached in multiplayer. Inspect immutable terminal records, status and element keys. Avoid holding the server at a breakpoint for long periods: stopped ticks can disconnect clients and change observed timing.

### Inspect restored data before it can be changed

Set a breakpoint on the final assignment in `LoadDevice.loadAdditional()`, then restart and load the saved chunk. Step past `resistanceIndex = restored` and stop before the method returns. Inspect `enabled == false` and `resistanceIndex == 2` (400 Ω); `reading` should still begin with `unavailable`. Do this on the logical server before `prepare()`/`commit()` runs. A breakpoint in `onLoad()` confirms chunk reload; it can fire during initial startup too, so compare the two visits. Resume promptly. For a storage device, inspect the restored charge/current fields at the equivalent point. A later lit lamp cannot prove persistence.

## Where to look

| Evidence | Location or command |
| --- | --- |
| Compiler error | Gradle console; reported source file and line |
| Mod-loading exception | Instance/server `logs/latest.log`; `crash-reports` if generated |
| Actual solver/backend | `/wwpg` |
| One device's identity, terminals and status | `/wwpg at x y z` |
| Recent compatibility errors | `/wwpg errors` |
| Structural connection changes | `/wwpg topology` |

WWPG commands require operator permission level 2. Run them in the dimension containing the device. A missing wire and an invalid device can look similar; read the status and error before changing circuit values. [Troubleshooting](ADDON_TESTING.md) gives the next checks.
